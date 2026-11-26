package com.swara.nativeapp.data

import com.swara.nativeapp.model.LyricLine
import com.swara.nativeapp.model.LyricsData
import com.swara.nativeapp.model.Shelf
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import java.util.concurrent.TimeUnit

// Same Innertube backend as music.youtube.com; some ISP/router DNS filters block the music subdomain.
private const val API = "https://www.youtube.com/youtubei/v1"

class YouTubeMusicClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var vrBlocked = false

    init {
        initNewPipeIfNeeded()
    }

    private fun initNewPipeIfNeeded() {
        try {
            if (NewPipe.getDownloader() == null) {
                NewPipe.init(object : Downloader() {
                    override fun execute(request: NpRequest): NpResponse {
                        val builder = Request.Builder().url(request.url())
                        request.headers().forEach { (k, vals) ->
                            vals.forEach { v -> builder.addHeader(k, v) }
                        }
                        if (request.headers()["User-Agent"].isNullOrEmpty()) {
                            builder.header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
                            )
                        }
                        val data = request.dataToSend()
                        when (request.httpMethod().uppercase()) {
                            "POST" -> builder.post((data ?: ByteArray(0)).toRequestBody(null))
                            "HEAD" -> builder.head()
                            else -> builder.get()
                        }
                        client.newCall(builder.build()).execute().use { res ->
                            val body = res.body?.string() ?: ""
                            val headersMap = res.headers.toMultimap()
                            return NpResponse(
                                res.code,
                                res.message,
                                headersMap,
                                body,
                                res.request.url.toString()
                            )
                        }
                    }
                })
            }
        } catch (_: Exception) {
        }
    }

    suspend fun fetchHomeTracks(hl: String = "id", gl: String = "ID"): List<Track> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("browseId", "FEmusic_home")
                put("context", webRemixContext(hl, gl))
            }
            val root = postJson("$API/browse?prettyPrint=false", payload)
            val found = mutableListOf<Track>()
            collectTracksRecursive(root, found, limit = 24)
            found.distinctBy { it.id }
        }

    suspend fun fetchHomeShelves(hl: String = "id", gl: String = "ID"): List<Shelf> =
        withContext(Dispatchers.IO) {
            parseBrowseShelves("FEmusic_home", hl, gl).distinctBy { it.title }
        }

    suspend fun fetchExploreShelves(hl: String = "id", gl: String = "ID"): List<Shelf> =
        withContext(Dispatchers.IO) {
            parseBrowseShelves("FEmusic_explore", hl, gl)
        }

    suspend fun fetchChartShelves(hl: String = "id", gl: String = "ID", countryName: String = "Indonesia"): List<Shelf> =
        withContext(Dispatchers.IO) {
            val s = stringsFor(hl)
            val out = mutableListOf<Shelf>()
            val charts = parseBrowseShelves("FEmusic_charts", hl, gl)
            val topCountry = charts.firstOrNull { it.tracks.any { t -> t.browseId.isBlank() } }?.tracks
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { searchTracks("Top 50 $countryName songs", hl, gl).take(10) }.getOrDefault(emptyList())
            if (topCountry.isNotEmpty()) {
                out.add(Shelf("Top Chart \u2022 $countryName", topCountry.take(10), isList = true))
            }
            val trendingPlaylists = charts.firstOrNull { it.tracks.any { t -> t.browseId.isNotBlank() } }?.tracks
                ?.takeIf { it.isNotEmpty() }
                ?: runCatching { searchTracks("Trending playlist $countryName 2026", hl, gl).take(10) }.getOrDefault(emptyList())
            if (trendingPlaylists.isNotEmpty()) {
                out.add(Shelf("Trending Playlist \u2022 $countryName", trendingPlaylists.take(10), isList = false))
            }
            val topArtistsCountry = charts.firstOrNull {
                it.title.contains("artist", ignoreCase = true) || it.title.contains("artis", ignoreCase = true)
            }?.tracks?.takeIf { it.isNotEmpty() }
                ?: runCatching {
                    searchTracks("Top artists $countryName 2026", hl, gl)
                        .distinctBy { it.artist.trim().lowercase() }
                        .take(10)
                }.getOrDefault(emptyList())
            if (topArtistsCountry.isNotEmpty()) {
                out.add(Shelf("${s.topArtistsShelf} \u2022 $countryName", topArtistsCountry, isList = false))
            }
            out
        }

    suspend fun identifyBySnippet(snippet: String, hl: String = "id", gl: String = "ID"): List<Track> =
        withContext(Dispatchers.IO) {
            val q = java.net.URLEncoder.encode(snippet.trim(), "UTF-8")
            val arr = getJsonArray("https://lrclib.net/api/search?q=$q")
            val found = mutableListOf<Track>()
            if (arr != null) {
                for (i in 0 until minOf(arr.length(), 4)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val t = o.optString("trackName").trim()
                    val a = o.optString("artistName").trim()
                    if (t.isNotBlank()) {
                        runCatching { searchTracks("$t $a", hl, gl).firstOrNull() }.getOrNull()?.let { found.add(it) }
                    }
                }
            }
            if (found.isEmpty()) {
                runCatching { searchTracks(snippet.trim(), hl, gl).take(6) }.getOrDefault(emptyList())
            } else found.distinctBy { it.id }
        }

    suspend fun fetchPlaylistTracks(browseId: String, hl: String = "id", gl: String = "ID"): List<Track> =
        fetchPlaylistWithTitle(browseId, hl, gl).second

    suspend fun fetchPlaylistWithTitle(browseId: String, hl: String = "id", gl: String = "ID"): Pair<String, List<Track>> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("browseId", browseId)
                put("context", webRemixContext(hl, gl))
            }
            val root = postJson("$API/browse?prettyPrint=false", payload)
            val title = extractPlaylistTitle(root) ?: "Playlist YouTube"
            val found = mutableListOf<Track>()

            val playlistShelf = findPlaylistShelf(root)
            if (playlistShelf != null) {
                collectTracksRecursive(playlistShelf.optJSONArray("contents"), found, limit = Int.MAX_VALUE)
                val seenTokens = mutableSetOf<String>()
                var token = extractContinuationToken(playlistShelf)
                while (!token.isNullOrBlank() && seenTokens.add(token)) {
                    val contPayload = JSONObject().apply {
                        put("continuation", token)
                        put("context", webRemixContext(hl, gl))
                    }
                    val contRoot = runCatching { postJson("$API/browse?prettyPrint=false", contPayload) }.getOrNull() ?: break
                    val contItems = findContinuationItems(contRoot) ?: break
                    val before = found.size
                    collectTracksRecursive(contItems, found, limit = Int.MAX_VALUE)
                    if (found.size == before) break
                    token = extractContinuationToken(contItems) ?: extractContinuationToken(contRoot)
                }
            } else {
                collectTracksRecursive(root, found, limit = Int.MAX_VALUE)
            }

            title to found.filter { it.browseId.isBlank() }.distinctBy { it.id }
        }

    private fun extractPlaylistTitle(root: JSONObject): String? {
        val header = root.optJSONObject("header")
        val hTitle = extractRunsText(header?.optJSONObject("musicResponsiveHeaderRenderer")?.optJSONObject("title"))
            ?: extractRunsText(header?.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("title"))
            ?: extractRunsText(
                header?.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                    ?.optJSONObject("header")?.optJSONObject("musicResponsiveHeaderRenderer")?.optJSONObject("title")
            )
            ?: extractRunsText(
                header?.optJSONObject("musicEditablePlaylistDetailHeaderRenderer")
                    ?.optJSONObject("header")?.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("title")
            )
        if (!hTitle.isNullOrBlank()) return hTitle
        val mf = root.optJSONObject("microformat")?.optJSONObject("microformatDataRenderer")?.optString("title")
            ?.removeSuffix(" - YouTube Music")?.trim()
        return mf?.takeIf { it.isNotBlank() }
    }

    private fun findPlaylistShelf(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicPlaylistShelfRenderer")?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val found = findPlaylistShelf(node.opt(keys.next()))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val found = findPlaylistShelf(node.opt(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun findContinuationItems(root: JSONObject): JSONArray? {
        root.optJSONObject("continuationContents")
            ?.optJSONObject("musicPlaylistShelfContinuation")
            ?.optJSONArray("contents")?.let { return it }
        root.optJSONObject("continuationContents")
            ?.optJSONObject("musicShelfContinuation")
            ?.optJSONArray("contents")?.let { return it }
        val actions = root.optJSONArray("onResponseReceivedActions")
        if (actions != null) {
            for (i in 0 until actions.length()) {
                actions.optJSONObject(i)
                    ?.optJSONObject("appendContinuationItemsAction")
                    ?.optJSONArray("continuationItems")?.let { return it }
            }
        }
        return null
    }

    private fun extractContinuationToken(node: Any?): String? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("continuationEndpoint")
                    ?.optJSONObject("continuationCommand")
                    ?.optString("token")?.takeIf { it.isNotBlank() }?.let { return it }
                node.optJSONObject("nextContinuationData")
                    ?.optString("continuation")?.takeIf { it.isNotBlank() }?.let { return it }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val t = extractContinuationToken(node.opt(keys.next()))
                    if (t != null) return t
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val t = extractContinuationToken(node.opt(i))
                    if (t != null) return t
                }
            }
        }
        return null
    }

    suspend fun importPlaylistFromUrl(rawUrl: String, hl: String = "id", gl: String = "ID"): Result<Pair<String, List<Track>>> =
        withContext(Dispatchers.IO) {
            try {
                val url = rawUrl.trim()
                val spMatch = Regex("spotify\\.com/(?:intl-[^/]+/)?playlist/([A-Za-z0-9]+)").find(url)
                if (spMatch != null) {
                    val plId = spMatch.groupValues[1]
                    val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    val req = Request.Builder()
                        .url("https://open.spotify.com/embed/playlist/$plId")
                        .header("User-Agent", ua)
                        .build()
                    val html = client.newCall(req).execute().use { res ->
                        if (!res.isSuccessful) return@withContext Result.failure(Exception("Spotify HTTP ${res.code}"))
                        res.body?.string().orEmpty()
                    }
                    val token = Regex("\"accessToken\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                    var plTitle = "Playlist Spotify"
                    val queries = mutableListOf<Pair<String, String>>()

                    if (!token.isNullOrBlank()) {
                        var queryHash = "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf"
                        var hashRefreshed = false
                        var offset = 0
                        var totalCount = Int.MAX_VALUE
                        while (offset < totalCount) {
                            val varsJson = JSONObject().apply {
                                put("uri", "spotify:playlist:$plId")
                                put("offset", offset)
                                put("limit", 100)
                                put("enableWatchFeedEntrypoint", false)
                            }.toString()
                            val extJson = JSONObject().apply {
                                put("persistedQuery", JSONObject().apply {
                                    put("version", 1)
                                    put("sha256Hash", queryHash)
                                })
                            }.toString()
                            val pfUrl = "https://api-partner.spotify.com/pathfinder/v1/query" +
                                "?operationName=fetchPlaylist" +
                                "&variables=${java.net.URLEncoder.encode(varsJson, "UTF-8")}" +
                                "&extensions=${java.net.URLEncoder.encode(extJson, "UTF-8")}"
                            val pfReq = Request.Builder()
                                .url(pfUrl)
                                .header("Authorization", "Bearer $token")
                                .header("User-Agent", ua)
                                .header("app-platform", "WebPlayer")
                                .build()
                            val pfBody = client.newCall(pfReq).execute().use { res ->
                                if (res.isSuccessful) res.body?.string().orEmpty() else ""
                            }
                            if (pfBody.isBlank()) break
                            val pfJson = JSONObject(pfBody)
                            if (pfJson.has("errors") && !hashRefreshed) {
                                hashRefreshed = true
                                val webHtml = runCatching {
                                    client.newCall(Request.Builder().url("https://open.spotify.com/playlist/$plId").header("User-Agent", ua).build())
                                        .execute().use { it.body?.string().orEmpty() }
                                }.getOrDefault("")
                                val jsUrl = Regex("https://open\\.spotifycdn\\.com/cdn/build/web-player/web-player\\.[^\"']+\\.js").find(webHtml)?.value
                                if (!jsUrl.isNullOrBlank()) {
                                    val jsBody = runCatching {
                                        client.newCall(Request.Builder().url(jsUrl).header("User-Agent", ua).build())
                                            .execute().use { it.body?.string().orEmpty() }
                                    }.getOrDefault("")
                                    val newHash = Regex("\"fetchPlaylist\",\"query\",\"([0-9a-f]{64})\"").find(jsBody)?.groupValues?.get(1)
                                    if (!newHash.isNullOrBlank() && newHash != queryHash) {
                                        queryHash = newHash
                                        continue
                                    }
                                }
                            }
                            val plV2 = pfJson.optJSONObject("data")?.optJSONObject("playlistV2") ?: break
                            val name = plV2.optString("name").trim()
                            if (name.isNotBlank()) plTitle = name
                            val content = plV2.optJSONObject("content") ?: break
                            totalCount = content.optInt("totalCount", 0)
                            val items = content.optJSONArray("items") ?: break
                            if (items.length() == 0) break
                            for (i in 0 until items.length()) {
                                val tr = items.optJSONObject(i)?.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                                val tTitle = tr.optString("name").trim()
                                if (tTitle.isBlank()) continue
                                val artistItems = tr.optJSONObject("artists")?.optJSONArray("items")
                                val artists = buildList {
                                    if (artistItems != null) {
                                        for (j in 0 until artistItems.length()) {
                                            val aName = artistItems.optJSONObject(j)?.optJSONObject("profile")?.optString("name")?.trim().orEmpty()
                                            if (aName.isNotBlank()) add(aName)
                                        }
                                    }
                                }.joinToString(" ")
                                queries.add(tTitle to artists)
                            }
                            offset += items.length()
                        }
                    }

                    // Fallback to embed __NEXT_DATA__ if pathfinder returned nothing
                    if (queries.isEmpty()) {
                        val marker = html.indexOf("__NEXT_DATA__")
                        if (marker < 0) return@withContext Result.failure(Exception("Playlist Spotify tidak ditemukan / privat"))
                        val start = html.indexOf('>', marker) + 1
                        val end = html.indexOf("</script>", start)
                        val json = JSONObject(html.substring(start, end))
                        val entity = json.optJSONObject("props")?.optJSONObject("pageProps")
                            ?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                            ?: return@withContext Result.failure(Exception("Data playlist Spotify kosong"))
                        plTitle = entity.optString("name").ifBlank { entity.optString("title") }.ifBlank { "Playlist Spotify" }
                        val trackList = entity.optJSONArray("trackList") ?: JSONArray()
                        for (i in 0 until trackList.length()) {
                            val item = trackList.optJSONObject(i) ?: continue
                            val tTitle = item.optString("title").trim()
                            val tArtist = item.optString("subtitle").replace('\u00A0', ' ').trim()
                            if (tTitle.isNotBlank()) queries.add(tTitle to tArtist)
                        }
                    }

                    val sem = Semaphore(8)
                    val resolved = coroutineScope {
                        queries.map { (tTitle, tArtist) ->
                            async {
                                sem.withPermit {
                                    runCatching { searchTracks("$tTitle $tArtist", hl, gl).firstOrNull() }.getOrNull()
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                    return@withContext if (resolved.isEmpty()) {
                        Result.failure(Exception("Tidak ada lagu Spotify yang cocok ditemukan"))
                    } else {
                        Result.success(plTitle to resolved.distinctBy { it.id })
                    }
                }

                val ytMatch = Regex("[?&]list=([A-Za-z0-9_-]+)").find(url)
                if (ytMatch != null) {
                    val rawList = ytMatch.groupValues[1]
                    val browseId = if (rawList.startsWith("VL")) rawList else "VL$rawList"
                    val (plTitle, tracks) = fetchPlaylistWithTitle(browseId, hl, gl)
                    return@withContext if (tracks.isEmpty()) {
                        Result.failure(Exception("Playlist YouTube kosong atau privat"))
                    } else {
                        Result.success(plTitle to tracks)
                    }
                }

                Result.failure(Exception("Tempel link playlist Spotify atau YouTube Music yang valid"))
            } catch (e: Exception) {
                Result.failure(Exception("Gagal impor: ${e.message ?: "Error"}"))
            }
        }

    private fun parseBrowseShelves(browseId: String, hl: String, gl: String): List<Shelf> {
        val payload = JSONObject().apply {
            put("browseId", browseId)
            put("context", webRemixContext(hl, gl))
        }
        val root = postJson("$API/browse?prettyPrint=false", payload)
        val sections = root.optJSONObject("contents")
            ?.optJSONObject("singleColumnBrowseResultsRenderer")
            ?.optJSONArray("tabs")?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            ?: JSONArray()
        val shelves = mutableListOf<Shelf>()
        for (i in 0 until sections.length()) {
            val shelf = sections.optJSONObject(i)?.optJSONObject("musicCarouselShelfRenderer") ?: continue
            val title = extractRunsText(
                shelf.optJSONObject("header")
                    ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                    ?.optJSONObject("title")
            ) ?: continue
            val items = shelf.optJSONArray("contents") ?: continue
            val tracks = mutableListOf<Track>()
            collectTracksRecursive(items, tracks, limit = 30)
            if (tracks.size < 2) continue
            val isList = items.optJSONObject(0)?.has("musicResponsiveListItemRenderer") == true
            shelves.add(Shelf(title, tracks.distinctBy { it.id }, isList))
        }
        return shelves
    }

    suspend fun searchTracks(query: String, hl: String = "id", gl: String = "ID"): List<Track> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("query", query)
                put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ%3D%3D")
                put("context", webRemixContext(hl, gl))
            }
            val root = postJson("$API/search?prettyPrint=false", payload)
            val found = mutableListOf<Track>()
            collectTracksRecursive(root, found, limit = 25)
            found.distinctBy { it.id }
        }

    suspend fun fetchRelatedTracks(videoId: String, hl: String = "id", gl: String = "ID"): List<Track> =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
                put("context", webRemixContext(hl, gl))
            }
            val root = postJson("$API/next?prettyPrint=false", payload)
            val found = mutableListOf<Track>()
            collectTracksRecursive(root, found, limit = 20)
            found.filter { it.id != videoId && it.browseId.isBlank() }.distinctBy { it.id }
        }

    suspend fun fetchLyrics(title: String, artist: String, durationSec: Int = 0): LyricsData? =
        withContext(Dispatchers.IO) {
            try {
                val cleanTitle = title.replace(Regex("\\s*[\\[(].*?[\\])]"), "").trim()
                val cleanArtist = artist.split(",", "&", "feat.", "ft.").firstOrNull()?.trim().orEmpty()
                val encodedTrack = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                val encodedArtist = java.net.URLEncoder.encode(cleanArtist, "UTF-8")
                val durParam = if (durationSec > 0) "&duration=$durationSec" else ""
                val getUrl = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist$durParam"

                val obj = getJsonObject(getUrl) ?: run {
                    val q = java.net.URLEncoder.encode("$cleanTitle $cleanArtist".trim(), "UTF-8")
                    val arr = getJsonArray("https://lrclib.net/api/search?q=$q")
                    arr?.optJSONObject(0)
                } ?: return@withContext null

                val synced = obj.optString("syncedLyrics")
                if (synced.isNotBlank()) {
                    val parsed = parseLrc(synced)
                    if (parsed.isNotEmpty()) return@withContext LyricsData(synced = true, lines = parsed)
                }
                val plain = obj.optString("plainLyrics")
                if (plain.isNotBlank()) {
                    val lines = plain.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        .map { LyricLine(0L, it) }
                    if (lines.isNotEmpty()) return@withContext LyricsData(synced = false, lines = lines)
                }
                null
            } catch (_: Exception) {
                null
            }
        }

    private fun getJsonObject(url: String): JSONObject? {
        val req = Request.Builder().url(url).header("User-Agent", "SwaraNative/1.0").build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            val raw = res.body?.string().orEmpty()
            if (raw.isBlank()) null else JSONObject(raw)
        }
    }

    private fun getJsonArray(url: String): JSONArray? {
        val req = Request.Builder().url(url).header("User-Agent", "SwaraNative/1.0").build()
        return client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            val raw = res.body?.string().orEmpty()
            if (raw.isBlank()) null else JSONArray(raw)
        }
    }

    internal fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("^\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?](.*)$")
        val out = mutableListOf<LyricLine>()
        for (rawLine in lrc.lines()) {
            val m = regex.matchEntire(rawLine.trim()) ?: continue
            val min = m.groupValues[1].toLongOrNull() ?: 0L
            val sec = m.groupValues[2].toLongOrNull() ?: 0L
            val fracStr = m.groupValues[3]
            val msPart = when (fracStr.length) {
                1 -> fracStr.toLong() * 100
                2 -> fracStr.toLong() * 10
                3 -> fracStr.toLong()
                else -> 0L
            }
            val text = m.groupValues[4].trim()
            if (text.isNotEmpty()) {
                out.add(LyricLine((min * 60 + sec) * 1000 + msPart, text))
            }
        }
        return out.sortedBy { it.startMs }
    }

    suspend fun resolveAudioUrl(
        videoId: String,
        hl: String = "id",
        gl: String = "ID",
        quality: String = "high",
        preferNewPipe: Boolean = false,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            val vrError = if (!vrBlocked && !preferNewPipe) try {
                val payload = JSONObject().apply {
                    put("videoId", videoId)
                    put(
                        "context",
                        JSONObject().apply {
                            put(
                                "client",
                                JSONObject().apply {
                                    put("clientName", "ANDROID_VR")
                                    put("clientVersion", "1.60.19")
                                    put("hl", hl)
                                    put("gl", gl)
                                }
                            )
                        }
                    )
                }
                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .header("Content-Type", "application/json")
                    .header(
                        "User-Agent",
                        "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    )
                    .build()

                client.newCall(req).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (res.isSuccessful && raw.isNotBlank()) {
                        val json = JSONObject(raw)
                        val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                        if (formats != null) {
                            data class AudioCandidate(val url: String, val isMp4: Boolean, val bitrate: Int)
                            val candidates = mutableListOf<AudioCandidate>()
                            for (i in 0 until formats.length()) {
                                val f = formats.optJSONObject(i) ?: continue
                                val mime = f.optString("mimeType")
                                val url = f.optString("url")
                                val br = f.optInt("bitrate", 0)
                                if (url.isBlank() || !mime.startsWith("audio/")) continue
                                candidates.add(AudioCandidate(url, mime.contains("audio/mp4"), br))
                            }
                            val pool = candidates.filter { it.isMp4 }.ifEmpty { candidates }
                            val chosen = when (quality) {
                                "low" -> pool.minByOrNull { it.bitrate }
                                "medium" -> pool.minByOrNull { kotlin.math.abs(it.bitrate - 128_000) }
                                else -> candidates.maxByOrNull { it.bitrate }
                            }
                            if (chosen != null) return@withContext Result.success(chosen.url)
                        }
                        val status = json.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
                        val reason = json.optJSONObject("playabilityStatus")?.optString("reason").orEmpty()
                        if (status == "LOGIN_REQUIRED") vrBlocked = true
                        reason.ifBlank { "Stream tidak tersedia ($status)" }
                    } else {
                        "HTTP ${res.code} dari YouTube"
                    }
                }
            } catch (e: Exception) {
                e.message ?: "Timeout/jaringan"
            } else "VR skipped"

            // Secondary client rotation: IOS client context
            if (!preferNewPipe) {
                runCatching {
                    val iosPayload = JSONObject().apply {
                        put("videoId", videoId)
                        put(
                            "context",
                            JSONObject().apply {
                                put(
                                    "client",
                                    JSONObject().apply {
                                        put("clientName", "IOS")
                                        put("clientVersion", "19.29.1")
                                        put("deviceModel", "iPhone16,2")
                                        put("hl", hl)
                                        put("gl", gl)
                                    }
                                )
                            }
                        )
                    }
                    val iosReq = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                        .post(iosPayload.toString().toRequestBody(jsonMediaType))
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)")
                        .build()
                    client.newCall(iosReq).execute().use { res ->
                        val raw = res.body?.string().orEmpty()
                        if (res.isSuccessful && raw.isNotBlank()) {
                            val json = JSONObject(raw)
                            val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                            if (formats != null) {
                                data class IosAudio(val url: String, val isMp4: Boolean, val bitrate: Int)
                                val candidates = mutableListOf<IosAudio>()
                                for (i in 0 until formats.length()) {
                                    val f = formats.optJSONObject(i) ?: continue
                                    val mime = f.optString("mimeType")
                                    val url = f.optString("url")
                                    val br = f.optInt("bitrate", 0)
                                    if (url.isBlank() || !mime.startsWith("audio/")) continue
                                    candidates.add(IosAudio(url, mime.contains("audio/mp4"), br))
                                }
                                val pool = candidates.filter { it.isMp4 }.ifEmpty { candidates }
                                val chosen = when (quality) {
                                    "low" -> pool.minByOrNull { it.bitrate }
                                    "medium" -> pool.minByOrNull { kotlin.math.abs(it.bitrate - 128_000) }
                                    else -> pool.maxByOrNull { it.bitrate }
                                }
                                if (chosen != null) return@withContext Result.success(chosen.url)
                            }
                        }
                    }
                }
            }

            // Fallback to NewPipeExtractor (local signature/stream extraction, zero login/cookie)
            try {
                initNewPipeIfNeeded()
                val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                extractor.fetchPage()
                val audioStreams = extractor.audioStreams.filter { !it.content.isNullOrBlank() }
                val mp4Pool = audioStreams.filter { it.format?.mimeType?.contains("mp4") == true }.ifEmpty { audioStreams }
                val best = when (quality) {
                    "low" -> mp4Pool.minByOrNull { it.averageBitrate }
                    "medium" -> mp4Pool.minByOrNull { kotlin.math.abs(it.averageBitrate - 128) }
                    else -> mp4Pool.maxByOrNull { it.averageBitrate }
                }
                if (best != null && !best.content.isNullOrBlank()) {
                    return@withContext Result.success(best.content)
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Gagal memuat audio: ${e.message ?: vrError}"))
            }

            Result.failure(Exception("Audio tidak tersedia: $vrError"))
        }

    private fun webRemixContext(hl: String, gl: String): JSONObject = JSONObject().apply {
        put(
            "client",
            JSONObject().apply {
                put("clientName", "WEB_REMIX")
                put("clientVersion", "1.20241118.01.00")
                put("hl", hl)
                put("gl", gl)
            }
        )
    }

    private fun postJson(url: String, payload: JSONObject): JSONObject {
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(jsonMediaType))
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
        client.newCall(reqBuilder.build()).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun collectTracksRecursive(node: Any?, out: MutableList<Track>, limit: Int) {
        if (out.size >= limit || node == null) return
        when (node) {
            is JSONObject -> {
                val renderer = node.optJSONObject("musicResponsiveListItemRenderer")
                    ?: node.optJSONObject("musicTwoRowItemRenderer")
                    ?: node.optJSONObject("playlistPanelVideoRenderer")
                if (renderer != null) {
                    parseTrackFromRenderer(renderer)?.let { out.add(it) }
                }
                val keys = node.keys()
                while (keys.hasNext() && out.size < limit) {
                    val k = keys.next()
                    collectTracksRecursive(node.opt(k), out, limit)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    if (out.size >= limit) break
                    collectTracksRecursive(node.opt(i), out, limit)
                }
            }
        }
    }

    private fun parseTrackFromRenderer(r: JSONObject): Track? {
        val videoId = r.optString("videoId").takeIf { it.isNotBlank() }
            ?: r.optJSONObject("playlistItemData")?.optString("videoId")?.takeIf { it.isNotBlank() }
            ?: r.optJSONObject("navigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: r.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
                ?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
            ?: ""

        val browseId = if (videoId.isBlank()) {
            r.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith("VL") || it.startsWith("MPRE") || it.startsWith("UC") }
                ?: ""
        } else ""

        if (videoId.isBlank() && browseId.isBlank()) return null

        val title = extractRunsText(r.optJSONObject("title"))
            ?: extractFlexColumnText(r, 0)
            ?: return null

        val artist = extractRunsText(r.optJSONObject("subtitle"))
            ?: extractRunsText(r.optJSONObject("shortBylineText"))
            ?: extractRunsText(r.optJSONObject("longBylineText"))
            ?: extractFlexColumnText(r, 1)
            ?: "YouTube Music"

        val thumbs = r.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: r.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            ?: r.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")

        val rawArtwork = thumbs?.optJSONObject(thumbs.length() - 1)?.optString("url")
            ?.takeIf { it.isNotBlank() }
            ?: if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else ""

        val artworkUrl = upgradeThumbnailQuality(rawArtwork)

        if (artworkUrl.isBlank()) return null

        return Track(
            id = if (videoId.isNotBlank()) videoId else "browse:$browseId",
            title = title,
            artist = artist.substringBefore(" • ").trim(),
            artworkUrl = artworkUrl,
            browseId = browseId,
        )
    }

    private fun upgradeThumbnailQuality(url: String): String {
        if (url.isBlank()) return url
        return url
            .replace(Regex("=w\\d+-h\\d+[^&\\s]*"), "=w544-h544-l90-rj")
            .replace(Regex("=s\\d+[^&\\s]*"), "=s544-c-k-c0x00ffffff-no-rj")
    }

    private fun extractRunsText(obj: JSONObject?): String? {
        val runs = obj?.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun extractFlexColumnText(r: JSONObject, index: Int): String? {
        val cols = r.optJSONArray("flexColumns") ?: return null
        val col = cols.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
        return extractRunsText(col)
    }
}
