package com.myframe.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.myframe.tv.core.StartupMode
import com.myframe.tv.data.MediaRepository
import com.myframe.tv.ui.AppsScreen
import com.myframe.tv.ui.ArtScreen
import com.myframe.tv.ui.CollectionScreen
import com.myframe.tv.ui.CollectionsScreen
import com.myframe.tv.ui.EditorScreen
import com.myframe.tv.ui.HomeScreen
import com.myframe.tv.ui.MixedScreen
import com.myframe.tv.ui.PhotosScreen
import com.myframe.tv.ui.PickerScreen
import com.myframe.tv.ui.SettingsScreen
import com.myframe.tv.ui.UploadScreen
import com.myframe.tv.ui.kit.LocalPalette
import com.myframe.tv.ui.kit.paletteFor
import com.myframe.tv.ui.viewer.ViewerScreen

sealed interface Screen {
    data object Home : Screen
    data object Art : Screen
    data object Photos : Screen
    data object Collections : Screen
    data class Collection(val id: Long) : Screen
    data class Picker(val albumId: Long) : Screen
    data object Mixed : Screen
    data class Editor(val presetId: Long) : Screen
    data object Apps : Screen
    data object Settings : Screen
    data object Upload : Screen
    data class Viewer(val presetId: Long, val startId: Long = 0, val nonce: Long = 0) : Screen
}

/** A plain back stack: small enough to reason about, and all the navigation a TV app needs. */
class Nav(val stack: SnapshotStateList<Screen>) {
    fun go(screen: Screen) { stack.add(screen) }
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
    /** Replace everything above Home, used when the phone asks the TV to show something. */
    fun show(screen: Screen) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack.add(screen)
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("Nav not provided") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        applySetupExtras(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        applySetupExtras(intent)
    }

    /**
     * Lets tools/appliance.sh switch the two startup options, because Android does not allow adb to enable another
     * app's Home entry directly. Only these two harmless switches are reachable, and both are also in Settings.
     *   adb shell am start -n com.myframe.tv/.MainActivity --ez myframe.home true --ez myframe.boot true
     */
    private fun applySetupExtras(intent: android.content.Intent?) {
        intent ?: return
        if (intent.hasExtra(EXTRA_HOME)) com.myframe.tv.system.HomeRole.setEnabled(this, intent.getBooleanExtra(EXTRA_HOME, false))
        if (intent.hasExtra(EXTRA_BOOT)) {
            val on = intent.getBooleanExtra(EXTRA_BOOT, false)
            graph.settings.update { it.copy(launchOnBoot = on) }
        }
    }

    companion object {
        const val EXTRA_HOME = "myframe.home"
        const val EXTRA_BOOT = "myframe.boot"
    }
}

@Composable
private fun App() {
    val graph = androidx.compose.ui.platform.LocalContext.current.graph
    val settings by graph.settings.flow.collectAsState()
    val nav = remember { Nav(mutableStateListOf(Screen.Home)) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        graph.repo.ensureSeeded()
        runCatching { graph.importer.importInbox() }
        val s = graph.settings.value
        val presets = graph.repo.presets
        val target = when (s.startupMode) {
            StartupMode.HOME -> null
            StartupMode.ART -> presets.byBuiltin(MediaRepository.BUILTIN_ART)?.id
            StartupMode.PHOTOS -> presets.byBuiltin(MediaRepository.BUILTIN_PHOTOS)?.id
            StartupMode.MIXED -> presets.byId(s.mixedPresetId)?.id ?: presets.listAll().firstOrNull { it.builtin.isEmpty() }?.id
            StartupMode.RESUME -> presets.byId(s.lastPresetId)?.id
        }
        if (target != null) nav.go(Screen.Viewer(target))
        ready = true
    }
    LaunchedEffect(Unit) {
        graph.commands.collect { command ->
            when (command) {
                is RemoteCommand.Show -> nav.show(Screen.Viewer(command.presetId, nonce = System.nanoTime()))
            }
        }
    }

    val palette = paletteFor(settings.theme)
    CompositionLocalProvider(LocalPalette provides palette, LocalNav provides nav) {
        Box(Modifier.fillMaxSize().background(palette.bg)) {
            if (ready) {
                BackHandler(enabled = nav.stack.size > 1) { nav.back() }
                val holder = rememberSaveableStateHolder()
                val screen = nav.stack.last()
                Crossfade(targetState = "${nav.stack.size}:$screen" to screen, animationSpec = tween(220), label = "screen") { (key, current) ->
                    holder.SaveableStateProvider(key) { ScreenContent(current, nav) }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen, nav: Nav) {
    when (screen) {
        Screen.Home -> HomeScreen()
        Screen.Art -> ArtScreen()
        Screen.Photos -> PhotosScreen()
        Screen.Collections -> CollectionsScreen()
        is Screen.Collection -> CollectionScreen(screen.id)
        is Screen.Picker -> PickerScreen(screen.albumId)
        Screen.Mixed -> MixedScreen()
        is Screen.Editor -> EditorScreen(screen.presetId)
        Screen.Apps -> AppsScreen()
        Screen.Settings -> SettingsScreen()
        Screen.Upload -> UploadScreen()
        is Screen.Viewer -> ViewerScreen(
            presetId = screen.presetId, startMediaId = screen.startId,
            onExit = { nav.back() }, onEdit = { nav.go(Screen.Editor(it)) }, onAddPhotos = { nav.go(Screen.Upload) },
        )
    }
}
