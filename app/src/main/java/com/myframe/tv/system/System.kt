package com.myframe.tv.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.service.dreams.DreamService
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.myframe.tv.MainActivity
import com.myframe.tv.core.RepeatMode
import com.myframe.tv.core.SlideshowEngine
import com.myframe.tv.core.slideSeconds
import com.myframe.tv.graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lists and launches other installed TV apps. MyFrame never embeds another app's content. */
object Apps {
    data class Entry(val label: String, val pkg: String, val icon: ImageBitmap?, val wide: Boolean)

    private val YOUTUBE = listOf("com.google.android.youtube.tv", "com.google.android.youtube")

    fun list(context: Context): List<Entry> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, Entry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val found = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0) }.getOrDefault(emptyList())
            for (ri in found) {
                val pkg = ri.activityInfo.packageName
                if (pkg == context.packageName || seen.containsKey(pkg)) continue
                val banner = runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull()
                val bitmap = runCatching { banner?.toBitmap(320, 180) ?: ri.loadIcon(pm)?.toBitmap(144, 144) }.getOrNull()
                seen[pkg] = Entry(ri.loadLabel(pm).toString(), pkg, bitmap?.asImageBitmap(), banner != null)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, pkg: String): Boolean {
        val intent = launchIntent(context, pkg) ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private val SPOTIFY = listOf("com.spotify.tv.android", "com.spotify.music")
    private val YOUTUBE_MUSIC = listOf("com.google.android.youtube.tvmusic", "com.google.android.apps.youtube.music")

    /** A media app the user asked for that is not installed, with the Play Store id to offer. */
    data class Missing(val name: String, val storePackage: String)

    val SPOTIFY_STORE = Missing("Spotify", "com.spotify.tv.android")
    val YOUTUBE_STORE = Missing("YouTube", "com.google.android.youtube.tv")

    /** Same lookup as [launch]: a TV-only app may have a Leanback entry and no phone-style launcher entry. */
    private fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return runCatching { pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }

    fun isInstalled(context: Context, packages: List<String>): Boolean = packages.any { launchIntent(context, it) != null }

    fun spotifyInstalled(context: Context) = isInstalled(context, SPOTIFY)
    fun youTubeInstalled(context: Context) = isInstalled(context, YOUTUBE)
    /** YouTube Music works through its own TV app or, failing that, through YouTube. */
    fun youTubeMusicInstalled(context: Context) = isInstalled(context, YOUTUBE_MUSIC + YOUTUBE)

    /**
     * Opens the app's Google Play page. MyFrame never installs or bundles other companies' apps:
     * the Play Store does the download, signing and updates. Returns false when no store can be opened.
     */
    fun openStore(context: Context, storePackage: String): Boolean {
        val uris = listOf("market://details?id=$storePackage", "https://play.google.com/store/apps/details?id=$storePackage")
        return uris.any { uri ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }

    /** Opens the official YouTube app. Returns false when it is not installed. */
    fun launchYouTube(context: Context): Boolean = YOUTUBE.any { launch(context, it) }

    /** Opens the official Spotify app (it plays its own audio; MyFrame never touches it). */
    fun launchSpotify(context: Context): Boolean = SPOTIFY.any { launch(context, it) }

    fun launchYouTubeMusic(context: Context): Boolean {
        if (YOUTUBE_MUSIC.any { launch(context, it) }) return true
        // On Android TV the Music launcher is optional: Google's supported fallback
        // is the Music section in the full YouTube app. Do not send users to a phone APK.
        if (!launchYouTube(context)) return false
        android.widget.Toast.makeText(context, "In YouTube, choose Music from the left menu.", android.widget.Toast.LENGTH_LONG).show()
        return true
    }

    /**
     * Opens Android's own Bluetooth screen, where remotes, keyboards, headphones and speakers are paired.
     * Pairing is a system job; MyFrame only gets you there. Falls back to the main Settings app.
     */
    fun openBluetoothSettings(context: Context): Boolean {
        // Android TV has no phone-style Bluetooth page: pairing lives behind CONNECT_INPUT ("Remotes & Accessories").
        val tries = listOf("com.google.android.intent.action.CONNECT_INPUT", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, android.provider.Settings.ACTION_SETTINGS)
        return tries.any { action ->
            runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }
    }
}

/**
 * Optional Home-launcher registration. Enabling it only makes MyFrame *eligible* as a Home app;
 * Android decides whether to offer the choice. The stock launcher is never disabled or removed.
 */
object HomeRole {
    private fun component(context: Context) = ComponentName(context, "com.myframe.tv.HomeAlias")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}

/**
 * Starts MyFrame after boot when the user asked for it. Android 10+ blocks background activity
 * starts unless the "display over other apps" permission was granted, so this is best effort.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.graph.settings.value.launchOnBoot) return
        runCatching {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** Screensaver: a plain, lightweight cross-fading slideshow of the chosen preset. */
class FrameDreamService : DreamService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var a: ImageView? = null
    private var b: ImageView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        a = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }.also { root.addView(it, params) }
        b = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0f }.also { root.addView(it, params) }
        setContentView(root)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        scope.launch {
            val g = graph
            g.repo.ensureSeeded()
            val preset = g.repo.presets.byId(g.settings.value.dreamPresetId) ?: g.repo.presets.byBuiltin("art") ?: return@launch
            val config = preset.config
            val list = g.repo.playlist(config.sources)
            if (list.isEmpty()) return@launch
            val engine = SlideshowEngine(list, config.order, RepeatMode.ALL)
            var front = a ?: return@launch
            var back = b ?: return@launch
            val metrics = resources.displayMetrics
            while (isActive) {
                val media = engine.current ?: break
                val bmp = g.images.full(media, metrics.widthPixels, metrics.heightPixels, cover = false)
                if (bmp != null) {
                    back.setImageBitmap(bmp)
                    back.alpha = 0f
                    back.bringToFront()
                    back.animate().alpha(1f).setDuration(1500).start()
                    val swap = front
                    front = back
                    back = swap
                }
                delay(slideSeconds(config.durationSec, media.durationSec) * 1000L)
                engine.next()
            }
        }
    }

    override fun onDreamingStopped() {
        scope.coroutineContext.cancelChildren()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }
}
