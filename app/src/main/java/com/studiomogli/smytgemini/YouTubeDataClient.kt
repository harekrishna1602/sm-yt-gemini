package com.studiomogli.smytgemini

import java.net.HttpURLConnection
import java.net.URL

object YouTubeDataClient {

    fun fetchChannelStatsNoApiKey(
        handleOrUrl: String,
        onResult: (Result<ChannelStats>) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val raw = handleOrUrl.trim()
                val normalizedHandle = when {
                    raw.startsWith("http", ignoreCase = true) -> raw
                    raw.startsWith("@") -> "https://www.youtube.com/$raw"
                    raw.startsWith("UC") && raw.length >= 20 -> "https://www.youtube.com/channel/$raw"
                    else -> "https://www.youtube.com/@$raw"
                }
                val displayHandle = if (raw.startsWith("http", ignoreCase = true)) {
                    raw.substringAfter("youtube.com/").substringBefore("?").ifBlank { raw }
                } else if (raw.startsWith("@") || raw.startsWith("UC")) {
                    raw
                } else {
                    "@$raw"
                }

                conn = (URL(normalizedHandle).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 12000
                    readTimeout = 12000
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                    )
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                }

                val code = conn.responseCode
                if (code !in 200..299) {
                    onResult(Result.failure(Exception("Channel not found (HTTP $code). Check your @handle.")))
                    return@Thread
                }

                val html = conn.inputStream.bufferedReader().use { it.readText() }

                val titleRegex = Regex("<meta\\s+property=\"og:title\"\\s+content=\"([^\"]+)\"")
                val rawTitle = titleRegex.find(html)?.groupValues?.getOrNull(1)?.trim() ?: ""
                val title = rawTitle
                    .replace("&amp;", "&")
                    .replace("&#39;", "'")
                    .replace("&quot;", "\"")
                    .ifBlank { displayHandle }

                val subRegexes = listOf(
                    Regex("\"subscriberCountText\"\\s*:\\s*\\{\\s*\"simpleText\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"content\"\\s*:\\s*\"([0-9.,]+[KMBkmb]?\\s+subscribers?)\"")
                )
                var subsText = ""
                for (rx in subRegexes) {
                    val m = rx.find(html)?.groupValues?.getOrNull(1)?.trim()
                    if (!m.isNullOrBlank()) {
                        subsText = m
                        break
                    }
                }

                val vidRegexes = listOf(
                    Regex("\"videosCountText\"\\s*:\\s*\\{\\s*\"runs\"\\s*:\\s*\\[\\s*\\{\\s*\"text\"\\s*:\\s*\"([^\"]+)\""),
                    Regex("\"content\"\\s*:\\s*\"([0-9.,]+[KMBkmb]?\\s+videos?)\"")
                )
                var videosText = ""
                for (rx in vidRegexes) {
                    val m = rx.find(html)?.groupValues?.getOrNull(1)?.trim()
                    if (!m.isNullOrBlank()) {
                        videosText = if (m.lowercase().contains("video")) m else "$m videos"
                        break
                    }
                }

                val stats = ChannelStats(
                    title = title,
                    handle = displayHandle,
                    subscribersText = subsText.ifBlank { "Channel linked" },
                    videosText = videosText,
                    fetchedAt = System.currentTimeMillis()
                )
                onResult(Result.success(stats))
            } catch (e: Exception) {
                onResult(Result.failure(Exception("Could not reach YouTube: ${e.message}")))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
