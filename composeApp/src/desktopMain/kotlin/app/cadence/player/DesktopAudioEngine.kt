package app.cadence.player

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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread
import kotlin.math.log10

/**
 * Desktop playback on top of Java Sound. Local files (MP3/FLAC/OGG/WAV via
 * soundlibs SPI) are supported. HTTP sources are downloaded to a temp/cache file
 * first so they can be decoded by available SPIs; formats without a decoder will
 * fail gracefully (common YT web formats like WebM/Opus or M4A/AAC may need
 * additional codecs on the host).
 */
class DesktopAudioEngine : AudioEngine {

    private val _state = MutableStateFlow(EngineState())
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
    override val events: Flow<EngineEvent> = _events.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Object()

    @Volatile private var playRequested = false
    @Volatile private var stopRequested = false
    @Volatile private var volume = 0.8f
    @Volatile private var currentTrack: Track? = null
    @Volatile private var currentHeaders: Map<String, String> = emptyMap()
    @Volatile private var baseFrame = 0L
    @Volatile private var frameRate = 44_100
    @Volatile private var totalFrames = 0L
    @Volatile private var ended = false

    private var worker: Thread? = null
    private var line: SourceDataLine? = null
    private var tempStreamFile: File? = null

    init {
        scope.launch {
            while (isActive) {
                val activeLine = line
                if (activeLine != null) {
                    val frames = baseFrame + activeLine.longFramePosition
                    val durationMs = if (totalFrames > 0) totalFrames * 1000L / frameRate else 0L
                    _state.update {
                        it.copy(
                            positionMs = frames * 1000L / frameRate,
                            isPlaying = playRequested && !ended,
                            durationMs = maxOf(it.durationMs, durationMs),
                        )
                    }
                }
                delay(120)
            }
        }
    }

    override fun setSource(track: Track, playWhenReady: Boolean, headers: Map<String, String>) {
        stopWorker()
        currentTrack = track
        currentHeaders = headers
        playRequested = playWhenReady
        ended = false
        baseFrame = 0L
        totalFrames = 0L
        _state.value = EngineState(
            isPlaying = playWhenReady,
            isBuffering = true,
            positionMs = 0L,
            durationMs = track.durationMs,
        )
        startWorker(0L)
    }

    override fun play() {
        if (currentTrack == null) return
        playRequested = true
        ended = false
        runCatching { line?.start() }
        synchronized(lock) { lock.notifyAll() }
        _state.update { it.copy(isPlaying = true) }
    }

    override fun pause() {
        playRequested = false
        runCatching { line?.stop() }
        _state.update { it.copy(isPlaying = false) }
    }

    override fun seekTo(positionMs: Long) {
        val track = currentTrack ?: return
        val resume = playRequested
        stopWorker()
        ended = false
        playRequested = resume
        baseFrame = positionMs * frameRate / 1000L
        _state.update { it.copy(positionMs = positionMs, isBuffering = true) }
        startWorker(positionMs)
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        line?.let { applyVolume(it, this.volume) }
    }

    override fun release() {
        stopWorker()
        currentTrack = null
        scope.cancel()
    }

    private fun startWorker(startMs: Long) {
        val track = currentTrack ?: return
        val headers = currentHeaders
        stopRequested = false
        val thread = thread(name = "cadence-audio", isDaemon = true) {
            runPlayback(track, headers, startMs)
        }
        worker = thread
    }

    private fun stopWorker() {
        stopRequested = true
        synchronized(lock) { lock.notifyAll() }
        runCatching { line?.stop() }
        val previous = worker
        worker = null
        if (previous != null && previous !== Thread.currentThread()) {
            runCatching { previous.join(800) }
        }
        runCatching { line?.close() }
        line = null
        runCatching { tempStreamFile?.takeIf { it.exists() }?.delete() }
        tempStreamFile = null
    }

    private fun resolveToFile(track: Track, headers: Map<String, String>): File {
        val source = track.playbackUri
        if (!source.startsWith("http://") && !source.startsWith("https://")) {
            return File(source)
        }
        val suffix = guessExt(source)
        val tmp = File.createTempFile("cadence-stream-", suffix)
        tempStreamFile = tmp
        downloadToFile(source, headers, tmp)
        return tmp
    }

    private fun downloadToFile(url: String, headers: Map<String, String>, out: File) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Cadence/1.0")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            conn.inputStream.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun guessExt(url: String): String = when {
        url.contains("mime=audio%2Fwebm") || url.contains(".webm") -> ".webm"
        url.contains("mime=audio%2Fmp4") || url.contains(".m4a") || url.contains(".mp4") -> ".m4a"
        url.contains(".ogg") -> ".ogg"
        url.contains(".opus") -> ".opus"
        url.contains(".mp3") -> ".mp3"
        else -> ".audio"
    }

    private fun runPlayback(track: Track, headers: Map<String, String>, startMs: Long) {
        var raw: AudioInputStream? = null
        var stream: AudioInputStream? = null
        var dataLine: SourceDataLine? = null
        try {
            val file = resolveToFile(track, headers)
            raw = AudioSystem.getAudioInputStream(file)
            val sourceFormat = raw.format

            fun pcmAt(sampleRate: Float) = AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                16,
                sourceFormat.channels,
                sourceFormat.channels * 2,
                sampleRate,
                false,
            )

            val decodedFormat = pcmAt(sourceFormat.sampleRate)
            val decoded = if (AudioSystem.isConversionSupported(decodedFormat, sourceFormat)) {
                AudioSystem.getAudioInputStream(decodedFormat, raw)
            } else {
                raw
            }
            raw = null

            // Devices do not always expose a line for the file's native rate, so
            // fall back to common output rates and let Java Sound resample.
            val candidates = listOf(decoded.format.sampleRate, 44_100f, 48_000f)
                .distinct()
            var outputLine: SourceDataLine? = null
            var outputFormat: AudioFormat? = null
            for (rate in candidates) {
                val candidate = pcmAt(rate)
                val opened = runCatching {
                    AudioSystem.getSourceDataLine(candidate).also {
                        it.open(candidate, 16_384)
                    }
                }.getOrNull()
                if (opened != null) {
                    outputLine = opened
                    outputFormat = candidate
                    break
                }
            }
            if (outputLine == null || outputFormat == null) {
                throw IllegalStateException("No audio output device is available")
            }

            stream = if (outputFormat == decoded.format) {
                decoded
            } else {
                AudioSystem.getAudioInputStream(outputFormat, decoded).also {
                    runCatching { decoded.close() }
                }
            }

            val format = stream.format
            frameRate = format.frameRate.toInt().coerceAtLeast(1)
            val frameSize = format.frameSize.coerceAtLeast(1)
            totalFrames = if (stream.frameLength > 0) stream.frameLength else 0L

            if (startMs > 0L) {
                baseFrame = startMs * frameRate / 1000L
                var toSkip = baseFrame * frameSize
                while (toSkip > 0) {
                    val skipped = stream.skip(toSkip)
                    if (skipped <= 0L) break
                    toSkip -= skipped
                }
            }

            dataLine = outputLine
            line = dataLine
            applyVolume(dataLine, volume)
            dataLine.start()

            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = playRequested,
                    durationMs = if (it.durationMs > 0) it.durationMs else totalFrames * 1000L / frameRate,
                )
            }

            val buffer = ByteArray(frameSize * 1024)
            while (!stopRequested) {
                synchronized(lock) {
                    while (!playRequested && !stopRequested) lock.wait(250)
                }
                if (stopRequested) break

                val read = stream.read(buffer)
                if (read <= 0) {
                    ended = true
                    break
                }

                var offset = 0
                while (offset < read && !stopRequested) {
                    if (!playRequested) {
                        synchronized(lock) {
                            while (!playRequested && !stopRequested) lock.wait(250)
                        }
                        if (stopRequested) break
                    }
                    offset += dataLine.write(buffer, offset, read - offset)
                }
            }

            if (ended) {
                runCatching { dataLine.drain() }
                _state.update {
                    it.copy(isPlaying = false, isBuffering = false, positionMs = it.durationMs)
                }
                _events.tryEmit(EngineEvent.TrackEnded)
            }
        } catch (t: Throwable) {
            if (!stopRequested) {
                val msg = t.message ?: "Could not play this track"
                val hint = if (msg.contains("Unsupported", true) || msg.contains("mark/reset", true)) {
                    "$msg (this audio format may not be supported on desktop yet)"
                } else msg
                _events.tryEmit(EngineEvent.Error(hint))
            }
        } finally {
            runCatching { dataLine?.stop() }
            runCatching { dataLine?.close() }
            runCatching { stream?.close() }
            runCatching { raw?.close() }
            if (line === dataLine) line = null
        }
    }

    private fun applyVolume(line: SourceDataLine, value: Float) {
        runCatching {
            val gain = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            gain.value = if (value <= 0.001f) {
                gain.minimum
            } else {
                (20.0 * log10(value.toDouble())).toFloat().coerceIn(gain.minimum, gain.maximum)
            }
        }
    }
}
