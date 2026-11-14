package app.cadence.platform

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import app.cadence.model.Track
import app.cadence.player.AndroidAudioEngine
import app.cadence.player.AudioEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import java.io.File

actual object Platform {

    actual val isDesktop: Boolean = false

    lateinit var appContext: Context
        private set

    var permissionLauncher: ((String) -> Unit)? = null

    private var pendingPermission: CompletableDeferred<Boolean>? = null

    private val prefs by lazy {
        appContext.getSharedPreferences("cadence_state", Context.MODE_PRIVATE)
    }

    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun deliverPermission(granted: Boolean) {
        pendingPermission?.complete(granted)
        pendingPermission = null
    }

    actual suspend fun ensureLibraryAccess(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val granted = ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true

        val launcher = permissionLauncher ?: return false
        val deferred = CompletableDeferred<Boolean>()
        pendingPermission = deferred
        return try {
            withContext(Dispatchers.Main) { launcher(permission) }
            deferred.await()
        } catch (t: Throwable) {
            pendingPermission = null
            false
        }
    }

    actual suspend fun importTracks(): List<Track> = withContext(Dispatchers.IO) {
        val context = appContext
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                    ?: cursor.getString(nameColumn)?.substringBeforeLast('.')
                    ?: "Untitled"
                val artist = cursor.getString(artistColumn)
                    .takeUnless { it == null || it == MediaStore.UNKNOWN_STRING }
                    .orEmpty()
                val album = cursor.getString(albumColumn)
                    .takeUnless { it == null || it == MediaStore.UNKNOWN_STRING }
                    .orEmpty()

                result += Track(
                    id = "m_$mediaId",
                    uri = Uri.withAppendedPath(collection, mediaId.toString()).toString(),
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = cursor.getLong(durationColumn),
                    trackNumber = cursor.getInt(trackColumn) % 1000,
                    year = cursor.getInt(yearColumn),
                    addedAt = cursor.getLong(addedColumn) * 1000L,
                )
            }
        }
        result
    }

    actual suspend fun loadArtwork(track: Track): ByteArray? = withContext(Dispatchers.IO) {
        val artDirectory = File(appContext.cacheDir, "artwork").apply { mkdirs() }
        val cached = File(artDirectory, "${track.id}.img")
        if (cached.isFile && cached.length() > 0L) return@withContext cached.readBytes()

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, Uri.parse(track.uri))
            val bytes = retriever.embeddedPicture ?: return@withContext null
            runCatching { cached.writeBytes(bytes) }
            bytes
        } catch (t: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    actual fun readState(key: String): String? = runCatching {
        prefs.getString(key, null)
    }.getOrNull()

    actual fun writeState(key: String, value: String) {
        runCatching { prefs.edit().putString(key, value).apply() }
    }

    actual fun downloadsDir(): String = File(appContext.filesDir, "downloads").apply { mkdirs() }.absolutePath

    actual fun writeFile(path: String, bytes: ByteArray): Boolean = runCatching {
        val f = File(path)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
        true
    }.getOrDefault(false)

    actual fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }.getOrDefault(false)

    actual fun fileSize(path: String): Long = runCatching { File(path).length() }.getOrDefault(0L)

    actual fun fileExists(path: String): Boolean = runCatching { File(path).isFile }.getOrDefault(false)

    var signInLauncher: (() -> Unit)? = null
    private var pendingSignIn: kotlinx.coroutines.CompletableDeferred<String?>? = null

    actual suspend fun signInYouTube(): String? {
        val launcher = signInLauncher ?: return null
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        pendingSignIn = deferred
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { launcher() }
            deferred.await()
        } catch (t: Throwable) {
            pendingSignIn = null
            null
        }
    }

    fun deliverSignIn(cookie: String?) {
        pendingSignIn?.complete(cookie?.takeIf { it.isNotBlank() })
        pendingSignIn = null
    }

    actual fun nowMs(): Long = System.currentTimeMillis()
}

actual fun ByteArray.toImageBitmap(): ImageBitmap? = runCatching {
    android.graphics.BitmapFactory.decodeByteArray(this, 0, size)?.asImageBitmap()
}.getOrNull()

actual fun createAudioEngine(): AudioEngine = AndroidAudioEngine(Platform.appContext)
