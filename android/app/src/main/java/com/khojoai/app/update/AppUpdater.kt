package com.khojoai.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.khojoai.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Self-update from our private AWS bucket: ask which build is newest, download it, check its SHA-256 and hand it to
 * Android's installer. Android itself refuses the file unless it is signed with the same key as the installed app,
 * so a tampered download cannot replace this app.
 */
class AppUpdater(private val context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Success(null) means this build is already the newest one, nothing is published, or this install is not in the rollout yet. */
    suspend fun check(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("${BuildConfig.API_BASE_URL.trimEnd('/')}/v1/releases/latest")
                .header("x-beta-key", BuildConfig.BETA_KEY)
                .header("x-install-id", installId())
                .build()
            http.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> null
                    !response.isSuccessful -> error("The update server answered HTTP ${response.code}.")
                    else -> UpdateParser.parse(response.body?.string().orEmpty()).takeIf { it.isNewerThan(BuildConfig.VERSION_CODE) }
                }
            }
        }
    }

    /** Downloads the build to the app cache and verifies it; [onProgress] gets 0..1. */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "khojo-${info.versionCode}.apk")
            val digest = MessageDigest.getInstance("SHA-256")
            http.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { response ->
                if (!response.isSuccessful) error("The download failed (HTTP ${response.code}). Try again.")
                val body = response.body ?: error("The download was empty.")
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                var done = 0L
                var shown = -1
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            done += read
                            // Whole-percent steps only, so the UI is not redrawn for every 64 KB chunk.
                            val percent = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
                            if (percent > shown) {
                                shown = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (toHex(digest.digest()) != info.sha256) {
                file.delete()
                error("The downloaded file is damaged. Try again.")
            }
            file
        }
    }

    /** Opens Android's installer for [file]; if installs from this app are not allowed yet, opens that setting instead. */
    fun install(file: File): InstallStart {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallStart.NEEDS_PERMISSION
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallStart.OPENED
    }

    /** Random id made once per install; the server uses it only to decide who is in a staged rollout. */
    private fun installId(): String {
        val prefs = context.getSharedPreferences("update", Context.MODE_PRIVATE)
        return prefs.getString("install_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("install_id", it).apply()
        }
    }

    enum class InstallStart { OPENED, NEEDS_PERMISSION }

    companion object {
        fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
