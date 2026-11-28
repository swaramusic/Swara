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

open class SwaraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context, lastTitle, lastArtist, lastIsPlaying, lastArtBitmap, lastShuffle, lastRepeat)
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
        @Volatile private var lastArtist: String = "Tap to open player"
        @Volatile private var lastIsPlaying: Boolean = false
        @Volatile private var lastArtBitmap: Bitmap? = null
        @Volatile private var lastShuffle: Boolean = false
        @Volatile private var lastRepeat: Int = 0

        fun updateAllWidgets(
            context: Context,
            title: String,
            artist: String,
            isPlaying: Boolean,
            artwork: Bitmap?,
            shuffleEnabled: Boolean = lastShuffle,
            repeatMode: Int = lastRepeat,
        ) {
            lastTitle = title.ifBlank { "Swara Music" }
            lastArtist = artist.ifBlank { "Swara" }
            lastIsPlaying = isPlaying
            lastShuffle = shuffleEnabled
            lastRepeat = repeatMode
            if (artwork != null) {
                lastArtBitmap = Bitmap.createScaledBitmap(artwork, 160, 160, true)
            }

            val mgr = AppWidgetManager.getInstance(context)
            val openIntent = PendingIntent.getActivity(
                context,
                10,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

            updateProvider(context, mgr, SwaraWidgetProvider::class.java, R.layout.swara_widget, openIntent, hasPrev = true, hasArtist = true, hasShuffleRepeat = false)
            updateProvider(context, mgr, SwaraLargeWidgetProvider::class.java, R.layout.swara_widget_large, openIntent, hasPrev = true, hasArtist = true, hasShuffleRepeat = true)
            updateProvider(context, mgr, SwaraMiniWidgetProvider::class.java, R.layout.swara_widget_mini, openIntent, hasPrev = false, hasArtist = false, hasShuffleRepeat = false)
        }

        private fun updateProvider(
            context: Context,
            mgr: AppWidgetManager,
            cls: Class<out AppWidgetProvider>,
            layoutRes: Int,
            openIntent: PendingIntent,
            hasPrev: Boolean,
            hasArtist: Boolean,
            hasShuffleRepeat: Boolean,
        ) {
            val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, layoutRes).apply {
                setTextViewText(R.id.widget_title, lastTitle)
                if (hasArtist) setTextViewText(R.id.widget_artist, lastArtist)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (lastIsPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                )
                val bmp = lastArtBitmap
                if (bmp != null) {
                    setImageViewBitmap(R.id.widget_art, bmp)
                } else {
                    setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher)
                }
                setOnClickPendingIntent(R.id.widget_art, openIntent)
                setOnClickPendingIntent(R.id.widget_text_col, openIntent)
                setOnClickPendingIntent(R.id.widget_play_pause, actionPendingIntent(context, ACTION_PLAY_PAUSE, 12))
                setOnClickPendingIntent(R.id.widget_next, actionPendingIntent(context, ACTION_NEXT, 13))
                if (hasPrev) {
                    setOnClickPendingIntent(R.id.widget_prev, actionPendingIntent(context, ACTION_PREV, 11))
                }
                if (hasShuffleRepeat) {
                    setOnClickPendingIntent(R.id.widget_shuffle, actionPendingIntent(context, ACTION_SHUFFLE, 14))
                    setOnClickPendingIntent(R.id.widget_repeat, actionPendingIntent(context, ACTION_REPEAT, 15))
                    setInt(R.id.widget_shuffle, "setAlpha", if (lastShuffle) 255 else 130)
                    setInt(R.id.widget_repeat, "setAlpha", if (lastRepeat != 0) 255 else 130)
                }
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

class SwaraLargeWidgetProvider : SwaraWidgetProvider()

class SwaraMiniWidgetProvider : SwaraWidgetProvider()
