package com.nzel.app.dl

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class HistoryItem(
    val id: Long,
    val title: String,
    val pageUrl: String?,
    val platform: String,
    val uriString: String,
    val fileName: String,
    val mime: String,
    val sizeBytes: Long,
    val timestamp: Long,
    val thumb: String?
)

object HistoryStore {
    var items by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "history.json")
        load()
    }

    @Synchronized
    fun load() {
        val f = file ?: return
        if (!f.exists()) { items = emptyList(); return }
        try {
            val arr = JSONArray(f.readText())
            val out = ArrayList<HistoryItem>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    HistoryItem(
                        id = o.optLong("id"),
                        title = o.optString("title"),
                        pageUrl = o.optString("pageUrl").ifBlank { null },
                        platform = o.optString("platform"),
                        uriString = o.optString("uri"),
                        fileName = o.optString("fileName"),
                        mime = o.optString("mime"),
                        sizeBytes = o.optLong("sizeBytes"),
                        timestamp = o.optLong("timestamp"),
                        thumb = o.optString("thumb").ifBlank { null }
                    )
                )
            }
            items = out.sortedByDescending { it.timestamp }
        } catch (e: Exception) {
            items = emptyList()
        }
    }

    @Synchronized
    fun add(item: HistoryItem) {
        items = listOf(item) + items
        save()
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        val item = items.firstOrNull { it.id == id } ?: return
        try {
            if (item.uriString.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(item.uriString), null, null)
            } else {
                File(item.uriString).delete()
            }
        } catch (_: Exception) {}
        items = items.filterNot { it.id == id }
        save()
    }

    private fun save() {
        val f = file ?: return
        try {
            val arr = JSONArray()
            items.take(200).forEach { it ->
                arr.put(JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("pageUrl", it.pageUrl ?: "")
                    put("platform", it.platform)
                    put("uri", it.uriString)
                    put("fileName", it.fileName)
                    put("mime", it.mime)
                    put("sizeBytes", it.sizeBytes)
                    put("timestamp", it.timestamp)
                    put("thumb", it.thumb ?: "")
                })
            }
            f.writeText(arr.toString())
        } catch (_: Exception) {}
    }
}
