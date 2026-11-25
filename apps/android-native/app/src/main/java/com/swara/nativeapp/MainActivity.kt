package com.swara.nativeapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.Coil
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.swara.nativeapp.model.*
import com.swara.nativeapp.playback.SwaraPlaybackService
import com.swara.nativeapp.ui.SwaraViewModel
import java.util.Calendar
import kotlin.math.sin

private var BgDark by mutableStateOf(Color(0xFF0D1524))
private var CardDark by mutableStateOf(Color(0xFF152033))
private var TextInk by mutableStateOf(Color(0xFFF2F1E8))
private var LangCode by mutableStateOf("en")
private val S: LangStrings get() = stringsFor(LangCode)
private val MOOD_CHIPS = listOf("Semua", "Santai", "Energi", "Fokus", "Romantis", "Galau")
private val SEARCH_SUGGESTIONS = listOf("Pop", "Lo-fi", "Acoustic", "Chill", "Remix")

class MainActivity : ComponentActivity() {
    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        Coil.setImageLoader(
            ImageLoader.Builder(applicationContext)
                .memoryCache {
                    MemoryCache.Builder(applicationContext)
                        .maxSizePercent(0.25)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(60L * 1024 * 1024)
                        .build()
                }
                .crossfade(150)
                .respectCacheHeaders(false)
                .build()
        )

        setContent { SwaraThemeRoot() }
    }

    override fun onStart() {
        super.onStart()
        SwaraPlaybackService.setAppInForeground(true)
    }

    override fun onStop() {
        SwaraPlaybackService.setAppInForeground(false)
        super.onStop()
    }
}

@Composable
private fun SwaraThemeRoot(vm: SwaraViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    val isCustom = state.accentId == "custom"
    val accent = BUILTIN_ACCENTS.find { it.id == state.accentId } ?: BUILTIN_ACCENTS.first()
    val primaryColor = if (isCustom) Color(state.customAccentHex) else Color(accent.primaryHex)
    val isLight = state.themeMode == "light"
    val bgColor = when (state.themeMode) {
        "light" -> Color(0xFFF2F1E8) // Putih Pasir Hangat
        "amoled" -> Color(0xFF000000)
        else -> Color(0xFF0D1524) // Biru Malam Samudra
    }
    val cardColor = when (state.themeMode) {
        "light" -> Color(0xFFFFFFFF)
        "amoled" -> Color(0xFF0D1524)
        else -> Color(0xFF152033)
    }
    val inkColor = if (isLight) Color(0xFF0D1524) else Color(0xFFF2F1E8)
    BgDark = bgColor
    CardDark = cardColor
    TextInk = inkColor
    LangCode = state.language
    LaunchedEffect(primaryColor) {
        SwaraPlaybackService.setAccentColor(primaryColor.toArgb())
    }

    val glowColor = if (isCustom) {
        Color(
            red = primaryColor.red * if (isLight) 0.12f else 0.22f,
            green = primaryColor.green * if (isLight) 0.12f else 0.22f,
            blue = primaryColor.blue * if (isLight) 0.12f else 0.22f,
            alpha = 1f,
        )
    } else if (isLight) {
        primaryColor.copy(alpha = 0.14f)
    } else Color(accent.glowBgHex)

    val scheme = if (isLight) {
        lightColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.18f),
            onPrimaryContainer = inkColor,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.14f),
            onSecondaryContainer = inkColor,
            tertiary = primaryColor,
            background = bgColor,
            onBackground = inkColor,
            surface = cardColor,
            onSurface = inkColor,
            surfaceVariant = cardColor,
            onSurfaceVariant = inkColor.copy(alpha = 0.72f),
            outline = inkColor.copy(alpha = 0.22f),
        )
    } else {
        darkColorScheme(
            primary = primaryColor,
            onPrimary = Color.White,
            primaryContainer = primaryColor.copy(alpha = 0.24f),
            onPrimaryContainer = inkColor,
            secondary = primaryColor,
            onSecondary = Color.White,
            secondaryContainer = primaryColor.copy(alpha = 0.18f),
            onSecondaryContainer = inkColor,
            tertiary = primaryColor,
            background = bgColor,
            onBackground = inkColor,
            surface = cardColor,
            onSurface = inkColor,
            surfaceVariant = cardColor,
            onSurfaceVariant = inkColor.copy(alpha = 0.72f),
            outline = inkColor.copy(alpha = 0.22f),
        )
    }

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3200L)
        showSplash = false
    }

    MaterialTheme(colorScheme = scheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            SwaraNativeApp(vm = vm, primaryColor = primaryColor, glowColor = glowColor)
            AnimatedVisibility(
                visible = showSplash,
                exit = fadeOut(animationSpec = tween(550))
            ) {
                SwaraSplashOverlay()
            }
        }
    }
}

@Composable
private fun SwaraSplashOverlay() {
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.76f) }
    val logoOffsetY = remember { Animatable(36f) }
    val titleAlpha = remember { Animatable(0f) }
    val lineFrac = remember { Animatable(0f) }

    val inf = rememberInfiniteTransition(label = "fluidSplash")
    val wavePhase by inf.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "wavePhase"
    )
    val floatY by inf.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY"
    )

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 220f))
    }
    LaunchedEffect(Unit) {
        logoOffsetY.animateTo(0f, tween(950, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(380L)
        titleAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(550L)
        lineFrac.animateTo(1f, tween(850, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF172742), Color(0xFF0D1524))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Fluid, organic acoustic sine waves gliding softly across the background (zero-GC path reuse)
        val wavePath = remember { Path() }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cy = h * 0.50f
            val waveSpecs = listOf(
                Triple(Color(0xFFE54B4B), 0f, 34f),
                Triple(Color(0xFFF7B538), 1.4f, 26f),
                Triple(Color(0xFF1C9B90), 2.8f, 30f),
                Triple(Color(0xFF7C3E66), 4.1f, 22f),
            )
            waveSpecs.forEachIndexed { idx, (col, phaseShift, amp) ->
                wavePath.reset()
                val steps = 28
                val yOff = (idx - 1.5f) * 18f
                for (i in 0..steps) {
                    val t = i.toFloat() / steps
                    val x = w * t
                    val env = kotlin.math.sin(t * Math.PI).toFloat()
                    val y = cy + yOff + kotlin.math.sin(t * 5.2f + wavePhase + phaseShift) * amp * env
                    if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                }
                drawPath(
                    path = wavePath,
                    color = col.copy(alpha = 0.24f * logoAlpha.value),
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.graphicsLayer(
                scaleX = logoScale.value,
                scaleY = logoScale.value,
                translationY = logoOffsetY.value + floatY,
                alpha = logoAlpha.value,
            )
        ) {
            SwaraAnimatedVectorLogo(modifier = Modifier.size(164.dp))
            Text(
                "SWARA",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (4f + 4f * titleAlpha.value).sp,
                color = Color(0xFFF2F1E8).copy(alpha = titleAlpha.value)
            )
            Box(
                modifier = Modifier
                    .width((92f * lineFrac.value).dp)
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFFE54B4B),
                                Color(0xFFF7B538),
                                Color(0xFF1C9B90),
                                Color(0xFF7C3E66),
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun SwaraAnimatedVectorLogo(modifier: Modifier = Modifier) {
    val timelineMs = remember { Animatable(0f) }
    val settleScale = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        timelineMs.animateTo(
            targetValue = 2000f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1750L)
        settleScale.animateTo(1.045f, tween(340, easing = FastOutSlowInEasing))
        settleScale.animateTo(1f, tween(510, easing = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)))
    }

    data class StrokeSpec(val measure: android.graphics.PathMeasure, val len: Float, val color: Color, val width: Float, val delayMs: Float, val durMs: Float)
    data class FillSpec(val path: Path, val color: Color, val delayMs: Float)

    val specs = remember {
        fun gp(block: android.graphics.Path.() -> Unit) = android.graphics.Path().apply(block)
        fun s(color: Long, w: Float, delayMs: Float, durMs: Float, block: android.graphics.Path.() -> Unit): StrokeSpec {
            val pm = android.graphics.PathMeasure(gp(block), false)
            return StrokeSpec(pm, pm.length, Color(color), w, delayMs, durMs)
        }
        val strokes = listOf(
            s(0xFFE54B4BL, 15.5f, 300f, 1050f) { moveTo(16f, 86f); cubicTo(18f, 42f, 68f, 20f, 106f, 46f) },
            s(0xFFF7B538L, 15.0f, 420f, 1000f) { moveTo(38f, 82f); cubicTo(42f, 54f, 76f, 44f, 96f, 62f) },
            s(0xFF1C9B90L, 14.0f, 540f, 900f)  { moveTo(59f, 80f); cubicTo(62f, 67f, 78f, 63f, 86f, 73f) },
            s(0xFFE54B4BL, 16.0f, 380f, 1250f) { moveTo(66f, 113f); cubicTo(96f, 112f, 114f, 55f, 186f, 66f) },
            s(0xFFF7B538L, 16.0f, 480f, 1300f) {
                moveTo(14f, 102f)
                cubicTo(32f, 82f, 54f, 88f, 52f, 108f)
                cubicTo(50f, 132f, 82f, 138f, 105f, 112f)
                cubicTo(128f, 86f, 152f, 82f, 186f, 91f)
            },
            s(0xFF1C9B90L, 16.5f, 580f, 1300f) {
                moveTo(34f, 108f)
                cubicTo(24f, 134f, 48f, 162f, 82f, 152f)
                cubicTo(112f, 143f, 122f, 108f, 154f, 108f)
                cubicTo(172f, 108f, 184f, 118f, 186f, 134f)
            },
            s(0xFFE54B4BL, 13.5f, 720f, 950f)  { moveTo(122f, 136f); cubicTo(122f, 146f, 134f, 150f, 140f, 142f) },
            s(0xFF7C3E66L, 15.5f, 660f, 1150f) {
                moveTo(76f, 168f)
                cubicTo(92f, 184f, 134f, 184f, 152f, 162f)
                cubicTo(166f, 144f, 154f, 122f, 134f, 126f)
            },
            s(0xFFF2F1E8L, 15.5f, 620f, 1200f) {
                moveTo(98f, 154f)
                cubicTo(118f, 178f, 162f, 176f, 172f, 148f)
                cubicTo(180f, 124f, 156f, 106f, 136f, 116f)
            },
        )
        val fills = listOf(
            FillSpec(gp { moveTo(102f, 12f); lineTo(182f, 12f); lineTo(178f, 48f); cubicTo(148f, 45f, 122f, 33f, 102f, 12f); close() }.asComposePath(), Color(0xFFE54B4BL), 950f),
            FillSpec(gp { moveTo(36f, 154f); cubicTo(54f, 164f, 72f, 173f, 88f, 186f); lineTo(34f, 186f); close() }.asComposePath(), Color(0xFFF2F1E8L), 1050f),
        )
        strokes to fills
    }
    val reusableSeg = remember { android.graphics.Path() }
    val easeDraw = remember { CubicBezierEasing(0.33f, 1f, 0.68f, 1f) }
    val clipSquircle = remember {
        Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(16f, 16f, 184f, 184f, 54f, 54f))
        }
    }

    Canvas(modifier = modifier.graphicsLayer(scaleX = settleScale.value, scaleY = settleScale.value)) {
        val t = timelineMs.value
        val s = size.minDimension / 200f
        drawContext.canvas.save()
        drawContext.canvas.scale(s, s)
        drawContext.canvas.clipPath(clipSquircle)

        specs.second.forEach { f ->
            val p = ((t - f.delayMs) / 500f).coerceIn(0f, 1f)
            if (p > 0.01f) {
                drawContext.canvas.save()
                val sc = 0.85f + 0.15f * p
                drawContext.canvas.translate(100f * (1f - sc), 100f * (1f - sc))
                drawContext.canvas.scale(sc, sc)
                drawPath(f.path, color = f.color.copy(alpha = p))
                drawContext.canvas.restore()
            }
        }
        specs.first.forEach { st ->
            val raw = ((t - st.delayMs) / st.durMs).coerceIn(0f, 1f)
            if (raw > 0f) {
                val frac = easeDraw.transform(raw)
                reusableSeg.reset()
                st.measure.getSegment(0f, st.len * frac, reusableSeg, true)
                drawPath(
                    path = reusableSeg.asComposePath(),
                    color = st.color.copy(alpha = (raw * 4f).coerceIn(0f, 1f)),
                    style = Stroke(width = st.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        drawContext.canvas.restore()
    }
}

private enum class NavTab { HOME, EXPLORE, LIBRARY, STATS, SETTINGS }

@Composable
private fun MiniPlayerProgress(
    progressFlow: kotlinx.coroutines.flow.StateFlow<Pair<Long, Long>>,
    primaryColor: Color,
    collapseFraction: Float,
) {
    val (posMs, durMs) = progressFlow.collectAsState().value
    val progress = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.dp).graphicsLayer { alpha = 1f - collapseFraction },
        color = primaryColor,
        trackColor = TextInk.copy(alpha = 0.08f),
    )
}
private fun navTabLabel(t: NavTab): String = when (t) {
    NavTab.HOME -> S.home
    NavTab.EXPLORE -> S.explore
    NavTab.LIBRARY -> S.library
    NavTab.STATS -> S.stats
    NavTab.SETTINGS -> S.settings
}

private enum class PlayerTab(val label: String) {
    SONG("Lagu"),
    LYRICS("Lirik"),
    QUEUE("Antrean"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwaraNativeApp(
    vm: SwaraViewModel,
    primaryColor: Color,
    glowColor: Color,
) {
    val state by vm.ui.collectAsState()
    var activeTab by remember { mutableStateOf(NavTab.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var showFullscreen by remember { mutableStateOf(false) }
    var showEqSheet by remember { mutableStateOf(false) }
    var showSoundStyleSheet by remember { mutableStateOf(false) }
    var playerTab by remember { mutableStateOf(PlayerTab.SONG) }
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportPlaylistDialog by remember { mutableStateOf(false) }
    var showIdentifySheet by remember { mutableStateOf(false) }
    var showListenSheet by remember { mutableStateOf(false) }
    var showSongMenuSheet by remember { mutableStateOf(false) }
    var showRecapSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showFriendsStats by remember { mutableStateOf(false) }
    var showDiscordSheet by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showStorageSheet by remember { mutableStateOf(false) }
    var ambientMode by remember { mutableStateOf(false) }
    var openedUserPlaylist by remember { mutableStateOf<UserPlaylist?>(null) }
    val deviceAudioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> vm.importDeviceAudio(uris) }

    // Intercept system Back button so it closes overlays/tabs instead of exiting the app
    val hasOpenOverlay = showSongMenuSheet || showEqSheet || showSoundStyleSheet ||
        showIdentifySheet || showListenSheet || showHistorySheet || showFriendsStats ||
        showDiscordSheet || showAboutSheet || showStorageSheet || showRecapSheet || showFullscreen || openedUserPlaylist != null ||
        playlistPickerTrack != null || showCreatePlaylistDialog || showImportPlaylistDialog ||
        state.openedArtistName != null || state.openedPlaylistTitle != null || state.openedAlbumTitle != null

    BackHandler(enabled = hasOpenOverlay || activeTab != NavTab.HOME) {
        when {
            showSongMenuSheet -> showSongMenuSheet = false
            showEqSheet -> showEqSheet = false
            showSoundStyleSheet -> showSoundStyleSheet = false
            showIdentifySheet -> showIdentifySheet = false
            showListenSheet -> showListenSheet = false
            showDiscordSheet -> showDiscordSheet = false
            showAboutSheet -> showAboutSheet = false
            showStorageSheet -> showStorageSheet = false
            playlistPickerTrack != null -> playlistPickerTrack = null
            showCreatePlaylistDialog -> showCreatePlaylistDialog = false
            showImportPlaylistDialog -> showImportPlaylistDialog = false
            openedUserPlaylist != null -> openedUserPlaylist = null
            state.openedArtistName != null -> vm.closeArtist()
            state.openedPlaylistTitle != null -> vm.closeOpenedPlaylist()
            state.openedAlbumTitle != null -> vm.closeAlbum()
            showHistorySheet -> showHistorySheet = false
            showFriendsStats -> showFriendsStats = false
            showRecapSheet -> showRecapSheet = false
            showFullscreen -> showFullscreen = false
            activeTab != NavTab.HOME -> activeTab = NavTab.HOME
        }
    }

    val likedIds = remember(state.likedTracks) { state.likedTracks.map { it.id }.toSet() }
    val downloadedIds = remember(state.downloadedTracks) { state.downloadedTracks.map { it.id }.toSet() }

    val onTrackAction: (List<Track>, Int) -> Unit = remember(vm) {
        { list, idx ->
            val item = list.getOrNull(idx)
            if (item != null && item.browseId.isNotBlank()) {
                vm.openBrowseItem(item)
            } else {
                vm.playFromList(list, idx)
            }
        }
    }
    val onToggleLikeCb: (Track) -> Unit = remember(vm) { { vm.toggleLike(it) } }
    val onAddToQueueCb: (Track) -> Unit = remember(vm) { { vm.addToQueue(it) } }
    val onDownloadCb: (Track) -> Unit = remember(vm) { { vm.downloadTrack(it) } }
    val onSaveToPlaylistCb: (Track) -> Unit = remember { { playlistPickerTrack = it } }
    val onOpenArtistCb: (String) -> Unit = remember(vm) { { vm.openArtist(it) } }

    var isBarCollapsed by remember { mutableStateOf(false) }
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    var availableUpdate by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(Unit) {
        val ver = runCatching {
            appCtx.packageManager.getPackageInfo(appCtx.packageName, 0).versionName ?: "1.0.0"
        }.getOrDefault("1.0.0")
        val found = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val client = okhttp3.OkHttpClient()
                val req = okhttp3.Request.Builder()
                    .url("https://api.github.com/repos/swaramusic/Swara/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) null
                    else {
                        val json = org.json.JSONObject(r.body?.string().orEmpty())
                        val tag = json.optString("tag_name").removePrefix("v").trim()
                        val html = json.optString("html_url").ifBlank { "https://github.com/swaramusic/Swara/releases" }
                        if (tag.isNotBlank() && tag != ver && html.startsWith("https://")) tag to html else null
                    }
                }
            }.getOrNull()
        }
        if (found != null) availableUpdate = found
    }
    val barScrollConn = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            private var dyAcc = 0f
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                dyAcc = (dyAcc + available.y).coerceIn(-60f, 60f)
                if (dyAcc < -32f && !isBarCollapsed) {
                    isBarCollapsed = true
                    dyAcc = 0f
                } else if (dyAcc > 28f && isBarCollapsed) {
                    isBarCollapsed = false
                    dyAcc = 0f
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgDark,
        contentColor = TextInk
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            primaryColor.copy(alpha = 0.24f),
                            glowColor.copy(alpha = 0.12f),
                            BgDark,
                            BgDark
                        )
                    )
                )
                .nestedScroll(barScrollConn)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                availableUpdate?.let { (newVer, dlUrl) ->
                    Surface(
                        color = primaryColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null, tint = primaryColor, modifier = Modifier.size(18.dp))
                            Text("Update v$newVer available", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.weight(1f))
                            Surface(
                                color = primaryColor,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                    runCatching { appCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dlUrl))) }
                                }
                            ) {
                                Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            IconButton(onClick = { availableUpdate = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextInk, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
                state.errorMessage?.let { msg ->
                    Surface(
                        color = Color(0xFF4A1822),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(msg, fontSize = 12.sp, color = Color(0xFFFFB3C1), modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.dismissError() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = activeTab,
                        contentKey = { it },
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (slideInHorizontally(tween(170, easing = FastOutSlowInEasing)) { dir * it / 16 } +
                                fadeIn(tween(150))) togetherWith
                                fadeOut(tween(110))
                        },
                        label = "mainTabTransition"
                    ) { currentNavTab ->
                    when (currentNavTab) {
                        NavTab.HOME -> HomeScreen(
                            shelves = state.homeShelves,
                            recent = state.recentTracks,
                            resumeTrack = state.currentTrack ?: state.recentTracks.firstOrNull(),
                            streakDays = state.streakDays,
                            activeMood = state.activeMood,
                            isLoading = state.isLoadingHome,
                            likedIds = likedIds,
                            downloadedIds = downloadedIds,
                            downloadingIds = state.downloadingIds,
                            primaryColor = primaryColor,
                            onSelectMood = { vm.selectMood(it) },
                            onPlay = onTrackAction,
                            onToggleLike = onToggleLikeCb,
                            onAddToQueue = onAddToQueueCb,
                            onDownload = onDownloadCb,
                            onSaveToPlaylist = onSaveToPlaylistCb,
                            onOpenHistory = { showHistorySheet = true },
                            onOpenListenTogether = { showListenSheet = true },
                            onOpenStats = { activeTab = NavTab.STATS },
                            onRefresh = { vm.loadHome() },
                        )
                        NavTab.EXPLORE -> ExploreScreen(
                            query = searchQuery,
                            onQueryChange = {
                                searchQuery = it
                                vm.search(it)
                            },
                            results = state.searchResults,
                            searchHistory = state.searchHistory,
                            recentTracks = state.recentTracks,
                            topSongs = state.topSongs,
                            topArtists = state.topArtists,
                            followedArtists = state.followedArtists,
                            artistImages = state.artistImages,
                            isSearching = state.isSearching,
                            likedIds = likedIds,
                            downloadedIds = downloadedIds,
                            downloadingIds = state.downloadingIds,
                            primaryColor = primaryColor,
                            glowColor = glowColor,
                            onPlay = onTrackAction,
                            onPlaySearchResult = { vm.playSingleWithRadio(it) },
                            onToggleLike = onToggleLikeCb,
                            onAddToQueue = onAddToQueueCb,
                            onDownload = onDownloadCb,
                            onSaveToPlaylist = onSaveToPlaylistCb,
                            onOpenIdentify = { showIdentifySheet = true },
                            onOpenArtist = onOpenArtistCb,
                            onRemoveHistory = { vm.removeSearchHistory(it) },
                            onClearHistory = { vm.clearSearchHistory() },
                        )
                        NavTab.LIBRARY -> LibraryScreen(
                            liked = state.likedTracks,
                            recent = state.recentTracks,
                            downloaded = state.downloadedTracks,
                            localTracks = state.localTracks,
                            playlists = state.playlists,
                            followedArtists = state.followedArtists,
                            artistImages = state.artistImages,
                            likedIds = likedIds,
                            downloadedIds = downloadedIds,
                            downloadingIds = state.downloadingIds,
                            primaryColor = primaryColor,
                            onPlay = onTrackAction,
                            onToggleLike = onToggleLikeCb,
                            onAddToQueue = onAddToQueueCb,
                            onDownload = onDownloadCb,
                            onDeleteDownload = { vm.deleteDownload(it.id) },
                            onSaveToPlaylist = onSaveToPlaylistCb,
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onImportPlaylist = { showImportPlaylistDialog = true },
                            onImportDevice = { deviceAudioLauncher.launch(arrayOf("audio/*")) },
                            onOpenPlaylist = { openedUserPlaylist = it },
                            onDeletePlaylist = { vm.deletePlaylist(it.id) },
                            onOpenArtist = onOpenArtistCb,
                            onUnfollowArtist = { vm.toggleFollowArtist(it) },
                            onOpenExplore = { activeTab = NavTab.EXPLORE },
                        )
                        NavTab.STATS -> StatsScreen(
                            streakDays = state.streakDays,
                            totalPlays = state.totalPlays,
                            totalMinutes = state.totalMinutes,
                            likedCount = state.likedTracks.size,
                            recentTracks = state.recentTracks,
                            topSongs = state.topSongs,
                            topArtists = state.topArtists,
                            artistImages = state.artistImages,
                            primaryColor = primaryColor,
                            onOpenRecap = { showRecapSheet = true },
                            onOpenFriendsStats = { showFriendsStats = true },
                            onPlaySongs = onTrackAction,
                            onOpenArtist = { vm.openArtist(it) },
                        )
                        NavTab.SETTINGS -> SettingsScreen(
                            themeMode = state.themeMode,
                            audioQuality = state.audioQuality,
                            accentId = state.accentId,
                            customAccentHex = state.customAccentHex,
                            country = state.country,
                            language = state.language,
                            dynamicIslandEnabled = state.dynamicIslandEnabled,
                            discordRpcEnabled = state.discordRpcEnabled,
                            listenRoomCode = state.listenRoomCode,
                            listenStatus = state.listenStatus,
                            sleepRemainingSec = state.sleepTimerRemainingSec,
                            currentTrack = state.currentTrack,
                            primaryColor = primaryColor,
                            onSelectThemeMode = { vm.setThemeMode(it) },
                            onSelectAudioQuality = { vm.setAudioQuality(it) },
                            onSelectAccent = { vm.setAccent(it) },
                            onSelectCustomAccent = { vm.setCustomAccent(it) },
                            onSelectCountry = { vm.setCountry(it) },
                            onSelectLanguage = { vm.setLanguage(it) },
                            onToggleDynamicIsland = { vm.setDynamicIslandEnabled(it) },
                            onOpenDiscord = { showDiscordSheet = true },
                            onOpenListenTogether = { showListenSheet = true },
                            onCycleSleepTimer = { vm.cycleSleepTimer() },
                            onOpenEq = { showEqSheet = true },
                            onOpenSoundStyle = { showSoundStyleSheet = true },
                            onOpenStorage = { showStorageSheet = true },
                            onOpenAbout = { showAboutSheet = true },
                        )
                    }
                    }
                }
            }

            // Transparent overlay Liquid Glass Miniplayer + Nav (content scrolls behind it edge-to-edge!)
            val rawCollapse by animateFloatAsState(
                targetValue = if (isBarCollapsed) 1f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "barCollapseFraction"
            )
            val collapseFraction = rawCollapse.coerceIn(0f, 1f)
            val liquidGlassBg = if (CardDark == Color(0xFFF3F6FC)) Color.White.copy(alpha = 0.86f) else CardDark.copy(alpha = 0.86f)
            val liquidGlassBorder = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.62f),
                    primaryColor.copy(alpha = 0.45f),
                    glowColor.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.35f)
                )
            )
            val activeIcon = when (activeTab) {
                NavTab.HOME -> Icons.Default.Home
                NavTab.EXPLORE -> Icons.Default.Explore
                NavTab.LIBRARY -> Icons.Default.LibraryMusic
                NavTab.STATS -> Icons.Default.BarChart
                NavTab.SETTINGS -> Icons.Default.Settings
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp * (1f - collapseFraction))
                ) {
                    // Top row: Left circular active-tab pill (morphs in) + Miniplayer pill (slides smoothly right)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (collapseFraction > 0.02f) {
                            Surface(
                                color = liquidGlassBg,
                                shape = CircleShape,
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(54.dp)
                                    .graphicsLayer {
                                        alpha = collapseFraction
                                        scaleX = 0.65f + 0.35f * collapseFraction
                                        scaleY = 0.65f + 0.35f * collapseFraction
                                    }
                                    .clip(CircleShape)
                                    .clickable { isBarCollapsed = false }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(activeIcon, contentDescription = "Expand Nav", tint = TextInk, modifier = Modifier.size(24.dp))
                                }
                            }
                        }

                        val curTrack = state.currentTrack
                        if (curTrack != null) {
                            Surface(
                                color = liquidGlassBg,
                                shape = RoundedCornerShape(30.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 64.dp * collapseFraction)
                                    .clip(RoundedCornerShape(30.dp))
                                    .clickable { showFullscreen = true }
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = curTrack.artworkUrl,
                                            contentDescription = curTrack.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(curTrack.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(curTrack.artist, fontSize = 11.sp, color = TextInk.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        IconButton(onClick = { vm.toggleLike(curTrack) }, modifier = Modifier.size(34.dp)) {
                                            Icon(
                                                if (curTrack.id in likedIds) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = "Like",
                                                tint = if (curTrack.id in likedIds) primaryColor else TextInk,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        Surface(
                                            color = TextInk,
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable { vm.togglePlayPause() }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = BgDark,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        IconButton(onClick = { vm.playNext() }, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextInk, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                    if (collapseFraction < 0.85f) {
                                        MiniPlayerProgress(progressFlow = vm.progress, primaryColor = primaryColor, collapseFraction = collapseFraction)
                                    }
                                }
                            }
                        } else if (collapseFraction > 0.02f) {
                            Surface(
                                color = liquidGlassBg,
                                shape = RoundedCornerShape(30.dp),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 64.dp * collapseFraction)
                                    .height(54.dp)
                                    .graphicsLayer { alpha = collapseFraction }
                                    .clip(RoundedCornerShape(30.dp))
                                    .clickable { isBarCollapsed = false }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(navTabLabel(activeTab), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                                    Icon(Icons.Default.ExpandLess, contentDescription = "Expand", tint = TextInk.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }

                    // Bottom 5-tab Nav Pill (smoothly shrinks & fades as collapseFraction -> 1f)
                    if (collapseFraction < 0.98f) {
                        Surface(
                            color = liquidGlassBg,
                            shape = RoundedCornerShape(32.dp),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp * (1f - collapseFraction))
                                .graphicsLayer {
                                    alpha = (1f - collapseFraction * 1.25f).coerceIn(0f, 1f)
                                    translationY = 24f * collapseFraction
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(vertical = 2.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavTab.values().forEach { tab ->
                                    val selected = activeTab == tab
                                    val iconScale by animateFloatAsState(
                                        targetValue = if (selected) 1.14f else 1.0f,
                                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                                        label = "navScale"
                                    )
                                    val icon = when (tab) {
                                        NavTab.HOME -> Icons.Default.Home
                                        NavTab.EXPLORE -> Icons.Default.Explore
                                        NavTab.LIBRARY -> Icons.Default.LibraryMusic
                                        NavTab.STATS -> Icons.Default.BarChart
                                        NavTab.SETTINGS -> Icons.Default.Settings
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(22.dp))
                                            .background(if (selected) TextInk.copy(alpha = 0.14f) else Color.Transparent)
                                            .clickable { activeTab = tab; isBarCollapsed = false }
                                            .padding(horizontal = 2.dp, vertical = 4.dp)
                                    ) {
                                        Icon(icon, contentDescription = navTabLabel(tab), tint = if (selected) primaryColor else TextInk.copy(alpha = 0.65f), modifier = Modifier.size(20.dp).scale(iconScale))
                                        Text(
                                            navTabLabel(tab),
                                            fontSize = 10.sp,
                                            lineHeight = 12.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            color = if (selected) TextInk else TextInk.copy(alpha = 0.65f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showFullscreen && state.currentTrack != null,
                enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(220)) { it / 3 } + fadeOut(tween(180))
            ) {
                state.currentTrack?.let { cur ->
                    val (posMs, durMs) = vm.progress.collectAsState().value
                    FullscreenPlayerOverlay(
                        track = cur,
                        isPlaying = state.isPlaying,
                        positionMs = posMs,
                        durationMs = durMs,
                    shuffleEnabled = state.shuffleEnabled,
                    repeatMode = state.repeatMode,
                    isLiked = cur.id in likedIds,
                    isDownloaded = cur.id in downloadedIds,
                    isDownloading = cur.id in state.downloadingIds,
                    activeTab = playerTab,
                    onSelectTab = { playerTab = it },
                    lyrics = state.lyrics,
                    isLoadingLyrics = state.isLoadingLyrics,
                    queue = state.queue,
                    currentIndex = state.currentIndex,
                    sleepRemainingSec = state.sleepTimerRemainingSec,
                    primaryColor = primaryColor,
                    glowColor = glowColor,
                    onClose = { showFullscreen = false },
                    onTogglePlayPause = { vm.togglePlayPause() },
                    onNext = { vm.playNext() },
                    onPrev = { vm.playPrevious() },
                    onSeek = { vm.seekTo(it) },
                    onToggleShuffle = { vm.toggleShuffle() },
                    onCycleRepeat = { vm.cycleRepeat() },
                    onToggleLike = { vm.toggleLike(cur) },
                    onDownload = { vm.downloadTrack(cur) },
                    onPlayQueueIndex = { idx -> vm.playFromList(state.queue, idx) },
                    onRemoveQueueIndex = { idx -> vm.removeFromQueue(idx) },
                    onCycleSleepTimer = { vm.cycleSleepTimer() },
                    onOpenEq = { showEqSheet = true },
                    onOpenMenu = { showSongMenuSheet = true },
                    ambientMode = ambientMode,
                    onToggleAmbient = { ambientMode = !ambientMode },
                )
                }
            }

            if (showSongMenuSheet && state.currentTrack != null) {
                val cur = state.currentTrack!!
                val ctx = androidx.compose.ui.platform.LocalContext.current
                ModalBottomSheet(
                    onDismissRequest = { showSongMenuSheet = false },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    SongOptionsSheetContent(
                        track = cur,
                        volume = state.volume,
                        soundStyle = state.soundStyle,
                        speed = state.speed,
                        reverbPct = state.reverbPct,
                        isDownloaded = cur.id in downloadedIds,
                        ambientMode = ambientMode,
                        primaryColor = primaryColor,
                        onVolumeChange = { vm.setPlayerVolume(it) },
                        onStartRadio = { vm.playSingleWithRadio(cur); showSongMenuSheet = false },
                        onAddToPlaylist = { showSongMenuSheet = false; playlistPickerTrack = cur },
                        onCopyLink = {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Swara", "https://music.youtube.com/watch?v=${cur.id}"))
                            showSongMenuSheet = false
                        },
                        onShare = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${cur.title} - ${cur.artist}\nhttps://music.youtube.com/watch?v=${cur.id}")
                            }
                            ctx.startActivity(Intent.createChooser(send, "Bagikan Lagu"))
                            showSongMenuSheet = false
                        },
                        onViewArtist = {
                            showSongMenuSheet = false
                            vm.openArtist(cur.artist, cur.artworkUrl)
                        },
                        onViewAlbum = {
                            showSongMenuSheet = false
                            vm.openAlbum(cur)
                        },
                        onExcludeFromTaste = {
                            showSongMenuSheet = false
                            vm.excludeFromTaste(cur)
                        },
                        sleepRemainingSec = state.sleepTimerRemainingSec,
                        onCycleSleepTimer = { vm.cycleSleepTimer() },
                        onDownload = { vm.downloadTrack(cur); showSongMenuSheet = false },
                        onListenTogether = { showSongMenuSheet = false; showListenSheet = true },
                        onToggleAmbient = { ambientMode = !ambientMode; showSongMenuSheet = false },
                        onOpenEq = { showSongMenuSheet = false; showEqSheet = true },
                        onOpenSoundStyle = { showSongMenuSheet = false; showSoundStyleSheet = true },
                    )
                }
            }

            AnimatedVisibility(
                visible = showRecapSheet,
                enter = slideInVertically(tween(280)) { it / 4 } + fadeIn(tween(240)),
                exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180))
            ) {
                RecapFullScreenOverlay(
                    totalMinutes = state.totalMinutes,
                    recentTracks = state.recentTracks,
                    topSongs = state.topSongs,
                    topArtists = state.topArtists,
                    artistImages = state.artistImages,
                    primaryColor = primaryColor,
                    onClose = { showRecapSheet = false },
                )
            }

            AnimatedVisibility(
                visible = showHistorySheet,
                enter = slideInVertically(tween(260)) { it / 4 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180))
            ) {
                HistoryFullScreenOverlay(
                    tracks = state.recentTracks,
                    likedIds = likedIds,
                    downloadedIds = downloadedIds,
                    downloadingIds = state.downloadingIds,
                    primaryColor = primaryColor,
                    onPlay = { list, idx ->
                        vm.playFromList(list, idx)
                        showHistorySheet = false
                    },
                    onToggleLike = { vm.toggleLike(it) },
                    onAddToQueue = { vm.addToQueue(it) },
                    onDownload = { vm.downloadTrack(it) },
                    onSaveToPlaylist = { playlistPickerTrack = it },
                    onClearHistory = { vm.clearListenHistory() },
                    onClose = { showHistorySheet = false },
                )
            }

            AnimatedVisibility(
                visible = showFriendsStats,
                enter = slideInVertically(tween(260)) { it / 4 } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(220)) { it / 4 } + fadeOut(tween(180))
            ) {
                TopArtistsWithFriendsOverlay(
                    topArtists = state.topArtists,
                    artistImages = state.artistImages,
                    recentTracks = state.recentTracks,
                    listenRoomCode = state.listenRoomCode,
                    primaryColor = primaryColor,
                    onOpenListenTogether = { showFriendsStats = false; showListenSheet = true },
                    onOpenArtist = { showFriendsStats = false; vm.openArtist(it) },
                    onClose = { showFriendsStats = false },
                )
            }

            AnimatedVisibility(
                visible = showDiscordSheet,
                enter = slideInVertically(tween(240)) { it / 4 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(180))
            ) {
                DiscordSettingsOverlay(
                    enabled = state.discordRpcEnabled,
                    token = state.discordToken,
                    username = state.discordUsername,
                    status = state.discordStatus,
                    showButtons = state.discordShowButtons,
                    template = state.discordTemplate,
                    currentTrack = state.currentTrack,
                    primaryColor = primaryColor,
                    onToggleEnabled = { vm.setDiscordRpcEnabled(it) },
                    onSaveToken = { vm.setDiscordToken(it) },
                    onDisconnect = { vm.disconnectDiscord() },
                    onToggleShowButtons = { vm.setDiscordShowButtons(it) },
                    onSelectTemplate = { vm.setDiscordTemplate(it) },
                    onClose = { showDiscordSheet = false },
                )
            }

            AnimatedVisibility(
                visible = showAboutSheet,
                enter = slideInVertically(tween(240)) { it / 4 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(180))
            ) {
                AboutOverlay(
                    primaryColor = primaryColor,
                    glowColor = glowColor,
                    onClose = { showAboutSheet = false },
                )
            }

            AnimatedVisibility(
                visible = showStorageSheet,
                enter = slideInVertically(tween(240)) { it / 4 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(200)) { it / 4 } + fadeOut(tween(180))
            ) {
                StorageOverlay(
                    recentCount = state.recentTracks.size,
                    songCacheEnabled = state.songCacheEnabled,
                    maxSongCacheMb = state.maxSongCacheMb,
                    primaryColor = primaryColor,
                    onToggleSongCache = { vm.setSongCacheEnabled(it) },
                    onChangeMaxCacheMb = { vm.setMaxSongCacheMb(it) },
                    onClearDownloads = { vm.clearAllDownloads() },
                    onClearCache = { vm.clearSongCache() },
                    onClearHistory = { vm.clearListenHistory() },
                    onClose = { showStorageSheet = false },
                )
            }

            state.openedAlbumTitle?.let { albTitle ->
                ModalBottomSheet(
                    onDismissRequest = { vm.closeAlbum() },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    PlaylistSheetContent(
                        title = albTitle,
                        tracks = state.openedAlbumTracks,
                        isLoading = state.isLoadingAlbum,
                        likedIds = likedIds,
                        downloadedIds = downloadedIds,
                        downloadingIds = state.downloadingIds,
                        primaryColor = primaryColor,
                        onPlay = { list, idx ->
                            vm.playFromList(list, idx)
                            vm.closeAlbum()
                        },
                        onToggleLike = { vm.toggleLike(it) },
                        onAddToQueue = { vm.addToQueue(it) },
                        onDownload = { vm.downloadTrack(it) },
                        onSaveToPlaylist = { playlistPickerTrack = it },
                    )
                }
            }

            if (showSoundStyleSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSoundStyleSheet = false },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    GayaSuaraSheetContent(
                        activeStyle = state.soundStyle,
                        speed = state.speed,
                        reverbPct = state.reverbPct,
                        primaryColor = primaryColor,
                        onSelectStyle = { vm.setSoundStyle(it) },
                        onChangeVinylRate = { vm.setVinylRate(it) },
                        onChangeReverb = { vm.setRoomReverb(it) },
                        onReset = { vm.resetSoundStyle() },
                        onDone = { showSoundStyleSheet = false },
                    )
                }
            }

            if (showEqSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showEqSheet = false },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    EqualizerSheetContent(
                        activePresetId = state.activeEqId,
                        bandsDb = state.eqBands,
                        primaryColor = primaryColor,
                        onSelectPreset = { vm.selectEqPreset(it) },
                        onChangeBand = { idx, db -> vm.setEqBand(idx, db) },
                    )
                }
            }

            // Opened Artist Page sheet (Follow / Play / Popular)
            state.openedArtistName?.let { artistName ->
                ModalBottomSheet(
                    onDismissRequest = { vm.closeArtist() },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    ArtistSheetContent(
                        name = artistName,
                        imageUrl = state.openedArtistImage,
                        tracks = state.openedArtistTracks,
                        isLoading = state.isLoadingArtist,
                        isFollowed = state.followedArtists.any { it.equals(artistName, ignoreCase = true) },
                        likedIds = likedIds,
                        downloadedIds = downloadedIds,
                        downloadingIds = state.downloadingIds,
                        primaryColor = primaryColor,
                        onToggleFollow = { vm.toggleFollowArtist(artistName) },
                        onPlay = { list, idx ->
                            vm.playFromList(list, idx)
                            vm.closeArtist()
                        },
                        onShufflePlay = { list ->
                            if (list.isNotEmpty()) {
                                vm.playFromList(list.shuffled(), 0)
                                vm.closeArtist()
                            }
                        },
                        onToggleLike = { vm.toggleLike(it) },
                        onAddToQueue = { vm.addToQueue(it) },
                        onDownload = { vm.downloadTrack(it) },
                        onSaveToPlaylist = { playlistPickerTrack = it },
                    )
                }
            }

            // Opened YouTube playlist/album sheet
            state.openedPlaylistTitle?.let { title ->
                ModalBottomSheet(
                    onDismissRequest = { vm.closeOpenedPlaylist() },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    PlaylistSheetContent(
                        title = title,
                        tracks = state.openedPlaylistTracks,
                        isLoading = state.isLoadingPlaylist,
                        likedIds = likedIds,
                        downloadedIds = downloadedIds,
                        downloadingIds = state.downloadingIds,
                        primaryColor = primaryColor,
                        onPlay = { list, idx ->
                            vm.playFromList(list, idx)
                            vm.closeOpenedPlaylist()
                        },
                        onToggleLike = { vm.toggleLike(it) },
                        onAddToQueue = { vm.addToQueue(it) },
                        onDownload = { vm.downloadTrack(it) },
                        onSaveToPlaylist = { playlistPickerTrack = it },
                    )
                }
            }

            // Opened user playlist sheet
            openedUserPlaylist?.let { pl ->
                val fresh = state.playlists.find { it.id == pl.id } ?: pl
                ModalBottomSheet(
                    onDismissRequest = { openedUserPlaylist = null },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    PlaylistSheetContent(
                        title = fresh.name,
                        tracks = fresh.tracks,
                        isLoading = false,
                        likedIds = likedIds,
                        downloadedIds = downloadedIds,
                        downloadingIds = state.downloadingIds,
                        primaryColor = primaryColor,
                        onPlay = { list, idx ->
                            vm.playFromList(list, idx)
                            openedUserPlaylist = null
                        },
                        onToggleLike = { vm.toggleLike(it) },
                        onAddToQueue = { vm.addToQueue(it) },
                        onDownload = { vm.downloadTrack(it) },
                        onSaveToPlaylist = { playlistPickerTrack = it },
                        onRemoveTrack = { t -> vm.removeTrackFromPlaylist(fresh.id, t.id) },
                    )
                }
            }

            // Add track to playlist picker
            playlistPickerTrack?.let { target ->
                ModalBottomSheet(
                    onDismissRequest = { playlistPickerTrack = null },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Simpan ke Playlist", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (state.playlists.isEmpty()) {
                            Text("Belum ada playlist.", color = TextInk.copy(alpha = 0.6f))
                        } else {
                            state.playlists.forEach { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TextInk.copy(alpha = 0.06f))
                                        .clickable {
                                            vm.addTrackToPlaylist(pl.id, target)
                                            playlistPickerTrack = null
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = primaryColor)
                                    Spacer(Modifier.width(10.dp))
                                    Text(pl.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    Text("${pl.tracks.size} lagu", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                playlistPickerTrack = null
                                showCreatePlaylistDialog = true
                            },
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.2.dp, primaryColor),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDark, contentColor = TextInk),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ Buat Playlist Baru", color = TextInk, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            if (showCreatePlaylistDialog) {
                var newName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    containerColor = CardDark,
                    titleContentColor = TextInk,
                    textContentColor = TextInk,
                    title = { Text("Buat Playlist Baru") },
                    text = {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("Nama playlist...") },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.createPlaylist(newName)
                            showCreatePlaylistDialog = false
                        }) { Text("Buat", color = primaryColor) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Batal", color = TextInk) }
                    }
                )
            }

            if (showImportPlaylistDialog) {
                var importUrl by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { if (!state.isImportingPlaylist) showImportPlaylistDialog = false },
                    containerColor = CardDark,
                    titleContentColor = TextInk,
                    textContentColor = TextInk,
                    title = { Text("Impor Playlist Spotify / YT") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Tempel link playlist publik Spotify atau YouTube Music untuk dikonversi otomatis.", fontSize = 12.sp, color = TextInk.copy(alpha = 0.7f))
                            OutlinedTextField(
                                value = importUrl,
                                onValueChange = { importUrl = it },
                                placeholder = { Text("https://open.spotify.com/playlist/...") },
                                singleLine = true,
                            )
                            if (state.isImportingPlaylist) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = primaryColor)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = !state.isImportingPlaylist,
                            onClick = { vm.importPlaylist(importUrl) { showImportPlaylistDialog = false } }
                        ) { Text(if (state.isImportingPlaylist) "Mengimpor..." else "Impor", color = primaryColor) }
                    },
                    dismissButton = {
                        TextButton(
                            enabled = !state.isImportingPlaylist,
                            onClick = { showImportPlaylistDialog = false }
                        ) { Text("Batal", color = TextInk) }
                    }
                )
            }

            if (showIdentifySheet) {
                ModalBottomSheet(
                    onDismissRequest = { showIdentifySheet = false },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    IdentifySongSheetContent(
                        results = state.identifyResults,
                        isIdentifying = state.isIdentifying,
                        primaryColor = primaryColor,
                        onSearchSnippet = { vm.identifyBySnippet(it) },
                        onPlay = { t ->
                            vm.playSingleWithRadio(t)
                            showIdentifySheet = false
                        }
                    )
                }
            }

            if (showListenSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showListenSheet = false },
                    containerColor = CardDark,
                    contentColor = TextInk
                ) {
                    ListenTogetherSheetContent(
                        roomCode = state.listenRoomCode,
                        isHost = state.listenIsHost,
                        status = state.listenStatus,
                        primaryColor = primaryColor,
                        onCreateRoom = { vm.createListenRoom() },
                        onJoinRoom = { vm.joinListenRoom(it) },
                        onLeaveRoom = { vm.leaveListenRoom() },
                    )
                }
            }
        }
    }
}

private fun greetingText(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> S.goodMorning
    in 11..14 -> S.goodAfternoon
    in 15..18 -> S.goodEvening
    else -> S.goodNight
}

@Composable
private fun HomeScreen(
    shelves: List<Shelf>,
    recent: List<Track>,
    resumeTrack: Track?,
    streakDays: Int,
    activeMood: String,
    isLoading: Boolean,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    onSelectMood: (String) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onOpenStats: () -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    greetingText(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenHistory) {
                    Icon(Icons.Default.History, contentDescription = S.recentPlayed, tint = TextInk)
                }
                IconButton(onClick = onOpenListenTogether) {
                    Icon(Icons.Default.Group, contentDescription = S.listenTogetherMenu, tint = TextInk)
                }
            }
        }

        // S.resume Hero Banner
        if (resumeTrack != null) {
            item {
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, TextInk.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPlay(listOf(resumeTrack), 0) }
                ) {
                    Row(
                        modifier = Modifier
                            .background(Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.22f), CardDark)))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AsyncImage(
                            model = resumeTrack.artworkUrl,
                            contentDescription = resumeTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(62.dp).clip(RoundedCornerShape(12.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(S.resume, fontSize = 11.sp, color = TextInk.copy(alpha = 0.6f))
                            Text(resumeTrack.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(resumeTrack.artist, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onPlay(listOf(resumeTrack), 0) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.playBtn, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // "Terakhir diputar" overlay cards (like screenshot 1)
        if (recent.isNotEmpty() && activeMood == "Semua") {
            item {
                Text(S.recentPlayed, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(recent.take(10), key = { _, t -> "recent_${t.id}" }) { idx, track ->
                        OverlayTrackCard(track = track, onClick = { onPlay(recent, idx) })
                    }
                }
            }
        }

        // Mood chips
        item {
            val moodLabels = mapOf(
                "Semua" to S.moodAll,
                "Santai" to S.moodChill,
                "Energi" to S.moodEnergy,
                "Fokus" to S.moodFocus,
                "Romantis" to S.moodRomantic,
                "Galau" to S.moodSad,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MOOD_CHIPS, key = { it }) { mood ->
                    val selected = mood == activeMood
                    val chipBg by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) primaryColor else CardDark,
                        animationSpec = tween(180),
                        label = "moodChipBg"
                    )
                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectMood(mood) }
                    ) {
                        Text(
                            moodLabels[mood] ?: mood,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.White else TextInk,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
        } else if (shelves.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(S.homeEmpty, color = TextInk.copy(alpha = 0.7f))
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, primaryColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = CardDark, contentColor = TextInk)
                    ) {
                        Text(S.retry, color = TextInk, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            shelves.forEach { shelf ->
                item(key = "h_${shelf.title}") {
                    val canPlayAll = shelf.tracks.any { it.browseId.isBlank() }
                    ShelfHeader(
                        title = shelf.title,
                        showPlayAll = canPlayAll,
                        onPlayAll = { onPlay(shelf.tracks.filter { it.browseId.isBlank() }, 0) }
                    )
                }
                if (shelf.isList) {
                    itemsIndexed(shelf.tracks.take(4), key = { _, t -> "${shelf.title}_${t.id}" }) { idx, track ->
                        TrackRow(
                            track = track,
                            isLiked = track.id in likedIds,
                            isDownloaded = track.id in downloadedIds,
                            isDownloading = track.id in downloadingIds,
                            primaryColor = primaryColor,
                            onClick = { onPlay(shelf.tracks, idx) },
                            onToggleLike = { onToggleLike(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onDownload = { onDownload(track) },
                            onSaveToPlaylist = { onSaveToPlaylist(track) },
                        )
                    }
                } else {
                    item(key = "r_${shelf.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(shelf.tracks, key = { idx, t -> "${shelf.title}_${idx}_${t.id}" }) { idx, track ->
                                OverlayTrackCard(track = track, onClick = { onPlay(shelf.tracks, idx) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTrackCard(track: Track, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardDark)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.86f)
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        ) {
            Text(track.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, fontSize = 11.sp, color = TextInk.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ShelfHeader(title: String, showPlayAll: Boolean, onPlayAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (showPlayAll) {
            Surface(
                color = TextInk.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onPlayAll)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.playAll, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private val BROWSE_CATEGORIES = listOf(
    "Pop" to listOf(0xFFEC4899L, 0xFFF43F5EL),
    "Dangdut" to listOf(0xFFF59E0BL, 0xFFEA580CL),
    "Hip-Hop" to listOf(0xFF8B5CF6L, 0xFF6366F1L),
    "Rock" to listOf(0xFFEF4444L, 0xFF991B1BL),
    "Indie" to listOf(0xFF10B981L, 0xFF059669L),
    "R&B / Soul" to listOf(0xFF06B6D4L, 0xFF2563EBL),
    "Jazz" to listOf(0xFFD97706L, 0xFF78350FL),
    "EDM / Electronic" to listOf(0xFF3B82F6L, 0xFF1D4ED8L),
    "K-Pop" to listOf(0xFFA855F7L, 0xFFEC4899L),
    "J-Pop" to listOf(0xFFF43F5EL, 0xFFFB7185L),
    "Acoustic / Folk" to listOf(0xFF84CC16L, 0xFF15803DL),
    "Lo-Fi & Chill" to listOf(0xFF6366F1L, 0xFF312E81L),
    "Metal" to listOf(0xFF64748BL, 0xFF1E293BL),
    "Classical" to listOf(0xFFEAB308L, 0xFFA16207L),
    "Reggae" to listOf(0xFF22C55EL, 0xFFCA8A04L),
    "Latin" to listOf(0xFFF97316L, 0xFFDC2626L),
    "Anime / OST" to listOf(0xFF14B8A6L, 0xFF0F766EL),
    "Religi / Sholawat" to listOf(0xFF0EA5E9L, 0xFF0369A1L),
)

@Composable
private fun ExploreScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Track>,
    searchHistory: List<String>,
    recentTracks: List<Track>,
    topSongs: List<Pair<Track, Int>>,
    topArtists: List<Pair<String, Int>>,
    followedArtists: List<String>,
    artistImages: Map<String, String>,
    isSearching: Boolean,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    glowColor: Color,
    onPlay: (List<Track>, Int) -> Unit,
    onPlaySearchResult: (Track) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onOpenIdentify: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(S.exploreSearch, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextInk)
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, primaryColor, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenIdentify)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.identifySong, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextInk)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(S.searchPlaceholder) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = primaryColor,
                unfocusedBorderColor = TextInk.copy(alpha = 0.18f),
                cursorColor = primaryColor,
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (query.isBlank()) {
            val favArtists = remember(topArtists, followedArtists) {
                (topArtists.map { it.first } + followedArtists).distinct().take(12)
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchHistory.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Search History", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextInk)
                                TextButton(onClick = onClearHistory) {
                                    Text("Clear", fontSize = 12.sp, color = primaryColor)
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchHistory) { hist ->
                                    Surface(
                                        color = CardDark,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onQueryChange(hist) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.History, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                            Text(hist, fontSize = 12.sp, color = TextInk)
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = TextInk.copy(alpha = 0.55f),
                                                modifier = Modifier.size(15.dp).clickable { onRemoveHistory(hist) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (recentTracks.isNotEmpty()) {
                    item { ShelfHeader("Recently Played", showPlayAll = true, onPlayAll = { onPlay(recentTracks, 0) }) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(recentTracks.take(12)) { idx, t ->
                                OverlayTrackCard(track = t, onClick = { onPlay(recentTracks, idx) })
                            }
                        }
                    }
                }

                if (topSongs.isNotEmpty()) {
                    val mostList = topSongs.map { it.first }
                    item { ShelfHeader("Your Most Played", showPlayAll = true, onPlayAll = { onPlay(mostList, 0) }) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(topSongs.take(12)) { idx, (t, count) ->
                                Column(
                                    modifier = Modifier.width(136.dp).clip(RoundedCornerShape(14.dp)).clickable { onPlay(mostList, idx) },
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    AsyncImage(model = t.artworkUrl, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.size(136.dp).clip(RoundedCornerShape(16.dp)))
                                    Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("$count ${S.playsUnit} \u2022 ${t.artist}", fontSize = 11.sp, color = TextInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                if (favArtists.isNotEmpty()) {
                    item {
                        Text("Your Favorite Artists", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(favArtists) { artistName ->
                                val img = artistImages[artistName] ?: recentTracks.firstOrNull { it.artist.contains(artistName, ignoreCase = true) && it.artworkUrl.isNotBlank() }?.artworkUrl
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(84.dp).clip(RoundedCornerShape(14.dp)).clickable { onOpenArtist(artistName) }.padding(vertical = 4.dp)
                                ) {
                                    if (!img.isNullOrBlank()) {
                                        AsyncImage(model = img, contentDescription = artistName, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(CircleShape))
                                    } else {
                                        Box(
                                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Brush.linearGradient(listOf(primaryColor, glowColor))),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(artistName.take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color.White)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(artistName, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Browse All", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                items(BROWSE_CATEGORIES.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { (cat, colors) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Brush.linearGradient(listOf(Color(colors[0]), Color(colors[1]), primaryColor.copy(alpha = 0.35f))))
                                    .clickable { onQueryChange(cat) }
                                    .padding(14.dp)
                            ) {
                                Text(cat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, modifier = Modifier.align(Alignment.BottomStart))
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = primaryColor)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                itemsIndexed(results) { _, t ->
                    TrackRow(
                        track = t,
                        isLiked = t.id in likedIds,
                        isDownloaded = t.id in downloadedIds,
                        isDownloading = t.id in downloadingIds,
                        primaryColor = primaryColor,
                        onClick = { onPlaySearchResult(t) },
                        onToggleLike = { onToggleLike(t) },
                        onAddToQueue = { onAddToQueue(t) },
                        onDownload = { onDownload(t) },
                        onSaveToPlaylist = { onSaveToPlaylist(t) },
                    )
                }
            }
        }
    }
}

private enum class LibTab { ALL, PLAYLISTS, SONGS, ARTISTS }
private fun libTabLabel(t: LibTab): String = when (t) {
    LibTab.ALL -> S.all
    LibTab.PLAYLISTS -> S.playlists
    LibTab.SONGS -> S.songs
    LibTab.ARTISTS -> S.artists
}

private enum class LibSongSubTab { LIKED, RECENT, DOWNLOADS, LOCAL }
private fun libSubLabel(t: LibSongSubTab): String = when (t) {
    LibSongSubTab.LIKED -> S.liked
    LibSongSubTab.RECENT -> S.recentPlayed
    LibSongSubTab.DOWNLOADS -> S.downloaded
    LibSongSubTab.LOCAL -> S.localFiles
}

@Composable
private fun LibraryScreen(
    liked: List<Track>,
    recent: List<Track>,
    downloaded: List<Track>,
    localTracks: List<Track>,
    playlists: List<UserPlaylist>,
    followedArtists: List<String>,
    artistImages: Map<String, String>,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onDeleteDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onCreatePlaylist: () -> Unit,
    onImportPlaylist: () -> Unit,
    onImportDevice: () -> Unit,
    onOpenPlaylist: (UserPlaylist) -> Unit,
    onDeletePlaylist: (UserPlaylist) -> Unit,
    onOpenArtist: (String) -> Unit,
    onUnfollowArtist: (String) -> Unit,
    onOpenExplore: () -> Unit,
) {
    var tab by remember { mutableStateOf(LibTab.ALL) }
    var songSubTab by remember { mutableStateOf(LibSongSubTab.LIKED) }
    var isGrid by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }

    val q = filterQuery.trim().lowercase()
    val filteredPlaylists = remember(playlists, q) {
        if (q.isEmpty()) playlists else playlists.filter { it.name.lowercase().contains(q) }
    }
    val filteredArtists = remember(followedArtists, q) {
        if (q.isEmpty()) followedArtists else followedArtists.filter { it.lowercase().contains(q) }
    }
    val rawActiveTracks = when (songSubTab) {
        LibSongSubTab.LIKED -> liked
        LibSongSubTab.RECENT -> recent
        LibSongSubTab.DOWNLOADS -> downloaded
        LibSongSubTab.LOCAL -> localTracks
    }
    val activeTracks = remember(rawActiveTracks, q) {
        if (q.isEmpty()) rawActiveTracks else rawActiveTracks.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
        // Header: Pustaka + Search + Impor + Buat (+)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(S.library, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextInk)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) filterQuery = "" }) {
                    Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search, contentDescription = S.searchLibrary, tint = TextInk)
                }
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, primaryColor.copy(alpha = 0.85f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onImportPlaylist)
                ) {
                    Text(S.importLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                IconButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Default.Add, contentDescription = "Buat Playlist", tint = TextInk)
                }
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                placeholder = { Text("Cari di pustaka...", color = TextInk.copy(alpha = 0.5f)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = TextInk.copy(alpha = 0.2f),
                    focusedTextColor = TextInk,
                    unfocusedTextColor = TextInk,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        // Filter Chips + Grid/List Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(LibTab.ALL, LibTab.PLAYLISTS, LibTab.SONGS, LibTab.ARTISTS).forEach { lt ->
                    FilterChip(
                        selected = tab == lt,
                        onClick = { tab = lt },
                        label = { Text(libTabLabel(lt), fontSize = 13.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor,
                            selectedLabelColor = Color.White,
                            containerColor = CardDark,
                            labelColor = TextInk,
                        )
                    )
                }
            }
            if (tab != LibTab.SONGS) {
                IconButton(onClick = { isGrid = !isGrid }) {
                    Icon(
                        if (isGrid) Icons.AutoMirrored.Filled.FormatListBulleted else Icons.Default.GridView,
                        contentDescription = "Ubah Tampilan",
                        tint = TextInk.copy(alpha = 0.75f)
                    )
                }
            }
        }

        when (tab) {
            LibTab.ALL -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Playlists (2-column grid or list)
                    if (filteredPlaylists.isNotEmpty()) {
                        item {
                            Text("Playlist", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextInk)
                        }
                        if (isGrid) {
                            items(filteredPlaylists.chunked(2)) { pair ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { pl ->
                                        PlaylistGridCard(pl = pl, primaryColor = primaryColor, modifier = Modifier.weight(1f), onOpen = { onOpenPlaylist(pl) })
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(filteredPlaylists) { pl ->
                                PlaylistListRow(pl = pl, primaryColor = primaryColor, onOpen = { onOpenPlaylist(pl) }, onDelete = { onDeletePlaylist(pl) })
                            }
                        }
                    }

                    // Collections (flat Dribbble-style rows, no card/outline clutter)
                    item {
                        Text(S.collections, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextInk, modifier = Modifier.padding(top = 4.dp))
                    }
                    item {
                        CollectionRow(Icons.Default.Favorite, S.liked, "${liked.size} ${S.songUnit}", primaryColor) {
                            songSubTab = LibSongSubTab.LIKED; tab = LibTab.SONGS
                        }
                    }
                    item {
                        CollectionRow(Icons.Default.DownloadDone, S.downloaded, "${downloaded.size} ${S.songUnit}", primaryColor) {
                            songSubTab = LibSongSubTab.DOWNLOADS; tab = LibTab.SONGS
                        }
                    }
                    item {
                        CollectionRow(Icons.Default.TrendingUp, S.recentPlayed, "${recent.size} ${S.songUnit}", primaryColor) {
                            songSubTab = LibSongSubTab.RECENT; tab = LibTab.SONGS
                        }
                    }
                    item {
                        CollectionRow(Icons.Default.FolderOpen, S.localFiles, "${localTracks.size} ${S.songUnit} \u2022 FLAC / MP3 / WAV", primaryColor) {
                            songSubTab = LibSongSubTab.LOCAL; tab = LibTab.SONGS
                        }
                    }
                    item {
                        CollectionRow(Icons.Default.FileOpen, S.importDevice, "FLAC, MP3, WAV, M4A, OGG", primaryColor, onClick = onImportDevice)
                    }

                    // Followed Artists
                    item {
                        Text(S.artists, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextInk, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (filteredArtists.isEmpty()) {
                        item {
                            AddArtistRow(primaryColor = primaryColor, onClick = onOpenExplore)
                        }
                    } else if (isGrid) {
                        items(filteredArtists.chunked(2)) { pair ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { artistName ->
                                    ArtistGridCard(name = artistName, imageUrl = artistImages[artistName], primaryColor = primaryColor, modifier = Modifier.weight(1f), onClick = { onOpenArtist(artistName) })
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(filteredArtists) { artistName ->
                            ArtistListRow(name = artistName, imageUrl = artistImages[artistName], primaryColor = primaryColor, onOpen = { onOpenArtist(artistName) }, onUnfollow = { onUnfollowArtist(artistName) })
                        }
                    }
                }
            }

            LibTab.PLAYLISTS -> {
                if (filteredPlaylists.isEmpty()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Belum ada playlist.", color = TextInk.copy(alpha = 0.65f))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(color = primaryColor, shape = RoundedCornerShape(50), modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onCreatePlaylist)) {
                                Text("+ Buat Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                            }
                            Surface(color = CardDark, shape = RoundedCornerShape(50), modifier = Modifier.border(1.dp, primaryColor, RoundedCornerShape(50)).clip(RoundedCornerShape(50)).clickable(onClick = onImportPlaylist)) {
                                Text("Impor Spotify/YT", color = TextInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp))
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (isGrid) {
                            items(filteredPlaylists.chunked(2)) { pair ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    pair.forEach { pl ->
                                        PlaylistGridCard(pl = pl, primaryColor = primaryColor, modifier = Modifier.weight(1f), onOpen = { onOpenPlaylist(pl) })
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(filteredPlaylists) { pl ->
                                PlaylistListRow(pl = pl, primaryColor = primaryColor, onOpen = { onOpenPlaylist(pl) }, onDelete = { onDeletePlaylist(pl) })
                            }
                        }
                    }
                }
            }

            LibTab.SONGS -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(listOf(LibSongSubTab.LIKED, LibSongSubTab.RECENT, LibSongSubTab.DOWNLOADS, LibSongSubTab.LOCAL)) { st ->
                            val selected = songSubTab == st
                            Surface(
                                color = if (selected) primaryColor.copy(alpha = 0.22f) else CardDark,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { songSubTab = st }
                            ) {
                                Text(
                                    libSubLabel(st),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextInk,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    if (activeTracks.isNotEmpty()) {
                        Surface(
                            color = primaryColor,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onPlay(activeTracks, 0) }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(S.playAll, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
                if (activeTracks.isEmpty()) {
                    Text(
                        when (songSubTab) {
                            LibSongSubTab.LIKED -> S.liked
                            LibSongSubTab.RECENT -> S.recentPlayed
                            LibSongSubTab.DOWNLOADS -> S.downloaded
                            LibSongSubTab.LOCAL -> S.localFiles
                        },
                        color = TextInk.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn {
                        itemsIndexed(activeTracks) { idx, t ->
                            TrackRow(
                                track = t,
                                isLiked = t.id in likedIds,
                                isDownloaded = t.id in downloadedIds,
                                isDownloading = t.id in downloadingIds,
                                primaryColor = primaryColor,
                                onClick = { onPlay(activeTracks, idx) },
                                onToggleLike = { onToggleLike(t) },
                                onAddToQueue = { onAddToQueue(t) },
                                onDownload = { if (songSubTab == LibSongSubTab.DOWNLOADS) onDeleteDownload(t) else onDownload(t) },
                                onSaveToPlaylist = { onSaveToPlaylist(t) },
                            )
                        }
                    }
                }
            }

            LibTab.ARTISTS -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isGrid && filteredArtists.isNotEmpty()) {
                        items(filteredArtists.chunked(2)) { pair ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { artistName ->
                                    ArtistGridCard(name = artistName, imageUrl = artistImages[artistName], primaryColor = primaryColor, modifier = Modifier.weight(1f), onClick = { onOpenArtist(artistName) })
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        items(filteredArtists) { artistName ->
                            ArtistListRow(name = artistName, imageUrl = artistImages[artistName], primaryColor = primaryColor, onOpen = { onOpenArtist(artistName) }, onUnfollow = { onUnfollowArtist(artistName) })
                        }
                    }
                    item {
                        AddArtistRow(primaryColor = primaryColor, onClick = onOpenExplore)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridCard(pl: UserPlaylist, primaryColor: Color, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val coverUrl = pl.tracks.firstOrNull()?.artworkUrl
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(model = coverUrl, contentDescription = pl.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(primaryColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = primaryColor, modifier = Modifier.size(42.dp))
                }
            }
        }
        Text(pl.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${pl.tracks.size} lagu", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
    }
}

@Composable
private fun PlaylistListRow(pl: UserPlaylist, primaryColor: Color, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val coverUrl = pl.tracks.firstOrNull()?.artworkUrl
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(model = coverUrl, contentDescription = pl.name, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(primaryColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = primaryColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pl.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                Text("${pl.tracks.size} lagu", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Hapus", tint = TextInk.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun CollectionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, primaryColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(primaryColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk)
            Text(subtitle, fontSize = 12.sp, color = TextInk.copy(alpha = 0.58f))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.45f))
    }
}

@Composable
private fun ArtistGridCard(name: String, imageUrl: String?, primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = primaryColor.copy(alpha = 0.14f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                }
            }
        }
        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Artis", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
    }
}

@Composable
private fun ArtistListRow(name: String, imageUrl: String?, primaryColor: Color, onOpen: () -> Unit, onUnfollow: () -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(model = imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(CircleShape))
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                Text("Artis", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
            }
            TextButton(onClick = onUnfollow, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) {
                Text(S.following, fontSize = 12.sp, color = primaryColor)
            }
        }
    }
}

@Composable
private fun AddArtistRow(primaryColor: Color, onClick: () -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(TextInk.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = primaryColor)
            }
            Spacer(Modifier.width(12.dp))
            Text(S.addArtist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk)
        }
    }
}

@Composable
private fun StatsScreen(
    streakDays: Int,
    totalPlays: Int,
    totalMinutes: Int,
    likedCount: Int,
    recentTracks: List<Track>,
    topSongs: List<Pair<Track, Int>>,
    topArtists: List<Pair<String, Int>>,
    artistImages: Map<String, String>,
    primaryColor: Color,
    onOpenRecap: () -> Unit,
    onOpenFriendsStats: () -> Unit,
    onPlaySongs: (List<Track>, Int) -> Unit,
    onOpenArtist: (String) -> Unit,
) {
    var timeframe by remember { mutableStateOf("1w") }
    val nextGoal = when {
        streakDays < 7 -> 7
        streakDays < 30 -> 30
        else -> ((streakDays / 30) + 1) * 30
    }
    val progress = (streakDays.toFloat() / nextGoal.toFloat()).coerceIn(0.08f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text(S.statsTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextInk) }

        // Recap Row (clean Material 3 flat row)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenRecap)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.recapTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                    Text(S.recapSub, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
            }
        }

        // Flame / Streak Highlight Card (clean, borderless)
        item {
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(primaryColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = primaryColor, modifier = Modifier.size(38.dp))
                        }
                        Column {
                            Text("$streakDays", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = TextInk)
                            Text(S.daysInARow, fontSize = 14.sp, color = TextInk.copy(alpha = 0.7f))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(S.nextFlame, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
                        LinearProgressIndicator(
                            progress = { progress },
                            color = primaryColor,
                            trackColor = TextInk.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50))
                        )
                    }
                    Text(
                        "$totalPlays ${S.totalPlays} \u2022 $totalMinutes ${S.minUnit} \u2022 $likedCount ${S.liked}",
                        fontSize = 12.sp,
                        color = TextInk.copy(alpha = 0.65f)
                    )
                }
            }
        }

        // Top artists with friends (clean flat row)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenFriendsStats)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Top artists with friends", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                    Text("Discover what your friends are listening to", fontSize = 12.sp, color = TextInk.copy(alpha = 0.62f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
            }
        }

        // Timeframe chips
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = listOf("1w" to "1 week", "1m" to "1 month", "3m" to "3 months", "all" to "All time")
                items(chips) { (id, label) ->
                    val selected = timeframe == id
                    val chipBg by androidx.compose.animation.animateColorAsState(
                        targetValue = if (selected) primaryColor else CardDark,
                        animationSpec = tween(180),
                        label = "statChipBg"
                    )
                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { timeframe = id }
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
        }

        // Top Songs horizontal carousel sorted by real play count
        val rankedSongs = if (topSongs.isNotEmpty()) topSongs else recentTracks.map { it to 0 }
        val rankedTrackList = rankedSongs.map { it.first }
        if (rankedSongs.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${rankedSongs.size} ${S.topSongsSection}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextInk)
                    TextButton(onClick = { onPlaySongs(rankedTrackList, 0) }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) {
                        Text(S.playAll, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = primaryColor)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(rankedSongs.take(12)) { idx, (track, plays) ->
                        Column(
                            modifier = Modifier
                                .width(136.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPlaySongs(rankedTrackList, idx) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AsyncImage(
                                model = track.artworkUrl,
                                contentDescription = track.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(136.dp).clip(RoundedCornerShape(16.dp)).background(CardDark)
                            )
                            Text("${idx + 1}. ${track.title}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (plays > 0) "$plays ${S.playsUnit} \u2022 ${track.artist}" else track.artist, fontSize = 11.sp, color = TextInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Top Artists horizontal carousel (matching reference)
        if (topArtists.isNotEmpty()) {
            item {
                Text("${topArtists.size} ${S.topArtists}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(topArtists) { idx, (name, count) ->
                        val imgUrl = artistImages[name]
                            ?: recentTracks.firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }?.artworkUrl
                        Column(
                            modifier = Modifier
                                .width(136.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenArtist(name) },
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = primaryColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(136.dp)
                            ) {
                                if (!imgUrl.isNullOrBlank()) {
                                    AsyncImage(model = imgUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(name.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = primaryColor)
                                    }
                                }
                            }
                            Text("${idx + 1}. $name", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count ${S.playsUnit}", fontSize = 11.sp, color = TextInk.copy(alpha = 0.65f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier, primaryColor: Color) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, TextInk.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = primaryColor)
            Text(label, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
        }
    }
}

@Composable
private fun HistoryFullScreenOverlay(
    tracks: List<Track>,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("newest") }
    var sortExpanded by remember { mutableStateOf(false) }

    val sorted = remember(tracks, query, sortMode) {
        val base = when (sortMode) {
            "oldest" -> tracks.reversed()
            "title" -> tracks.sortedBy { it.title.lowercase() }
            "artist" -> tracks.sortedBy { it.artist.lowercase() }
            else -> tracks
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) base else base.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = TextInk) }
                Text(S.recentPlayed, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.weight(1f))
                if (sorted.isNotEmpty()) {
                    TextButton(onClick = onClearHistory, colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF7A8A))) {
                        Text(S.clearBtn, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(onClick = { onPlay(sorted, 0) }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) {
                        Text(S.playAll, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search history...", color = TextInk.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextInk.copy(alpha = 0.6f)) },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryColor,
                    unfocusedBorderColor = TextInk.copy(alpha = 0.14f),
                    focusedTextColor = TextInk,
                    unfocusedTextColor = TextInk,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val sortLabel = when (sortMode) {
                    "oldest" -> "Oldest"
                    "title" -> "Title"
                    "artist" -> "Artist"
                    else -> "Newest"
                }
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { sortExpanded = true }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sortLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextInk)
                    }
                }
                DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }, containerColor = CardDark) {
                    listOf("newest" to "Newest", "oldest" to "Oldest", "title" to "Title", "artist" to "Artist").forEach { (id, lbl) ->
                        DropdownMenuItem(
                            text = { Text(lbl, color = if (sortMode == id) primaryColor else TextInk) },
                            onClick = { sortMode = id; sortExpanded = false }
                        )
                    }
                }
            }
            if (sorted.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(S.recentPlayed, color = TextInk.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(sorted) { idx, t ->
                        TrackRow(
                            track = t,
                            isLiked = t.id in likedIds,
                            isDownloaded = t.id in downloadedIds,
                            isDownloading = t.id in downloadingIds,
                            primaryColor = primaryColor,
                            onClick = { onPlay(sorted, idx) },
                            onToggleLike = { onToggleLike(t) },
                            onAddToQueue = { onAddToQueue(t) },
                            onDownload = { onDownload(t) },
                            onSaveToPlaylist = { onSaveToPlaylist(t) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopArtistsWithFriendsOverlay(
    topArtists: List<Pair<String, Int>>,
    artistImages: Map<String, String>,
    recentTracks: List<Track>,
    listenRoomCode: String?,
    primaryColor: Color,
    onOpenListenTogether: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = TextInk) }
                    Text("Top artists with friends", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextInk)
                }
            }
            item {
                Text(
                    "Discover what your friends are listening to.",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 32.sp,
                    color = TextInk
                )
            }
            item {
                Surface(
                    color = CardDark,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, TextInk.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listening activity", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                            Text(
                                if (listenRoomCode != null) "Room active: $listenRoomCode" else "See how your listening compares with friends.",
                                fontSize = 12.sp,
                                color = TextInk.copy(alpha = 0.65f)
                            )
                        }
                        Surface(
                            color = primaryColor,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpenListenTogether)
                        ) {
                            Text(
                                if (listenRoomCode != null) "Active" else "Turn on",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
            if (listenRoomCode == null) {
                item {
                    Surface(
                        color = CardDark,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, TextInk.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No friend activity yet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                            Text(
                                "You haven't started or joined a Listen Together session yet. Tap Turn on above to listen with friends.",
                                fontSize = 13.sp,
                                color = TextInk.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(topArtists.take(10)) { idx, (name, count) ->
                    val imgUrl = artistImages[name]
                        ?: recentTracks.firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }?.artworkUrl
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenArtist(name) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk.copy(alpha = 0.6f), modifier = Modifier.width(20.dp))
                        if (!imgUrl.isNullOrBlank()) {
                            AsyncImage(model = imgUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(52.dp).clip(CircleShape))
                        } else {
                            Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = primaryColor)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
                            Text("$count ${S.playsUnit}", fontSize = 12.sp, color = TextInk.copy(alpha = 0.62f))
                        }
                        Icon(Icons.Default.Group, contentDescription = null, tint = TextInk.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecapFullScreenOverlay(
    totalMinutes: Int,
    recentTracks: List<Track>,
    topSongs: List<Pair<Track, Int>>,
    topArtists: List<Pair<String, Int>>,
    artistImages: Map<String, String>,
    primaryColor: Color,
    onClose: () -> Unit,
) {
    var slide by remember { mutableIntStateOf(0) }
    val totalSlides = 6
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val rankedSongs = if (topSongs.isNotEmpty()) topSongs else recentTracks.map { it to 0 }

    val gradTop = when (slide) {
        0 -> primaryColor.copy(alpha = 0.90f)
        1 -> primaryColor.copy(alpha = 0.76f)
        2 -> primaryColor.copy(alpha = 0.64f)
        3 -> primaryColor.copy(alpha = 0.84f)
        4 -> primaryColor.copy(alpha = 0.52f)
        else -> primaryColor.copy(alpha = 0.88f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .background(Brush.verticalGradient(listOf(gradTop, BgDark)))
            .pointerInput(slide) {
                detectTapGestures { offset ->
                    if (offset.x < size.width * 0.35f) {
                        if (slide > 0) slide--
                    } else {
                        if (slide < totalSlides - 1) slide++
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Segmented Story progress bars
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(totalSlides) { idx ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (idx <= slide) Color.White else Color.White.copy(alpha = 0.28f))
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painter = painterResource(id = R.drawable.swara_splash_logo), contentDescription = null, modifier = Modifier.size(22.dp))
                    Text(S.recapTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f))) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedContent(
                targetState = slide,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it / 4 } + fadeOut(tween(180)))
                },
                label = "recapSlide",
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { s ->
                when (s) {
                    0 -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text(S.recapTitle, fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(10.dp))
                        Text("You listened for", fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f))
                        Text("$totalMinutes", fontSize = 72.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif)
                        Text(S.minUnit, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("${recentTracks.size} ${S.songs} \u2022 ${topArtists.size} ${S.artists}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    1 -> {
                        val topSongEntry = rankedSongs.firstOrNull()
                        val topSong = topSongEntry?.first
                        val topSongPlays = topSongEntry?.second ?: 0
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Your top song", fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Serif)
                            Spacer(Modifier.height(20.dp))
                            if (topSong != null) {
                                AsyncImage(model = topSong.artworkUrl, contentDescription = topSong.title, contentScale = ContentScale.Crop, modifier = Modifier.size(220.dp).clip(RoundedCornerShape(22.dp)))
                                Spacer(Modifier.height(20.dp))
                                Text(topSong.title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (topSongPlays > 0) "${topSong.artist} \u2022 $topSongPlays ${S.playsUnit}" else topSong.artist, fontSize = 15.sp, color = Color.White.copy(alpha = 0.78f))
                            }
                        }
                    }
                    2 -> {
                        val topArt = topArtists.firstOrNull()
                        val artImg = topArt?.first?.let { name ->
                            artistImages[name] ?: recentTracks.firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }?.artworkUrl
                        }
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Your top artist", fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f), fontFamily = FontFamily.Serif)
                            Spacer(Modifier.height(20.dp))
                            if (!artImg.isNullOrBlank()) {
                                AsyncImage(model = artImg, contentDescription = topArt?.first, contentScale = ContentScale.Crop, modifier = Modifier.size(210.dp).clip(CircleShape).border(3.dp, Color.White, CircleShape))
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(topArt?.first ?: "Swara", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            if (topArt != null) {
                                Text("${topArt.second} ${S.playsUnit}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.78f))
                            }
                        }
                    }
                    3 -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("Your top 5 songs", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(18.dp))
                        rankedSongs.take(5).forEachIndexed { idx, (t, plays) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.width(20.dp))
                                AsyncImage(model = t.artworkUrl, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(t.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (plays > 0) "$plays ${S.playsUnit} \u2022 ${t.artist}" else t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    4 -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        Text("Your top 5 artists", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(18.dp))
                        topArtists.take(5).forEachIndexed { idx, (name, count) ->
                            val imgUrl = artistImages[name] ?: recentTracks.firstOrNull { it.artist.contains(name, ignoreCase = true) && it.artworkUrl.isNotBlank() }?.artworkUrl
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${idx + 1}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFF7B538), modifier = Modifier.width(20.dp))
                                if (!imgUrl.isNullOrBlank()) {
                                    AsyncImage(model = imgUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(CircleShape))
                                } else {
                                    Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                        Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                                    Text("$count ${S.playsUnit}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.72f))
                                }
                            }
                        }
                    }
                    else -> Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painter = painterResource(id = R.drawable.swara_splash_logo), contentDescription = "Swara", modifier = Modifier.size(72.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Swara", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(18.dp))
                        Text("That's your Recap", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif)
                        Text("Thanks for listening with Swara.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.78f))
                        Spacer(Modifier.height(26.dp))
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                shareRecapStoryCard(
                                    context = ctx,
                                    totalMinutes = totalMinutes,
                                    topArtists = topArtists,
                                    topTracks = rankedSongs.map { it.first },
                                    accentColor = primaryColor,
                                )
                            }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 24.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0D1524), modifier = Modifier.size(18.dp))
                                Text("Share your Recap", color = Color(0xFF0D1524), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareRecapStoryCard(
    context: android.content.Context,
    totalMinutes: Int,
    topArtists: List<Pair<String, Int>>,
    topTracks: List<Track>,
    accentColor: Color,
) {
    val w = 1080
    val h = 1920
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val bgPaint = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(accentColor.toArgb(), android.graphics.Color.parseColor("#0B0F17")),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(215, 255, 255, 255)
        textSize = 34f
        letterSpacing = 0.14f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val serifTitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 96f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    val bigNumPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 138f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val subPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(225, 255, 255, 255)
        textSize = 38f
    }

    c.drawText("SWARA RECAP", 88f, 190f, labelPaint)
    c.drawText("Your Recap 2026", 88f, 305f, serifTitlePaint)
    c.drawText("$totalMinutes", 88f, 455f, bigNumPaint)
    c.drawText("minutes listened  \u2022  ${topTracks.size} songs", 88f, 520f, subPaint)

    // Translucent frosted glass panel (matching reference screenshot)
    val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(46, 255, 255, 255)
    }
    val panelRect = android.graphics.RectF(72f, 585f, w - 72f, 1690f)
    c.drawRoundRect(panelRect, 48f, 48f, glassPaint)

    val secHeadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 42f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val rankPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#F7B538")
        textSize = 36f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val itemTitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
    }
    val itemSubPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(190, 255, 255, 255)
        textSize = 28f
    }

    c.drawText("Top songs", 118f, 665f, secHeadPaint)
    topTracks.take(5).forEachIndexed { idx, t ->
        val y = 745f + idx * 105f
        c.drawText("${idx + 1}", 120f, y, rankPaint)
        c.drawText(t.title.take(30), 178f, y - 8f, itemTitlePaint)
        c.drawText(t.artist.take(36), 178f, y + 30f, itemSubPaint)
    }

    c.drawText("Top artists", 118f, 1295f, secHeadPaint)
    topArtists.take(5).forEachIndexed { idx, (name, count) ->
        val y = 1370f + idx * 62f
        c.drawText("${idx + 1}", 120f, y, rankPaint)
        c.drawText(name.take(28), 178f, y, itemTitlePaint)
        c.drawText("$count plays", w - 260f, y, itemSubPaint)
    }

    c.drawText("Swara", 88f, 1800f, secHeadPaint)

    val outFile = java.io.File(context.cacheDir, "swara_recap_story.png")
    outFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, it) }
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Recap Story"))
}

@Composable
private fun SettingsScreen(
    themeMode: String,
    audioQuality: String,
    accentId: String,
    customAccentHex: Long,
    country: String,
    language: String,
    dynamicIslandEnabled: Boolean,
    discordRpcEnabled: Boolean,
    listenRoomCode: String?,
    listenStatus: String?,
    sleepRemainingSec: Int,
    currentTrack: Track?,
    primaryColor: Color,
    onSelectThemeMode: (String) -> Unit,
    onSelectAudioQuality: (String) -> Unit,
    onSelectAccent: (String) -> Unit,
    onSelectCustomAccent: (Long) -> Unit,
    onSelectCountry: (String) -> Unit,
    onSelectLanguage: (String) -> Unit,
    onToggleDynamicIsland: (Boolean) -> Unit,
    onOpenDiscord: () -> Unit,
    onOpenListenTogether: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSoundStyle: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasOverlay = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    var langExpanded by remember { mutableStateOf(false) }
    var countryExpanded by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf("appearance") }

    val currentLangLabel = SUPPORTED_LANGUAGES.find { it.first == language }?.second ?: language
    val currentCountryLabel = SUPPORTED_COUNTRIES.find { it.first == country }?.let { "${it.first} \u2022 ${it.second}" } ?: country

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(S.settingsTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(bottom = 6.dp))

        // 1. Appearance (Theme, Accent, Dynamic Island) - flat Material 3 accordion, zero cards
        SettingsGroupHeader(
            icon = Icons.Default.Palette,
            title = S.appearanceMode,
            subtitle = "${themeMode.uppercase()} \u2022 ${BUILTIN_ACCENTS.find { it.id == accentId }?.name ?: S.customColor}",
            expanded = expandedGroup == "appearance",
            primaryColor = primaryColor,
            onClick = { expandedGroup = if (expandedGroup == "appearance") "" else "appearance" }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        ) {
            if (expandedGroup == "appearance") {
                Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("dark" to S.dark, "light" to S.light, "amoled" to "AMOLED").forEach { (id, label) ->
                            val selected = themeMode == id
                            val bg by androidx.compose.animation.animateColorAsState(if (selected) primaryColor else TextInk.copy(alpha = 0.08f), tween(180), label = "tmBg")
                            Surface(
                                color = bg,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { onSelectThemeMode(id) }
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(BUILTIN_ACCENTS) { acc ->
                            val selected = acc.id == accentId
                            Surface(
                                color = if (selected) Color(acc.primaryHex) else TextInk.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelectAccent(acc.id) }
                            ) {
                                Text(acc.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                        item {
                            val selected = accentId == "custom"
                            Surface(
                                color = if (selected) Color(customAccentHex) else TextInk.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showColorPicker = true }
                            ) {
                                Text(S.customColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(S.dynamicIsland, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                            Text(if (!hasOverlay) S.dynamicIslandHintOff else S.dynamicIslandHintOn, fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = dynamicIslandEnabled && hasOverlay,
                            onCheckedChange = { checked ->
                                hasOverlay = Settings.canDrawOverlays(context)
                                if (checked && !hasOverlay) {
                                    onToggleDynamicIsland(true)
                                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } else {
                                    onToggleDynamicIsland(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))

        // 2. Audio & Playback
        SettingsGroupHeader(
            icon = Icons.Default.GraphicEq,
            title = S.audioPlaybackTitle,
            subtitle = "${audioQuality.uppercase()} \u2022 ${S.soundStyleBtn} \u2022 ${S.eqBtn}",
            expanded = expandedGroup == "audio",
            primaryColor = primaryColor,
            onClick = { expandedGroup = if (expandedGroup == "audio") "" else "audio" }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        ) {
            if (expandedGroup == "audio") {
                Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("high" to "Ultra", "medium" to "Normal", "low" to S.low).forEach { (id, label) ->
                            val selected = audioQuality == id
                            Surface(
                                color = if (selected) primaryColor else TextInk.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { onSelectAudioQuality(id) }
                            ) {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    SettingsFlatRow(Icons.Default.Tune, S.soundStyleBtn, primaryColor, onClick = onOpenSoundStyle)
                    SettingsFlatRow(Icons.Default.GraphicEq, S.eqBtn, primaryColor, onClick = onOpenEq)
                    val sleepLabel = if (sleepRemainingSec > 0) "Sleep Timer: ${formatMs(sleepRemainingSec * 1000L)}" else S.sleepTimerOff
                    SettingsFlatRow(Icons.Default.Bedtime, sleepLabel, primaryColor, onClick = onCycleSleepTimer)
                }
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))

        // 3. Integrations (Discord Soon, Listen Together)
        val ltLabel = if (listenRoomCode != null) "${S.listenTogether}: ${listenStatus ?: listenRoomCode}" else S.listenTogether
        SettingsGroupHeader(
            icon = Icons.Default.Hub,
            title = S.integrationsTitle,
            subtitle = "Discord (Soon) \u2022 $ltLabel",
            expanded = expandedGroup == "integrations",
            primaryColor = primaryColor,
            onClick = { expandedGroup = if (expandedGroup == "integrations") "" else "integrations" }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        ) {
            if (expandedGroup == "integrations") {
                Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DiscordGlyphIcon(tint = primaryColor.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Discord", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk.copy(alpha = 0.65f))
                            Text("Profile activity \u00B7 Soon", fontSize = 12.sp, color = TextInk.copy(alpha = 0.45f))
                        }
                        Surface(color = primaryColor.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                            Text("Soon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    SettingsFlatRow(Icons.Default.Group, ltLabel, primaryColor, onClick = onOpenListenTogether)
                }
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))

        // 4. Language & Region
        SettingsGroupHeader(
            icon = Icons.Default.Language,
            title = "${S.language} & ${S.countryRegion}",
            subtitle = "$currentLangLabel \u2022 $currentCountryLabel",
            expanded = expandedGroup == "region",
            primaryColor = primaryColor,
            onClick = { expandedGroup = if (expandedGroup == "region") "" else "region" }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
        ) {
            if (expandedGroup == "region") {
                Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = TextInk.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { langExpanded = true }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${S.language}: $currentLangLabel", fontSize = 14.sp, color = TextInk, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextInk)
                            }
                        }
                        DropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }, containerColor = CardDark, modifier = Modifier.heightIn(max = 320.dp)) {
                            SUPPORTED_LANGUAGES.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, color = if (code == language) primaryColor else TextInk) },
                                    onClick = { langExpanded = false; onSelectLanguage(code) }
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = TextInk.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { countryExpanded = true }
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${S.countryRegion}: $currentCountryLabel", fontSize = 14.sp, color = TextInk, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextInk)
                            }
                        }
                        DropdownMenu(expanded = countryExpanded, onDismissRequest = { countryExpanded = false }, containerColor = CardDark, modifier = Modifier.heightIn(max = 320.dp)) {
                            SUPPORTED_COUNTRIES.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text("$code \u2022 $label", color = if (code == country) primaryColor else TextInk) },
                                    onClick = { countryExpanded = false; onSelectCountry(code) }
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))
        SettingsGroupHeader(
            icon = Icons.Default.Storage,
            title = S.storageTitle,
            subtitle = S.storageSubtitle,
            expanded = false,
            primaryColor = primaryColor,
            onClick = onOpenStorage
        )
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))
        SettingsGroupHeader(
            icon = Icons.Default.Info,
            title = S.aboutTitle,
            subtitle = S.aboutSubtitle,
            expanded = false,
            primaryColor = primaryColor,
            onClick = onOpenAbout
        )
        Spacer(Modifier.height(140.dp))
    }

    if (showColorPicker) {
        val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(customAccentHex.toInt(), it) } }
        var hue by remember { mutableFloatStateOf(hsv[0]) }
        var sat by remember { mutableFloatStateOf(hsv[1].coerceAtLeast(0.4f)) }
        var valBri by remember { mutableFloatStateOf(hsv[2].coerceAtLeast(0.6f)) }
        val previewArgb = remember(hue, sat, valBri) {
            android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, valBri)).toLong() and 0xFFFFFFFFL
        }
        val previewColor = Color(previewArgb)
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            containerColor = CardDark,
            titleContentColor = TextInk,
            textContentColor = TextInk,
            title = { Text("Pilih Warna Bebas", color = TextInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "#${(previewArgb and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    // Interactive RGB / HSV Color Wheel
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val r = minOf(cx, cy)
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtMost(r)
                                    val angle = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                                    hue = angle
                                    sat = (dist / r).coerceIn(0f, 1f)
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = change.position.x - cx
                                    val dy = change.position.y - cy
                                    val r = minOf(cx, cy)
                                    val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtMost(r)
                                    val angle = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 360.0) % 360.0).toFloat()
                                    hue = angle
                                    sat = (dist / r).coerceIn(0f, 1f)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val r = size.minDimension / 2f
                            drawCircle(
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color.Red, Color.Yellow, Color.Green,
                                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                    ),
                                    center = Offset(cx, cy)
                                ),
                                radius = r
                            )
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color.White, Color.Transparent),
                                    center = Offset(cx, cy),
                                    radius = r
                                ),
                                radius = r
                            )
                            val rad = Math.toRadians(hue.toDouble())
                            val px = cx + kotlin.math.cos(rad).toFloat() * sat * r
                            val py = cy + kotlin.math.sin(rad).toFloat() * sat * r
                            drawCircle(color = Color.White, radius = 11f, center = Offset(px, py), style = Stroke(width = 4f))
                            drawCircle(color = previewColor, radius = 8f, center = Offset(px, py))
                        }
                    }
                    Text("Kecerahan: ${(valBri * 100).toInt()}%", fontSize = 12.sp, color = TextInk)
                    Slider(value = valBri, onValueChange = { valBri = it }, valueRange = 0.35f..1f, colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = previewColor))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelectCustomAccent(previewArgb)
                        showColorPicker = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = previewColor)
                ) { Text("Terapkan", color = previewColor) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showColorPicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = TextInk)
                ) { Text("Batal", color = TextInk) }
            }
        )
    }
}

@Composable
private fun PlaylistSheetContent(
    title: String,
    tracks: List<Track>,
    isLoading: Boolean,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onRemoveTrack: ((Track) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                Surface(
                    color = primaryColor,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onPlay(tracks, 0) }
                ) {
                    Text(S.playAll, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(tracks) { idx, t ->
                    TrackRow(
                        track = t,
                        isLiked = t.id in likedIds,
                        isDownloaded = t.id in downloadedIds,
                        isDownloading = t.id in downloadingIds,
                        primaryColor = primaryColor,
                        onClick = { onPlay(tracks, idx) },
                        onToggleLike = { onToggleLike(t) },
                        onAddToQueue = { onAddToQueue(t) },
                        onDownload = { onDownload(t) },
                        onSaveToPlaylist = { onSaveToPlaylist(t) },
                    )
                    if (onRemoveTrack != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onRemoveTrack(t) }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove / Hide from playlist", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutOverlay(
    primaryColor: Color,
    glowColor: Color,
    onClose: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val pkgInfo = remember(ctx) {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull()
    }
    val appVersion = pkgInfo?.versionName ?: "1.0.0"
    val abiLabel = remember { android.os.Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "UNIVERSAL" }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember(LangCode) { mutableStateOf(S.tapCheckUpdates) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var showDevSheet by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }

    val openUrl: (String) -> Unit = { url ->
        if (url.startsWith("https://", ignoreCase = true)) {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
    }

    val checkUpdates: () -> Unit = {
        if (!isCheckingUpdate) {
            isCheckingUpdate = true
            updateStatus = S.checkingUpdates
            updateUrl = null
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val res = runCatching {
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/swaramusic/Swara/releases/latest")
                        .header("Accept", "application/vnd.github+json")
                        .build()
                    client.newCall(req).execute().use { r ->
                        if (r.code == 404) {
                            "latest" to null
                        } else if (!r.isSuccessful) {
                            "err" to null
                        } else {
                            val json = org.json.JSONObject(r.body?.string().orEmpty())
                            val tag = json.optString("tag_name").removePrefix("v").trim()
                            val html = json.optString("html_url").ifBlank { "https://github.com/swaramusic/Swara/releases" }
                            if (tag.isNotBlank() && tag != appVersion) "new:$tag" to html
                            else "latest" to null
                        }
                    }
                }.getOrDefault("err" to null)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    isCheckingUpdate = false
                    when {
                        res.first.startsWith("new:") -> {
                            val v = res.first.removePrefix("new:")
                            updateStatus = "Update available: v$v"
                            updateUrl = res.second
                        }
                        res.first == "latest" -> updateStatus = "${S.latestVersionMsg} (v$appVersion)"
                        else -> updateStatus = S.updateErrorMsg
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .background(Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.22f), glowColor.copy(alpha = 0.1f), BgDark)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextInk)
            }
            Text(S.aboutTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextInk)
        }

        // Hero Header
        Surface(
            color = CardDark.copy(alpha = 0.8f),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SwaraAnimatedVectorLogo(modifier = Modifier.size(84.dp))
                Text("Swara", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextInk)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("v$appVersion", abiLabel, "GPL-3.0").forEach { badge ->
                        Surface(color = primaryColor.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
                            Text(badge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }

        // Check for updates (Auralys-inspired clean pill row)
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = checkUpdates)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isCheckingUpdate) {
                    CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Default.Update, contentDescription = null, tint = primaryColor, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(S.checkUpdates, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk)
                    Text(updateStatus, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
                }
                val dlUrl = updateUrl
                if (dlUrl != null) {
                    Surface(
                        color = primaryColor,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { openUrl(dlUrl) }
                    ) {
                        Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                } else {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
                }
            }
        }

        // Community & Website links (real GitHub logo, no email)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { openUrl("https://github.com/swaramusic/Swara") }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GitHubLogoIcon(tint = primaryColor, modifier = Modifier.size(22.dp))
                    Text("GitHub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
                }
            }
            Surface(
                color = CardDark,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { openUrl("https://swaramusic.github.io/Swara/") }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Public, contentDescription = "Website", tint = primaryColor, modifier = Modifier.size(22.dp))
                    Text("Website", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
                }
            }
        }

        // Creator / Developer (Metrolist-inspired profile + interactive bottom sheet)
        Text(S.developerTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk.copy(alpha = 0.72f), modifier = Modifier.padding(top = 4.dp))
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { showDevSheet = true }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AsyncImage(
                    model = "https://github.com/tirtadhi.png",
                    contentDescription = "tirtadhi",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(50.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.2f))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("tirtadhi", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextInk)
                    Text(S.creatorLeadDev, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Swara is licensed under GNU GPL-3.0",
            fontSize = 12.sp,
            color = TextInk.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }

    if (showSupportSheet) {
        var qrisSaved by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = { showSupportSheet = false },
            containerColor = CardDark,
            contentColor = TextInk,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(S.sponsorDev, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextInk)
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(210.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.swara_qris_qr),
                        contentDescription = "Support",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(10.dp)
                    )
                }
                Text(
                    S.supportScanHint,
                    fontSize = 12.sp,
                    color = TextInk.copy(alpha = 0.68f),
                    textAlign = TextAlign.Center
                )
                FilledTonalButton(
                    onClick = {
                        runCatching {
                            val bmp = android.graphics.BitmapFactory.decodeResource(ctx.resources, R.drawable.swara_qris_qr)
                            val vals = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "swara_qris.png")
                                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                            }
                            val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vals)
                            if (uri != null) {
                                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                qrisSaved = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = primaryColor, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (qrisSaved) S.qrSavedBtn else S.saveQrBtn, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showDevSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDevSheet = false },
            containerColor = CardDark,
            contentColor = TextInk,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AsyncImage(
                        model = "https://github.com/tirtadhi.png",
                        contentDescription = "tirtadhi",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.2f))
                    )
                    Column {
                        Text("tirtadhi", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextInk)
                        Text(S.creatorLeadDev, fontSize = 13.sp, color = TextInk.copy(alpha = 0.68f))
                    }
                }
                FilledTonalButton(
                    onClick = { showDevSheet = false; openUrl("https://github.com/tirtadhi") },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = primaryColor, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GitHubLogoIcon(tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GitHub", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { showDevSheet = false; showSupportSheet = true },
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextInk.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextInk),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(S.sponsorDev, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun StorageOverlay(
    recentCount: Int,
    songCacheEnabled: Boolean,
    maxSongCacheMb: Int,
    primaryColor: Color,
    onToggleSongCache: (Boolean) -> Unit,
    onChangeMaxCacheMb: (Int) -> Unit,
    onClearDownloads: () -> Unit,
    onClearCache: () -> Unit,
    onClearHistory: () -> Unit,
    onClose: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var downloadBytes by remember { mutableStateOf(0L) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var refreshTick by remember { mutableStateOf(0) }

    LaunchedEffect(refreshTick) {
        val (dl, ca) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            fun dirBytes(f: java.io.File): Long =
                if (!f.exists()) 0L else if (f.isFile) f.length() else f.listFiles()?.sumOf { dirBytes(it) } ?: 0L
            dirBytes(ctx.filesDir.resolve("downloads")) to (dirBytes(ctx.cacheDir.resolve("image_cache")) + dirBytes(ctx.cacheDir.resolve("song_cache")))
        }
        downloadBytes = dl
        cacheBytes = ca
    }

    fun fmtBytes(b: Long): String = when {
        b >= 1024L * 1024L * 1024L -> String.format("%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
        b >= 1024L * 1024L -> String.format("%.0f MB", b / (1024.0 * 1024.0))
        b >= 1024L -> String.format("%.0f KB", b / 1024.0)
        else -> "$b B"
    }
    fun fmtMaxMb(mb: Int): String = if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else "$mb MB"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextInk)
            }
            Text(S.storageTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextInk)
        }

        Text(S.storageTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp))
        Surface(color = CardDark, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                    Text(S.downloadedSongsLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk, modifier = Modifier.weight(1f))
                    Text(fmtBytes(downloadBytes), fontSize = 13.sp, color = TextInk.copy(alpha = 0.65f))
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.06f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onClearDownloads()
                        refreshTick++
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                    Text(S.clearAllDownloads, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.5f))
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.06f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onClearHistory()
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                    Text(S.clearListenHistory, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk, modifier = Modifier.weight(1f))
                    Text("$recentCount", fontSize = 13.sp, color = TextInk.copy(alpha = 0.55f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.5f))
                }
            }
        }

        Text(S.songCacheSection, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Surface(color = CardDark, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                    Text(S.enableSongCache, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk, modifier = Modifier.weight(1f))
                    Switch(
                        checked = songCacheEnabled,
                        onCheckedChange = onToggleSongCache,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                    )
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.06f))
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(S.maxSongCacheSize, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk)
                            Text(fmtMaxMb(maxSongCacheMb), fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
                        }
                    }
                    Slider(
                        value = maxSongCacheMb.toFloat(),
                        onValueChange = { onChangeMaxCacheMb(it.toInt()) },
                        valueRange = 128f..2048f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = primaryColor)
                    )
                    val maxBytes = maxSongCacheMb.toLong() * 1024L * 1024L
                    val ratio = if (maxBytes > 0L) (cacheBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                        color = primaryColor,
                        trackColor = TextInk.copy(alpha = 0.14f)
                    )
                    Text("${fmtBytes(cacheBytes)} / ${fmtMaxMb(maxSongCacheMb)}", fontSize = 12.sp, color = TextInk.copy(alpha = 0.7f))
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.06f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onClearCache()
                        refreshTick++
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                    Text(S.clearSongCache, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.5f))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun GitHubLogoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h) / 24f
        val ox = (w - 24f * s) / 2f
        val oy = (h - 24f * s) / 2f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(ox + 12f * s, oy + 2f * s)
            cubicTo(ox + 6.48f * s, oy + 2f * s, ox + 2f * s, oy + 6.59f * s, ox + 2f * s, oy + 12.25f * s)
            cubicTo(ox + 2f * s, oy + 16.78f * s, ox + 4.87f * s, oy + 20.62f * s, ox + 8.84f * s, oy + 21.97f * s)
            cubicTo(ox + 9.34f * s, oy + 22.07f * s, ox + 9.52f * s, oy + 21.75f * s, ox + 9.52f * s, oy + 21.48f * s)
            lineTo(ox + 9.52f * s, oy + 19.78f * s)
            cubicTo(ox + 6.73f * s, oy + 20.4f * s, ox + 6.14f * s, oy + 18.4f * s, ox + 6.14f * s, oy + 18.4f * s)
            cubicTo(ox + 5.68f * s, oy + 17.22f * s, ox + 5.03f * s, oy + 16.91f * s, ox + 5.03f * s, oy + 16.91f * s)
            cubicTo(ox + 4.12f * s, oy + 16.27f * s, ox + 5.1f * s, oy + 16.28f * s, ox + 5.1f * s, oy + 16.28f * s)
            cubicTo(ox + 6.1f * s, oy + 16.36f * s, ox + 6.63f * s, oy + 17.34f * s, ox + 6.63f * s, oy + 17.34f * s)
            cubicTo(ox + 7.52f * s, oy + 18.9f * s, ox + 8.97f * s, oy + 18.45f * s, ox + 9.54f * s, oy + 18.19f * s)
            cubicTo(ox + 9.63f * s, oy + 17.53f * s, ox + 9.89f * s, oy + 17.08f * s, ox + 10.18f * s, oy + 16.82f * s)
            cubicTo(ox + 7.95f * s, oy + 16.56f * s, ox + 5.62f * s, oy + 15.68f * s, ox + 5.62f * s, oy + 11.75f * s)
            cubicTo(ox + 5.62f * s, oy + 10.63f * s, ox + 6.01f * s, oy + 9.71f * s, ox + 6.65f * s, oy + 9f * s)
            cubicTo(ox + 6.55f * s, oy + 8.74f * s, ox + 6.2f * s, oy + 7.69f * s, ox + 6.75f * s, oy + 6.28f * s)
            cubicTo(ox + 6.75f * s, oy + 6.28f * s, ox + 7.59f * s, oy + 6f * s, ox + 9.5f * s, oy + 7.33f * s)
            cubicTo(ox + 10.3f * s, oy + 7.1f * s, ox + 11.15f * s, oy + 6.99f * s, ox + 12f * s, oy + 6.98f * s)
            cubicTo(ox + 12.85f * s, oy + 6.99f * s, ox + 13.7f * s, oy + 7.1f * s, ox + 14.5f * s, oy + 7.33f * s)
            cubicTo(ox + 16.41f * s, oy + 6f * s, ox + 17.25f * s, oy + 6.28f * s, ox + 17.25f * s, oy + 6.28f * s)
            cubicTo(ox + 17.8f * s, oy + 7.69f * s, ox + 17.45f * s, oy + 8.74f * s, ox + 17.35f * s, oy + 9f * s)
            cubicTo(ox + 17.99f * s, oy + 9.71f * s, ox + 18.38f * s, oy + 10.63f * s, ox + 18.38f * s, oy + 11.75f * s)
            cubicTo(ox + 18.38f * s, oy + 15.69f * s, ox + 16.04f * s, oy + 16.56f * s, ox + 13.81f * s, oy + 16.81f * s)
            cubicTo(ox + 14.17f * s, oy + 17.13f * s, ox + 14.49f * s, oy + 17.75f * s, ox + 14.49f * s, oy + 18.71f * s)
            lineTo(ox + 14.49f * s, oy + 21.48f * s)
            cubicTo(ox + 14.49f * s, oy + 21.75f * s, ox + 14.67f * s, oy + 22.08f * s, ox + 15.17f * s, oy + 21.97f * s)
            cubicTo(ox + 19.14f * s, oy + 20.62f * s, ox + 22f * s, oy + 16.78f * s, ox + 22f * s, oy + 12.25f * s)
            cubicTo(ox + 22f * s, oy + 6.59f * s, ox + 17.52f * s, oy + 2f * s, ox + 12f * s, oy + 2f * s)
            close()
        }
        drawPath(path = p, color = tint)
    }
}

@Composable
private fun SettingsGroupHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    expanded: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    val rot by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "grpRot")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk)
            Text(subtitle, fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TextInk.copy(alpha = 0.65f),
            modifier = Modifier.graphicsLayer { rotationZ = rot }
        )
    }
}

@Composable
private fun SettingsFlatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    primaryColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = primaryColor, modifier = Modifier.size(20.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
    }
}

@Composable
private fun DiscordGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.20f)
            quadraticTo(w * 0.50f, h * 0.14f, w * 0.78f, h * 0.20f)
            quadraticTo(w * 0.92f, h * 0.42f, w * 0.88f, h * 0.74f)
            quadraticTo(w * 0.76f, h * 0.83f, w * 0.65f, h * 0.84f)
            lineTo(w * 0.60f, h * 0.75f)
            quadraticTo(w * 0.50f, h * 0.78f, w * 0.40f, h * 0.75f)
            lineTo(w * 0.35f, h * 0.84f)
            quadraticTo(w * 0.24f, h * 0.83f, w * 0.12f, h * 0.74f)
            quadraticTo(w * 0.08f, h * 0.42f, w * 0.22f, h * 0.20f)
            close()
        }
        drawPath(path, color = tint)
        drawCircle(color = BgDark, radius = w * 0.085f, center = androidx.compose.ui.geometry.Offset(w * 0.37f, h * 0.50f))
        drawCircle(color = BgDark, radius = w * 0.085f, center = androidx.compose.ui.geometry.Offset(w * 0.63f, h * 0.50f))
    }
}

@Composable
private fun DiscordSettingsOverlay(
    enabled: Boolean,
    token: String,
    username: String,
    status: String,
    showButtons: Boolean,
    template: String,
    currentTrack: Track?,
    primaryColor: Color,
    onToggleEnabled: (Boolean) -> Unit,
    onSaveToken: (String) -> Unit,
    onDisconnect: () -> Unit,
    onToggleShowButtons: (Boolean) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val openDiscordWeb: () -> Unit = {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/login")))
    }

    val openDiscordApp: () -> Unit = {
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage("com.discord")
        if (launchIntent != null) {
            ctx.startActivity(launchIntent)
        } else {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("discord://")))
            }.onFailure {
                openDiscordWeb()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextInk)
        }
        Text("Discord", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = TextInk)

        // Main pill toggle (Coming Soon - off)
        Surface(
            color = primaryColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Discord Rich Presence", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextInk)
                    Surface(color = primaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                        Text("Soon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                }
                Switch(
                    checked = false,
                    enabled = false,
                    onCheckedChange = {},
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                )
            }
        }
        Text(
            "Coming Soon \u2022 Integrasi Discord Rich Presence akan tersedia di pembaruan berikutnya.",
            fontSize = 13.sp,
            color = TextInk.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        // Account status & direct browser/app launch (flat Material 3 section, no card border)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    DiscordGlyphIcon(tint = primaryColor, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (token.isBlank()) "Discord Account" else if (username.isNotBlank()) "@$username" else "Discord Connected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextInk
                    )
                    Text(
                        if (token.isBlank()) "Login via browser atau buka aplikasi Discord di HP Anda" else "Status: $status",
                        fontSize = 12.sp,
                        color = TextInk.copy(alpha = 0.65f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (token.isBlank()) {
                    FilledTonalButton(
                        onClick = openDiscordWeb,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = primaryColor, contentColor = Color.White),
                    ) {
                        Text("Login Discord", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.16f), contentColor = Color(0xFFEF4444)),
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = openDiscordApp,
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TextInk.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextInk),
                ) {
                    Text("Buka Discord App", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))

        // Templates & Link Buttons (flat section)
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Activity Template", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("app" to "Swara", "artist" to "Artist", "song" to "Song").forEach { (id, label) ->
                    val sel = template == id
                    val chipBg by androidx.compose.animation.animateColorAsState(if (sel) primaryColor else CardDark, tween(180), label = "tplBg")
                    Surface(
                        color = chipBg,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelectTemplate(id) }
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextInk, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Link Button", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                    Text("Tampilkan tombol \"Listen on Swara\" di profil", fontSize = 12.sp, color = TextInk.copy(alpha = 0.62f))
                }
                Switch(
                    checked = showButtons,
                    onCheckedChange = onToggleShowButtons,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                )
            }
        }
        HorizontalDivider(color = TextInk.copy(alpha = 0.08f))

        // Live Profile Activity Preview (single subtle highlight card)
        val previewTrack = currentTrack ?: Track("demo", "Sekian, Terima Kasih", "The Jeblogs", "")
        val previewHeader = when (template) {
            "artist" -> "LISTENING TO ${previewTrack.artist.uppercase()}"
            "song" -> "LISTENING TO ${previewTrack.title.uppercase()}"
            else -> "LISTENING TO SWARA"
        }
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(previewHeader, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextInk.copy(alpha = 0.65f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (previewTrack.artworkUrl.isNotBlank()) {
                        AsyncImage(model = previewTrack.artworkUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)))
                    } else {
                        Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(primaryColor.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = primaryColor)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(previewTrack.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(previewTrack.artist, fontSize = 12.sp, color = TextInk.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (showButtons) {
                    Surface(color = BgDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Listen on Swara", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onSaveToPlaylist: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(CardDark)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isDownloaded) {
                    Icon(Icons.Default.DownloadDone, contentDescription = "Offline", tint = primaryColor, modifier = Modifier.size(13.dp))
                }
                Text(track.artist, fontSize = 13.sp, color = TextInk.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onToggleLike) {
            Icon(
                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Suka",
                tint = if (isLiked) primaryColor else TextInk.copy(alpha = 0.7f)
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Opsi", tint = TextInk.copy(alpha = 0.7f))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = CardDark) {
                DropdownMenuItem(
                    text = { Text("Tambah ke Antrean", color = Color.White) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = Color.White) },
                    onClick = { menuOpen = false; onAddToQueue() }
                )
                DropdownMenuItem(
                    text = { Text("Simpan ke Playlist", color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = Color.White) },
                    onClick = { menuOpen = false; onSaveToPlaylist() }
                )
                DropdownMenuItem(
                    text = { Text(if (isDownloading) "Mengunduh..." else if (isDownloaded) "Tersimpan Offline" else "Unduh Offline", color = Color.White) },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = if (isDownloaded) primaryColor else Color.White) },
                    onClick = { menuOpen = false; onDownload() }
                )
            }
        }
    }
}

@Composable
private fun FullscreenPlayerOverlay(
    track: Track,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    isLiked: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    activeTab: PlayerTab,
    onSelectTab: (PlayerTab) -> Unit,
    lyrics: LyricsData?,
    isLoadingLyrics: Boolean,
    queue: List<Track>,
    currentIndex: Int,
    sleepRemainingSec: Int,
    primaryColor: Color,
    glowColor: Color,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenMenu: () -> Unit,
    ambientMode: Boolean,
    onToggleAmbient: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var isVinylMode by remember { mutableStateOf(false) }
    val vinylRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, isVinylMode) {
        if (isPlaying && isVinylMode) {
            while (true) {
                vinylRotation.animateTo(
                    targetValue = vinylRotation.value + 360f,
                    animationSpec = tween(durationMillis = 8000, easing = LinearEasing)
                )
            }
        }
    }
    val glassBg = Color.White.copy(alpha = 0.09f)
    val glassBorder = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.05f)))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .background(
                Brush.radialGradient(
                    colors = listOf(primaryColor.copy(alpha = 0.34f), glowColor.copy(alpha = 0.14f), Color.Transparent),
                    radius = 950f
                )
            )
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            // Top bar: Liquid glass < | Now Playing | Liquid glass Heart + Menu
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = glassBg,
                    shape = CircleShape,
                    border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                    modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClose)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
                    }
                }
                Text(
                    text = if (sleepRemainingSec > 0) "SLEEP ${formatMs(sleepRemainingSec * 1000L)}" else "Now Playing",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (sleepRemainingSec > 0) primaryColor else Color.White,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = glassBg,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onToggleLike)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Suka",
                                tint = if (isLiked) primaryColor else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Surface(
                        color = glassBg,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onOpenMenu)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Opsi Lagu", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (ambientMode) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().clickable { onToggleAmbient() }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                        )
                        Text(track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(track.artist, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(S.exitCalmMode, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                }
            } else {

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (activeTab) {
                    PlayerTab.SONG -> {
                        val coverInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.76f)
                                .aspectRatio(1f)
                                .clickable(interactionSource = coverInteraction, indication = null) { isVinylMode = !isVinylMode },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVinylMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { rotationZ = vinylRotation.value % 360f }
                                        .clip(CircleShape)
                                        .background(Color(0xFF101218)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val r = size.minDimension / 2f
                                        listOf(0.94f, 0.84f, 0.74f, 0.64f).forEach { frac ->
                                            drawCircle(
                                                color = Color.White.copy(alpha = 0.08f),
                                                radius = r * frac,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
                                            )
                                        }
                                    }
                                    AsyncImage(
                                        model = track.artworkUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(0.54f).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(BgDark)
                                            .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(24.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                                )
                            }
                        }
                    }
                    PlayerTab.LYRICS -> {
                        LyricsView(
                            lyrics = lyrics,
                            isLoading = isLoadingLyrics,
                            positionMs = positionMs,
                            primaryColor = primaryColor,
                            onSeek = onSeek,
                        )
                    }
                    PlayerTab.QUEUE -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 8.dp)) {
                            itemsIndexed(queue) { idx, t ->
                                val isCur = idx == currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isCur) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                                        .clickable { onPlayQueueIndex(idx) }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(model = t.artworkUrl, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(t.title, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium, color = if (isCur) primaryColor else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(t.artist, fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (!isCur) {
                                        IconButton(onClick = { onRemoveQueueIndex(idx) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // end else !ambientMode

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(track.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(track.artist, fontSize = 14.sp, color = Color.White.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Column {
                    SquigglySeekbar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        primaryColor = primaryColor,
                        onSeek = onSeek,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatMs(positionMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(formatMs(durationMs), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Ulangi",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) primaryColor else Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Surface(
                        color = glassBg,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onPrev)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Sebelumnya", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Surface(
                        color = primaryColor,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp).shadow(16.dp, CircleShape, ambientColor = primaryColor, spotColor = primaryColor).clip(CircleShape).clickable(onClick = onTogglePlayPause)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    Surface(
                        color = glassBg,
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                        modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onNext)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Berikutnya", tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    IconButton(onClick = onToggleShuffle) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Acak", tint = if (shuffleEnabled) primaryColor else Color.White.copy(alpha = 0.7f))
                    }
                }

                // Liquid Glass Lyrics Preview Card (matching reference screenshot)
                Surface(
                    color = glassBg,
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, glassBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { onSelectTab(if (activeTab == PlayerTab.LYRICS) PlayerTab.SONG else PlayerTab.LYRICS) }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Lyrics preview", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                            IconButton(onClick = { onSelectTab(if (activeTab == PlayerTab.QUEUE) PlayerTab.SONG else PlayerTab.QUEUE) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.FormatListBulleted, contentDescription = "Queue", tint = if (activeTab == PlayerTab.QUEUE) primaryColor else Color.White.copy(alpha = 0.75f), modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.OpenInFull, contentDescription = "Expand Lyrics", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
                        }
                        val lines = lyrics?.lines.orEmpty()
                        if (isLoadingLyrics) {
                            Text("Loading lyrics...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                        } else if (lines.isEmpty()) {
                            Text("Ketuk untuk melihat lirik lagu ini", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                        } else {
                            val activeIdx = if (lyrics?.synced == true) {
                                lines.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0)
                            } else 0
                            lines.drop(activeIdx).take(3).forEachIndexed { idx, line ->
                                Text(
                                    text = line.text,
                                    fontSize = 15.sp,
                                    fontWeight = if (idx == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (idx == 0) primaryColor else Color.White.copy(alpha = 0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsView(
    lyrics: LyricsData?,
    isLoading: Boolean,
    positionMs: Long,
    primaryColor: Color,
    onSeek: (Long) -> Unit,
) {
    if (isLoading) {
        CircularProgressIndicator(color = primaryColor)
        return
    }
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Text("Lirik tidak tersedia untuk lagu ini.", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        return
    }
    val activeIdx = remember(lyrics, positionMs) {
        if (!lyrics.synced) -1 else lyrics.lines.indexOfLast { positionMs >= it.startMs }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0) listState.animateScrollToItem((activeIdx - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(lyrics.lines) { idx, line ->
            val isActive = idx == activeIdx
            Text(
                text = line.text,
                fontSize = if (isActive) 22.sp else 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isActive -> primaryColor
                    lyrics.synced -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.85f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = lyrics.synced) { onSeek(line.startMs) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun EqualizerSheetContent(
    activePresetId: String,
    bandsDb: List<Float>,
    primaryColor: Color,
    onSelectPreset: (String) -> Unit,
    onChangeBand: (Int, Float) -> Unit,
) {
    val freqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
    val eqActive = SwaraPlaybackService.isEqualizerAttached()
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Equalizer Native Android", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (eqActive) "AudioFX Aktif" else "Siap (Putar lagu)",
                fontSize = 11.sp,
                color = if (eqActive) primaryColor else TextInk.copy(alpha = 0.6f)
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BUILTIN_EQ_PRESETS) { preset ->
                FilterChip(
                    selected = activePresetId == preset.id,
                    onClick = { onSelectPreset(preset.id) },
                    label = { Text(preset.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = primaryColor, selectedLabelColor = Color.White)
                )
            }
        }
        bandsDb.forEachIndexed { idx, db ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(freqLabels.getOrElse(idx) { "B$idx" }, fontSize = 12.sp, modifier = Modifier.width(50.dp))
                Slider(
                    value = db,
                    onValueChange = { onChangeBand(idx, it) },
                    valueRange = -12f..12f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                )
                Text("${db.toInt()}dB", fontSize = 12.sp, modifier = Modifier.width(42.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}


@Composable
private fun IdentifySongSheetContent(
    results: List<Track>,
    isIdentifying: Boolean,
    primaryColor: Color,
    onSearchSnippet: (String) -> Unit,
    onPlay: (Track) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var snippet by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Kenali Lagu (Identify Song)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Temukan lagu dari suara sekitar (Google Sound Search) atau ketik potongan lirik yang terdengar.", fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
        Button(
            onClick = {
                val intent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val fallback = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(fallback) } }
            },
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Dengarkan Musik via Mikrofon", color = Color.White)
        }
        OutlinedTextField(
            value = snippet,
            onValueChange = { snippet = it },
            placeholder = { Text("Ketik sepenggal lirik yang terdengar...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSearchSnippet(snippet) },
            colors = ButtonDefaults.buttonColors(containerColor = TextInk.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cari dari Potongan Lirik", color = Color.White)
        }
        if (isIdentifying) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = primaryColor)
        }
        LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
            items(results) { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPlay(t) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(model = t.artworkUrl, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(t.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(t.artist, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = primaryColor)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ListenTogetherSheetContent(
    roomCode: String?,
    isHost: Boolean,
    status: String?,
    primaryColor: Color,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit,
    onLeaveRoom: () -> Unit,
) {
    var joinInput by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Listen Together (Dengar Bareng)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Sinkronkan lagu & posisi pemutaran secara real-time bersama teman.", fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
        if (roomCode != null) {
            Surface(color = TextInk.copy(alpha = 0.08f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isHost) "Kamu adalah Host \u2022 Kode Ruang: $roomCode" else "Tamu \u2022 Kode Ruang: $roomCode", fontWeight = FontWeight.Bold, color = primaryColor)
                    Text(status ?: "Aktif", fontSize = 12.sp, color = TextInk.copy(alpha = 0.75f))
                }
            }
            Button(
                onClick = onLeaveRoom,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A1822)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keluar dari Ruang", color = Color.White)
            }
        } else {
            Button(
                onClick = onCreateRoom,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Buat Ruang Dengar Bareng", color = Color.White)
            }
            OutlinedTextField(
                value = joinInput,
                onValueChange = { joinInput = it },
                placeholder = { Text("Masukkan kode 6 digit teman...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onJoinRoom(joinInput) },
                colors = ButtonDefaults.buttonColors(containerColor = TextInk.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Gabung ke Ruang", color = Color.White)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}


@Composable
private fun SquigglySeekbar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    primaryColor: Color,
    onSeek: (Long) -> Unit,
) {
    val maxDur = maxOf(1L, durationMs)
    val fraction = (positionMs.toFloat() / maxDur.toFloat()).coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "wavePhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "phase"
    )
    val amp by animateFloatAsState(
        targetValue = if (isPlaying) 5.5f else 0f,
        animationSpec = tween(300),
        label = "amp"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(maxDur) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek((ratio * maxDur).toLong())
                }
            }
            .pointerInput(maxDur) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onSeek((ratio * maxDur).toLong())
                }
            }
    ) {
        val cy = size.height / 2f
        val thumbX = fraction * size.width
        val strokePx = 3.5.dp.toPx()

        // Inactive track
        drawLine(
            color = TextInk.copy(alpha = 0.22f),
            start = Offset(thumbX, cy),
            end = Offset(size.width, cy),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        // Active squiggly wave
        if (thumbX > 1f) {
            val path = Path()
            val step = 4f
            var x = 0f
            path.moveTo(0f, cy + amp * sin(phase))
            while (x < thumbX) {
                x = (x + step).coerceAtMost(thumbX)
                val y = cy + amp * sin(x * 0.085f - phase)
                path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = primaryColor,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Glowing thumb
        drawCircle(color = primaryColor.copy(alpha = 0.28f), radius = 12.dp.toPx(), center = Offset(thumbX, cy))
        drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = Offset(thumbX, cy))
    }
}


@Composable
private fun SongOptionsSheetContent(
    track: Track,
    volume: Float,
    soundStyle: String,
    speed: Float,
    reverbPct: Int,
    sleepRemainingSec: Int,
    isDownloaded: Boolean,
    ambientMode: Boolean,
    primaryColor: Color,
    onVolumeChange: (Float) -> Unit,
    onStartRadio: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCopyLink: () -> Unit,
    onShare: () -> Unit,
    onViewArtist: () -> Unit,
    onViewAlbum: () -> Unit,
    onExcludeFromTaste: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onDownload: () -> Unit,
    onListenTogether: () -> Unit,
    onToggleAmbient: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSoundStyle: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(model = track.artworkUrl, contentDescription = track.title, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, fontSize = 13.sp, color = TextInk.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VolumeDown, contentDescription = null, tint = TextInk.copy(alpha = 0.7f))
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor)
                )
                Icon(Icons.Default.VolumeUp, contentDescription = null, tint = TextInk.copy(alpha = 0.7f))
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickTile(S.radioLabel, Icons.Default.Radio, Modifier.weight(1f), onStartRadio)
                QuickTile("Playlist", Icons.AutoMirrored.Filled.PlaylistAdd, Modifier.weight(1f), onAddToPlaylist)
                QuickTile(S.copyLabel, Icons.Default.Link, Modifier.weight(1f), onCopyLink)
                QuickTile(S.shareLabel, Icons.Default.Share, Modifier.weight(1f), onShare)
            }
        }
        item {
            OptionRow(Icons.Default.Tune, S.soundStyleTitle, "$soundStyle \u2022 ${"%.2f".format(speed)}\u00D7 \u2022 ${S.reverbLabel} $reverbPct%", onOpenSoundStyle)
            OptionRow(Icons.Default.PersonSearch, S.viewArtist, track.artist, onViewArtist)
            OptionRow(Icons.Default.Album, S.viewAlbum, track.title, onViewAlbum)
            OptionRow(Icons.Default.Block, S.excludeTaste, S.excludeTasteSub, onExcludeFromTaste)
            OptionRow(Icons.Default.Bedtime, if (sleepRemainingSec > 0) "${S.sleepTimerLabel}: ${formatMs(sleepRemainingSec * 1000L)}" else S.sleepTimerOff, S.sleepTimerSub, onCycleSleepTimer)
            OptionRow(Icons.Default.Download, if (isDownloaded) S.savedOffline else S.downloadOffline, S.playOfflineSub, onDownload)
            OptionRow(Icons.Default.Group, S.listenTogetherMenu, S.listenTogetherSub, onListenTogether)
            OptionRow(Icons.Default.Fullscreen, if (ambientMode) S.exitCalmMode else S.ambientLabel, S.ambientSub, onToggleAmbient)
            OptionRow(Icons.Default.GraphicEq, S.eqMenu, S.eqMenuSub, onOpenEq)
        }
    }
}

private data class SoundStylePreset(val id: String, val subtitleProvider: () -> String)

private val SOUND_STYLE_PRESETS = listOf(
    SoundStylePreset("Normal") { S.styleNormalSub },
    SoundStylePreset("Sped up") { S.styleSpedSub },
    SoundStylePreset("Slowed + reverb") { S.styleSlowedSub },
    SoundStylePreset("Nightcore") { S.styleNightcoreSub },
    SoundStylePreset("Lo-fi") { S.styleLofiSub },
    SoundStylePreset("8D") { S.style8dSub },
    SoundStylePreset("Konser") { S.styleKonserSub },
    SoundStylePreset("Bass Heavy") { S.styleBassSub },
    SoundStylePreset("Vaporwave") { S.styleVaporSub },
    SoundStylePreset("Vokal / Podcast") { S.styleVocalSub },
)

@Composable
private fun GayaSuaraSheetContent(
    activeStyle: String,
    speed: Float,
    reverbPct: Int,
    primaryColor: Color,
    onSelectStyle: (String) -> Unit,
    onChangeVinylRate: (Float) -> Unit,
    onChangeReverb: (Int) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(S.soundStyleTitle, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextInk)
        Text(
            S.soundStyleDesc,
            fontSize = 13.sp,
            color = TextInk.copy(alpha = 0.7f),
            lineHeight = 18.sp
        )

        SOUND_STYLE_PRESETS.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { preset ->
                    val selected = activeStyle == preset.id
                    Surface(
                        color = if (selected) primaryColor.copy(alpha = 0.22f) else TextInk.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 86.dp)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) primaryColor else TextInk.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectStyle(preset.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                preset.id,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (selected) primaryColor else TextInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                preset.subtitleProvider(),
                                fontSize = 11.sp,
                                color = TextInk.copy(alpha = 0.68f),
                                lineHeight = 14.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.speedPitch, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextInk)
                Text("${"%.2f".format(speed)}\u00D7", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
            }
            Slider(
                value = speed,
                onValueChange = onChangeVinylRate,
                valueRange = 0.6f..1.6f,
                colors = SliderDefaults.colors(
                    thumbColor = primaryColor,
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = TextInk.copy(alpha = 0.16f)
                )
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(S.roomReverb, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextInk)
                Text("$reverbPct%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
            }
            Slider(
                value = reverbPct.toFloat(),
                onValueChange = { onChangeReverb(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = primaryColor,
                    activeTrackColor = primaryColor,
                    inactiveTrackColor = TextInk.copy(alpha = 0.16f)
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = TextInk.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onReset)
            ) {
                Text(S.reset, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                color = primaryColor,
                shape = RoundedCornerShape(50),
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onDone)
            ) {
                Text(S.done, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = TextInk.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 11.sp, color = Color.White)
        }
    }
}

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = title, tint = Color.White)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}


@Composable
private fun ArtistSheetContent(
    name: String,
    imageUrl: String,
    tracks: List<Track>,
    isLoading: Boolean,
    isFollowed: Boolean,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    primaryColor: Color,
    onToggleFollow: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShufflePlay: (List<Track>) -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownload: (Track) -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onRemoveTrack: ((Track) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(68.dp).clip(CircleShape).background(BgDark)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Artis", fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = if (isFollowed) primaryColor else Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .border(1.dp, if (isFollowed) primaryColor else TextInk.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleFollow)
            ) {
                Text(
                    if (isFollowed) S.following else "Ikuti",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            if (tracks.isNotEmpty()) {
                IconButton(onClick = { onShufflePlay(tracks) }) {
                    Icon(Icons.Default.Shuffle, contentDescription = "Acak", tint = primaryColor)
                }
                Surface(
                    color = primaryColor,
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onPlay(tracks, 0) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = Color.White)
                    }
                }
            }
        }
        Text("Populer", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primaryColor)
            }
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                itemsIndexed(tracks) { idx, t ->
                    TrackRow(
                        track = t,
                        isLiked = t.id in likedIds,
                        isDownloaded = t.id in downloadedIds,
                        isDownloading = t.id in downloadingIds,
                        primaryColor = primaryColor,
                        onClick = { onPlay(tracks, idx) },
                        onToggleLike = { onToggleLike(t) },
                        onAddToQueue = { onAddToQueue(t) },
                        onDownload = { onDownload(t) },
                        onSaveToPlaylist = { onSaveToPlaylist(t) },
                    )
                    if (onRemoveTrack != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { onRemoveTrack(t) }, colors = ButtonDefaults.textButtonColors(contentColor = primaryColor)) {
                                Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Remove / Hide from playlist", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

