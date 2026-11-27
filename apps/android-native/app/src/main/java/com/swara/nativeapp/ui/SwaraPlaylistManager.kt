package com.swara.nativeapp.ui

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.UserPlaylist
import com.swara.nativeapp.playback.SwaraPlaybackService
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

fun SwaraViewModel.importPlaylist(url: String, onSuccess: () -> Unit = {}) {
    if (url.isBlank() || _ui.value.isImportingPlaylist) return
    viewModelScope.launch {
        _ui.update { it.copy(isImportingPlaylist = true, errorMessage = null) }
        val res = yt.importPlaylistFromUrl(url, gl = _ui.value.country)
        res.onSuccess { imported ->
            val next = _ui.value.playlists + UserPlaylist(
                id = "pl_${System.currentTimeMillis()}",
                name = imported.title,
                tracks = imported.tracks,
                artworkUri = imported.artworkUrl,
            )
            _ui.update { it.copy(playlists = next, isImportingPlaylist = false) }
            savePlaylists(next)
            onSuccess()
        }.onFailure { err ->
            _ui.update {
                it.copy(
                    isImportingPlaylist = false,
                    errorMessage = err.message ?: "Gagal impor playlist"
                )
            }
        }
    }
}

fun SwaraViewModel.openBrowseItem(item: Track) {
    if (item.browseId.isBlank()) return
    if (item.browseId.startsWith("UC")) {
        openArtist(item.title, item.artworkUrl)
        return
    }
    viewModelScope.launch {
        _ui.update {
            it.copy(
                openedPlaylistTitle = item.title,
                openedPlaylistTracks = emptyList(),
                isLoadingPlaylist = true
            )
        }
        val tracks =
            runCatching {
                yt.fetchPlaylistTracks(
                    item.browseId,
                    hl = _ui.value.language,
                    gl = _ui.value.country
                )
            }.getOrDefault(emptyList())
        _ui.update { it.copy(openedPlaylistTracks = tracks, isLoadingPlaylist = false) }
    }
}

fun SwaraViewModel.removeTrackFromPlaylist(playlistId: String, trackId: String) {
    val next =
        _ui.value.playlists.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(
                    tracks = pl.tracks.filterNot { it.id == trackId }
                )
            } else {
                pl
            }
        }
    _ui.update { it.copy(playlists = next) }
    savePlaylists(next)
}

fun SwaraViewModel.importDeviceAudio(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        val added = mutableListOf<Track>()
        for (uri in uris) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val mmr = MediaMetadataRetriever()
            var title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Local Audio"
            var artist = "Local File"
            var dur = 0L
            runCatching {
                mmr.setDataSource(ctx, uri)
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { title = it }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { artist = it }
                dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
            runCatching { mmr.release() }
            val id = "loc_${uri.toString().hashCode().toUInt()}"
            val uriStr = uri.toString()
            SwaraPlaybackService.registerLocalTrack(id, uriStr)
            val uriObj = runCatching {
                JSONObject(prefs.getString("local_uris", "{}"))
            }.getOrDefault(JSONObject())
            uriObj.put(id, uriStr)
            prefs.edit().putString("local_uris", uriObj.toString()).apply()
            added.add(
                Track(id = id, title = title, artist = artist, artworkUrl = "", durationMs = dur)
            )
        }
        if (added.isNotEmpty()) {
            val merged = (added + _ui.value.localTracks).distinctBy { it.id }
            saveTrackList("local", merged)
            _ui.update { it.copy(localTracks = merged) }
        }
    }
}

fun SwaraViewModel.closeOpenedPlaylist() {
    _ui.update {
        it.copy(
            openedPlaylistTitle = null,
            openedPlaylistTracks = emptyList(),
            isLoadingPlaylist = false
        )
    }
}

fun SwaraViewModel.createPlaylist(name: String) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    val next = _ui.value.playlists + UserPlaylist("pl_${System.currentTimeMillis()}", clean, emptyList())
    _ui.update { it.copy(playlists = next) }
    savePlaylists(next)
}

fun SwaraViewModel.addTrackToPlaylist(playlistId: String, track: Track) {
    val next =
        _ui.value.playlists.map { pl ->
            if (pl.id == playlistId && pl.tracks.none { it.id == track.id }) {
                pl.copy(tracks = pl.tracks + track)
            } else {
                pl
            }
        }
    _ui.update { it.copy(playlists = next) }
    savePlaylists(next)
}

fun SwaraViewModel.deletePlaylist(playlistId: String) {
    val next = _ui.value.playlists.filterNot { it.id == playlistId }
    _ui.update { it.copy(playlists = next) }
    savePlaylists(next)
}

fun SwaraViewModel.updatePlaylist(
    playlistId: String,
    newName: String,
    pickedImageUri: Uri? = null
) {
    val cleanName = newName.trim()
    if (cleanName.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
        var savedCoverPath: String? = null
        if (pickedImageUri != null) {
            runCatching {
                val dir = getApplication<Application>().filesDir.resolve(
                    "playlist_covers"
                ).apply { mkdirs() }
                val out = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
                getApplication<Application>().contentResolver.openInputStream(
                    pickedImageUri
                )?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                if (out.exists() && out.length() > 0L) savedCoverPath = out.absolutePath
            }
        }
        val next =
            _ui.value.playlists.map { pl ->
                if (pl.id == playlistId) {
                    pl.copy(name = cleanName, artworkUri = savedCoverPath ?: pl.artworkUri)
                } else {
                    pl
                }
            }
        _ui.update { it.copy(playlists = next) }
        savePlaylists(next)
    }
}

internal fun SwaraViewModel.savePlaylists(list: List<UserPlaylist>) {
    val arr = JSONArray()
    list.forEach { pl ->
        val tArr = JSONArray()
        pl.tracks.forEach { t ->
            tArr.put(
                JSONObject()
                    .put("id", t.id)
                    .put("t", t.title)
                    .put("a", t.artist)
                    .put("img", t.artworkUrl)
            )
        }
        arr.put(
            JSONObject()
                .put("id", pl.id)
                .put("name", pl.name)
                .put("img", pl.artworkUri)
                .put("tracks", tArr)
        )
    }
    prefs.edit().putString("playlists", arr.toString()).apply()
}

internal fun SwaraViewModel.loadPlaylists(): List<UserPlaylist> = runCatching {
    val arr = JSONArray(prefs.getString("playlists", "[]"))
    (0 until arr.length()).mapNotNull { i ->
        runCatching {
            val o = arr.getJSONObject(i)
            val tArr = o.optJSONArray("tracks") ?: JSONArray()
            val tracks =
                (0 until tArr.length()).mapNotNull { j ->
                    runCatching {
                        val to = tArr.getJSONObject(j)
                        val id = to.optString("id", "")
                        if (id.isBlank()) null
                        else Track(
                            id,
                            to.optString("t", ""),
                            to.optString("a", ""),
                            to.optString("img", "")
                        )
                    }.getOrNull()
                }
            val id = o.optString("id", "")
            val name = o.optString("name", "")
            if (id.isBlank() || name.isBlank()) null
            else {
                val art = o.optString("img", "").ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
                UserPlaylist(id, name, tracks, art)
            }
        }.getOrNull()
    }
}.getOrDefault(emptyList())
