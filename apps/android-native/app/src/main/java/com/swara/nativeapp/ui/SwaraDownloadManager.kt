package com.swara.nativeapp.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.playback.SwaraPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Offline download, chunked transfer, and cache management for SwaraViewModel. */
internal fun SwaraViewModel.safeDownloadFile(trackId: String): java.io.File? {
    if (!safeIdRegex.matches(trackId)) return null
    val dir = java.io.File(getApplication<Application>().filesDir, "downloads").apply { mkdirs() }
    val target = java.io.File(dir, "$trackId.m4a")
    return if (target.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) target else null
}

fun SwaraViewModel.setDownloadTreeUri(uriStr: String) {
    prefs.edit().putString("download_tree_uri", uriStr).apply()
    _ui.update { it.copy(downloadTreeUri = uriStr) }
}

private fun SwaraViewModel.copyToUserFolder(track: Track, src: java.io.File, treeUriStr: String) {
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

private fun SwaraViewModel.notifyDownloadProgress(
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

private fun SwaraViewModel.fetchAudioToFile(url: String, dest: java.io.File) {
    if (!url.startsWith("https://", ignoreCase = true)) {
        throw IllegalArgumentException("URL unduhan tidak valid")
    }
    val vrUa = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    val webUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    val maxBytes = 100L * 1024L * 1024L
    val chunkSize = 512L * 1024L

    fun tryChunkedDownload(ua: String): Int {
        var offset = 0L
        var totalSize = -1L
        dest.outputStream().use { output ->
            while (true) {
                val endByte = if (totalSize > 0) minOf(offset + chunkSize - 1, totalSize - 1) else offset + chunkSize - 1
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", ua)
                    .header("Range", "bytes=$offset-$endByte")
                    .build()
                dlClient.newCall(req).execute().use { res ->
                    if (res.code == 403 && offset == 0L) return 403
                    if (!res.isSuccessful && res.code != 206) return res.code
                    val body = res.body ?: throw Exception("Respons kosong")
                    if (res.code == 200) {
                        if (body.contentLength() > maxBytes) throw Exception("Ukuran file terlalu besar")
                        body.byteStream().use { input ->
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
                        return 200
                    }
                    val cr = res.header("Content-Range").orEmpty()
                    val parsedTotal = cr.substringAfter('/', "").toLongOrNull()
                    if (parsedTotal != null && parsedTotal > 0L) {
                        if (parsedTotal > maxBytes) throw Exception("Ukuran file terlalu besar")
                        totalSize = parsedTotal
                    }
                    var chunkWritten = 0L
                    body.byteStream().use { input ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            chunkWritten += read
                            if (offset + chunkWritten > maxBytes) throw Exception("Ukuran file melebihi batas")
                            output.write(buf, 0, read)
                        }
                    }
                    offset += chunkWritten
                    if (chunkWritten == 0L || (totalSize > 0 && offset >= totalSize)) {
                        return 200
                    }
                }
            }
        }
    }

    val code1 = tryChunkedDownload(vrUa)
    if (code1 == 200) {
        if (dest.length() < 1024L) throw Exception("File unduhan kosong")
        return
    }
    if (code1 == 403) {
        val code2 = tryChunkedDownload(webUa)
        if (code2 == 200) {
            if (dest.length() < 1024L) throw Exception("File unduhan kosong")
            return
        }
        throw Exception("HTTP $code2")
    }
    throw Exception("HTTP $code1")
}

fun SwaraViewModel.dismissDownloadBanner() {
    _ui.update { it.copy(downloadStatusBanner = null) }
}

fun SwaraViewModel.downloadPlaylist(tracks: List<Track>, treeUriOverride: String? = null) {
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
    viewModelScope.launch(Dispatchers.IO) {
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
                        delay(350)
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

fun SwaraViewModel.downloadTrack(track: Track, treeUriOverride: String? = null) {
    if (track.browseId.isNotBlank() || track.id in _ui.value.downloadingIds) return
    if (_ui.value.downloadedTracks.any { it.id == track.id }) {
        _ui.update { it.copy(downloadStatusBanner = "${track.title} sudah tersimpan di Downloaded") }
        return
    }
    val dest = safeDownloadFile(track.id) ?: return
    val folderUri = treeUriOverride ?: _ui.value.downloadTreeUri
    viewModelScope.launch(Dispatchers.IO) {
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
                delay(350)
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

fun SwaraViewModel.deleteDownload(trackId: String) {
    safeDownloadFile(trackId)?.delete()
    val updated = _ui.value.downloadedTracks.filterNot { it.id == trackId }
    _ui.update { it.copy(downloadedTracks = updated) }
    saveTrackList("downloads", updated)
}

fun SwaraViewModel.clearAllDownloads() {
    _ui.value.downloadedTracks.forEach { safeDownloadFile(it.id)?.delete() }
    val dlDir = getApplication<Application>().filesDir.resolve("downloads")
    runCatching { dlDir.listFiles()?.forEach { it.delete() } }
    saveTrackList("downloads", emptyList())
    _ui.update { it.copy(downloadedTracks = emptyList()) }
}

fun SwaraViewModel.setSongCacheEnabled(enabled: Boolean) {
    prefs.edit().putBoolean("song_cache_enabled", enabled).apply()
    SwaraPlaybackService.songCacheEnabled = enabled
    if (!enabled) clearSongCache()
    _ui.update { it.copy(songCacheEnabled = enabled) }
}

fun SwaraViewModel.setMaxSongCacheMb(mb: Int) {
    val clamped = mb.coerceIn(128, 2048)
    prefs.edit().putInt("max_song_cache_mb", clamped).apply()
    SwaraPlaybackService.maxSongCacheMb = clamped
    SwaraPlaybackService.enforceSongCacheLimit(getApplication())
    _ui.update { it.copy(maxSongCacheMb = clamped) }
}

fun SwaraViewModel.clearSongCache() {
    val app = getApplication<Application>()
    runCatching { coil.Coil.imageLoader(app).memoryCache?.clear() }
    runCatching { app.cacheDir.resolve("image_cache").deleteRecursively() }
    runCatching { app.cacheDir.resolve("song_cache").deleteRecursively() }
}
