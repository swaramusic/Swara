package com.swara.nativeapp.ui

import androidx.lifecycle.viewModelScope
import com.swara.nativeapp.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Listen Together room host/guest SSE sync logic for SwaraViewModel. */
fun SwaraViewModel.createListenRoom() {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val rng = java.security.SecureRandom()
    val code = buildString(10) {
        repeat(10) { append(alphabet[rng.nextInt(alphabet.length)]) }
    }
    listenJob?.cancel()
    _ui.update { it.copy(listenRoomCode = code, listenIsHost = true, listenPeerCount = 0, listenStatus = "Host aktif • Kode: $code • 0 bergabung") }
    val mc = controller
    broadcastRoomState(_ui.value.currentTrack, mc?.currentPosition ?: 0L, mc?.isPlaying ?: false)
    listenJob = viewModelScope.launch(Dispatchers.IO) {
        val peers = java.util.concurrent.ConcurrentHashMap<String, Long>()
        while (currentCoroutineContext()[Job]?.isActive == true) {
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
            delay(1500L)
        }
    }
}

fun SwaraViewModel.joinListenRoom(rawCode: String) {
    val code = rawCode.trim().uppercase()
    if (!safeRoomRegex.matches(code)) return
    listenJob?.cancel()
    val peerId = "p_${System.currentTimeMillis()}_${(100..999).random()}"
    _ui.update { it.copy(listenRoomCode = code, listenIsHost = false, listenPeerCount = 0, listenStatus = "Menghubungkan ke ruang $code...") }
    listenJob = viewModelScope.launch(Dispatchers.IO) {
        val heartbeat = launch {
            sendPeerEvent(code, "join", peerId)
            while (true) {
                delay(20_000L)
                sendPeerEvent(code, "ping", peerId)
            }
        }
        try {
            while (currentCoroutineContext()[Job]?.isActive == true) {
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
                            viewModelScope.launch(Dispatchers.Main) {
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
                delay(1500L)
            }
        } finally {
            heartbeat.cancel()
            sendPeerEvent(code, "leave", peerId)
        }
    }
}

private fun SwaraViewModel.sendPeerEvent(code: String, ev: String, peerId: String) {
    val payload = org.json.JSONObject().put("ev", ev).put("peer", peerId).toString()
    val req = okhttp3.Request.Builder()
        .url("https://ntfy.sh/swara-room-$code")
        .post(okhttp3.RequestBody.create(null, payload))
        .build()
    runCatching { dlClient.newCall(req).execute().close() }
}

fun SwaraViewModel.leaveListenRoom() {
    listenJob?.cancel()
    _ui.update { it.copy(listenRoomCode = null, listenIsHost = false, listenPeerCount = 0, listenStatus = null) }
}

internal fun SwaraViewModel.broadcastRoomState(track: Track?, posMs: Long, playing: Boolean) {
    val code = _ui.value.listenRoomCode ?: return
    if (track == null) return
    viewModelScope.launch(Dispatchers.IO) {
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
