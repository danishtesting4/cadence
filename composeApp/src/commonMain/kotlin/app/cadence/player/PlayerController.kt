package app.cadence.player

import app.cadence.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val volume: Float = 0.8f,
    val error: String? = null,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val upcoming: List<Track>
        get() = if (index < 0) emptyList() else queue.drop(index + 1)
}

/** Result of resolving an online track into a playable stream. */
data class ResolvedPlayback(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long = 0L,
)

/** A segment that should be skipped during playback. */
data class PlaybackSkipSegment(val startMs: Long, val endMs: Long)

/**
 * Queue, shuffle and repeat live here so the behaviour is identical on every
 * platform; [AudioEngine] only knows how to play a single source.
 */
class PlayerController(
    private val engine: AudioEngine,
    private val scope: CoroutineScope,
    private val resolvePlayback: (suspend (Track) -> ResolvedPlayback?)? = null,
    private val fetchSkipSegments: (suspend (videoId: String) -> List<PlaybackSkipSegment>)? = null,
) {
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val shuffleBag = ArrayDeque<Int>()
    private var resolveJob: Job? = null
    private var skipJob: Job? = null
    private var skipSegments: List<PlaybackSkipSegment> = emptyList()
    private var skipIndex: Int = 0
    private var lastSkipSeekMs: Long = -1L

    init {
        scope.launch {
            engine.state.collect { e ->
                val pos = applySkipSegments(e.positionMs, e.isPlaying)
                _state.update { current ->
                    current.copy(
                        isPlaying = e.isPlaying,
                        isBuffering = e.isBuffering,
                        positionMs = pos,
                        durationMs = if (e.durationMs > 0) e.durationMs else current.durationMs,
                    )
                }
            }
        }
        scope.launch {
            engine.events.collect { event ->
                when (event) {
                    EngineEvent.TrackEnded -> handleTrackEnded()
                    is EngineEvent.Error -> _state.update {
                        it.copy(isPlaying = false, isBuffering = false, error = event.message)
                    }
                }
            }
        }
        engine.setVolume(_state.value.volume)
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        shuffleBag.clear()
        _state.update { it.copy(queue = tracks, index = index, error = null) }
        startCurrent()
    }

    fun playTrack(track: Track, context: List<Track>? = null) {
        val list = context?.takeIf { it.any { item -> item.id == track.id } } ?: listOf(track)
        playQueue(list, list.indexOfFirst { it.id == track.id })
    }

    fun toggle() {
        val current = _state.value
        if (current.track == null) return
        if (current.isPlaying) engine.pause() else engine.play()
        _state.update { it.copy(isPlaying = !current.isPlaying) }
    }

    fun next() {
        val current = _state.value
        val target = nextIndex(current) ?: return
        _state.update { it.copy(index = target) }
        startCurrent()
    }

    fun previous() {
        val current = _state.value
        if (current.queue.isEmpty()) return
        if (current.positionMs > 3_000L) {
            seekTo(0L)
            return
        }
        val target = when {
            current.index > 0 -> current.index - 1
            current.repeat == RepeatMode.ALL -> current.queue.lastIndex
            else -> 0
        }
        _state.update { it.copy(index = target) }
        startCurrent()
    }

    fun seekTo(positionMs: Long) {
        val current = _state.value
        val bounded = positionMs.coerceIn(0L, if (current.durationMs > 0) current.durationMs else positionMs)
        engine.seekTo(bounded)
        _state.update { it.copy(positionMs = bounded) }
        // allow re-skipping if user seeks back, but avoid immediate re-trigger loops
        if (bounded < lastSkipSeekMs - 1500L) lastSkipSeekMs = -1L
        resetSkipIndex(bounded)
    }

    fun seekRelative(deltaMs: Long) {
        seekTo(_state.value.positionMs + deltaMs)
    }

    fun setVolume(volume: Float) {
        val bounded = volume.coerceIn(0f, 1f)
        engine.setVolume(bounded)
        _state.update { it.copy(volume = bounded) }
    }

    fun toggleShuffle() {
        shuffleBag.clear()
        _state.update { it.copy(shuffle = !it.shuffle) }
    }

    fun cycleRepeat() {
        _state.update {
            it.copy(
                repeat = when (it.repeat) {
                    RepeatMode.OFF -> RepeatMode.ALL
                    RepeatMode.ALL -> RepeatMode.ONE
                    RepeatMode.ONE -> RepeatMode.OFF
                },
            )
        }
    }

    fun playNext(track: Track) {
        val current = _state.value
        if (current.queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        val insertAt = (current.index + 1).coerceIn(0, current.queue.size)
        val queue = current.queue.toMutableList().apply { add(insertAt, track) }
        _state.update { it.copy(queue = queue) }
    }

    fun addToQueue(track: Track) {
        val current = _state.value
        if (current.queue.isEmpty()) {
            playQueue(listOf(track), 0)
            return
        }
        _state.update { it.copy(queue = it.queue + track) }
    }

    fun jumpTo(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        _state.update { it.copy(index = index) }
        startCurrent()
    }

    fun removeFromQueue(index: Int) {
        val current = _state.value
        if (index !in current.queue.indices) return
        if (index == current.index) {
            engine.pause()
            _state.update {
                it.copy(
                    queue = it.queue.filterIndexed { i, _ -> i != index },
                    track = null,
                    isPlaying = false,
                    positionMs = 0,
                    durationMs = 0,
                )
            }
            return
        }
        val queue = current.queue.filterIndexed { i, _ -> i != index }
        val newIndex = if (index < current.index) current.index - 1 else current.index
        _state.update { it.copy(queue = queue, index = newIndex) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startCurrent() {
        val current = _state.value
        val track = current.queue.getOrNull(current.index) ?: return
        resolveJob?.cancel()
        skipJob?.cancel()
        skipSegments = emptyList()
        skipIndex = 0
        lastSkipSeekMs = -1L

        _state.update {
            it.copy(
                track = track,
                positionMs = 0L,
                durationMs = track.durationMs,
                isPlaying = true,
                isBuffering = true,
                error = null,
            )
        }

        if (track.isOnline && resolvePlayback != null) {
            resolveJob = scope.launch {
                val resolved = runCatching { resolvePlayback.invoke(track) }.getOrNull()
                if (_state.value.track?.id != track.id) return@launch
                if (resolved == null) {
                    engine.pause()
                    _state.update { it.copy(isPlaying = false, isBuffering = false, error = "Could not load this online track") }
                    return@launch
                }
                val playable = track.copy(
                    uri = resolved.url,
                    durationMs = if (resolved.durationMs > 0) resolved.durationMs else track.durationMs,
                )
                engine.setSource(playable, playWhenReady = true, headers = resolved.headers)
                _state.update { it.copy(durationMs = playable.durationMs) }
            }
        } else {
            engine.setSource(track, playWhenReady = true)
        }

        val videoId = track.videoId
        if (videoId != null && fetchSkipSegments != null) {
            skipJob = scope.launch {
                val segs = runCatching { fetchSkipSegments.invoke(videoId) }.getOrDefault(emptyList())
                if (_state.value.track?.id == track.id) {
                    skipSegments = segs.sortedBy { it.startMs }
                    skipIndex = 0
                }
            }
        }
    }

    private fun handleTrackEnded() {
        val current = _state.value
        if (current.repeat == RepeatMode.ONE) {
            engine.seekTo(0L)
            engine.play()
            return
        }
        val target = nextIndex(current)
        if (target == null) {
            _state.update { it.copy(isPlaying = false, positionMs = 0L) }
            return
        }
        _state.update { it.copy(index = target) }
        startCurrent()
    }

    private fun applySkipSegments(positionMs: Long, isPlaying: Boolean): Long {
        if (!isPlaying || skipSegments.isEmpty()) return positionMs
        while (skipIndex < skipSegments.size && positionMs >= skipSegments[skipIndex].endMs) skipIndex++
        if (skipIndex < skipSegments.size) {
            val seg = skipSegments[skipIndex]
            if (positionMs in seg.startMs until seg.endMs && kotlin.math.abs(lastSkipSeekMs - seg.endMs) > 500L) {
                lastSkipSeekMs = seg.endMs
                engine.seekTo(seg.endMs)
                return seg.endMs
            }
        }
        return positionMs
    }

    private fun resetSkipIndex(positionMs: Long) {
        skipIndex = skipSegments.indexOfFirst { positionMs < it.endMs }.let { if (it < 0) skipSegments.size else it }
    }

    private fun nextIndex(current: PlayerUiState): Int? {
        if (current.queue.isEmpty()) return null
        if (current.shuffle) {
            if (shuffleBag.isEmpty()) refillShuffleBag(current.queue.size, current.index)
            return shuffleBag.removeFirstOrNull()
                ?: if (current.repeat == RepeatMode.ALL) 0 else null
        }
        val next = current.index + 1
        return when {
            next < current.queue.size -> next
            current.repeat == RepeatMode.ALL -> 0
            else -> null
        }
    }

    private fun refillShuffleBag(size: Int, exclude: Int) {
        shuffleBag.clear()
        (0 until size).filter { it != exclude }.shuffled().forEach(shuffleBag::addLast)
    }
}
