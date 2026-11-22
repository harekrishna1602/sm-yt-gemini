package com.studiomogli.smytgemini

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: VaultStore
    private lateinit var adapter: IdeaAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var txtShippedCount: TextView
    private lateinit var txtStreakCount: TextView
    private lateinit var txtDriveStatus: TextView
    private lateinit var btnCreatorStyle: TextView

    private lateinit var cardChannelStats: LinearLayout
    private lateinit var txtChannelName: TextView
    private lateinit var txtChannelNumbers: TextView
    private lateinit var txtWatchCreateRatio: TextView
    private lateinit var txtAccountabilityAlert: TextView

    private var activeLogoPreviewInDialog: ImageView? = null

    private val pickBrandLogoLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val outFile = store.getBrandLogoFile()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output -> input.copyTo(output) }
                }
                activeLogoPreviewInDialog?.let { img ->
                    img.setImageBitmap(BitmapFactory.decodeFile(outFile.absolutePath))
                    img.visibility = View.VISIBLE
                }
                refreshUi()
                Toast.makeText(this, "Channel Logo saved! Will auto-cover Gemini watermark.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save logo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportDriveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(store.exportVaultJson().toByteArray(Charsets.UTF_8))
                }
                store.markDriveSynced()
                refreshUi()
                Toast.makeText(this, "Vault saved to your Google Drive!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val importDriveLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val count = store.importVaultJson(raw)
                refreshUi()
                Toast.makeText(this, "Restored $count ideas from your Drive vault!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not read vault file", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = VaultStore(this)

        recyclerView = findViewById(R.id.recyclerIdeas)
        emptyState = findViewById(R.id.emptyStateLayout)
        txtShippedCount = findViewById(R.id.txtShippedCount)
        txtStreakCount = findViewById(R.id.txtStreakCount)
        txtDriveStatus = findViewById(R.id.txtDriveStatus)
        btnCreatorStyle = findViewById(R.id.btnCreatorStyle)

        cardChannelStats = findViewById(R.id.cardChannelStats)
        txtChannelName = findViewById(R.id.txtChannelName)
        txtChannelNumbers = findViewById(R.id.txtChannelNumbers)
        txtWatchCreateRatio = findViewById(R.id.txtWatchCreateRatio)
        txtAccountabilityAlert = findViewById(R.id.txtAccountabilityAlert)

        adapter = IdeaAdapter(emptyList()) { idea ->
            openProjectDetail(idea.id)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabNewIdea).setOnClickListener {
            openProjectDetail(null)
        }

        findViewById<TextView>(R.id.btnDriveVault).setOnClickListener {
            showDriveVaultDialog()
        }

        btnCreatorStyle.setOnClickListener {
            showCreatorStyleDialog()
        }

        cardChannelStats.setOnClickListener {
            showChannelConnectDialog()
        }

        findViewById<Button>(R.id.btnLogWatch15).setOnClickListener {
            store.addWatchMinsToday(15)
            refreshUi()
            Toast.makeText(this, "Logged +15m watch time", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnBingeFlip).setOnClickListener {
            showBingeToBuildDialog()
        }

        findViewById<Button>(R.id.btnImportGeminiIdea).setOnClickListener {
            importIdeaFromGeminiClipboard()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        val all = store.getAllIdeas()
        val shipped = all.count { it.stage == VideoIdea.STAGE_PUBLISHED }
        val streak = store.getStreakDays()

        txtShippedCount.text = shipped.toString()
        txtStreakCount.text = "${streak}d"

        val hasLogo = store.hasBrandLogo()
        val hasStyle = store.getCreatorStyle().isNotBlank()
        btnCreatorStyle.text = if (hasLogo && hasStyle) "Brand ✓" else "Brand & Logo"
        btnCreatorStyle.setTextColor(if (hasLogo) 0xFF34C759.toInt() else 0xFFFF9F0A.toInt())

        val ch = store.getChannelStats()
        if (ch.title.isNotBlank()) {
            txtChannelName.text = "${ch.title} (${ch.handle})"
            val parts = listOf(ch.subscribersText, ch.videosText).filter { it.isNotBlank() }
            txtChannelNumbers.text = if (parts.isNotEmpty()) parts.joinToString("  •  ") else "Tap to refresh stats"
        } else {
            txtChannelName.text = "Connect YouTube Channel"
            txtChannelNumbers.text = "Tap to sync with just your @handle (Zero API Key)"
        }

        val watchMins = store.getWatchMinsToday()
        val createMins = store.getCreateMinsToday()
        txtWatchCreateRatio.text = "Watched: ${watchMins}m   •   Created: ${createMins}m"

        if (watchMins > createMins && watchMins >= 15) {
            txtAccountabilityAlert.text = "Reality Check: Watched ${watchMins}m vs ${createMins}m creating. Tap Binge-to-Build!"
            txtAccountabilityAlert.setTextColor(0xFFFF453A.toInt())
        } else {
            val logoNote = if (hasLogo) "Auto-Logo Ready" else "Add Logo in Brand"
            txtAccountabilityAlert.text = "$logoNote • Teleprompter Ready"
            txtAccountabilityAlert.setTextColor(0xFF34C759.toInt())
        }

        val lastSync = store.getLastDriveSync()
        txtDriveStatus.text = if (lastSync > 0L) {
            val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSync))
            "Private on device & Drive • Last backup: $fmt"
        } else {
            "100% private on your device. Tap Drive Vault to back up to Google Drive."
        }

        adapter.updateData(all)
        emptyState.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openProjectDetail(ideaId: String?) {
        val intent = Intent(this, ProjectDetailActivity::class.java)
        if (ideaId != null) intent.putExtra(ProjectDetailActivity.EXTRA_IDEA_ID, ideaId)
        startActivity(intent)
    }

    private fun showBingeToBuildDialog() {
        val input = EditText(this).apply {
            hint = "What topic/videos were you just watching?"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(36, 28, 36, 28)
        }
        AlertDialog.Builder(this)
            .setTitle("Binge-to-Build (Mobile Gemini)")
            .setMessage("Sends your watch stats & topic straight to the Google Gemini app on your phone. Copy Gemini's reply, then tap 'Paste Idea' here.")
            .setView(input)
            .setPositiveButton("Open in Gemini") { _, _ ->
                val topic = input.text.toString().trim().ifEmpty { "trending videos in my niche" }
                val prompt = MobileGeminiBridge.buildBingeToBuildPrompt(
                    creatorStyle = store.getCreatorStyle(),
                    watchMins = store.getWatchMinsToday(),
                    createMins = store.getCreateMinsToday(),
                    topic = topic
                )
                MobileGeminiBridge.openInMobileGemini(this, prompt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importIdeaFromGeminiClipboard() {
        val pack = MobileGeminiBridge.parseClipboardResponse(this)
        if (pack == null) {
            Toast.makeText(this, "Copy Gemini's reply first, then tap Paste Idea.", Toast.LENGTH_LONG).show()
            return
        }
        val idea = VideoIdea(
            id = "idea_${System.currentTimeMillis()}",
            angle = pack.angle.ifBlank { pack.title.ifBlank { pack.script.lines().firstOrNull()?.take(80) ?: "Gemini Idea" } },
            title = pack.title,
            thumbText = pack.thumbText,
            description = pack.description,
            tags = pack.tags,
            script = if (pack.title.isBlank() && pack.angle.isBlank()) pack.script else "",
            stage = VideoIdea.STAGE_IDEA
        )
        store.upsert(idea)
        refreshUi()
        Toast.makeText(this, "Imported from Mobile Gemini!", Toast.LENGTH_SHORT).show()
        openProjectDetail(idea.id)
    }

    private fun showCreatorStyleDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_creator_style, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtStyle = view.findViewById<EditText>(R.id.edtCreatorStyle)
        val imgLogoPreview = view.findViewById<ImageView>(R.id.imgBrandLogoPreview)
        val btnPickLogo = view.findViewById<Button>(R.id.btnPickBrandLogo)

        activeLogoPreviewInDialog = imgLogoPreview
        edtStyle.setText(store.getCreatorStyle())

        if (store.hasBrandLogo()) {
            imgLogoPreview.setImageBitmap(BitmapFactory.decodeFile(store.getBrandLogoFile().absolutePath))
            imgLogoPreview.visibility = View.VISIBLE
        }

        btnPickLogo.setOnClickListener {
            pickBrandLogoLauncher.launch("image/*")
        }

        view.findViewById<Button>(R.id.btnSaveStyle).setOnClickListener {
            store.saveCreatorStyle(edtStyle.text.toString())
            refreshUi()
            dialog.dismiss()
            Toast.makeText(this, "Brand & Style saved!", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btnCancelStyle).setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { activeLogoPreviewInDialog = null }
        dialog.show()
    }

    private fun showChannelConnectDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_channel_connect, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtHandle = view.findViewById<EditText>(R.id.edtYtHandle)
        val btnSync = view.findViewById<Button>(R.id.btnSyncChannel)

        edtHandle.setText(store.getYtHandle())

        btnSync.setOnClickListener {
            val handle = edtHandle.text.toString().trim()
            if (handle.isEmpty()) {
                Toast.makeText(this, "Enter your @handle or channel link", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.saveYtHandle(handle)
            btnSync.isEnabled = false
            btnSync.text = "Syncing..."

            YouTubeDataClient.fetchChannelStatsNoApiKey(handle) { result ->
                runOnUiThread {
                    btnSync.isEnabled = true
                    btnSync.text = "Sync Channel (No API Key)"
                    result.fold(
                        onSuccess = { stats ->
                            store.saveChannelStats(stats)
                            refreshUi()
                            dialog.dismiss()
                            Toast.makeText(this, "Connected ${stats.title}!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { err ->
                            Toast.makeText(this, "Sync Error: ${err.message}", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }

        view.findViewById<TextView>(R.id.btnCancelChannel).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showDriveVaultDialog() {
        val options = arrayOf(
            "Save Vault to Google Drive (sm-yt-gemini-vault.json)",
            "Restore Vault from Google Drive"
        )
        AlertDialog.Builder(this)
            .setTitle("Your Personal Google Drive Vault")
            .setItems(options) { _, which ->
                if (which == 0) exportDriveLauncher.launch("sm-yt-gemini-vault.json")
                else importDriveLauncher.launch(arrayOf("application/json", "*/*"))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
