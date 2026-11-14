package app.cadence.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.cadence.model.Track
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

class AndroidAudioEngine(private val context: Context) : AudioEngine {

    private val player = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        volume = 0.8f
    }

    private val _state = MutableStateFlow(EngineState())
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
    override val events: Flow<EngineEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        player.addListener(object : Player.Listener {
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
        })

        scope.launch {
            while (isActive) {
                _state.update { current ->
                    current.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.takeIf { it > 0L } ?: current.durationMs,
                    )
                }
                delay(200)
            }
        }
    }

    override fun setSource(track: Track, playWhenReady: Boolean, headers: Map<String, String>) {
        val uri = track.playbackUri
        val mediaItem = MediaItem.fromUri(uri)
        if (headers.isNotEmpty() && (uri.startsWith("http://") || uri.startsWith("https://"))) {
            val http = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers)
            val dataSource = DefaultDataSource.Factory(context, http)
            val source = DefaultMediaSourceFactory(dataSource).createMediaSource(mediaItem)
            player.setMediaSource(source)
        } else {
            player.setMediaItem(mediaItem)
        }
        player.prepare()
        player.playWhenReady = playWhenReady
        _state.value = EngineState(
            isPlaying = playWhenReady,
            isBuffering = true,
            positionMs = 0L,
            durationMs = track.durationMs,
        )
    }

    override fun play() {
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
    }

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun release() {
        player.release()
        scope.cancel()
    }
}
