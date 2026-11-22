package com.studiomogli.smytgemini

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VideoIdea(
    val id: String,
    var angle: String,
    var script: String = "",
    var title: String = "",
    var thumbText: String = "",
    var videoLink: String = "",
    var geminiChatLink: String = "",
    var description: String = "",
    var tags: String = "",
    var thumbnailPath: String = "",
    var recordedAudioUri: String = "",
    var recordedVideoUri: String = "",
    var stage: String = STAGE_IDEA,
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("angle", angle)
        put("script", script)
        put("title", title)
        put("thumbText", thumbText)
        put("videoLink", videoLink)
        put("geminiChatLink", geminiChatLink)
        put("description", description)
        put("tags", tags)
        put("thumbnailPath", thumbnailPath)
        put("recordedAudioUri", recordedAudioUri)
        put("recordedVideoUri", recordedVideoUri)
        put("stage", stage)
        put("updatedAt", updatedAt)
    }

    companion object {
        const val STAGE_IDEA = "IDEA"
        const val STAGE_SCRIPTING = "SCRIPTING"
        const val STAGE_READY = "READY"
        const val STAGE_PUBLISHED = "PUBLISHED"

        fun fromJson(obj: JSONObject): VideoIdea = VideoIdea(
            id = obj.optString("id", "idea_${System.currentTimeMillis()}"),
            angle = obj.optString("angle", ""),
            script = obj.optString("script", ""),
            title = obj.optString("title", ""),
            thumbText = obj.optString("thumbText", ""),
            videoLink = obj.optString("videoLink", ""),
            geminiChatLink = obj.optString("geminiChatLink", ""),
            description = obj.optString("description", ""),
            tags = obj.optString("tags", ""),
            thumbnailPath = obj.optString("thumbnailPath", ""),
            recordedAudioUri = obj.optString("recordedAudioUri", ""),
            recordedVideoUri = obj.optString("recordedVideoUri", ""),
            stage = obj.optString("stage", STAGE_IDEA),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}

data class ChannelStats(
    val title: String = "",
    val handle: String = "",
    val subscribersText: String = "",
    val videosText: String = "",
    val fetchedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("handle", handle)
        put("subscribersText", subscribersText)
        put("videosText", videosText)
        put("fetchedAt", fetchedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject?): ChannelStats {
            if (obj == null) return ChannelStats()
            return ChannelStats(
                title = obj.optString("title", ""),
                handle = obj.optString("handle", ""),
                subscribersText = obj.optString("subscribersText", ""),
                videosText = obj.optString("videosText", ""),
                fetchedAt = obj.optLong("fetchedAt", 0L)
            )
        }
    }
}

class VaultStore(private val appContext: Context) {

    companion object {
        private const val PREFS_NAME = "sm_yt_gemini_vault_v1"
        private const val KEY_IDEAS = "ideas_json"
        private const val KEY_DRIVE_LAST_SYNC = "drive_last_sync"
        private const val KEY_CREATOR_STYLE = "creator_style_notes"

        private const val KEY_YT_HANDLE = "yt_channel_handle"
        private const val KEY_CHANNEL_STATS = "yt_channel_stats_json"
        private const val KEY_STATS_DATE = "accountability_date"
        private const val KEY_WATCH_MINS = "watch_mins_today"
        private const val KEY_CREATE_SECS = "create_secs_today"
        private const val KEY_STREAK_DAYS = "creator_streak_days"
        private const val KEY_LAST_ACTIVE_DATE = "creator_last_active_date"
    }

    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun ensureDateCurrent() {
        val today = todayKey()
        if (prefs.getString(KEY_STATS_DATE, "") != today) {
            prefs.edit()
                .putString(KEY_STATS_DATE, today)
                .putInt(KEY_WATCH_MINS, 0)
                .putInt(KEY_CREATE_SECS, 0)
                .apply()
        }
    }

    fun getBrandLogoFile(): File = File(appContext.filesDir, "channel_brand_logo.png")
    fun hasBrandLogo(): Boolean = getBrandLogoFile().exists()

    fun getAllIdeas(): MutableList<VideoIdea> {
        val raw = prefs.getString(KEY_IDEAS, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<VideoIdea>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(VideoIdea.fromJson(obj))
            }
            list.sortByDescending { it.updatedAt }
            list
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getById(id: String): VideoIdea? = getAllIdeas().firstOrNull { it.id == id }

    fun saveAll(ideas: List<VideoIdea>) {
        val arr = JSONArray()
        ideas.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_IDEAS, arr.toString()).apply()
    }

    fun upsert(idea: VideoIdea) {
        idea.updatedAt = System.currentTimeMillis()
        val list = getAllIdeas()
        val idx = list.indexOfFirst { it.id == idea.id }
        if (idx >= 0) list[idx] = idea else list.add(0, idea)
        saveAll(list)
        touchDailyStreak()
    }

    fun delete(id: String) {
        val list = getAllIdeas()
        list.removeAll { it.id == id }
        saveAll(list)
    }

    fun getCreatorStyle(): String = prefs.getString(KEY_CREATOR_STYLE, "") ?: ""
    fun saveCreatorStyle(style: String) {
        prefs.edit().putString(KEY_CREATOR_STYLE, style.trim()).apply()
    }

    fun getYtHandle(): String = prefs.getString(KEY_YT_HANDLE, "") ?: ""
    fun saveYtHandle(handle: String) {
        prefs.edit().putString(KEY_YT_HANDLE, handle.trim()).apply()
    }

    fun getChannelStats(): ChannelStats {
        val raw = prefs.getString(KEY_CHANNEL_STATS, null) ?: return ChannelStats()
        return try {
            ChannelStats.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            ChannelStats()
        }
    }

    fun saveChannelStats(stats: ChannelStats) {
        prefs.edit().putString(KEY_CHANNEL_STATS, stats.toJson().toString()).apply()
    }

    fun getWatchMinsToday(): Int {
        ensureDateCurrent()
        return prefs.getInt(KEY_WATCH_MINS, 0)
    }

    fun addWatchMinsToday(deltaMins: Int) {
        ensureDateCurrent()
        val updated = (prefs.getInt(KEY_WATCH_MINS, 0) + deltaMins).coerceAtLeast(0)
        prefs.edit().putInt(KEY_WATCH_MINS, updated).apply()
    }

    fun getCreateMinsToday(): Int {
        ensureDateCurrent()
        return prefs.getInt(KEY_CREATE_SECS, 0) / 60
    }

    fun addCreateSecondsToday(deltaSecs: Int) {
        ensureDateCurrent()
        val updated = prefs.getInt(KEY_CREATE_SECS, 0) + deltaSecs
        prefs.edit().putInt(KEY_CREATE_SECS, updated).apply()
        if (updated >= 60) touchDailyStreak()
    }

    fun getStreakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    private fun touchDailyStreak() {
        val today = todayKey()
        if (prefs.getString(KEY_LAST_ACTIVE_DATE, "") != today) {
            val current = prefs.getInt(KEY_STREAK_DAYS, 0)
            prefs.edit()
                .putString(KEY_LAST_ACTIVE_DATE, today)
                .putInt(KEY_STREAK_DAYS, current + 1)
                .apply()
        }
    }

    fun exportVaultJson(): String {
        val root = JSONObject()
        root.put("app", "sm-yt-gemini")
        root.put("schemaVersion", 13)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("creatorStyle", getCreatorStyle())
        root.put("ytHandle", getYtHandle())
        root.put("streakDays", getStreakDays())

        val logoFile = getBrandLogoFile()
        if (logoFile.exists()) {
            try {
                val logoBytes = logoFile.readBytes()
                root.put("brandLogoBase64", Base64.encodeToString(logoBytes, Base64.NO_WRAP))
            } catch (_: Exception) {
            }
        }

        val arr = JSONArray()
        getAllIdeas().forEach { idea ->
            val obj = idea.toJson()
            if (idea.thumbnailPath.isNotBlank()) {
                val thumbFile = File(idea.thumbnailPath)
                if (thumbFile.exists()) {
                    try {
                        val thumbBytes = thumbFile.readBytes()
                        obj.put("thumbnailBase64", Base64.encodeToString(thumbBytes, Base64.NO_WRAP))
                    } catch (_: Exception) {
                    }
                }
            }
            arr.put(obj)
        }
        root.put("ideas", arr)
        return root.toString(2)
    }

    fun importVaultJson(rawJson: String): Int {
        val root = JSONObject(rawJson)
        val style = root.optString("creatorStyle", "")
        if (style.isNotBlank()) saveCreatorStyle(style)
        val handle = root.optString("ytHandle", "")
        if (handle.isNotBlank()) saveYtHandle(handle)

        val logoB64 = root.optString("brandLogoBase64", "")
        if (logoB64.isNotBlank()) {
            try {
                val bytes = Base64.decode(logoB64, Base64.DEFAULT)
                getBrandLogoFile().writeBytes(bytes)
            } catch (_: Exception) {
            }
        }

        val arr = root.optJSONArray("ideas") ?: return 0
        val thumbDir = File(appContext.filesDir, "thumbnails")
        if (!thumbDir.exists()) thumbDir.mkdirs()

        val imported = mutableListOf<VideoIdea>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val idea = VideoIdea.fromJson(obj)
            val thumbB64 = obj.optString("thumbnailBase64", "")
            if (thumbB64.isNotBlank()) {
                try {
                    val bytes = Base64.decode(thumbB64, Base64.DEFAULT)
                    val restoredThumb = File(thumbDir, "thumb_${idea.id}.jpg")
                    restoredThumb.writeBytes(bytes)
                    idea.thumbnailPath = restoredThumb.absolutePath
                } catch (_: Exception) {
                }
            }
            imported.add(idea)
        }
        if (imported.isNotEmpty()) {
            saveAll(imported)
        }
        return imported.size
    }

    fun markDriveSynced() {
        prefs.edit().putLong(KEY_DRIVE_LAST_SYNC, System.currentTimeMillis()).apply()
    }

    fun getLastDriveSync(): Long = prefs.getLong(KEY_DRIVE_LAST_SYNC, 0L)
}
