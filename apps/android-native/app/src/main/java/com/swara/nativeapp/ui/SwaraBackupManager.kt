package com.swara.nativeapp.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal object SwaraBackupCodec {
    fun buildBackupJson(
        playlistsRaw: String,
        likedRaw: String,
        recentRaw: String,
        followedRaw: String,
        excludedRaw: String,
        streakDays: Int,
        totalPlays: Int,
        totalSeconds: Int,
        artistCountsRaw: String,
        songCountsRaw: String,
    ): String = JSONObject().apply {
        put("format", "swara_backup_v1")
        put("exportedAt", System.currentTimeMillis())
        put("playlists", JSONArray(playlistsRaw))
        put("liked", JSONArray(likedRaw))
        put("recent", JSONArray(recentRaw))
        put("followed_artists", JSONArray(followedRaw))
        put("excluded_tracks", JSONArray(excludedRaw))
        put("streak_days", streakDays)
        put("total_plays", totalPlays)
        put("total_seconds", totalSeconds)
        put("artist_counts", JSONObject(artistCountsRaw))
        put("song_counts", JSONObject(songCountsRaw))
    }.toString(2)
}

fun SwaraViewModel.exportBackupToUri(uri: Uri, onResult: (String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        val msg = runCatching {
            val st = _ui.value
            val playlistsArr = com.swara.nativeapp.data.SwaraDatabaseHelper.encodeTracks(emptyList())
            val plJson = JSONArray().apply {
                st.playlists.forEach { pl ->
                    put(
                        JSONObject()
                            .put("id", pl.id)
                            .put("name", pl.name)
                            .put("img", pl.artworkUri)
                            .put("tracks", com.swara.nativeapp.data.SwaraDatabaseHelper.encodeTracks(pl.tracks)),
                    )
                }
            }.toString()
            val likedJson = com.swara.nativeapp.data.SwaraDatabaseHelper.encodeTracks(st.likedTracks).toString()
            val recentJson = com.swara.nativeapp.data.SwaraDatabaseHelper.encodeTracks(st.recentTracks).toString()
            val payload = SwaraBackupCodec.buildBackupJson(
                playlistsRaw = plJson,
                likedRaw = likedJson,
                recentRaw = recentJson,
                followedRaw = prefs.getString("followed_artists", "[]") ?: "[]",
                excludedRaw = prefs.getString("excluded_tracks", "[]") ?: "[]",
                streakDays = prefs.getInt("streak_days", 1),
                totalPlays = prefs.getInt("total_plays", 0),
                totalSeconds = prefs.getInt("total_seconds", 0),
                artistCountsRaw = prefs.getString("artist_counts", "{}") ?: "{}",
                songCountsRaw = prefs.getString("song_counts", "{}") ?: "{}",
            )
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Tidak dapat menulis file")
            "Backup berhasil disimpan (${st.playlists.size} playlist, ${st.likedTracks.size} liked)"
        }.getOrElse { "Gagal ekspor backup: ${it.message ?: "Error"}" }
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
    }
}

fun SwaraViewModel.importBackupFromUri(uri: Uri, onResult: (String) -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        val msg = runCatching {
            val raw = getApplication<Application>().contentResolver.openInputStream(uri)?.use { inp ->
                inp.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Tidak dapat membaca file")
            val json = JSONObject(raw)
            if (json.optString("format") != "swara_backup_v1") error("Format file backup tidak valid")

            val playlists = com.swara.nativeapp.data.SwaraDatabaseHelper.decodeLegacyPlaylists(
                json.optJSONArray("playlists")?.toString() ?: "[]",
            )
            val liked = com.swara.nativeapp.data.SwaraDatabaseHelper.decodeTracks(
                json.optJSONArray("liked")?.toString() ?: "[]",
            )
            val recent = com.swara.nativeapp.data.SwaraDatabaseHelper.decodeTracks(
                json.optJSONArray("recent")?.toString() ?: "[]",
            )

            savePlaylists(playlists)
            saveTrackList("liked", liked)
            saveTrackList("recent", recent)

            prefs.edit()
                .putString("followed_artists", json.optJSONArray("followed_artists")?.toString() ?: "[]")
                .putString("excluded_tracks", json.optJSONArray("excluded_tracks")?.toString() ?: "[]")
                .putInt("streak_days", json.optInt("streak_days", 1))
                .putInt("total_plays", json.optInt("total_plays", 0))
                .putInt("total_seconds", json.optInt("total_seconds", 0))
                .putString("artist_counts", json.optJSONObject("artist_counts")?.toString() ?: "{}")
                .putString("song_counts", json.optJSONObject("song_counts")?.toString() ?: "{}")
                .apply()

            _ui.update {
                it.copy(
                    playlists = playlists,
                    likedTracks = liked,
                    recentTracks = recent,
                    followedArtists = loadFollowedArtists(),
                    excludedTrackIds = loadExcludedTrackIds(),
                    streakDays = prefs.getInt("streak_days", 1),
                    totalPlays = prefs.getInt("total_plays", 0),
                    totalMinutes = prefs.getInt("total_seconds", 0) / 60,
                    topArtists = loadTopArtists(),
                    topSongs = loadTopSongs(),
                )
            }
            "Restore berhasil (${playlists.size} playlist, ${liked.size} liked)"
        }.getOrElse { "Gagal restore backup: ${it.message ?: "Error"}" }
        kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(msg) }
    }
}
