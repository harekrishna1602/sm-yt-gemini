package com.studiomogli.smytgemini

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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

    private val createTimerHandler = Handler(Looper.getMainLooper())
    private val createTimerRunnable = object : Runnable {
        override fun run() {
            store.addCreateSecondsToday(5)
            createTimerHandler.postDelayed(this, 5000L)
        }
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

        edtVideoLink.setText(currentIdea.videoLink)
        edtAngle.setText(currentIdea.angle)
        edtScript.setText(currentIdea.script)
        edtTitle.setText(currentIdea.title)
        edtThumbText.setText(currentIdea.thumbText)
        edtDescription.setText(currentIdea.description)
        edtTags.setText(currentIdea.tags)
        renderThumbnailPreview()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateGuardrails() }
            override fun afterTextChanged(s: Editable?) {}
        }
        edtTitle.addTextChangedListener(watcher)
        edtThumbText.addTextChangedListener(watcher)
        updateGuardrails()

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
            MobileGeminiBridge.openInMobileGemini(this, prompt)
        }

        findViewById<Button>(R.id.btnSmartPasteGemini).setOnClickListener {
            val pack = MobileGeminiBridge.parseClipboardResponse(this)
            if (pack == null) {
                Toast.makeText(this, "Copy Gemini's reply first, then tap Smart Paste.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            var filled = 0
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
                        MobileGeminiBridge.openInMobileGemini(this, prompt)
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
            MobileGeminiBridge.openInMobileGemini(this, prompt)
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
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnHookInGemini).setOnClickListener {
            val angle = edtAngle.text.toString().trim()
            val script = edtScript.text.toString().trim()
            if (angle.isEmpty() && script.isEmpty()) {
                Toast.makeText(this, "Add a Core Video Angle or Script first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prompt = MobileGeminiBridge.buildHookPrompt(store.getCreatorStyle(), angle, script)
            MobileGeminiBridge.openInMobileGemini(this, prompt)
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
        super.onPause()
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
