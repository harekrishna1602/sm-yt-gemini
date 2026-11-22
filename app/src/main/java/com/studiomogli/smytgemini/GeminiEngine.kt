package com.studiomogli.smytgemini

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class PackagingOption(
    val title: String,
    val thumbText: String,
    val visualCue: String
)

data class FullVideoMetadata(
    val title: String,
    val thumbText: String,
    val description: String,
    val tags: String,
    val thumbnailPrompt: String
)

data class BingeFlipResult(
    val realityCheck: String,
    val angle: String,
    val title: String,
    val thumbText: String
)

object GeminiEngine {

    private const val MODEL_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private const val IMAGEN_ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/imagen-3.0-generate-002:predict"

    /**
     * Phase 4: Binge-to-Build Flip — turns what the creator was watching into a concrete Workbench Idea.
     */
    fun generateBingeToBuildFlip(
        apiKey: String,
        creatorStyle: String,
        watchMins: Int,
        createMins: Int,
        bingeTopic: String,
        onResult: (Result<BingeFlipResult>) -> Unit
    ) {
        val prompt = buildString {
            append("You are a sharp, no-excuses YouTube accountability coach.\n")
            if (creatorStyle.isNotBlank()) {
                append("CREATOR SETUP & STYLE: ").append(creatorStyle).append("\n")
            }
            append("TODAY'S STATS: Watched $watchMins mins of others vs Created $createMins mins.\n")
            append("WHAT THEY KEPT WATCHING: \"$bingeTopic\"\n\n")
            append("TASK: Flip their binge topic into 1 contrarian or remix video they can make TODAY.\n")
            append("Return ONLY valid JSON:\n")
            append("{\"realityCheck\":\"2-sentence blunt nudge\",\"angle\":\"1-sentence core video angle\",\"title\":\"High-CTR title <55 chars\",\"thumbText\":\"2-4 words, zero words repeated from title\"}")
        }

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        callGemini(apiKey, parts, expectJson = true) { res ->
            res.fold(
                onSuccess = { raw ->
                    try {
                        val j = JSONObject(stripCodeFences(raw))
                        onResult(
                            Result.success(
                                BingeFlipResult(
                                    realityCheck = j.optString("realityCheck", "Time to switch from viewer to creator."),
                                    angle = j.optString("angle", bingeTopic),
                                    title = j.optString("title", ""),
                                    thumbText = j.optString("thumbText", "")
                                )
                            )
                        )
                    } catch (e: Exception) {
                        onResult(Result.failure(Exception("Parse error: ${e.message}")))
                    }
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    fun generateTightHook(
        apiKey: String,
        creatorStyle: String,
        angle: String,
        rawScript: String,
        onResult: (Result<String>) -> Unit
    ) {
        val prompt = buildString {
            append("You are an elite YouTube retention script editor. Keep the creator's authentic voice (English or Hinglish if used) and NEVER sound robotic or generic.\n")
            if (creatorStyle.isNotBlank()) {
                append("CREATOR SETUP & STYLE: ").append(creatorStyle).append("\n")
            }
            append("\nVIDEO ANGLE: ").append(angle).append("\n")
            append("RAW SCRIPT / VOICE DUMP:\n").append(rawScript.ifBlank { angle }).append("\n\n")
            append("TASK:\n")
            append("Rewrite ONLY into a punchy, ready-to-record script with:\n")
            append("[0-15s CLICK-TO-CONFIRM HOOK]\n")
            append("- Immediate confirmation of the click (no 'Hey guys welcome back').\n")
            append("- Clear stakes + open curiosity loop.\n\n")
            append("[CORE BEATS]\n")
            append("- 3 to 4 tight bullet beats that cut all filler from the raw dump and end with a natural subscribe payoff.\n")
            append("Output plain text only, no markdown asterisks.")
        }

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        callGemini(apiKey, parts, expectJson = false, callback = onResult)
    }

    fun generatePackagingOptions(
        apiKey: String,
        creatorStyle: String,
        angle: String,
        script: String,
        onResult: (Result<List<PackagingOption>>) -> Unit
    ) {
        val prompt = buildString {
            append("You are a YouTube CTR & Thumbnail packaging strategist.\n")
            if (creatorStyle.isNotBlank()) {
                append("CREATOR SETUP & STYLE: ").append(creatorStyle).append("\n")
            }
            append("\nVIDEO ANGLE: ").append(angle).append("\n")
            if (script.isNotBlank()) {
                append("SCRIPT EXCERPT: ").append(script.take(1200)).append("\n")
            }
            append("\nGenerate EXACTLY 3 high-CTR Title + Thumbnail packaging options.\n")
            append("STRICT RULES:\n")
            append("1. title MUST be under 55 characters.\n")
            append("2. thumbText MUST be 2 to 4 words MAX.\n")
            append("3. NO-REPEAT RULE: thumbText must NEVER repeat words already used in title.\n")
            append("4. visualCue: 1 short sentence describing the thumbnail visual.\n\n")
            append("Return ONLY valid JSON matching this schema:\n")
            append("{\"options\":[{\"title\":\"...\",\"thumbText\":\"...\",\"visualCue\":\"...\"}]}")
        }

        val parts = JSONArray().put(JSONObject().put("text", prompt))
        callGemini(apiKey, parts, expectJson = true) { res ->
            res.fold(
                onSuccess = { raw ->
                    try {
                        val cleaned = stripCodeFences(raw)
                        val root = JSONObject(cleaned)
                        val arr = root.optJSONArray("options") ?: JSONArray()
                        val list = mutableListOf<PackagingOption>()
                        for (i in 0 until arr.length()) {
                            val o = arr.optJSONObject(i) ?: continue
                            val t = o.optString("title", "").trim()
                            val th = o.optString("thumbText", "").trim()
                            val vc = o.optString("visualCue", "").trim()
                            if (t.isNotEmpty() && th.isNotEmpty()) {
                                list.add(PackagingOption(t, th, vc))
                            }
                        }
                        if (list.isEmpty()) onResult(Result.failure(Exception("No options returned.")))
                        else onResult(Result.success(list))
                    } catch (e: Exception) {
                        onResult(Result.failure(Exception("Parse error: ${e.message}")))
                    }
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    fun analyzeLocalVideo(
        context: Context,
        apiKey: String,
        creatorStyle: String,
        videoUri: Uri,
        angleHint: String,
        onResult: (Result<FullVideoMetadata>) -> Unit
    ) {
        Thread {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, videoUri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 10000L

                val samplePoints = listOf(0.15, 0.45, 0.75)
                val parts = JSONArray()

                for (pct in samplePoints) {
                    val timeUs = (durationMs * pct * 1000.0).toLong().coerceAtLeast(0L)
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) {
                        val scaled = scaleBitmap(frame, 640)
                        val baos = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 72, baos)
                        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                        parts.put(
                            JSONObject().put(
                                "inline_data",
                                JSONObject()
                                    .put("mime_type", "image/jpeg")
                                    .put("data", b64)
                            )
                        )
                        if (scaled != frame) scaled.recycle()
                        frame.recycle()
                    }
                }

                val prompt = buildMetadataPrompt(
                    creatorStyle = creatorStyle,
                    sourceContext = "Local video (${durationMs / 1000}s) with extracted visual frames attached.",
                    angleHint = angleHint
                )
                parts.put(JSONObject().put("text", prompt))

                callGeminiSyncAndParseMetadata(apiKey, parts, onResult)
            } catch (e: Exception) {
                onResult(Result.failure(Exception("Video read failed: ${e.message}")))
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    fun analyzeYouTubeLink(
        apiKey: String,
        creatorStyle: String,
        youtubeUrl: String,
        angleHint: String,
        onResult: (Result<FullVideoMetadata>) -> Unit
    ) {
        Thread {
            var oembedInfo = ""
            try {
                val oembedUrl = "https://www.youtube.com/oembed?url=${java.net.URLEncoder.encode(youtubeUrl, "UTF-8")}&format=json"
                val conn = (URL(oembedUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val t = json.optString("title", "")
                    val author = json.optString("author_name", "")
                    if (t.isNotBlank()) oembedInfo = "Current YouTube Title: $t | Channel: $author"
                }
                conn.disconnect()
            } catch (_: Exception) {
            }

            val prompt = buildMetadataPrompt(
                creatorStyle = creatorStyle,
                sourceContext = "YouTube Video Link: $youtubeUrl\n$oembedInfo",
                angleHint = angleHint
            )
            val parts = JSONArray().put(JSONObject().put("text", prompt))
            callGeminiSyncAndParseMetadata(apiKey, parts, onResult)
        }.start()
    }

    fun generateThumbnailImage(
        apiKey: String,
        creatorStyle: String,
        title: String,
        thumbText: String,
        customVisualPrompt: String,
        onResult: (Result<ByteArray>) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val fullPrompt = buildString {
                    append("High-CTR YouTube thumbnail, 16:9 widescreen, dramatic studio lighting, ultra-crisp focal subject, vibrant high-contrast colors, clean composition. ")
                    if (creatorStyle.isNotBlank()) append("Creator style: ").append(creatorStyle).append(". ")
                    if (customVisualPrompt.isNotBlank()) {
                        append(customVisualPrompt)
                    } else {
                        append("Visual concept for video titled '").append(title).append("'")
                    }
                    if (thumbText.isNotBlank()) {
                        append(". Leave clean negative space for bold text: ").append(thumbText)
                    }
                }

                val url = URL("$IMAGEN_ENDPOINT?key=$apiKey")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 45000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val body = JSONObject().apply {
                    put("instances", JSONArray().put(JSONObject().put("prompt", fullPrompt)))
                    put("parameters", JSONObject().apply {
                        put("sampleCount", 1)
                        put("aspectRatio", "16:9")
                    })
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream.bufferedReader().use { it.readText() }

                if (code in 200..299) {
                    val json = JSONObject(respText)
                    val b64 = json.optJSONArray("predictions")
                        ?.optJSONObject(0)
                        ?.optString("bytesBase64Encoded", "") ?: ""
                    if (b64.isBlank()) onResult(Result.failure(Exception("No image returned by Imagen.")))
                    else onResult(Result.success(Base64.decode(b64, Base64.DEFAULT)))
                } else {
                    val errMsg = try {
                        JSONObject(respText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                    } catch (_: Exception) {
                        "HTTP $code"
                    }
                    onResult(Result.failure(Exception(errMsg)))
                }
            } catch (e: Exception) {
                onResult(Result.failure(e))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }

    private fun buildMetadataPrompt(
        creatorStyle: String,
        sourceContext: String,
        angleHint: String
    ): String = buildString {
        append("You are an elite YouTube packaging & SEO metadata strategist.\n")
        if (creatorStyle.isNotBlank()) {
            append("CREATOR SETUP & STYLE: ").append(creatorStyle).append("\n")
        }
        append("SOURCE: ").append(sourceContext).append("\n")
        if (angleHint.isNotBlank()) {
            append("CREATOR NOTES / ANGLE: ").append(angleHint).append("\n")
        }
        append("\nGenerate a complete, ready-to-publish YouTube metadata pack.\n")
        append("RULES:\n")
        append("1. title: High-CTR title under 55 characters.\n")
        append("2. thumbText: 2 to 4 punchy words that NEVER repeat words from title.\n")
        append("3. description: Clean 2-paragraph YouTube description with a strong first line and subscribe CTA.\n")
        append("4. tags: 10 to 15 high-relevance comma-separated YouTube tags.\n")
        append("5. thumbnailPrompt: A vivid 2-sentence visual description for generating a 16:9 thumbnail image.\n\n")
        append("Return ONLY valid JSON:\n")
        append("{\"title\":\"...\",\"thumbText\":\"...\",\"description\":\"...\",\"tags\":\"...\",\"thumbnailPrompt\":\"...\"}")
    }

    private fun callGeminiSyncAndParseMetadata(
        apiKey: String,
        parts: JSONArray,
        onResult: (Result<FullVideoMetadata>) -> Unit
    ) {
        callGemini(apiKey, parts, expectJson = true) { res ->
            res.fold(
                onSuccess = { raw ->
                    try {
                        val json = JSONObject(stripCodeFences(raw))
                        onResult(
                            Result.success(
                                FullVideoMetadata(
                                    title = json.optString("title", "").trim(),
                                    thumbText = json.optString("thumbText", "").trim(),
                                    description = json.optString("description", "").trim(),
                                    tags = json.optString("tags", "").trim(),
                                    thumbnailPrompt = json.optString("thumbnailPrompt", "").trim()
                                )
                            )
                        )
                    } catch (e: Exception) {
                        onResult(Result.failure(Exception("Metadata parse failed: ${e.message}")))
                    }
                },
                onFailure = { onResult(Result.failure(it)) }
            )
        }
    }

    private fun scaleBitmap(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toFloat() / src.width.toFloat()
        val newH = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, maxWidth, newH, true)
    }

    private fun stripCodeFences(raw: String): String = raw.trim()
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    private fun callGemini(
        apiKey: String,
        parts: JSONArray,
        expectJson: Boolean,
        callback: (Result<String>) -> Unit
    ) {
        Thread {
            var conn: HttpURLConnection? = null
            try {
                val url = URL("$MODEL_ENDPOINT?key=$apiKey")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 40000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val body = JSONObject().apply {
                    put("contents", JSONArray().put(JSONObject().put("parts", parts)))
                    if (expectJson) {
                        put("generationConfig", JSONObject().apply {
                            put("responseMimeType", "application/json")
                        })
                    }
                }

                OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val respText = stream.bufferedReader().use { it.readText() }

                if (code in 200..299) {
                    val json = JSONObject(respText)
                    val text = json.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text", "")
                        ?.trim() ?: ""
                    if (text.isBlank()) callback(Result.failure(Exception("Empty response from Gemini.")))
                    else callback(Result.success(text))
                } else {
                    val errMsg = try {
                        JSONObject(respText).optJSONObject("error")?.optString("message") ?: "HTTP $code"
                    } catch (_: Exception) {
                        "HTTP $code"
                    }
                    callback(Result.failure(Exception(errMsg)))
                }
            } catch (e: Exception) {
                callback(Result.failure(e))
            } finally {
                conn?.disconnect()
            }
        }.start()
    }
}
