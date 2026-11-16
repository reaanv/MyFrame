package com.myframe.tv.ui.viewer

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.currentStateAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.myframe.tv.core.FrameConfig
import com.myframe.tv.core.FrameGeometry
import com.myframe.tv.core.FrameStyle
import com.myframe.tv.core.MediaKind
import com.myframe.tv.core.PlaybackConfig
import com.myframe.tv.core.ScaleMode
import com.myframe.tv.core.SlideshowEngine
import com.myframe.tv.core.Transition
import com.myframe.tv.core.formatDuration
import com.myframe.tv.core.slideSeconds
import com.myframe.tv.data.Media
import com.myframe.tv.data.Preset
import com.myframe.tv.graph
import com.myframe.tv.ui.kit.EmptyState
import com.myframe.tv.ui.kit.Glyph
import com.myframe.tv.ui.kit.LocalPalette
import com.myframe.tv.ui.kit.PillButton
import com.myframe.tv.ui.kit.Txt
import com.myframe.tv.ui.kit.Type
import com.myframe.tv.ui.kit.rememberInitialFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class Slide(val media: Media, val image: ImageBitmap, val blur: ImageBitmap?)

/** The picture with its mount: everything inside the frame moulding. Opaque, so two can be cross-dissolved. */
@Composable
fun FramedPicture(slide: Slide, config: PlaybackConfig, modifier: Modifier = Modifier, motion: () -> Float = { 0f }) {
    val frame = config.frame
    Canvas(modifier.fillMaxSize()) {
        val srcW = if (slide.media.width > 0) slide.media.width else slide.image.width
        val srcH = if (slide.media.height > 0) slide.media.height else slide.image.height
        val lay = FrameGeometry.compute(size.width, size.height, srcW, srcH, frame, config.scaleMode, slide.media.alignX, slide.media.alignY, slide.media.zoom)
        val hasMount = frame.mountThickness > 0.001f
        drawRect(Color(frame.backgroundColor))

        val blur = slide.blur
        if (blur != null && config.blurBackground && !hasMount) {
            // Cover-crop the tiny blurred copy so the backdrop is never stretched either.
            val screenAspect = size.width / size.height
            val blurAspect = blur.width.toFloat() / blur.height
            val sw = if (blurAspect > screenAspect) (blur.height * screenAspect).toInt() else blur.width
            val sh = if (blurAspect > screenAspect) blur.height else (blur.width / screenAspect).toInt()
            drawImage(
                blur, srcOffset = IntOffset((blur.width - sw) / 2, (blur.height - sh) / 2), srcSize = IntSize(max(1, sw), max(1, sh)),
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = 0.7f, filterQuality = FilterQuality.Low,
            )
            drawRect(Color.Black.copy(alpha = 0.3f))
        }
        if (hasMount) {
            val fi = lay.frameInner
            drawRect(Color(frame.mountColor), Offset(fi.left, fi.top), Size(fi.width, fi.height))
            if (frame.texture) {
                // A whisper of light falloff gives the mount board some depth.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.07f)), fi.top, fi.bottom), Offset(fi.left, fi.top), Size(fi.width, fi.height))
            }
        }

        val o = lay.opening
        val im = lay.image
        clipRect(o.left, o.top, o.right, o.bottom) {
            val zoom = 1f + 0.07f * motion()
            scale(zoom, zoom, Offset(o.left + o.width * 0.5f, o.top + o.height * 0.45f)) {
                drawImage(
                    slide.image, dstOffset = IntOffset(im.left.roundToInt(), im.top.roundToInt()),
                    dstSize = IntSize(max(1, im.width.roundToInt()), max(1, im.height.roundToInt())), filterQuality = FilterQuality.Medium,
                )
            }
        }
        if (hasMount) {
            // The bevelled cut of the mount, then the shadow it throws onto the picture.
            val b = max(2f, size.height * 0.0032f)
            drawRect(lerp(Color(frame.mountColor), Color.White, 0.55f), Offset(o.left - b, o.top - b), Size(o.width + 2 * b, o.height + 2 * b), style = Stroke(b))
            if (frame.shadow) {
                val d = b * 5
                drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent), o.top, o.top + d), Offset(o.left, o.top), Size(o.width, d))
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), o.left, o.left + d), Offset(o.left, o.top), Size(d, o.height))
            }
        }
    }
}

/** The frame moulding itself: four mitred sides lit from the top left, optional wood grain, and its inner shadow. */
@Composable
fun FrameMoulding(frame: FrameConfig, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val t = frame.frameThickness.coerceIn(0f, FrameConfig.MAX_FRAME) * size.height
        if (frame.style == FrameStyle.BORDERLESS || t < 1f) return@Canvas
        val w = size.width
        val h = size.height
        val base = Color(frame.frameColor)
        fun shade(f: Float) = lerp(base, if (f > 0) Color.White else Color.Black, abs(f))
        fun quad(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) =
            Path().apply { moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); lineTo(dx, dy); close() }

        drawPath(quad(0f, 0f, w, 0f, w - t, t, t, t), Brush.verticalGradient(listOf(shade(0.18f), shade(-0.05f)), 0f, t))
        drawPath(quad(0f, h, w, h, w - t, h - t, t, h - t), Brush.verticalGradient(listOf(shade(-0.12f), shade(-0.3f)), h - t, h))
        drawPath(quad(0f, 0f, t, t, t, h - t, 0f, h), Brush.horizontalGradient(listOf(shade(0.12f), shade(-0.08f)), 0f, t))
        drawPath(quad(w, 0f, w - t, t, w - t, h - t, w, h), Brush.horizontalGradient(listOf(shade(-0.08f), shade(-0.26f)), w - t, w))

        if (frame.texture && frame.style.wood) {
            val rnd = Random(11)
            repeat(64) {
                val d = rnd.nextFloat() * t
                val c = Color.Black.copy(alpha = 0.03f + rnd.nextFloat() * 0.08f)
                drawLine(c, Offset(d, d), Offset(w - d, d), 1f)
                drawLine(c, Offset(d, h - d), Offset(w - d, h - d), 1f)
                drawLine(c, Offset(d, d), Offset(d, h - d), 1f)
                drawLine(c, Offset(w - d, d), Offset(w - d, h - d), 1f)
            }
        }
        val mitre = Color.Black.copy(alpha = 0.28f)
        drawLine(mitre, Offset(0f, 0f), Offset(t, t), 1.5f)
        drawLine(mitre, Offset(w, 0f), Offset(w - t, t), 1.5f)
        drawLine(mitre, Offset(0f, h), Offset(t, h - t), 1.5f)
        drawLine(mitre, Offset(w, h), Offset(w - t, h - t), 1.5f)
        drawRect(shade(0.35f).copy(alpha = 0.55f), Offset(t, t), Size(w - 2 * t, h - 2 * t), style = Stroke(max(1f, t * 0.05f)))

        if (frame.shadow) {
            val s = max(8f, t * 0.7f)
            drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent), t, t + s), Offset(t, t), Size(w - 2 * t, s))
            drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent), t, t + s), Offset(t, t), Size(s, h - 2 * t))
        }
    }
}

private val TIME_STEPS = listOf(0, 10, 30, 60, 300, 900, 3600)
private val CROP_STEPS = listOf(0.5f to 0.5f, 0.5f to 0f, 0.5f to 1f, 0f to 0.5f, 1f to 0.5f)
private val CROP_NAMES = listOf("Centre", "Top", "Bottom", "Left", "Right")

/**
 * Full-screen playback. With the menu closed nothing but the picture is on screen:
 * left/right change picture, up toggles the caption, OK or down opens the controls, Back leaves.
 */
@Composable
fun ViewerScreen(presetId: Long, startMediaId: Long, onExit: () -> Unit, onEdit: (Long) -> Unit, onAddPhotos: () -> Unit) {
    val context = LocalContext.current
    val graph = context.graph
    val scope = rememberCoroutineScope()
    val settings = graph.settings.flow.value
    // Re-read the clock once a minute: evening dimming, the night screen and the burn-in shift all follow the time.
    val startedAt = remember { android.os.SystemClock.elapsedRealtime() }
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(60_000); value = System.currentTimeMillis() } }
    val cal = remember(now) { java.util.Calendar.getInstance().apply { timeInMillis = now } }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val evening = if (settings.eveningDim) com.myframe.tv.core.Ambience.eveningDim(hour, cal.get(java.util.Calendar.MINUTE)) else 0f
    val dim = settings.dimLevel + evening
    val shift = if (settings.burnInShift) com.myframe.tv.core.Ambience.drift((android.os.SystemClock.elapsedRealtime() - startedAt) / 60_000) else (0 to 0)
    // Any remote button lights the screen for ten minutes during the night window.
    var wakeUntil by remember { mutableStateOf(0L) }
    val asleep = settings.nightOff && com.myframe.tv.core.Ambience.isNight(hour, settings.nightStartHour, settings.nightEndHour) && now >= wakeUntil

    // False while another app (Spotify, YouTube) is in front: the slideshow then stops decoding pictures nobody sees.
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val visible = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

    var preset by remember { mutableStateOf<Preset?>(null) }
    var config by remember { mutableStateOf(PlaybackConfig()) }
    var engine by remember { mutableStateOf<SlideshowEngine<Long>?>(null) }
    var items by remember { mutableStateOf<Map<Long, Media>>(emptyMap()) }
    var tick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf<Slide?>(null) }
    var back by remember { mutableStateOf<Slide?>(null) }
    var paused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }
    var centerDown by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }
    val motion = remember { Animatable(0f) }
    val rootFocus = remember { FocusRequester() }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(presetId) {
        val p = graph.repo.presets.byId(presetId) ?: graph.repo.presets.byBuiltin("art")
        if (p != null) {
            val list = graph.repo.playlist(p.config.sources)
            val start = list.firstOrNull { it.id == startMediaId } ?: list.firstOrNull { it.id == p.lastMediaId }
            items = list.associateBy { it.id }
            engine = SlideshowEngine(list.map { it.id }, p.config.order, p.config.repeat, start = start?.id)
            config = p.config
            preset = p
            graph.settings.update { it.copy(lastPresetId = p.id) }
        }
        loaded = true
    }

    fun change(block: (PlaybackConfig) -> PlaybackConfig) {
        config = block(config)
        val id = preset?.id ?: return
        scope.launch { graph.repo.updateConfig(id, block) }
    }
    fun step(forward: Boolean) {
        val e = engine ?: return
        if (if (forward) e.next() else e.previous()) tick++
    }
    fun patch(m: Media) {
        items = items + (m.id to m)
        front?.let { if (it.media.id == m.id) front = Slide(m, it.image, it.blur) }
        scope.launch { graph.repo.media.update(m) }
    }

    val currentId = remember(tick, engine) { engine?.current }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight

        // Decode each picture at the size it is drawn inside its frame, not at screen size.
        fun sizeFor(m: Media): Pair<Int, Int> = FrameGeometry.decodeSize(
            w, h, m.width, m.height, config.frame, config.scaleMode, m.alignX, m.alignY, m.zoom, extra = if (config.kenBurns) 1.07f else 1f,
        )

        // Load the current picture, then run the transition from the previous one.
        LaunchedEffect(currentId, config.blurBackground, config.frame, config.scaleMode, config.kenBurns, visible) {
            if (!visible) {
                front = null
                back = null
                graph.images.trimMemory()
                return@LaunchedEffect
            }
            val m = currentId?.let { items[it] } ?: return@LaunchedEffect
            val (dw, dh) = sizeFor(m)
            val bmp = graph.images.full(m, dw, dh)
            if (bmp == null) {
                // Missing or corrupt file: skip it, but stop once every item has failed.
                failures++
                if (failures < (engine?.size ?: 0)) step(true)
                return@LaunchedEffect
            }
            failures = 0
            val blur = if (config.blurBackground) graph.images.blurred(m)?.asImageBitmap() else null
            val previous = front
            if (previous?.media?.id != m.id) back = previous
            front = Slide(m, bmp.asImageBitmap(), blur)
            preset?.let { graph.repo.presets.setLast(it.id, m.id) }
            launch { engine?.peekNext()?.let { id -> items[id]?.let { n -> sizeFor(n).let { (nw, nh) -> graph.images.full(n, nw, nh) } } } }
            motion.snapTo(0f)
            if (back != null && config.transition != Transition.NONE) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(config.transitionMs.coerceIn(200, 6000), easing = FastOutSlowInEasing))
            } else {
                progress.snapTo(1f)
            }
            back = null
        }

        // The slide timer and the optional slow drift. Both stop while paused or while the menu is open.
        LaunchedEffect(front?.media?.id, paused, menu, visible, config.autoAdvance, config.durationSec, config.kenBurns) {
            val m = front?.media ?: return@LaunchedEffect
            if (paused || menu || !visible) return@LaunchedEffect
            val ms = slideSeconds(config.durationSec, m.durationSec) * 1000L
            if (config.kenBurns) launch { motion.animateTo(1f, tween(ms.coerceAtMost(180_000L).toInt(), easing = LinearEasing)) }
            if (!config.autoAdvance) return@LaunchedEffect
            delay(ms)
            if (engine?.next(auto = true) == true) tick++
        }

        LaunchedEffect(menu, loaded) { if (!menu) runCatching { rootFocus.requestFocus() } }
        BackHandler(enabled = menu) { menu = false }

        fun onKey(e: KeyEvent): Boolean {
            if (asleep) {
                // The first press only wakes the screen; it is not passed on, so nothing is changed by accident.
                wakeUntil = System.currentTimeMillis() + 10 * 60_000
                return true
            }
            // Back is handled here, ahead of focus handling: because this root is focusable, Compose would
            // otherwise spend the first Back press moving focus from a button up to the root.
            val empty = loaded && engine?.isEmpty != false
            if ((e.key == Key.Back || e.key == Key.Escape) && (menu || empty)) {
                if (e.type == KeyEventType.KeyUp) { if (menu) menu = false else onExit() }
                return true
            }
            if (menu || empty) return false
            val center = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
            if (e.type == KeyEventType.KeyDown) {
                when {
                    center -> { centerDown = true; return true }
                    e.key == Key.DirectionRight || e.key == Key.MediaNext -> { step(true); return true }
                    e.key == Key.DirectionLeft || e.key == Key.MediaPrevious -> { step(false); return true }
                    e.key == Key.DirectionUp -> { change { it.copy(showMetadata = !it.showMetadata) }; return true }
                    e.key == Key.DirectionDown -> { menu = true; return true }
                    e.key == Key.MediaPlayPause || e.key == Key.MediaPlay || e.key == Key.MediaPause || e.key == Key.Spacebar -> { paused = !paused; return true }
                }
            } else if (e.type == KeyEventType.KeyUp && center && centerDown) {
                centerDown = false
                menu = true
                return true
            }
            return false
        }

        Box(
            Modifier.fillMaxSize().background(Color(config.frame.backgroundColor))
                .focusRequester(rootFocus).focusable().onPreviewKeyEvent(::onKey)
                .graphicsLayer { translationX = shift.first.toFloat(); translationY = shift.second.toFloat() },
        ) {
            val f = front
            val b = back
            if (b != null) {
                FramedPicture(b, config, Modifier.graphicsLayer {
                    val p = progress.value
                    when (config.transition) {
                        Transition.FADE -> alpha = (1f - p * 2f).coerceIn(0f, 1f)
                        Transition.SLIDE -> translationX = -size.width * p
                        else -> Unit
                    }
                })
            }
            if (f != null) {
                FramedPicture(f, config, Modifier.graphicsLayer {
                    val p = progress.value
                    when (config.transition) {
                        Transition.DISSOLVE -> alpha = p
                        Transition.FADE -> alpha = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
                        Transition.SLIDE -> translationX = size.width * (1f - p)
                        Transition.NONE -> Unit
                    }
                }, motion = { motion.value })
            }
            FrameMoulding(config.frame)
            if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.coerceIn(0f, 0.8f))))
            // A faint amber wash in the evening takes the blue edge off, like warm gallery lighting.
            if (evening > 0f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8A2B).copy(alpha = evening * 0.12f)))

            val inset = (config.frame.frameThickness * 540).dp + 20.dp
            if (f != null) {
                val photo = f.media.kind == MediaKind.PHOTO
                val showCaption = menu || if (photo) (config.showDate || config.showFilename || config.showMetadata) else config.showMetadata
                AnimatedVisibility(showCaption, Modifier.align(Alignment.BottomStart).padding(start = inset, top = inset, end = inset, bottom = if (menu) 96.dp else inset), enter = fadeIn(), exit = fadeOut()) {
                    Caption(f.media, config, menu)
                }
            }
            if (config.showClock) Clock(graph.settings.flow.value.clock24h, Modifier.align(Alignment.BottomEnd).padding(inset))
            if (paused && !menu) Tag("Paused", Modifier.align(Alignment.TopEnd).padding(inset))

            if (loaded && engine?.isEmpty != false) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg), contentAlignment = Alignment.Center) {
                    EmptyState("Nothing to show yet", "This slideshow has no pictures. Add photos from your phone, or choose different sources for it.") {
                        PillButton("Add photos from phone", onAddPhotos, Modifier.focusRequester(rememberInitialFocus()), primary = true, icon = Glyph.PHONE)
                        preset?.let { p -> PillButton("Choose sources", { onEdit(p.id) }) }
                        PillButton("Back", onExit)
                    }
                }
            }

            AnimatedVisibility(menu && !asleep && f != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                val m = f?.media
                Row(
                    Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))).padding(start = 40.dp, end = 40.dp, top = 70.dp, bottom = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m != null) {
                        PillButton("Previous", { step(false) }, icon = Glyph.PREV)
                        PillButton(if (paused) "Play" else "Pause", { paused = !paused }, Modifier.focusRequester(rememberInitialFocus()), primary = true, icon = if (paused) Glyph.PLAY else Glyph.PAUSE)
                        PillButton("Next", { step(true) }, icon = Glyph.NEXT)
                        PillButton("Favourite", { patch(m.copy(favorite = !m.favorite)) }, selected = m.favorite, icon = Glyph.HEART)
                        PillButton(if (config.autoAdvance) "Hold" else "Resume", { change { it.copy(autoAdvance = !it.autoAdvance) } }, selected = !config.autoAdvance, icon = Glyph.HOLD)
                        PillButton("Time: " + formatDuration(m.durationSec), { patch(m.copy(durationSec = TIME_STEPS[(TIME_STEPS.indexOf(m.durationSec) + 1).mod(TIME_STEPS.size)])) })
                        if (config.scaleMode == ScaleMode.FILL) {
                            val i = CROP_STEPS.indexOfFirst { it.first == m.alignX && it.second == m.alignY }.coerceAtLeast(0)
                            PillButton("Crop: " + CROP_NAMES[i], { CROP_STEPS[(i + 1) % CROP_STEPS.size].let { (x, y) -> patch(m.copy(alignX = x, alignY = y)) } })
                        }
                        preset?.let { p -> PillButton("Options", { onEdit(p.id) }, icon = Glyph.GEAR) }
                    }
                }
            }
            // Last layer, so nothing (captions, clock, controls) shows through at night.
            AnimatedVisibility(asleep, enter = fadeIn(), exit = fadeOut()) {
                // Cancels the burn-in drift so the black layer always covers the whole screen.
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = -shift.first.toFloat(); translationY = -shift.second.toFloat() }.background(Color.Black))
            }
        }
    }
}

/** A museum-style label. Photographs show their date or file name only when asked to. */
@Composable
private fun Caption(m: Media, config: PlaybackConfig, full: Boolean) {
    val photo = m.kind == MediaKind.PHOTO
    val title = if (photo && !config.showFilename && !full && !config.showMetadata) "" else m.title
    val date = if (photo) m.takenAt.takeIf { it > 0 && (config.showDate || full) }?.let { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(it)) }.orEmpty() else m.dateText
    val second = listOf(m.artist, date).filter { it.isNotBlank() }.joinToString(", ")
    Column(Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xB8000000)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (title.isNotBlank()) Txt(title, Type.heading, color = Color(0xFFF4EFE6), maxLines = 2)
        if (second.isNotBlank()) Txt(second, Type.italic, color = Color(0xFFD8D1C3), maxLines = 1)
        if (full && m.credit.isNotBlank()) Txt(m.credit, Type.caption, Modifier.padding(top = 4.dp), color = Color(0xFFA9A397), maxLines = 2)
    }
}

@Composable
private fun Clock(is24h: Boolean, modifier: Modifier) {
    val time by produceState("") {
        while (true) {
            value = SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date())
            delay(15_000)
        }
    }
    Txt(time, Type.title, modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x8C000000)).padding(horizontal = 14.dp, vertical = 4.dp), color = Color(0xFFF4EFE6))
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Txt(text, Type.label, modifier.clip(RoundedCornerShape(50)).background(Color(0xB3000000)).padding(horizontal = 14.dp, vertical = 6.dp), color = Color.White)
}
