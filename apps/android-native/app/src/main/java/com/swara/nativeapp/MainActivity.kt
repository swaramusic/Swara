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

internal var BgDark by mutableStateOf(Color(0xFF0D1524))
internal var CardDark by mutableStateOf(Color(0xFF152033))
internal var TextInk by mutableStateOf(Color(0xFFF2F1E8))
internal var LangCode by mutableStateOf("en")
internal val S: LangStrings get() = stringsFor(LangCode)
internal val MOOD_CHIPS = listOf("Semua", "Santai", "Energi", "Fokus", "Romantis", "Galau")
internal val SEARCH_SUGGESTIONS = listOf("Pop", "Lo-fi", "Acoustic", "Chill", "Remix")

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
                .crossfade(false)
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
internal fun SwaraThemeRoot(vm: SwaraViewModel = viewModel()) {
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
internal fun SwaraSplashOverlay() {
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
internal fun SwaraAnimatedVectorLogo(modifier: Modifier = Modifier) {
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

internal enum class NavTab { HOME, EXPLORE, LIBRARY, STATS, SETTINGS }

@Composable
internal fun MiniPlayerProgress(
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
internal fun navTabLabel(t: NavTab): String = when (t) {
    NavTab.HOME -> S.home
    NavTab.EXPLORE -> S.explore
    NavTab.LIBRARY -> S.library
    NavTab.STATS -> S.stats
    NavTab.SETTINGS -> S.settings
}

internal enum class PlayerTab(val label: String) {
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
    var confirmDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var pendingDownloadTracks by remember { mutableStateOf<List<Track>?>(null) }
    val appCtxForTree = androidx.compose.ui.platform.LocalContext.current
    val downloadFolderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                appCtxForTree.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            vm.setDownloadTreeUri(uri.toString())
            val queued = pendingDownloadTracks
            if (queued != null) {
                pendingDownloadTracks = null
                if (queued.size == 1) vm.downloadTrack(queued.first(), uri.toString())
                else vm.downloadPlaylist(queued, uri.toString())
            }
        }
    }
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
            showFullscreen -> showFullscreen = false
            openedUserPlaylist != null -> openedUserPlaylist = null
            state.openedArtistName != null -> vm.closeArtist()
            state.openedPlaylistTitle != null -> vm.closeOpenedPlaylist()
            state.openedAlbumTitle != null -> vm.closeAlbum()
            showHistorySheet -> showHistorySheet = false
            showFriendsStats -> showFriendsStats = false
            showRecapSheet -> showRecapSheet = false
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
    val onDownloadCb: (Track) -> Unit = remember { { t -> pendingDownloadTracks = listOf(t) } }
    val onSaveToPlaylistCb: (Track) -> Unit = remember { { playlistPickerTrack = it } }
    val onOpenArtistCb: (String) -> Unit = remember(vm) { { vm.openArtist(it) } }

    // shortcut: navbar always visible; collapse-on-scroll removed (triggered on all tabs)
    val appCtx = androidx.compose.ui.platform.LocalContext.current
    var availableUpdate by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var showUpdateNotesDialog by remember { mutableStateOf(false) }
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
                        val body = json.optString("body").trim()
                        val isNewer = tag.split(".").map { it.toIntOrNull() ?: 0 }
                            .let { t ->
                                val c = ver.split(".").map { it.toIntOrNull() ?: 0 }
                                val maxLen = maxOf(t.size, c.size)
                                (0 until maxLen).firstNotNullOfOrNull { i ->
                                    val tv = t.getOrElse(i) { 0 }
                                    val cv = c.getOrElse(i) { 0 }
                                    if (tv != cv) tv > cv else null
                                } ?: false
                            }
                        if (tag.isNotBlank() && isNewer && html.startsWith("https://")) Triple(tag, html, body) else null
                    }
                }
            }.getOrNull()
        }
        if (found != null) availableUpdate = found
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
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                availableUpdate?.let { (newVer, dlUrl, releaseNotes) ->
                    Surface(
                        color = primaryColor.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showUpdateNotesDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Update, contentDescription = null, tint = TextInk, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Update v$newVer tersedia", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
                                Text("Ketuk untuk lihat catatan rilis", fontSize = 10.sp, color = TextInk.copy(alpha = 0.7f))
                            }
                            Surface(
                                color = primaryColor,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showUpdateNotesDialog = true }
                            ) {
                                Text("Update", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                            IconButton(onClick = { availableUpdate = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextInk, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    if (showUpdateNotesDialog) {
                        AlertDialog(
                            onDismissRequest = { showUpdateNotesDialog = false },
                            containerColor = CardDark,
                            titleContentColor = TextInk,
                            textContentColor = TextInk,
                            title = { Text("Update v$newVer", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(
                                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        releaseNotes.ifBlank { "Perbaikan bug dan peningkatan performa." },
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = TextInk.copy(alpha = 0.85f)
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showUpdateNotesDialog = false
                                    runCatching { appCtx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dlUrl))) }
                                }) { Text("Download Update", color = TextInk, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUpdateNotesDialog = false }) {
                                    Text("Nanti", color = TextInk.copy(alpha = 0.7f))
                                }
                            }
                        )
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
                            (slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)) { dir * it / 12 } +
                                fadeIn(tween(200, easing = FastOutSlowInEasing))) togetherWith
                                (slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { -dir * it / 16 } +
                                    fadeOut(tween(140)))
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
                            glowColor = glowColor,
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
                            onDeleteDownload = { t -> confirmDelete = "Hapus unduhan \"${t.title}\"?" to { vm.deleteDownload(t.id) } },
                            onSaveToPlaylist = onSaveToPlaylistCb,
                            onCreatePlaylist = { showCreatePlaylistDialog = true },
                            onImportPlaylist = { showImportPlaylistDialog = true },
                            onImportDevice = { deviceAudioLauncher.launch(arrayOf("audio/*")) },
                            onOpenPlaylist = { openedUserPlaylist = it },
                            onDeletePlaylist = { pl -> confirmDelete = "Hapus playlist \"${pl.name}\"?" to { vm.deletePlaylist(pl.id) } },
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
                            romanizeLyrics = state.romanizeLyrics,
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
                            onToggleRomanizeLyrics = { vm.setRomanizeLyrics(it) },
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

            // Transparent overlay Liquid Glass Miniplayer + Nav (zero per-frame layout thrashing!)
            val liquidGlassBg = if (CardDark == Color(0xFFF3F6FC)) Color.White.copy(alpha = 0.88f) else CardDark.copy(alpha = 0.88f)
            val liquidGlassBorder = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.55f),
                    primaryColor.copy(alpha = 0.42f),
                    glowColor.copy(alpha = 0.38f),
                    Color.White.copy(alpha = 0.3f)
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.downloadTotal > 0) {
                        val rawProgress = (state.downloadDone.toFloat() / state.downloadTotal.toFloat()).coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(
                            targetValue = rawProgress.coerceAtLeast(0.06f),
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
                            label = "dlProgress"
                        )
                        val infiniteTransition = rememberInfiniteTransition(label = "dlShimmer")
                        val shimmerPos by infiniteTransition.animateFloat(
                            initialValue = -0.35f,
                            targetValue = 1.35f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1250, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "dlShimmerPos"
                        )
                        Surface(
                            color = liquidGlassBg,
                            shape = RoundedCornerShape(18.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, liquidGlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = TextInk,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        "Mengunduh ${state.downloadDone} / ${state.downloadTotal} lagu",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextInk
                                    )
                                    Text(
                                        state.downloadCurrentTitle,
                                        fontSize = 11.sp,
                                        color = TextInk.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                ) {
                                    drawRect(color = TextInk.copy(alpha = 0.14f))
                                    val fillW = (size.width * animatedProgress).coerceIn(0f, size.width)
                                    if (fillW > 0f) {
                                        drawRect(
                                            color = primaryColor,
                                            size = androidx.compose.ui.geometry.Size(fillW, size.height)
                                        )
                                        val shimmerW = size.width * 0.28f
                                        val sx = shimmerPos * fillW
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = 0.55f),
                                                    Color.Transparent
                                                ),
                                                startX = sx - shimmerW * 0.5f,
                                                endX = sx + shimmerW * 0.5f
                                            ),
                                            size = androidx.compose.ui.geometry.Size(fillW, size.height)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        val bannerMsg = state.downloadStatusBanner
                        if (bannerMsg != null) {
                            LaunchedEffect(bannerMsg) {
                                kotlinx.coroutines.delay(4500)
                                vm.dismissDownloadBanner()
                            }
                            Surface(
                                color = liquidGlassBg,
                                shape = RoundedCornerShape(18.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, liquidGlassBorder),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { vm.dismissDownloadBanner() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.DownloadDone, contentDescription = null, tint = TextInk, modifier = Modifier.size(16.dp))
                                    Text(bannerMsg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextInk, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextInk.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }

                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val curTrack = state.currentTrack
                                        if (curTrack != null) {
                                            Surface(
                                                color = liquidGlassBg,
                                                shape = RoundedCornerShape(30.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).clickable { showFullscreen = true }
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
                                                    MiniPlayerProgress(progressFlow = vm.progress, primaryColor = primaryColor, collapseFraction = 0f)
                                                }
                                            }
                                        }
                                        Surface(
                                            color = liquidGlassBg,
                                            shape = RoundedCornerShape(32.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.2.dp, liquidGlassBorder),
                                            modifier = Modifier.fillMaxWidth().height(64.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(vertical = 2.dp, horizontal = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceEvenly,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                NavTab.values().forEach { tab ->
                                                    val selected = activeTab == tab
                                                    val iconScale by animateFloatAsState(
                                                        targetValue = if (selected) 1.12f else 1.0f,
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
                                                    Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(18.dp))
                                                                .background(if (selected) TextInk.copy(alpha = 0.14f) else Color.Transparent)
                                                                .clickable { activeTab = tab }
                                                                .padding(horizontal = 12.dp, vertical = 5.dp)
                                                        ) {
                                                            Icon(icon, contentDescription = navTabLabel(tab), tint = if (selected) TextInk else TextInk.copy(alpha = 0.62f), modifier = Modifier.size(20.dp).scale(iconScale))
                                                            Text(
                                                                navTabLabel(tab),
                                                                fontSize = 10.sp,
                                                                lineHeight = 12.sp,
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                color = if (selected) TextInk else TextInk.copy(alpha = 0.62f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                        onDownload = { pendingDownloadTracks = listOf(cur); showSongMenuSheet = false },
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

            pendingDownloadTracks?.let { targets ->
                AlertDialog(
                    onDismissRequest = { pendingDownloadTracks = null },
                    containerColor = CardDark,
                    titleContentColor = TextInk,
                    textContentColor = TextInk,
                    title = {
                        Text(
                            if (targets.size == 1) "Simpan Lagu di Mana?" else "Simpan ${targets.size} Lagu di Mana?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "Pilih lokasi penyimpanan unduhan:",
                                fontSize = 13.sp,
                                color = TextInk.copy(alpha = 0.72f)
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    pendingDownloadTracks = null
                                    if (targets.size == 1) vm.downloadTrack(targets.first(), "")
                                    else vm.downloadPlaylist(targets, "")
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = TextInk)
                                    Column {
                                        Text("Penyimpanan Aplikasi (Offline)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                                        Text("Cepat, langsung bisa diputar offline di Swara", fontSize = 11.sp, color = TextInk.copy(alpha = 0.6f))
                                    }
                                }
                            }
                            if (state.downloadTreeUri.isNotBlank()) {
                                val folderName = runCatching {
                                    android.net.Uri.parse(state.downloadTreeUri).lastPathSegment?.substringAfterLast(':') ?: "Folder Terpilih"
                                }.getOrDefault("Folder Terpilih")
                                Surface(
                                    color = Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                        pendingDownloadTracks = null
                                        if (targets.size == 1) vm.downloadTrack(targets.first(), state.downloadTreeUri)
                                        else vm.downloadPlaylist(targets, state.downloadTreeUri)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(Icons.Default.Folder, contentDescription = null, tint = TextInk)
                                        Column {
                                            Text("Folder HP: $folderName", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                                            Text("Simpan ke folder yang sudah dipilih + Offline", fontSize = 11.sp, color = TextInk.copy(alpha = 0.6f))
                                        }
                                    }
                                }
                            }
                            Surface(
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                                    downloadFolderLauncher.launch(null)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = TextInk)
                                    Column {
                                        Text(
                                            if (state.downloadTreeUri.isNotBlank()) "Pilih Folder Lain di HP" else "Pilih Folder di Penyimpanan HP",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = TextInk
                                        )
                                        Text("Simpan file audio ke folder pilihan Anda", fontSize = 11.sp, color = TextInk.copy(alpha = 0.6f))
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { pendingDownloadTracks = null }) {
                            Text("Batal", color = TextInk)
                        }
                    }
                )
            }

            confirmDelete?.let { (msg, onConfirm) ->
                AlertDialog(
                    onDismissRequest = { confirmDelete = null },
                    containerColor = CardDark,
                    titleContentColor = TextInk,
                    textContentColor = TextInk,
                    title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
                    text = { Text(msg, fontSize = 14.sp, color = TextInk.copy(alpha = 0.82f)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmDelete = null
                            onConfirm()
                        }) { Text("Hapus", color = Color(0xFFFF5A5F), fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = null }) {
                            Text("Batal", color = TextInk)
                        }
                    }
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
                        onDownload = { pendingDownloadTracks = listOf(it) },
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
                        onPlayNextList = { vm.playNextList(it) },
                        onToggleLike = { vm.toggleLike(it) },
                        onAddToQueue = { vm.addToQueue(it) },
                        onDownload = { pendingDownloadTracks = listOf(it) },
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
                        onDownload = { pendingDownloadTracks = listOf(it) },
                        onSaveToPlaylist = { playlistPickerTrack = it },
                    )
                }
            }

            // Opened user playlist fullscreen overlay (YouTube Music style)
            AnimatedVisibility(
                visible = openedUserPlaylist != null,
                enter = slideInVertically(tween(220)) { it / 5 } + fadeIn(tween(180)),
                exit = slideOutVertically(tween(180)) { it / 5 } + fadeOut(tween(150))
            ) {
                openedUserPlaylist?.let { pl ->
                    val fresh = state.playlists.find { it.id == pl.id } ?: pl
                    UserPlaylistOverlay(
                        playlist = fresh,
                        likedIds = likedIds,
                        downloadedIds = downloadedIds,
                        downloadingIds = state.downloadingIds,
                        currentTrack = state.currentTrack,
                        isPlaying = state.isPlaying,
                        primaryColor = primaryColor,
                        onClose = { openedUserPlaylist = null },
                        onPlay = { list, idx -> vm.playFromList(list, idx) },
                        onShufflePlay = { list -> if (list.isNotEmpty()) vm.playFromList(list.shuffled(), 0) },
                        onPlayNextList = { list -> vm.playNextList(list) },
                        onTogglePlayPause = { vm.togglePlayPause() },
                        onNext = { vm.playNext() },
                        onOpenPlayer = { showFullscreen = true },
                        onToggleLike = onToggleLikeCb,
                        onAddToQueue = onAddToQueueCb,
                        onDownloadTrack = onDownloadCb,
                        onDownloadAll = { pendingDownloadTracks = fresh.tracks },
                        onSaveToPlaylist = onSaveToPlaylistCb,
                        onRemoveTrack = { t -> vm.removeTrackFromPlaylist(fresh.id, t.id) },
                        onEditPlaylist = { newName, uri -> vm.updatePlaylist(fresh.id, newName, uri) },
                    )
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
                        romanizeLyrics = state.romanizeLyrics,
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
                        onDownload = { pendingDownloadTracks = listOf(cur) },
                        onPlayQueueIndex = { idx -> vm.playFromList(state.queue, idx) },
                        onRemoveQueueIndex = { idx -> vm.removeFromQueue(idx) },
                        onCycleSleepTimer = { vm.cycleSleepTimer() },
                        onOpenEq = { showEqSheet = true },
                        onOpenMenu = { showSongMenuSheet = true },
                        ambientMode = ambientMode,
                        onToggleAmbient = { ambientMode = !ambientMode },
                        onToggleRomanizeLyrics = { vm.setRomanizeLyrics(!state.romanizeLyrics) },
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
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = TextInk)
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
                        }) { Text("Buat", color = TextInk) }
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
                        ) { Text(if (state.isImportingPlaylist) "Mengimpor..." else "Impor", color = TextInk) }
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
                        peerCount = state.listenPeerCount,
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

