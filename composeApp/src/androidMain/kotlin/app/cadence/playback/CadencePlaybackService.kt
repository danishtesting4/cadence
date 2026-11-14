package app.cadence.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.net.Uri

/**
 * Per-stream request headers shared between the playback engine (which resolves
 * streams and knows the required headers) and the service-owned ExoPlayer.
 */
object StreamHeaders {
    private val byUrl = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    fun set(url: String, headers: Map<String, String>) {
        if (headers.isEmpty()) byUrl.remove(url) else byUrl[url] = headers
    }

    fun forUrl(url: String): Map<String, String> = byUrl[url].orEmpty()
}

private class HeaderAwareDataSource : DataSource {

    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val headers = StreamHeaders.forUrl(dataSpec.uri.toString())
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)
            .apply { if (headers.isNotEmpty()) setDefaultRequestProperties(headers) }
            .createDataSource()
        delegate = http
        return http.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders.orEmpty()

    override fun close() {
        runCatching { delegate?.close() }
        delegate = null
    }

    private companion object {
        const val USER_AGENT = "Cadence/1.0 (Android)"
    }
}

private class HeaderAwareDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = HeaderAwareDataSource()
}

/**
 * Foreground media service that owns the ExoPlayer instance and exposes it as a
 * [MediaSession]. This is what enables background playback, the notification and
 * lockscreen/headset controls.
 */
class CadencePlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        val built = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(this, HeaderAwareDataSourceFactory()),
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player = built
        mediaSession = MediaSession.Builder(this, built).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 || p.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}