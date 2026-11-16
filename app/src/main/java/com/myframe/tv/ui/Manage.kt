package com.myframe.tv.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myframe.tv.BuildConfig
import com.myframe.tv.LocalNav
import com.myframe.tv.Screen
import com.myframe.tv.core.AppJson
import com.myframe.tv.core.DURATION_CHOICES
import com.myframe.tv.core.FrameConfig
import com.myframe.tv.core.FrameStyle
import com.myframe.tv.core.PlayOrder
import com.myframe.tv.core.PlaybackConfig
import com.myframe.tv.core.RepeatMode
import com.myframe.tv.core.ScaleMode
import com.myframe.tv.core.Source
import com.myframe.tv.core.SourceType
import com.myframe.tv.core.StartupMode
import com.myframe.tv.core.ThemeMode
import com.myframe.tv.core.Transition
import com.myframe.tv.core.formatDuration
import com.myframe.tv.data.Album
import com.myframe.tv.data.Media
import com.myframe.tv.data.MediaRepository
import com.myframe.tv.data.Preset
import com.myframe.tv.data.StorageInfo
import com.myframe.tv.graph
import com.myframe.tv.system.Apps
import com.myframe.tv.system.HomeRole
import com.myframe.tv.ui.kit.*
import com.myframe.tv.ui.viewer.FrameMoulding
import com.myframe.tv.ui.viewer.FramedPicture
import com.myframe.tv.ui.viewer.Slide
import kotlinx.coroutines.launch
import kotlin.math.abs

private fun sourcesSummary(config: PlaybackConfig, albums: List<Album>): String =
    config.sources.joinToString(" + ") { s ->
        when (s.type) {
            SourceType.ALL_ART -> "All paintings"
            SourceType.FAV_ART -> "Favourite paintings"
            SourceType.ALL_PHOTOS -> "All photographs"
            SourceType.FAV_PHOTOS -> "Favourite photographs"
            SourceType.COLLECTION -> albums.firstOrNull { it.id == s.collectionId }?.name ?: "Collection"
        }
    }.ifEmpty { "No sources chosen" }

private const val COLLECTION_PLAYER = "Collection player"

@Composable
fun MixedScreen() {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val all by graph.repo.presets.observeAll().collectAsState(emptyList())
    val albums by graph.repo.albums.observeAll().collectAsState(emptyList())
    val presets = all.filter { it.builtin.isEmpty() && it.name != COLLECTION_PLAYER }
    val memory = rememberFocusMemory("new")

    ScreenScaffold(
        "Mixed Carousel", overline = "Saved presets", subtitle = "Each preset remembers its own pictures, timing and frame. Press OK to play one.",
        actions = {
            PillButton("New preset", {
                scope.launch {
                    val config = PlaybackConfig(sources = listOf(Source(SourceType.ALL_ART), Source(SourceType.ALL_PHOTOS)), durationSec = 30)
                    val id = graph.repo.presets.insert(Preset(name = "New preset", sortOrder = 50, configJson = AppJson.encodeToString(PlaybackConfig.serializer(), config)))
                    nav.go(Screen.Editor(id))
                }
            }, Modifier.remembered(memory, "new"), primary = true, icon = Glyph.PLUS)
        },
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(3), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(presets, key = { it.id }) { preset ->
                val config = remember(preset.configJson) { preset.config }
                val cover by produceState<Media?>(null, preset.configJson) { value = graph.repo.playlist(config.sources).firstOrNull() }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusSurface({ nav.go(Screen.Viewer(preset.id)) }, Modifier.remembered(memory, "p${preset.id}"), scale = 1.04f) { focused ->
                        Column {
                            MediaThumb(cover, Modifier.fillMaxWidth().height(120.dp))
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Txt(preset.name, Type.heading, color = if (focused) p.accent else p.text, maxLines = 1)
                                Txt(sourcesSummary(config, albums.map { it.album }), Type.caption, color = p.muted, maxLines = 1)
                                Txt("${formatDuration(config.durationSec)} · ${config.order.label} · ${config.frame.style.label}", Type.caption, color = p.muted, maxLines = 1)
                            }
                        }
                    }
                    PillButton("Edit", { nav.go(Screen.Editor(preset.id)) }, Modifier.remembered(memory, "e${preset.id}"), icon = Glyph.GEAR)
                }
            }
        }
    }
}

private val FRAME_SIZES = listOf(0f to "None", 0.012f to "Hairline", 0.02f to "Thin", 0.028f to "Slim", 0.036f to "Medium", 0.046f to "Wide", 0.06f to "Bold", 0.08f to "Extra bold")
private val MOUNT_SIZES = listOf(0f to "None", 0.03f to "Narrow", 0.05f to "Small", 0.07f to "Medium", 0.09f to "Wide", 0.12f to "Museum", 0.16f to "Extra wide", 0.2f to "Maximum")
private val IMAGE_SIZES = listOf(1f to "Full", 0.9f to "90%", 0.8f to "80%", 0.7f to "70%")
private val SPEEDS = listOf(600 to "Quick", 1400 to "Gentle", 2600 to "Slow")
private val DIM_LEVELS = listOf(0f to "Off", 0.15f to "Slight", 0.3f to "Medium", 0.45f to "Strong", 0.6f to "Night")
private val COLOURS = listOf(
    0xFFF2EEE5 to "Ivory", 0xFFFBF9F4 to "Bright white", 0xFFE6DCC8 to "Beige", 0xFFB9B5AD to "Stone grey",
    0xFF2A2A2A to "Charcoal", 0xFF0A0A0A to "Black", 0xFFB5874F to "Oak", 0xFF4B301F to "Walnut",
    0xFF2E4057 to "Navy", 0xFF2F4A3A to "Forest green", 0xFF5A1F2B to "Burgundy", 0xFFC9A66B to "Brass",
)

private fun nearest(options: List<Pair<Float, String>>, v: Float): Float = options.minBy { abs(it.first - v) }.first
private fun colourOptions(current: Long) = if (COLOURS.any { it.first == current }) COLOURS else COLOURS + (current to "Style default")

@Composable
private fun Section(title: String) {
    Txt(title.uppercase(), Type.overline, Modifier.padding(top = 14.dp, bottom = 2.dp), color = LocalPalette.current.accent)
}

/** Edits one preset. Every change is saved at once and shown in the live preview on the right. */
@Composable
fun EditorScreen(presetId: Long) {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val presets by graph.repo.presets.observeAll().collectAsState(emptyList())
    val albumInfos by graph.repo.albums.observeAll().collectAsState(emptyList())
    val preset = presets.firstOrNull { it.id == presetId }
    if (preset == null) {
        Box(Modifier.fillMaxSize().background(p.bg))
        return
    }
    val config = remember(preset.configJson) { preset.config }
    val albums = albumInfos.map { it.album }
    val memory = rememberFocusMemory("sources")
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var choosingSources by remember { mutableStateOf(false) }
    val edit: ((PlaybackConfig) -> PlaybackConfig) -> Unit = { block -> scope.launch { graph.repo.updateConfig(presetId, block) } }
    val frame: ((FrameConfig) -> FrameConfig) -> Unit = { block -> edit { it.copy(frame = block(it.frame)) } }
    val sample by produceState<Slide?>(null, config.sources) {
        val m = graph.repo.playlist(config.sources).firstOrNull() ?: graph.repo.art(false).firstOrNull()
        value = m?.let { media ->
            graph.images.full(media, 960, 540)?.let { Slide(media, it.asImageBitmap(), graph.images.blurred(media)?.asImageBitmap()) }
        }
    }

    Row(Modifier.fillMaxSize().background(p.bg).padding(horizontal = EdgeH, vertical = EdgeV), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.weight(1f)) {
            Txt("FRAME AND SLIDESHOW", Type.overline, color = p.accent)
            Txt(preset.name, Type.title, maxLines = 1)
            Column(Modifier.padding(top = 6.dp).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Section("Pictures")
                SettingRow("Sources", sourcesSummary(config, albums), { choosingSources = true }, Modifier.remembered(memory, "sources"), subtitle = "Which paintings, photographs and collections this slideshow plays.")
                if (preset.builtin.isEmpty()) SettingRow("Name", preset.name, { renaming = true }, Modifier.remembered(memory, "name"))

                Section("Slideshow")
                ChoiceRow("Mode", listOf(true to "Slideshow", false to "Single picture"), config.autoAdvance, { v -> edit { it.copy(autoAdvance = v) } }, Modifier.remembered(memory, "mode"), "Single picture keeps one image on screen until you change it with the remote.")
                ChoiceRow("Time per picture", DURATION_CHOICES.map { it to formatDuration(it) }, config.durationSec, { v -> edit { it.copy(durationSec = v) } }, Modifier.remembered(memory, "duration"))
                ChoiceRow("Order", PlayOrder.entries.map { it to it.label }, config.order, { v -> edit { it.copy(order = v) } }, Modifier.remembered(memory, "order"))
                ChoiceRow("Looping", RepeatMode.entries.map { it to it.label }, config.repeat, { v -> edit { it.copy(repeat = v) } }, Modifier.remembered(memory, "repeat"))
                ChoiceRow("Transition", Transition.entries.map { it to it.label }, config.transition, { v -> edit { it.copy(transition = v) } }, Modifier.remembered(memory, "transition"))
                ChoiceRow("Transition speed", SPEEDS, SPEEDS.minBy { abs(it.first - config.transitionMs) }.first, { v -> edit { it.copy(transitionMs = v) } }, Modifier.remembered(memory, "speed"))
                ChoiceRow("Movement", listOf(false to "None", true to "Slow drift"), config.kenBurns, { v -> edit { it.copy(kenBurns = v) } }, Modifier.remembered(memory, "motion"), "Slow drift gently zooms each picture. None keeps everything perfectly still.")
                ChoiceRow("Picture fit", ScaleMode.entries.map { it to it.label }, config.scaleMode, { v -> edit { it.copy(scaleMode = v) } }, Modifier.remembered(memory, "fit"), "Fit shows the whole picture. Fill crops to the opening. Pictures are never stretched.")
                ToggleRow("Blurred backdrop", config.blurBackground, { v -> edit { it.copy(blurBackground = v) } }, Modifier.remembered(memory, "blur"), "Without a mount, fills the space around portrait pictures with a soft blur of the picture.")

                Section("Frame")
                ChoiceRow("Style", FrameStyle.entries.map { it to it.label }, config.frame.style, { v -> frame { FrameConfig.of(v) } }, Modifier.remembered(memory, "style"), "Choosing a style resets the sizes and colours below to that style.")
                ChoiceRow("Frame thickness", FRAME_SIZES, nearest(FRAME_SIZES, config.frame.frameThickness), { v -> frame { it.copy(frameThickness = v) } }, Modifier.remembered(memory, "thickness"))
                ChoiceRow("Frame colour", colourOptions(config.frame.frameColor), config.frame.frameColor, { v -> frame { it.copy(frameColor = v, style = FrameStyle.CUSTOM) } }, Modifier.remembered(memory, "frameColour"))
                ChoiceRow("Mount size", MOUNT_SIZES, nearest(MOUNT_SIZES, config.frame.mountThickness), { v -> frame { it.copy(mountThickness = v) } }, Modifier.remembered(memory, "mount"))
                ChoiceRow("Mount colour", colourOptions(config.frame.mountColor), config.frame.mountColor, { v -> frame { it.copy(mountColor = v) } }, Modifier.remembered(memory, "mountColour"))
                ChoiceRow("Background colour", colourOptions(config.frame.backgroundColor), config.frame.backgroundColor, { v -> frame { it.copy(backgroundColor = v) } }, Modifier.remembered(memory, "wall"))
                ChoiceRow("Picture size", IMAGE_SIZES, nearest(IMAGE_SIZES, config.frame.imageScale), { v -> frame { it.copy(imageScale = v) } }, Modifier.remembered(memory, "scale"))
                ToggleRow("Texture", config.frame.texture, { v -> frame { it.copy(texture = v) } }, Modifier.remembered(memory, "texture"))
                ToggleRow("Shadow", config.frame.shadow, { v -> frame { it.copy(shadow = v) } }, Modifier.remembered(memory, "shadow"))

                Section("On screen")
                ToggleRow("Title and artist", config.showMetadata, { v -> edit { it.copy(showMetadata = v) } }, Modifier.remembered(memory, "meta"))
                ToggleRow("Clock", config.showClock, { v -> edit { it.copy(showClock = v) } }, Modifier.remembered(memory, "clock"))
                ToggleRow("Photo date", config.showDate, { v -> edit { it.copy(showDate = v) } }, Modifier.remembered(memory, "date"))
                ToggleRow("Photo file name", config.showFilename, { v -> edit { it.copy(showFilename = v) } }, Modifier.remembered(memory, "file"))
                if (preset.builtin.isEmpty()) {
                    Section("Preset")
                    PillButton("Delete this preset", { deleting = true }, Modifier.remembered(memory, "delete"), danger = true)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        Column(Modifier.width(380.dp).padding(top = 30.dp)) {
            Txt("LIVE PREVIEW", Type.overline, Modifier.padding(bottom = 8.dp), color = p.muted)
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)).border(1.dp, p.outline, RoundedCornerShape(4.dp))) {
                sample?.let { FramedPicture(it, config) }
                FrameMoulding(config.frame)
            }
            Txt("${config.frame.style.label} · ${config.scaleMode.label}", Type.label, Modifier.padding(top = 10.dp))
            Txt("${formatDuration(config.durationSec)} per picture · ${config.transition.label}", Type.caption, color = p.muted)
            PillButton("Play", { nav.go(Screen.Viewer(presetId)) }, Modifier.padding(top = 14.dp).remembered(memory, "play"), primary = true, icon = Glyph.PLAY)
        }
    }

    if (choosingSources) {
        val options = listOf(
            Source(SourceType.ALL_ART) to "All paintings", Source(SourceType.FAV_ART) to "Favourite paintings",
            Source(SourceType.ALL_PHOTOS) to "All photographs", Source(SourceType.FAV_PHOTOS) to "Favourite photographs",
        ) + albumInfos.map { Source(SourceType.COLLECTION, it.album.id) to "${it.album.name} (${it.itemCount})" }
        Dialog(onDismissRequest = { choosingSources = false }) {
            Column(Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(16.dp)).background(p.bg).border(1.dp, p.outline, RoundedCornerShape(16.dp)).padding(20.dp)) {
                Txt("Sources for ${preset.name}", Type.heading, Modifier.padding(bottom = 10.dp))
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEachIndexed { i, (source, label) ->
                        val on = source in config.sources
                        SettingRow(label, if (on) "Included" else "—", {
                            edit { c -> c.copy(sources = if (on) c.sources - source else c.sources + source) }
                        }, if (i == 0) Modifier.focusRequesterInitial() else Modifier)
                    }
                }
                PillButton("Done", { choosingSources = false }, Modifier.padding(top = 14.dp), primary = true)
            }
        }
    }
    if (renaming) {
        TextInputDialog("Rename preset", preset.name, listOf("Living Room Art", "Family Memories", "Travel Photos", "Mixed Gallery"), { renaming = false }) { name ->
            renaming = false
            scope.launch { graph.repo.presets.byId(presetId)?.let { graph.repo.presets.update(it.copy(name = name)) } }
        }
    }
    if (deleting) {
        ConfirmDialog("Delete \"${preset.name}\"?", "The preset is removed. Your pictures are not affected.", "Delete", { deleting = false }, danger = true) {
            deleting = false
            scope.launch { graph.repo.presets.delete(presetId) }
            nav.back()
        }
    }
}

@Composable
private fun Modifier.focusRequesterInitial(): Modifier = this.focusRequester(rememberInitialFocus())

private val SECTIONS = listOf("Appearance", "Slideshow", "Media", "Cloud", "Phone upload", "Startup", "Devices", "About")

private fun mb(bytes: Long): String = if (bytes >= 1L shl 30) "%.1f GB".format(bytes / 1073741824.0) else "${bytes / (1024 * 1024)} MB"

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val settings by graph.settings.flow.collectAsState()
    val presets by graph.repo.presets.observeAll().collectAsState(emptyList())
    val upload by graph.upload.state.collectAsState()
    val memory = rememberFocusMemory("s0")
    var section by rememberSaveable { mutableStateOf(SECTIONS.first()) }
    var storageTick by remember { mutableIntStateOf(0) }
    val storage by produceState<StorageInfo?>(null, storageTick) { value = graph.repo.storage() }
    var homeEnabled by remember { mutableStateOf(HomeRole.isEnabled(context)) }
    var note by remember { mutableStateOf<String?>(null) }
    var artBusy by remember { mutableStateOf(false) }
    val art = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_ART }
    val photos = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_PHOTOS }
    val mixed = presets.filter { it.builtin.isEmpty() && it.name != COLLECTION_PLAYER }

    Row(Modifier.fillMaxSize().background(p.bg).padding(horizontal = EdgeH, vertical = EdgeV), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("Settings", Type.title, Modifier.padding(bottom = 10.dp))
            SECTIONS.forEachIndexed { i, name ->
                FocusSurface({ section = name }, Modifier.fillMaxWidth().remembered(memory, "s$i"), scale = 1.02f, background = if (section == name) p.surfaceHi else p.surface, onFocus = { section = name; note = null }) { focused ->
                    Txt(name, Type.label, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), color = if (focused || section == name) p.accent else p.text)
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(section, Type.heading, Modifier.padding(bottom = 6.dp))
            when (section) {
                "Appearance" -> {
                    ChoiceRow("Theme", ThemeMode.entries.map { it to it.label }, settings.theme, { v -> graph.settings.update { it.copy(theme = v) } })
                    art?.let { SettingRow("Art Mode frame, borders and overlays", it.config.frame.style.label, { nav.go(Screen.Editor(it.id)) }) }
                    photos?.let { SettingRow("My Photos frame, borders and overlays", it.config.frame.style.label, { nav.go(Screen.Editor(it.id)) }) }
                    ChoiceRow("Screen dimming", DIM_LEVELS, nearest(DIM_LEVELS, settings.dimLevel), { v -> graph.settings.update { it.copy(dimLevel = v) } }, subtitle = "Darkens pictures inside MyFrame for evenings. Apps cannot change the TV backlight itself; use the TV's picture menu for that.")
                    ToggleRow("Softer picture in the evening", settings.eveningDim, { v -> graph.settings.update { it.copy(eveningDim = v) } }, subtitle = "From 7 pm the picture slowly gets darker and warmer, and returns to normal by 7 am.")
                    ToggleRow("Protect the screen from burn-in", settings.burnInShift, { v -> graph.settings.update { it.copy(burnInShift = v) } }, subtitle = "Moves the picture by a hair every few minutes. You will not notice it. Leave on for OLED TVs.")
                    ToggleRow("Black screen at night", settings.nightOff, { v -> graph.settings.update { it.copy(nightOff = v) } }, subtitle = "Shows a black screen during the hours below. Press any remote button to see the picture for ten minutes. The TV itself stays on.")
                    if (settings.nightOff) {
                        ChoiceRow("Night starts at", (0..23).map { it to "%02d:00".format(java.util.Locale.ROOT, it) }, settings.nightStartHour, { v -> graph.settings.update { it.copy(nightStartHour = v) } })
                        ChoiceRow("Night ends at", (0..23).map { it to "%02d:00".format(java.util.Locale.ROOT, it) }, settings.nightEndHour, { v -> graph.settings.update { it.copy(nightEndHour = v) } })
                    }
                    ChoiceRow("Clock format", listOf(true to "24-hour", false to "12-hour"), settings.clock24h, { v -> graph.settings.update { it.copy(clock24h = v) } })
                }
                "Slideshow" -> listOf(art to "Art Mode", photos to "My Photos").forEach { (preset, name) ->
                    if (preset != null) {
                        val c = preset.config
                        Section(name)
                        ChoiceRow("Time per picture", DURATION_CHOICES.map { it to formatDuration(it) }, c.durationSec, { v -> scope.launch { graph.repo.updateConfig(preset.id) { it.copy(durationSec = v) } } })
                        ChoiceRow("Order", PlayOrder.entries.map { it to it.label }, c.order, { v -> scope.launch { graph.repo.updateConfig(preset.id) { it.copy(order = v) } } })
                        ChoiceRow("Transition", Transition.entries.map { it to it.label }, c.transition, { v -> scope.launch { graph.repo.updateConfig(preset.id) { it.copy(transition = v) } } })
                        ChoiceRow("Looping", RepeatMode.entries.map { it to it.label }, c.repeat, { v -> scope.launch { graph.repo.updateConfig(preset.id) { it.copy(repeat = v) } } })
                        SettingRow("Movement, fit and every other option", "Open", { nav.go(Screen.Editor(preset.id)) })
                    }
                }
                "Media" -> {
                    ToggleRow("New paintings over Wi-Fi", settings.artUpdates, { v -> graph.settings.update { it.copy(artUpdates = v) } }, subtitle = "Once a day MyFrame adds up to five public-domain paintings from The Metropolitan Museum of Art. Classic painters only, no abstract art.")
                    SettingRow("Get new paintings now", if (artBusy) "Looking…" else "Start", {
                        if (!artBusy) {
                            artBusy = true
                            scope.launch {
                                val n = graph.artUpdater.runNow()
                                note = when {
                                    n == null -> "The museum could not be reached. Check the Wi-Fi and try again."
                                    n == 0 -> "No new paintings this time. Try again later."
                                    else -> "$n new paintings added to Art Mode."
                                }
                                artBusy = false
                                storageTick++
                            }
                        }
                    }, subtitle = "This can take a minute.")
                    storage?.let { s ->
                        Txt("${s.photos} photographs using ${mb(s.photoBytes)} · ${s.art} artworks · ${mb(s.freeBytes)} free of ${mb(s.totalBytes)}", Type.body, Modifier.padding(bottom = 6.dp), color = p.muted)
                    }
                    SettingRow("Manage artwork", "Open", { nav.go(Screen.Art) }, subtitle = "Hide works you do not want shown, and mark favourites.")
                    SettingRow("Manage photos", "Open", { nav.go(Screen.Photos) }, subtitle = "Select, favourite or delete photographs.")
                    SettingRow("Manage collections", "Open", { nav.go(Screen.Collections) })
                    ToggleRow("Optimise large photos on import", settings.optimizeImports, { v -> graph.settings.update { it.copy(optimizeImports = v) } }, subtitle = "Keeps new photos within 1920 pixels to save space on an 8 GB stick. Existing photos and originals on your phone stay as they are.")
                    Txt("MyFrame leaves 1 GB free for Android, music apps and updates. Picture caches use at most 32 MB; art is decoded within a Full HD budget.", Type.caption, color = p.muted)
                    SettingRow("Clear thumbnail cache", storage?.let { mb(it.cacheBytes) } ?: "", { graph.images.clearThumbnails(); storageTick++ }, subtitle = "Thumbnails are rebuilt when needed.")
                    SettingRow("Import from the inbox folder", "Scan", {
                        scope.launch { note = "Imported ${graph.importer.importInbox()} new photos from the inbox folder."; storageTick++ }
                    }, subtitle = "For photos copied with adb into Android/data/com.myframe.tv/files/inbox.")
                }
                "Cloud" -> {
                    Txt("Google Photos: not connected", Type.label)
                    Txt(
                        "Google Photos import is not included in this build. Since March 2025 Google only lets apps receive photos you pick yourself through the Google Photos Picker; continuous syncing of your whole library is no longer possible for any app. " +
                            "See docs/GOOGLE_PHOTOS_SETUP.md for the planned sign-in and pick-on-your-phone flow. Until then, Phone upload does the same job without an account.",
                        Type.body, color = p.muted,
                    )
                    SettingRow("Use Phone upload instead", "Open", { nav.go(Screen.Upload) })
                }
                "Phone upload" -> {
                    SettingRow("Upload photos from your phone", if (upload.running) "On" else "Off", { nav.go(Screen.Upload) }, subtitle = "Shows a QR code and a PIN. Works on your home network only, and switches itself off after 30 idle minutes.")
                    if (upload.running) {
                        SettingRow("Paired devices", "${upload.devices.size}", { nav.go(Screen.Upload) })
                        SettingRow("Turn off and disconnect everything", "Turn off", { graph.upload.stop() })
                    }
                }
                "Startup" -> {
                    ChoiceRow("When MyFrame opens, start in", StartupMode.entries.map { it to it.label }, settings.startupMode, { v -> graph.settings.update { it.copy(startupMode = v) } })
                    if (mixed.isNotEmpty()) {
                        ChoiceRow("Mixed Carousel preset to start with", mixed.map { it.id to it.name }, settings.mixedPresetId.takeIf { id -> mixed.any { it.id == id } } ?: mixed.first().id, { v -> graph.settings.update { it.copy(mixedPresetId = v) } })
                    }
                    ToggleRow("Open MyFrame when the TV box starts", settings.launchOnBoot, { v -> graph.settings.update { it.copy(launchOnBoot = v) } }, subtitle = "Android 10 and newer only allow this after one extra permission is granted with adb. See docs/ADB_INSTALL.md. Without it this switch has no effect.")
                    ToggleRow("Offer MyFrame as the Home screen", homeEnabled, { v ->
                        HomeRole.setEnabled(context, v)
                        homeEnabled = HomeRole.isEnabled(context)
                    }, subtitle = "Makes MyFrame available as a Home app. Android decides whether it may replace the built-in launcher; the built-in launcher is never removed. Turn this off to undo it.")
                    SettingRow("Screensaver", "Open TV settings", {
                        val opened = runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                        if (!opened) note = "This TV does not expose screensaver settings. See docs/ADB_INSTALL.md for the adb command."
                    }, subtitle = "Choose \"MyFrame gallery\" as the TV screensaver, where the device allows it.")
                    if (presets.isNotEmpty()) {
                        ChoiceRow("Screensaver shows", presets.filter { it.name != COLLECTION_PLAYER }.map { it.id to it.name }, settings.dreamPresetId.takeIf { id -> presets.any { it.id == id } } ?: (art?.id ?: presets.first().id), { v -> graph.settings.update { it.copy(dreamPresetId = v) } })
                    }
                }
                "Devices" -> {
                    SettingRow("Bluetooth", "Open TV settings", {
                        if (!Apps.openBluetoothSettings(context)) note = "This TV has no Bluetooth settings screen to open."
                    }, subtitle = "Opens the TV's own Settings. Choose \"Remotes & Accessories\" there to pair a Bluetooth speaker, headphones, keyboard or remote.")
                    SettingRow("Sound output", "Open", {
                        runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            .onFailure { note = "This TV has no sound settings screen to open." }
                    }, subtitle = "Choose where Spotify and YouTube Music play: TV speakers, Bluetooth speaker or headphones.")
                }
                "About" -> {
                    val memInfo = remember { ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) } }
                    val dm = context.resources.displayMetrics
                    listOf(
                        "Version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})",
                        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); MyFrame supports API 26 and newer",
                        "Screen" to "${dm.widthPixels} x ${dm.heightPixels} at ${dm.densityDpi} dpi",
                        "Memory" to "${mb(memInfo.totalMem)} total, ${mb(memInfo.availMem)} available, app limit ${mb(Runtime.getRuntime().maxMemory())}",
                        "Library" to (storage?.let { "${it.art} artworks, ${it.photos} photographs, ${mb(it.cacheBytes)} of thumbnails" } ?: "…"),
                        "Artwork" to "Bundled works are public domain (CC0), from The Metropolitan Museum of Art Open Access. Each work's credit appears in its caption.",
                        "Typeface" to "Cormorant Garamond, SIL Open Font License 1.1",
                    ).forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 5.dp)) {
                            Txt(k, Type.label, Modifier.width(110.dp), color = p.muted)
                            Txt(v, Type.body, Modifier.weight(1f))
                        }
                    }
                }
            }
            note?.let { Txt(it, Type.body, Modifier.padding(top = 10.dp), color = p.accent) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun UploadScreen() {
    val graph = LocalContext.current.graph
    val p = LocalPalette.current
    val state by graph.upload.state.collectAsState()
    val memory = rememberFocusMemory("toggle")
    LaunchedEffect(Unit) { graph.upload.start() }

    ScreenScaffold(
        "Add photos from your phone", overline = "Phone upload",
        subtitle = "Your phone and this TV must be on the same Wi-Fi network. Nothing is sent over the internet.",
        actions = {
            PillButton(if (state.running) "Turn off" else "Turn on", { if (state.running) graph.upload.stop() else graph.upload.start() }, Modifier.remembered(memory, "toggle"), primary = !state.running, selected = state.running, icon = Glyph.PHONE)
            if (state.running && state.devices.isNotEmpty()) PillButton("Disconnect all devices", { graph.upload.disconnectAll() }, Modifier.remembered(memory, "disconnect"), danger = true)
        },
    ) {
        val url = state.url
        if (state.running && url != null) {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                QrCode(url, Modifier.size(220.dp).clip(RoundedCornerShape(8.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Txt("1  Scan the code with your phone camera", Type.body)
                    Txt("    or open this address in a browser:", Type.body, color = p.muted)
                    Txt(url, Type.heading, color = p.accent)
                    Txt("2  Enter this PIN on your phone", Type.body, Modifier.padding(top = 14.dp))
                    Txt(state.pin.chunked(3).joinToString("  "), Type.display, color = p.accent)
                    Txt("3  Choose photos. They appear in My Photos straight away.", Type.body, Modifier.padding(top = 14.dp))
                    Txt(
                        if (state.devices.isEmpty()) "No devices paired yet." else "Paired: " + state.devices.joinToString(", ") { "${it.name} (${it.address})" },
                        Type.caption, Modifier.padding(top = 14.dp), color = p.muted, maxLines = 3,
                    )
                    if (state.uploaded > 0) Txt("${state.uploaded} photos received this session.", Type.caption, color = p.accent)
                }
            }
        } else {
            EmptyState(
                if (state.error != null) "Could not start phone upload" else "Phone upload is off",
                state.error ?: "Turn it on to show a QR code. The service only runs while you need it and stops by itself after 30 idle minutes.",
            )
        }
    }
}
