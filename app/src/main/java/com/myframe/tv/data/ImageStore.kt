package com.myframe.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.myframe.tv.core.StickLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes pictures at the size they are needed and caches them.
 * Nothing is ever decoded at full resolution unless the screen actually needs it,
 * which is what keeps a 2 GB TV box comfortable with 12-megapixel phone photos.
 */
class ImageStore(private val context: Context) {
    // Two decodes at a time: enough to preload the next slide without memory spikes.
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(2)
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    // Room for three screen-sized pictures: the one showing, the one before it and the next one, preloaded.
    // More than that only takes memory away from the music app playing alongside.
    private val full = object : LruCache<String, Bitmap>(min(maxKb / 4, StickLimits.FULL_CACHE_KIB)) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
    private val small = object : LruCache<String, Bitmap>(min(maxKb / 8, StickLimits.THUMB_CACHE_KIB)) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount / 1024
    }
    private val thumbDir = File(context.cacheDir, "thumbs")

    fun open(media: Media): InputStream =
        if (media.isAsset) context.assets.open(media.uri.removePrefix("asset:")) else FileInputStream(media.uri)

    private fun key(media: Media) = "${media.id}_${media.sha256.takeLast(12).filter { it.isLetterOrDigit() }}"

    /** A bitmap large enough to cover a [maxW] x [maxH] area. Null when the file is missing or corrupt. */
    suspend fun full(media: Media, maxW: Int, maxH: Int, cover: Boolean = true): Bitmap? = withContext(decodeDispatcher) {
        val (w, h) = StickLimits.decodeSize(maxW, maxH)
        val k = "${key(media)}@${w}x$h${if (cover) "" else "f"}"
        full.get(k) ?: decode({ open(media) }, w, h, cover, limitPixels = true)?.also { full.put(k, it) }
    }

    suspend fun thumb(media: Media): Bitmap? = withContext(decodeDispatcher) {
        val k = key(media)
        small.get(k)?.let { return@withContext it }
        thumbDir.mkdirs()
        val file = File(thumbDir, "$k.jpg")
        val cached = if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() else null
        val bmp = cached ?: decode({ open(media) }, THUMB, THUMB, cover = false)?.also { b ->
            runCatching { file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
        }
        bmp?.also { small.put(k, it) }
    }

    /** A tiny, heavily blurred copy used as a soft backdrop behind pictures that do not fill the screen. */
    suspend fun blurred(media: Media): Bitmap? {
        val k = "blur_${key(media)}"
        small.get(k)?.let { return it }
        val src = thumb(media) ?: return null
        return withContext(decodeDispatcher) {
            val w = 40
            val h = max(1, (w * src.height.toFloat() / src.width).toInt())
            val tiny = Bitmap.createScaledBitmap(src, w, h, true)
            boxBlur(tiny, 2).also { small.put(k, it) }
        }
    }

    fun forget(media: Media) {
        File(thumbDir, "${key(media)}.jpg").delete()
        small.remove(key(media))
        small.remove("blur_${key(media)}")
    }

    fun clearThumbnails(): Long {
        val freed = thumbDir.listFiles()?.sumOf { it.length() } ?: 0L
        thumbDir.deleteRecursively()
        small.evictAll()
        return freed
    }

    fun trimMemory() {
        full.evictAll()
        small.trimToSize(small.maxSize() / 2)
    }

    fun cacheBytes(): Long = thumbDir.listFiles()?.sumOf { it.length() } ?: 0L

    companion object {
        const val THUMB = 400
        /** Larger than any texture a low-end TV GPU is guaranteed to accept would fail to draw. */
        private const val MAX_TEXTURE = 4096

        /**
         * Two-pass decode: read the size, pick a power-of-two sample, then scale exactly and
         * apply the EXIF orientation. [cover] sizes the result to cover the box, otherwise to fit inside it.
         */
        fun decode(open: () -> InputStream, maxW: Int, maxH: Int, cover: Boolean, limitPixels: Boolean = false): Bitmap? {
            return try {
                decodeOnce(open, maxW, maxH, cover, extraSample = 1, limitPixels = limitPixels)
            } catch (e: OutOfMemoryError) {
                runCatching { decodeOnce(open, maxW / 2, maxH / 2, cover, extraSample = 2, limitPixels = limitPixels) }.getOrNull()
            } catch (e: Exception) {
                null
            }
        }

        private fun decodeOnce(open: () -> InputStream, maxW: Int, maxH: Int, cover: Boolean, extraSample: Int, limitPixels: Boolean): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val orientation = runCatching {
                open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val swapped = orientation in SWAPPING
            val srcW = if (swapped) bounds.outHeight else bounds.outWidth
            val srcH = if (swapped) bounds.outWidth else bounds.outHeight

            val sx = maxW.toFloat() / srcW
            val sy = maxH.toFloat() / srcH
            var scale = min(1f, if (cover) max(sx, sy) else min(sx, sy))
            scale = min(scale, min(MAX_TEXTURE.toFloat() / srcW, MAX_TEXTURE.toFloat() / srcH))
            if (limitPixels) scale = min(scale, StickLimits.pixelBudgetScale(srcW, srcH))

            var sample = 1
            while (sample * 2 <= 1f / scale) sample *= 2
            sample *= extraSample

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = open().use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val matrix = Matrix()
            val residual = (scale * sample).coerceAtMost(1f)
            if (residual < 0.98f) matrix.postScale(residual, residual)
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(-90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            if (matrix.isIdentity) return raw
            return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }

        private val SWAPPING = setOf(
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE,
        )

        /** Small separable box blur; only ever run on thumbnails of a few thousand pixels. */
        fun boxBlur(src: Bitmap, radius: Int): Bitmap {
            val w = src.width
            val h = src.height
            val px = IntArray(w * h)
            src.getPixels(px, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            repeat(2) {
                blurPass(px, tmp, w, h, radius, horizontal = true)
                blurPass(tmp, px, w, h, radius, horizontal = false)
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        }

        private fun blurPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val outer = if (horizontal) h else w
            val inner = if (horizontal) w else h
            for (o in 0 until outer) {
                for (i in 0 until inner) {
                    var rs = 0; var gs = 0; var bs = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, inner - 1)
                        val c = if (horizontal) src[o * w + j] else src[j * w + o]
                        rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
                    }
                    val v = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                    if (horizontal) dst[o * w + i] = v else dst[i * w + o] = v
                }
            }
        }
    }
}
