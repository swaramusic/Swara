package com.swara.nativeapp.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AuxEffectInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.swara.nativeapp.MainActivity
import com.swara.nativeapp.data.YouTubeMusicClient
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class SwaraPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val yt = YouTubeMusicClient()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val imgExecutor = Executors.newSingleThreadExecutor()
    private val artCache = ConcurrentHashMap<String, Bitmap>()

    private var islandRoot: LinearLayout? = null
    private var isIslandExpanded = false
    private var lastBuiltExpanded: Boolean? = null

    private var artView: ImageView? = null
    private var titleView: TextView? = null
    private var artistView: TextView? = null
    private var elapsedView: TextView? = null
    private var remainView: TextView? = null
    private var squigglyProgressView: SquigglyIslandProgressView? = null
    private var playPauseIcon: ImageView? = null
    private val waveBars = mutableListOf<View>()
    private var waveStep = 0

    // shortcut: stream URLs expire after ~6h, so a song left paused longer than that may need a skip; add TTL if reported.
    private val streamCache = ConcurrentHashMap<String, String>()

    private val ticker = object : Runnable {
        override fun run() {
            if (islandRoot != null) {
                if (isIslandExpanded) updateExpandedProgress()
                if (player?.isPlaying == true && waveBars.isNotEmpty()) {
                    animateWaveBars()
                }
                mainHandler.postDelayed(this, 180L)
            }
        }
    }

    companion object {
        private const val SCHEME = "swara"

        fun trackUri(videoId: String): Uri = Uri.parse("$SCHEME://$videoId")

        @Volatile
        private var activeService: SwaraPlaybackService? = null

        @Volatile
        private var equalizer: Equalizer? = null

        @Volatile
        private var bassBoost: BassBoost? = null

        @Volatile
        private var presetReverb: PresetReverb? = null

        @Volatile
        private var virtualizer: Virtualizer? = null

        @Volatile
        private var pendingBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f)

        @Volatile
        private var pendingReverbPct: Int = 0

        @Volatile
        private var pending8D: Boolean = false

        @Volatile
        var audioQuality: String = "high"

        @Volatile
        var accentColorInt: Int = 0xFF2563EB.toInt()

        private val localTrackUris = ConcurrentHashMap<String, String>()

        fun registerLocalTrack(id: String, uri: String) {
            localTrackUris[id] = uri
        }

        fun setAccentColor(colorInt: Int) {
            accentColorInt = colorInt
            activeService?.mainHandler?.post {
                activeService?.waveBars?.forEach { v ->
                    (v.background as? GradientDrawable)?.setColor(colorInt)
                }
            }
        }

        @Volatile
        var appInForeground: Boolean = true
            private set

        @Volatile
        var dynamicIslandEnabled: Boolean = true

        @Volatile
        var songCacheEnabled: Boolean = true

        @Volatile
        var maxSongCacheMb: Int = 1126

        fun enforceSongCacheLimit(ctx: android.content.Context) {
            val maxBytes = maxSongCacheMb.toLong().coerceAtLeast(128L) * 1024L * 1024L
            val songDir = java.io.File(ctx.cacheDir, "song_cache")
            val imgDir = java.io.File(ctx.cacheDir, "image_cache")
            val files = ((songDir.listFiles()?.toList().orEmpty()) + (imgDir.listFiles()?.toList().orEmpty()))
                .filter { it.isFile }
                .sortedBy { it.lastModified() }
                .toMutableList()
            var total = files.sumOf { it.length() }
            while (total > maxBytes && files.isNotEmpty()) {
                val oldest = files.removeAt(0)
                val len = oldest.length()
                if (oldest.delete()) total -= len
            }
        }

        fun setAppInForeground(inForeground: Boolean) {
            appInForeground = inForeground
            activeService?.mainHandler?.post {
                if (inForeground) activeService?.isIslandExpanded = false
                activeService?.refreshDynamicIsland()
            }
        }

        fun refreshIslandFromUi() {
            activeService?.mainHandler?.post {
                activeService?.refreshDynamicIsland()
            }
        }

        fun clearStreamCache() {
            activeService?.streamCache?.clear()
        }

        @OptIn(UnstableApi::class)
        fun setSpatial8D(enabled: Boolean) {
            pending8D = enabled
            val svc = activeService
            val exo = svc?.player
            if (!enabled) {
                runCatching { virtualizer?.enabled = false }
                runCatching { virtualizer?.release() }
                virtualizer = null
                return
            }
            if (virtualizer == null && exo != null) {
                val sid = exo.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET && sid != 0) {
                    virtualizer = runCatching { Virtualizer(0, sid) }.getOrNull()
                }
            }
            val v = virtualizer ?: return
            try {
                if (v.strengthSupported) {
                    v.setStrength(1000)
                }
                v.enabled = true
            } catch (_: Exception) {
            }
        }

        fun prefetchTrack(videoId: String) {
            if (videoId.isBlank() || videoId.startsWith("browse:")) return
            val svc = activeService ?: return
            if (svc.streamCache.containsKey(videoId)) return
            svc.imgExecutor.execute {
                runBlocking { svc.yt.resolveAudioUrl(videoId, quality = audioQuality) }.onSuccess { url ->
                    svc.streamCache[videoId] = url
                }
            }
        }

        fun isEqualizerAttached(): Boolean = equalizer != null

        @OptIn(UnstableApi::class)
        fun applyRoomReverb(percent: Int) {
            pendingReverbPct = percent.coerceIn(0, 100)
            val svc = activeService
            val exo = svc?.player
            if (pendingReverbPct <= 2) {
                runCatching { presetReverb?.enabled = false }
                runCatching { presetReverb?.preset = PresetReverb.PRESET_NONE }
                runCatching { presetReverb?.release() }
                presetReverb = null
                runCatching { exo?.setAuxEffectInfo(AuxEffectInfo(0, 0f)) }
                return
            }
            if (presetReverb == null && exo != null) {
                val sid = exo.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET && sid != 0) {
                    presetReverb = runCatching { PresetReverb(0, 0) }.getOrNull()
                }
            }
            val rev = presetReverb ?: return
            try {
                rev.preset = when {
                    pendingReverbPct < 35 -> PresetReverb.PRESET_SMALLROOM
                    pendingReverbPct < 60 -> PresetReverb.PRESET_MEDIUMROOM
                    pendingReverbPct < 80 -> PresetReverb.PRESET_LARGEHALL
                    else -> PresetReverb.PRESET_PLATE
                }
                rev.enabled = true
                exo?.setAuxEffectInfo(AuxEffectInfo(rev.id, (pendingReverbPct / 100f).coerceIn(0.15f, 1.0f)))
            } catch (_: Exception) {
            }
        }

        @OptIn(UnstableApi::class)
        fun applyEqualizerBands(bandsDb: List<Float>) {
            pendingBandsDb = bandsDb
            val isFlat = bandsDb.all { kotlin.math.abs(it) < 0.25f }
            if (isFlat) {
                runCatching { equalizer?.enabled = false }
                runCatching { equalizer?.release() }
                equalizer = null
                runCatching { bassBoost?.enabled = false }
                runCatching { bassBoost?.release() }
                bassBoost = null
                return
            }
            val svc = activeService
            val exo = svc?.player
            if (equalizer == null && exo != null) {
                val sid = exo.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET && sid != 0) {
                    equalizer = runCatching { Equalizer(0, sid) }.getOrNull()
                    bassBoost = runCatching { BassBoost(0, sid) }.getOrNull()
                }
            }
            val eq = equalizer ?: return
            try {
                eq.enabled = true
                val numBands = eq.numberOfBands.toInt()
                val range = eq.bandLevelRange
                val minMb = range[0].toInt()
                val maxMb = range[1].toInt()
                for (i in 0 until numBands) {
                    val srcIdx = ((i.toFloat() * (bandsDb.size - 1)) / maxOf(1, numBands - 1))
                        .roundToInt()
                        .coerceIn(0, bandsDb.lastIndex)
                    val mb = (bandsDb[srcIdx] * 100f).roundToInt().coerceIn(minMb, maxMb)
                    eq.setBandLevel(i.toShort(), mb.toShort())
                }
                bassBoost?.let { bb ->
                    val bassGain = bandsDb.firstOrNull() ?: 0f
                    bb.enabled = bassGain > 0.5f
                    if (bb.strengthSupported && bassGain > 0.5f) {
                        bb.setStrength(((bassGain / 12f) * 800f).roundToInt().coerceIn(0, 1000).toShort())
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        activeService = this
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip")
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(12000)

        val baseFactory = DefaultDataSource.Factory(this, httpFactory)

        val safeIdRegex = Regex("^[A-Za-z0-9_-]{1,64}$")
        val dataSourceFactory = ResolvingDataSource.Factory(baseFactory) { spec ->
            if (spec.uri.scheme != SCHEME) return@Factory spec
            val videoId = spec.uri.host.orEmpty()
            localTrackUris[videoId]?.let { localUri ->
                return@Factory spec.withUri(Uri.parse(localUri))
            }
            if (!safeIdRegex.matches(videoId)) {
                throw IOException("ID trek tidak valid")
            }
            val downloadsDir = java.io.File(filesDir, "downloads")
            val offlineFile = java.io.File(downloadsDir, "$videoId.m4a")
            if (offlineFile.canonicalPath.startsWith(downloadsDir.canonicalPath + java.io.File.separator) &&
                offlineFile.exists() && offlineFile.length() > 1024L
            ) {
                return@Factory spec.withUri(Uri.fromFile(offlineFile))
            }
            val songCacheDir = java.io.File(cacheDir, "song_cache")
            val cachedFile = java.io.File(songCacheDir, "$videoId.m4a")
            if (songCacheEnabled &&
                cachedFile.canonicalPath.startsWith(songCacheDir.canonicalPath + java.io.File.separator) &&
                cachedFile.exists() && cachedFile.length() > 1024L
            ) {
                cachedFile.setLastModified(System.currentTimeMillis())
                return@Factory spec.withUri(Uri.fromFile(cachedFile))
            }
            val url = streamCache[videoId] ?: runBlocking { yt.resolveAudioUrl(videoId, quality = audioQuality) }
                .getOrElse { throw IOException(it.message ?: "Audio tidak tersedia", it) }
                .also { streamCache[videoId] = it }
            if (songCacheEnabled && !cachedFile.exists()) {
                imgExecutor.execute {
                    runCatching {
                        songCacheDir.mkdirs()
                        val tmp = java.io.File(songCacheDir, "$videoId.m4a.tmp")
                        val req = okhttp3.Request.Builder().url(url).build()
                        artHttpClient.newCall(req).execute().use { res ->
                            if (res.isSuccessful) {
                                res.body?.byteStream()?.use { inp ->
                                    tmp.outputStream().use { out -> inp.copyTo(out) }
                                }
                                if (tmp.length() > 1024L) tmp.renameTo(cachedFile) else tmp.delete()
                            }
                        }
                        enforceSongCacheLimit(applicationContext)
                    }
                }
            }
            spec.withUri(Uri.parse(url))
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 25_000, 450, 1_200)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                initEqualizer(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && equalizer == null) {
                    initEqualizer(exo.audioSessionId)
                }
                refreshDynamicIsland()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshDynamicIsland()
                val nextIdx = exo.currentMediaItemIndex + 1
                if (nextIdx in 0 until exo.mediaItemCount) {
                    prefetchTrack(exo.getMediaItemAt(nextIdx).mediaId)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                exo.currentMediaItem?.mediaId?.let { streamCache.remove(it) }
                if (exo.hasNextMediaItem()) {
                    exo.seekToNextMediaItem()
                    exo.prepare()
                    exo.play()
                }
            }
        })

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val allowedControllerPackages = setOf(
            packageName,
            "com.android.systemui",
            "com.google.android.projection.gearhead",
            "com.google.android.wearable.app",
            "com.android.bluetooth",
            "android"
        )
        player = exo
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    if (controller.packageName !in allowedControllerPackages &&
                        controller.uid != android.os.Process.SYSTEM_UID &&
                        controller.uid != android.os.Process.myUid()
                    ) {
                        return MediaSession.ConnectionResult.reject()
                    }
                    return super.onConnect(session, controller)
                }
            })
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun initEqualizer(sessionId: Int) {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET || sessionId == 0) return
        try {
            equalizer?.release()
            bassBoost?.release()
            presetReverb?.release()
            virtualizer?.release()
            equalizer = null
            bassBoost = null
            presetReverb = null
            virtualizer = null
            // Keep pure untouched stereo AudioTrack unless an effect is actively requested
            if (pendingBandsDb.any { kotlin.math.abs(it) >= 0.25f }) {
                applyEqualizerBands(pendingBandsDb)
            }
            if (pendingReverbPct > 2) {
                applyRoomReverb(pendingReverbPct)
            }
            if (pending8D) {
                setSpatial8D(true)
            }
        } catch (_: Exception) {
            equalizer = null
            bassBoost = null
            presetReverb = null
            virtualizer = null
        }
    }

    private fun makeWaveform(dp: (Int) -> Int): LinearLayout {
        waveBars.clear()
        val heights = listOf(8, 16, 11, 14, 7)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            heights.forEachIndexed { idx, h ->
                val bar = View(this@SwaraPlaybackService).apply {
                    background = GradientDrawable().apply {
                        setColor(accentColorInt)
                        cornerRadius = dp(2).toFloat()
                    }
                }
                waveBars.add(bar)
                addView(bar, LinearLayout.LayoutParams(dp(3), dp(h)).apply {
                    if (idx < heights.lastIndex) marginEnd = dp(3)
                })
            }
        }
    }

    private fun animateWaveBars() {
        val density = resources.displayMetrics.density
        val frames = arrayOf(
            intArrayOf(7, 15, 10, 17, 8),
            intArrayOf(13, 8, 17, 9, 14),
            intArrayOf(10, 17, 8, 14, 6),
            intArrayOf(16, 10, 14, 7, 12),
        )
        val cur = frames[waveStep % frames.size]
        waveStep++
        waveBars.forEachIndexed { idx, v ->
            val h = (cur[idx % cur.size] * density).roundToInt()
            val lp = v.layoutParams
            if (lp.height != h) {
                lp.height = h
                v.layoutParams = lp
            }
        }
    }

    private fun drawMediaIcon(kind: String, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val s = sizePx.toFloat()
        when (kind) {
            "pause" -> {
                val w = s * 0.16f
                val r = s * 0.06f
                c.drawRoundRect(RectF(s * 0.27f, s * 0.2f, s * 0.27f + w, s * 0.8f), r, r, p)
                c.drawRoundRect(RectF(s * 0.57f, s * 0.2f, s * 0.57f + w, s * 0.8f), r, r, p)
            }
            "play" -> {
                val path = Path().apply {
                    moveTo(s * 0.30f, s * 0.20f)
                    lineTo(s * 0.80f, s * 0.50f)
                    lineTo(s * 0.30f, s * 0.80f)
                    close()
                }
                c.drawPath(path, p)
            }
            "prev" -> {
                c.drawRoundRect(RectF(s * 0.20f, s * 0.24f, s * 0.29f, s * 0.76f), s * 0.03f, s * 0.03f, p)
                val path = Path().apply {
                    moveTo(s * 0.78f, s * 0.24f)
                    lineTo(s * 0.34f, s * 0.50f)
                    lineTo(s * 0.78f, s * 0.76f)
                    close()
                }
                c.drawPath(path, p)
            }
            "next" -> {
                c.drawRoundRect(RectF(s * 0.71f, s * 0.24f, s * 0.80f, s * 0.76f), s * 0.03f, s * 0.03f, p)
                val path = Path().apply {
                    moveTo(s * 0.22f, s * 0.24f)
                    lineTo(s * 0.66f, s * 0.50f)
                    lineTo(s * 0.22f, s * 0.76f)
                    close()
                }
                c.drawPath(path, p)
            }
        }
        return bmp
    }

    private fun refreshDynamicIsland() {
        val exo = player
        // Keep visible even when paused so tapping Pause doesn't make the Dynamic Island vanish.
        val shouldShow = dynamicIslandEnabled &&
            !appInForeground &&
            exo != null &&
            exo.currentMediaItem != null &&
            Settings.canDrawOverlays(this)

        if (!shouldShow) {
            hideDynamicIsland()
            return
        }

        val item = exo?.currentMediaItem ?: return
        val title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Swara" }
        val artist = item.mediaMetadata.artist?.toString().orEmpty()
        val artUrl = item.mediaMetadata.artworkUri?.toString().orEmpty()
            .ifBlank { if (item.mediaId.isNotBlank()) "https://i.ytimg.com/vi/${item.mediaId}/hqdefault.jpg" else "" }

        if (islandRoot == null || lastBuiltExpanded != isIslandExpanded) {
            hideDynamicIsland()
            buildDynamicIsland(isIslandExpanded)
            lastBuiltExpanded = isIslandExpanded
        }

        titleView?.text = title
        artistView?.text = artist
        val iconSize = (28 * resources.displayMetrics.density).roundToInt()
        playPauseIcon?.setImageBitmap(drawMediaIcon(if (exo.isPlaying) "pause" else "play", iconSize))

        if (artUrl.isNotBlank()) {
            val cached = artCache[artUrl]
            if (cached != null) {
                artView?.setImageBitmap(cached)
            } else {
                imgExecutor.execute {
                    val bmp = fetchSafeBitmap(artUrl) ?: run {
                        val safeId = item.mediaId.takeIf { Regex("^[A-Za-z0-9_-]{1,64}$").matches(it) }
                        if (safeId != null) fetchSafeBitmap("https://i.ytimg.com/vi/$safeId/hqdefault.jpg") else null
                    }
                    if (bmp != null) {
                        artCache[artUrl] = bmp
                        mainHandler.post { artView?.setImageBitmap(bmp) }
                    }
                }
            }
        }

        if (isIslandExpanded) updateExpandedProgress()
        mainHandler.removeCallbacks(ticker)
        mainHandler.post(ticker)
    }

    private val artHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun fetchSafeBitmap(rawUrl: String): android.graphics.Bitmap? {
        if (!rawUrl.startsWith("https://", ignoreCase = true)) return null
        return runCatching {
            val req = okhttp3.Request.Builder().url(rawUrl).build()
            artHttpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body ?: return@use null
                val maxBytes = 5L * 1024L * 1024L
                if (body.contentLength() > maxBytes) return@use null
                val bytes = body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        total += n
                        if (total > maxBytes) return@use null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: return@use null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }

    private fun buildDynamicIsland(expanded: Boolean) {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).roundToInt() }

        val bg = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = dp(28).toFloat()
            setStroke(dp(1), Color.parseColor("#2AFFFFFF"))
        }

        val root = object : LinearLayout(this) {
            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (expanded && event.action == MotionEvent.ACTION_OUTSIDE) {
                    isIslandExpanded = false
                    refreshDynamicIsland()
                    return true
                }
                return super.onTouchEvent(event)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = bg
        }

        val roundedClip = { radiusDp: Int ->
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(radiusDp).toFloat())
                }
            }
        }

        if (!expanded) {
            root.setPadding(dp(10), dp(6), dp(12), dp(6))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = roundedClip(8)
                clipToOutline = true
                setBackgroundColor(Color.parseColor("#1A1A22"))
            }
            artView = iv
            row.addView(iv, LinearLayout.LayoutParams(dp(26), dp(26)))

            val spacer = View(this)
            row.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))

            row.addView(makeWaveform(dp))
            root.addView(row)
            root.setOnClickListener {
                isIslandExpanded = true
                refreshDynamicIsland()
            }
            titleView = null
            artistView = null
            elapsedView = null
            remainView = null
            squigglyProgressView = null
            playPauseIcon = null
        } else {
            root.setPadding(dp(18), dp(16), dp(18), dp(14))

            val top = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                outlineProvider = roundedClip(12)
                clipToOutline = true
                setBackgroundColor(Color.parseColor("#1A1A22"))
                setOnClickListener { openMainApp() }
            }
            artView = iv
            top.addView(iv, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setOnClickListener { openMainApp() }
            }
            val tvTitle = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            titleView = tvTitle
            val tvArtist = TextView(this).apply {
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            artistView = tvArtist
            textCol.addView(tvTitle)
            textCol.addView(tvArtist)
            top.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })

            val wf = makeWaveform(dp).apply {
                setPadding(dp(8), dp(8), dp(6), dp(8))
                setOnClickListener {
                    isIslandExpanded = false
                    refreshDynamicIsland()
                }
            }
            top.addView(wf)

            val closeBtn = TextView(this).apply {
                text = "✕"
                setTextColor(Color.parseColor("#AAFFFFFF"))
                textSize = 15f
                setPadding(dp(8), dp(6), dp(2), dp(6))
                setOnClickListener {
                    isIslandExpanded = false
                    player?.pause()
                    player?.stop()
                    player?.clearMediaItems()
                    hideDynamicIsland()
                }
            }
            top.addView(closeBtn)
            root.addView(top)

            val progRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tvElapsed = TextView(this).apply {
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 11f
                text = "0:00"
            }
            elapsedView = tvElapsed

            val pb = SquigglyIslandProgressView(this).apply {
                setAccentColor(accentColorInt)
            }
            squigglyProgressView = pb

            val tvRemain = TextView(this).apply {
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 11f
                text = "-0:00"
            }
            remainView = tvRemain

            progRow.addView(tvElapsed)
            progRow.addView(pb, LinearLayout.LayoutParams(0, dp(18), 1f).apply {
                marginStart = dp(10)
                marginEnd = dp(10)
            })
            progRow.addView(tvRemain)
            root.addView(progRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
                bottomMargin = dp(10)
            })

            val ctrlRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val iconPx = dp(28)
            val makeIconBtn = { kind: String, onClick: () -> Unit ->
                ImageView(this).apply {
                    setImageBitmap(drawMediaIcon(kind, iconPx))
                    setPadding(dp(20), dp(4), dp(20), dp(2))
                    setOnClickListener { onClick() }
                }
            }
            ctrlRow.addView(makeIconBtn("prev") { player?.seekToPrevious() })
            val pBtn = makeIconBtn("pause") {
                val exo = player ?: return@makeIconBtn
                if (exo.isPlaying) exo.pause() else exo.play()
            }
            playPauseIcon = pBtn
            ctrlRow.addView(pBtn)
            ctrlRow.addView(makeIconBtn("next") { player?.seekToNextMediaItem() })
            root.addView(ctrlRow)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            dp(if (expanded) 340 else 190),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8)
        }
        runCatching {
            wm.addView(root, params)
            islandRoot = root
        }
    }

    private fun openMainApp() {
        isIslandExpanded = false
        hideDynamicIsland()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
    }

    private fun updateExpandedProgress() {
        val exo = player ?: return
        val pos = exo.currentPosition.coerceAtLeast(0L)
        val dur = exo.duration.coerceAtLeast(0L)
        elapsedView?.text = fmt(pos)
        remainView?.text = if (dur > pos) "-${fmt(dur - pos)}" else "-0:00"
        val frac = if (dur > 0) (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
        squigglyProgressView?.updateState(frac, exo.isPlaying)
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    private fun hideDynamicIsland() {
        mainHandler.removeCallbacks(ticker)
        waveBars.clear()
        val view = islandRoot ?: return
        runCatching {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        }
        islandRoot = null
        lastBuiltExpanded = null
        artView = null
        titleView = null
        artistView = null
        elapsedView = null
        remainView = null
        squigglyProgressView = null
        playPauseIcon = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = player
        if (exo == null || !exo.playWhenReady || exo.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        activeService = null
        hideDynamicIsland()
        try {
            equalizer?.release()
            bassBoost?.release()
            presetReverb?.release()
            virtualizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
        bassBoost = null
        presetReverb = null
        virtualizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        runCatching { imgExecutor.shutdownNow() }
        super.onDestroy()
    }
}

private class SquigglyIslandProgressView(ctx: android.content.Context) : View(ctx) {
    private var fraction = 0f
    private var playing = false
    private var phase = 0f
    private var lastFrameMs = 0L
    private val density = ctx.resources.displayMetrics.density

    private val trackPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38FFFFFF")
        strokeWidth = 2.5f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
        style = android.graphics.Paint.Style.STROKE
    }
    private val activePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB")
        strokeWidth = 3f * density
        strokeCap = android.graphics.Paint.Cap.ROUND
        style = android.graphics.Paint.Style.STROKE
    }
    private val thumbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = android.graphics.Paint.Style.FILL
    }
    private val wavePath = android.graphics.Path()

    fun setAccentColor(color: Int) {
        activePaint.color = color
        invalidate()
    }

    fun updateState(newFraction: Float, isPlaying: Boolean) {
        fraction = newFraction.coerceIn(0f, 1f)
        val wasPlaying = playing
        playing = isPlaying
        if (playing && !wasPlaying) lastFrameMs = android.os.SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val cy = height / 2f
        if (w <= 0f) return
        val thumbX = fraction * w

        canvas.drawLine(thumbX, cy, w, cy, trackPaint)

        if (thumbX > 2f) {
            if (playing) {
                val now = android.os.SystemClock.uptimeMillis()
                val dt = if (lastFrameMs > 0L) ((now - lastFrameMs).coerceIn(0L, 64L)) / 1000f else 0.016f
                lastFrameMs = now
                phase = (phase + dt * 5.7f) % (2f * Math.PI.toFloat())
            }
            val amp = if (playing) 3.2f * density else 0f
            val freq = 0.055f / density * 3f
            wavePath.reset()
            wavePath.moveTo(0f, cy + kotlin.math.sin(phase.toDouble()).toFloat() * amp)
            var x = 0f
            val step = (3f * density).coerceAtLeast(2f)
            while (x < thumbX) {
                x = (x + step).coerceAtMost(thumbX)
                val y = cy + kotlin.math.sin((x * freq + phase).toDouble()).toFloat() * amp
                wavePath.lineTo(x, y)
            }
            canvas.drawPath(wavePath, activePaint)
        }

        canvas.drawCircle(thumbX.coerceIn(3f * density, w - 3f * density), cy, 4f * density, thumbPaint)
        if (playing) postInvalidateOnAnimation()
    }
}
