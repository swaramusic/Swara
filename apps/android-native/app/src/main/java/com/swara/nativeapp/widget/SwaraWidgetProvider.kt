package com.swara.nativeapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.swara.nativeapp.MainActivity
import com.swara.nativeapp.R
import com.swara.nativeapp.playback.SwaraPlaybackService

open class SwaraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        refreshWithSettings(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV, ACTION_SHUFFLE, ACTION_REPEAT -> {
                val svcIntent = Intent(context, SwaraPlaybackService::class.java).apply {
                    action = intent.action
                }
                runCatching { context.startService(svcIntent) }
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.swara.nativeapp.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.swara.nativeapp.widget.NEXT"
        const val ACTION_PREV = "com.swara.nativeapp.widget.PREV"
        const val ACTION_SHUFFLE = "com.swara.nativeapp.widget.SHUFFLE"
        const val ACTION_REPEAT = "com.swara.nativeapp.widget.REPEAT"

        @Volatile private var lastTitle: String = "Swara Music"
        @Volatile private var lastArtist: String = "Tap for widget settings"
        @Volatile private var lastIsPlaying: Boolean = false
        @Volatile private var lastArtBitmap: Bitmap? = null
        @Volatile private var lastShuffle: Boolean = false
        @Volatile private var lastRepeat: Int = 0
        @Volatile private var lastPosMs: Long = 0L
        @Volatile private var lastDurMs: Long = 0L

        fun refreshWithSettings(context: Context) {
            updateAllWidgets(
                context,
                lastTitle,
                lastArtist,
                lastIsPlaying,
                lastArtBitmap,
                lastShuffle,
                lastRepeat,
                lastPosMs,
                lastDurMs,
            )
        }

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artwork: Bitmap?,
            shuffleEnabled: Boolean = lastShuffle,
            repeatMode: Int = lastRepeat,
            posMs: Long = lastPosMs,
            durMs: Long = lastDurMs,
        ) {
            lastTitle = title.ifBlank { "Swara Music" }
            lastArtist = artist.ifBlank { "Swara" }
            lastIsPlaying = isPlaying
            lastShuffle = shuffleEnabled
            lastRepeat = repeatMode
            lastPosMs = posMs.coerceAtLeast(0L)
            lastDurMs = durMs.coerceAtLeast(0L)
            if (artwork != null) {
                lastArtBitmap = Bitmap.createScaledBitmap(artwork, 180, 180, true)
            }

            val settings = SwaraWidgetPrefs.load(context)
            val mgr = AppWidgetManager.getInstance(context)
            val openAppIntent = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val openSettingsIntent = PendingIntent.getActivity(
                context,
                16,
                Intent(context, SwaraWidgetConfigActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            data class Spec(
                val cls: Class<out AppWidgetProvider>,
                val layoutRes: Int,
                val bgW: Int,
                val bgH: Int,
                val hasText: Boolean,
                val hasPrevNext: Boolean,
                val hasShuffleRepeat: Boolean,
                val hasProgress: Boolean,
            )

            val specs = listOf(
                Spec(SwaraWidgetProvider::class.java, R.layout.swara_widget, 520, 150, true, true, false, true),
                Spec(SwaraLargeWidgetProvider::class.java, R.layout.swara_widget_large, 520, 280, true, true, true, true),
                Spec(SwaraSquareWidgetProvider::class.java, R.layout.swara_widget_square, 280, 280, true, true, false, true),
                Spec(SwaraTallWidgetProvider::class.java, R.layout.swara_widget_tall, 280, 520, true, true, false, true),
                Spec(SwaraOneByOneWidgetProvider::class.java, R.layout.swara_widget_1x1, 160, 160, false, false, false, false),
            )

            for (sp in specs) {
                val ids = mgr.getAppWidgetIds(ComponentName(context, sp.cls))
                if (ids.isEmpty()) continue
                val bgBmp = SwaraWidgetPrefs.renderWidgetBackground(sp.bgW, sp.bgH, settings, lastArtBitmap)
                val shapedCover = lastArtBitmap?.let { SwaraWidgetPrefs.shapeCoverBitmap(it, settings.coverShape) }
                val textColor = if (settings.style == WidgetStyle.LIGHT) Color.parseColor("#111827") else Color.WHITE
                val subTextColor = if (settings.style == WidgetStyle.LIGHT) Color.parseColor("#6B7280") else Color.parseColor("#BBFFFFFF")

                val views = RemoteViews(context.packageName, sp.layoutRes).apply {
                    setImageViewBitmap(R.id.widget_bg, bgBmp)
                    setOnClickPendingIntent(R.id.widget_root, openSettingsIntent)

                    if (settings.showCover) {
                        setViewVisibility(R.id.widget_art, View.VISIBLE)
                        if (shapedCover != null) {
                            setImageViewBitmap(R.id.widget_art, shapedCover)
                        } else {
                            setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
                        }
                        setOnClickPendingIntent(R.id.widget_art, openAppIntent)
                    } else {
                        setViewVisibility(R.id.widget_art, View.GONE)
                    }

                    if (sp.hasText) {
                        setTextViewText(R.id.widget_title, lastTitle)
                        setTextViewText(R.id.widget_artist, lastArtist)
                        setTextColor(R.id.widget_title, textColor)
                        setTextColor(R.id.widget_artist, subTextColor)
                        setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP, settings.textSize.titleSp)
                        setTextViewTextSize(R.id.widget_artist, android.util.TypedValue.COMPLEX_UNIT_SP, settings.textSize.artistSp)
                        setOnClickPendingIntent(R.id.widget_text_col, openSettingsIntent)
                    }

                    setImageViewResource(
                        R.id.widget_play_pause,
                        if (lastIsPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                    )
                    if (settings.playStyle == WidgetPlayStyle.PLAIN) {
                        setInt(R.id.widget_play_pause, "setBackgroundResource", android.R.color.transparent)
                    } else {
                        setInt(R.id.widget_play_pause, "setBackgroundResource", R.drawable.swara_widget_play_bg)
                    }
                    setOnClickPendingIntent(R.id.widget_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE, 12))

                    if (sp.hasPrevNext) {
                        val vis = if (settings.showPrevNext) View.VISIBLE else View.GONE
                        setViewVisibility(R.id.widget_prev, vis)
                        setViewVisibility(R.id.widget_next, vis)
                        setOnClickPendingIntent(R.id.widget_prev, actionPendingIntent(context, ACTION_PREV, 11))
                        setOnClickPendingIntent(R.id.widget_next, actionPendingIntent(context, ACTION_NEXT, 13))
                    }

                    if (sp.hasShuffleRepeat) {
                        setOnClickPendingIntent(R.id.widget_shuffle, actionPendingIntent(context, ACTION_SHUFFLE, 14))
                        setOnClickPendingIntent(R.id.widget_repeat, actionPendingIntent(context, ACTION_REPEAT, 15))
                        setInt(R.id.widget_shuffle, "setAlpha", if (lastShuffle) 255 else 130)
                        setInt(R.id.widget_repeat, "setAlpha", if (lastRepeat != 0) 255 else 130)
                    }

                    if (sp.hasProgress) {
                        val showRow = settings.showProgress || settings.showTime
                        setViewVisibility(R.id.widget_progress_row, if (showRow) View.VISIBLE else View.GONE)
                        setViewVisibility(R.id.widget_progress, if (settings.showProgress) View.VISIBLE else View.INVISIBLE)
                        setViewVisibility(R.id.widget_time_pos, if (settings.showTime) View.VISIBLE else View.GONE)
                        setViewVisibility(R.id.widget_time_dur, if (settings.showTime) View.VISIBLE else View.GONE)
                        val pct = if (lastDurMs > 0L) ((lastPosMs * 100L) / lastDurMs).toInt().coerceIn(0, 100) else 0
                        setProgressBar(R.id.widget_progress, 100, pct, false)
                        setTextViewText(R.id.widget_time_pos, fmtTime(lastPosMs))
                        setTextViewText(R.id.widget_time_dur, fmtTime(lastDurMs))
                        setTextColor(R.id.widget_time_pos, subTextColor)
                        setTextColor(R.id.widget_time_dur, subTextColor)
                    }
                }
                mgr.updateAppWidget(ids, views)
            }
        }

        private fun fmtTime(ms: Long): String {
            val s = (ms / 1000L).coerceAtLeast(0L)
            return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
        }

        private fun actionPendingIntent(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, SwaraWidgetProvider::class.java).apply { this.action = action }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

class SwaraLargeWidgetProvider : SwaraWidgetProvider()

class SwaraSquareWidgetProvider : SwaraWidgetProvider()

class SwaraTallWidgetProvider : SwaraWidgetProvider()

class SwaraOneByOneWidgetProvider : SwaraWidgetProvider()
