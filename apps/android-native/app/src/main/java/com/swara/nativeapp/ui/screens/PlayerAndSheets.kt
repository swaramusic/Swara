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


@Composable
internal fun TrackRow(
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
    onRemoveFromPlaylist: (() -> Unit)? = null,
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
                    Icon(Icons.Default.DownloadDone, contentDescription = "Offline", tint = TextInk.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
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
                    text = { Text(if (isDownloading) "Mengunduh..." else if (isDownloaded) "Tersimpan Offline" else "Unduh Offline", color = TextInk) },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = TextInk) },
                    onClick = { menuOpen = false; onDownload() }
                )
                if (onRemoveFromPlaylist != null) {
                    DropdownMenuItem(
                        text = { Text("Remove from playlist", color = TextInk) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = TextInk) },
                        onClick = { menuOpen = false; onRemoveFromPlaylist() }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FullscreenPlayerOverlay(
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
    romanizeLyrics: Boolean,
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
    onToggleRomanizeLyrics: () -> Unit,
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
                        color = if (romanizeLyrics) Color.White.copy(alpha = 0.24f) else glassBg,
                        shape = CircleShape,
                        border = if (romanizeLyrics) {
                            androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.72f))
                        } else {
                            androidx.compose.foundation.BorderStroke(1.dp, glassBorder)
                        },
                        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onToggleRomanizeLyrics)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = "Romanize Lyrics",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
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
                            romanize = romanizeLyrics,
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
                                        Text(t.title, fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                    color = if (idx == 0) Color.White else Color.White.copy(alpha = 0.62f),
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
internal fun LyricsView(
    lyrics: LyricsData?,
    isLoading: Boolean,
    positionMs: Long,
    romanize: Boolean,
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
            val roman = remember(line.text, romanize) { if (romanize) romanizeLyricLine(line.text) else null }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isActive) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                    .clickable(enabled = lyrics.synced) { onSeek(line.startMs) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = line.text,
                    fontSize = if (isActive) 22.sp else 18.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    color = when {
                        isActive -> Color.White
                        lyrics.synced -> Color.White.copy(alpha = 0.42f)
                        else -> Color.White.copy(alpha = 0.88f)
                    }
                )
                if (!roman.isNullOrBlank()) {
                    Text(
                        text = roman,
                        fontSize = if (isActive) 14.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isActive) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun EqualizerSheetContent(
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

internal fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}


@Composable
internal fun IdentifySongSheetContent(
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
                    Icon(Icons.Default.PlayArrow, contentDescription = "Putar", tint = TextInk)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
internal fun ListenTogetherSheetContent(
    roomCode: String?,
    isHost: Boolean,
    peerCount: Int,
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
                    Text(
                        if (isHost) "Kamu adalah Host \u2022 Kode: $roomCode \u2022 $peerCount bergabung"
                        else "Tamu \u2022 Kode Ruang: $roomCode",
                        fontWeight = FontWeight.Bold,
                        color = TextInk
                    )
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
internal fun SquigglySeekbar(
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
internal fun SongOptionsSheetContent(
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

internal data class SoundStylePreset(val id: String, val subtitleProvider: () -> String)

internal val SOUND_STYLE_PRESETS = listOf(
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
internal fun GayaSuaraSheetContent(
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
internal fun QuickTile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
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
internal fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
internal fun ArtistSheetContent(
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
    onPlayNextList: (List<Track>) -> Unit,
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
                    Icon(Icons.Default.Shuffle, contentDescription = "Acak", tint = TextInk)
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
                        onRemoveFromPlaylist = onRemoveTrack?.let { fn -> { fn(t) } },
                    )
                }
            }
        }
    }
}

