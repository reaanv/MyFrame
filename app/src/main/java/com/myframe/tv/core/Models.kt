package com.myframe.tv.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared JSON codec: tolerant of fields added or removed by later versions. */
val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

enum class MediaKind { ART, PHOTO }

@Serializable
enum class ScaleMode(val label: String) { FIT("Fit"), FILL("Fill and crop"), CONTAIN("Original size") }

@Serializable
enum class Transition(val label: String) { DISSOLVE("Dissolve"), FADE("Fade through dark"), SLIDE("Slide"), NONE("Cut") }

@Serializable
enum class PlayOrder(val label: String) { SEQUENTIAL("In order"), SHUFFLE("Shuffle") }

@Serializable
enum class RepeatMode(val label: String) { ALL("Repeat all"), ONE("Repeat one"), OFF("Play once") }

@Serializable
enum class SourceType { ALL_ART, FAV_ART, ALL_PHOTOS, FAV_PHOTOS, COLLECTION }

@Serializable
data class Source(val type: SourceType, val collectionId: Long = 0)

@Serializable
enum class FrameStyle(val label: String, val wood: Boolean = false) {
    CLASSIC_WHITE("Classic White"),
    MODERN_BLACK("Modern Black"),
    WARM_OAK("Warm Oak", wood = true),
    WALNUT("Walnut", wood = true),
    BORDERLESS("Minimal Borderless"),
    MUSEUM_MOUNT("Museum Mount"),
    CUSTOM("Custom Colour"),
}

/**
 * A digital frame. Thicknesses are fractions of the screen height so a design
 * looks the same on any resolution. Colours are ARGB.
 */
@Serializable
data class FrameConfig(
    val style: FrameStyle = FrameStyle.MUSEUM_MOUNT,
    val frameColor: Long = 0xFF1B1A18,
    val frameThickness: Float = 0.012f,
    val mountColor: Long = 0xFFF2EEE5,
    val mountThickness: Float = 0.12f,
    val backgroundColor: Long = 0xFF0A0A0A,
    val imageScale: Float = 1f,
    val texture: Boolean = true,
    val shadow: Boolean = true,
) {
    companion object {
        const val MAX_FRAME = 0.09f
        const val MAX_MOUNT = 0.22f

        /** The starting point for each named style; every value stays adjustable afterwards. */
        fun of(style: FrameStyle): FrameConfig = when (style) {
            FrameStyle.CLASSIC_WHITE -> FrameConfig(style, 0xFFF1EEE7, 0.036f, 0xFFFBF9F4, 0.075f)
            FrameStyle.MODERN_BLACK -> FrameConfig(style, 0xFF151515, 0.028f, 0xFFF1EEE7, 0.07f)
            FrameStyle.WARM_OAK -> FrameConfig(style, 0xFFB5874F, 0.042f, 0xFFF3EDE0, 0.065f)
            FrameStyle.WALNUT -> FrameConfig(style, 0xFF4B301F, 0.046f, 0xFFEFE8DA, 0.065f)
            FrameStyle.BORDERLESS -> FrameConfig(style, 0xFF000000, 0f, 0xFF000000, 0f, 0xFF000000, texture = false, shadow = false)
            FrameStyle.MUSEUM_MOUNT -> FrameConfig(style, 0xFF1B1A18, 0.012f, 0xFFF2EEE5, 0.12f)
            FrameStyle.CUSTOM -> FrameConfig(style, 0xFF2E4057, 0.036f, 0xFFF2EEE5, 0.07f)
        }
    }
}

/** Everything that defines how one slideshow looks and behaves. Stored per preset. */
@Serializable
data class PlaybackConfig(
    val sources: List<Source> = listOf(Source(SourceType.ALL_ART)),
    val order: PlayOrder = PlayOrder.SEQUENTIAL,
    val repeat: RepeatMode = RepeatMode.ALL,
    /** false = static mode: one picture stays until changed with the remote. */
    val autoAdvance: Boolean = true,
    val durationSec: Int = 60,
    val transition: Transition = Transition.DISSOLVE,
    val transitionMs: Int = 1400,
    val kenBurns: Boolean = false,
    val scaleMode: ScaleMode = ScaleMode.FIT,
    val blurBackground: Boolean = false,
    val showMetadata: Boolean = false,
    val showClock: Boolean = false,
    val showDate: Boolean = false,
    val showFilename: Boolean = false,
    val frame: FrameConfig = FrameConfig(),
)

@Serializable
enum class StartupMode(val label: String) {
    HOME("MyFrame Home"), ART("Art Mode"), PHOTOS("My Photos"), MIXED("Mixed Carousel"), RESUME("Resume last used"),
}

@Serializable
enum class ThemeMode(val label: String) { GALLERY("Gallery (charcoal and brass)"), MIDNIGHT("Midnight (navy and silver)"), LINEN("Linen (warm light)") }

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.GALLERY,
    val startupMode: StartupMode = StartupMode.HOME,
    val lastPresetId: Long = 0,
    val mixedPresetId: Long = 0,
    val launchOnBoot: Boolean = false,
    val dimLevel: Float = 0f,
    val clock24h: Boolean = true,
    val uploadPort: Int = 8080,
    val optimizeImports: Boolean = true,
    val dreamPresetId: Long = 0,
    /** Darken and warm the picture in the evening. */
    val eveningDim: Boolean = true,
    /** Shift the picture by a pixel or two every few minutes to protect OLED screens. */
    val burnInShift: Boolean = true,
    /** Show a black screen during the night hours below. */
    val nightOff: Boolean = false,
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 7,
    /** Download new public-domain paintings over Wi-Fi. */
    val artUpdates: Boolean = true,
)

/** Slide durations offered in the UI, in seconds. */
val DURATION_CHOICES = listOf(5, 10, 15, 20, 30, 45, 60, 120, 300, 600, 900, 1800, 3600)

fun formatDuration(sec: Int): String = when {
    sec <= 0 -> "Default"
    sec < 60 -> "$sec seconds"
    sec < 3600 -> if (sec % 60 == 0) "${sec / 60} min" else "${sec / 60} min ${sec % 60} s"
    else -> if (sec % 3600 == 0) "${sec / 3600} h" else "${sec / 3600} h ${(sec % 3600) / 60} min"
}

/** The time one item stays on screen: its own override when set, otherwise the preset default. */
fun slideSeconds(defaultSec: Int, overrideSec: Int): Int = (if (overrideSec > 0) overrideSec else defaultSec).coerceAtLeast(3)
