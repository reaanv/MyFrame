package com.myframe.tv

import com.myframe.tv.core.StickLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickLimitsTest {
    @Test fun coverImagesCannotExceedThePixelBudgetOnUnusualAspectRatios() {
        listOf(4000 to 6000, 20000 to 1000, 1000 to 20000, 3840 to 2160).forEach { (w, h) ->
            val scale = StickLimits.pixelBudgetScale(w, h)
            assertTrue(w.toDouble() * h * scale * scale <= StickLimits.BITMAP_PIXELS + 1.0)
        }
        assertEquals(1f, StickLimits.pixelBudgetScale(640, 480))
    }
    @Test fun fourKOutputKeepsAFullHdBitmapBudget() {
        assertEquals(1920 to 1080, StickLimits.decodeSize(3840, 2160))
    }

    @Test fun smallerFrameOpeningsAreNotUpscaled() {
        assertEquals(960 to 540, StickLimits.decodeSize(960, 540))
        assertEquals(400 to 800, StickLimits.decodeSize(400, 800))
    }

    @Test fun portraitAndPanoramaRequestsKeepTheirShape() {
        assertEquals(540 to 1080, StickLimits.decodeSize(2000, 4000))
        assertEquals(1920 to 480, StickLimits.decodeSize(8000, 2000))
    }

    @Test fun malformedAndVeryLargeSizesAlwaysStayDrawable() {
        listOf(0 to 0, -10 to 1080, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE).forEach { (w, h) ->
            val (dw, dh) = StickLimits.decodeSize(w, h)
            assertTrue(dw in 1..1920 && dh in 1..1080)
        }
    }
}
