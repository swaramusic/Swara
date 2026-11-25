package com.swara.nativeapp.ui

import android.app.Application
import com.swara.nativeapp.model.stringsFor
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.swara.nativeapp.data.YouTubeMusicClient
import com.swara.nativeapp.model.BUILTIN_EQ_PRESETS
import com.swara.nativeapp.model.LyricsData
import com.swara.nativeapp.model.SUPPORTED_COUNTRIES
import com.swara.nativeapp.model.Shelf
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.UserPlaylist
import com.swara.nativeapp.playback.SwaraPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SwaraUiState(
    val homeShelves: List<Shelf> = emptyList(),
    val exploreShelves: List<Shelf> = emptyList(),
    val homeTracks: List<Track> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val likedTracks: List<Track> = emptyList(),
    val recentTracks: List<Track> = emptyList(),
    val downloadedTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
    val downloadDone: Int = 0,
    val downloadTotal: Int = 0,
    val downloadCurrentTitle: String = "",
    val downloadTreeUri: String = "",
    val downloadStatusBanner: String? = null,
    val playlists: List<UserPlaylist> = emptyList(),
    val followedArtists: List<String> = emptyList(),
    val openedArtistName: String? = null,
    val openedArtistImage: String = "",
    val openedArtistTracks: List<Track> = emptyList(),
    val isLoadingArtist: Boolean = false,
    val openedPlaylistTitle: String? = null,
    val openedPlaylistTracks: List<Track> = emptyList(),
    val isLoadingPlaylist: Boolean = false,
    val openedAlbumTitle: String? = null,
    val openedAlbumTracks: List<Track> = emptyList(),
    val isLoadingAlbum: Boolean = false,
    val excludedTrackIds: Set<String> = emptySet(),
    val streakDays: Int = 1,
    val totalPlays: Int = 0,
    val totalMinutes: Int = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topSongs: List<Pair<Track, Int>> = emptyList(),
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoadingHome: Boolean = true,
    val isSearching: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val activeEqId: String = "flat",
    val eqBands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val reverbPct: Int = 0,
    val soundStyle: String = "Normal",
    val volume: Float = 1.0f,
    val lyrics: LyricsData? = null,
    val isLoadingLyrics: Boolean = false,
    val activeMood: String = "Semua",
    val country: String = "ID",
    val language: String = "en",
    val themeMode: String = "dark",
    val audioQuality: String = "high",
    val artistImages: Map<String, String> = emptyMap(),
    val accentId: String = "cobalt",
    val customAccentHex: Long = 0xFF2563EBL,
    val dynamicIslandEnabled: Boolean = true,
    val discordRpcEnabled: Boolean = false,
    val discordToken: String = "",
    val discordUsername: String = "",
    val discordStatus: String = "Off",
    val discordShowButtons: Boolean = true,
    val discordTemplate: String = "app",
    val sleepTimerRemainingSec: Int = 0,
    val isImportingPlaylist: Boolean = false,
    val listenRoomCode: String? = null,
    val listenIsHost: Boolean = false,
    val listenStatus: String? = null,
    val listenPeerCount: Int = 0,
    val identifyResults: List<Track> = emptyList(),
    val isIdentifying: Boolean = false,
    val songCacheEnabled: Boolean = true,
    val maxSongCacheMb: Int = 1126,
    val romanizeLyrics: Boolean = false,
    val errorMessage: String? = null,
)

class SwaraViewModel(app: Application) : AndroidViewModel(app) {
    private val yt = YouTubeMusicClient()
    private val prefs = app.getSharedPreferences("swara_native_prefs", 0).also {
        if (it.contains("discord_token")) it.edit().remove("discord_token").apply()
    }
    private val dlClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val sseClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _ui = MutableStateFlow(
        SwaraUiState(
            likedTracks = loadTrackList("liked"),
            recentTracks = loadTrackList("recent"),
            downloadedTracks = loadTrackList("downloads"),
            localTracks = loadTrackList("local").also {
                val uriObj = runCatching { org.json.JSONObject(prefs.getString("local_uris", "{}")) }.getOrNull()
                if (uriObj != null) {
                    it.forEach { t ->
                        val u = uriObj.optString(t.id)
                        if (u.isNotBlank()) SwaraPlaybackService.registerLocalTrack(t.id, u)
                    }
                }
            },
            playlists = loadPlaylists(),
            followedArtists = loadFollowedArtists(),
            streakDays = prefs.getInt("streak_days", 1),
            totalPlays = prefs.getInt("total_plays", 0),
            totalMinutes = prefs.getInt("total_seconds", 0) / 60,
            topArtists = loadTopArtists(),
            country = prefs.getString("country", "ID") ?: "ID",
            language = prefs.getString("lang", "en") ?: "en",
            themeMode = prefs.getString("theme_mode", "dark") ?: "dark",
            audioQuality = (prefs.getString("audio_quality", "high") ?: "high").also {
                SwaraPlaybackService.audioQuality = it
            },
            artistImages = loadArtistImages(),
            excludedTrackIds = loadExcludedTrackIds(),
            accentId = prefs.getString("accent", "cobalt") ?: "cobalt",
            customAccentHex = prefs.getLong("custom_accent_hex", 0xFF2563EBL),
            dynamicIslandEnabled = prefs.getBoolean("dynamic_island", true),
            discordRpcEnabled = prefs.getBoolean("discord_rpc_enabled", false),
            discordToken = "",
            discordUsername = prefs.getString("discord_username", "") ?: "",
            discordShowButtons = prefs.getBoolean("discord_show_buttons", true),
            discordTemplate = prefs.getString("discord_template", "app") ?: "app",
            songCacheEnabled = prefs.getBoolean("song_cache_enabled", true).also {
                SwaraPlaybackService.songCacheEnabled = it
            },
            maxSongCacheMb = prefs.getInt("max_song_cache_mb", 1126).also {
                SwaraPlaybackService.maxSongCacheMb = it
            },
            romanizeLyrics = prefs.getBoolean("romanize_lyrics", false),
            downloadTreeUri = prefs.getString("download_tree_uri", "") ?: "",
        )
    )
    val ui: StateFlow<SwaraUiState> = _ui.asStateFlow()

    private val _progress = MutableStateFlow(0L to 0L)
    val progress: StateFlow<Pair<Long, Long>> = _progress.asStateFlow()

    private var controller: MediaController? = null
    private var lastLyricsTrackId: String? = null
    private var sleepJob: Job? = null
    private var listenJob: Job? = null

    init {
        _ui.update { it.copy(topSongs = loadTopSongs(), searchHistory = loadSearchHistory()) }
        SwaraPlaybackService.dynamicIslandEnabled = _ui.value.dynamicIslandEnabled
        connectMediaController()
        loadHome()
        resolveMissingArtistImages((_ui.value.followedArtists + _ui.value.topArtists.map { it.first }).distinct())
        startProgressLoop()
    }

    private fun connectMediaController() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, SwaraPlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener(
            {
                try {
                    val mc = future.get()
                    controller = mc
                    syncFromPlayer(mc)
                    mc.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            // Queue, current item, play state, shuffle and repeat all live in the
                            // service's player so they survive the app being closed.
                            syncFromPlayer(mc)
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            _ui.update {
                                it.copy(
                                    isPlaying = false,
                                    errorMessage = "Gagal memutar: ${error.cause?.message ?: error.errorCodeName}"
                                )
                            }
                        }
                    })
                } catch (_: Exception) {
                }
            },
            ContextCompat.getMainExecutor(ctx)
        )
    }

    private fun startProgressLoop() {
        viewModelScope.launch {
            while (true) {
                controller?.let { mc ->
                    if (mc.isPlaying) {
                        val pos = mc.currentPosition.coerceAtLeast(0L)
                        val dur = mc.duration.coerceAtLeast(0L)
                        if (pos != _progress.value.first || dur != _progress.value.second) {
                            _progress.value = pos to dur
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingHome = true, errorMessage = null) }
            try {
                val gl = _ui.value.country
                val hl = _ui.value.language
                val countryName = com.swara.nativeapp.model.ENGLISH_COUNTRY_NAMES[gl]
                    ?: SUPPORTED_COUNTRIES.find { it.first == gl }?.second ?: gl
                val mood = _ui.value.activeMood
                val shelves = if (mood == "Semua") {
                    coroutineScope {
                        val baseDef = async { yt.fetchHomeShelves(hl = hl, gl = gl) }
                        val chartsDef = async { runCatching { yt.fetchChartShelves(hl = hl, gl = gl, countryName = countryName) }.getOrDefault(emptyList()) }
                        val tasteSeed = _ui.value.recentTracks.firstOrNull() ?: _ui.value.likedTracks.firstOrNull()
                        val similarDef = async {
                            if (tasteSeed != null) runCatching { yt.fetchRelatedTracks(tasteSeed.id, hl = hl, gl = gl) }.getOrDefault(emptyList())
                            else emptyList()
                        }
                        val topArtist = _ui.value.topArtists.firstOrNull()?.first
                        val artistDef = async {
                            if (!topArtist.isNullOrBlank()) runCatching { yt.searchTracks(topArtist, hl = hl, gl = gl) }.getOrDefault(emptyList())
                            else emptyList()
                        }
                        val newRelQuery = "New music $countryName 2026"
                        val newRelDef = async {
                            runCatching { yt.searchTracks(newRelQuery, hl = hl, gl = gl).take(10) }.getOrDefault(emptyList())
                        }
                        val albumQuery = if (!topArtist.isNullOrBlank()) "Full Album $topArtist" else "Best album $countryName"
                        val albumDef = async {
                            runCatching { yt.searchTracks(albumQuery, hl = hl, gl = gl).take(10) }.getOrDefault(emptyList())
                        }
                        val countryMixDef = async {
                            runCatching { yt.searchTracks("Popular music $countryName", hl = hl, gl = gl).take(10) }.getOrDefault(emptyList())
                        }

                        val base = baseDef.await()
                        val charts = chartsDef.await()
                        val similar = similarDef.await()
                        val artistTracks = artistDef.await()
                        val newReleases = newRelDef.await()
                        val albumTracks = albumDef.await()
                        val countryMix = countryMixDef.await()

                        val str = stringsFor(hl)
                        val out = mutableListOf<Shelf>()
                        if (tasteSeed != null && similar.size >= 2) {
                            out.add(Shelf("${str.moreLike} ${tasteSeed.artist}", similar, isList = false))
                        }
                        if (!topArtist.isNullOrBlank() && artistTracks.size >= 2) {
                            out.add(Shelf("${str.mixedForYou} • $topArtist", artistTracks.take(4), isList = true))
                        } else if (countryMix.isNotEmpty()) {
                            out.add(Shelf("${str.mixedForYou} • $countryName", countryMix.take(4), isList = true))
                        }
                        if (albumTracks.size >= 2) {
                            out.add(Shelf(str.albumForYou, albumTracks, isList = false))
                        }
                        if (newReleases.isNotEmpty()) {
                            out.add(Shelf("${str.newRelease} • $countryName", newReleases, isList = false))
                        }
                        if (gl == "ID") {
                            base.forEach { s ->
                                val title = when {
                                    s.title.contains("Hujan", ignoreCase = true) ||
                                        s.title.contains("Perasaan", ignoreCase = true) ||
                                        s.title.contains("Suasana", ignoreCase = true) ||
                                        s.title.contains("Mood", ignoreCase = true) ||
                                        s.title.contains("Rain", ignoreCase = true) ||
                                        s.title.contains("Feeling", ignoreCase = true) -> null
                                    s.title.contains("Mix", ignoreCase = true) || s.title.contains("Pilihan", ignoreCase = true) -> str.mixedForYou
                                    s.title.contains("Trending", ignoreCase = true) -> str.trendingForYou
                                    s.title.contains("Album", ignoreCase = true) -> null
                                    else -> s.title
                                } ?: return@forEach
                                if (out.none { it.title == title }) {
                                    out.add(s.copy(title = title))
                                }
                            }
                        }
                        charts.forEach { c ->
                            if (out.none { it.title == c.title }) out.add(c)
                        }
                        out
                    }
                } else {
                    val enMood = when (mood) {
                        "Santai" -> "chill"
                        "Energi" -> "energy"
                        "Fokus" -> "focus"
                        "Romantis" -> "romantic"
                        "Galau" -> "sad"
                        else -> mood
                    }
                    val moodTracks = yt.searchTracks("$enMood music $countryName", hl = hl, gl = gl)
                    if (moodTracks.isNotEmpty()) {
                        listOf(Shelf("$enMood • $countryName", moodTracks, isList = true))
                    } else emptyList()
                }
                val explore = if (mood == "Semua") {
                    shelves.filter {
                        it.title.startsWith("Top") ||
                            it.title.startsWith("Trending") ||
                            it.tracks.any { t -> t.browseId.isNotBlank() }
                    }
                } else emptyList()
                _ui.update {
                    it.copy(
                        homeShelves = shelves,
                        exploreShelves = explore.ifEmpty { it.exploreShelves },
                        homeTracks = shelves.flatMap { s -> s.tracks }.distinctBy { t -> t.id },
                        isLoadingHome = false,
                    )
                }
                // Pre-warm resume & top 4 visible home tracks so Play feels instant
                _ui.value.recentTracks.firstOrNull()?.id?.let { SwaraPlaybackService.prefetchTrack(it) }
                shelves.take(2).flatMap { it.tracks }.distinctBy { it.id }.take(4).forEach {
                    SwaraPlaybackService.prefetchTrack(it.id)
                }
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isLoadingHome = false,
                        errorMessage = "Gagal memuat Beranda. Cek koneksi internet lalu ketuk muat ulang."
                    )
                }
            }
        }
    }

    fun selectMood(mood: String) {
        if (_ui.value.activeMood == mood) return
        _ui.update { it.copy(activeMood = mood) }
        loadHome()
    }

    fun setCountry(code: String) {
        if (_ui.value.country == code) return
        prefs.edit().putString("country", code).apply()
        _ui.update { it.copy(country = code) }
        loadHome()
    }

    fun setLanguage(code: String) {
        if (_ui.value.language == code) return
        prefs.edit().putString("lang", code).apply()
        _ui.update { it.copy(language = code) }
        loadHome()
    }

    fun setDynamicIslandEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_island", enabled).apply()
        SwaraPlaybackService.dynamicIslandEnabled = enabled
        SwaraPlaybackService.refreshIslandFromUi()
        _ui.update { it.copy(dynamicIslandEnabled = enabled) }
    }

    fun setSoundStyle(style: String) {
        val (rate, rev) = when (style) {
            "Sped up" -> 1.25f to 0
            "Slowed + reverb" -> 0.85f to 58
            "Nightcore" -> 1.35f to 15
            "Lo-fi" -> 0.94f to 38
            "8D" -> 1.00f to 65
            "Konser" -> 1.00f to 92
            "Bass Heavy" -> 1.00f to 10
            "Vaporwave" -> 0.78f to 75
            "Vokal / Podcast" -> 1.00f to 0
            else -> 1.00f to 0
        }
        controller?.setPlaybackParameters(PlaybackParameters(rate, rate))
        SwaraPlaybackService.applyRoomReverb(rev)
        SwaraPlaybackService.setSpatial8D(style == "8D")
        val styleEq = when (style) {
            "Lo-fi" -> listOf(4f, 3f, 0f, -2f, -4f)
            "Nightcore" -> listOf(2f, 1f, 0f, 3f, 5f)
            "8D" -> listOf(3f, 1f, -1f, 2f, 4f)
            "Konser" -> listOf(4f, 2f, 1f, 3f, 4f)
            "Bass Heavy" -> listOf(7f, 5f, 0f, 1f, 2f)
            "Vaporwave" -> listOf(4f, 3f, 0f, -2f, -3f)
            "Vokal / Podcast" -> listOf(-2f, 0f, 5f, 4f, 2f)
            else -> _ui.value.eqBands
        }
        SwaraPlaybackService.applyEqualizerBands(styleEq)
        _ui.update { it.copy(soundStyle = style, speed = rate, pitch = rate, reverbPct = rev) }
    }

    fun setVinylRate(rate: Float) {
        val r = rate.coerceIn(0.6f, 1.6f)
        controller?.setPlaybackParameters(PlaybackParameters(r, r))
        _ui.update { it.copy(soundStyle = "Custom", speed = r, pitch = r) }
    }

    fun setRoomReverb(percent: Int) {
        val p = percent.coerceIn(0, 100)
        SwaraPlaybackService.applyRoomReverb(p)
        _ui.update { it.copy(reverbPct = p) }
    }

    fun resetSoundStyle() {
        setSoundStyle("Normal")
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _ui.update { it.copy(themeMode = mode) }
    }

    fun setAudioQuality(quality: String) {
        prefs.edit().putString("audio_quality", quality).apply()
        SwaraPlaybackService.audioQuality = quality
        SwaraPlaybackService.clearStreamCache()
        _ui.update { it.copy(audioQuality = quality) }
    }

    fun setPlaybackParams(speed: Float, pitch: Float) {
        val s = speed.coerceIn(0.5f, 2.0f)
        val p = pitch.coerceIn(0.5f, 2.0f)
        controller?.setPlaybackParameters(PlaybackParameters(s, p))
        _ui.update { it.copy(soundStyle = "Custom", speed = s, pitch = p) }
    }

    fun setPlayerVolume(vol: Float) {
        val v = vol.coerceIn(0f, 1f)
        controller?.volume = v
        _ui.update { it.copy(volume = v) }
    }

    fun setAccent(accentId: String) {
        prefs.edit().putString("accent", accentId).apply()
        _ui.update { it.copy(accentId = accentId) }
    }

    fun setCustomAccent(argb: Long) {
        prefs.edit().putString("accent", "custom").putLong("custom_accent_hex", argb).apply()
        _ui.update { it.copy(accentId = "custom", customAccentHex = argb) }
    }

    fun cycleSleepTimer() {
        val currentMin = (_ui.value.sleepTimerRemainingSec + 59) / 60
        val nextMin = when {
            currentMin <= 0 -> 15
            currentMin <= 15 -> 30
            currentMin <= 30 -> 45
            currentMin <= 45 -> 60
            else -> 0
        }
        sleepJob?.cancel()
        if (nextMin == 0) {
            _ui.update { it.copy(sleepTimerRemainingSec = 0) }
            return
        }
        _ui.update { it.copy(sleepTimerRemainingSec = nextMin * 60) }
        sleepJob = viewModelScope.launch {
            while (_ui.value.sleepTimerRemainingSec > 0) {
                delay(1000)
                val rem = (_ui.value.sleepTimerRemainingSec - 1).coerceAtLeast(0)
                _ui.update { it.copy(sleepTimerRemainingSec = rem) }
                if (rem == 0) {
                    controller?.pause()
                }
            }
        }
    }

    fun removeFromQueue(index: Int) {
        val mc = controller ?: return
        if (index in 0 until mc.mediaItemCount && index != mc.currentMediaItemIndex) {
            mc.removeMediaItem(index)
        }
    }

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce typing so stale results never overwrite newer ones
            _ui.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val q = query.trim()
                val tracks = yt.searchTracks(q, hl = _ui.value.language, gl = _ui.value.country)
                addSearchHistory(q)
                _ui.update { it.copy(searchResults = tracks, isSearching = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(isSearching = false, errorMessage = "Pencarian gagal. Cek koneksi internet.") }
            }
        }
    }

    fun removeSearchHistory(query: String) {
        val next = _ui.value.searchHistory.filterNot { it.equals(query, ignoreCase = true) }
        saveSearchHistory(next)
        _ui.update { it.copy(searchHistory = next) }
    }

    fun clearSearchHistory() {
        saveSearchHistory(emptyList())
        _ui.update { it.copy(searchHistory = emptyList()) }
    }

    private fun addSearchHistory(q: String) {
        if (q.length < 2) return
        val next = (listOf(q) + _ui.value.searchHistory.filterNot { it.equals(q, ignoreCase = true) }).take(12)
        saveSearchHistory(next)
        _ui.update { it.copy(searchHistory = next) }
    }

    private fun saveSearchHistory(list: List<String>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString("search_history", arr.toString()).apply()
    }

    private fun loadSearchHistory(): List<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("search_history", "[]"))
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
    }.getOrDefault(emptyList())

    /** Handles Android "play X" (MEDIA_PLAY_FROM_SEARCH): search and play the first hit. */
    fun playFromSearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val tracks = runCatching { yt.searchTracks(query.trim()) }.getOrDefault(emptyList())
            if (tracks.isEmpty()) {
                _ui.update { it.copy(errorMessage = "Lagu \"$query\" tidak ditemukan") }
            } else {
                // Wait briefly for the MediaController connection on cold start.
                repeat(20) { if (controller == null) delay(250) }
                playFromList(listOf(tracks.first()), 0)
            }
        }
    }


    fun playFromList(tracks: List<Track>, index: Int) {
        if (index !in tracks.indices) return
        val mc = controller ?: run {
            _ui.update { it.copy(errorMessage = "Layanan audio belum siap, coba ketuk lagi") }
            return
        }
        _ui.update { it.copy(errorMessage = null) }
        mc.setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
        mc.prepare()
        mc.play()
        // Pre-warm next 2 tracks in queue for zero-wait transitions
        tracks.getOrNull(index + 1)?.id?.let { SwaraPlaybackService.prefetchTrack(it) }
        tracks.getOrNull(index + 2)?.id?.let { SwaraPlaybackService.prefetchTrack(it) }
        if (tracks.size == 1) {
            val seedId = tracks[0].id
            viewModelScope.launch {
                val related = runCatching { yt.fetchRelatedTracks(seedId, gl = _ui.value.country) }.getOrDefault(emptyList())
                val c = controller ?: return@launch
                if (related.isNotEmpty() && c.mediaItemCount == 1 && c.currentMediaItem?.mediaId == seedId) {
                    c.addMediaItems(related.map { it.toMediaItem() })
                }
            }
        }
    }

    fun playSingleWithRadio(track: Track) {
        playFromList(listOf(track), 0)
    }

    fun importPlaylist(url: String, onSuccess: () -> Unit = {}) {
        if (url.isBlank() || _ui.value.isImportingPlaylist) return
        viewModelScope.launch {
            _ui.update { it.copy(isImportingPlaylist = true, errorMessage = null) }
            val res = yt.importPlaylistFromUrl(url, gl = _ui.value.country)
            res.onSuccess { (title, tracks) ->
                val next = _ui.value.playlists + UserPlaylist("pl_${System.currentTimeMillis()}", title, tracks)
                _ui.update { it.copy(playlists = next, isImportingPlaylist = false) }
                savePlaylists(next)
                onSuccess()
            }.onFailure { err ->
                _ui.update { it.copy(isImportingPlaylist = false, errorMessage = err.message ?: "Gagal impor playlist") }
            }
        }
    }

    fun playNextList(tracks: List<Track>) {
        val mc = controller ?: return
        val playable = tracks.filter { it.browseId.isBlank() }
        if (playable.isEmpty()) return
        if (mc.mediaItemCount == 0) {
            playFromList(playable, 0)
            return
        }
        val insertIdx = (mc.currentMediaItemIndex + 1).coerceIn(0, mc.mediaItemCount)
        playable.reversed().forEach { t ->
            mc.addMediaItem(insertIdx, t.toMediaItem())
        }
    }

    fun addToQueue(track: Track) {
        val mc = controller ?: return
        if (mc.mediaItemCount == 0) {
            playFromList(listOf(track), 0)
            return
        }
        val exists = (0 until mc.mediaItemCount).any { mc.getMediaItemAt(it).mediaId == track.id }
        if (!exists) mc.addMediaItem(track.toMediaItem())
    }

    fun togglePlayPause() {
        val mc = controller ?: return
        if (mc.isPlaying) mc.pause() else {
            if (mc.playbackState == Player.STATE_IDLE) mc.prepare()
            mc.play()
        }
    }

    fun playNext() {
        controller?.seekToNextMediaItem()
    }

    fun playPrevious() {
        controller?.seekToPrevious()
    }

    private fun syncFromPlayer(mc: MediaController) {
        val queue = (0 until mc.mediaItemCount).map { mc.getMediaItemAt(it).toTrack() }
        val current = mc.currentMediaItem?.toTrack()
        val dur = mc.duration.coerceAtLeast(0L)
        _ui.update {
            it.copy(
                queue = queue,
                currentIndex = mc.currentMediaItemIndex,
                currentTrack = current,
                isPlaying = mc.isPlaying,
                shuffleEnabled = mc.shuffleModeEnabled,
                repeatMode = mc.repeatMode,
                durationMs = dur,
            )
        }
        if (current != null && current.id != lastLyricsTrackId) {
            lastLyricsTrackId = current.id
            val updatedRecent = (listOf(current) + _ui.value.recentTracks.filterNot { it.id == current.id }).take(30)
            _ui.update { it.copy(recentTracks = updatedRecent, lyrics = null, isLoadingLyrics = true) }
            saveTrackList("recent", updatedRecent)
            recordPlayStats(current)
            viewModelScope.launch {
                val res = yt.fetchLyrics(current.title, current.artist, (dur / 1000).toInt())
                if (_ui.value.currentTrack?.id == current.id) {
                    _ui.update { it.copy(lyrics = res, isLoadingLyrics = false) }
                }
            }
        }
        if (_ui.value.listenIsHost && _ui.value.listenRoomCode != null) {
            broadcastRoomState(current, mc.currentPosition, mc.isPlaying)
        }
    }

    fun openBrowseItem(item: Track) {
        if (item.browseId.isBlank()) return
        if (item.browseId.startsWith("UC")) {
            openArtist(item.title, item.artworkUrl)
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(openedPlaylistTitle = item.title, openedPlaylistTracks = emptyList(), isLoadingPlaylist = true) }
            val tracks = runCatching {
                yt.fetchPlaylistTracks(item.browseId, hl = _ui.value.language, gl = _ui.value.country)
            }.getOrDefault(emptyList())
            _ui.update { it.copy(openedPlaylistTracks = tracks, isLoadingPlaylist = false) }
        }
    }

    fun openArtist(name: String, imageUrl: String = "") {
        val clean = name.substringBefore(",").substringBefore("&").trim().ifBlank { return }
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    openedArtistName = clean,
                    openedArtistImage = imageUrl,
                    openedArtistTracks = emptyList(),
                    isLoadingArtist = true,
                )
            }
            val tracks = runCatching {
                yt.searchTracks(clean, hl = _ui.value.language, gl = _ui.value.country)
            }.getOrDefault(emptyList())
            val resolvedImg = imageUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
            _ui.update { st ->
                val imgs = if (resolvedImg.isNotBlank()) {
                    (st.artistImages + (clean to resolvedImg)).also { saveArtistImages(it) }
                } else st.artistImages
                st.copy(
                    openedArtistImage = resolvedImg,
                    openedArtistTracks = tracks,
                    artistImages = imgs,
                    isLoadingArtist = false,
                )
            }
        }
    }

    fun openAlbum(track: Track) {
        val albumName = "${track.title} • ${track.artist}"
        val q = "${track.title} ${track.artist} album"
        viewModelScope.launch {
            _ui.update { it.copy(openedAlbumTitle = albumName, openedAlbumTracks = emptyList(), isLoadingAlbum = true) }
            val tracks = runCatching {
                yt.searchTracks(q, hl = _ui.value.language, gl = _ui.value.country)
            }.getOrDefault(emptyList())
            _ui.update { it.copy(openedAlbumTracks = tracks.ifEmpty { listOf(track) }, isLoadingAlbum = false) }
        }
    }

    fun closeAlbum() {
        _ui.update { it.copy(openedAlbumTitle = null, openedAlbumTracks = emptyList(), isLoadingAlbum = false) }
    }

    fun excludeFromTaste(track: Track) {
        _ui.update { st ->
            val nextEx = st.excludedTrackIds + track.id
            val arr = org.json.JSONArray()
            nextEx.forEach { arr.put(it) }
            prefs.edit().putString("excluded_tracks", arr.toString()).apply()
            val filteredRecent = st.recentTracks.filterNot { it.id in nextEx }
            saveTrackList("recent", filteredRecent)
            st.copy(excludedTrackIds = nextEx, recentTracks = filteredRecent)
        }
        loadHome()
    }

    private fun loadExcludedTrackIds(): Set<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("excluded_tracks", "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }.toSet()
    }.getOrDefault(emptySet())

    fun closeArtist() {
        _ui.update { it.copy(openedArtistName = null, openedArtistTracks = emptyList(), isLoadingArtist = false) }
    }

    fun toggleFollowArtist(name: String) {
        val clean = name.trim().ifBlank { return }
        var needsFetch = false
        _ui.update { st ->
            val exists = st.followedArtists.any { it.equals(clean, ignoreCase = true) }
            val next = if (exists) st.followedArtists.filterNot { it.equals(clean, ignoreCase = true) } else listOf(clean) + st.followedArtists
            val arr = org.json.JSONArray()
            next.forEach { arr.put(it) }
            prefs.edit().putString("followed_artists", arr.toString()).apply()

            var nextImgs = st.artistImages
            if (!exists && nextImgs[clean].isNullOrBlank()) {
                val candidate = st.openedArtistImage.takeIf { st.openedArtistName.equals(clean, ignoreCase = true) && it.isNotBlank() }
                    ?: (st.recentTracks + st.likedTracks + st.homeTracks)
                        .firstOrNull { it.artist.contains(clean, ignoreCase = true) && it.artworkUrl.isNotBlank() }
                        ?.artworkUrl
                if (!candidate.isNullOrBlank()) {
                    nextImgs = nextImgs + (clean to candidate)
                    saveArtistImages(nextImgs)
                } else {
                    needsFetch = true
                }
            }
            st.copy(followedArtists = next, artistImages = nextImgs)
        }
        if (needsFetch) resolveMissingArtistImages(listOf(clean))
    }

    private fun resolveMissingArtistImages(names: List<String>) {
        if (names.isEmpty()) return
        viewModelScope.launch {
            val st = _ui.value
            val updated = st.artistImages.toMutableMap()
            var changed = false
            for (name in names) {
                if (!updated[name].isNullOrBlank()) continue
                val localHit = (st.recentTracks + st.likedTracks + st.homeTracks)
                    .firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }
                    ?.artworkUrl
                val img = localHit ?: runCatching {
                    yt.searchTracks(name, hl = st.language, gl = st.country).firstOrNull()?.artworkUrl
                }.getOrNull()
                if (!img.isNullOrBlank()) {
                    updated[name] = img
                    changed = true
                }
            }
            if (changed) {
                saveArtistImages(updated)
                _ui.update { it.copy(artistImages = updated) }
            }
        }
    }

    private fun saveArtistImages(map: Map<String, String>) {
        val obj = org.json.JSONObject()
        map.forEach { (k, v) -> if (v.isNotBlank()) obj.put(k, v) }
        prefs.edit().putString("artist_images", obj.toString()).apply()
    }

    private fun loadArtistImages(): Map<String, String> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("artist_images", "{}"))
        val out = mutableMapOf<String, String>()
        obj.keys().forEach { k ->
            val v = obj.optString(k)
            if (k.isNotBlank() && v.isNotBlank()) out[k] = v
        }
        out
    }.getOrDefault(emptyMap())

    private fun loadFollowedArtists(): List<String> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("followed_artists", "[]"))
        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
    }.getOrDefault(emptyList())

    fun identifyBySnippet(snippet: String) {
        if (snippet.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isIdentifying = true, identifyResults = emptyList()) }
            val hits = runCatching { yt.identifyBySnippet(snippet, _ui.value.language, _ui.value.country) }.getOrDefault(emptyList())
            _ui.update { it.copy(identifyResults = hits, isIdentifying = false) }
        }
    }

    private val safeIdRegex = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val safeRoomRegex = Regex("^[A-Z0-9]{4,32}$")
    private val allowedArtworkHosts = setOf(
        "i.ytimg.com",
        "lh3.googleusercontent.com",
        "yt3.googleusercontent.com",
        "music.youtube.com"
    )

    private fun safeDownloadFile(trackId: String): java.io.File? {
        if (!safeIdRegex.matches(trackId)) return null
        val dir = java.io.File(getApplication<Application>().filesDir, "downloads").apply { mkdirs() }
        val target = java.io.File(dir, "$trackId.m4a")
        return if (target.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) target else null
    }

    private fun sanitizeRemoteArtworkUrl(raw: String): String {
        val parsed = runCatching { java.net.URI(raw) }.getOrNull() ?: return ""
        val host = parsed.host?.lowercase() ?: return ""
        return if (parsed.scheme == "https" && host in allowedArtworkHosts) raw else ""
    }

    fun createListenRoom() {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rng = java.security.SecureRandom()
        val code = buildString(10) {
            repeat(10) { append(alphabet[rng.nextInt(alphabet.length)]) }
        }
        listenJob?.cancel()
        _ui.update { it.copy(listenRoomCode = code, listenIsHost = true, listenPeerCount = 0, listenStatus = "Host aktif • Kode: $code • 0 bergabung") }
        val mc = controller
        broadcastRoomState(_ui.value.currentTrack, mc?.currentPosition ?: 0L, mc?.isPlaying ?: false)
        listenJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val peers = java.util.concurrent.ConcurrentHashMap<String, Long>()
            while (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) {
                runCatching {
                    val req = okhttp3.Request.Builder().url("https://ntfy.sh/swara-room-$code/sse").build()
                    sseClient.newCall(req).execute().use { res ->
                        val reader = res.body?.byteStream()?.bufferedReader() ?: return@use
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val raw = line.removePrefix("data:").trim()
                            val outer = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: continue
                            val msg = outer.optString("message")
                            if (!msg.startsWith("{")) continue
                            val obj = runCatching { org.json.JSONObject(msg) }.getOrNull() ?: continue
                            val ev = obj.optString("ev")
                            val peer = obj.optString("peer")
                            if ((ev == "join" || ev == "ping") && peer.isNotBlank()) {
                                val now = System.currentTimeMillis()
                                peers[peer] = now
                                peers.entries.removeIf { now - it.value > 50_000L }
                                val cnt = peers.size
                                _ui.update { it.copy(listenPeerCount = cnt, listenStatus = "Host aktif • Kode: $code • $cnt bergabung") }
                                if (ev == "join") {
                                    val cur = _ui.value.currentTrack
                                    broadcastRoomState(cur, _progress.value.first, _ui.value.isPlaying)
                                }
                            } else if (ev == "leave" && peer.isNotBlank()) {
                                peers.remove(peer)
                                val cnt = peers.size
                                _ui.update { it.copy(listenPeerCount = cnt, listenStatus = "Host aktif • Kode: $code • $cnt bergabung") }
                            }
                        }
                    }
                }
                kotlinx.coroutines.delay(1500L)
            }
        }
    }

    fun joinListenRoom(rawCode: String) {
        val code = rawCode.trim().uppercase()
        if (!safeRoomRegex.matches(code)) return
        listenJob?.cancel()
        val peerId = "p_${System.currentTimeMillis()}_${(100..999).random()}"
        _ui.update { it.copy(listenRoomCode = code, listenIsHost = false, listenPeerCount = 0, listenStatus = "Menghubungkan ke ruang $code...") }
        listenJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val heartbeat = launch {
                sendPeerEvent(code, "join", peerId)
                while (true) {
                    kotlinx.coroutines.delay(20_000L)
                    sendPeerEvent(code, "ping", peerId)
                }
            }
            try {
                while (kotlinx.coroutines.currentCoroutineContext()[Job]?.isActive == true) {
                    try {
                        val req = okhttp3.Request.Builder().url("https://ntfy.sh/swara-room-$code/sse").build()
                        sseClient.newCall(req).execute().use { res ->
                            val reader = res.body?.byteStream()?.bufferedReader() ?: return@use
                            _ui.update { it.copy(listenStatus = "Terhubung ke ruang $code") }
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (!line.startsWith("data:")) continue
                                val raw = line.removePrefix("data:").trim()
                                val outer = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: continue
                                val msg = outer.optString("message")
                                if (!msg.startsWith("{")) continue
                                val stateObj = runCatching { org.json.JSONObject(msg) }.getOrNull() ?: continue
                                val vid = stateObj.optString("id")
                                if (!safeIdRegex.matches(vid)) continue
                                val title = stateObj.optString("t").take(200)
                                val artist = stateObj.optString("a").take(200)
                                val img = sanitizeRemoteArtworkUrl(stateObj.optString("img"))
                                val pos = stateObj.optLong("pos", 0L).coerceAtLeast(0L)
                                val playing = stateObj.optBoolean("play", true)
                                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    val mc = controller
                                    if (_ui.value.currentTrack?.id != vid) {
                                        playFromList(listOf(Track(vid, title, artist, img)), 0)
                                    } else if (mc != null) {
                                        if (kotlin.math.abs(mc.currentPosition - pos) > 3500) mc.seekTo(pos)
                                        if (playing && !mc.isPlaying) mc.play()
                                        else if (!playing && mc.isPlaying) mc.pause()
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        _ui.update { it.copy(listenStatus = "Menyambung ulang ke $code...") }
                    }
                    kotlinx.coroutines.delay(1500L)
                }
            } finally {
                heartbeat.cancel()
                sendPeerEvent(code, "leave", peerId)
            }
        }
    }

    private fun sendPeerEvent(code: String, ev: String, peerId: String) {
        val payload = org.json.JSONObject().put("ev", ev).put("peer", peerId).toString()
        val req = okhttp3.Request.Builder()
            .url("https://ntfy.sh/swara-room-$code")
            .post(okhttp3.RequestBody.create(null, payload))
            .build()
        runCatching { dlClient.newCall(req).execute().close() }
    }

    fun leaveListenRoom() {
        listenJob?.cancel()
        _ui.update { it.copy(listenRoomCode = null, listenIsHost = false, listenPeerCount = 0, listenStatus = null) }
    }

    private fun broadcastRoomState(track: Track?, posMs: Long, playing: Boolean) {
        val code = _ui.value.listenRoomCode ?: return
        if (track == null) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val payload = org.json.JSONObject()
                .put("id", track.id)
                .put("t", track.title)
                .put("a", track.artist)
                .put("img", track.artworkUrl)
                .put("pos", posMs)
                .put("play", playing)
                .toString()
            val req = okhttp3.Request.Builder()
                .url("https://ntfy.sh/swara-room-$code")
                .post(okhttp3.RequestBody.create(null, payload))
                .build()
            runCatching { dlClient.newCall(req).execute().close() }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val next = _ui.value.playlists.map { pl ->
            if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl
        }
        _ui.update { it.copy(playlists = next) }
        savePlaylists(next)
    }

    fun importDeviceAudio(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val added = mutableListOf<Track>()
            for (uri in uris) {
                runCatching {
                    ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val mmr = android.media.MediaMetadataRetriever()
                var title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Local Audio"
                var artist = "Local File"
                var dur = 0L
                runCatching {
                    mmr.setDataSource(ctx, uri)
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                    dur = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                }
                runCatching { mmr.release() }
                val id = "loc_${uri.toString().hashCode().toUInt()}"
                val uriStr = uri.toString()
                SwaraPlaybackService.registerLocalTrack(id, uriStr)
                val uriObj = runCatching { org.json.JSONObject(prefs.getString("local_uris", "{}")) }.getOrDefault(org.json.JSONObject())
                uriObj.put(id, uriStr)
                prefs.edit().putString("local_uris", uriObj.toString()).apply()
                added.add(Track(id = id, title = title, artist = artist, artworkUrl = "", durationMs = dur))
            }
            if (added.isNotEmpty()) {
                val merged = (added + _ui.value.localTracks).distinctBy { it.id }
                saveTrackList("local", merged)
                _ui.update { it.copy(localTracks = merged) }
            }
        }
    }

    fun closeOpenedPlaylist() {
        _ui.update { it.copy(openedPlaylistTitle = null, openedPlaylistTracks = emptyList(), isLoadingPlaylist = false) }
    }

    fun createPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val next = _ui.value.playlists + UserPlaylist("pl_${System.currentTimeMillis()}", clean, emptyList())
        _ui.update { it.copy(playlists = next) }
        savePlaylists(next)
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        val next = _ui.value.playlists.map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.id == track.id }) {
                pl.copy(tracks = pl.tracks + track)
            } else pl
        }
        _ui.update { it.copy(playlists = next) }
        savePlaylists(next)
    }

    fun deletePlaylist(playlistId: String) {
        val next = _ui.value.playlists.filterNot { it.id == playlistId }
        _ui.update { it.copy(playlists = next) }
        savePlaylists(next)
    }

    fun updatePlaylist(playlistId: String, newName: String, pickedImageUri: android.net.Uri? = null) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var savedCoverPath: String? = null
            if (pickedImageUri != null) {
                runCatching {
                    val dir = getApplication<Application>().filesDir.resolve("playlist_covers").apply { mkdirs() }
                    val out = java.io.File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
                    getApplication<Application>().contentResolver.openInputStream(pickedImageUri)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (out.exists() && out.length() > 0L) savedCoverPath = out.absolutePath
                }
            }
            val next = _ui.value.playlists.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(name = cleanName, artworkUri = savedCoverPath ?: pl.artworkUri)
                } else pl
            }
            _ui.update { it.copy(playlists = next) }
            savePlaylists(next)
        }
    }

    fun setDownloadTreeUri(uriStr: String) {
        prefs.edit().putString("download_tree_uri", uriStr).apply()
        _ui.update { it.copy(downloadTreeUri = uriStr) }
    }

    private fun copyToUserFolder(track: Track, src: java.io.File, treeUriStr: String) {
        if (treeUriStr.isBlank()) return
        runCatching {
            val ctx = getApplication<Application>()
            val treeUri = android.net.Uri.parse(treeUriStr)
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            val parentDocUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val safeName = "${track.title} - ${track.artist}".replace(Regex("[\\\\/:*?\"<>|]"), "_").take(90) + ".m4a"
            val created = android.provider.DocumentsContract.createDocument(
                ctx.contentResolver,
                parentDocUri,
                "audio/mp4",
                safeName
            ) ?: return
            ctx.contentResolver.openOutputStream(created)?.use { out ->
                src.inputStream().use { inp -> inp.copyTo(out) }
            }
        }
    }

    private fun notifyDownloadProgress(
        done: Int,
        total: Int,
        title: String,
        finished: Boolean = false,
        succeeded: Int = done,
    ) {
        runCatching {
            val ctx = getApplication<Application>()
            val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val progChId = "swara_downloads"
            val doneChId = "swara_downloads_done"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(progChId) == null) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(
                            progChId,
                            "Progres Unduhan",
                            android.app.NotificationManager.IMPORTANCE_LOW
                        )
                    )
                }
                if (nm.getNotificationChannel(doneChId) == null) {
                    nm.createNotificationChannel(
                        android.app.NotificationChannel(
                            doneChId,
                            "Unduhan Selesai",
                            android.app.NotificationManager.IMPORTANCE_DEFAULT
                        )
                    )
                }
            }
            if (finished) {
                nm.cancel(4201)
                val doneBuilder = androidx.core.app.NotificationCompat.Builder(ctx, doneChId)
                    .setSmallIcon(
                        if (succeeded > 0) android.R.drawable.stat_sys_download_done
                        else android.R.drawable.stat_notify_error
                    )
                    .setContentTitle(if (succeeded > 0) "Unduhan selesai" else "Unduhan gagal")
                    .setContentText(
                        if (succeeded > 0) "$succeeded / $total lagu berhasil disimpan"
                        else "Tidak ada lagu yang berhasil diunduh"
                    )
                    .setAutoCancel(true)
                nm.notify(4202, doneBuilder.build())
            } else {
                val builder = androidx.core.app.NotificationCompat.Builder(ctx, progChId)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setContentTitle("Mengunduh $done / $total lagu")
                    .setContentText(title)
                    .setProgress(total.coerceAtLeast(1), done.coerceIn(0, total), false)
                nm.notify(4201, builder.build())
            }
        }
    }

    private fun fetchAudioToFile(url: String, dest: java.io.File) {
        if (!url.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("URL unduhan tidak valid")
        }
        val vrUa = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        val webUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        val maxBytes = 100L * 1024L * 1024L

        fun tryDownload(ua: String): Int {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", ua)
                .build()
            dlClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return res.code
                val body = res.body ?: throw Exception("Respons kosong")
                if (body.contentLength() > maxBytes) throw Exception("Ukuran file terlalu besar")
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            total += read
                            if (total > maxBytes) throw Exception("Ukuran file melebihi batas")
                            output.write(buf, 0, read)
                        }
                    }
                }
                if (dest.length() < 1024L) throw Exception("File unduhan kosong")
                return 200
            }
        }

        val code1 = tryDownload(vrUa)
        if (code1 == 200) return
        if (code1 == 403) {
            val code2 = tryDownload(webUa)
            if (code2 == 200) return
            throw Exception("HTTP $code2")
        }
        throw Exception("HTTP $code1")
    }

    fun dismissDownloadBanner() {
        _ui.update { it.copy(downloadStatusBanner = null) }
    }

    fun downloadPlaylist(tracks: List<Track>, treeUriOverride: String? = null) {
        val targets = tracks.filter { t ->
            t.browseId.isBlank() &&
                t.id !in _ui.value.downloadingIds &&
                _ui.value.downloadedTracks.none { it.id == t.id }
        }
        if (targets.isEmpty()) {
            _ui.update { it.copy(downloadStatusBanner = "Semua lagu sudah ada di Downloaded") }
            return
        }
        val folderUri = treeUriOverride ?: _ui.value.downloadTreeUri
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pm = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val wl = runCatching { pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Swara:BatchDownload")?.apply { acquire(30 * 60 * 1000L) } }.getOrNull()
            val totalCount = targets.size
            var doneCount = 0
            var succeededCount = 0
            var lastErr = ""
            try {
                _ui.update {
                    it.copy(
                        downloadDone = 0,
                        downloadTotal = totalCount,
                        downloadCurrentTitle = targets.firstOrNull()?.title.orEmpty(),
                        downloadStatusBanner = null
                    )
                }
                notifyDownloadProgress(0, totalCount, targets.firstOrNull()?.title.orEmpty(), finished = false)
                targets.forEach { track ->
                    val dest = safeDownloadFile(track.id) ?: run {
                        doneCount++
                        return@forEach
                    }
                    _ui.update {
                        it.copy(
                            downloadingIds = it.downloadingIds + track.id,
                            downloadCurrentTitle = track.title
                        )
                    }
                    notifyDownloadProgress(doneCount, totalCount, track.title, finished = false)
                    try {
                        try {
                            val url = yt.resolveAudioUrl(track.id, quality = _ui.value.audioQuality).getOrThrow()
                            fetchAudioToFile(url, dest)
                        } catch (_: Exception) {
                            runCatching { dest.delete() }
                            kotlinx.coroutines.delay(350)
                            val retryUrl = yt.resolveAudioUrl(track.id, quality = _ui.value.audioQuality, preferNewPipe = true).getOrThrow()
                            fetchAudioToFile(retryUrl, dest)
                        }
                        copyToUserFolder(track, dest, folderUri)
                        succeededCount++
                        doneCount++
                        val updated = listOf(track) + _ui.value.downloadedTracks.filterNot { it.id == track.id }
                        _ui.update {
                            it.copy(
                                downloadedTracks = updated,
                                downloadingIds = it.downloadingIds - track.id,
                                downloadDone = doneCount
                            )
                        }
                        saveTrackList("downloads", updated)
                    } catch (e: Exception) {
                        runCatching { dest.delete() }
                        lastErr = e.message ?: "Error"
                        doneCount++
                        _ui.update {
                            it.copy(
                                downloadingIds = it.downloadingIds - track.id,
                                downloadDone = doneCount
                            )
                        }
                    }
                    notifyDownloadProgress(doneCount, totalCount, track.title, finished = false)
                }
                notifyDownloadProgress(doneCount, totalCount, "", finished = true, succeeded = succeededCount)
                val banner = when {
                    succeededCount == totalCount -> "Selesai mengunduh $succeededCount / $totalCount lagu"
                    succeededCount > 0 -> "Unduhan selesai: $succeededCount / $totalCount berhasil (${totalCount - succeededCount} gagal: $lastErr)"
                    else -> "Gagal mengunduh ($lastErr)"
                }
                _ui.update {
                    it.copy(
                        downloadDone = 0,
                        downloadTotal = 0,
                        downloadCurrentTitle = "",
                        downloadStatusBanner = banner
                    )
                }
            } finally {
                runCatching { if (wl?.isHeld == true) wl.release() }
            }
        }
    }

    fun downloadTrack(track: Track, treeUriOverride: String? = null) {
        if (track.browseId.isNotBlank() || track.id in _ui.value.downloadingIds) return
        if (_ui.value.downloadedTracks.any { it.id == track.id }) {
            _ui.update { it.copy(downloadStatusBanner = "${track.title} sudah tersimpan di Downloaded") }
            return
        }
        val dest = safeDownloadFile(track.id) ?: return
        val folderUri = treeUriOverride ?: _ui.value.downloadTreeUri
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _ui.update {
                it.copy(
                    downloadingIds = it.downloadingIds + track.id,
                    downloadDone = 0,
                    downloadTotal = 1,
                    downloadCurrentTitle = track.title,
                    downloadStatusBanner = null
                )
            }
            notifyDownloadProgress(0, 1, track.title, finished = false)
            try {
                try {
                    val url = yt.resolveAudioUrl(track.id, quality = _ui.value.audioQuality).getOrThrow()
                    fetchAudioToFile(url, dest)
                } catch (_: Exception) {
                    runCatching { dest.delete() }
                    kotlinx.coroutines.delay(350)
                    val retryUrl = yt.resolveAudioUrl(track.id, quality = _ui.value.audioQuality, preferNewPipe = true).getOrThrow()
                    fetchAudioToFile(retryUrl, dest)
                }
                copyToUserFolder(track, dest, folderUri)
                val updated = listOf(track) + _ui.value.downloadedTracks.filterNot { it.id == track.id }
                _ui.update {
                    it.copy(
                        downloadedTracks = updated,
                        downloadingIds = it.downloadingIds - track.id,
                        downloadDone = 1,
                        downloadStatusBanner = "Selesai mengunduh: ${track.title}"
                    )
                }
                saveTrackList("downloads", updated)
                notifyDownloadProgress(1, 1, track.title, finished = true, succeeded = 1)
            } catch (e: Exception) {
                runCatching { dest.delete() }
                notifyDownloadProgress(1, 1, track.title, finished = true, succeeded = 0)
                _ui.update {
                    it.copy(
                        downloadingIds = it.downloadingIds - track.id,
                        errorMessage = "Gagal unduh: ${e.message ?: "Error"}"
                    )
                }
            } finally {
                _ui.update { it.copy(downloadDone = 0, downloadTotal = 0, downloadCurrentTitle = "") }
            }
        }
    }

    fun deleteDownload(trackId: String) {
        safeDownloadFile(trackId)?.delete()
        val updated = _ui.value.downloadedTracks.filterNot { it.id == trackId }
        _ui.update { it.copy(downloadedTracks = updated) }
        saveTrackList("downloads", updated)
    }

    fun clearListenHistory() {
        saveTrackList("recent", emptyList())
        _ui.update { it.copy(recentTracks = emptyList()) }
    }

    fun clearAllDownloads() {
        _ui.value.downloadedTracks.forEach { safeDownloadFile(it.id)?.delete() }
        val dlDir = getApplication<Application>().filesDir.resolve("downloads")
        runCatching { dlDir.listFiles()?.forEach { it.delete() } }
        saveTrackList("downloads", emptyList())
        _ui.update { it.copy(downloadedTracks = emptyList()) }
    }

    fun setSongCacheEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("song_cache_enabled", enabled).apply()
        SwaraPlaybackService.songCacheEnabled = enabled
        if (!enabled) clearSongCache()
        _ui.update { it.copy(songCacheEnabled = enabled) }
    }

    fun setMaxSongCacheMb(mb: Int) {
        val clamped = mb.coerceIn(128, 2048)
        prefs.edit().putInt("max_song_cache_mb", clamped).apply()
        SwaraPlaybackService.maxSongCacheMb = clamped
        SwaraPlaybackService.enforceSongCacheLimit(getApplication())
        _ui.update { it.copy(maxSongCacheMb = clamped) }
    }

    fun setRomanizeLyrics(enabled: Boolean) {
        prefs.edit().putBoolean("romanize_lyrics", enabled).apply()
        _ui.update { it.copy(romanizeLyrics = enabled) }
    }

    fun clearSongCache() {
        val app = getApplication<Application>()
        runCatching { coil.Coil.imageLoader(app).memoryCache?.clear() }
        runCatching { app.cacheDir.resolve("image_cache").deleteRecursively() }
        runCatching { app.cacheDir.resolve("song_cache").deleteRecursively() }
    }

    private fun recordPlayStats(track: Track) {
        val today = java.time.LocalDate.now().toString()
        val lastDate = prefs.getString("last_play_date", "") ?: ""
        var streak = prefs.getInt("streak_days", 1)
        if (lastDate.isNotEmpty() && lastDate != today) {
            val yesterday = java.time.LocalDate.now().minusDays(1).toString()
            streak = if (lastDate == yesterday) streak + 1 else 1
        }
        val plays = prefs.getInt("total_plays", 0) + 1
        val secs = prefs.getInt("total_seconds", 0) + 180
        val artistJson = org.json.JSONObject(prefs.getString("artist_counts", "{}") ?: "{}")
        val cleanArtist = track.artist.trim().ifBlank { "Artis" }
        artistJson.put(cleanArtist, artistJson.optInt(cleanArtist, 0) + 1)
        val songJson = org.json.JSONObject(prefs.getString("song_counts", "{}") ?: "{}")
        if (track.id.isNotBlank()) {
            songJson.put(track.id, songJson.optInt(track.id, 0) + 1)
        }
        prefs.edit()
            .putString("last_play_date", today)
            .putInt("streak_days", streak)
            .putInt("total_plays", plays)
            .putInt("total_seconds", secs)
            .putString("artist_counts", artistJson.toString())
            .putString("song_counts", songJson.toString())
            .apply()
        _ui.update {
            it.copy(
                streakDays = streak,
                totalPlays = plays,
                totalMinutes = secs / 60,
                topArtists = loadTopArtists(),
                topSongs = loadTopSongs(),
            )
        }
    }

    private fun loadTopArtists(): List<Pair<String, Int>> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("artist_counts", "{}") ?: "{}")
        obj.keys().asSequence().map { it to obj.optInt(it, 0) }.sortedByDescending { it.second }.take(5).toList()
    }.getOrDefault(emptyList())

    private fun loadTopSongs(): List<Pair<Track, Int>> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("song_counts", "{}") ?: "{}")
        val pool = (_ui.value.recentTracks + _ui.value.likedTracks + _ui.value.downloadedTracks + _ui.value.localTracks)
            .associateBy { it.id }
        obj.keys().asSequence()
            .mapNotNull { id -> pool[id]?.let { it to obj.optInt(id, 0) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(12)
            .toList()
    }.getOrDefault(emptyList())

    private fun savePlaylists(list: List<UserPlaylist>) {
        val arr = org.json.JSONArray()
        list.forEach { pl ->
            val tArr = org.json.JSONArray()
            pl.tracks.forEach { t ->
                tArr.put(org.json.JSONObject().put("id", t.id).put("t", t.title).put("a", t.artist).put("img", t.artworkUrl))
            }
            arr.put(org.json.JSONObject().put("id", pl.id).put("name", pl.name).put("img", pl.artworkUri).put("tracks", tArr))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    private fun loadPlaylists(): List<UserPlaylist> = runCatching {
        val arr = org.json.JSONArray(prefs.getString("playlists", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val tArr = o.getJSONArray("tracks")
            val tracks = (0 until tArr.length()).map { j ->
                val to = tArr.getJSONObject(j)
                Track(to.getString("id"), to.getString("t"), to.getString("a"), to.getString("img"))
            }
            UserPlaylist(o.getString("id"), o.getString("name"), tracks, o.optString("img", ""))
        }
    }.getOrDefault(emptyList())

    fun seekTo(posMs: Long) {
        controller?.seekTo(posMs.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val next = !_ui.value.shuffleEnabled
        controller?.shuffleModeEnabled = next
        _ui.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeat() {
        val next = when (_ui.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _ui.update { it.copy(repeatMode = next) }
    }

    fun toggleLike(track: Track) {
        _ui.update { st ->
            val exists = st.likedTracks.any { it.id == track.id }
            val next = if (exists) st.likedTracks.filterNot { it.id == track.id } else listOf(track) + st.likedTracks
            st.copy(likedTracks = next)
        }
        saveTrackList("liked", _ui.value.likedTracks)
    }

    private fun saveTrackList(key: String, list: List<Track>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(org.json.JSONObject().put("id", it.id).put("t", it.title).put("a", it.artist).put("img", it.artworkUrl))
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun loadTrackList(key: String): List<Track> = runCatching {
        val arr = org.json.JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Track(o.getString("id"), o.getString("t"), o.getString("a"), o.getString("img"))
        }
    }.getOrDefault(emptyList())

    fun selectEqPreset(presetId: String) {
        val preset = BUILTIN_EQ_PRESETS.find { it.id == presetId } ?: return
        _ui.update { it.copy(activeEqId = preset.id, eqBands = preset.bandsDb) }
        SwaraPlaybackService.applyEqualizerBands(preset.bandsDb)
    }

    fun setEqBand(index: Int, gainDb: Float) {
        val next = _ui.value.eqBands.mapIndexed { i, v -> if (i == index) gainDb else v }
        _ui.update { it.copy(activeEqId = "custom", eqBands = next) }
        SwaraPlaybackService.applyEqualizerBands(next)
    }

    fun setDiscordRpcEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("discord_rpc_enabled", false).apply()
        _ui.update { it.copy(discordRpcEnabled = false) }
    }

    fun setDiscordToken(token: String) {}

    fun disconnectDiscord() {
        prefs.edit().remove("discord_token").remove("discord_username").putBoolean("discord_rpc_enabled", false).apply()
        _ui.update { it.copy(discordRpcEnabled = false, discordToken = "", discordUsername = "", discordStatus = "Off") }
    }

    fun setDiscordShowButtons(show: Boolean) {
        prefs.edit().putBoolean("discord_show_buttons", show).apply()
        _ui.update { it.copy(discordShowButtons = show) }
    }

    fun setDiscordTemplate(template: String) {
        prefs.edit().putString("discord_template", template).apply()
        _ui.update { it.copy(discordTemplate = template) }
    }

    fun dismissError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        controller?.release()
        super.onCleared()
    }
}

private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(SwaraPlaybackService.trackUri(id)) // resolved to a stream URL inside the service
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(artworkUrl))
            .build()
    )
    .build()

private fun MediaItem.toTrack(): Track = Track(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    artworkUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
)
