package com.myframe.tv

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.myframe.tv.data.AppDatabase
import com.myframe.tv.data.ImageStore
import com.myframe.tv.data.MediaImporter
import com.myframe.tv.data.MediaRepository
import com.myframe.tv.data.SettingsStore
import com.myframe.tv.server.UploadController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow

/** Something another device asked the TV to do (sent from the phone dashboard). */
sealed interface RemoteCommand {
    data class Show(val presetId: Long) : RemoteCommand
}

/**
 * The app's few long-lived objects, created lazily so launch stays fast:
 * nothing here touches the database or the network until a screen needs it.
 */
class Graph(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(app)
    val db: AppDatabase by lazy { AppDatabase.build(app) }
    val images: ImageStore by lazy { ImageStore(app) }
    val repo: MediaRepository by lazy { MediaRepository(app, db, images) }
    val importer: MediaImporter by lazy { MediaImporter(app, repo, settings) }
    val commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 8)
    val upload: UploadController by lazy { UploadController(this) }
    val artUpdater: com.myframe.tv.data.ArtUpdater by lazy { com.myframe.tv.data.ArtUpdater(app, repo, importer) { settings.value.artUpdates } }
}

class MyFrameApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        // New public-domain paintings arrive over Wi-Fi. Checked every 6 hours; the updater itself runs at most once a day.
        graph.scope.launch {
            while (true) {
                runCatching { graph.artUpdater.runIfDue() }
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) graph.images.trimMemory()
    }
}

val Context.graph: Graph get() = (applicationContext as MyFrameApp).graph
