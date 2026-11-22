package com.studiomogli.smytgemini

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
    var description: String = "",
    var tags: String = "",
    var thumbnailPath: String = "",
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
        put("description", description)
        put("tags", tags)
        put("thumbnailPath", thumbnailPath)
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
            description = obj.optString("description", ""),
            tags = obj.optString("tags", ""),
            thumbnailPath = obj.optString("thumbnailPath", ""),
            stage = obj.optString("stage", STAGE_IDEA),
            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}

data class ChannelStats(
    val title: String = "",
    val handle: String = "",
    val subscribers: Long = 0L,
    val totalViews: Long = 0L,
    val videoCount: Long = 0L,
    val fetchedAt: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("handle", handle)
        put("subscribers", subscribers)
        put("totalViews", totalViews)
        put("videoCount", videoCount)
        put("fetchedAt", fetchedAt)
    }

    companion object {
        fun fromJson(obj: JSONObject?): ChannelStats {
            if (obj == null) return ChannelStats()
            return ChannelStats(
                title = obj.optString("title", ""),
                handle = obj.optString("handle", ""),
                subscribers = obj.optLong("subscribers", 0L),
                totalViews = obj.optLong("totalViews", 0L),
                videoCount = obj.optLong("videoCount", 0L),
                fetchedAt = obj.optLong("fetchedAt", 0L)
            )
        }
    }
}

class VaultStore(context: Context) {

    companion object {
        private const val PREFS_NAME = "sm_yt_gemini_vault_v1"
        private const val KEY_IDEAS = "ideas_json"
        private const val KEY_DRIVE_LAST_SYNC = "drive_last_sync"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CREATOR_STYLE = "creator_style_notes"

        // Phase 4: Channel & Accountability
        private const val KEY_YT_HANDLE = "yt_channel_handle"
        private const val KEY_YT_DATA_API_KEY = "yt_data_api_key"
        private const val KEY_CHANNEL_STATS = "yt_channel_stats_json"
        private const val KEY_STATS_DATE = "accountability_date"
        private const val KEY_WATCH_MINS = "watch_mins_today"
        private const val KEY_CREATE_SECS = "create_secs_today"
        private const val KEY_STREAK_DAYS = "creator_streak_days"
        private const val KEY_LAST_ACTIVE_DATE = "creator_last_active_date"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun getGeminiApiKey(): String = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun getCreatorStyle(): String = prefs.getString(KEY_CREATOR_STYLE, "") ?: ""
    fun saveCreatorStyle(style: String) {
        prefs.edit().putString(KEY_CREATOR_STYLE, style.trim()).apply()
    }

    // Phase 4: YouTube Channel Settings & Cached Stats
    fun getYtHandle(): String = prefs.getString(KEY_YT_HANDLE, "") ?: ""
    fun getYtDataApiKey(): String = prefs.getString(KEY_YT_DATA_API_KEY, "") ?: ""

    fun saveYtChannelConfig(handle: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_YT_HANDLE, handle.trim())
            .putString(KEY_YT_DATA_API_KEY, apiKey.trim())
            .apply()
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

    // Phase 4: Accountability & Streak
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
        root.put("schemaVersion", 4)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("creatorStyle", getCreatorStyle())
        root.put("ytHandle", getYtHandle())
        root.put("streakDays", getStreakDays())
        val arr = JSONArray()
        getAllIdeas().forEach { arr.put(it.toJson()) }
        root.put("ideas", arr)
        return root.toString(2)
    }

    fun importVaultJson(rawJson: String): Int {
        val root = JSONObject(rawJson)
        val style = root.optString("creatorStyle", "")
        if (style.isNotBlank()) saveCreatorStyle(style)
        val handle = root.optString("ytHandle", "")
        if (handle.isNotBlank()) {
            prefs.edit().putString(KEY_YT_HANDLE, handle).apply()
        }
        val arr = root.optJSONArray("ideas") ?: return 0
        val imported = mutableListOf<VideoIdea>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            imported.add(VideoIdea.fromJson(obj))
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
