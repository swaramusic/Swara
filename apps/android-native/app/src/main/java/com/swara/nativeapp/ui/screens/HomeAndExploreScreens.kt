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


internal fun greetingText(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 4..10 -> S.goodMorning
    in 11..14 -> S.goodAfternoon
    in 15..18 -> S.goodEvening
    else -> S.goodNight
}

@Composable
internal fun HomeScreen(
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
    glowColor: Color = primaryColor,
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
        contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "home_header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    S.home,
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

        // SoundCloud-inspired "Your likes" + 2x2 quick-pick grid
        val quickGrid = recent.filter { it.browseId.isBlank() }.ifEmpty {
            shelves.flatMap { it.tracks }.filter { it.browseId.isBlank() }
        }.distinctBy { it.id }.take(4)

        if (quickGrid.isNotEmpty()) {
            if (likedIds.isNotEmpty()) {
                item(key = "your_likes_banner") {
                    Surface(
                        color = CardDark,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlay(quickGrid.shuffled(), 0) }
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.28f), CardDark)))
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = TextInk, modifier = Modifier.size(20.dp))
                            }
                            Text(
                                S.liked,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextInk,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                color = TextInk.copy(alpha = 0.1f),
                                shape = CircleShape,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = TextInk, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                item(key = "get_back_header") {
                    Text(
                        if (recent.isNotEmpty()) S.resume else S.quickPlay,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextInk,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
                    )
                }
            }
            item(key = "quick_2x2_grid") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickGrid.chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { t ->
                                val idx = quickGrid.indexOf(t)
                                Surface(
                                    color = CardDark,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onPlay(quickGrid, idx) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        AsyncImage(
                                            model = t.artworkUrl,
                                            contentDescription = t.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(t.artist, fontSize = 10.sp, color = TextInk.copy(alpha = 0.62f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Trending by genre / mood chips
        item(key = "trending_genre_header") {
            Text(S.trendingForYou, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
        item(key = "mood_chips_row") {
            val moodLabels = mapOf(
                "Semua" to S.moodAll,
                "Santai" to S.moodChill,
                "Energi" to S.moodEnergy,
                "Fokus" to S.moodFocus,
                "Romantis" to S.moodRomantic,
                "Galau" to S.moodSad,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MOOD_CHIPS, key = { it }) { mood ->
                    val selected = mood == activeMood
                    Surface(
                        color = if (selected) primaryColor.copy(alpha = 0.16f) else Color.Transparent,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) primaryColor else TextInk.copy(alpha = 0.2f)),
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onSelectMood(mood) }
                    ) {
                        Text(
                            (moodLabels[mood] ?: mood).uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextInk,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // TODAY'S PICK - Hot For You featured card (from reference screenshot 2)
        val hotTrack = resumeTrack ?: quickGrid.firstOrNull()
        if (hotTrack != null && activeMood == "Semua") {
            item(key = "hot_for_you") {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("TODAY'S PICK", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = TextInk.copy(alpha = 0.55f))
                    Text("Hot For You", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextInk)
                    Surface(
                        color = CardDark,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onPlay(listOf(hotTrack), 0) }
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(listOf(primaryColor.copy(alpha = 0.28f), glowColor.copy(alpha = 0.16f), CardDark)))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(modifier = Modifier.width(86.dp).height(70.dp), contentAlignment = Alignment.CenterStart) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10131A))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                )
                                AsyncImage(
                                    model = hotTrack.artworkUrl,
                                    contentDescription = hotTrack.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(70.dp).clip(RoundedCornerShape(10.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(hotTrack.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(hotTrack.artist, fontSize = 12.sp, color = TextInk.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Surface(
                                color = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onPlay(listOf(hotTrack), 0) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = S.playBtn, tint = Color.Black, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
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
internal fun OverlayTrackCard(track: Track, onClick: () -> Unit) {
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
internal fun ShelfHeader(title: String, showPlayAll: Boolean, onPlayAll: () -> Unit) {
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

internal val BROWSE_CATEGORIES = listOf(
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
internal fun ExploreScreen(
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
                    Icon(Icons.Default.Mic, contentDescription = null, tint = TextInk, modifier = Modifier.size(16.dp))
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
                                    Text("Clear", fontSize = 12.sp, color = TextInk)
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
                                            Icon(Icons.Default.History, contentDescription = null, tint = TextInk, modifier = Modifier.size(14.dp))
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

internal enum class LibTab { ALL, PLAYLISTS, SONGS, ARTISTS }
