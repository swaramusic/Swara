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


internal fun libTabLabel(t: LibTab): String = when (t) {
    LibTab.ALL -> S.all
    LibTab.PLAYLISTS -> S.playlists
    LibTab.SONGS -> S.songs
    LibTab.ARTISTS -> S.artists
}

internal enum class LibSongSubTab { LIKED, RECENT, DOWNLOADS, LOCAL }
internal fun libSubLabel(t: LibSongSubTab): String = when (t) {
    LibSongSubTab.LIKED -> S.liked
    LibSongSubTab.RECENT -> S.recentPlayed
    LibSongSubTab.DOWNLOADS -> S.downloaded
    LibSongSubTab.LOCAL -> S.localFiles
}

@Composable
internal fun LibraryScreen(
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 164.dp),
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
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 164.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    LazyColumn(contentPadding = PaddingValues(bottom = 164.dp)) {
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
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 164.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

internal fun UserPlaylist.coverModel(): Any? = when {
    artworkUri.startsWith("http", ignoreCase = true) -> artworkUri
    artworkUri.isNotBlank() -> java.io.File(artworkUri)
    else -> tracks.firstOrNull()?.artworkUrl
}

@Composable
internal fun PlaylistGridCard(pl: UserPlaylist, primaryColor: Color, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val coverModel: Any? = pl.coverModel()
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            color = CardDark,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            if (coverModel != null) {
                AsyncImage(model = coverModel, contentDescription = pl.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = TextInk, modifier = Modifier.size(42.dp))
                }
            }
        }
        Text(pl.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${pl.tracks.size} ${S.songUnit}", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
    }
}

@Composable
internal fun PlaylistListRow(pl: UserPlaylist, primaryColor: Color, onOpen: () -> Unit, onDelete: () -> Unit) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val coverModel: Any? = pl.coverModel()
            if (coverModel != null) {
                AsyncImage(model = coverModel, contentDescription = pl.name, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = TextInk)
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
internal fun CollectionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, primaryColor: Color, onClick: () -> Unit) {
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
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextInk, modifier = Modifier.size(21.dp))
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
internal fun ArtistGridCard(name: String, imageUrl: String?, primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
internal fun ArtistListRow(name: String, imageUrl: String?, primaryColor: Color, onOpen: () -> Unit, onUnfollow: () -> Unit) {
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
            TextButton(onClick = onUnfollow, colors = ButtonDefaults.textButtonColors(contentColor = TextInk)) {
                Text(S.following, fontSize = 12.sp, color = TextInk)
            }
        }
    }
}

@Composable
internal fun AddArtistRow(primaryColor: Color, onClick: () -> Unit) {
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
                Icon(Icons.Default.Add, contentDescription = null, tint = TextInk)
            }
            Spacer(Modifier.width(12.dp))
            Text(S.addArtist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk)
        }
    }
}

@Composable
internal fun StatsScreen(
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            S.statsTitle,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextInk,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 164.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

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
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
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
                                .background(Color(0xFFFF6D00).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF6D00), modifier = Modifier.size(38.dp))
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
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
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
                    TextButton(onClick = { onPlaySongs(rankedTrackList, 0) }, colors = ButtonDefaults.textButtonColors(contentColor = TextInk)) {
                        Text(S.playAll, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextInk)
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
}

@Composable
internal fun StatCard(value: String, label: String, modifier: Modifier, primaryColor: Color) {
    Surface(
        color = CardDark,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, TextInk.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextInk)
            Text(label, fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
        }
    }
}

@Composable
internal fun HistoryFullScreenOverlay(
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
                    TextButton(onClick = onClearHistory, colors = ButtonDefaults.textButtonColors(contentColor = TextInk)) {
                        Text(S.clearBtn, fontWeight = FontWeight.SemiBold, color = TextInk)
                    }
                    TextButton(onClick = { onPlay(sorted, 0) }, colors = ButtonDefaults.textButtonColors(contentColor = TextInk)) {
                        Text(S.playAll, fontWeight = FontWeight.Bold, color = TextInk)
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
internal fun TopArtistsWithFriendsOverlay(
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
internal fun RecapFullScreenOverlay(
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
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                        shareRecapStoryCard(
                            context = ctx,
                            slideIndex = slide,
                            totalMinutes = totalMinutes,
                            songCount = recentTracks.size,
                            topArtists = topArtists,
                            topTracks = rankedSongs.map { it.first },
                            accentColor = primaryColor,
                        )
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF0D1524), modifier = Modifier.size(18.dp))
                        Text("Share Story", color = Color(0xFF0D1524), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

internal fun shareRecapStoryCard(
    context: android.content.Context,
    slideIndex: Int,
    totalMinutes: Int,
    songCount: Int,
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

    val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(46, 255, 255, 255)
    }
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

    when (slideIndex) {
        0 -> {
            c.drawText("You listened for", 88f, 520f, subPaint)
            c.drawText("$totalMinutes", 88f, 700f, bigNumPaint)
            c.drawText("minutes", 88f, 780f, serifTitlePaint)
            c.drawText("$songCount songs  \u2022  ${topArtists.size} artists", 88f, 880f, subPaint)
        }
        1 -> {
            val t = topTracks.firstOrNull()
            c.drawText("Your #1 Top Song", 88f, 440f, subPaint)
            c.drawRoundRect(android.graphics.RectF(72f, 540f, w - 72f, 1120f), 48f, 48f, glassPaint)
            c.drawText((t?.title ?: "Swara").take(26), 118f, 760f, serifTitlePaint)
            c.drawText((t?.artist ?: "").take(36), 118f, 840f, secHeadPaint)
        }
        2 -> {
            val a = topArtists.firstOrNull()
            c.drawText("Your #1 Top Artist", 88f, 440f, subPaint)
            c.drawRoundRect(android.graphics.RectF(72f, 540f, w - 72f, 1120f), 48f, 48f, glassPaint)
            c.drawText((a?.first ?: "Swara").take(24), 118f, 760f, serifTitlePaint)
            if (a != null) c.drawText("${a.second} plays", 118f, 840f, secHeadPaint)
        }
        3 -> {
            c.drawText("Your Top 5 Songs", 88f, 440f, subPaint)
            c.drawRoundRect(android.graphics.RectF(72f, 540f, w - 72f, 1340f), 48f, 48f, glassPaint)
            topTracks.take(5).forEachIndexed { idx, t ->
                val y = 660f + idx * 125f
                c.drawText("${idx + 1}", 120f, y, rankPaint)
                c.drawText(t.title.take(30), 178f, y - 8f, itemTitlePaint)
                c.drawText(t.artist.take(36), 178f, y + 32f, itemSubPaint)
            }
        }
        4 -> {
            c.drawText("Your Top 5 Artists", 88f, 440f, subPaint)
            c.drawRoundRect(android.graphics.RectF(72f, 540f, w - 72f, 1260f), 48f, 48f, glassPaint)
            topArtists.take(5).forEachIndexed { idx, (name, count) ->
                val y = 660f + idx * 110f
                c.drawText("${idx + 1}", 120f, y, rankPaint)
                c.drawText(name.take(28), 178f, y, itemTitlePaint)
                c.drawText("$count plays", w - 260f, y, itemSubPaint)
            }
        }
        else -> {
            c.drawText("$totalMinutes", 88f, 455f, bigNumPaint)
            c.drawText("minutes listened  \u2022  ${topTracks.size} songs", 88f, 520f, subPaint)
            val panelRect = android.graphics.RectF(72f, 585f, w - 72f, 1690f)
            c.drawRoundRect(panelRect, 48f, 48f, glassPaint)
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
        }
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
