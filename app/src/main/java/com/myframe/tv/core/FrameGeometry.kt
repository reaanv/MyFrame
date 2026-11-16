package com.myframe.tv.core

import kotlin.math.max
import kotlin.math.min

data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    fun inset(d: Float): Box = Box(left + d, top + d, right - d, bottom - d)
    fun scaledAboutCenter(s: Float): Box {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        return Box(cx - width * s / 2f, cy - height * s / 2f, cx + width * s / 2f, cy + height * s / 2f)
    }
}

/**
 * Where everything goes for one framed picture.
 *  - [frameInner]: the area inside the frame moulding.
 *  - [opening]: the window through which the picture is seen.
 *  - [image]: where the whole picture is drawn. It can be larger than [opening] (it is clipped) but its
 *    aspect ratio always equals the source picture, so nothing is ever stretched.
 */
data class FrameLayout(val screen: Box, val frameInner: Box, val opening: Box, val image: Box)

object FrameGeometry {
    fun compute(
        screenW: Float,
        screenH: Float,
        imageW: Int,
        imageH: Int,
        frame: FrameConfig,
        scaleMode: ScaleMode,
        alignX: Float = 0.5f,
        alignY: Float = 0.5f,
        zoom: Float = 1f,
        /** Screen pixels per image pixel at "original size"; 1 means one image pixel per screen pixel. */
        nativeScale: Float = 1f,
    ): FrameLayout {
        val screen = Box(0f, 0f, screenW, screenH)
        val t = (frame.frameThickness.coerceIn(0f, FrameConfig.MAX_FRAME) * screenH)
        val m = (frame.mountThickness.coerceIn(0f, FrameConfig.MAX_MOUNT) * screenH)
        val frameInner = screen.inset(t)
        val available = frameInner.inset(m).scaledAboutCenter(frame.imageScale.coerceIn(0.5f, 1f))
        val iw = max(1, imageW).toFloat()
        val ih = max(1, imageH).toFloat()
        val fit = min(available.width / iw, available.height / ih)

        return when (scaleMode) {
            ScaleMode.FILL -> {
                val s = max(available.width / iw, available.height / ih) * zoom.coerceIn(1f, 3f)
                val w = iw * s
                val h = ih * s
                val left = available.left - (w - available.width) * alignX.coerceIn(0f, 1f)
                val top = available.top - (h - available.height) * alignY.coerceIn(0f, 1f)
                FrameLayout(screen, frameInner, available, Box(left, top, left + w, top + h))
            }
            ScaleMode.FIT, ScaleMode.CONTAIN -> {
                val s = if (scaleMode == ScaleMode.CONTAIN) min(fit, nativeScale) else fit
                val w = iw * s
                val h = ih * s
                val left = available.left + (available.width - w) / 2f
                val top = available.top + (available.height - h) / 2f
                val box = Box(left, top, left + w, top + h)
                FrameLayout(screen, frameInner, box, box)
            }
        }
    }

    /**
     * The pixel size a picture should be decoded at: exactly as large as it is drawn, no larger.
     * A portrait painting inside a mount covers a fraction of the screen, so decoding it screen-sized
     * (as a "cover" decode would) can waste five times the memory. [extra] leaves room for the slow zoom.
     * The result never exceeds two screens' worth of pixels, whatever zoom the user picked.
     */
    fun decodeSize(
        screenW: Int, screenH: Int, imageW: Int, imageH: Int, frame: FrameConfig, scaleMode: ScaleMode,
        alignX: Float = 0.5f, alignY: Float = 0.5f, zoom: Float = 1f, extra: Float = 1f,
    ): Pair<Int, Int> {
        if (imageW <= 0 || imageH <= 0 || screenW <= 0 || screenH <= 0) return max(1, screenW) to max(1, screenH)
        val image = compute(screenW.toFloat(), screenH.toFloat(), imageW, imageH, frame, scaleMode, alignX, alignY, zoom).image
        var w = image.width * extra
        var h = image.height * extra
        val budget = 2f * screenW * screenH
        if (w * h > budget) {
            val k = kotlin.math.sqrt(budget / (w * h))
            w *= k
            h *= k
        }
        return max(1, kotlin.math.ceil(w).toInt()) to max(1, kotlin.math.ceil(h).toInt())
    }
}
