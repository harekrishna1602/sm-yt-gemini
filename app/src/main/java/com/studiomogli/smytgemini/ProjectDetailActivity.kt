package com.studiomogli.smytgemini

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
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
    private lateinit var btnPickLocalVideo: Button
    private lateinit var btnAnalyzeLink: Button
    private lateinit var imgThumbnailPreview: ImageView
    private lateinit var btnGenThumbnail: Button
    private lateinit var btnPickThumbGallery: Button

    private lateinit var edtAngle: EditText
    private lateinit var edtScript: EditText
    private lateinit var edtTitle: EditText
    private lateinit var txtTitleGuard: TextView
    private lateinit var edtThumbText: EditText
    private lateinit var txtThumbGuard: TextView
    private lateinit var edtDescription: EditText
    private lateinit var edtTags: EditText
    private lateinit var btnAiHook: Button
    private lateinit var btnAiPackage: Button

    private var lastThumbnailPrompt: String = ""

    // Phase 4: Automatically tracks active creation time while working on an idea
    private val createTimerHandler = Handler(Looper.getMainLooper())
    private val createTimerRunnable = object : Runnable {
        override fun run() {
            store.addCreateSecondsToday(5)
            createTimerHandler.postDelayed(this, 5000L)
        }
    }

    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim() ?: ""
            if (spoken.isNotEmpty()) {
                val existing = edtScript.text.toString().trim()
                val updated = if (existing.isEmpty()) spoken else "$existing\n$spoken"
                edtScript.setText(updated)
                edtScript.setSelection(updated.length)
            }
        }
    }

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val apiKey = requireApiKey() ?: return@registerForActivityResult
            btnPickLocalVideo.isEnabled = false
            btnPickLocalVideo.text = "Analyzing Video..."
            GeminiEngine.analyzeLocalVideo(
                context = this,
                apiKey = apiKey,
                creatorStyle = store.getCreatorStyle(),
                videoUri = uri,
                angleHint = edtAngle.text.toString().trim()
            ) { result ->
                runOnUiThread {
                    btnPickLocalVideo.isEnabled = true
                    btnPickLocalVideo.text = "Pick Device Video"
                    handleMetadataResult(result)
                }
            }
        }
    }

    private val pickThumbImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val outFile = getThumbnailFile()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                currentIdea.thumbnailPath = outFile.absolutePath
                renderThumbnailPreview()
                Toast.makeText(this, "Thumbnail attached!", Toast.LENGTH_SHORT).show()
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
        btnPickLocalVideo = findViewById(R.id.btnPickLocalVideo)
        btnAnalyzeLink = findViewById(R.id.btnAnalyzeLink)
        imgThumbnailPreview = findViewById(R.id.imgThumbnailPreview)
        btnGenThumbnail = findViewById(R.id.btnGenThumbnail)
        btnPickThumbGallery = findViewById(R.id.btnPickThumbGallery)

        edtAngle = findViewById(R.id.edtAngle)
        edtScript = findViewById(R.id.edtScript)
        edtTitle = findViewById(R.id.edtTitle)
        txtTitleGuard = findViewById(R.id.txtTitleGuard)
        edtThumbText = findViewById(R.id.edtThumbText)
        txtThumbGuard = findViewById(R.id.txtThumbGuard)
        edtDescription = findViewById(R.id.edtDescription)
        edtTags = findViewById(R.id.edtTags)
        btnAiHook = findViewById(R.id.btnAiHook)
        btnAiPackage = findViewById(R.id.btnAiPackage)

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

        btnPickLocalVideo.setOnClickListener {
            if (requireApiKey() != null) pickVideoLauncher.launch("video/*")
        }

        btnAnalyzeLink.setOnClickListener {
            val apiKey = requireApiKey() ?: return@setOnClickListener
            val link = edtVideoLink.text.toString().trim()
            if (link.isEmpty()) {
                Toast.makeText(this, "Paste an unlisted or public YouTube link first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnAnalyzeLink.isEnabled = false
            btnAnalyzeLink.text = "Analyzing..."
            GeminiEngine.analyzeYouTubeLink(
                apiKey = apiKey,
                creatorStyle = store.getCreatorStyle(),
                youtubeUrl = link,
                angleHint = edtAngle.text.toString().trim()
            ) { result ->
                runOnUiThread {
                    btnAnalyzeLink.isEnabled = true
                    btnAnalyzeLink.text = "Analyze Link"
                    handleMetadataResult(result)
                }
            }
        }

        btnGenThumbnail.setOnClickListener { runAiThumbnailGeneration() }
        btnPickThumbGallery.setOnClickListener { pickThumbImageLauncher.launch("image/*") }

        findViewById<Button>(R.id.btnVoiceScript).setOnClickListener {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your video script naturally...")
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Voice input not supported on this device", Toast.LENGTH_SHORT).show()
            }
        }

        btnAiHook.setOnClickListener { runAiHookTightener() }
        btnAiPackage.setOnClickListener { runAiPackagingGenerator() }

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

    override fun onResume() {
        super.onResume()
        createTimerHandler.removeCallbacks(createTimerRunnable)
        createTimerHandler.postDelayed(createTimerRunnable, 5000L)
    }

    override fun onPause() {
        createTimerHandler.removeCallbacks(createTimerRunnable)
        super.onPause()
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

    private fun handleMetadataResult(result: Result<FullVideoMetadata>) {
        result.fold(
            onSuccess = { meta ->
                if (meta.title.isNotBlank()) edtTitle.setText(meta.title)
                if (meta.thumbText.isNotBlank()) edtThumbText.setText(meta.thumbText)
                if (meta.description.isNotBlank()) edtDescription.setText(meta.description)
                if (meta.tags.isNotBlank()) edtTags.setText(meta.tags)
                if (edtAngle.text.toString().isBlank() && meta.title.isNotBlank()) {
                    edtAngle.setText(meta.title)
                }
                lastThumbnailPrompt = meta.thumbnailPrompt
                Toast.makeText(this, "Title, Thumb Text, Description & Tags auto-filled!", Toast.LENGTH_LONG).show()
            },
            onFailure = { err ->
                Toast.makeText(this, "AI Error: ${err.message}", Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun runAiThumbnailGeneration() {
        val apiKey = requireApiKey() ?: return
        val title = edtTitle.text.toString().trim().ifEmpty { edtAngle.text.toString().trim() }
        if (title.isEmpty() && lastThumbnailPrompt.isEmpty()) {
            Toast.makeText(this, "Add a Title/Angle or analyze a video first.", Toast.LENGTH_SHORT).show()
            return
        }

        btnGenThumbnail.isEnabled = false
        btnGenThumbnail.text = "Generating 16:9..."

        GeminiEngine.generateThumbnailImage(
            apiKey = apiKey,
            creatorStyle = store.getCreatorStyle(),
            title = title,
            thumbText = edtThumbText.text.toString().trim(),
            customVisualPrompt = lastThumbnailPrompt
        ) { result ->
            runOnUiThread {
                btnGenThumbnail.isEnabled = true
                btnGenThumbnail.text = "Generate AI Thumbnail"
                result.fold(
                    onSuccess = { bytes ->
                        try {
                            val outFile = getThumbnailFile()
                            FileOutputStream(outFile).use { it.write(bytes) }
                            currentIdea.thumbnailPath = outFile.absolutePath
                            renderThumbnailPreview()
                            Toast.makeText(this, "16:9 AI Thumbnail generated & saved!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "Imagen Error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun requireApiKey(): String? {
        val key = store.getGeminiApiKey()
        if (key.isBlank()) {
            Toast.makeText(this, "Tap 'Connect AI' on the home screen to add your free Gemini API key first.", Toast.LENGTH_LONG).show()
            return null
        }
        return key
    }

    private fun runAiHookTightener() {
        val apiKey = requireApiKey() ?: return
        val angle = edtAngle.text.toString().trim()
        val script = edtScript.text.toString().trim()
        if (angle.isEmpty() && script.isEmpty()) {
            Toast.makeText(this, "Add a Core Video Angle or Voice Script first.", Toast.LENGTH_SHORT).show()
            return
        }

        btnAiHook.isEnabled = false
        btnAiHook.text = "Thinking..."

        GeminiEngine.generateTightHook(
            apiKey = apiKey,
            creatorStyle = store.getCreatorStyle(),
            angle = angle.ifEmpty { script.take(100) },
            rawScript = script
        ) { result ->
            runOnUiThread {
                btnAiHook.isEnabled = true
                btnAiHook.text = "Tighten 15s Hook (AI)"
                result.fold(
                    onSuccess = { tightened ->
                        AlertDialog.Builder(this)
                            .setTitle("Gemini 15s Hook & Beats")
                            .setMessage(tightened)
                            .setPositiveButton("Apply to Script") { _, _ ->
                                edtScript.setText(tightened)
                                edtScript.setSelection(tightened.length)
                                Toast.makeText(this, "Applied directly to script!", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Discard", null)
                            .show()
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "AI Error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun runAiPackagingGenerator() {
        val apiKey = requireApiKey() ?: return
        val angle = edtAngle.text.toString().trim()
        val script = edtScript.text.toString().trim()
        if (angle.isEmpty() && script.isEmpty()) {
            Toast.makeText(this, "Add a Core Video Angle or Script first.", Toast.LENGTH_SHORT).show()
            return
        }

        btnAiPackage.isEnabled = false
        btnAiPackage.text = "Packaging..."

        GeminiEngine.generatePackagingOptions(
            apiKey = apiKey,
            creatorStyle = store.getCreatorStyle(),
            angle = angle.ifEmpty { script.take(100) },
            script = script
        ) { result ->
            runOnUiThread {
                btnAiPackage.isEnabled = true
                btnAiPackage.text = "Auto-Package Title & Thumb (AI)"
                result.fold(
                    onSuccess = { options ->
                        val labels = options.mapIndexed { idx, opt ->
                            "${idx + 1}. ${opt.title}\nThumb: \"${opt.thumbText}\" — ${opt.visualCue}"
                        }.toTypedArray()

                        AlertDialog.Builder(this)
                            .setTitle("Tap to Auto-Fill Title & Thumb")
                            .setItems(labels) { _, which ->
                                val picked = options[which]
                                edtTitle.setText(picked.title)
                                edtThumbText.setText(picked.thumbText)
                                lastThumbnailPrompt = picked.visualCue
                                Toast.makeText(this, "Packaging auto-filled!", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    },
                    onFailure = { err ->
                        Toast.makeText(this, "AI Error: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
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

    private fun saveAndFinish(markPublished: Boolean) {
        val angle = edtAngle.text.toString().trim()
        val script = edtScript.text.toString().trim()
        val title = edtTitle.text.toString().trim()
        val thumb = edtThumbText.text.toString().trim()
        val link = edtVideoLink.text.toString().trim()
        val desc = edtDescription.text.toString().trim()
        val tags = edtTags.text.toString().trim()

        if (angle.isEmpty() && script.isEmpty() && title.isEmpty() && link.isEmpty()) {
            finish()
            return
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
        Toast.makeText(
            this,
            if (markPublished) "Marked as Published!" else "Saved to Workbench",
            Toast.LENGTH_SHORT
        ).show()
        finish()
    }
}
