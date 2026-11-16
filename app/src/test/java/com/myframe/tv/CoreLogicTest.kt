package com.myframe.tv

import com.myframe.tv.core.AppJson
import com.myframe.tv.core.AppSettings
import com.myframe.tv.core.FrameConfig
import com.myframe.tv.core.FrameGeometry
import com.myframe.tv.core.FrameStyle
import com.myframe.tv.core.MediaSource
import com.myframe.tv.core.PlayOrder
import com.myframe.tv.core.PlaybackConfig
import com.myframe.tv.core.PlaylistBuilder
import com.myframe.tv.core.RepeatMode
import com.myframe.tv.core.ScaleMode
import com.myframe.tv.core.SlideshowEngine
import com.myframe.tv.core.Source
import com.myframe.tv.core.SourceType
import com.myframe.tv.core.StartupMode
import com.myframe.tv.core.ThemeMode
import com.myframe.tv.core.formatDuration
import com.myframe.tv.core.slideSeconds
import com.myframe.tv.data.MediaImporter
import com.myframe.tv.server.NetGuard
import com.myframe.tv.server.Pairing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SlideshowEngineTest {
    private val items = listOf(1L, 2L, 3L, 4L)

    @Test fun sequentialWrapsWhenRepeatingAll() {
        val e = SlideshowEngine(items)
        assertEquals(1L, e.current)
        repeat(3) { assertTrue(e.next(auto = true)) }
        assertEquals(4L, e.current)
        assertTrue(e.next(auto = true))
        assertEquals(1L, e.current)
    }

    @Test fun startsOnTheRequestedItem() {
        assertEquals(3L, SlideshowEngine(items, start = 3L).current)
        assertEquals(3L, SlideshowEngine(items, PlayOrder.SHUFFLE, random = Random(1), start = 3L).current)
    }

    @Test fun previousWrapsToTheEnd() {
        val e = SlideshowEngine(items)
        assertTrue(e.previous())
        assertEquals(4L, e.current)
    }

    @Test fun repeatOneHoldsOnTimerButRemoteStillMoves() {
        val e = SlideshowEngine(items, repeat = RepeatMode.ONE)
        assertFalse(e.next(auto = true))
        assertEquals(1L, e.current)
        assertTrue(e.next())
        assertEquals(2L, e.current)
    }

    @Test fun playOnceStopsAtTheEnd() {
        val e = SlideshowEngine(items, repeat = RepeatMode.OFF)
        repeat(3) { e.next(auto = true) }
        assertFalse(e.finished)
        assertFalse(e.next(auto = true))
        assertTrue(e.finished)
        assertEquals(4L, e.current)
        assertNull(e.peekNext())
        assertTrue(e.previous())
        assertFalse(e.finished)
    }

    @Test fun shuffleShowsEveryItemOncePerCycleAndNeverRepeatsAcrossCycles() {
        val e = SlideshowEngine(items, PlayOrder.SHUFFLE, random = Random(42))
        repeat(20) {
            val cycle = mutableListOf(e.current!!)
            repeat(items.size - 1) { e.next(auto = true); cycle += e.current!! }
            assertEquals(items.toSet(), cycle.toSet())
            val last = e.current
            e.next(auto = true)
            assertNotEquals(last, e.current)
        }
    }

    @Test fun duplicatesAreDroppedAndEmptyIsSafe() {
        assertEquals(2, SlideshowEngine(listOf(1L, 1L, 2L)).size)
        val empty = SlideshowEngine(emptyList<Long>())
        assertTrue(empty.isEmpty)
        assertNull(empty.current)
        assertFalse(empty.next())
        assertFalse(empty.previous())
    }

    @Test fun singleItemNeverReportsAChange() {
        val e = SlideshowEngine(listOf(7L))
        assertFalse(e.next(auto = true))
        assertFalse(e.previous())
        assertEquals(7L, e.current)
    }

    @Test fun updateKeepsTheCurrentItemWhenItSurvives() {
        val e = SlideshowEngine(items, start = 3L)
        e.update(listOf(5L, 3L, 1L))
        assertEquals(3L, e.current)
        e.update(listOf(9L))
        assertEquals(9L, e.current)
        e.update(emptyList())
        assertNull(e.current)
    }

    @Test fun jumpToSelectsAnItem() {
        val e = SlideshowEngine(items)
        assertTrue(e.jumpTo(4L))
        assertEquals(4L, e.current)
        assertFalse(e.jumpTo(99L))
    }
}

class PlaylistBuilderTest {
    private val source = object : MediaSource<Long> {
        override suspend fun art(favOnly: Boolean) = if (favOnly) listOf(2L) else listOf(1L, 2L, 3L)
        override suspend fun photos(favOnly: Boolean) = if (favOnly) listOf(11L) else listOf(10L, 11L)
        override suspend fun collection(id: Long) = if (id == 5L) listOf(11L, 3L, 20L) else emptyList()
    }

    private fun build(vararg sources: Source) = runBlocking { PlaylistBuilder.build(sources.toList(), source) { it } }

    @Test fun artOnlyAndPhotosOnly() {
        assertEquals(listOf(1L, 2L, 3L), build(Source(SourceType.ALL_ART)))
        assertEquals(listOf(10L, 11L), build(Source(SourceType.ALL_PHOTOS)))
    }

    @Test fun favouritesOfBothKindsTogether() {
        assertEquals(listOf(2L, 11L), build(Source(SourceType.FAV_ART), Source(SourceType.FAV_PHOTOS)))
    }

    @Test fun mixedSourcesKeepOrderAndPlayEachItemOnce() {
        assertEquals(listOf(11L, 3L, 20L, 1L, 2L), build(Source(SourceType.COLLECTION, 5), Source(SourceType.ALL_ART)))
    }

    @Test fun unknownCollectionAndNoSourcesAreEmpty() {
        assertTrue(build(Source(SourceType.COLLECTION, 99)).isEmpty())
        assertTrue(build().isEmpty())
    }
}

class FrameGeometryTest {
    private fun aspect(w: Float, h: Float) = w / h

    @Test fun fitNeverStretchesAndStaysInsideTheMount() {
        for ((iw, ih) in listOf(4000 to 3000, 3000 to 4000, 320 to 240, 6000 to 1000, 1000 to 1000)) {
            for (style in FrameStyle.entries) {
                val l = FrameGeometry.compute(1920f, 1080f, iw, ih, FrameConfig.of(style), ScaleMode.FIT)
                assertEquals("$style $iw x $ih", aspect(iw.toFloat(), ih.toFloat()), aspect(l.image.width, l.image.height), 0.01f)
                assertTrue(l.opening.left >= l.frameInner.left - 0.5f && l.opening.right <= l.frameInner.right + 0.5f)
                assertTrue(l.opening.top >= l.frameInner.top - 0.5f && l.opening.bottom <= l.frameInner.bottom + 0.5f)
            }
        }
    }

    @Test fun fillCoversTheOpeningWithoutStretching() {
        val l = FrameGeometry.compute(1920f, 1080f, 3000, 4000, FrameConfig.of(FrameStyle.MODERN_BLACK), ScaleMode.FILL)
        assertEquals(0.75f, aspect(l.image.width, l.image.height), 0.01f)
        assertTrue(l.image.left <= l.opening.left + 0.5f && l.image.right >= l.opening.right - 0.5f)
        assertTrue(l.image.top <= l.opening.top + 0.5f && l.image.bottom >= l.opening.bottom - 0.5f)
    }

    @Test fun fillAlignmentChoosesWhichPartIsKept() {
        val frame = FrameConfig.of(FrameStyle.BORDERLESS)
        val top = FrameGeometry.compute(1920f, 1080f, 3000, 4000, frame, ScaleMode.FILL, alignY = 0f)
        val bottom = FrameGeometry.compute(1920f, 1080f, 3000, 4000, frame, ScaleMode.FILL, alignY = 1f)
        assertEquals(top.opening.top, top.image.top, 0.5f)
        assertEquals(bottom.opening.bottom, bottom.image.bottom, 0.5f)
    }

    @Test fun originalSizeNeverEnlargesASmallPicture() {
        val l = FrameGeometry.compute(1920f, 1080f, 320, 240, FrameConfig.of(FrameStyle.BORDERLESS), ScaleMode.CONTAIN)
        assertEquals(320f, l.image.width, 0.5f)
        assertEquals(240f, l.image.height, 0.5f)
        assertTrue(abs((l.image.left + l.image.right) / 2 - 960f) < 1f)
    }

    @Test fun borderlessUsesTheWholeScreen() {
        val l = FrameGeometry.compute(1920f, 1080f, 1920, 1080, FrameConfig.of(FrameStyle.BORDERLESS), ScaleMode.FIT)
        assertEquals(1920f, l.image.width, 0.5f)
        assertEquals(1080f, l.image.height, 0.5f)
    }

    @Test fun outOfRangeThicknessIsClamped() {
        val l = FrameGeometry.compute(1920f, 1080f, 100, 100, FrameConfig(frameThickness = 5f, mountThickness = 5f), ScaleMode.FIT)
        assertTrue(l.opening.width > 0 && l.opening.height > 0)
    }
}

class SettingsTest {
    @Test fun settingsSurviveARoundTrip() {
        val s = AppSettings(theme = ThemeMode.MIDNIGHT, startupMode = StartupMode.ART, dimLevel = 0.3f, launchOnBoot = true, clock24h = false)
        assertEquals(s, AppJson.decodeFromString(AppSettings.serializer(), AppJson.encodeToString(AppSettings.serializer(), s)))
    }

    @Test fun presetsKeepTheirOwnFrameAndTiming() {
        val c = PlaybackConfig(sources = listOf(Source(SourceType.COLLECTION, 7)), order = PlayOrder.SHUFFLE, durationSec = 45, kenBurns = true, frame = FrameConfig.of(FrameStyle.WALNUT))
        val back = AppJson.decodeFromString(PlaybackConfig.serializer(), AppJson.encodeToString(PlaybackConfig.serializer(), c))
        assertEquals(c, back)
        assertEquals(FrameStyle.WALNUT, back.frame.style)
    }

    @Test fun unknownAndMissingFieldsFallBackToDefaults() {
        val s = AppJson.decodeFromString(AppSettings.serializer(), """{"theme":"LINEN","somethingNew":1}""")
        assertEquals(ThemeMode.LINEN, s.theme)
        assertEquals(StartupMode.HOME, s.startupMode)
    }

    @Test fun perItemDurationOverridesThePresetDefault() {
        assertEquals(30, slideSeconds(30, 0))
        assertEquals(300, slideSeconds(30, 300))
        assertEquals(3, slideSeconds(1, 0))
        assertEquals("Default", formatDuration(0))
        assertEquals("30 seconds", formatDuration(30))
        assertEquals("5 min", formatDuration(300))
        assertEquals("1 h", formatDuration(3600))
    }
}

class DecodeSizeTest {
    private val g = com.myframe.tv.core.FrameGeometry
    private val mount = com.myframe.tv.core.FrameConfig.of(com.myframe.tv.core.FrameStyle.MUSEUM_MOUNT)

    @Test fun portraitInAMountIsDecodedFarSmallerThanTheScreen() {
        val (w, h) = g.decodeSize(1920, 1080, 1938, 2560, mount, com.myframe.tv.core.ScaleMode.FIT)
        assertTrue("height fits the screen", h <= 1080)
        assertTrue("keeps the picture's shape", kotlin.math.abs(w.toFloat() / h - 1938f / 2560f) < 0.01f)
        assertTrue("under a quarter of a cover decode", w.toLong() * h < 1920L * 2536 / 4)
    }

    @Test fun neverMoreThanTwoScreensOfPixelsAndNeverZero() {
        val (w, h) = g.decodeSize(1920, 1080, 12000, 3000, mount, com.myframe.tv.core.ScaleMode.FILL, zoom = 3f, extra = 1.07f)
        assertTrue(w.toLong() * h <= 2L * 1920 * 1080 + 4000)
        assertEquals(1920 to 1080, g.decodeSize(1920, 1080, 0, 0, mount, com.myframe.tv.core.ScaleMode.FIT))
        val (a, b) = g.decodeSize(1920, 1080, 1, 5000, mount, com.myframe.tv.core.ScaleMode.FIT)
        assertTrue(a >= 1 && b >= 1)
    }
}

class AmbienceTest {
    private val a = com.myframe.tv.core.Ambience

    @Test fun nightWindowCrossesMidnight() {
        assertTrue(a.isNight(23, 23, 7)); assertTrue(a.isNight(2, 23, 7)); assertTrue(a.isNight(6, 23, 7))
        assertFalse(a.isNight(7, 23, 7)); assertFalse(a.isNight(15, 23, 7)); assertFalse(a.isNight(22, 23, 7))
        assertTrue(a.isNight(13, 12, 14)); assertFalse(a.isNight(14, 12, 14)); assertFalse(a.isNight(3, 5, 5))
    }

    @Test fun eveningDimIsZeroByDayFullAtNightAndNeverJumps() {
        assertEquals(0f, a.eveningDim(12, 0), 0.0001f)
        assertEquals(0f, a.eveningDim(19, 0), 0.0001f)
        assertEquals(a.MAX_EVENING_DIM, a.eveningDim(23, 30), 0.0001f)
        assertEquals(a.MAX_EVENING_DIM, a.eveningDim(3, 0), 0.0001f)
        var prev = a.eveningDim(0, 0)
        for (m in 1..(24 * 60)) {
            val v = a.eveningDim((m / 60) % 24, m % 60)
            assertTrue("jump at minute $m", kotlin.math.abs(v - prev) < 0.01f)
            prev = v
        }
    }

    @Test fun driftStaysWithinTwoPixelsAndReturnsToCentre() {
        assertEquals(0 to 0, a.drift(0))
        for (m in 0L..500L) {
            val (x, y) = a.drift(m)
            assertTrue(kotlin.math.abs(x) <= 2 && kotlin.math.abs(y) <= 2)
        }
        assertEquals(a.drift(0), a.drift(27))
        for (m in -500L..0L) assertTrue(kotlin.math.abs(a.drift(m).first) <= 2)
    }
}

class ArtUpdaterFilterTest {
    @Test fun representationalPaintersAreAcceptedAndCubistOrAbstractAreNot() {
        assertTrue(com.myframe.tv.data.ArtUpdater.isRepresentational("Claude Monet"))
        assertTrue(com.myframe.tv.data.ArtUpdater.isRepresentational("Vincent van Gogh"))
        assertFalse(com.myframe.tv.data.ArtUpdater.isRepresentational("Pablo Picasso"))
        assertFalse(com.myframe.tv.data.ArtUpdater.isRepresentational("Georges Braque"))
        assertFalse(com.myframe.tv.data.ArtUpdater.isRepresentational("Wassily Kandinsky"))
    }
}

class UploadSecurityTest {
    @Test fun correctPinPairsAndTheTokenValidates() {
        val p = Pairing()
        val r = p.pair(p.pin, "Phone", "192.168.1.20")
        assertTrue(r is Pairing.Result.Ok)
        val token = (r as Pairing.Result.Ok).token
        assertTrue(p.validate(token))
        assertFalse(p.validate("nope"))
        assertFalse(p.validate(null))
        p.revoke(token)
        assertFalse(p.validate(token))
    }

    @Test fun wrongPinLocksOutAfterFiveTriesThenRecovers() {
        var now = 1_000L
        val p = Pairing(clock = { now })
        val wrong = if (p.pin == "000000") "111111" else "000000"
        repeat(4) { assertTrue(p.pair(wrong, "x", "a") is Pairing.Result.BadPin) }
        assertTrue(p.pair(wrong, "x", "a") is Pairing.Result.Locked)
        assertTrue("locked even with the right PIN", p.pair(p.pin, "x", "a") is Pairing.Result.Locked)
        now += Pairing.LOCK_MS + 1
        assertTrue(p.pair(p.pin, "x", "a") is Pairing.Result.Ok)
    }

    @Test fun pinIsPlainDigitsOnAnArabicTvAndLocalisedDigitsStillPair() {
        val before = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"))
            val p = Pairing()
            assertTrue("PIN must be typeable on a phone keypad", p.pin.all { it in '0'..'9' })
            // The same PIN written in Arabic-Indic digits (U+0660..) is accepted too.
            val arabic = p.pin.map { '٠' + (it - '0') }.joinToString("")
            assertTrue(p.pair(arabic, "Phone", "a") is Pairing.Result.Ok)
            assertTrue(p.pair(p.pin, "Phone", "a") is Pairing.Result.Ok)
        } finally {
            java.util.Locale.setDefault(before)
        }
    }

    @Test fun resetDropsEverySessionAndChangesNothingElse() {
        val p = Pairing()
        val token = (p.pair(p.pin, "Phone", "a") as Pairing.Result.Ok).token
        p.reset()
        assertFalse(p.validate(token))
        assertEquals(6, p.pin.length)
        assertTrue(p.devices().isEmpty())
    }

    @Test fun onlyLocalNetworkAddressesAreAccepted() {
        for (ip in listOf("192.168.1.5", "10.0.2.2", "172.16.4.1", "127.0.0.1", "169.254.1.1", "fe80::1", "fd00::1", "::1")) assertTrue(ip, NetGuard.isLocalAddress(ip))
        for (ip in listOf("8.8.8.8", "172.32.0.1", "2001:4860:4860::8888", "example.com", "", null)) assertFalse("$ip", NetGuard.isLocalAddress(ip))
    }

    @Test fun hostHeaderMustNameThisDevice() {
        val ips = listOf("192.168.1.50")
        assertTrue(NetGuard.hostAllowed("192.168.1.50:8080", ips))
        assertTrue(NetGuard.hostAllowed("127.0.0.1:8080", ips))
        assertTrue(NetGuard.hostAllowed("localhost", ips))
        assertFalse(NetGuard.hostAllowed("evil.example.com:8080", ips))
        assertFalse(NetGuard.hostAllowed("192.168.1.51:8080", ips))
        assertFalse(NetGuard.hostAllowed(null, ips))
    }

    @Test fun idsAndNamesCannotCarryPaths() {
        assertEquals(42L, NetGuard.parseId("42"))
        for (bad in listOf("../etc", "4/2", "-1", "1e3", "", "12345678901234567890", null)) assertNull("$bad", NetGuard.parseId(bad))
        assertEquals("passwd", MediaImporter.cleanName("../../etc/passwd"))
        assertEquals("b.jpg", MediaImporter.cleanName("C:\\a\\b.jpg"))
        assertEquals("holiday.jpg", MediaImporter.cleanName("holi\u0000day.jpg"))
    }
}
