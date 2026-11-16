package com.myframe.tv.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.myframe.tv.R
import com.myframe.tv.core.ThemeMode
import com.myframe.tv.data.Media
import com.myframe.tv.graph

// ---------- Theme ----------

data class Palette(
    val bg: Color, val surface: Color, val surfaceHi: Color, val text: Color, val muted: Color,
    val accent: Color, val onAccent: Color, val outline: Color, val danger: Color,
)

fun paletteFor(mode: ThemeMode): Palette = when (mode) {
    ThemeMode.GALLERY -> Palette(Color(0xFF0E0D0B), Color(0xFF1B1A17), Color(0xFF2A2723), Color(0xFFF2EDE4), Color(0xFFA7A195), Color(0xFFC9A66B), Color(0xFF15110A), Color(0x2EF2EDE4), Color(0xFFE08573))
    ThemeMode.MIDNIGHT -> Palette(Color(0xFF090E18), Color(0xFF131B2B), Color(0xFF1E2A42), Color(0xFFE9EEF6), Color(0xFF97A3B8), Color(0xFFBCCBE2), Color(0xFF0A0F1A), Color(0x2EE9EEF6), Color(0xFFE08573))
    ThemeMode.LINEN -> Palette(Color(0xFFF3EEE4), Color(0xFFE8E1D3), Color(0xFFDBD1BF), Color(0xFF1F1C17), Color(0xFF686256), Color(0xFF86662E), Color(0xFFFFFFFF), Color(0x331F1C17), Color(0xFFA5402E))
}

val LocalPalette = staticCompositionLocalOf { paletteFor(ThemeMode.GALLERY) }

@OptIn(ExperimentalTextApi::class)
private val Serif = FontFamily(
    Font(R.font.cormorant, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.cormorant, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.cormorant_italic, FontWeight.Medium, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
)

/** Type scale tuned for a 1080p TV seen from a sofa: serif for names and titles, sans for everything functional. */
object Type {
    val display = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 46.sp, lineHeight = 50.sp)
    val title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 36.sp)
    val heading = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp)
    val italic = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp)
    val label = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp)
    val overline = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 2.2.sp)
}

/** Safe margins: keeps content clear of TV overscan. */
val EdgeH = 48.dp
val EdgeV = 28.dp

@Composable
fun Txt(
    text: String,
    style: TextStyle = Type.body,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.text,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = style.copy(color = color, textAlign = align ?: TextAlign.Unspecified),
    )
}

// ---------- Focus ----------

/**
 * Remembers which element of a screen had focus so that coming back lands on the same place.
 *
 * While a restore is [pending], focus that Compose hands to some other element first (it does this when the
 * previously focused element disappears) is not recorded, and the intended element keeps asking until it wins.
 * The window is time-limited so a key that never appears cannot block normal focus tracking.
 */
class FocusMemory(initial: String, private val save: (String) -> Unit) {
    var lastKey: String = initial
        private set
    private var armed = true
    private var deadline = android.os.SystemClock.uptimeMillis() + WINDOW_MS

    val pending: Boolean get() = armed && android.os.SystemClock.uptimeMillis() < deadline

    /** Ask for focus to land on [key] when an element with that key next appears (used when a toolbar changes). */
    fun expect(key: String) {
        lastKey = key
        save(key)
        armed = true
        deadline = android.os.SystemClock.uptimeMillis() + WINDOW_MS
    }

    fun onFocused(key: String) {
        if (pending && key != lastKey) return
        armed = false
        if (key != lastKey) { lastKey = key; save(key) }
    }

    private companion object { const val WINDOW_MS = 2500L }
}

@Composable
fun rememberFocusMemory(default: String): FocusMemory {
    var saved by rememberSaveable { mutableStateOf(default) }
    return remember { FocusMemory(saved) { saved = it } }
}

/** Tags an element with a key in [memory]; the element whose key was focused last takes focus on entry. */
@Composable
fun Modifier.remembered(memory: FocusMemory, key: String): Modifier {
    val requester = remember { FocusRequester() }
    LaunchedEffect(key) {
        if (memory.pending && memory.lastKey == key) {
            for (attempt in 0 until 30) {
                withFrameNanos { }
                if (!memory.pending) break
                runCatching { requester.requestFocus() }
            }
        }
    }
    return this.focusRequester(requester).onFocusChanged { if (it.hasFocus) memory.onFocused(key) }
}

@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
    return requester
}

/** The one focusable building block: every interactive element shares this focus treatment. */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    scale: Float = 1.04f,
    background: Color = LocalPalette.current.surface,
    focusedBackground: Color = LocalPalette.current.surfaceHi,
    onFocus: (() -> Unit)? = null,
    onKey: ((KeyEvent) -> Boolean)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val p = LocalPalette.current
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, tween(160), label = "focusScale")
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = s; scaleY = s }
            .onFocusChanged {
                if (it.isFocused && !focused) onFocus?.invoke()
                focused = it.isFocused
            }
            .then(if (onKey != null) Modifier.onPreviewKeyEvent(onKey) else Modifier)
            .clip(shape)
            .background(if (focused) focusedBackground else background)
            .border(if (focused) 2.dp else 1.dp, if (focused) p.accent else p.outline, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        content = { content(focused) },
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    selected: Boolean = false,
    danger: Boolean = false,
    icon: Glyph? = null,
) {
    val p = LocalPalette.current
    FocusSurface(
        onClick, modifier, shape = RoundedCornerShape(50),
        background = when { primary -> p.accent.copy(alpha = 0.16f); selected -> p.surfaceHi; else -> p.surface },
        focusedBackground = if (danger) p.danger else p.accent,
    ) { focused ->
        val fg = when { focused -> p.onAccent; danger -> p.danger; primary || selected -> p.accent; else -> p.text }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                GlyphIcon(icon, fg, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Txt(text, Type.label, color = fg, maxLines = 1)
        }
    }
}

/** A settings line: title on the left, current value on the right. OK steps to the next value or opens a list. */
@Composable
fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val p = LocalPalette.current
    FocusSurface(onClick, modifier.fillMaxWidth(), scale = 1.015f) { focused ->
        Row(Modifier.padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt(title, Type.label, maxLines = 1)
                if (subtitle != null) Txt(subtitle, Type.caption, color = p.muted, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.width(16.dp))
            Txt(value, Type.label, color = if (focused) p.accent else p.muted, maxLines = 1)
        }
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    SettingRow(title, if (checked) "On" else "Off", { onToggle(!checked) }, modifier, subtitle)
}

/** Opens a list for long option sets; for two or three options OK simply steps through them. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val index = options.indexOfFirst { it.first == selected }
    SettingRow(title, options.getOrNull(index)?.second ?: "—", {
        if (options.size <= 3) onSelect(options[(index + 1).mod(options.size)].first) else open = true
    }, modifier, subtitle)
    if (open) {
        ChoiceDialog(title, options.map { it.second }, index, onDismiss = { open = false }) {
            open = false
            onSelect(options[it].first)
        }
    }
}

@Composable
fun ChoiceDialog(title: String, labels: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(min = 300.dp, max = 420.dp).clip(RoundedCornerShape(16.dp)).background(p.bg)
                .border(1.dp, p.outline, RoundedCornerShape(16.dp)).padding(20.dp),
        ) {
            Txt(title, Type.heading, modifier = Modifier.padding(bottom = 12.dp))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed { i, label ->
                    val requester = if (i == selected.coerceAtLeast(0)) rememberInitialFocus() else remember { FocusRequester() }
                    FocusSurface({ onPick(i) }, Modifier.fillMaxWidth().focusRequester(requester).padding(horizontal = 4.dp), scale = 1.02f) { focused ->
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Txt(label, Type.label, Modifier.weight(1f), color = if (focused) p.accent else p.text, maxLines = 1)
                            if (i == selected) GlyphIcon(Glyph.CHECK, p.accent, Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String, onDismiss: () -> Unit, danger: Boolean = false, onConfirm: () -> Unit) {
    val p = LocalPalette.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 460.dp).clip(RoundedCornerShape(16.dp)).background(p.bg)
                .border(1.dp, p.outline, RoundedCornerShape(16.dp)).padding(24.dp),
        ) {
            Txt(title, Type.heading)
            Txt(message, Type.body, Modifier.padding(top = 8.dp, bottom = 20.dp), color = p.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Focus starts on Cancel so a stray OK press can never delete anything.
                PillButton("Cancel", onDismiss, Modifier.focusRequester(rememberInitialFocus()))
                PillButton(confirmLabel, onConfirm, danger = danger, primary = !danger)
            }
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val p = LocalPalette.current
    var text by remember { mutableStateOf(initial) }
    val submit = { text.trim().take(40).takeIf { it.isNotEmpty() }?.let(onConfirm) ?: Unit }
    val saveFocus = rememberInitialFocus()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 520.dp).clip(RoundedCornerShape(16.dp)).background(p.bg)
                .border(1.dp, p.outline, RoundedCornerShape(16.dp)).padding(24.dp),
        ) {
            Txt(title, Type.heading)
            var fieldFocused by remember { mutableStateOf(false) }
            BasicTextField(
                value = text, onValueChange = { text = it.take(40) }, singleLine = true,
                textStyle = Type.body.copy(color = p.text), cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.padding(top = 14.dp).fillMaxWidth().onFocusChanged { fieldFocused = it.isFocused }
                    .clip(RoundedCornerShape(10.dp)).background(p.surface)
                    .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) p.accent else p.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
            if (suggestions.isNotEmpty()) {
                Txt("SUGGESTIONS", Type.overline, Modifier.padding(top = 16.dp, bottom = 8.dp), color = p.muted)
                // Down from any suggestion goes to Save, never Cancel, so a picked name is not lost by accident.
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.take(6).forEach { s -> PillButton(s, { text = s }, Modifier.focusProperties { down = saveFocus }, selected = text == s) }
                }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Save", { submit() }, Modifier.focusRequester(saveFocus), primary = true)
                PillButton("Cancel", onDismiss)
            }
        }
    }
}

// ---------- Layout ----------

@Composable
fun ScreenScaffold(
    title: String,
    subtitle: String? = null,
    overline: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().background(p.bg).padding(horizontal = EdgeH, vertical = EdgeV)) {
        if (overline != null) Txt(overline.uppercase(), Type.overline, color = p.accent)
        Txt(title, Type.title, maxLines = 1)
        if (subtitle != null) Txt(subtitle, Type.body, Modifier.padding(top = 2.dp), color = p.muted, maxLines = 2)
        if (actions != null) {
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val p = LocalPalette.current
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GlyphIcon(Glyph.FRAME, p.accent, Modifier.size(44.dp))
        Txt(title, Type.heading, Modifier.padding(top = 16.dp))
        Txt(message, Type.body, Modifier.padding(top = 6.dp).widthIn(max = 520.dp), color = p.muted, align = TextAlign.Center)
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

// ---------- Images ----------

@Composable
fun rememberThumb(media: Media?): ImageBitmap? {
    val images = LocalContext.current.graph.images
    val state by produceState<ImageBitmap?>(null, media?.id, media?.sha256) {
        value = media?.let { images.thumb(it)?.asImageBitmap() }
    }
    return state
}

@Composable
fun MediaThumb(media: Media?, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val p = LocalPalette.current
    val bmp = rememberThumb(media)
    Box(modifier.background(p.surfaceHi)) {
        if (bmp != null) Image(bmp, media?.title, Modifier.fillMaxSize(), contentScale = scale)
    }
}

/** A picture card used by every grid: thumbnail, optional caption, and badges. */
@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: Boolean = true,
    checked: Boolean? = null,
    grabbed: Boolean = false,
    onKey: ((KeyEvent) -> Boolean)? = null,
) {
    val p = LocalPalette.current
    FocusSurface(onClick, modifier, shape = RoundedCornerShape(8.dp), scale = 1.06f, onKey = onKey) { focused ->
        Column {
            Box {
                MediaThumb(media, Modifier.fillMaxWidth().height(112.dp))
                if (media.favorite) Badge(Glyph.HEART, Modifier.align(Alignment.TopStart))
                if (checked != null) Badge(if (checked) Glyph.CHECK else Glyph.RING, Modifier.align(Alignment.TopEnd), filled = checked)
                if (grabbed) Badge(Glyph.MOVE, Modifier.align(Alignment.Center), filled = true)
                if (media.hidden) Txt("Hidden", Type.caption, Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White)
            }
            if (caption) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    Txt(media.title, Type.label, color = if (focused) p.accent else p.text, maxLines = 1)
                    if (media.artist.isNotBlank()) Txt(media.artist, Type.caption, color = p.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Badge(glyph: Glyph, modifier: Modifier, filled: Boolean = false) {
    val p = LocalPalette.current
    Box(
        modifier.padding(6.dp).size(22.dp).clip(CircleShape).background(if (filled) p.accent else Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, if (filled) p.onAccent else Color.White, Modifier.size(12.dp)) }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 2)) }.getOrNull()
    }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ---------- Icons: drawn, so there is no icon font or image to ship ----------

enum class Glyph { FRAME, PHOTO, MIX, STACK, PLAY, PAUSE, GRID, GEAR, HEART, CHECK, RING, NEXT, PREV, INFO, PLUS, PHONE, CLOUD, MOVE, HOLD, SHUFFLE, NOTE }

@Composable
fun GlyphIcon(glyph: Glyph, color: Color, modifier: Modifier = Modifier, stroke: Dp = 1.6.dp) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = stroke.toPx()
        val line = Stroke(sw)
        fun l(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), sw)
        fun box(x: Float, y: Float, bw: Float, bh: Float, fill: Boolean = false) =
            drawRect(color, Offset(x * w, y * h), Size(bw * w, bh * h), style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else line)
        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) {
            val path = androidx.compose.ui.graphics.Path().apply { moveTo(ax * w, ay * h); lineTo(bx * w, by * h); lineTo(cx * w, cy * h); close() }
            drawPath(path, color)
        }
        when (glyph) {
            Glyph.FRAME -> { box(0.08f, 0.14f, 0.84f, 0.72f); box(0.26f, 0.32f, 0.48f, 0.36f) }
            Glyph.PHOTO -> { box(0.08f, 0.16f, 0.84f, 0.68f); l(0.08f, 0.72f, 0.36f, 0.46f); l(0.36f, 0.46f, 0.56f, 0.64f); l(0.56f, 0.64f, 0.7f, 0.52f); l(0.7f, 0.52f, 0.92f, 0.72f); drawCircle(color, 0.06f * w, Offset(0.7f * w, 0.34f * h)) }
            Glyph.MIX -> { box(0.06f, 0.26f, 0.5f, 0.5f); box(0.44f, 0.14f, 0.5f, 0.5f) }
            Glyph.STACK -> { box(0.14f, 0.34f, 0.72f, 0.5f); l(0.22f, 0.24f, 0.78f, 0.24f); l(0.3f, 0.14f, 0.7f, 0.14f) }
            Glyph.PLAY -> tri(0.26f, 0.14f, 0.26f, 0.86f, 0.86f, 0.5f)
            Glyph.PAUSE -> { box(0.24f, 0.16f, 0.16f, 0.68f, true); box(0.6f, 0.16f, 0.16f, 0.68f, true) }
            Glyph.GRID -> for (r in 0..1) for (c in 0..1) box(0.12f + c * 0.44f, 0.12f + r * 0.44f, 0.32f, 0.32f)
            Glyph.GEAR -> { drawCircle(color, 0.3f * w, style = line); drawCircle(color, 0.09f * w); for (i in 0 until 8) { val a = Math.toRadians(i * 45.0); l(0.5f + 0.3f * kotlin.math.cos(a).toFloat(), 0.5f + 0.3f * kotlin.math.sin(a).toFloat(), 0.5f + 0.44f * kotlin.math.cos(a).toFloat(), 0.5f + 0.44f * kotlin.math.sin(a).toFloat()) } }
            Glyph.HEART -> { drawCircle(color, 0.19f * w, Offset(0.32f * w, 0.36f * h)); drawCircle(color, 0.19f * w, Offset(0.68f * w, 0.36f * h)); tri(0.14f, 0.46f, 0.86f, 0.46f, 0.5f, 0.88f) }
            Glyph.CHECK -> { l(0.16f, 0.54f, 0.4f, 0.78f); l(0.4f, 0.78f, 0.86f, 0.24f) }
            Glyph.RING -> drawCircle(color, 0.36f * w, style = line)
            Glyph.NOTE -> { drawCircle(color, 0.15f * w, Offset(0.36f * w, 0.74f * h)); l(0.51f, 0.74f, 0.51f, 0.16f); l(0.51f, 0.16f, 0.78f, 0.28f) }
            Glyph.NEXT -> { tri(0.16f, 0.16f, 0.16f, 0.84f, 0.7f, 0.5f); box(0.74f, 0.16f, 0.1f, 0.68f, true) }
            Glyph.PREV -> { tri(0.84f, 0.16f, 0.84f, 0.84f, 0.3f, 0.5f); box(0.16f, 0.16f, 0.1f, 0.68f, true) }
            Glyph.INFO -> { drawCircle(color, 0.4f * w, style = line); l(0.5f, 0.44f, 0.5f, 0.72f); drawCircle(color, 0.05f * w, Offset(0.5f * w, 0.3f * h)) }
            Glyph.PLUS -> { l(0.5f, 0.16f, 0.5f, 0.84f); l(0.16f, 0.5f, 0.84f, 0.5f) }
            Glyph.PHONE -> { box(0.28f, 0.08f, 0.44f, 0.84f); l(0.44f, 0.8f, 0.56f, 0.8f) }
            Glyph.CLOUD -> { drawCircle(color, 0.2f * w, Offset(0.36f * w, 0.56f * h), style = line); drawCircle(color, 0.26f * w, Offset(0.58f * w, 0.46f * h), style = line); l(0.2f, 0.76f, 0.82f, 0.76f) }
            Glyph.MOVE -> { l(0.14f, 0.5f, 0.86f, 0.5f); tri(0.06f, 0.5f, 0.26f, 0.34f, 0.26f, 0.66f); tri(0.94f, 0.5f, 0.74f, 0.34f, 0.74f, 0.66f) }
            Glyph.HOLD -> { box(0.2f, 0.42f, 0.6f, 0.44f); drawArc(color, 180f, 180f, false, Offset(0.3f * w, 0.14f * h), Size(0.4f * w, 0.56f * h), style = line) }
            Glyph.SHUFFLE -> { l(0.1f, 0.28f, 0.34f, 0.28f); l(0.34f, 0.28f, 0.62f, 0.72f); l(0.62f, 0.72f, 0.86f, 0.72f); l(0.1f, 0.72f, 0.34f, 0.72f); l(0.34f, 0.72f, 0.62f, 0.28f); l(0.62f, 0.28f, 0.86f, 0.28f) }
        }
    }
}
