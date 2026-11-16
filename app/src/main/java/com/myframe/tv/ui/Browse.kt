package com.myframe.tv.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.myframe.tv.LocalNav
import com.myframe.tv.Screen
import com.myframe.tv.core.PlayOrder
import com.myframe.tv.core.Source
import com.myframe.tv.core.SourceType
import com.myframe.tv.data.Album
import com.myframe.tv.data.Media
import com.myframe.tv.data.MediaRepository
import com.myframe.tv.graph
import com.myframe.tv.system.Apps
import com.myframe.tv.ui.kit.ChoiceDialog
import com.myframe.tv.ui.kit.ConfirmDialog
import com.myframe.tv.ui.kit.EdgeH
import com.myframe.tv.ui.kit.EdgeV
import com.myframe.tv.ui.kit.EmptyState
import com.myframe.tv.ui.kit.FocusSurface
import com.myframe.tv.ui.kit.Glyph
import com.myframe.tv.ui.kit.GlyphIcon
import com.myframe.tv.ui.kit.LocalPalette
import com.myframe.tv.ui.kit.MediaCard
import com.myframe.tv.ui.kit.MediaThumb
import com.myframe.tv.ui.kit.PillButton
import com.myframe.tv.ui.kit.ScreenScaffold
import com.myframe.tv.ui.kit.TextInputDialog
import com.myframe.tv.ui.kit.Txt
import com.myframe.tv.ui.kit.Type
import com.myframe.tv.ui.kit.remembered
import com.myframe.tv.ui.kit.rememberFocusMemory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val GRID_GAP = 14.dp
val ALBUM_SUGGESTIONS = listOf("Family", "Holidays", "Travel", "Favourite Memories", "Landscapes")

@Composable
private fun rememberLarge(media: Media?): ImageBitmap? {
    val images = LocalContext.current.graph.images
    val state by produceState<ImageBitmap?>(null, media?.id) {
        // A short pause: moving quickly across the tiles then decodes one backdrop, not one per tile.
        if (value != null) delay(220)
        value = media?.let { images.full(it, 1280, 720)?.asImageBitmap() }
    }
    return state
}

private class Tile(val key: String, val title: String, val blurb: String, val glyph: Glyph, val label: String = title, val open: () -> Unit)

/** Two roomy rows: pictures first, then music, apps and settings. */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val graph = context.graph
    val p = LocalPalette.current
    val art by graph.repo.media.observeArt().collectAsState(emptyList())
    val photos by graph.repo.media.observePhotos().collectAsState(emptyList())
    val memory = rememberFocusMemory("art")
    var focused by rememberSaveable { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    var missing by remember { mutableStateOf<Apps.Missing?>(null) }
    // Re-check which media apps are installed whenever we come back (for example from the Play Store).
    var appsCheck by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { appsCheck++; onPauseOrDispose { } }
    val hasYouTube = remember(appsCheck) { Apps.youTubeInstalled(context) }
    val hasSpotify = remember(appsCheck) { Apps.spotifyInstalled(context) }
    val hasYtMusic = remember(appsCheck) { Apps.youTubeMusicInstalled(context) }
    val getIt = "Not installed. Press OK to get it from the Play Store."
    val featured = remember(art.size) { art.randomOrNull() }
    val time by produceState("") {
        while (true) {
            value = SimpleDateFormat(if (graph.settings.value.clock24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())
            delay(15_000)
        }
    }

    val tiles = listOf(
        Tile("art", "Art Mode", "${art.size} masterpieces, framed and lit like a gallery wall.", Glyph.FRAME) { nav.go(Screen.Art) },
        Tile("photos", "My Photos", if (photos.isEmpty()) "Your own pictures. Add some from your phone to begin." else "${photos.size} photographs from your own life.", Glyph.PHOTO, "Photos") { nav.go(Screen.Photos) },
        Tile("mixed", "Mixed Carousel", "Paintings and photographs together, in saved presets.", Glyph.MIX, "Mixed") { nav.go(Screen.Mixed) },
        Tile("collections", "Collections", "Albums and playlists you arrange yourself.", Glyph.STACK, "Albums") { nav.go(Screen.Collections) },
        Tile("youtube", "YouTube", if (hasYouTube) "Opens the YouTube app installed on this TV." else getIt, Glyph.PLAY) {
            if (!Apps.launchYouTube(context)) missing = Apps.YOUTUBE_STORE
        },
        Tile("spotify", "Spotify", if (hasSpotify) "Start music, then press Home to return to your pictures. Background playback depends on Spotify and the TV." else getIt, Glyph.NOTE) {
            if (!Apps.launchSpotify(context)) missing = Apps.SPOTIFY_STORE
        },
        Tile("ytmusic", "YouTube Music", if (hasYtMusic) "Opens YouTube Music, or YouTube's Music menu. TV playback may stop when you return to art." else "YouTube Music runs inside the YouTube app on TV. YouTube is not installed. Press OK to get it from the Play Store.", Glyph.NOTE, "YT Music") {
            // On TV, YouTube Music is reached through YouTube when it has no app of its own.
            if (!Apps.launchYouTubeMusic(context)) missing = Apps.Missing("YouTube (needed for YouTube Music)", Apps.YOUTUBE_STORE.storePackage)
        },
        Tile("apps", "All Apps", "Everything else installed on this TV.", Glyph.GRID) { nav.go(Screen.Apps) },
        Tile("settings", "Settings", "Frames, slideshow, phone upload and startup.", Glyph.GEAR) { nav.go(Screen.Settings) },
    )
    val current = tiles[focused.coerceIn(0, tiles.lastIndex)]
    val backdrop = if (current.key == "photos" && photos.isNotEmpty()) photos.first() else featured

    missing?.let { app ->
        ConfirmDialog(
            title = "${app.name} is not installed",
            message = "MyFrame opens the real app but cannot include it. Install it from the Google Play Store, then come back and try again.",
            confirmLabel = "Open Play Store",
            onDismiss = { missing = null },
        ) {
            missing = null
            if (!Apps.openStore(context, app.storePackage)) {
                notice = "The Play Store could not be opened. Open it from your TV's home screen and search for ${app.name.substringBefore(" (")}."
            }
        }
    }

    Box(Modifier.fillMaxSize().background(p.bg)) {
        Crossfade(backdrop, animationSpec = tween(700), label = "backdrop") { media ->
            val bmp = rememberLarge(media)
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg.copy(alpha = 0.62f), p.bg.copy(alpha = 0.5f), p.bg.copy(alpha = 0.96f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = EdgeH, vertical = EdgeV)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.FRAME, p.accent, Modifier.size(22.dp))
                Txt("MyFrame", Type.heading, Modifier.padding(start = 10.dp).weight(1f))
                Txt(time, Type.heading, color = p.muted)
            }
            Spacer(Modifier.weight(1f))
            Txt(current.title, Type.display)
            Txt(notice ?: current.blurb, Type.body, Modifier.padding(top = 4.dp, bottom = 22.dp), color = if (notice != null) p.accent else p.muted, maxLines = 2)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
              listOf(tiles.take(4), tiles.drop(4)).forEach { row ->
               Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    val index = tiles.indexOf(tile)
                    FocusSurface(
                        tile.open, Modifier.weight(1f).height(82.dp).remembered(memory, tile.key), scale = 1.04f,
                        background = p.surface.copy(alpha = 0.72f), onFocus = { focused = index; notice = null },
                    ) { isFocused ->
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            GlyphIcon(tile.glyph, if (isFocused) p.accent else p.muted, Modifier.size(24.dp))
                            Txt(tile.label, Type.label, color = if (isFocused) p.accent else p.text, maxLines = 1)
                        }
                    }
                }
               }
              }
            }
        }
    }
}

@Composable
fun ArtScreen() {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val presets by graph.repo.presets.observeAll().collectAsState(emptyList())
    val all by graph.repo.media.observeArtAll().collectAsState(emptyList())
    val preset = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_ART }
    val config = preset?.config
    val memory = rememberFocusMemory("play")
    var manage by rememberSaveable { mutableStateOf(false) }
    val shown = if (manage) all else all.filter { !it.hidden }
    val favOnly = config?.sources?.any { it.type == SourceType.FAV_ART } == true

    ScreenScaffold(
        "Art Mode", overline = "Gallery",
        subtitle = if (manage) "Manage: press OK on a work to hide or show it. Hidden works are skipped." else "${shown.size} public-domain works. Press OK on a painting to show it, or Menu to mark a favourite.",
        actions = {
            if (preset != null && config != null) {
                PillButton("Start Art Mode", { nav.go(Screen.Viewer(preset.id)) }, Modifier.remembered(memory, "play"), primary = true, icon = Glyph.PLAY)
                PillButton(if (config.order == PlayOrder.SHUFFLE) "Shuffle" else "In order", {
                    scope.launch { graph.repo.updateConfig(preset.id) { it.copy(order = if (it.order == PlayOrder.SHUFFLE) PlayOrder.SEQUENTIAL else PlayOrder.SHUFFLE) } }
                }, Modifier.remembered(memory, "order"), selected = config.order == PlayOrder.SHUFFLE, icon = Glyph.SHUFFLE)
                PillButton(if (favOnly) "Favourites only" else "All artworks", {
                    scope.launch { graph.repo.updateConfig(preset.id) { it.copy(sources = listOf(Source(if (favOnly) SourceType.ALL_ART else SourceType.FAV_ART))) } }
                }, Modifier.remembered(memory, "fav"), selected = favOnly, icon = Glyph.HEART)
                PillButton("Frame and slideshow", { nav.go(Screen.Editor(preset.id)) }, Modifier.remembered(memory, "edit"), icon = Glyph.GEAR)
                PillButton(if (manage) "Done" else "Manage", { manage = !manage }, Modifier.remembered(memory, "manage"), selected = manage)
            }
        },
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(5), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            items(shown, key = { it.id }) { m ->
                MediaCard(
                    m, modifier = Modifier.remembered(memory, "m${m.id}"),
                    onClick = {
                        if (manage) scope.launch { graph.repo.media.setHidden(m.id, !m.hidden) }
                        else preset?.let { nav.go(Screen.Viewer(it.id, m.id)) }
                    },
                    onKey = { e ->
                        if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) {
                            scope.launch { graph.repo.media.setFavorite(m.id, !m.favorite) }
                            true
                        } else false
                    },
                )
            }
        }
    }
}

/** What "My Photos" plays: everything, the hand-picked selection, favourites, or one album. */
private fun sourceLabel(source: Source?, albums: List<Album>): String = when (source?.type) {
    SourceType.FAV_PHOTOS -> "Favourites"
    SourceType.COLLECTION -> albums.firstOrNull { it.id == source.collectionId }?.name ?: "Album"
    else -> "All photos"
}

@Composable
fun PhotosScreen() {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val presets by graph.repo.presets.observeAll().collectAsState(emptyList())
    val photos by graph.repo.media.observePhotos().collectAsState(emptyList())
    val albumInfos by graph.repo.albums.observeAll().collectAsState(emptyList())
    val selectionId by produceState(0L) { value = graph.repo.selectionId() }
    val selected by graph.repo.albums.observeItemIds(selectionId).collectAsState(emptyList())
    val preset = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_PHOTOS }
    val config = preset?.config
    val source = config?.sources?.firstOrNull()
    val albums = albumInfos.map { it.album }
    val memory = rememberFocusMemory("play")
    var selecting by rememberSaveable { mutableStateOf(false) }
    var pickSource by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val sourceItems by produceState(emptyList<Media>(), source, photos, selected) {
        value = if (source == null) emptyList() else graph.repo.playlist(listOf(source))
    }
    val shown = if (selecting) photos else sourceItems

    ScreenScaffold(
        "My Photos", overline = "Your pictures",
        subtitle = when {
            photos.isEmpty() -> "No photographs yet."
            selecting -> "${selected.size} selected. Press OK on a photo to add or remove it; only selected photos will be shown."
            else -> "Showing ${sourceLabel(source, albums)} · ${shown.size} of ${photos.size} photographs."
        },
        actions = {
            if (preset != null && config != null) {
                if (!selecting) {
                    PillButton("Start slideshow", { nav.go(Screen.Viewer(preset.id)) }, Modifier.remembered(memory, "play"), primary = true, icon = Glyph.PLAY)
                    PillButton("Show: " + sourceLabel(source, albums), { pickSource = true }, Modifier.remembered(memory, "source"))
                    PillButton("Select photos", { memory.expect("play"); selecting = true }, Modifier.remembered(memory, "select"), icon = Glyph.CHECK)
                    PillButton("Add photos", { nav.go(Screen.Upload) }, Modifier.remembered(memory, "add"), icon = Glyph.PHONE)
                    PillButton("Frame and slideshow", { nav.go(Screen.Editor(preset.id)) }, Modifier.remembered(memory, "edit"), icon = Glyph.GEAR)
                } else {
                    PillButton("Show only these", {
                        scope.launch { graph.repo.updateConfig(preset.id) { it.copy(sources = listOf(Source(SourceType.COLLECTION, selectionId))) } }
                        memory.expect("play")
                        selecting = false
                    }, Modifier.remembered(memory, "play"), primary = true, icon = Glyph.PLAY)
                    PillButton("Save as album", { naming = true }, Modifier.remembered(memory, "save"), icon = Glyph.PLUS)
                    PillButton("Clear", { scope.launch { graph.repo.albums.clear(selectionId) } }, Modifier.remembered(memory, "clear"))
                    PillButton("Delete selected", { if (selected.isNotEmpty()) confirmDelete = true }, Modifier.remembered(memory, "delete"), danger = true)
                    PillButton("Done", { memory.expect("select"); selecting = false }, Modifier.remembered(memory, "done"))
                }
            }
        },
    ) {
        if (photos.isEmpty()) {
            EmptyState("No photos yet", "Send pictures straight from your phone: MyFrame shows a QR code, you scan it and choose photos. No cable or computer needed.") {
                PillButton("Add photos from phone", { nav.go(Screen.Upload) }, Modifier.remembered(memory, "play"), primary = true, icon = Glyph.PHONE)
            }
        } else if (shown.isEmpty()) {
            EmptyState("Nothing in ${sourceLabel(source, albums)}", "Choose Select photos to pick pictures, or switch what is shown.")
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(6), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                items(shown, key = { it.id }) { m ->
                    MediaCard(
                        m, modifier = Modifier.remembered(memory, "m${m.id}"), caption = false,
                        checked = if (selecting) m.id in selected else null,
                        onClick = {
                            if (selecting) scope.launch {
                                if (m.id in selected) graph.repo.albums.removeItems(selectionId, listOf(m.id)) else graph.repo.albums.addItems(selectionId, listOf(m.id))
                            } else preset?.let { nav.go(Screen.Viewer(it.id, m.id)) }
                        },
                        onKey = { e ->
                            if (e.key == Key.Menu && e.type == KeyEventType.KeyUp) {
                                scope.launch { graph.repo.media.setFavorite(m.id, !m.favorite) }
                                true
                            } else false
                        },
                    )
                }
            }
        }
    }

    if (pickSource && preset != null) {
        val options = listOf(Source(SourceType.ALL_PHOTOS) to "All photos", Source(SourceType.FAV_PHOTOS) to "Favourites") +
            albumInfos.map { Source(SourceType.COLLECTION, it.album.id) to "${it.album.name} (${it.itemCount})" }
        ChoiceDialog("Show in My Photos", options.map { it.second }, options.indexOfFirst { it.first == source }, { pickSource = false }) { i ->
            pickSource = false
            scope.launch { graph.repo.updateConfig(preset.id) { it.copy(sources = listOf(options[i].first)) } }
        }
    }
    if (naming) {
        TextInputDialog("New album from selection", "", ALBUM_SUGGESTIONS, { naming = false }) { name ->
            naming = false
            scope.launch {
                val id = graph.repo.albums.insert(Album(name = name, createdAt = System.currentTimeMillis()))
                graph.repo.albums.addItems(id, graph.repo.albums.itemIds(selectionId))
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog("Delete ${selected.size} photos?", "They will be removed from this TV permanently. The originals on your phone are not affected.", "Delete", { confirmDelete = false }, danger = true) {
            confirmDelete = false
            scope.launch { graph.repo.deleteMedia(selected) }
        }
    }
}

@Composable
fun CollectionsScreen() {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val p = LocalPalette.current
    val infos by graph.repo.albums.observeAll().collectAsState(emptyList())
    val memory = rememberFocusMemory("new")
    var naming by remember { mutableStateOf(false) }

    ScreenScaffold(
        "Collections", overline = "Albums and playlists", subtitle = "Group paintings and photographs in any order, then play a collection or use it in a preset.",
        actions = { PillButton("New collection", { naming = true }, Modifier.remembered(memory, "new"), primary = true, icon = Glyph.PLUS) },
    ) {
        LazyVerticalGrid(
            GridCells.Fixed(4), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
        ) {
            items(infos, key = { it.album.id }) { info ->
                val cover by produceState<Media?>(null, info.coverId) { value = info.coverId?.let { graph.repo.media.byId(it) } }
                FocusSurface({ nav.go(Screen.Collection(info.album.id)) }, Modifier.remembered(memory, "a${info.album.id}"), scale = 1.05f) { focused ->
                    Column {
                        MediaThumb(cover, Modifier.fillMaxWidth().height(110.dp))
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                            Txt(info.album.name, Type.heading, color = if (focused) p.accent else p.text, maxLines = 1)
                            Txt(if (info.itemCount == 1) "1 picture" else "${info.itemCount} pictures", Type.caption, color = p.muted)
                        }
                    }
                }
            }
        }
    }
    if (naming) {
        TextInputDialog("New collection", "", ALBUM_SUGGESTIONS, { naming = false }) { name ->
            naming = false
            scope.launch { nav.go(Screen.Collection(graph.repo.albums.insert(Album(name = name, createdAt = System.currentTimeMillis())))) }
        }
    }
}

@Composable
fun CollectionScreen(albumId: Long) {
    val nav = LocalNav.current
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val infos by graph.repo.albums.observeAll().collectAsState(emptyList())
    val items by graph.repo.albums.observeItems(albumId).collectAsState(emptyList())
    val album = infos.firstOrNull { it.album.id == albumId }?.album
    val memory = rememberFocusMemory("play")
    var mode by rememberSaveable { mutableStateOf("view") }
    var grabbed by remember { mutableStateOf(0L) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val system = album?.systemKey?.isNotEmpty() == true

    ScreenScaffold(
        album?.name ?: "Collection", overline = "Collection",
        subtitle = when (mode) {
            "reorder" -> if (grabbed != 0L) "Moving: press left or right to move the picture, OK to drop it." else "Reorder: press OK on a picture to pick it up."
            "remove" -> "Remove: press OK on a picture to take it out of this collection. Nothing is deleted from the TV."
            else -> "${items.size} pictures, shown in this order."
        },
        actions = {
            PillButton("Play", {
                scope.launch {
                    // Collections play through the Mixed Gallery style without changing any saved preset's sources.
                    val base = graph.repo.presets.listAll().firstOrNull { it.name == "Collection player" }
                        ?: graph.repo.presets.byBuiltin(MediaRepository.BUILTIN_PHOTOS)?.let { it.copy(id = 0, name = "Collection player", builtin = "", sortOrder = 99) }?.let { it.copy(id = graph.repo.presets.insert(it)) }
                    if (base != null) {
                        graph.repo.updateConfig(base.id) { it.copy(sources = listOf(Source(SourceType.COLLECTION, albumId))) }
                        nav.go(Screen.Viewer(base.id))
                    }
                }
            }, Modifier.remembered(memory, "play"), primary = true, icon = Glyph.PLAY)
            PillButton("Add pictures", { nav.go(Screen.Picker(albumId)) }, Modifier.remembered(memory, "add"), icon = Glyph.PLUS)
            PillButton(if (mode == "reorder") "Done" else "Reorder", { mode = if (mode == "reorder") "view" else "reorder"; grabbed = 0 }, Modifier.remembered(memory, "reorder"), selected = mode == "reorder", icon = Glyph.MOVE)
            PillButton(if (mode == "remove") "Done" else "Remove", { mode = if (mode == "remove") "view" else "remove" }, Modifier.remembered(memory, "remove"), selected = mode == "remove")
            if (!system) {
                PillButton("Rename", { renaming = true }, Modifier.remembered(memory, "rename"))
                PillButton("Delete", { deleting = true }, Modifier.remembered(memory, "delete"), danger = true)
            }
        },
    ) {
        if (items.isEmpty()) {
            EmptyState("This collection is empty", "Choose Add pictures to pick paintings and photographs for it.")
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(6), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                items(items, key = { it.id }) { m ->
                    MediaCard(
                        m, modifier = Modifier.remembered(memory, "m${m.id}"), caption = false, grabbed = grabbed == m.id,
                        onClick = {
                            when (mode) {
                                "reorder" -> grabbed = if (grabbed == m.id) 0 else m.id
                                "remove" -> scope.launch { graph.repo.albums.removeItems(albumId, listOf(m.id)) }
                                else -> Unit
                            }
                        },
                        onKey = { e ->
                            val delta = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                            if (grabbed == m.id && delta != 0) {
                                if (e.type == KeyEventType.KeyDown) scope.launch { graph.repo.albums.move(albumId, m.id, delta) }
                                true
                            } else false
                        },
                    )
                }
            }
        }
    }
    if (renaming && album != null) {
        TextInputDialog("Rename collection", album.name, ALBUM_SUGGESTIONS, { renaming = false }) { name ->
            renaming = false
            scope.launch { graph.repo.albums.rename(albumId, name) }
        }
    }
    if (deleting && album != null) {
        ConfirmDialog("Delete \"${album.name}\"?", "The collection is removed. The pictures in it stay in your library.", "Delete", { deleting = false }, danger = true) {
            deleting = false
            scope.launch { graph.repo.albums.delete(albumId) }
            nav.back()
        }
    }
}

/** Tick paintings and photographs in or out of a collection. */
@Composable
fun PickerScreen(albumId: Long) {
    val graph = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val art by graph.repo.media.observeArt().collectAsState(emptyList())
    val photos by graph.repo.media.observePhotos().collectAsState(emptyList())
    val inAlbum by graph.repo.albums.observeItemIds(albumId).collectAsState(emptyList())
    val memory = rememberFocusMemory("photos")
    var showArt by rememberSaveable { mutableStateOf(false) }
    val nav = LocalNav.current

    ScreenScaffold(
        "Add pictures", overline = "Collection", subtitle = "${inAlbum.size} in this collection. Press OK to add or remove a picture.",
        actions = {
            PillButton("Photographs (${photos.size})", { showArt = false }, Modifier.remembered(memory, "photos"), selected = !showArt, icon = Glyph.PHOTO)
            PillButton("Paintings (${art.size})", { showArt = true }, Modifier.remembered(memory, "art"), selected = showArt, icon = Glyph.FRAME)
            PillButton("Done", { nav.back() }, Modifier.remembered(memory, "done"), primary = true)
        },
    ) {
        val list = if (showArt) art else photos
        if (list.isEmpty()) {
            EmptyState("Nothing here yet", "Add photographs from your phone first, then come back to arrange them.")
        } else {
            LazyVerticalGrid(
                GridCells.Fixed(6), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                items(list, key = { it.id }) { m ->
                    MediaCard(m, modifier = Modifier.remembered(memory, "m${m.id}"), caption = showArt, checked = m.id in inAlbum, onClick = {
                        scope.launch {
                            if (m.id in inAlbum) graph.repo.albums.removeItems(albumId, listOf(m.id)) else graph.repo.albums.addItems(albumId, listOf(m.id))
                        }
                    })
                }
            }
        }
    }
}

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val p = LocalPalette.current
    val apps by produceState<List<Apps.Entry>?>(null) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Apps.list(context) } }
    val memory = rememberFocusMemory("first")
    ScreenScaffold("All Apps", overline = "This TV", subtitle = "Open another app. Press Home on the remote, or reopen MyFrame, to come back to your gallery.") {
        val list = apps
        when {
            list == null -> Txt("Looking for apps…", Type.body, color = p.muted)
            list.isEmpty() -> EmptyState("No other apps found", "Apps you install on this TV will appear here.")
            else -> LazyVerticalGrid(
                GridCells.Fixed(5), contentPadding = PaddingValues(6.dp, 8.dp, 6.dp, 24.dp),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP), verticalArrangement = Arrangement.spacedBy(GRID_GAP),
            ) {
                items(list, key = { it.pkg }) { app ->
                    FocusSurface({ Apps.launch(context, app.pkg) }, Modifier.remembered(memory, if (app == list.first()) "first" else app.pkg), scale = 1.06f) { focused ->
                        Column {
                            Box(Modifier.fillMaxWidth().height(92.dp).background(p.surfaceHi), contentAlignment = Alignment.Center) {
                                app.icon?.let { Image(it, app.label, if (app.wide) Modifier.fillMaxSize() else Modifier.size(56.dp), contentScale = if (app.wide) ContentScale.Crop else ContentScale.Fit) }
                            }
                            Txt(app.label, Type.label, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = if (focused) p.accent else p.text, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
