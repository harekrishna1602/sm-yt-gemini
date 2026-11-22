package com.studiomogli.smytgemini

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object YouTubeDataClient {

    fun fetchChannelStats(
        handleOrId: String,
        apiKey: String,
        onResult: (Result<ChannelStats>) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val clean = handleOrId.trim()
                val encoded = URLEncoder.encode(clean.removePrefix("@"), "UTF-8")
                val queryParam = if (clean.startsWith("UC") && clean.length >= 20) {
                    "id=$encoded"
                } else {
                    "forHandle=$encoded"
                }

                val endpoint =
                    "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&$queryParam&key=$apiKey"
                conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream.bufferedReader().use { it.readText() }

                if (code !in 200..299) {
                    val msg = try {
                        JSONObject(respText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                    } catch (_: Exception) {
                        "HTTP $code"
                    }
                    onResult(Result.failure(Exception(msg)))
                    return@Thread
                }

                val items = JSONObject(respText).optJSONArray("items")
                if (items == null || items.length() == 0) {
                    onResult(Result.failure(Exception("Channel not found. Check your @handle or Channel ID.")))
                    return@Thread
                }

                val item = items.getJSONObject(0)
                val snippet = item.optJSONObject("snippet")
                val stats = item.optJSONObject("statistics")

                val result = ChannelStats(
                    title = snippet?.optString("title", clean) ?: clean,
                    handle = clean,
                    subscribers = stats?.optString("subscriberCount", "0")?.toLongOrNull() ?: 0L,
                    totalViews = stats?.optString("viewCount", "0")?.toLongOrNull() ?: 0L,
                    videoCount = stats?.optString("videoCount", "0")?.toLongOrNull() ?: 0L,
                    fetchedAt = System.currentTimeMillis()
                )
                onResult(Result.success(result))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
