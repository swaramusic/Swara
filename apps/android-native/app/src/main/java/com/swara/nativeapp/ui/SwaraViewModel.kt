package com.swara.nativeapp.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.swara.nativeapp.data.YouTubeMusicClient
import com.swara.nativeapp.model.LyricsData
import com.swara.nativeapp.model.SUPPORTED_COUNTRIES
import com.swara.nativeapp.model.Shelf
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.UserPlaylist
import com.swara.nativeapp.model.stringsFor
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
    val errorMessage: String? = null
)

class SwaraViewModel(app: Application) : AndroidViewModel(app) {
    internal val yt = YouTubeMusicClient()
    internal val prefs =
        app.getSharedPreferences("swara_native_prefs", 0).also {
            if (it.contains("discord_token")) it.edit().remove("discord_token").apply()
        }
    internal val dlClient =
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    internal val sseClient =
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    internal val _ui =
        MutableStateFlow(
            SwaraUiState(
                likedTracks = loadTrackList("liked"),
                recentTracks = loadTrackList("recent"),
                downloadedTracks = loadTrackList("downloads"),
                localTracks =
                loadTrackList("local").also {
                    val uriObj =
                        runCatching {
                            org.json.JSONObject(
                                prefs.getString("local_uris", "{}")
                            )
                        }.getOrNull()
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
                audioQuality =
                (prefs.getString("audio_quality", "high") ?: "high").also {
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
                songCacheEnabled =
                prefs.getBoolean("song_cache_enabled", true).also {
                    SwaraPlaybackService.songCacheEnabled = it
                },
                maxSongCacheMb =
                prefs.getInt("max_song_cache_mb", 1126).also {
                    SwaraPlaybackService.maxSongCacheMb = it
                },
                romanizeLyrics = prefs.getBoolean("romanize_lyrics", false),
                downloadTreeUri = prefs.getString("download_tree_uri", "") ?: ""
            )
        )
    val ui: StateFlow<SwaraUiState> = _ui.asStateFlow()

    internal val _progress = MutableStateFlow(0L to 0L)
    val progress: StateFlow<Pair<Long, Long>> = _progress.asStateFlow()

    internal var controller: MediaController? = null
    private var lastLyricsTrackId: String? = null
    private var sleepJob: Job? = null
    internal var listenJob: Job? = null
    private var unshuffledQueue: List<Track> = emptyList()

    init {
        _ui.update { it.copy(topSongs = loadTopSongs(), searchHistory = loadSearchHistory()) }
        SwaraPlaybackService.dynamicIslandEnabled = _ui.value.dynamicIslandEnabled
        connectMediaController()
        loadHome()
        resolveMissingArtistImages(
            (
                _ui.value.followedArtists + _ui.value.topArtists.map {
                    it.first
                }
                ).distinct()
        )
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
                    mc.addListener(
                        object : Player.Listener {
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
                        }
                    )
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
                val countryName =
                    com.swara.nativeapp.model.ENGLISH_COUNTRY_NAMES[gl]
                        ?: SUPPORTED_COUNTRIES.find { it.first == gl }?.second ?: gl
                val mood = _ui.value.activeMood
                val shelves =
                    if (mood == "Semua") {
                        coroutineScope {
                            val baseDef = async { yt.fetchHomeShelves(hl = hl, gl = gl) }
                            val chartsDef =
                                async {
                                    runCatching {
                                        yt.fetchChartShelves(
                                            hl = hl,
                                            gl = gl,
                                            countryName = countryName
                                        )
                                    }.getOrDefault(emptyList())
                                }
                            val tasteSeed = _ui.value.recentTracks.firstOrNull() ?: _ui.value.likedTracks.firstOrNull()
                            val similarDef =
                                async {
                                    if (tasteSeed != null) {
                                        runCatching {
                                            yt.fetchRelatedTracks(
                                                tasteSeed.id,
                                                hl = hl,
                                                gl = gl
                                            )
                                        }.getOrDefault(emptyList())
                                    } else {
                                        emptyList()
                                    }
                                }
                            val topArtist = _ui.value.topArtists.firstOrNull()?.first
                            val artistDef =
                                async {
                                    if (!topArtist.isNullOrBlank()) {
                                        runCatching {
                                            yt.searchTracks(
                                                topArtist,
                                                hl = hl,
                                                gl = gl
                                            )
                                        }.getOrDefault(emptyList())
                                    } else {
                                        emptyList()
                                    }
                                }
                            val newRelQuery = "New music $countryName 2026"
                            val newRelDef =
                                async {
                                    runCatching {
                                        yt.searchTracks(
                                            newRelQuery,
                                            hl = hl,
                                            gl = gl
                                        ).take(10)
                                    }.getOrDefault(emptyList())
                                }
                            val albumQuery = if (!topArtist.isNullOrBlank()) "Full Album $topArtist" else "Best album $countryName"
                            val albumDef =
                                async {
                                    runCatching {
                                        yt.searchTracks(
                                            albumQuery,
                                            hl = hl,
                                            gl = gl
                                        ).take(10)
                                    }.getOrDefault(emptyList())
                                }
                            val countryMixDef =
                                async {
                                    runCatching {
                                        yt.searchTracks(
                                            "Popular music $countryName",
                                            hl = hl,
                                            gl = gl
                                        ).take(10)
                                    }.getOrDefault(emptyList())
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
                                out.add(
                                    Shelf(
                                        "${str.moreLike} ${tasteSeed.artist}",
                                        similar,
                                        isList = false
                                    )
                                )
                            }
                            if (!topArtist.isNullOrBlank() && artistTracks.size >= 2) {
                                out.add(
                                    Shelf(
                                        "${str.mixedForYou} • $topArtist",
                                        artistTracks.take(4),
                                        isList = true
                                    )
                                )
                            } else if (countryMix.isNotEmpty()) {
                                out.add(
                                    Shelf(
                                        "${str.mixedForYou} • $countryName",
                                        countryMix.take(4),
                                        isList = true
                                    )
                                )
                            }
                            if (albumTracks.size >= 2) {
                                out.add(Shelf(str.albumForYou, albumTracks, isList = false))
                            }
                            if (newReleases.isNotEmpty()) {
                                out.add(
                                    Shelf(
                                        "${str.newRelease} • $countryName",
                                        newReleases,
                                        isList = false
                                    )
                                )
                            }
                            if (gl == "ID") {
                                base.forEach { s ->
                                    val title =
                                        when {
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
                        val enMood =
                            when (mood) {
                                "Santai" -> "chill"
                                "Energi" -> "energy"
                                "Fokus" -> "focus"
                                "Romantis" -> "romantic"
                                "Galau" -> "sad"
                                else -> mood
                            }
                        val moodTracks = yt.searchTracks(
                            "trending $enMood music $countryName",
                            hl = hl,
                            gl = gl
                        ).filter { it.browseId.isBlank() }
                        if (moodTracks.isNotEmpty()) {
                            listOf(Shelf("Trending $enMood • $countryName", moodTracks, isList = true))
                        } else {
                            emptyList()
                        }
                    }
                val explore =
                    if (mood == "Semua") {
                        shelves.filter {
                            it.title.startsWith("Top") ||
                                it.title.startsWith("Trending") ||
                                it.tracks.any { t -> t.browseId.isNotBlank() }
                        }
                    } else {
                        emptyList()
                    }
                _ui.update {
                    it.copy(
                        homeShelves = shelves,
                        exploreShelves = explore.ifEmpty { it.exploreShelves },
                        homeTracks = shelves.flatMap { s -> s.tracks }.distinctBy { t -> t.id },
                        isLoadingHome = false
                    )
                }
                // Pre-warm resume & top 4 visible home tracks so Play feels instant
                _ui.value.recentTracks.firstOrNull()?.id?.let {
                    SwaraPlaybackService.prefetchTrack(
                        it
                    )
                }
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
        val nextMin =
            when {
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
        sleepJob =
            viewModelScope.launch {
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
        searchJob =
            viewModelScope.launch {
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
        val next = (
            listOf(q) + _ui.value.searchHistory.filterNot {
                it.equals(q, ignoreCase = true)
            }
            ).take(12)
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
        val mc =
            controller ?: run {
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
                val related =
                    runCatching {
                        yt.fetchRelatedTracks(
                            seedId,
                            gl = _ui.value.country
                        )
                    }.getOrDefault(emptyList())
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
        if (mc.isPlaying) {
            mc.pause()
        } else {
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
                durationMs = dur
            )
        }
        if (current != null && current.id != lastLyricsTrackId) {
            lastLyricsTrackId = current.id
            val updatedRecent = (
                listOf(current) + _ui.value.recentTracks.filterNot {
                    it.id == current.id
                }
                ).take(30)
            _ui.update {
                it.copy(
                    recentTracks = updatedRecent,
                    lyrics = null,
                    isLoadingLyrics = true
                )
            }
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

    fun identifyBySnippet(snippet: String) {
        if (snippet.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isIdentifying = true, identifyResults = emptyList()) }
            val hits =
                runCatching {
                    yt.identifyBySnippet(snippet, _ui.value.language, _ui.value.country)
                }.getOrDefault(emptyList())
            _ui.update { it.copy(identifyResults = hits, isIdentifying = false) }
        }
    }

    internal val safeIdRegex = Regex("^[A-Za-z0-9_-]{1,64}$")
    internal val safeRoomRegex = Regex("^[A-Z0-9]{4,32}$")

    fun setRomanizeLyrics(enabled: Boolean) {
        prefs.edit().putBoolean("romanize_lyrics", enabled).apply()
        _ui.update { it.copy(romanizeLyrics = enabled) }
    }

    fun seekTo(posMs: Long) {
        controller?.seekTo(posMs.coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val next = !_ui.value.shuffleEnabled
        val mc = controller
        val curQueue = _ui.value.queue
        val curIdx = _ui.value.currentIndex.coerceIn(0, (curQueue.size - 1).coerceAtLeast(0))
        val curTrack = curQueue.getOrNull(curIdx)

        if (curQueue.size > 1 && curTrack != null && mc != null) {
            if (next) {
                unshuffledQueue = curQueue
                val newQueue = buildShuffledQueue(curQueue, curIdx)
                val upcoming = newQueue.drop(1)
                mc.shuffleModeEnabled = false
                if (curIdx > 0) mc.removeMediaItems(0, curIdx)
                if (mc.mediaItemCount > 1) mc.removeMediaItems(1, mc.mediaItemCount)
                if (upcoming.isNotEmpty()) mc.addMediaItems(upcoming.map { it.toMediaItem() })
                _ui.update { it.copy(shuffleEnabled = true, queue = newQueue, currentIndex = 0) }
                return
            } else if (unshuffledQueue.isNotEmpty()) {
                val restored = unshuffledQueue
                val restoredIdx = restored.indexOfFirst { it.id == curTrack.id }.coerceAtLeast(0)
                val before = restored.take(restoredIdx)
                val after = restored.drop(restoredIdx + 1)
                mc.shuffleModeEnabled = false
                if (mc.mediaItemCount > 1) mc.removeMediaItems(1, mc.mediaItemCount)
                if (after.isNotEmpty()) mc.addMediaItems(after.map { it.toMediaItem() })
                if (before.isNotEmpty()) mc.addMediaItems(0, before.map { it.toMediaItem() })
                unshuffledQueue = emptyList()
                _ui.update {
                    it.copy(
                        shuffleEnabled = false,
                        queue = restored,
                        currentIndex = restoredIdx
                    )
                }
                return
            }
        }
        mc?.shuffleModeEnabled = next
        _ui.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeat() {
        val next =
            when (_ui.value.repeatMode) {
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
            val next = if (exists) {
                st.likedTracks.filterNot {
                    it.id == track.id
                }
            } else {
                listOf(track) + st.likedTracks
            }
            st.copy(likedTracks = next)
        }
        saveTrackList("liked", _ui.value.likedTracks)
    }

    internal fun saveTrackList(key: String, list: List<Track>) {
        val arr = org.json.JSONArray()
        list.forEach {
            arr.put(
                org.json.JSONObject().put(
                    "id",
                    it.id
                ).put("t", it.title).put("a", it.artist).put("img", it.artworkUrl)
            )
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

    fun setDiscordRpcEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("discord_rpc_enabled", false).apply()
        _ui.update { it.copy(discordRpcEnabled = false) }
    }

    fun setDiscordToken(token: String) {}

    fun disconnectDiscord() {
        prefs.edit().remove(
            "discord_token"
        ).remove("discord_username").putBoolean("discord_rpc_enabled", false).apply()
        _ui.update {
            it.copy(
                discordRpcEnabled = false,
                discordToken = "",
                discordUsername = "",
                discordStatus = "Off"
            )
        }
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
    artworkUrl = mediaMetadata.artworkUri?.toString().orEmpty()
)

private val allowedArtworkHosts =
    setOf(
        "i.ytimg.com",
        "lh3.googleusercontent.com",
        "yt3.googleusercontent.com",
        "music.youtube.com"
    )

internal fun sanitizeRemoteArtworkUrl(raw: String): String {
    val parsed = runCatching { java.net.URI(raw) }.getOrNull() ?: return ""
    val host = parsed.host?.lowercase() ?: return ""
    return if (parsed.scheme == "https" && host in allowedArtworkHosts) raw else ""
}

internal fun buildShuffledQueue(queue: List<Track>, currentIndex: Int): List<Track> {
    if (queue.size <= 1) return queue
    val idx = currentIndex.coerceIn(0, queue.lastIndex)
    val current = queue[idx]
    val upcoming = queue.filterIndexed { i, _ -> i != idx }.shuffled()
    return listOf(current) + upcoming
}
