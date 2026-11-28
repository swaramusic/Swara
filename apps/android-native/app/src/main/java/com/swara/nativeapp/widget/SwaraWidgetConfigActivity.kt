package com.swara.nativeapp.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class SwaraWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            WidgetSettingsScreen(
                initial = SwaraWidgetPrefs.load(this),
                onSave = { updated ->
                    SwaraWidgetPrefs.save(this, updated)
                    SwaraWidgetProvider.refreshWithSettings(this)
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, result)
                    }
                    finish()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetSettingsScreen(
    initial: SwaraWidgetSettings,
    onSave: (SwaraWidgetSettings) -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    val bgPage = Color(0xFF0B111E)
    val ink = Color(0xFFF8FAFC)
    val cardBg = Color(0xFF151E31)
    val accentColors = listOf(
        0xFF7C3AED.toInt(),
        0xFF2563EB.toInt(),
        0xFF0D9488.toInt(),
        0xFF16A34A.toInt(),
        0xFFF59E0B.toInt(),
        0xFFEA580C.toInt(),
        0xFFEC4899.toInt(),
        0xFFDC2626.toInt(),
    )

    Surface(color = bgPage, contentColor = ink, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text("Widget settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ink)
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WidgetLivePreview(s)

                Text(
                    "Size comes from the widget you picked in the widget list (4×2, 4×1, 2×2, 2×4, or 1×1). Long-press the widget to resize or tap it anytime to reopen settings.",
                    fontSize = 12.sp,
                    color = ink.copy(alpha = 0.65f),
                    lineHeight = 17.sp,
                )

                SectionTitle("Style", ink)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WidgetStyle.entries.forEach { st ->
                        ChoiceChip(
                            text = st.label,
                            selected = s.style == st,
                            cardBg = cardBg,
                            ink = ink,
                            onClick = { s = s.copy(style = st) },
                        )
                    }
                }

                SectionTitle("Accent colour", ink)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChoiceChip(
                        text = "From the cover",
                        selected = s.accentHex == null,
                        cardBg = cardBg,
                        ink = ink,
                        onClick = { s = s.copy(accentHex = null) },
                    )
                    accentColors.forEach { hex ->
                        val selected = s.accentHex == hex
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .then(
                                    if (selected) Modifier.border(2.5.dp, Color.White, CircleShape)
                                    else Modifier,
                                )
                                .clickable { s = s.copy(accentHex = hex) },
                        )
                    }
                }

                Column {
                    Text("Corner roundness: ${s.cornerRoundnessPct}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink)
                    Slider(
                        value = s.cornerRoundnessPct.toFloat(),
                        onValueChange = { s = s.copy(cornerRoundnessPct = it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF60A5FA), activeTrackColor = Color(0xFF2563EB)),
                    )
                }

                Column {
                    Text("Frost (cover blur): ${s.frostPct}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ink)
                    Slider(
                        value = s.frostPct.toFloat(),
                        onValueChange = { s = s.copy(frostPct = it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF60A5FA), activeTrackColor = Color(0xFF2563EB)),
                    )
                }

                SectionTitle("Cover shape", ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetCoverShape.entries.forEach { sh ->
                        ChoiceChip(sh.label, s.coverShape == sh, cardBg, ink) { s = s.copy(coverShape = sh) }
                    }
                }

                SectionTitle("Text size", ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetTextSize.entries.forEach { ts ->
                        ChoiceChip(ts.label, s.textSize == ts, cardBg, ink) { s = s.copy(textSize = ts) }
                    }
                }

                SectionTitle("Play button", ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WidgetPlayStyle.entries.forEach { ps ->
                        ChoiceChip(ps.label, s.playStyle == ps, cardBg, ink) { s = s.copy(playStyle = ps) }
                    }
                }

                ToggleRow("Show cover", s.showCover, ink) { s = s.copy(showCover = it) }
                ToggleRow("Show progress bar", s.showProgress, ink) { s = s.copy(showProgress = it) }
                ToggleRow("Show time", s.showTime, ink) { s = s.copy(showTime = it) }
                ToggleRow("Show previous and next", s.showPrevNext, ink) { s = s.copy(showPrevNext = it) }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { onSave(s) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun WidgetLivePreview(s: SwaraWidgetSettings) {
    val accent = Color(s.accentHex ?: 0xFF2563EB.toInt())
    val cornerDp = (24f * (s.cornerRoundnessPct / 100f)).dp
    val isLight = s.style == WidgetStyle.LIGHT
    val textColor = if (isLight) Color(0xFF111827) else Color.White
    val bgBrush = when (s.style) {
        WidgetStyle.LIGHT -> Brush.linearGradient(listOf(Color(0xFFF3F4F6), Color(0xFFE5E7EB)))
        WidgetStyle.DARK -> Brush.linearGradient(listOf(Color(0xFF111827), Color(0xFF0F172A)))
        WidgetStyle.CLEAR_GLASS -> Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), Color(0x660F172A)))
        WidgetStyle.COVER_COLOUR -> Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.45f)))
        WidgetStyle.GLASS -> Brush.linearGradient(listOf(Color(0xCC1E293B), accent.copy(alpha = 0.55f)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF785A28), Color(0xFF3E2D14))))
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(cornerDp))
                .background(bgBrush)
                .border(1.dp, if (isLight) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.25f), RoundedCornerShape(cornerDp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.showCover) {
                    val coverShape = when (s.coverShape) {
                        WidgetCoverShape.CIRCLE -> CircleShape
                        WidgetCoverShape.SQUARE -> RoundedCornerShape(0.dp)
                        WidgetCoverShape.ROUNDED -> RoundedCornerShape(10.dp)
                    }
                    Box(
                        modifier = Modifier.size(46.dp).clip(coverShape).background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = textColor)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Song title", fontWeight = FontWeight.Bold, fontSize = s.textSize.titleSp.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Artist name", fontSize = s.textSize.artistSp.sp, color = textColor.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.showPrevNext) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .then(
                            if (s.playStyle == WidgetPlayStyle.ROUND) Modifier.clip(CircleShape).background(Color.White)
                            else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (s.playStyle == WidgetPlayStyle.ROUND) Color.Black else textColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (s.showPrevNext) {
                    Icon(Icons.Default.SkipNext, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                }
            }
            if (s.showProgress || s.showTime) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.showTime) Text("1:10", fontSize = 10.sp, color = textColor.copy(alpha = 0.72f))
                    if (s.showProgress) {
                        LinearProgressIndicator(
                            progress = { 0.38f },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(50)),
                            color = textColor,
                            trackColor = textColor.copy(alpha = 0.25f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (s.showTime) Text("3:20", fontSize = 10.sp, color = textColor.copy(alpha = 0.72f))
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, ink: Color) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ink)
}

@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    cardBg: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) Color(0xFF2563EB).copy(alpha = 0.26f) else cardBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF60A5FA) else ink.copy(alpha = 0.14f)),
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = ink,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, ink: Color, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 15.sp, color = ink)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
