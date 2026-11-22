package com.studiomogli.smytgemini

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.text.NumberFormat
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
    private lateinit var btnAiSetup: TextView

    // Phase 4: Native Channel Stats & Accountability Views
    private lateinit var cardChannelStats: LinearLayout
    private lateinit var txtChannelName: TextView
    private lateinit var txtChannelNumbers: TextView
    private lateinit var txtWatchCreateRatio: TextView
    private lateinit var txtAccountabilityAlert: TextView
    private lateinit var btnBingeFlip: Button

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
        btnAiSetup = findViewById(R.id.btnAiSetup)

        cardChannelStats = findViewById(R.id.cardChannelStats)
        txtChannelName = findViewById(R.id.txtChannelName)
        txtChannelNumbers = findViewById(R.id.txtChannelNumbers)
        txtWatchCreateRatio = findViewById(R.id.txtWatchCreateRatio)
        txtAccountabilityAlert = findViewById(R.id.txtAccountabilityAlert)
        btnBingeFlip = findViewById(R.id.btnBingeFlip)

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

        btnAiSetup.setOnClickListener {
            showAiSetupDialog()
        }

        cardChannelStats.setOnClickListener {
            showChannelConnectDialog()
        }

        findViewById<Button>(R.id.btnLogWatch15).setOnClickListener {
            store.addWatchMinsToday(15)
            refreshUi()
            Toast.makeText(this, "Logged +15m watch time", Toast.LENGTH_SHORT).show()
        }

        btnBingeFlip.setOnClickListener {
            showBingeToBuildDialog()
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

        val hasKey = store.getGeminiApiKey().isNotBlank()
        btnAiSetup.text = if (hasKey) "AI Ready" else "Connect AI"
        btnAiSetup.setTextColor(if (hasKey) 0xFF34C759.toInt() else 0xFFFF9F0A.toInt())

        // Channel Stats Card
        val ch = store.getChannelStats()
        if (ch.title.isNotBlank()) {
            val nf = NumberFormat.getIntegerInstance(Locale.US)
            txtChannelName.text = "${ch.title} (${ch.handle})"
            txtChannelNumbers.text = "${nf.format(ch.subscribers)} subs  •  ${nf.format(ch.totalViews)} views  •  ${ch.videoCount} videos"
        } else {
            txtChannelName.text = "Connect YouTube Channel"
            txtChannelNumbers.text = "Tap to sync live Subs & Views via YouTube Data API v3"
        }

        // Watch vs Create Accountability
        val watchMins = store.getWatchMinsToday()
        val createMins = store.getCreateMinsToday()
        txtWatchCreateRatio.text = "Watched: ${watchMins}m   •   Created: ${createMins}m"

        if (watchMins > createMins && watchMins >= 15) {
            txtAccountabilityAlert.text = "Reality Check: You watched ${watchMins}m today vs ${createMins}m creating. Flip it into a video!"
            txtAccountabilityAlert.setTextColor(0xFFFF453A.toInt())
        } else {
            txtAccountabilityAlert.text = "Creation ratio on track — keep shipping!"
            txtAccountabilityAlert.setTextColor(0xFF34C759.toInt())
        }

        val lastSync = store.getLastDriveSync()
        txtDriveStatus.text = if (lastSync > 0L) {
            val fmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(lastSync))
            "Private on device & Google Drive • Last backup: $fmt"
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

    private fun showChannelConnectDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_channel_connect, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtHandle = view.findViewById<EditText>(R.id.edtYtHandle)
        val edtYtKey = view.findViewById<EditText>(R.id.edtYtApiKey)
        val btnSync = view.findViewById<Button>(R.id.btnSyncChannel)

        edtHandle.setText(store.getYtHandle())
        edtYtKey.setText(store.getYtDataApiKey().ifBlank { store.getGeminiApiKey() })

        btnSync.setOnClickListener {
            val handle = edtHandle.text.toString().trim()
            val key = edtYtKey.text.toString().trim()
            if (handle.isEmpty() || key.isEmpty()) {
                Toast.makeText(this, "Enter both @handle and YouTube Data API key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            store.saveYtChannelConfig(handle, key)
            btnSync.isEnabled = false
            btnSync.text = "Syncing..."

            YouTubeDataClient.fetchChannelStats(handle, key) { result ->
                runOnUiThread {
                    btnSync.isEnabled = true
                    btnSync.text = "Sync Live Channel Stats"
                    result.fold(
                        onSuccess = { stats ->
                            store.saveChannelStats(stats)
                            refreshUi()
                            dialog.dismiss()
                            Toast.makeText(this, "Synced ${stats.title}!", Toast.LENGTH_SHORT).show()
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

    private fun showBingeToBuildDialog() {
        val apiKey = store.getGeminiApiKey()
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Tap 'Connect AI' first to add your Gemini API key.", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(this).apply {
            hint = "What topic/videos were you just watching?"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF888888.toInt())
            setPadding(36, 28, 36, 28)
        }
        AlertDialog.Builder(this)
            .setTitle("Binge-to-Build Flip (AI)")
            .setMessage("Turn what you were watching into your next video idea right now.")
            .setView(input)
            .setPositiveButton("Flip into Idea") { _, _ ->
                val topic = input.text.toString().trim().ifEmpty { "trending videos in my niche" }
                btnBingeFlip.isEnabled = false
                btnBingeFlip.text = "Flipping..."
                GeminiEngine.generateBingeToBuildFlip(
                    apiKey = apiKey,
                    creatorStyle = store.getCreatorStyle(),
                    watchMins = store.getWatchMinsToday(),
                    createMins = store.getCreateMinsToday(),
                    bingeTopic = topic
                ) { result ->
                    runOnUiThread {
                        btnBingeFlip.isEnabled = true
                        btnBingeFlip.text = "Binge-to-Build"
                        result.fold(
                            onSuccess = { flip ->
                                val newIdea = VideoIdea(
                                    id = "idea_${System.currentTimeMillis()}",
                                    angle = flip.angle,
                                    title = flip.title,
                                    thumbText = flip.thumbText,
                                    stage = VideoIdea.STAGE_IDEA
                                )
                                store.upsert(newIdea)
                                refreshUi()
                                AlertDialog.Builder(this)
                                    .setTitle("Reality Check")
                                    .setMessage("${flip.realityCheck}\n\nAdded to Workbench:\n\"${flip.title}\"")
                                    .setPositiveButton("Open Idea") { _, _ -> openProjectDetail(newIdea.id) }
                                    .setNegativeButton("Later", null)
                                    .show()
                            },
                            onFailure = { err ->
                                Toast.makeText(this, "AI Error: ${err.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAiSetupDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_ai_setup, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtKey = view.findViewById<EditText>(R.id.edtApiKey)
        val edtStyle = view.findViewById<EditText>(R.id.edtCreatorStyle)
        edtKey.setText(store.getGeminiApiKey())
        edtStyle.setText(store.getCreatorStyle())

        view.findViewById<TextView>(R.id.btnGetFreeKey).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
            } catch (_: Exception) {
                Toast.makeText(this, "Visit aistudio.google.com/apikey", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<Button>(R.id.btnSaveAiSetup).setOnClickListener {
            store.saveGeminiApiKey(edtKey.text.toString())
            store.saveCreatorStyle(edtStyle.text.toString())
            refreshUi()
            dialog.dismiss()
            Toast.makeText(this, "Gemini AI engine saved!", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<TextView>(R.id.btnCancelAiSetup).setOnClickListener { dialog.dismiss() }
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
