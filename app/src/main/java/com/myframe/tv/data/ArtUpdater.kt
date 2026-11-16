package com.myframe.tv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.myframe.tv.core.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Adds new public-domain paintings over Wi-Fi, at most once a day. Source: The Met Open Access API (CC0).
 * Only representational painters from [REPRESENTATIONAL] are considered, so abstract or cubist work is never added.
 * A failed run is not recorded, so it is simply retried on the next check.
 */
class ArtUpdater(
    private val context: Context,
    private val repo: MediaRepository,
    private val importer: MediaImporter,
    private val enabled: () -> Boolean = { true },
) {
    private val prefs = context.getSharedPreferences("art_updater", Context.MODE_PRIVATE)
    private val gate = kotlinx.coroutines.sync.Mutex()

    /** Returns how many works were added. Does nothing if the last successful run was under 24 hours ago. */
    suspend fun runIfDue(now: Long = System.currentTimeMillis()): Int {
        if (!enabled() || !onWifi()) return 0
        return gate.withLock {
            if (now - prefs.getLong("lastRun", 0L) < INTERVAL_MS) return@withLock 0
            val added = runCatching { fetchNew(MAX_PER_RUN) }.getOrNull() ?: return@withLock 0
            prefs.edit().putLong("lastRun", now).apply()
            added
        }
    }

    /** Runs straight away, whatever the schedule says. Returns the number added, or null if the museum could not be reached. */
    suspend fun runNow(): Int? {
        if (!onWifi()) return null
        return gate.withLock {
            val added = runCatching { fetchNew(MAX_PER_RUN) }
                .onFailure { android.util.Log.w("MyFrame", "Art update failed", it) }.getOrNull() ?: return@withLock null
            prefs.edit().putLong("lastRun", System.currentTimeMillis()).apply()
            added
        }
    }

    private fun onWifi(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun fetchNew(limit: Int): Int = withContext(Dispatchers.IO) {
        val have = repo.media.everyOfKind(MediaKind.ART)
        // An 8 GB stick fills up quickly: stop for good once enough paintings have been downloaded (hidden ones count).
        val room = MAX_DOWNLOADED - have.count { !it.isAsset }
        if (room <= 0) return@withContext 0
        val limit = minOf(limit, room)
        val known = have.mapTo(HashSet()) { it.sourceUrl }
        // The v1.1 search is paged (limit/offset). A different random page each day keeps the additions varied.
        val total = prefs.getInt("total", 0)
        val offset = if (total > CANDIDATES) kotlin.random.Random.nextInt(total - CANDIDATES) else 0
        val page = JSONObject(getText("$SEARCH&limit=$CANDIDATES&offset=$offset"))
        prefs.edit().putInt("total", page.optInt("total")).apply()
        val ids = page.optJSONArray("objectIDs") ?: return@withContext 0
        val candidates = (0 until ids.length()).map { ids.getLong(it) }.shuffled()
        var added = 0
        for (id in candidates) {
            if (added >= limit) break
            val o = runCatching { JSONObject(getText("$BASE/objects/$id")) }.getOrNull() ?: continue
            val artist = o.optString("artistDisplayName")
            val title = o.optString("title")
            val url = o.optString("objectURL")
            val image = o.optString("primaryImage")
            if (!o.optBoolean("isPublicDomain") || image.isBlank() || url in known) continue
            if (!isRepresentational(artist)) continue
            val stream = runCatching { openImage(image) }.getOrNull() ?: continue
            val result = importer.import(stream, "$title.jpg", "art")
            if (result is ImportResult.Added) {
                repo.media.update(
                    result.media.copy(
                        kind = MediaKind.ART, title = title, artist = artist, dateText = o.optString("objectDate"),
                        credit = o.optString("creditLine"), sourceUrl = url,
                    ),
                )
                added++
            }
        }
        added
    }

    private fun getText(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "MyFrame/1.0")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** The returned stream closes the connection with it. Any failure on the way disconnects right here. */
    private fun openImage(url: String): InputStream? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            if (c.responseCode != HttpURLConnection.HTTP_OK || c.contentLengthLong > MAX_IMAGE_BYTES) {
                c.errorStream?.close()
                c.disconnect()
                return null
            }
            val body = c.inputStream
            return object : java.io.FilterInputStream(body) {
                override fun close() {
                    try { super.close() } finally { c.disconnect() }
                }
            }
        } catch (e: Exception) {
            c.errorStream?.runCatching { close() }
            c.disconnect()
            throw e
        }
    }

    companion object {
        const val BASE = "https://collectionapi.metmuseum.org/public/collection/v1"
        /** European Paintings (department 11), public domain, with an image. */
        const val SEARCH = "https://collectionapi.metmuseum.org/public/collection/v1.1/search?isPublicDomain=true&hasImages=true&departmentId=11&q=painting"
        const val INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val MAX_PER_RUN = 5
        /** About 400 MB at the usual 1 to 2 MB per painting. */
        const val MAX_DOWNLOADED = 300
        const val CANDIDATES = 200
        const val MAX_IMAGE_BYTES = 25L * 1024 * 1024

        /** Painters whose work is recognisably representational. Anything not listed is skipped. */
        val REPRESENTATIONAL = listOf(
            "Monet", "Renoir", "Degas", "Cézanne", "Cezanne", "Van Gogh", "Vermeer", "Rembrandt", "Sargent", "Manet",
            "Pissarro", "Sisley", "Morisot", "Cassatt", "Corot", "Turner", "Constable", "Friedrich", "Hokusai", "Hiroshige",
            "Bierstadt", "Frederic Edwin Church", "Winslow Homer", "Gainsborough", "Reynolds", "Canaletto", "Watteau",
            "Fragonard", "Chardin", "Bruegel", "Leutze", "Thomas Cole", "Hals", "Ruisdael", "Gauguin", "Seurat",
            "Caillebotte", "Whistler", "Eakins", "Sorolla", "Zorn",
        )

        fun isRepresentational(artist: String): Boolean = REPRESENTATIONAL.any { artist.contains(it, ignoreCase = true) }
    }
}
