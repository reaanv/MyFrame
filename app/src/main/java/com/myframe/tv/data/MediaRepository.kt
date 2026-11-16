package com.myframe.tv.data

import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import androidx.exifinterface.media.ExifInterface
import com.myframe.tv.core.AppJson
import com.myframe.tv.core.AppSettings
import com.myframe.tv.core.FrameConfig
import com.myframe.tv.core.FrameStyle
import com.myframe.tv.core.MediaKind
import com.myframe.tv.core.MediaSource
import com.myframe.tv.core.PlayOrder
import com.myframe.tv.core.PlaybackConfig
import com.myframe.tv.core.PlaylistBuilder
import com.myframe.tv.core.ScaleMode
import com.myframe.tv.core.Source
import com.myframe.tv.core.SourceType
import com.myframe.tv.core.StickLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max

/** Persistent app-wide settings, held in memory and written through on every change. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val flow: StateFlow<AppSettings> = state
    val value: AppSettings get() = state.value

    private fun load(): AppSettings = prefs.getString("json", null)
        ?.let { runCatching { AppJson.decodeFromString(AppSettings.serializer(), it) }.getOrNull() }
        ?: AppSettings()

    fun update(block: (AppSettings) -> AppSettings) {
        val next = block(state.value)
        state.value = next
        prefs.edit().putString("json", AppJson.encodeToString(AppSettings.serializer(), next)).apply()
    }
}

@Serializable
private data class ArtEntry(
    val file: String, val title: String, val artist: String = "", val date: String = "",
    val width: Int, val height: Int, val credit: String = "", val license: String = "", val source: String = "",
)

@Serializable
private data class ArtCatalog(val version: Int = 1, val items: List<ArtEntry> = emptyList())

data class StorageInfo(val freeBytes: Long, val totalBytes: Long, val photoBytes: Long, val cacheBytes: Long, val photos: Int, val art: Int)

class MediaRepository(
    private val context: Context,
    val db: AppDatabase,
    val images: ImageStore,
) : MediaSource<Media> {
    val media: MediaDao = db.media()
    val albums: AlbumDao = db.albums()
    val presets: PresetDao = db.presets()

    /** Idempotent first-run setup: bundled artwork, the built-in modes and the sample presets. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        seedArt()
        seedDefaults()
    }

    private suspend fun seedArt() {
        val text = runCatching { context.assets.open("art/catalog.json").bufferedReader().use { it.readText() } }.getOrNull() ?: return
        val catalog = runCatching { AppJson.decodeFromString(ArtCatalog.serializer(), text) }.getOrNull() ?: return
        val prefs = context.getSharedPreferences("seed", Context.MODE_PRIVATE)
        if (prefs.getInt("artVersion", 0) == catalog.version && media.count(MediaKind.ART) > 0) return
        val now = System.currentTimeMillis()
        for (a in catalog.items) {
            // sha256 is unique, so re-running after an update only adds the new works.
            media.insert(
                Media(
                    kind = MediaKind.ART, title = a.title, artist = a.artist, dateText = a.date,
                    credit = listOf(a.credit, a.license).filter { it.isNotBlank() }.joinToString(" · "),
                    sourceUrl = a.source, uri = "asset:art/${a.file}", width = a.width, height = a.height,
                    addedAt = now, sha256 = "asset:${a.file}", origin = "bundled", fileName = a.file,
                )
            )
        }
        // Works dropped from the bundle would otherwise linger as rows pointing at a missing asset.
        val current = catalog.items.map { "asset:art/${it.file}" }.toSet()
        media.delete(media.bundled().filter { it.uri !in current }.map { it.id })
        prefs.edit().putInt("artVersion", catalog.version).apply()
    }

    private suspend fun seedDefaults() {
        val now = System.currentTimeMillis()
        if (albums.bySystem(SELECTION) == null) {
            albums.insert(Album(name = "My Selection", systemKey = SELECTION, sortOrder = -1, createdAt = now))
        }
        if (presets.byBuiltin(BUILTIN_ART) != null) return
        val family = albums.insert(Album(name = "Family", createdAt = now))
        val travel = albums.insert(Album(name = "Travel", createdAt = now))
        fun preset(name: String, order: Int, config: PlaybackConfig, builtin: String = "") =
            Preset(name = name, builtin = builtin, sortOrder = order, configJson = AppJson.encodeToString(PlaybackConfig.serializer(), config))

        presets.insert(preset("Art Mode", 0, PlaybackConfig(sources = listOf(Source(SourceType.ALL_ART)), durationSec = 120, frame = FrameConfig.of(FrameStyle.MUSEUM_MOUNT)), BUILTIN_ART))
        presets.insert(preset("My Photos", 1, PlaybackConfig(sources = listOf(Source(SourceType.ALL_PHOTOS)), durationSec = 20, frame = FrameConfig.of(FrameStyle.CLASSIC_WHITE)), BUILTIN_PHOTOS))
        presets.insert(preset("Living Room Art", 10, PlaybackConfig(sources = listOf(Source(SourceType.ALL_ART)), order = PlayOrder.SHUFFLE, durationSec = 600, showMetadata = true, frame = FrameConfig.of(FrameStyle.WALNUT))))
        presets.insert(preset("Family Memories", 11, PlaybackConfig(sources = listOf(Source(SourceType.COLLECTION, family), Source(SourceType.FAV_PHOTOS)), durationSec = 15, kenBurns = true, frame = FrameConfig.of(FrameStyle.WARM_OAK))))
        presets.insert(preset("Travel Photos", 12, PlaybackConfig(sources = listOf(Source(SourceType.COLLECTION, travel)), order = PlayOrder.SHUFFLE, durationSec = 12, kenBurns = true, scaleMode = ScaleMode.FIT, blurBackground = true, showDate = true, frame = FrameConfig.of(FrameStyle.BORDERLESS))))
        presets.insert(preset("Mixed Gallery", 13, PlaybackConfig(sources = listOf(Source(SourceType.ALL_ART), Source(SourceType.ALL_PHOTOS)), order = PlayOrder.SHUFFLE, durationSec = 45, frame = FrameConfig.of(FrameStyle.MODERN_BLACK))))
    }

    override suspend fun art(favOnly: Boolean): List<Media> = media.list(MediaKind.ART, favOnly)
    override suspend fun photos(favOnly: Boolean): List<Media> = media.list(MediaKind.PHOTO, favOnly)
    override suspend fun collection(id: Long): List<Media> = albums.items(id).filter { !it.hidden }

    suspend fun playlist(sources: List<Source>): List<Media> = PlaylistBuilder.build(sources, this) { it.id }

    suspend fun selectionId(): Long = albums.bySystem(SELECTION)?.id ?: 0

    suspend fun updateConfig(presetId: Long, block: (PlaybackConfig) -> PlaybackConfig) {
        val p = presets.byId(presetId) ?: return
        presets.update(p.with(block(p.config)))
    }

    /** Removes imported photos from storage. Bundled artwork cannot be deleted, only hidden. */
    suspend fun deleteMedia(ids: List<Long>) = withContext(Dispatchers.IO) {
        val items = media.byIds(ids)
        val removable = items.filter { !it.isAsset }
        removable.forEach {
            images.forget(it)
            runCatching { File(it.uri).delete() }
        }
        media.delete(removable.map { it.id })
        items.filter { it.isAsset }.forEach { media.setHidden(it.id, true) }
    }

    suspend fun storage(): StorageInfo = withContext(Dispatchers.IO) {
        val stat = StatFs(context.filesDir.path)
        StorageInfo(
            freeBytes = stat.availableBytes, totalBytes = stat.totalBytes, photoBytes = media.photoBytes(),
            cacheBytes = images.cacheBytes(), photos = media.count(MediaKind.PHOTO), art = media.count(MediaKind.ART),
        )
    }

    companion object {
        const val SELECTION = "selection"
        const val BUILTIN_ART = "art"
        const val BUILTIN_PHOTOS = "photos"
    }
}

sealed interface ImportResult {
    data class Added(val media: Media) : ImportResult
    data class Duplicate(val media: Media) : ImportResult
    data class Rejected(val reason: Reason, val detail: String = "") : ImportResult
    enum class Reason { TOO_LARGE, UNSUPPORTED, NO_SPACE, CORRUPT, IO }
}

/**
 * The single way pictures enter the library, whatever the source (phone upload, device storage,
 * Google Photos, the adb inbox). Files are named by their content hash, never by a client-supplied name.
 */
class MediaImporter(
    private val context: Context,
    private val repo: MediaRepository,
    private val settings: SettingsStore,
) {
    private val mediaDir = File(context.filesDir, "media")
    private val tmpDir = File(context.cacheDir, "import")
    private val importGate = Mutex()

    suspend fun import(input: InputStream, displayName: String, origin: String, albumId: Long = 0): ImportResult =
        importGate.withLock { withContext(Dispatchers.IO) {
            tmpDir.mkdirs()
            val tmp = File.createTempFile("import", ".tmp", tmpDir)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                input.use { ins ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_BYTES) return@withContext ImportResult.Rejected(ImportResult.Reason.TOO_LARGE)
                            if (freeBytes() - n < RESERVE_BYTES) return@withContext ImportResult.Rejected(ImportResult.Reason.NO_SPACE)
                            digest.update(buf, 0, n)
                            out.write(buf, 0, n)
                        }
                    }
                }
                val sha = digest.digest().joinToString("") { "%02x".format(it) }
                store(tmp, sha, displayName, origin, albumId)
            } catch (e: IOException) {
                ImportResult.Rejected(ImportResult.Reason.IO, e.message ?: "")
            } finally {
                tmp.delete()
            }
        } }

    private suspend fun store(tmp: File, sha: String, rawName: String, origin: String, albumId: Long): ImportResult {
        repo.media.bySha(sha)?.let { existing ->
            if (albumId > 0) repo.albums.addItems(albumId, listOf(existing.id))
            return ImportResult.Duplicate(existing)
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ImportResult.Rejected(ImportResult.Reason.CORRUPT)
        val ext = SUPPORTED[bounds.outMimeType] ?: return ImportResult.Rejected(ImportResult.Reason.UNSUPPORTED, bounds.outMimeType ?: "")
        if (freeBytes() < tmp.length() + RESERVE_BYTES) return ImportResult.Rejected(ImportResult.Reason.NO_SPACE)

        val exif = runCatching { ExifInterface(tmp.path) }.getOrNull()
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        val takenAt = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { parseExifDate(it) } ?: 0L

        mediaDir.mkdirs()
        val dest: File
        val w: Int
        val h: Int
        if (settings.value.optimizeImports && max(bounds.outWidth, bounds.outHeight) > MAX_EDGE) {
            // A 1080p screen never needs more; this turns a 6 MB phone photo into well under 1 MB.
            val bmp = ImageStore.decode({ tmp.inputStream() }, MAX_EDGE, MAX_EDGE, cover = false)
                ?: return ImportResult.Rejected(ImportResult.Reason.CORRUPT)
            dest = File(mediaDir, "$sha.jpg")
            dest.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
            w = bmp.width
            h = bmp.height
        } else {
            dest = File(mediaDir, "$sha.$ext")
            tmp.copyTo(dest, overwrite = true)
            val swapped = orientation in ROTATED
            w = if (swapped) bounds.outHeight else bounds.outWidth
            h = if (swapped) bounds.outWidth else bounds.outHeight
        }
        val name = cleanName(rawName)
        val item = Media(
            kind = MediaKind.PHOTO, title = name.substringBeforeLast('.').ifBlank { "Photo" }, uri = dest.absolutePath,
            width = w, height = h, takenAt = takenAt, addedAt = System.currentTimeMillis(), sha256 = sha,
            sizeBytes = dest.length(), origin = origin, fileName = name,
        )
        val id = repo.media.insert(item)
        if (id == -1L) {
            val existing = repo.media.bySha(sha) ?: return ImportResult.Rejected(ImportResult.Reason.IO)
            if (albumId > 0) repo.albums.addItems(albumId, listOf(existing.id))
            return ImportResult.Duplicate(existing)
        }
        if (albumId > 0) repo.albums.addItems(albumId, listOf(id))
        return ImportResult.Added(item.copy(id = id))
    }

    /** Imports anything dropped into the app's external "inbox" folder (handy with adb push). */
    suspend fun importInbox(): Int = withContext(Dispatchers.IO) {
        val inbox = context.getExternalFilesDir("inbox") ?: return@withContext 0
        var added = 0
        inbox.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { f ->
            val result = runCatching { import(f.inputStream(), f.name, "inbox") }.getOrNull()
            if (result is ImportResult.Added) added++
            if (result != null && result !is ImportResult.Rejected) f.delete()
        }
        added
    }

    private fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    companion object {
        const val MAX_BYTES = 40L * 1024 * 1024
        const val MAX_EDGE = StickLimits.IMPORT_EDGE
        /** Leave Android, app updates and streamed music enough room on an 8 GB stick. */
        const val RESERVE_BYTES = StickLimits.STORAGE_RESERVE_BYTES
        val SUPPORTED = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp")
        private val ROTATED = setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )

        /** Display names are only ever shown, never used as paths; still strip anything path-like. */
        fun cleanName(raw: String): String =
            raw.substringAfterLast('/').substringAfterLast('\\').filter { !it.isISOControl() }.trim().take(120)

        fun parseExifDate(s: String): Long? =
            runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(s)?.time }.getOrNull()
    }
}
