package com.nzel.app.dl

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Format(val isAudio: Boolean) {
    BEST(false), P720(false), P480(false), MP3(true)
}

object DownloadEngine {

    var ready by mutableStateOf(false); private set
    var initError by mutableStateOf<String?>(null); private set
    var engineVersion by mutableStateOf<String?>(null); private set
    var updateState by mutableStateOf("idle"); private set

    var running by mutableStateOf(false); private set
    var progress by mutableStateOf(0f); private set
    var etaSec by mutableStateOf(-1L); private set
    var lastLine by mutableStateOf(""); private set

    private var processId: String? = null
    @Volatile private var cancelled = false

    data class Meta(
        val title: String,
        val uploader: String?,
        val durationSec: Int,
        val thumb: String?,
        val pageUrl: String?,
        val extractor: String?
    )

    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            ready = true
            engineVersion = try { YoutubeDL.getInstance().version(context.applicationContext) } catch (_: Exception) { null }
            refreshEngine(context.applicationContext)
        } catch (e: Exception) {
            initError = e.message ?: e.toString()
        }
    }

    suspend fun refreshEngine(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!ready) return@withContext false
        updateState = "updating"
        val ok = try {
            YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext) == YoutubeDL.UpdateStatus.DONE
        } catch (_: Exception) { false }
        if (ok) {
            engineVersion = try { YoutubeDL.getInstance().version(context.applicationContext) } catch (_: Exception) { engineVersion }
        }
        updateState = if (ok) "done" else "failed"
        ok
    }

    suspend fun analyze(url: String): Meta = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        val info: VideoInfo = YoutubeDL.getInstance().getInfo(request)
        Meta(
            title = info.title?.ifBlank { null } ?: "Untitled",
            uploader = info.uploader?.ifBlank { null },
            durationSec = info.duration,
            thumb = info.thumbnail?.ifBlank { null },
            pageUrl = info.webpageUrl?.ifBlank { null },
            extractor = info.extractor?.ifBlank { null }
        )
    }

    fun cancel() {
        cancelled = true
        processId?.let {
            try { YoutubeDL.getInstance().destroyProcessById(it) } catch (_: Exception) {}
        }
    }

    fun wasCancelled() = cancelled

    suspend fun download(context: Context, url: String, format: Format): File = withContext(Dispatchers.IO) {
        cancelled = false
        running = true
        progress = 0f
        etaSec = -1
        lastLine = ""
        try {
            val outDir = File(context.cacheDir, "dl").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--newline")
            request.addOption("--retries", "3")
            request.addOption("-o", outDir.absolutePath + "/%(title).80B.%(ext)s")
            when (format) {
                Format.BEST -> {
                    request.addOption("-f", "bv*+ba/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                Format.P720 -> {
                    request.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b[height<=720]/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                Format.P480 -> {
                    request.addOption("-f", "bv*[height<=480]+ba/b[height<=480]/b[height<=480]/b")
                    request.addOption("--merge-output-format", "mp4")
                }
                Format.MP3 -> {
                    request.addOption("-f", "ba/b")
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                }
            }
            val id = "nzel-" + System.currentTimeMillis()
            processId = id
            YoutubeDL.getInstance().execute(request, id) { p, eta, line ->
                if (p >= 0) progress = (p / 100f).coerceIn(0f, 1f)
                etaSec = eta
                if (!line.isNullOrBlank()) lastLine = line
            }
            if (cancelled) throw InterruptedException("cancelled")
            val file = outDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 && !it.name.endsWith(".part") }
                ?.maxByOrNull { it.lastModified() }
                ?: throw IllegalStateException("no output file")
            file
        } finally {
            running = false
            processId = null
        }
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    /** Copy the finished file into the public Downloads/Nzel collection. Returns (openable uri string, display name). */
    fun publishToDownloads(context: Context, file: File): Pair<String, String> {
        val mime = mimeFor(file.name)
        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nzel")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("mediastore insert failed")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("mediastore write failed")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            file.delete()
            uri.toString() to file.name
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nzel")
            dir.mkdirs()
            val dest = File(dir, file.name)
            file.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            file.delete()
            dest.absolutePath to dest.name
        }
    }
}
