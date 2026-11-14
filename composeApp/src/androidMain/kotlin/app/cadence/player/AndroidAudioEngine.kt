package app.cadence.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.cadence.model.Track
import app.cadence.playback.CadencePlaybackService
import app.cadence.playback.StreamHeaders
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Android playback engine that talks to [CadencePlaybackService] through a
 * [MediaController] instead of owning an ExoPlayer. The service owns the player,
 * which gives background playback, a media notification and lockscreen controls.
 */
class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(EngineState())
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
    override val events: Flow<EngineEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var controller: MediaController? = null
    private var pendingSource: PendingSource? = null
    @Volatile private var desiredPlayWhenReady = false
    @Volatile private var polling = false

    private class PendingSource(
        val track: Track,
        val headers: Map<String, String>,
        val playWhenReady: Boolean,
    )

    init {
        val sessionToken = SessionToken(appContext, ComponentName(appContext, CadencePlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        future.addListener({
            runCatching {
                val built = future.get()
                controller = built
                built.addListener(
                    object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> _state.update { it.copy(isBuffering = true) }
                                Player.STATE_READY -> _state.update { it.copy(isBuffering = false) }
                                Player.STATE_ENDED -> {
                                    _state.update { it.copy(isPlaying = false, isBuffering = false) }
                                    _events.tryEmit(EngineEvent.TrackEnded)
                                }
                                else -> Unit
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _state.update { it.copy(isPlaying = isPlaying) }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _state.update { it.copy(isPlaying = false, isBuffering = false) }
                            _events.tryEmit(EngineEvent.Error(error.message ?: "Could not play this track"))
                        }
                    },
                )
                controller?.volume = _state.value.volume
                pendingSource?.let { startSource(it) }
                pendingSource = null
                startPolling()
            }
        }, Executors.newSingleThreadExecutor())
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update { current ->
                        current.copy(
                            positionMs = c.currentPosition.coerceAtLeast(0L),
                            durationMs = c.duration.takeIf { it > 0L } ?: current.durationMs,
                            volume = c.volume,
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun startSource(pending: PendingSource) {
        val c = controller ?: run { pendingSource = pending; return }
        val track = pending.track
        val uri = track.playbackUri
        StreamHeaders.set(uri, pending.headers)

        val builder = MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaId(track.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.displayArtist)
                    .setAlbumTitle(track.displayAlbum)
                    .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )

        c.setMediaItem(builder.build())
        c.prepare()
        desiredPlayWhenReady = pending.playWhenReady
        c.playWhenReady = pending.playWhenReady
        _state.value = EngineState(
            isPlaying = pending.playWhenReady,
            isBuffering = true,
            positionMs = 0L,
            durationMs = track.durationMs,
            volume = _state.value.volume,
        )
    }

    override fun setSource(track: Track, playWhenReady: Boolean, headers: Map<String, String>) {
        val pending = PendingSource(track, headers, playWhenReady)
        val c = controller
        if (c == null) {
            pendingSource = pending
            return
        }
        startSource(pending)
    }

    override fun play() {
        val c = controller ?: return
        desiredPlayWhenReady = true
        c.play()
        _state.update { it.copy(isPlaying = true) }
    }

    override fun pause() {
        val c = controller ?: return
        desiredPlayWhenReady = false
        c.pause()
        _state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun setVolume(volume: Float) {
        val bounded = volume.coerceIn(0f, 1f)
        controller?.volume = bounded
        _state.update { it.copy(volume = bounded) }
    }

    override fun release() {
        controller = null
        scope.cancel()
    }
}
