package app.cadence.player

import app.cadence.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class EngineState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

sealed interface EngineEvent {
    data object TrackEnded : EngineEvent
    data class Error(val message: String) : EngineEvent
}

/** Thin platform playback abstraction: one source at a time, no queue logic. */
interface AudioEngine {
    val state: StateFlow<EngineState>
    val events: Flow<EngineEvent>

    fun setSource(track: Track, playWhenReady: Boolean)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun release()
}
