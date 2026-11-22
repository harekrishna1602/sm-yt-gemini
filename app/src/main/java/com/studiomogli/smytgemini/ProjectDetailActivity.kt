package com.studiomogli.smytgemini

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ProjectDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IDEA_ID = "extra_idea_id"
    }

    private lateinit var store: VaultStore
    private lateinit var currentIdea: VideoIdea

    private lateinit var edtGeminiChatLink: EditText
    private lateinit var txtChatPrivacyWarning: TextView
    private lateinit var edtVideoLink: EditText
    private lateinit var imgThumbnailPreview: ImageView
    private lateinit var edtAngle: EditText
    private lateinit var edtScript: EditText
    private lateinit var edtTitle: EditText
    private lateinit var txtTitleGuard: TextView
    private lateinit var edtThumbText: EditText
    private lateinit var txtThumbGuard: TextView
    private lateinit var edtDescription: EditText
    private lateinit var edtTags: EditText
    private lateinit var recordedTakesSection: LinearLayout
    private lateinit var btnPlayAudioTake: Button
    private lateinit var btnPlayVideoTake: Button
    private var inlineAudioPlayer: MediaPlayer? = null

    private val createTimerHandler = Handler(Looper.getMainLooper())
    private val createTimerRunnable = object : Runnable {
        override fun run() {
            store.addCreateSecondsToday(5)
            createTimerHandler.postDelayed(this, 5000L)
        }
    }

    private val teleprompterLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val audioUri = data?.getStringExtra(TeleprompterActivity.RESULT_AUDIO_URI)
        val videoUri = data?.getStringExtra(TeleprompterActivity.RESULT_VIDEO_URI)
        if (!audioUri.isNullOrBlank()) currentIdea.recordedAudioUri = audioUri
        if (!videoUri.isNullOrBlank()) currentIdea.recordedVideoUri = videoUri
        store.getById(currentIdea.id)?.let { fresh ->
            if (fresh.recordedAudioUri.isNotBlank()) currentIdea.recordedAudioUri = fresh.recordedAudioUri
            if (fresh.recordedVideoUri.isNotBlank()) currentIdea.recordedVideoUri = fresh.recordedVideoUri
        }
        renderRecordedTakes()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        executeExportToStudio()
    }

    private val pickVideoForGeminiLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val prompt = MobileGeminiBridge.buildPackagingPrompt(
                creatorStyle = store.getCreatorStyle(),
                angle = edtAngle.text.toString().trim(),
                script = edtScript.text.toString().trim(),
                videoLink = edtVideoLink.text.toString().trim()
            )
            MobileGeminiBridge.openInMobileGemini(this, prompt, mediaUri = uri, mimeType = "video/*")
        }
    }

    private val pickRefPhotoForThumbLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val prompt = MobileGeminiBridge.buildThumbnailPrompt(
                creatorStyle = store.getCreatorStyle(),
                title = edtTitle.text.toString().trim(),
                thumbText = edtThumbText.text.toString().trim(),
                angle = edtAngle.text.toString().trim(),
                hasRefImage = true
            )
            MobileGeminiBridge.openInMobileGemini(this, prompt, mediaUri = uri, mimeType = "image/*")
        }
    }

    private val pickThumbImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val rawFile = getRawThumbnailFile()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(rawFile).use { output -> input.copyTo(output) }
                }

                val finalFile = getThumbnailFile()
                if (store.hasBrandLogo()) {
                    val stamped = ThumbnailBrander.stampLogoOverWatermark(
                        sourceThumbFile = rawFile,
                        brandLogoFile = store.getBrandLogoFile(),
                        outputFile = finalFile
                    )
                    if (stamped) {
                        currentIdea.thumbnailPath = finalFile.absolutePath
                        renderThumbnailPreview()
        renderRecordedTakes()
                        val savedPublic = ThumbnailBrander.saveToPublicGallery(
                            this,
                            finalFile,
                            edtTitle.text.toString().ifBlank { currentIdea.id }
                        )
                        val msg = if (savedPublic) {
                            "Logo stamped over watermark & saved to Pictures/YTGemini!"
                        } else {
                            "Logo stamped over watermark!"
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }
                }

                rawFile.copyTo(finalFile, overwrite = true)
                currentIdea.thumbnailPath = finalFile.absolutePath
                renderThumbnailPreview()
        renderRecordedTakes()
                Toast.makeText(this, "Thumbnail attached! (Set your logo in Brand & Logo to auto-cover watermark)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not attach image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_detail)

        store = VaultStore(this)
        val existingId = intent.getStringExtra(EXTRA_IDEA_ID)
        currentIdea = if (existingId != null) {
            store.getById(existingId) ?: VideoIdea(id = "idea_${System.currentTimeMillis()}", angle = "")
        } else {
            VideoIdea(id = "idea_${System.currentTimeMillis()}", angle = "")
        }

        edtGeminiChatLink = findViewById(R.id.edtGeminiChatLink)
        txtChatPrivacyWarning = findViewById(R.id.txtChatPrivacyWarning)
        edtVideoLink = findViewById(R.id.edtVideoLink)
        imgThumbnailPreview = findViewById(R.id.imgThumbnailPreview)
        edtAngle = findViewById(R.id.edtAngle)
        edtScript = findViewById(R.id.edtScript)
        edtTitle = findViewById(R.id.edtTitle)
        txtTitleGuard = findViewById(R.id.txtTitleGuard)
        edtThumbText = findViewById(R.id.edtThumbText)
        txtThumbGuard = findViewById(R.id.txtThumbGuard)
        edtDescription = findViewById(R.id.edtDescription)
        edtTags = findViewById(R.id.edtTags)
        recordedTakesSection = findViewById(R.id.recordedTakesSection)
        btnPlayAudioTake = findViewById(R.id.btnPlayAudioTake)
        btnPlayVideoTake = findViewById(R.id.btnPlayVideoTake)

        edtGeminiChatLink.setText(currentIdea.geminiChatLink)
        edtVideoLink.setText(currentIdea.videoLink)
        edtAngle.setText(currentIdea.angle)
        edtScript.setText(currentIdea.script)
        edtTitle.setText(currentIdea.title)
        edtThumbText.setText(currentIdea.thumbText)
        edtDescription.setText(currentIdea.description)
        edtTags.setText(currentIdea.tags)
        renderThumbnailPreview()
        renderRecordedTakes()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateGuardrails() }
            override fun afterTextChanged(s: Editable?) {}
        }
        edtTitle.addTextChangedListener(watcher)
        edtThumbText.addTextChangedListener(watcher)
        edtGeminiChatLink.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateChatPrivacyGuard() }
            override fun afterTextChanged(s: Editable?) {}
        })
        updateGuardrails()
        updateChatPrivacyGuard()

        findViewById<TextView>(R.id.btnBack).setOnClickListener {
            saveAndFinish(markPublished = false)
        }

        findViewById<Button>(R.id.btnExportToStudio).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                executeExportToStudio()
            }
        }

        txtChatPrivacyWarning.setOnClickListener {
            showPrivateChatHelpDialog()
        }

        findViewById<Button>(R.id.btnOpenGeminiChat).setOnClickListener {
            val chatUrl = edtGeminiChatLink.text.toString().trim()
            if (chatUrl.isBlank()) {
                showPrivateChatHelpDialog()
            } else if (MobileGeminiBridge.isPublicShareLink(chatUrl)) {
                showPrivateChatHelpDialog()
            } else {
                currentIdea.geminiChatLink = chatUrl
                store.upsert(currentIdea)
                MobileGeminiBridge.openSavedGeminiChat(this, chatUrl)
            }
        }

        findViewById<Button>(R.id.btnSendVideoToGemini).setOnClickListener {
            pickVideoForGeminiLauncher.launch("video/*")
        }

        findViewById<Button>(R.id.btnSendLinkToGemini).setOnClickListener {
            val prompt = MobileGeminiBridge.buildPackagingPrompt(
                creatorStyle = store.getCreatorStyle(),
                angle = edtAngle.text.toString().trim(),
                script = edtScript.text.toString().trim(),
                videoLink = edtVideoLink.text.toString().trim()
            )
            MobileGeminiBridge.openInMobileGemini(this, prompt, chatUrl = edtGeminiChatLink.text.toString().trim())
        }

        findViewById<Button>(R.id.btnSmartPasteGemini).setOnClickListener {
            val pack = MobileGeminiBridge.parseClipboardResponse(this)
            if (pack == null) {
                Toast.makeText(this, "Copy Gemini's reply first, then tap Smart Paste.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            var filled = 0
            if (pack.angle.isNotBlank()) { edtAngle.setText(pack.angle); filled++ }
            if (pack.title.isNotBlank()) { edtTitle.setText(pack.title); filled++ }
            if (pack.thumbText.isNotBlank()) { edtThumbText.setText(pack.thumbText); filled++ }
            if (pack.description.isNotBlank()) { edtDescription.setText(pack.description); filled++ }
            if (pack.tags.isNotBlank()) { edtTags.setText(pack.tags); filled++ }
            if (filled == 0) {
                edtScript.setText(pack.script)
                Toast.makeText(this, "Pasted Gemini script directly into Script box!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Auto-filled $filled fields from Mobile Gemini!", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnThumbYtStudio).setOnClickListener {
            val prompt = MobileGeminiBridge.buildThumbnailPrompt(
                creatorStyle = store.getCreatorStyle(),
                title = edtTitle.text.toString().trim(),
                thumbText = edtThumbText.text.toString().trim(),
                angle = edtAngle.text.toString().trim(),
                hasRefImage = false
            )
            MobileGeminiBridge.openYouTubeStudioForThumbnail(this, prompt)
        }

        findViewById<Button>(R.id.btnThumbGemini).setOnClickListener {
            val options = arrayOf(
                "Generate in Mobile Gemini (Text Prompt Only)",
                "Generate with Reference Face / Mask Photo"
            )
            AlertDialog.Builder(this)
                .setTitle("16:9 Thumbnail in Mobile Gemini")
                .setItems(options) { _, which ->
                    if (which == 0) {
                        val prompt = MobileGeminiBridge.buildThumbnailPrompt(
                            creatorStyle = store.getCreatorStyle(),
                            title = edtTitle.text.toString().trim(),
                            thumbText = edtThumbText.text.toString().trim(),
                            angle = edtAngle.text.toString().trim(),
                            hasRefImage = false
                        )
                        MobileGeminiBridge.openInMobileGemini(this, prompt, chatUrl = edtGeminiChatLink.text.toString().trim())
                    } else {
                        pickRefPhotoForThumbLauncher.launch("image/*")
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btnPickThumbGallery).setOnClickListener {
            pickThumbImageLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnStampLogoNow).setOnClickListener {
            if (!store.hasBrandLogo()) {
                Toast.makeText(this, "Upload your Channel Logo first via 'Brand & Logo' on Home.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val src = if (getRawThumbnailFile().exists()) getRawThumbnailFile() else File(currentIdea.thumbnailPath)
            if (!src.exists()) {
                Toast.makeText(this, "Attach a Gemini thumbnail first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val outFile = getThumbnailFile()
            val ok = ThumbnailBrander.stampLogoOverWatermark(src, store.getBrandLogoFile(), outFile)
            if (ok) {
                currentIdea.thumbnailPath = outFile.absolutePath
                renderThumbnailPreview()
        renderRecordedTakes()
                val saved = ThumbnailBrander.saveToPublicGallery(
                    this,
                    outFile,
                    edtTitle.text.toString().ifBlank { currentIdea.id }
                )
                Toast.makeText(
                    this,
                    if (saved) "Branded 1280x720 saved to Pictures/YTGemini!" else "Logo stamped!",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Could not merge logo.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnVoiceScript).setOnClickListener {
            val prompt = MobileGeminiBridge.buildGeminiVoiceScriptPrompt(
                creatorStyle = store.getCreatorStyle(),
                angle = edtAngle.text.toString().trim()
            )
            MobileGeminiBridge.openInMobileGemini(this, prompt, chatUrl = edtGeminiChatLink.text.toString().trim())
            Toast.makeText(this, "Tap Gemini's Mic to dictate, copy reply, then tap Smart Paste!", Toast.LENGTH_LONG).show()
        }

        // Phase 2 (v0.10.0): Open Teleprompter (Audio-Only or Facecam)
        findViewById<Button>(R.id.btnRecordIntro).setOnClickListener {
            persistFields(markPublished = false)
            val scriptToRead = edtScript.text.toString().trim().ifBlank { edtAngle.text.toString().trim() }
            if (scriptToRead.isEmpty()) {
                Toast.makeText(this, "Add a Script or Angle first to read on the Teleprompter.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, TeleprompterActivity::class.java).apply {
                putExtra(TeleprompterActivity.EXTRA_SCRIPT, scriptToRead)
                putExtra(TeleprompterActivity.EXTRA_TITLE, edtTitle.text.toString().ifBlank { currentIdea.angle })
                putExtra(TeleprompterActivity.EXTRA_IDEA_ID, currentIdea.id)
            }
            teleprompterLauncher.launch(intent)
        }

        findViewById<Button>(R.id.btnHookInGemini).setOnClickListener {
            val angle = edtAngle.text.toString().trim()
            val script = edtScript.text.toString().trim()
            if (angle.isEmpty() && script.isEmpty()) {
                Toast.makeText(this, "Add a Core Video Angle or Script first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prompt = MobileGeminiBridge.buildHookPrompt(store.getCreatorStyle(), angle, script)
            MobileGeminiBridge.openInMobileGemini(this, prompt, chatUrl = edtGeminiChatLink.text.toString().trim())
        }

        btnPlayAudioTake.setOnClickListener { toggleInlineAudioPlayback() }
        btnPlayVideoTake.setOnClickListener { openRecordedVideoTake() }

        findViewById<Button>(R.id.btnNextEpisodeRepackage).setOnClickListener {
            showNextEpisodeRepackageDialog()
        }

        findViewById<Button>(R.id.btnSaveIdea).setOnClickListener { saveAndFinish(markPublished = false) }
        findViewById<Button>(R.id.btnPublishIdea).setOnClickListener { saveAndFinish(markPublished = true) }

        findViewById<TextView>(R.id.btnDeleteIdea).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete this idea?")
                .setPositiveButton("Delete") { _, _ ->
                    store.delete(currentIdea.id)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun updateChatPrivacyGuard() {
        val url = edtGeminiChatLink.text.toString().trim()
        txtChatPrivacyWarning.visibility = if (MobileGeminiBridge.isPublicShareLink(url)) View.VISIBLE else View.GONE
    }

    private fun showPrivateChatHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keep Your Gemini Chat Private")
            .setMessage(
                "The Gemini app's Share button creates a PUBLIC snapshot link (g.co/gemini/share) that anyone with the link can view.\n\n" +
                "To link your PRIVATE live chat instead:\n" +
                "1. Tap 'Open Gemini Web' below (opens gemini.google.com in your browser).\n" +
                "2. Open your chat from the left sidebar.\n" +
                "3. Copy the URL from your browser's address bar (gemini.google.com/app/...) and paste it here.\n\n" +
                "That URL stays 100% private to your Google account!"
            )
            .setPositiveButton("Open Gemini Web") { _, _ ->
                MobileGeminiBridge.openGeminiWebForPrivateLink(this)
            }
            .setNegativeButton("Got it", null)
            .show()
    }

    private fun showNextEpisodeRepackageDialog() {
        persistFields(markPublished = false)
        val input = EditText(this).apply {
            hint = "What happens next in this episode? (optional)"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(36, 28, 36, 28)
        }
        AlertDialog.Builder(this)
            .setTitle("Next Episode (Fresh Repackage)")
            .setMessage(
                "Creates a brand-new Idea card and asks Gemini to repackage the continuation as a standalone viral video (zero 'Day 2' or 'Part 2' fatigue).\n\n" +
                "Copy Gemini's reply, come back, and tap Smart Paste!"
            )
            .setView(input)
            .setPositiveButton("Build Next Episode") { _, _ ->
                val whatsNext = input.text.toString().trim()
                val chatUrl = edtGeminiChatLink.text.toString().trim()
                val safeChatUrl = if (MobileGeminiBridge.isPublicShareLink(chatUrl)) "" else chatUrl

                val nextIdea = VideoIdea(
                    id = "idea_${System.currentTimeMillis()}",
                    angle = whatsNext.ifBlank { "Next Episode: ${currentIdea.title.ifBlank { currentIdea.angle }}" },
                    geminiChatLink = safeChatUrl,
                    stage = VideoIdea.STAGE_IDEA
                )
                store.upsert(nextIdea)

                val prompt = MobileGeminiBridge.buildNextEpisodeRepackagePrompt(
                    creatorStyle = store.getCreatorStyle(),
                    prevAngle = edtAngle.text.toString().trim(),
                    prevTitle = edtTitle.text.toString().trim(),
                    prevScript = edtScript.text.toString().trim(),
                    whatsNext = whatsNext
                )

                // Switch this screen to the newly created Next Episode card so Smart Paste lands right here
                val intent = Intent(this, ProjectDetailActivity::class.java).apply {
                    putExtra(EXTRA_IDEA_ID, nextIdea.id)
                }
                startActivity(intent)
                finish()

                MobileGeminiBridge.openInMobileGemini(this, prompt, chatUrl = safeChatUrl)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun executeExportToStudio() {
        persistFields(markPublished = false)

        val title = edtTitle.text.toString().trim()
        val desc = edtDescription.text.toString().trim()
        val tags = edtTags.text.toString().trim()

        val thumbFile = File(currentIdea.thumbnailPath)
        if (thumbFile.exists()) {
            ThumbnailBrander.saveToPublicGallery(this, thumbFile, title.ifBlank { currentIdea.id })
        }

        if (title.isNotBlank()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("YouTube Title", title))
        }

        PublishPackReceiver.showPublishPackNotification(this, title, desc, tags)

        Toast.makeText(
            this,
            "Title copied + Publish Pack pinned in notifications! Opening YT Studio...",
            Toast.LENGTH_LONG
        ).show()
        MobileGeminiBridge.openYouTubeStudioApp(this)
    }

    override fun onResume() {
        super.onResume()
        createTimerHandler.removeCallbacks(createTimerRunnable)
        createTimerHandler.postDelayed(createTimerRunnable, 5000L)
    }

    override fun onPause() {
        createTimerHandler.removeCallbacks(createTimerRunnable)
        stopInlineAudio()
        super.onPause()
    }

    private fun renderRecordedTakes() {
        val hasAudio = currentIdea.recordedAudioUri.isNotBlank()
        val hasVideo = currentIdea.recordedVideoUri.isNotBlank()
        recordedTakesSection.visibility = if (hasAudio || hasVideo) View.VISIBLE else View.GONE
        btnPlayAudioTake.visibility = if (hasAudio) View.VISIBLE else View.GONE
        btnPlayVideoTake.visibility = if (hasVideo) View.VISIBLE else View.GONE
    }

    private fun toggleInlineAudioPlayback() {
        if (inlineAudioPlayer != null) {
            stopInlineAudio()
            return
        }
        val uriStr = currentIdea.recordedAudioUri
        if (uriStr.isBlank()) return
        try {
            val mp = MediaPlayer().apply {
                setDataSource(this@ProjectDetailActivity, Uri.parse(uriStr))
                setOnCompletionListener { stopInlineAudio() }
                prepare()
                start()
            }
            inlineAudioPlayer = mp
            btnPlayAudioTake.text = "Stop Audio"
            btnPlayAudioTake.setBackgroundColor(0xFFFF3B30.toInt())
        } catch (e: Exception) {
            Toast.makeText(this, "Audio file not found in Music/YTGemini.", Toast.LENGTH_SHORT).show()
            stopInlineAudio()
        }
    }

    private fun stopInlineAudio() {
        try {
            inlineAudioPlayer?.stop()
            inlineAudioPlayer?.release()
        } catch (_: Exception) {
        }
        inlineAudioPlayer = null
        if (::btnPlayAudioTake.isInitialized) {
            btnPlayAudioTake.text = "Play Audio"
            btnPlayAudioTake.setBackgroundColor(0xFF34C759.toInt())
        }
    }

    private fun openRecordedVideoTake() {
        val uriStr = currentIdea.recordedVideoUri
        if (uriStr.isBlank()) return
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(uriStr), "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(viewIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Video file not found in Movies/YTGemini.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getRawThumbnailFile(): File {
        val dir = File(filesDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "raw_${currentIdea.id}.jpg")
    }

    private fun getThumbnailFile(): File {
        val dir = File(filesDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "thumb_${currentIdea.id}.jpg")
    }

    private fun renderThumbnailPreview() {
        val path = currentIdea.thumbnailPath
        if (path.isNotBlank() && File(path).exists()) {
            imgThumbnailPreview.setImageBitmap(BitmapFactory.decodeFile(path))
            imgThumbnailPreview.visibility = View.VISIBLE
        } else {
            imgThumbnailPreview.visibility = View.GONE
        }
    }

    private fun updateGuardrails() {
        val title = edtTitle.text.toString().trim()
        val thumb = edtThumbText.text.toString().trim()

        if (title.length > 55) {
            txtTitleGuard.text = "${title.length}/55 chars — Too long for mobile feed"
            txtTitleGuard.setTextColor(0xFFFF453A.toInt())
        } else {
            txtTitleGuard.text = "${title.length}/55 chars — Clean mobile length"
            txtTitleGuard.setTextColor(0xFF34C759.toInt())
        }

        val titleWords = title.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val thumbWords = thumb.lowercase(Locale.US).split(Regex("\\W+")).filter { it.length > 2 }
        val repeated = thumbWords.filter { it in titleWords }
        val wordCount = if (thumb.isEmpty()) 0 else thumb.split(Regex("\\s+")).size

        when {
            wordCount > 4 -> {
                txtThumbGuard.text = "$wordCount words — Keep thumbnail text under 4 words"
                txtThumbGuard.setTextColor(0xFFFF453A.toInt())
            }
            repeated.isNotEmpty() -> {
                txtThumbGuard.text = "No-Repeat Alert: '${repeated.joinToString()}' already in Title"
                txtThumbGuard.setTextColor(0xFFFF9F0A.toInt())
            }
            else -> {
                txtThumbGuard.text = "$wordCount/4 words — High-contrast packaging"
                txtThumbGuard.setTextColor(0xFF34C759.toInt())
            }
        }
    }

    private fun persistFields(markPublished: Boolean): Boolean {
        val angle = edtAngle.text.toString().trim()
        val script = edtScript.text.toString().trim()
        val title = edtTitle.text.toString().trim()
        val thumb = edtThumbText.text.toString().trim()
        val chatLink = edtGeminiChatLink.text.toString().trim()
        val link = edtVideoLink.text.toString().trim()
        val desc = edtDescription.text.toString().trim()
        val tags = edtTags.text.toString().trim()

        if (angle.isEmpty() && script.isEmpty() && title.isEmpty() && link.isEmpty()) {
            return false
        }

        currentIdea.angle = angle.ifEmpty { title.ifEmpty { "Untitled Idea" } }
        currentIdea.script = script
        currentIdea.title = title
        currentIdea.thumbText = thumb
        currentIdea.geminiChatLink = chatLink
        currentIdea.videoLink = link
        currentIdea.description = desc
        currentIdea.tags = tags

        currentIdea.stage = when {
            markPublished -> VideoIdea.STAGE_PUBLISHED
            currentIdea.stage == VideoIdea.STAGE_PUBLISHED -> VideoIdea.STAGE_PUBLISHED
            title.isNotEmpty() && (script.isNotEmpty() || desc.isNotEmpty()) -> VideoIdea.STAGE_READY
            script.isNotEmpty() -> VideoIdea.STAGE_SCRIPTING
            else -> VideoIdea.STAGE_IDEA
        }

        store.upsert(currentIdea)
        return true
    }

    private fun saveAndFinish(markPublished: Boolean) {
        if (persistFields(markPublished)) {
            Toast.makeText(
                this,
                if (markPublished) "Marked as Published!" else "Saved to Workbench",
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }
}
