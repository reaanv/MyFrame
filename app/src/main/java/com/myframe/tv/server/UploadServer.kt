package com.myframe.tv.server

import com.myframe.tv.Graph
import com.myframe.tv.RemoteCommand
import com.myframe.tv.core.MediaKind
import com.myframe.tv.core.Source
import com.myframe.tv.core.SourceType
import com.myframe.tv.data.Album
import com.myframe.tv.data.ImportResult
import com.myframe.tv.data.MediaImporter
import com.myframe.tv.data.MediaRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/** Owns the lifetime of the local upload service. It only runs while the user has it switched on. */
class UploadController(private val graph: Graph) {
    data class State(
        val running: Boolean = false,
        val url: String? = null,
        val pin: String = "",
        val devices: List<Pairing.Device> = emptyList(),
        val error: String? = null,
        val uploaded: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    val pairing = Pairing()
    private var server: UploadServer? = null
    private var watchdog: Job? = null
    @Volatile private var lastActivity = 0L

    @Synchronized
    fun start() {
        if (server != null) return
        val ip = localIp()
        if (ip == null) {
            _state.value = State(error = "No network connection. Connect the TV to Wi-Fi or Ethernet and try again.")
            return
        }
        pairing.reset()
        val port = graph.settings.value.uploadPort
        val s = UploadServer(port, graph, pairing, ::touch)
        try {
            s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        } catch (e: Exception) {
            _state.value = State(error = "Could not start the upload service on port $port (${e.message}).")
            return
        }
        server = s
        lastActivity = System.currentTimeMillis()
        _state.value = State(running = true, url = "http://$ip:$port", pin = pairing.pin)
        watchdog = graph.scope.launch {
            while (true) {
                delay(5_000)
                refresh()
                // The service switches itself off after 30 idle minutes, or when the network goes away.
                if (System.currentTimeMillis() - lastActivity > IDLE_MS || localIp() == null) {
                    stop()
                    break
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        watchdog?.cancel()
        watchdog = null
        runCatching { server?.stop() }
        server = null
        pairing.revokeAll()
        _state.value = State()
    }

    fun disconnectAll() {
        pairing.revokeAll()
        refresh()
    }

    private fun touch(uploaded: Boolean) {
        lastActivity = System.currentTimeMillis()
        if (uploaded) _state.value = _state.value.copy(uploaded = _state.value.uploaded + 1)
    }

    private fun refresh() {
        val current = _state.value
        if (current.running) _state.value = current.copy(devices = pairing.devices())
    }

    companion object {
        const val IDLE_MS = 30 * 60_000L

        fun localIps(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { nif -> nif.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress } }
        }.getOrDefault(emptyList())

        fun localIp(): String? = localIps().firstOrNull()
    }
}

/**
 * The phone dashboard: one static page plus a small JSON API.
 * Every request must come from the local network, name this device by IP, and (except pairing)
 * carry a session token obtained with the PIN shown on the TV.
 */
class UploadServer(
    port: Int,
    private val graph: Graph,
    private val pairing: Pairing,
    private val onActivity: (uploaded: Boolean) -> Unit,
) : NanoHTTPD(port) {
    private val repo: MediaRepository get() = graph.repo

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            route(session)
        } catch (e: ResponseException) {
            json(e.status, err(e.message ?: "bad_request"))
        } catch (e: Exception) {
            android.util.Log.w("MyFrame", "Dashboard request failed: ${session.method} ${session.uri}", e)
            json(Response.Status.INTERNAL_ERROR, err("server_error"))
        }
        // Some POST paths reject before parseBody (auth, size and MIME checks),
        // and logout has no body to parse. NanoHTTPD otherwise reuses the socket
        // with unread bytes, treating JSON/multipart as the next HTTP request.
        // Close after writes rather than draining an untrusted/oversized body.
        if (session.method != Method.GET) response.closeConnection(true)
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("X-Frame-Options", "DENY")
        response.addHeader("Referrer-Policy", "no-referrer")
        return response
    }

    private fun route(s: IHTTPSession): Response {
        if (!NetGuard.isLocalAddress(s.remoteIpAddress)) return json(Response.Status.FORBIDDEN, err("local_network_only"))
        if (!NetGuard.hostAllowed(s.headers["host"], UploadController.localIps())) return json(Response.Status.FORBIDDEN, err("bad_host"))
        if (s.method != Method.GET && s.method != Method.POST) return json(Response.Status.METHOD_NOT_ALLOWED, err("method_not_allowed"))
        onActivity(false)

        val path = s.uri.trimEnd('/').ifEmpty { "/" }
        val parts = path.split('/').filter { it.isNotEmpty() }

        if (s.method == Method.GET && (path == "/" || path == "/index.html")) return page()
        if (parts.firstOrNull() != "api") return json(Response.Status.NOT_FOUND, err("not_found"))

        if (path == "/api/pair" && s.method == Method.POST) return pair(s)

        val headerToken = s.headers["x-myframe-token"]
        val cookieToken = s.cookies.read(COOKIE)
        // State-changing calls need the header: a cross-site form cannot set it, which rules out CSRF.
        val authorised = if (s.method == Method.POST) pairing.validate(headerToken) else pairing.validate(headerToken) || pairing.validate(cookieToken)
        if (!authorised) return json(Response.Status.UNAUTHORIZED, err("pairing_required"))

        return when {
            s.method == Method.GET && path == "/api/state" -> json(Response.Status.OK, runBlocking { state() })
            s.method == Method.GET && path == "/api/photos" -> json(Response.Status.OK, runBlocking { photos(s) })
            s.method == Method.GET && parts.size == 3 && parts[1] == "thumb" -> thumb(NetGuard.parseId(parts[2]))
            s.method == Method.POST && path == "/api/upload" -> upload(s)
            s.method == Method.POST && path == "/api/collections" -> createCollection(body(s))
            s.method == Method.POST && parts.size == 4 && parts[1] == "collections" -> collectionAction(NetGuard.parseId(parts[2]), parts[3], body(s))
            s.method == Method.POST && path == "/api/photos/delete" -> deletePhotos(body(s))
            s.method == Method.POST && path == "/api/photos/favorite" -> favorite(body(s))
            s.method == Method.POST && path == "/api/display" -> display(body(s))
            s.method == Method.POST && path == "/api/logout" -> {
                pairing.revoke(headerToken)
                json(Response.Status.OK, ok())
            }
            else -> json(Response.Status.NOT_FOUND, err("not_found"))
        }
    }

    private fun page(): Response {
        val html = runCatching { graph.app.assets.open("web/index.html").use { it.readBytes() } }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Dashboard not bundled")
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", ByteArrayInputStream(html), html.size.toLong()).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; base-uri 'none'; form-action 'self'")
        }
    }

    private fun pair(s: IHTTPSession): Response {
        val b = body(s)
        val pin = b["pin"]?.jsonPrimitive?.content.orEmpty()
        val name = b["name"]?.jsonPrimitive?.content ?: "Device"
        return when (val r = pairing.pair(pin, name, s.remoteIpAddress ?: "")) {
            is Pairing.Result.Ok -> json(Response.Status.OK, buildJsonObject { put("ok", true); put("token", r.token) }).apply {
                addHeader("Set-Cookie", "$COOKIE=${r.token}; Path=/; HttpOnly; SameSite=Strict")
            }
            is Pairing.Result.BadPin -> json(Response.Status.FORBIDDEN, buildJsonObject { put("ok", false); put("error", "bad_pin"); put("remaining", r.remaining) })
            is Pairing.Result.Locked -> json(Response.Status.TOO_MANY_REQUESTS, buildJsonObject { put("ok", false); put("error", "locked"); put("retryAfterSec", r.retryAfterSec) })
        }
    }

    private suspend fun state(): JsonObject {
        val storage = repo.storage()
        val albums = repo.albums.listAll()
        val presets = repo.presets.listAll()
        val photos = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_PHOTOS }
        val art = presets.firstOrNull { it.builtin == MediaRepository.BUILTIN_ART }
        val photoSource = photos?.config?.sources?.firstOrNull()
        return buildJsonObject {
            put("ok", true)
            putJsonObject("device") { put("name", "MyFrame"); put("model", android.os.Build.MODEL ?: "") }
            putJsonObject("storage") {
                put("freeBytes", storage.freeBytes); put("totalBytes", storage.totalBytes)
                put("libraryBytes", storage.photoBytes); put("reserveBytes", MediaImporter.RESERVE_BYTES)
            }
            putJsonObject("counts") { put("photos", storage.photos); put("art", storage.art) }
            putJsonObject("limits") { put("maxUploadBytes", MediaImporter.MAX_BYTES); putJsonArray("types") { MediaImporter.SUPPORTED.keys.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } } }
            putJsonArray("collections") {
                albums.forEach { a ->
                    add(buildJsonObject {
                        put("id", a.album.id); put("name", a.album.name); put("count", a.itemCount)
                        put("coverId", a.coverId ?: 0L); put("system", a.album.systemKey.isNotEmpty())
                    })
                }
            }
            putJsonArray("presets") {
                presets.forEach { p ->
                    add(buildJsonObject {
                        put("id", p.id); put("name", p.name); put("builtin", p.builtin)
                        put("durationSec", p.config.durationSec)
                    })
                }
            }
            putJsonObject("display") {
                put("lastPresetId", graph.settings.value.lastPresetId)
                put("artDurationSec", art?.config?.durationSec ?: 0)
                put("photosDurationSec", photos?.config?.durationSec ?: 0)
                put("photosCollectionId", if (photoSource?.type == SourceType.COLLECTION) photoSource.collectionId else 0L)
            }
        }
    }

    private suspend fun photos(s: IHTTPSession): JsonObject {
        val collection = NetGuard.parseId(s.parameters["collection"]?.firstOrNull())
        val offset = s.parameters["offset"]?.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val limit = (s.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 60).coerceIn(1, 200)
        val all = if (collection != null && collection > 0) repo.albums.items(collection) else repo.media.list(MediaKind.PHOTO, false)
        return buildJsonObject {
            put("ok", true)
            put("total", all.size)
            putJsonArray("items") {
                all.drop(offset).take(limit).forEach { m ->
                    add(buildJsonObject {
                        put("id", m.id); put("name", m.fileName.ifBlank { m.title }); put("kind", m.kind.name)
                        put("width", m.width); put("height", m.height); put("favorite", m.favorite)
                        put("addedAt", m.addedAt); put("sizeBytes", m.sizeBytes)
                    })
                }
            }
        }
    }

    private fun thumb(id: Long?): Response {
        val media = id?.let { runBlocking { repo.media.byId(it) } } ?: return json(Response.Status.NOT_FOUND, err("not_found"))
        val bmp = runBlocking { graph.images.thumb(media) } ?: return json(Response.Status.NOT_FOUND, err("unreadable"))
        val bytes = ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "private, max-age=3600")
        }
    }

    private fun upload(s: IHTTPSession): Response {
        val length = s.headers["content-length"]?.toLongOrNull() ?: return json(Response.Status.LENGTH_REQUIRED, err("length_required"))
        // Checked before a single byte of the body is read.
        if (length > MediaImporter.MAX_BYTES + 64 * 1024) return json(Response.Status.PAYLOAD_TOO_LARGE, err("too_large"))
        if (!s.headers["content-type"].orEmpty().startsWith("multipart/form-data")) return json(Response.Status.BAD_REQUEST, err("multipart_required"))
        val albumId = NetGuard.parseId(s.parameters["collection"]?.firstOrNull()) ?: 0
        val files = HashMap<String, String>()
        s.parseBody(files)
        val tmpPath = files["file"] ?: return json(Response.Status.BAD_REQUEST, err("file_missing"))
        val name = s.parameters["file"]?.firstOrNull() ?: "photo.jpg"
        val result = runBlocking {
            if (albumId > 0 && repo.albums.byId(albumId) == null) null
            else graph.importer.import(File(tmpPath).inputStream(), name, "upload", albumId)
        } ?: return json(Response.Status.NOT_FOUND, err("collection_not_found"))
        return when (result) {
            is ImportResult.Added -> {
                onActivity(true)
                json(Response.Status.OK, buildJsonObject { put("ok", true); put("id", result.media.id); put("duplicate", false) })
            }
            is ImportResult.Duplicate -> json(Response.Status.OK, buildJsonObject { put("ok", true); put("id", result.media.id); put("duplicate", true) })
            is ImportResult.Rejected -> when (result.reason) {
                ImportResult.Reason.TOO_LARGE -> json(Response.Status.PAYLOAD_TOO_LARGE, err("too_large"))
                ImportResult.Reason.UNSUPPORTED, ImportResult.Reason.CORRUPT -> json(Response.Status.UNSUPPORTED_MEDIA_TYPE, err("unsupported_type"))
                ImportResult.Reason.NO_SPACE -> json(INSUFFICIENT_STORAGE, err("no_space"))
                ImportResult.Reason.IO -> json(Response.Status.INTERNAL_ERROR, err("io_error"))
            }
        }
    }

    private fun createCollection(b: JsonObject): Response {
        val name = cleanName(b["name"]?.jsonPrimitive?.content) ?: return json(Response.Status.BAD_REQUEST, err("name_required"))
        val id = runBlocking { repo.albums.insert(Album(name = name, createdAt = System.currentTimeMillis())) }
        return json(Response.Status.OK, buildJsonObject { put("ok", true); put("id", id) })
    }

    private fun collectionAction(id: Long?, action: String, b: JsonObject): Response {
        val album = id?.let { runBlocking { repo.albums.byId(it) } } ?: return json(Response.Status.NOT_FOUND, err("collection_not_found"))
        return runBlocking {
            when (action) {
                "rename" -> {
                    if (album.systemKey.isNotEmpty()) return@runBlocking json(Response.Status.FORBIDDEN, err("system_collection"))
                    val name = cleanName(b["name"]?.jsonPrimitive?.content) ?: return@runBlocking json(Response.Status.BAD_REQUEST, err("name_required"))
                    repo.albums.rename(album.id, name)
                    json(Response.Status.OK, ok())
                }
                "delete" -> {
                    if (album.systemKey.isNotEmpty()) return@runBlocking json(Response.Status.FORBIDDEN, err("system_collection"))
                    repo.albums.delete(album.id)
                    json(Response.Status.OK, ok())
                }
                "items" -> {
                    val ids = ids(b)
                    if (b["action"]?.jsonPrimitive?.content == "remove") repo.albums.removeItems(album.id, ids)
                    else repo.albums.addItems(album.id, repo.media.byIds(ids).map { it.id })
                    json(Response.Status.OK, ok())
                }
                else -> json(Response.Status.NOT_FOUND, err("not_found"))
            }
        }
    }

    private fun deletePhotos(b: JsonObject): Response {
        val ids = ids(b)
        // Only uploaded photographs can be removed from here; bundled artwork is never touched.
        runBlocking { repo.deleteMedia(repo.media.byIds(ids).filter { it.kind == MediaKind.PHOTO }.map { it.id }) }
        return json(Response.Status.OK, ok())
    }

    private fun favorite(b: JsonObject): Response {
        val id = b["id"]?.jsonPrimitive?.longOrNull ?: return json(Response.Status.BAD_REQUEST, err("id_required"))
        val fav = b["favorite"]?.jsonPrimitive?.content == "true"
        runBlocking { repo.media.setFavorite(id, fav) }
        return json(Response.Status.OK, ok())
    }

    private fun display(b: JsonObject): Response = runBlocking {
        val mode = b["mode"]?.jsonPrimitive?.content
        val duration = b["durationSec"]?.jsonPrimitive?.longOrNull?.toInt()?.coerceIn(3, 86_400)
        val preset = when (mode) {
            "art" -> repo.presets.byBuiltin(MediaRepository.BUILTIN_ART)
            "photos" -> repo.presets.byBuiltin(MediaRepository.BUILTIN_PHOTOS)
            "mixed" -> b["presetId"]?.jsonPrimitive?.longOrNull?.let { repo.presets.byId(it) }
            else -> null
        } ?: return@runBlocking json(Response.Status.BAD_REQUEST, err("unknown_mode"))
        val collectionId = b["collectionId"]?.jsonPrimitive?.longOrNull
        if (mode == "photos" && collectionId != null) {
            if (collectionId > 0 && repo.albums.byId(collectionId) == null) return@runBlocking json(Response.Status.NOT_FOUND, err("collection_not_found"))
            repo.updateConfig(preset.id) { it.copy(sources = listOf(if (collectionId > 0) Source(SourceType.COLLECTION, collectionId) else Source(SourceType.ALL_PHOTOS))) }
        }
        if (duration != null) repo.updateConfig(preset.id) { it.copy(durationSec = duration) }
        if (b["show"]?.jsonPrimitive?.content != "false") graph.commands.tryEmit(RemoteCommand.Show(preset.id))
        json(Response.Status.OK, ok())
    }

    // ---- helpers ----

    private fun body(s: IHTTPSession): JsonObject {
        val length = s.headers["content-length"]?.toLongOrNull() ?: 0
        if (length > 64 * 1024) throw ResponseException(Response.Status.PAYLOAD_TOO_LARGE, "too_large")
        val files = HashMap<String, String>()
        s.parseBody(files)
        val text = files["postData"] ?: return JsonObject(emptyMap())
        return runCatching { com.myframe.tv.core.AppJson.parseToJsonElement(text).jsonObject }
            .getOrElse { throw ResponseException(Response.Status.BAD_REQUEST, "invalid_json") }
    }

    // Refused, never truncated: a silent cut-off would report "Deleted" while leaving photos behind.
    private fun ids(b: JsonObject): List<Long> {
        val all = runCatching { b["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }.orEmpty() }.getOrDefault(emptyList())
        if (all.size > MAX_IDS) throw ResponseException(Response.Status.BAD_REQUEST, "too_many")
        return all
    }

    private fun cleanName(raw: String?): String? =
        raw?.filter { !it.isISOControl() }?.trim()?.take(40)?.takeIf { it.isNotEmpty() }

    private fun ok(): JsonObject = buildJsonObject { put("ok", true) }
    private fun err(code: String): JsonObject = buildJsonObject { put("ok", false); put("error", code) }

    private fun json(status: Response.IStatus, body: JsonObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).apply { addHeader("Cache-Control", "no-store") }

    companion object {
        const val COOKIE = "mf_session"
        const val MAX_IDS = 500
        val INSUFFICIENT_STORAGE = object : Response.IStatus {
            override fun getDescription() = "507 Insufficient Storage"
            override fun getRequestStatus() = 507
        }
    }
}

@Suppress("unused")
private fun JsonObjectBuilder.noop() = Unit
