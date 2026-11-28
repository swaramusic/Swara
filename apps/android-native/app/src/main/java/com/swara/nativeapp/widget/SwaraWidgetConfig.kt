package com.swara.nativeapp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader

enum class WidgetStyle(val label: String) {
    GLASS("Glass"),
    CLEAR_GLASS("Clear glass"),
    DARK("Dark"),
    LIGHT("Light"),
    COVER_COLOUR("Cover colour"),
}

enum class WidgetCoverShape(val label: String) {
    ROUNDED("Rounded"),
    CIRCLE("Circle"),
    SQUARE("Square"),
}

enum class WidgetTextSize(val label: String, val titleSp: Float, val artistSp: Float) {
    SMALL("Small", 12f, 10.5f),
    MEDIUM("Medium", 14.5f, 12f),
    LARGE("Large", 17f, 13.5f),
}

enum class WidgetPlayStyle(val label: String) {
    ROUND("Round"),
    PLAIN("Plain"),
}

data class SwaraWidgetSettings(
    val style: WidgetStyle = WidgetStyle.GLASS,
    val accentHex: Int? = null, // null = "From the cover"
    val frostPct: Int = 40,
    val cornerRoundnessPct: Int = 66,
    val coverShape: WidgetCoverShape = WidgetCoverShape.ROUNDED,
    val textSize: WidgetTextSize = WidgetTextSize.MEDIUM,
    val playStyle: WidgetPlayStyle = WidgetPlayStyle.ROUND,
    val showCover: Boolean = true,
    val showProgress: Boolean = true,
    val showTime: Boolean = true,
    val showPrevNext: Boolean = true,
)

object SwaraWidgetPrefs {
    private const val PREFS = "swara_widget_prefs"

    fun load(context: Context): SwaraWidgetSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val style = runCatching { WidgetStyle.valueOf(p.getString("style", WidgetStyle.GLASS.name)!!) }
            .getOrDefault(WidgetStyle.GLASS)
        val hasAccent = p.contains("accent_hex")
        val accent = if (hasAccent) p.getInt("accent_hex", 0xFF2563EB.toInt()) else null
        val shape = runCatching { WidgetCoverShape.valueOf(p.getString("cover_shape", WidgetCoverShape.ROUNDED.name)!!) }
            .getOrDefault(WidgetCoverShape.ROUNDED)
        val txt = runCatching { WidgetTextSize.valueOf(p.getString("text_size", WidgetTextSize.MEDIUM.name)!!) }
            .getOrDefault(WidgetTextSize.MEDIUM)
        val play = runCatching { WidgetPlayStyle.valueOf(p.getString("play_style", WidgetPlayStyle.ROUND.name)!!) }
            .getOrDefault(WidgetPlayStyle.ROUND)
        return SwaraWidgetSettings(
            style = style,
            accentHex = accent,
            frostPct = p.getInt("frost_pct", 40).coerceIn(0, 100),
            cornerRoundnessPct = p.getInt("corner_pct", 66).coerceIn(0, 100),
            coverShape = shape,
            textSize = txt,
            playStyle = play,
            showCover = p.getBoolean("show_cover", true),
            showProgress = p.getBoolean("show_progress", true),
            showTime = p.getBoolean("show_time", true),
            showPrevNext = p.getBoolean("show_prev_next", true),
        )
    }

    fun save(context: Context, s: SwaraWidgetSettings) {
        val ed = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("style", s.style.name)
            .putInt("frost_pct", s.frostPct)
            .putInt("corner_pct", s.cornerRoundnessPct)
            .putString("cover_shape", s.coverShape.name)
            .putString("text_size", s.textSize.name)
            .putString("play_style", s.playStyle.name)
            .putBoolean("show_cover", s.showCover)
            .putBoolean("show_progress", s.showProgress)
            .putBoolean("show_time", s.showTime)
            .putBoolean("show_prev_next", s.showPrevNext)
        if (s.accentHex == null) ed.remove("accent_hex") else ed.putInt("accent_hex", s.accentHex)
        ed.apply()
    }

    fun extractDominantColor(bmp: Bitmap?): Int {
        if (bmp == null) return 0xFF2563EB.toInt()
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val px = small.getPixel(x, y)
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                if (r + g + b > 45) {
                    rSum += r
                    gSum += g
                    bSum += b
                    count++
                }
            }
        }
        if (count == 0) return 0xFF2563EB.toInt()
        return Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    }

    fun renderWidgetBackground(
        widthPx: Int,
        heightPx: Int,
        settings: SwaraWidgetSettings,
        coverBmp: Bitmap?,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(120)
        val h = heightPx.coerceAtLeast(80)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val radius = (minOf(w, h) * 0.28f) * (settings.cornerRoundnessPct / 100f)
        val rect = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val clipPath = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(clipPath)

        val dom = extractDominantColor(coverBmp)
        val accent = settings.accentHex ?: dom
        val frostAlpha = (110 + (settings.frostPct * 1.25f)).toInt().coerceIn(80, 240)

        when (settings.style) {
            WidgetStyle.DARK -> {
                c.drawColor(Color.argb(242, 17, 24, 39))
            }
            WidgetStyle.LIGHT -> {
                c.drawColor(Color.argb(245, 243, 244, 246))
            }
            WidgetStyle.CLEAR_GLASS -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, w.toFloat(), h.toFloat(),
                        Color.argb((frostAlpha * 0.6f).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                        Color.argb((frostAlpha * 0.45f).toInt(), 15, 23, 42),
                        Shader.TileMode.CLAMP,
                    )
                }
                c.drawRect(rect, p)
            }
            WidgetStyle.COVER_COLOUR -> {
                val darkDom = Color.rgb(
                    (Color.red(dom) * 0.45f).toInt(),
                    (Color.green(dom) * 0.45f).toInt(),
                    (Color.blue(dom) * 0.45f).toInt(),
                )
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), accent, darkDom, Shader.TileMode.CLAMP)
                }
                c.drawRect(rect, p)
            }
            WidgetStyle.GLASS -> {
                if (coverBmp != null) {
                    val blurred = Bitmap.createScaledBitmap(coverBmp, 18, 18, true)
                    val stretched = Bitmap.createScaledBitmap(blurred, w, h, true)
                    c.drawBitmap(stretched, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, 0f, w.toFloat(), h.toFloat(),
                        Color.argb(frostAlpha, 15, 23, 42),
                        Color.argb((frostAlpha * 0.85f).toInt(), Color.red(accent) / 2, Color.green(accent) / 2, Color.blue(accent) / 2),
                        Shader.TileMode.CLAMP,
                    )
                }
                c.drawRect(rect, tint)
            }
        }
        c.restore()

        val borderColor = if (settings.style == WidgetStyle.LIGHT) Color.argb(40, 0, 0, 0) else Color.argb(75, 255, 255, 255)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = borderColor
        }
        c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), radius, radius, borderPaint)
        return out
    }

    fun shapeCoverBitmap(src: Bitmap, shape: WidgetCoverShape, sizePx: Int = 180): Bitmap {
        val scaled = Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
        if (shape == WidgetCoverShape.SQUARE) return scaled
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, sizePx, sizePx)
        val rectF = RectF(rect)
        if (shape == WidgetCoverShape.CIRCLE) {
            c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, p)
        } else {
            val r = sizePx * 0.18f
            c.drawRoundRect(rectF, r, r, p)
        }
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(scaled, rect, rect, p)
        return out
    }

    fun renderTurntableBitmap(coverBmp: Bitmap?, sizePx: Int = 220): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val r = sizePx * 0.48f

        val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#11131A") }
        c.drawCircle(cx, cy, r, discPaint)

        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = Color.argb(45, 255, 255, 255)
        }
        c.drawCircle(cx, cy, r * 0.88f, groovePaint)
        c.drawCircle(cx, cy, r * 0.76f, groovePaint)
        c.drawCircle(cx, cy, r * 0.64f, groovePaint)

        if (coverBmp != null) {
            val labelSize = (sizePx * 0.46f).toInt()
            val centerArt = shapeCoverBitmap(coverBmp, WidgetCoverShape.CIRCLE, labelSize)
            c.drawBitmap(centerArt, cx - labelSize / 2f, cy - labelSize / 2f, null)
        } else {
            val lblPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") }
            c.drawCircle(cx, cy, r * 0.44f, lblPaint)
        }

        val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0B111E") }
        c.drawCircle(cx, cy, r * 0.09f, holePaint)
        return out
    }
}
