package app.cadence.player

import app.cadence.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun track(id: String, durationMs: Long = 120_000L) = Track(
    id = id,
    uri = "file:///$id.mp3",
    title = id.uppercase(),
    artist = "Artist",
    album = "Album",
    durationMs = durationMs,
)

private class FakeAudioEngine : AudioEngine {
    private val _state = MutableStateFlow(EngineState())
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    override val events = _events.asSharedFlow()

    val loaded = mutableListOf<Track>()
    var playCalls = 0
    var pauseCalls = 0

    override fun setSource(track: Track, playWhenReady: Boolean, headers: Map<String, String>) {
        loaded += track
        _state.value = EngineState(
            isPlaying = playWhenReady,
            positionMs = 0L,
            durationMs = track.durationMs,
        )
    }

    override fun play() {
        playCalls++
        _state.value = _state.value.copy(isPlaying = true)
    }

    override fun pause() {
        pauseCalls++
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    override fun setVolume(volume: Float) = Unit

    override fun release() = Unit

    fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {

    private val a = track("a")
    private val b = track("b")
    private val c = track("c")

    @Test
    fun `playing a queue starts the selected track`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a, b, c), 1)
        advanceUntilIdle()

        assertEquals(b.id, player.state.value.track?.id)
        assertEquals(1, player.state.value.index)
        assertEquals(listOf(b), engine.loaded)
        assertTrue(player.state.value.isPlaying)
    }

    @Test
    fun `next advances through the queue`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a, b, c), 0)
        player.next()
        advanceUntilIdle()

        assertEquals(b.id, player.state.value.track?.id)
        assertEquals(listOf(a, b), engine.loaded)
    }

    @Test
    fun `repeat all wraps after the last track`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a, b), 1)
        player.cycleRepeat()
        player.next()
        advanceUntilIdle()

        assertEquals(0, player.state.value.index)
        assertEquals(a.id, player.state.value.track?.id)
        assertEquals(RepeatMode.ALL, player.state.value.repeat)
    }

    @Test
    fun `repeat one restarts the current track when it ends`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a, b), 0)
        player.cycleRepeat()
        player.cycleRepeat()
        engine.emit(EngineEvent.TrackEnded)
        advanceUntilIdle()

        assertEquals(RepeatMode.ONE, player.state.value.repeat)
        assertEquals(a.id, player.state.value.track?.id)
        assertEquals(listOf(a), engine.loaded)
        assertEquals(1, engine.playCalls)
    }

    @Test
    fun `stopping after the queue ends without repeat`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a), 0)
        engine.emit(EngineEvent.TrackEnded)
        advanceUntilIdle()

        assertEquals(a.id, player.state.value.track?.id)
        assertEquals(false, player.state.value.isPlaying)
        assertEquals(0L, player.state.value.positionMs)
    }

    @Test
    fun `shuffle visits every track exactly once per pass`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        val queue = (1..5).map { track("t$it") }

        player.playQueue(queue, 0)
        player.toggleShuffle()
        repeat(queue.size - 1) {
            player.next()
            advanceUntilIdle()
        }

        assertEquals(queue.size, engine.loaded.map { it.id }.toSet().size)
        assertEquals(queue.size, engine.loaded.size)
    }

    @Test
    fun `removing the current track stops playback`() = runTest {
        val engine = FakeAudioEngine()
        val player = PlayerController(engine, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        advanceUntilIdle()

        player.playQueue(listOf(a, b), 0)
        player.removeFromQueue(0)
        advanceUntilIdle()

        assertEquals(null, player.state.value.track)
        assertEquals(listOf(b), player.state.value.queue)
        assertEquals(false, player.state.value.isPlaying)
    }
}
