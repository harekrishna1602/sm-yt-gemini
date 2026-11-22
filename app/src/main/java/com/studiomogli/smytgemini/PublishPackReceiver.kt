package com.studiomogli.smytgemini

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat

class PublishPackReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "sm_yt_gemini_publish_pack"
        const val NOTIFICATION_ID = 3001

        const val ACTION_COPY_TITLE = "com.studiomogli.smytgemini.ACTION_COPY_TITLE"
        const val ACTION_COPY_DESC = "com.studiomogli.smytgemini.ACTION_COPY_DESC"
        const val ACTION_COPY_TAGS = "com.studiomogli.smytgemini.ACTION_COPY_TAGS"

        const val EXTRA_TEXT = "extra_copy_text"
        const val EXTRA_LABEL = "extra_copy_label"

        fun showPublishPackNotification(
            context: Context,
            title: String,
            description: String,
            tags: String
        ) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "YouTube Studio Publish Pack",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    this.description = "1-tap Copy Title, Description, and Tags while publishing in YouTube Studio"
                }
                manager.createNotificationChannel(channel)
            }

            fun buildCopyAction(action: String, label: String, text: String, reqCode: Int): PendingIntent {
                val intent = Intent(context, PublishPackReceiver::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_LABEL, label)
                    putExtra(EXTRA_TEXT, text)
                }
                return PendingIntent.getBroadcast(
                    context,
                    reqCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_publish)
                .setContentTitle("Ready to Publish in YT Studio")
                .setContentText(title.ifBlank { "Tap below to copy Title, Desc, or Tags" })
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(false)
                .setAutoCancel(false)

            if (title.isNotBlank()) {
                builder.addAction(0, "Copy Title", buildCopyAction(ACTION_COPY_TITLE, "Title", title, 11))
            }
            if (description.isNotBlank()) {
                builder.addAction(0, "Copy Desc", buildCopyAction(ACTION_COPY_DESC, "Description", description, 12))
            }
            if (tags.isNotBlank()) {
                builder.addAction(0, "Copy Tags", buildCopyAction(ACTION_COPY_TAGS, "Tags", tags, 13))
            }

            manager.notify(NOTIFICATION_ID, builder.build())
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT) ?: return
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Text"
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "$label copied! Paste in YT Studio.", Toast.LENGTH_SHORT).show()
    }
}
