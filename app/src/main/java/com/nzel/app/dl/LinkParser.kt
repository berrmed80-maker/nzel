package com.nzel.app.dl

enum class Platform(val label: String) {
    YOUTUBE("YouTube"), INSTAGRAM("Instagram"), TIKTOK("TikTok"),
    FACEBOOK("Facebook"), X("X"), OTHER("Other")
}

object LinkParser {
    private val urlRegex = Regex("""https?://[^\s<>"'“”’\u060C]+""", RegexOption.IGNORE_CASE)
    private val trailingJunk = charArrayOf(')', ']', '}', '.', ',', ';', '!', '?', '"', '\'', '…', ':')

    /** Extract the first usable http(s) URL from arbitrary shared/pasted text. */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = urlRegex.find(text.trim())?.value ?: return null
        return raw.trimEnd(*trailingJunk).ifBlank { null }
    }

    fun isValid(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        val host = url.removePrefix("http://").removePrefix("https://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
        return host.contains('.') && host.length > 3
    }

    fun platform(url: String): Platform {
        val host = url.removePrefix("http://").removePrefix("https://")
            .substringBefore('/').lowercase()
        return when {
            host.contains("youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be") -> Platform.YOUTUBE
            host.contains("instagram.com") -> Platform.INSTAGRAM
            host.contains("tiktok.com") -> Platform.TIKTOK
            host.contains("facebook.com") || host == "fb.watch" || host.endsWith(".fb.watch") -> Platform.FACEBOOK
            host == "x.com" || host.endsWith(".x.com") || host.contains("twitter.com") -> Platform.X
            else -> Platform.OTHER
        }
    }
}
