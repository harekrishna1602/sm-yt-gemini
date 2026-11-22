package com.studiomogli.smytgemini

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

class TeleprompterActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCRIPT = "extra_script"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_IDEA_ID = "extra_idea_id"
        const val RESULT_AUDIO_URI = "result_audio_uri"
        const val RESULT_VIDEO_URI = "result_video_uri"
    }

    private lateinit var cameraPreview: PreviewView
    private lateinit var teleprompterOverlayBg: View
    private lateinit var scrollView: ScrollView
    private lateinit var txtPrompterScript: TextView
    private lateinit var txtRecTimer: TextView
    private lateinit var btnModeAudio: TextView
    private lateinit var btnModeFacecam: TextView
    private lateinit var btnRecordToggle: Button
    private lateinit var seekSpeed: SeekBar
    private lateinit var txtSpeedLabel: TextView

    private var isFacecamMode = false
    private var isRecording = false
    private var scrollSpeedPx = 3
    private var elapsedSeconds = 0
    private var videoTitle = "Intro"
    private var ideaId = ""
    private var lastSavedAudioUri = ""
    private var lastSavedVideoUri = ""

    // Audio-Only Recorder
    private var audioRecorder: MediaRecorder? = null
    private var currentAudioTempFile: File? = null

    // Facecam CameraX Recorder
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeVideoRecording: Recording? = null

    private val handler = Handler(Looper.getMainLooper())

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                scrollView.smoothScrollBy(0, scrollSpeedPx)
                handler.postDelayed(this, 35L)
            }
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                elapsedSeconds++
                val m = elapsedSeconds / 60
                val s = elapsedSeconds % 60
                txtRecTimer.text = String.format(Locale.US, "REC %02d:%02d", m, s)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val audioOk = perms[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camOk = perms[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        if (isFacecamMode) {
            if (audioOk && camOk) {
                startCameraPreview()
            } else {
                Toast.makeText(this, "Camera & Mic permissions needed for Facecam.", Toast.LENGTH_LONG).show()
                switchToMode(facecam = false)
            }
        } else if (!audioOk) {
            Toast.makeText(this, "Microphone permission is required to record audio.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter)

        cameraPreview = findViewById(R.id.cameraPreview)
        teleprompterOverlayBg = findViewById(R.id.teleprompterOverlayBg)
        scrollView = findViewById(R.id.prompterScrollView)
        txtPrompterScript = findViewById(R.id.txtPrompterScript)
        txtRecTimer = findViewById(R.id.txtRecTimer)
        btnModeAudio = findViewById(R.id.btnModeAudio)
        btnModeFacecam = findViewById(R.id.btnModeFacecam)
        btnRecordToggle = findViewById(R.id.btnRecordToggle)
        seekSpeed = findViewById(R.id.seekScrollSpeed)
        txtSpeedLabel = findViewById(R.id.txtSpeedLabel)

        val script = intent.getStringExtra(EXTRA_SCRIPT)?.trim().orEmpty()
        videoTitle = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "Intro" }
        ideaId = intent.getStringExtra(EXTRA_IDEA_ID)?.trim().orEmpty()

        txtPrompterScript.text = if (script.isNotBlank()) {
            "\n\n$script\n\n\n\n— END OF SCRIPT —\n\n\n"
        } else {
            "\n\nNo script yet. Go back and write or dictate your intro first.\n\n"
        }

        findViewById<TextView>(R.id.btnClosePrompter).setOnClickListener {
            if (isRecording) stopRecording()
            deliverResultAndFinish()
        }

        btnModeAudio.setOnClickListener {
            if (isRecording) {
                Toast.makeText(this, "Stop recording before switching modes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            switchToMode(facecam = false)
        }

        btnModeFacecam.setOnClickListener {
            if (isRecording) {
                Toast.makeText(this, "Stop recording before switching modes.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            switchToMode(facecam = true)
        }

        seekSpeed.progress = 2
        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                scrollSpeedPx = progress + 1
                txtSpeedLabel.text = "Speed ${progress + 1}x"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnRecordToggle.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        switchToMode(facecam = false)
    }

    private fun switchToMode(facecam: Boolean) {
        isFacecamMode = facecam
        if (facecam) {
            btnModeFacecam.setBackgroundResource(R.drawable.bg_badge_red)
            btnModeFacecam.setTextColor(0xFFFFFFFF.toInt())
            btnModeAudio.setBackgroundResource(R.drawable.bg_pill_surface)
            btnModeAudio.setTextColor(0xFF9CA3AF.toInt())

            cameraPreview.visibility = View.VISIBLE
            teleprompterOverlayBg.setBackgroundColor(0x880B0D10.toInt())

            if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                startCameraPreview()
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            }
        } else {
            btnModeAudio.setBackgroundResource(R.drawable.bg_badge_red)
            btnModeAudio.setTextColor(0xFFFFFFFF.toInt())
            btnModeFacecam.setBackgroundResource(R.drawable.bg_pill_surface)
            btnModeFacecam.setTextColor(0xFF9CA3AF.toInt())

            stopCameraPreview()
            cameraPreview.visibility = View.GONE
            teleprompterOverlayBg.setBackgroundColor(0xFF0B0D10.toInt())
        }
    }

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun startCameraPreview() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                cameraProvider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreview.surfaceProvider)
                }
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HD))
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    videoCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
                switchToMode(facecam = false)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCameraPreview() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
    }

    private fun startRecording() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        if (isFacecamMode && !hasPermission(Manifest.permission.CAMERA)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }

        scrollView.scrollTo(0, 0)
        elapsedSeconds = 0
        txtRecTimer.text = "REC 00:00"
        txtRecTimer.visibility = View.VISIBLE

        if (isFacecamMode) {
            startFacecamRecording()
        } else {
            startAudioOnlyRecording()
        }
    }

    private fun startAudioOnlyRecording() {
        try {
            val tempFile = File(cacheDir, "ytgemini_audio_${System.currentTimeMillis()}.m4a")
            currentAudioTempFile = tempFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(tempFile.absolutePath)
                prepare()
                start()
            }
            audioRecorder = recorder
            setRecordingUiActive(true)
        } catch (e: Exception) {
            Toast.makeText(this, "Audio record failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFacecamRecording() {
        val vc = videoCapture ?: run {
            Toast.makeText(this, "Camera still initializing, tap again.", Toast.LENGTH_SHORT).show()
            return
        }

        val safeName = videoTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        val fileName = "YTGemini_${safeName}_${System.currentTimeMillis() % 10000}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/YTGemini")
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(this, outputOptions)
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            pending = pending.withAudioEnabled()
        }

        activeVideoRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> setRecordingUiActive(true)
                is VideoRecordEvent.Finalize -> {
                    setRecordingUiActive(false)
                    if (!event.hasError()) {
                        val savedUri = event.outputResults.outputUri.toString()
                        lastSavedVideoUri = savedUri
                        persistTakeToIdea(audioUri = null, videoUri = savedUri)
                        Toast.makeText(this, "Facecam saved & linked to Idea!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Video save error: ${event.error}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun stopRecording() {
        if (isFacecamMode) {
            activeVideoRecording?.stop()
            activeVideoRecording = null
        } else {
            try {
                audioRecorder?.stop()
                audioRecorder?.release()
            } catch (_: Exception) {
            }
            audioRecorder = null
            setRecordingUiActive(false)

            currentAudioTempFile?.let { file ->
                if (file.exists()) {
                    val savedUri = saveAudioToPublicMusic(file)
                    file.delete()
                    if (savedUri != null) {
                        lastSavedAudioUri = savedUri
                        persistTakeToIdea(audioUri = savedUri, videoUri = null)
                        Toast.makeText(this, "Audio saved & linked to Idea!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Audio recording finished.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            currentAudioTempFile = null
        }
    }

    private fun persistTakeToIdea(audioUri: String?, videoUri: String?) {
        if (ideaId.isBlank()) return
        val store = VaultStore(this)
        val idea = store.getById(ideaId) ?: return
        if (!audioUri.isNullOrBlank()) idea.recordedAudioUri = audioUri
        if (!videoUri.isNullOrBlank()) idea.recordedVideoUri = videoUri
        store.upsert(idea)
    }

    private fun deliverResultAndFinish() {
        val data = Intent().apply {
            if (lastSavedAudioUri.isNotBlank()) putExtra(RESULT_AUDIO_URI, lastSavedAudioUri)
            if (lastSavedVideoUri.isNotBlank()) putExtra(RESULT_VIDEO_URI, lastSavedVideoUri)
        }
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun saveAudioToPublicMusic(source: File): String? {
        return try {
            val safeName = videoTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
            val fileName = "YTGemini_${safeName}_${System.currentTimeMillis() % 10000}.m4a"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/YTGemini")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                contentResolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                uri.toString()
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "YTGemini"
                )
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, fileName)
                source.copyTo(dest, overwrite = true)
                Uri.fromFile(dest).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun setRecordingUiActive(active: Boolean) {
        isRecording = active
        handler.removeCallbacks(scrollRunnable)
        handler.removeCallbacks(timerRunnable)
        if (active) {
            btnRecordToggle.text = "Stop Recording"
            btnRecordToggle.setBackgroundColor(0xFFFF3B30.toInt())
            handler.postDelayed(scrollRunnable, 35L)
            handler.postDelayed(timerRunnable, 1000L)
        } else {
            btnRecordToggle.text = if (isFacecamMode) "Start Facecam + Scroll" else "Start Audio + Scroll"
            btnRecordToggle.setBackgroundColor(0xFF34C759.toInt())
            txtRecTimer.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        if (isRecording) stopRecording()
        stopCameraPreview()
        super.onDestroy()
    }
}
