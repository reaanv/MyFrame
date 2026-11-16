package com.myframe.tv.core

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Leave room for Android and a music app on a 2 GB RAM / 8 GB storage TV stick. */
object StickLimits {
    const val FULL_CACHE_KIB = 24 * 1024
    const val THUMB_CACHE_KIB = 8 * 1024
    const val IMPORT_EDGE = 1920
    const val STORAGE_RESERVE_BYTES = 1024L * 1024 * 1024
    const val BITMAP_PIXELS = 1920 * 1080

    fun pixelBudgetScale(width: Int, height: Int): Float =
        min(1.0, sqrt(BITMAP_PIXELS.toDouble() / (width.coerceAtLeast(1).toDouble() * height.coerceAtLeast(1)))).toFloat()

    // A 4K surface costs four times the bitmap memory. Keep a Full HD budget while
    // preserving the requested aspect ratio, including on a stick set to 4K output.
    fun decodeSize(width: Int, height: Int): Pair<Int, Int> {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val scale = min(1.0, min(1920.0 / w, 1080.0 / h))
        return (w * scale).roundToInt().coerceAtLeast(1) to
            (h * scale).roundToInt().coerceAtLeast(1)
    }
}
