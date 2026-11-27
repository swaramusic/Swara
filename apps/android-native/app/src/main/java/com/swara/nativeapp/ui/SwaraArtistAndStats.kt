package com.swara.nativeapp.ui

import androidx.lifecycle.viewModelScope
import com.swara.nativeapp.model.Track
import java.time.LocalDate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun SwaraViewModel.openArtist(name: String, imageUrl: String = "") {
    val clean = name.substringBefore(",").substringBefore("&").trim().ifBlank { return }
    viewModelScope.launch {
        _ui.update {
            it.copy(
                openedArtistName = clean,
                openedArtistImage = imageUrl,
                openedArtistTracks = emptyList(),
                isLoadingArtist = true
            )
        }
        val tracks =
            runCatching {
                yt.searchTracks(clean, hl = _ui.value.language, gl = _ui.value.country)
            }.getOrDefault(emptyList())
        val resolvedImg = imageUrl.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
        _ui.update { st ->
            val imgs =
                if (resolvedImg.isNotBlank()) {
                    (st.artistImages + (clean to resolvedImg)).also { saveArtistImages(it) }
                } else {
                    st.artistImages
                }
            st.copy(
                openedArtistImage = resolvedImg,
                openedArtistTracks = tracks,
                artistImages = imgs,
                isLoadingArtist = false
            )
        }
    }
}

fun SwaraViewModel.openAlbum(track: Track) {
    val albumName = "${track.title} • ${track.artist}"
    val q = "${track.title} ${track.artist} album"
    viewModelScope.launch {
        _ui.update {
            it.copy(
                openedAlbumTitle = albumName,
                openedAlbumTracks = emptyList(),
                isLoadingAlbum = true
            )
        }
        val tracks =
            runCatching {
                yt.searchTracks(q, hl = _ui.value.language, gl = _ui.value.country)
            }.getOrDefault(emptyList())
        _ui.update {
            it.copy(
                openedAlbumTracks = tracks.ifEmpty { listOf(track) },
                isLoadingAlbum = false
            )
        }
    }
}

fun SwaraViewModel.closeAlbum() {
    _ui.update {
        it.copy(openedAlbumTitle = null, openedAlbumTracks = emptyList(), isLoadingAlbum = false)
    }
}

fun SwaraViewModel.excludeFromTaste(track: Track) {
    _ui.update { st ->
        val nextEx = st.excludedTrackIds + track.id
        val arr = JSONArray()
        nextEx.forEach { arr.put(it) }
        prefs.edit().putString("excluded_tracks", arr.toString()).apply()
        val filteredRecent = st.recentTracks.filterNot { it.id in nextEx }
        saveTrackList("recent", filteredRecent)
        st.copy(excludedTrackIds = nextEx, recentTracks = filteredRecent)
    }
    loadHome()
}

internal fun SwaraViewModel.loadExcludedTrackIds(): Set<String> = runCatching {
    val arr = JSONArray(prefs.getString("excluded_tracks", "[]"))
    (0 until arr.length()).mapNotNull { i ->
        arr.optString(
            i
        ).takeIf { it.isNotBlank() }
    }.toSet()
}.getOrDefault(emptySet())

fun SwaraViewModel.closeArtist() {
    _ui.update {
        it.copy(openedArtistName = null, openedArtistTracks = emptyList(), isLoadingArtist = false)
    }
}

fun SwaraViewModel.toggleFollowArtist(name: String) {
    val clean = name.trim().ifBlank { return }
    var needsFetch = false
    _ui.update { st ->
        val exists = st.followedArtists.any { it.equals(clean, ignoreCase = true) }
        val next =
            if (exists) {
                st.followedArtists.filterNot { it.equals(clean, ignoreCase = true) }
            } else {
                listOf(clean) + st.followedArtists
            }
        val arr = JSONArray()
        next.forEach { arr.put(it) }
        prefs.edit().putString("followed_artists", arr.toString()).apply()

        var nextImgs = st.artistImages
        if (!exists && nextImgs[clean].isNullOrBlank()) {
            val candidate =
                st.openedArtistImage.takeIf {
                    st.openedArtistName.equals(clean, ignoreCase = true) && it.isNotBlank()
                } ?: (st.recentTracks + st.likedTracks + st.homeTracks)
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

internal fun SwaraViewModel.resolveMissingArtistImages(names: List<String>) {
    if (names.isEmpty()) return
    viewModelScope.launch {
        val st = _ui.value
        val updated = st.artistImages.toMutableMap()
        var changed = false
        for (name in names) {
            if (!updated[name].isNullOrBlank()) continue
            val localHit =
                (st.recentTracks + st.likedTracks + st.homeTracks)
                    .firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }
                    ?.artworkUrl
            val img =
                localHit ?: runCatching {
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

internal fun SwaraViewModel.saveArtistImages(map: Map<String, String>) {
    val obj = JSONObject()
    map.forEach { (k, v) -> if (v.isNotBlank()) obj.put(k, v) }
    prefs.edit().putString("artist_images", obj.toString()).apply()
}

internal fun SwaraViewModel.loadArtistImages(): Map<String, String> = runCatching {
    val obj = JSONObject(prefs.getString("artist_images", "{}"))
    val out = mutableMapOf<String, String>()
    obj.keys().forEach { k ->
        val v = obj.optString(k)
        if (k.isNotBlank() && v.isNotBlank()) out[k] = v
    }
    out
}.getOrDefault(emptyMap())

internal fun SwaraViewModel.loadFollowedArtists(): List<String> = runCatching {
    val arr = JSONArray(prefs.getString("followed_artists", "[]"))
    (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
}.getOrDefault(emptyList())

fun SwaraViewModel.clearListenHistory() {
    saveTrackList("recent", emptyList())
    _ui.update { it.copy(recentTracks = emptyList()) }
}

internal fun SwaraViewModel.recordPlayStats(track: Track) {
    val today = LocalDate.now().toString()
    val lastDate = prefs.getString("last_play_date", "") ?: ""
    var streak = prefs.getInt("streak_days", 1)
    if (lastDate.isNotEmpty() && lastDate != today) {
        val yesterday = LocalDate.now().minusDays(1).toString()
        streak = if (lastDate == yesterday) streak + 1 else 1
    }
    val plays = prefs.getInt("total_plays", 0) + 1
    val secs = prefs.getInt("total_seconds", 0) + 180
    val artistJson = JSONObject(prefs.getString("artist_counts", "{}") ?: "{}")
    val cleanArtist = track.artist.trim().ifBlank { "Artis" }
    artistJson.put(cleanArtist, artistJson.optInt(cleanArtist, 0) + 1)
    val songJson = JSONObject(prefs.getString("song_counts", "{}") ?: "{}")
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
            topSongs = loadTopSongs()
        )
    }
}

internal fun SwaraViewModel.loadTopArtists(): List<Pair<String, Int>> = runCatching {
    val obj = JSONObject(prefs.getString("artist_counts", "{}") ?: "{}")
    obj.keys().asSequence().map {
        it to obj.optInt(it, 0)
    }.sortedByDescending { it.second }.take(5).toList()
}.getOrDefault(emptyList())

internal fun SwaraViewModel.loadTopSongs(): List<Pair<Track, Int>> = runCatching {
    val obj = JSONObject(prefs.getString("song_counts", "{}") ?: "{}")
    val pool =
        (_ui.value.recentTracks + _ui.value.likedTracks + _ui.value.downloadedTracks + _ui.value.localTracks)
            .associateBy { it.id }
    obj.keys().asSequence()
        .mapNotNull { id -> pool[id]?.let { it to obj.optInt(id, 0) } }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .take(12)
        .toList()
}.getOrDefault(emptyList())
