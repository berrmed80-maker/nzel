package com.nzel.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nzel.app.dl.DownloadEngine
import com.nzel.app.dl.DownloadService
import com.nzel.app.dl.Format
import com.nzel.app.dl.HistoryItem
import com.nzel.app.dl.HistoryStore
import com.nzel.app.dl.LinkParser
import com.nzel.app.dl.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class Phase { IDLE, ANALYZING, READY, DOWNLOADING, DONE, ERROR }

class MainViewModel : ViewModel() {

    var urlText by mutableStateOf("")
    var phase by mutableStateOf(Phase.IDLE); private set
    var meta by mutableStateOf<DownloadEngine.Meta?>(null); private set
    var errorTextRes by mutableStateOf<Int?>(null); private set
    var errorDetail by mutableStateOf<String?>(null); private set
    var selectedFormat by mutableStateOf(Format.BEST)
    var savedItem by mutableStateOf<HistoryItem?>(null); private set

    val platform: Platform?
        get() = LinkParser.extract(urlText)?.let { if (LinkParser.isValid(it)) LinkParser.platform(it) else null }

    fun softReset() {
        if (phase != Phase.DOWNLOADING) {
            phase = Phase.IDLE
            meta = null
            errorTextRes = null
            errorDetail = null
            savedItem = null
        }
    }

    fun reset() {
        urlText = ""
        softReset()
    }

    fun analyze() {
        val url = LinkParser.extract(urlText)
        if (url == null || !LinkParser.isValid(url)) {
            errorTextRes = R.string.error_invalid_url
            errorDetail = null
            phase = Phase.ERROR
            return
        }
        urlText = url
        phase = Phase.ANALYZING
        errorTextRes = null
        errorDetail = null
        meta = null
        savedItem = null
        viewModelScope.launch {
            try {
                meta = DownloadEngine.analyze(url)
                phase = Phase.READY
            } catch (e: Exception) {
                mapError(e)
                phase = Phase.ERROR
            }
        }
    }

    private fun mapError(e: Exception) {
        val m = (e.message ?: e.toString()).lowercase()
        errorDetail = e.message?.lineSequence()?.firstOrNull()?.take(200)
        errorTextRes = when {
            "sign in to confirm" in m || "not a bot" in m -> R.string.error_blocked
            "unsupported url" in m -> R.string.error_unsupported
            "login" in m || "private" in m || "empty media response" in m -> R.string.error_login_required
            "unsupported" in m -> R.string.error_unsupported
            else -> R.string.error_generic
        }
    }

    fun download(context: Context) {
        val m = meta ?: return
        val url = LinkParser.extract(urlText) ?: return
        phase = Phase.DOWNLOADING
        viewModelScope.launch {
            try { DownloadService.start(context) } catch (_: Exception) {}
            val notifier = launch {
                var last = -1
                while (isActive && DownloadEngine.running) {
                    val pct = (DownloadEngine.progress * 100).toInt()
                    if (pct != last && pct >= 0) {
                        last = pct
                        try { DownloadService.update(context, pct) } catch (_: Exception) {}
                    }
                    delay(1000)
                }
            }
            try {
                val file = DownloadEngine.download(context, url, selectedFormat)
                val size = file.length()
                val (uriStr, name) = DownloadEngine.publishToDownloads(context, file)
                val item = HistoryItem(
                    id = System.currentTimeMillis(),
                    title = m.title,
                    pageUrl = m.pageUrl ?: url,
                    platform = LinkParser.platform(url).label,
                    uriString = uriStr,
                    fileName = name,
                    mime = DownloadEngine.mimeFor(name),
                    sizeBytes = size,
                    timestamp = System.currentTimeMillis(),
                    thumb = m.thumb
                )
                HistoryStore.add(item)
                savedItem = item
                phase = Phase.DONE
            } catch (e: Exception) {
                if (DownloadEngine.wasCancelled()) {
                    phase = Phase.READY
                } else {
                    mapError(e)
                    phase = Phase.ERROR
                }
            } finally {
                notifier.cancel()
                try { DownloadService.stop(context) } catch (_: Exception) {}
            }
        }
    }

    fun cancelDownload() = DownloadEngine.cancel()

    fun openItem(context: Context, item: HistoryItem) {
        try {
            val uri = uriFor(context, item)
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareItem(context: Context, item: HistoryItem) {
        try {
            val uri = uriFor(context, item)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = item.mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_via)))
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriFor(context: Context, item: HistoryItem): Uri =
        if (item.uriString.startsWith("content://")) Uri.parse(item.uriString)
        else FileProvider.getUriForFile(context, "com.nzel.app.fileprovider", File(item.uriString))
}
