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
internal fun SettingsScreen(
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
    romanizeLyrics: Boolean,
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
    onToggleRomanizeLyrics: (Boolean) -> Unit,
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

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            S.settingsTitle,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = TextInk,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

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
                        listOf("high" to "Best (Max)", "medium" to "Normal (128k)", "low" to "Saver (48k)").forEach { (id, label) ->
                            val selected = audioQuality == id
                            Surface(
                                color = if (selected) primaryColor else TextInk.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).clickable { onSelectAudioQuality(id) }
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) Color.White else TextInk, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                    Text(
                        "Streaming selects highest source bitrate (~160–256kbps Opus/AAC). Local FLAC/WAV plays in native Lossless.",
                        fontSize = 11.sp,
                        color = TextInk.copy(alpha = 0.55f),
                        lineHeight = 15.sp,
                    )
                    SettingsFlatRow(Icons.Default.Tune, S.soundStyleBtn, primaryColor, onClick = onOpenSoundStyle)
                    SettingsFlatRow(Icons.Default.GraphicEq, S.eqBtn, primaryColor, onClick = onOpenEq)
                    val sleepLabel = if (sleepRemainingSec > 0) "Sleep Timer: ${formatMs(sleepRemainingSec * 1000L)}" else S.sleepTimerOff
                    SettingsFlatRow(Icons.Default.Bedtime, sleepLabel, primaryColor, onClick = onCycleSleepTimer)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = TextInk, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Romanize Lyrics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk)
                            Text("Romaji & Hangul below lyrics", fontSize = 12.sp, color = TextInk.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = romanizeLyrics,
                            onCheckedChange = onToggleRomanizeLyrics,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = primaryColor)
                        )
                    }
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
                        DiscordGlyphIcon(tint = TextInk.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Discord", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextInk.copy(alpha = 0.65f))
                            Text("Profile activity \u00B7 Soon", fontSize = 12.sp, color = TextInk.copy(alpha = 0.45f))
                        }
                        Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                            Text("Soon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
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
                                    text = { Text(label, fontWeight = if (code == language) FontWeight.Bold else FontWeight.Normal, color = TextInk) },
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
                                    text = { Text("$code \u2022 $label", fontWeight = if (code == country) FontWeight.Bold else FontWeight.Normal, color = TextInk) },
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
        val settingsCtx = androidx.compose.ui.platform.LocalContext.current
        val currentVersion = remember(settingsCtx) {
            runCatching {
                settingsCtx.packageManager.getPackageInfo(settingsCtx.packageName, 0).versionName ?: "1.0.0"
            }.getOrDefault("1.0.0")
        }
        SettingsGroupHeader(
            icon = Icons.Default.Info,
            title = S.aboutTitle,
            subtitle = S.aboutSubtitle.replace("1.0.0", currentVersion),
            expanded = false,
            primaryColor = primaryColor,
            onClick = onOpenAbout
        )
        Spacer(Modifier.height(140.dp))
    }
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
                    colors = ButtonDefaults.textButtonColors(contentColor = TextInk)
                ) { Text("Terapkan", color = TextInk) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserPlaylistOverlay(
    playlist: UserPlaylist,
    likedIds: Set<String>,
    downloadedIds: Set<String>,
    downloadingIds: Set<String>,
    currentTrack: Track?,
    isPlaying: Boolean,
    primaryColor: Color,
    onClose: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onShufflePlay: (List<Track>) -> Unit,
    onPlayNextList: (List<Track>) -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onToggleLike: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onDownloadAll: () -> Unit,
    onSaveToPlaylist: (Track) -> Unit,
    onRemoveTrack: (Track) -> Unit,
    onEditPlaylist: (String, Uri?) -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("default") }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember(playlist.id, playlist.name) { mutableStateOf(playlist.name) }
    var pickedCoverUri by remember(playlist.id) { mutableStateOf<Uri?>(null) }

    val coverPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) pickedCoverUri = uri }

    val displayedTracks = remember(playlist.tracks, searchQuery, sortMode) {
        val base = when (sortMode) {
            "newest" -> playlist.tracks.reversed()
            "oldest" -> playlist.tracks
            "title" -> playlist.tracks.sortedBy { it.title.lowercase() }
            "artist" -> playlist.tracks.sortedBy { it.artist.lowercase() }
            else -> playlist.tracks
        }
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) base else base.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
    }

    val coverModel: Any? = when {
        playlist.artworkUri.isNotBlank() -> java.io.File(playlist.artworkUri)
        else -> playlist.tracks.firstOrNull()?.artworkUrl
    }
    val allDownloaded = playlist.tracks.isNotEmpty() && playlist.tracks.all { it.id in downloadedIds }
    val anyDownloading = playlist.tracks.any { it.id in downloadingIds }
    val playlistActive = currentTrack != null && displayedTracks.any { it.id == currentTrack.id }

    Surface(color = BgDark, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(primaryColor.copy(alpha = 0.22f), BgDark, BgDark)))
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (currentTrack != null) 108.dp else 40.dp)
            ) {
            item(key = "pl_top_bar") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextInk)
                    }
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search playlist...", color = TextInk.copy(alpha = 0.5f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = TextInk.copy(alpha = 0.2f),
                                focusedTextColor = TextInk,
                                unfocusedTextColor = TextInk,
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { searchOpen = false; searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Search", tint = TextInk)
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searchOpen = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = TextInk)
                        }
                    }
                }
            }

            item(key = "pl_hero") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = CardDark,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(188.dp)
                    ) {
                        if (coverModel != null) {
                            AsyncImage(model = coverModel, contentDescription = playlist.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = TextInk, modifier = Modifier.size(56.dp))
                            }
                        }
                    }
                    Text(playlist.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextInk, textAlign = TextAlign.Center)
                    Text("${playlist.tracks.size} ${S.songUnit}", fontSize = 13.sp, color = TextInk.copy(alpha = 0.65f))

                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = TextInk.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(enabled = !anyDownloading, onClick = onDownloadAll)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (anyDownloading) {
                                    CircularProgressIndicator(color = primaryColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(
                                        if (allDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                        contentDescription = "Download Playlist",
                                        tint = TextInk,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            }
                        }
                        Surface(
                            color = TextInk.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable {
                                editName = playlist.name
                                pickedCoverUri = null
                                showEditDialog = true
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Playlist", tint = TextInk, modifier = Modifier.size(20.dp))
                            }
                        }
                        Surface(
                            color = TextInk,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp).clip(CircleShape).clickable {
                                if (playlistActive) onTogglePlayPause()
                                else if (displayedTracks.isNotEmpty()) onPlay(displayedTracks, 0)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (playlistActive && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = S.playBtn,
                                    tint = BgDark,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Surface(
                            color = TextInk.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { onShufflePlay(displayedTracks) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = TextInk, modifier = Modifier.size(20.dp))
                            }
                        }
                        Surface(
                            color = TextInk.copy(alpha = 0.1f),
                            shape = CircleShape,
                            modifier = Modifier.size(44.dp).clip(CircleShape).clickable { showMenuSheet = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = TextInk, modifier = Modifier.size(21.dp))
                            }
                        }
                    }
                }
            }

            itemsIndexed(displayedTracks, key = { idx, t -> "${playlist.id}_${idx}_${t.id}" }) { idx, t ->
                TrackRow(
                    track = t,
                    isLiked = t.id in likedIds,
                    isDownloaded = t.id in downloadedIds,
                    isDownloading = t.id in downloadingIds,
                    primaryColor = primaryColor,
                    isCurrent = currentTrack?.id == t.id,
                    onClick = { onPlay(displayedTracks, idx) },
                    onToggleLike = { onToggleLike(t) },
                    onAddToQueue = { onAddToQueue(t) },
                    onDownload = { onDownloadTrack(t) },
                    onSaveToPlaylist = { onSaveToPlaylist(t) },
                    onRemoveFromPlaylist = { onRemoveTrack(t) },
                )
            }
        }

            if (currentTrack != null) {
                Surface(
                    color = CardDark.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(28.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(onClick = onOpenPlayer)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AsyncImage(
                            model = currentTrack.artworkUrl,
                            contentDescription = currentTrack.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(currentTrack.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(currentTrack.artist, fontSize = 11.sp, color = TextInk.copy(alpha = 0.68f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            color = TextInk,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onTogglePlayPause)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = BgDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextInk, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            containerColor = CardDark,
            contentColor = TextInk
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (coverModel != null) {
                        AsyncImage(model = coverModel, contentDescription = playlist.name, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextInk)
                        Text("${playlist.tracks.size} ${S.songUnit}", fontSize = 12.sp, color = TextInk.copy(alpha = 0.65f))
                    }
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.08f))
                PlaylistMenuRow(Icons.Default.PlaylistPlay, "Play next") {
                    showMenuSheet = false; onPlayNextList(displayedTracks)
                }
                val sortLabel = when (sortMode) {
                    "newest" -> "Sort: Newest first"
                    "oldest" -> "Sort: Oldest first"
                    "title" -> "Sort: Title"
                    "artist" -> "Sort: Artist"
                    else -> "Sort: Default ordering"
                }
                PlaylistMenuRow(Icons.Default.Sort, sortLabel) {
                    showMenuSheet = false
                    showSortSheet = true
                }
                PlaylistMenuRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add all to queue") {
                    showMenuSheet = false; displayedTracks.forEach { onAddToQueue(it) }
                }
                PlaylistMenuRow(Icons.Default.Download, "Download playlist") {
                    showMenuSheet = false; onDownloadAll()
                }
                PlaylistMenuRow(Icons.Default.Edit, "Edit playlist") {
                    showMenuSheet = false
                    editName = playlist.name
                    pickedCoverUri = null
                    showEditDialog = true
                }
            }
        }
    }

    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = CardDark,
            contentColor = TextInk
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sort track list by", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextInk, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showSortSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextInk)
                    }
                }
                HorizontalDivider(color = TextInk.copy(alpha = 0.08f))
                listOf(
                    "default" to "Default ordering",
                    "newest" to "Newest first",
                    "oldest" to "Oldest first",
                    "title" to "Title",
                    "artist" to "Artist",
                ).forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sortMode = id; showSortSheet = false }
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (sortMode == id) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
                        } else {
                            Spacer(Modifier.size(22.dp))
                        }
                        Text(label, fontSize = 16.sp, fontWeight = if (sortMode == id) FontWeight.Bold else FontWeight.Normal, color = TextInk)
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            containerColor = CardDark,
            titleContentColor = TextInk,
            textContentColor = TextInk,
            title = { Text("Edit Playlist", fontWeight = FontWeight.Bold, color = TextInk) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val previewModel: Any? = pickedCoverUri ?: coverModel
                    Surface(
                        color = BgDark,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)).clickable { coverPicker.launch("image/*") }
                    ) {
                        if (previewModel != null) {
                            AsyncImage(model = previewModel, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Pick Photo", tint = TextInk, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { coverPicker.launch("image/*") },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextInk)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih Foto Cover", color = TextInk, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Nama Playlist", color = TextInk.copy(alpha = 0.7f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = TextInk.copy(alpha = 0.25f),
                            focusedTextColor = TextInk,
                            unfocusedTextColor = TextInk,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEditPlaylist(editName, pickedCoverUri)
                    showEditDialog = false
                }) { Text("Simpan", color = TextInk, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Batal", color = TextInk.copy(alpha = 0.7f)) }
            }
        )
    }
}

@Composable
internal fun PlaylistSheetContent(
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
                        onRemoveFromPlaylist = onRemoveTrack?.let { fn -> { fn(t) } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutOverlay(
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
                    Icon(Icons.Default.Update, contentDescription = null, tint = TextInk, modifier = Modifier.size(22.dp))
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
                    GitHubLogoIcon(tint = TextInk, modifier = Modifier.size(22.dp))
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
                    Icon(Icons.Default.Public, contentDescription = "Website", tint = TextInk, modifier = Modifier.size(22.dp))
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
internal fun StorageOverlay(
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
        b >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", b / (1024.0 * 1024.0 * 1024.0))
        b >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.0f MB", b / (1024.0 * 1024.0))
        b >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }
    fun fmtMaxMb(mb: Int): String = if (mb >= 1024) String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0) else "$mb MB"

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
                var confirmStorageAction by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
                confirmStorageAction?.let { (msg, action) ->
                    AlertDialog(
                        onDismissRequest = { confirmStorageAction = null },
                        containerColor = CardDark,
                        titleContentColor = TextInk,
                        textContentColor = TextInk,
                        title = { Text("Konfirmasi Hapus", fontWeight = FontWeight.Bold) },
                        text = { Text(msg, fontSize = 14.sp, color = TextInk.copy(alpha = 0.82f)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmStorageAction = null
                                action()
                                refreshTick++
                            }) { Text("Hapus", color = Color(0xFFFF5A5F), fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmStorageAction = null }) { Text("Batal", color = TextInk) }
                        }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        confirmStorageAction = "Hapus semua lagu yang sudah diunduh?" to onClearDownloads
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
                        confirmStorageAction = "Hapus semua riwayat pemutaran?" to onClearHistory
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
internal fun GitHubLogoIcon(tint: Color, modifier: Modifier = Modifier) {
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
internal fun SettingsGroupHeader(
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
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TextInk, modifier = Modifier.size(20.dp))
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
internal fun PlaylistMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextInk, modifier = Modifier.size(24.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = TextInk)
    }
}

@Composable
internal fun SettingsFlatRow(
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
        Icon(icon, contentDescription = null, tint = TextInk, modifier = Modifier.size(20.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextInk, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextInk.copy(alpha = 0.55f))
    }
}

@Composable
internal fun DiscordGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
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
internal fun DiscordSettingsOverlay(
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
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
                        Text("Soon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextInk, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
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
                    DiscordGlyphIcon(tint = TextInk, modifier = Modifier.size(20.dp))
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
                            Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextInk)
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
