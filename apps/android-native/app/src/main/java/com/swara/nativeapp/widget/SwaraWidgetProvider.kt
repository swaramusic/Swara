package com.swara.nativeapp.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.swara.nativeapp.MainActivity
import com.swara.nativeapp.R
import com.swara.nativeapp.playback.SwaraPlaybackService

class SwaraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, lastTitle, lastArtist, lastIsPlaying, lastArtBitmap)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV -> {
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

        @Volatile private var lastTitle: String = "Swara Music"
        @Volatile private var lastArtist: String = "Tap to open player"
        @Volatile private var lastIsPlaying: Boolean = false
        @Volatile private var lastArtBitmap: Bitmap? = null

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artwork: Bitmap?,
        ) {
            lastTitle = title.ifBlank { "Swara Music" }
            lastArtist = artist.ifBlank { "Swara" }
            lastIsPlaying = isPlaying
            if (artwork != null) {
                lastArtBitmap = Bitmap.createScaledBitmap(artwork, 160, 160, true)
            }

            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, SwaraWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val openIntent = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            val views = RemoteViews(context.packageName, R.layout.swara_widget).apply {
                setTextViewText(R.id.widget_title, lastTitle)
                setTextViewText(R.id.widget_artist, lastArtist)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                val bmp = lastArtBitmap
                if (bmp != null) {
                    setImageViewBitmap(R.id.widget_art, bmp)
                } else {
                    setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
                }
                setOnClickPendingIntent(R.id.widget_art, openIntent)
                setOnClickPendingIntent(R.id.widget_text_col, openIntent)
                setOnClickPendingIntent(R.id.widget_prev, actionPendingIntent(context, ACTION_PREV, 11))
                setOnClickPendingIntent(R.id.widget_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE, 12))
                setOnClickPendingIntent(R.id.widget_next, actionPendingIntent(context, ACTION_NEXT, 13))
            }
            mgr.updateAppWidget(ids, views)
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
