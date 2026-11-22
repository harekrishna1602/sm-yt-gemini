package com.studiomogli.smytgemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

data class ParsedGeminiPack(
    val title: String = "",
    val thumbText: String = "",
    val description: String = "",
    val tags: String = "",
    val script: String = "",
    val angle: String = ""
)

object MobileGeminiBridge {

    private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
    private const val YT_STUDIO_PACKAGE = "com.google.android.apps.youtube.creator"
    private const val GEMINI_WEB_FALLBACK = "https://gemini.google.com/app"
    private const val YT_STUDIO_WEB_FALLBACK = "https://studio.youtube.com"

    fun isGeminiAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(GEMINI_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openSavedGeminiChat(context: Context, chatUrl: String, copiedPrompt: Boolean = false) {
        val cleanUrl = chatUrl.trim()
        if (cleanUrl.isBlank()) {
            Toast.makeText(context, "Paste your Gemini chat link first.", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = Uri.parse(if (cleanUrl.startsWith("http")) cleanUrl else "https://$cleanUrl")
        val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewIntent)
            val msg = if (copiedPrompt) {
                "Prompt copied! Opening this video's Gemini chat..."
            } else {
                "Opening this video's Gemini chat..."
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open chat link. Check the URL.", Toast.LENGTH_LONG).show()
        }
    }

    fun openInMobileGemini(
        context: Context,
        prompt: String,
        mediaUri: Uri? = null,
        mimeType: String = "video/*",
        chatUrl: String = ""
    ) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("YT Gemini Prompt", prompt))

        if (chatUrl.isNotBlank() && mediaUri == null) {
            openSavedGeminiChat(context, chatUrl, copiedPrompt = true)
            return
        }

        val installed = isGeminiAppInstalled(context)

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, prompt)
            if (mediaUri != null) {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, mediaUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
            if (installed) {
                setPackage(GEMINI_PACKAGE)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            if (installed) {
                context.startActivity(sendIntent)
                Toast.makeText(context, "Opened in Mobile Gemini (prompt also copied)", Toast.LENGTH_SHORT).show()
            } else {
                val chooser = Intent.createChooser(sendIntent, "Send prompt to Gemini")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(GEMINI_PACKAGE)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    Toast.makeText(context, "Prompt copied! Paste it into Gemini.", Toast.LENGTH_LONG).show()
                } else {
                    val web = Intent(Intent.ACTION_VIEW, Uri.parse(GEMINI_WEB_FALLBACK))
                    web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(web)
                    Toast.makeText(context, "Prompt copied! Paste it into Gemini.", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Prompt copied to clipboard. Open Gemini and paste.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun openYouTubeStudioApp(context: Context) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(YT_STUDIO_PACKAGE)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
            } else {
                val web = Intent(Intent.ACTION_VIEW, Uri.parse(YT_STUDIO_WEB_FALLBACK))
                web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(web)
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Open YouTube Studio to paste your Publish Pack.", Toast.LENGTH_LONG).show()
        }
    }

    fun openYouTubeStudioForThumbnail(context: Context, prompt: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("YT Studio Thumbnail Prompt", prompt))
        openYouTubeStudioApp(context)
        Toast.makeText(context, "Thumbnail prompt copied! Opening YouTube Studio...", Toast.LENGTH_LONG).show()
    }

    fun buildGeminiVoiceScriptPrompt(creatorStyle: String, angle: String): String = buildString {
        append("I am going to dictate my raw YouTube script to you using your mic button. ")
        append("Clean up filler words while keeping my exact natural tone (Hinglish/English) and format it into a teleprompter-ready script (0-15s Hook + Short Punchy Lines).\n")
        if (creatorStyle.isNotBlank()) append("MY STYLE: ").append(creatorStyle).append("\n")
        if (angle.isNotBlank()) append("VIDEO ANGLE: ").append(angle).append("\n")
        append("\nMY VOICE DICTATION:\n")
    }

    fun buildThumbnailPrompt(
        creatorStyle: String,
        title: String,
        thumbText: String,
        angle: String,
        hasRefImage: Boolean
    ): String = buildString {
        append("Generate a high-CTR 16:9 widescreen YouTube thumbnail image.\n")
        if (hasRefImage) {
            append("REFERENCE IMAGE ATTACHED: Keep the person/mask/subject style from the attached reference photo consistent.\n")
        }
        if (creatorStyle.isNotBlank()) {
            append("CREATOR VISUAL STYLE: ").append(creatorStyle).append("\n")
        }
        append("VIDEO TOPIC / TITLE: ").append(title.ifBlank { angle.ifBlank { "YouTube Video" } }).append("\n")
        if (thumbText.isNotBlank()) {
            append("BOLD TEXT ON THUMBNAIL (large, ultra-readable, high contrast): \"").append(thumbText).append("\"\n")
        }
        append("VISUAL RULES: 16:9 aspect ratio, dramatic studio lighting, sharp foreground subject, clean uncluttered background, strong color contrast so it pops on a small mobile YouTube feed.")
    }

    fun buildHookPrompt(creatorStyle: String, angle: String, rawScript: String): String = buildString {
        append("You are my YouTube retention script editor. Keep my natural voice (English/Hinglish) and NEVER sound robotic.\n")
        if (creatorStyle.isNotBlank()) append("MY SETUP & STYLE: ").append(creatorStyle).append("\n")
        append("\nVIDEO ANGLE: ").append(angle.ifBlank { "New Video" }).append("\n")
        append("MY RAW VOICE DUMP:\n").append(rawScript.ifBlank { angle }).append("\n\n")
        append("TASK: Rewrite into a tight, ready-to-record script with:\n")
        append("1. [0-15s CLICK-TO-CONFIRM HOOK] — confirm the click immediately, no 'Hey guys welcome back', add stakes + open loop.\n")
        append("2. [CORE BEATS] — 3 to 4 crisp talking points cutting all filler.\n")
        append("Output clean plain text so I can copy it straight back into my script box.")
    }

    fun buildPackagingPrompt(creatorStyle: String, angle: String, script: String, videoLink: String): String = buildString {
        append("You are my YouTube CTR & SEO packaging strategist.\n")
        if (creatorStyle.isNotBlank()) append("MY SETUP & STYLE: ").append(creatorStyle).append("\n")
        if (videoLink.isNotBlank()) append("VIDEO LINK: ").append(videoLink).append("\n")
        append("VIDEO ANGLE: ").append(angle.ifBlank { "Attached video" }).append("\n")
        if (script.isNotBlank()) append("SCRIPT NOTES: ").append(script.take(1200)).append("\n")
        append("\nGenerate my best high-CTR YouTube packaging.\n")
        append("RULES:\n")
        append("- TITLE must be under 55 characters.\n")
        append("- THUMB must be 2 to 4 words MAX and NEVER repeat words from TITLE.\n")
        append("- Reply using these EXACT labels so my app can auto-fill them when I copy your reply:\n\n")
        append("TITLE: <your best title>\n")
        append("THUMB: <2-4 word thumbnail text>\n")
        append("DESC: <2-paragraph YouTube description with subscribe CTA>\n")
        append("TAGS: <10-15 comma-separated tags>")
    }

    fun buildBingeToBuildPrompt(creatorStyle: String, watchMins: Int, createMins: Int, topic: String): String = buildString {
        append("You are my strict, no-excuses YouTube accountability coach.\n")
        if (creatorStyle.isNotBlank()) append("MY SETUP & STYLE: ").append(creatorStyle).append("\n")
        append("TODAY'S STATS: I watched $watchMins mins of other creators vs created for $createMins mins.\n")
        append("WHAT I KEPT WATCHING: \"$topic\"\n\n")
        append("TASK: Give me a 2-sentence blunt reality check, then flip what I was watching into 1 contrarian/remix video I can make TODAY.\n")
        append("End your reply with these EXACT labels so my app can import it in 1 tap:\n\n")
        append("ANGLE: <1-sentence core video angle>\n")
        append("TITLE: <high-CTR title under 55 chars>\n")
        append("THUMB: <2-4 word thumbnail text, zero words repeated from title>")
    }

    fun parseClipboardResponse(context: Context): ParsedGeminiPack? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val raw = cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: return null
        if (raw.isBlank()) return null

        fun extract(label: String): String {
            val regex = Regex("(?im)^\\**$label\\**:\\s*(.+)$")
            return regex.find(raw)?.groupValues?.getOrNull(1)?.replace("**", "")?.trim() ?: ""
        }

        val title = extract("TITLE")
        val thumb = extract("THUMB")
        val tags = extract("TAGS")
        val angle = extract("ANGLE")

        val descRegex = Regex("(?is)\\**DESC\\**:\\s*(.+?)(?=\\n\\**TAGS\\**:|\\z)")
        val desc = descRegex.find(raw)?.groupValues?.getOrNull(1)?.replace("**", "")?.trim() ?: ""

        return ParsedGeminiPack(
            title = title,
            thumbText = thumb,
            description = desc,
            tags = tags,
            script = raw,
            angle = angle
        )
    }
}
