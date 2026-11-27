package com.swara.nativeapp.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val version: String,
    val htmlUrl: String,
    val apkUrl: String?,
    val releaseNotes: String,
)

internal object SwaraUpdater {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    internal fun formatReleaseNotes(rawBody: String): String {
        val cleaned = rawBody
            .lines()
            .map { it.trim() }
            .filterNot { line ->
                line.isBlank() ||
                    line.contains("Full Changelog", ignoreCase = true) && line.contains("github.com")
            }
            .map { line ->
                line.removePrefix("## ")
                    .removePrefix("### ")
                    .removePrefix("# ")
                    .replace("**", "")
                    .let { if (it.startsWith("- ") || it.startsWith("* ")) "• ${it.drop(2)}" else it }
            }
            .joinToString("\n")
            .trim()
        return cleaned.ifBlank { "• Perbaikan bug dan peningkatan performa\n• Pembaruan stabilitas pemutaran & UI" }
    }

    internal fun isVersionNewer(remote: String, current: String): Boolean {
        val rParts = remote.removePrefix("v").trim().split(".").map { it.toIntOrNull() ?: 0 }
        val cParts = current.removePrefix("v").trim().split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val cv = cParts.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun checkLatestUpdate(currentVersion: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/swaramusic/Swara/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "SwaraNative/$currentVersion")
                .build()
            httpClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val json = JSONObject(r.body?.string().orEmpty())
                val tag = json.optString("tag_name").removePrefix("v").trim()
                val html = json.optString("html_url").ifBlank { "https://github.com/swaramusic/Swara/releases" }
                if (tag.isBlank() || !isVersionNewer(tag, currentVersion) || !html.startsWith("https://")) {
                    return@use null
                }
                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val dl = a.optString("browser_download_url")
                        if (dl.startsWith("https://") && dl.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = dl
                            break
                        }
                    }
                }
                val notes = formatReleaseNotes(json.optString("body"))
                AppUpdateInfo(version = tag, htmlUrl = html, apkUrl = apkUrl, releaseNotes = notes)
            }
        }.getOrNull()
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        version: String,
        onProgress: (Int) -> Unit,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { old -> if (old.isFile) runCatching { old.delete() } }
            val safeVer = version.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val tmp = File(dir, "Swara-v$safeVer.apk.tmp")
            val apk = File(dir, "Swara-v$safeVer.apk")

            val req = Request.Builder()
                .url(apkUrl)
                .header("User-Agent", "SwaraNative/$safeVer")
                .build()
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) error("HTTP ${res.code}")
                val body = res.body ?: error("Body kosong")
                val total = body.contentLength().coerceAtLeast(1L)
                var downloaded = 0L
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buf)
                            if (read == -1) break
                            output.write(buf, 0, read)
                            downloaded += read
                            val pct = ((downloaded * 100L) / total).toInt().coerceIn(0, 99)
                            onProgress(pct)
                        }
                    }
                }
            }
            if (!tmp.exists() || tmp.length() < 100_000L) error("File APK tidak valid")
            if (apk.exists()) apk.delete()
            if (!tmp.renameTo(apk)) error("Gagal menyimpan file APK")
            onProgress(100)

            withContext(Dispatchers.Main) {
                launchApkInstaller(context, apk)
            }
        }
    }

    private fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(permIntent)
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}
