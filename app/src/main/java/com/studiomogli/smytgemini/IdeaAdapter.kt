package com.studiomogli.smytgemini

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class IdeaAdapter(
    private var items: List<VideoIdea>,
    private val onClick: (VideoIdea) -> Unit
) : RecyclerView.Adapter<IdeaAdapter.IdeaViewHolder>() {

    class IdeaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgThumb: ImageView = view.findViewById(R.id.imgCardThumb)
        val txtStage: TextView = view.findViewById(R.id.txtCardStage)
        val txtAngle: TextView = view.findViewById(R.id.txtCardAngle)
        val txtTitle: TextView = view.findViewById(R.id.txtCardTitle)
        val txtMeta: TextView = view.findViewById(R.id.txtCardMeta)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IdeaViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_idea_card, parent, false)
        return IdeaViewHolder(v)
    }

    override fun onBindViewHolder(holder: IdeaViewHolder, position: Int) {
        val idea = items[position]
        holder.txtStage.text = idea.stage
        val badgeBg = when (idea.stage) {
            VideoIdea.STAGE_PUBLISHED -> R.drawable.bg_badge_green
            VideoIdea.STAGE_READY -> R.drawable.bg_badge_blue
            else -> R.drawable.bg_badge_red
        }
        holder.txtStage.setBackgroundResource(badgeBg)

        if (idea.thumbnailPath.isNotBlank() && File(idea.thumbnailPath).exists()) {
            holder.imgThumb.setImageBitmap(BitmapFactory.decodeFile(idea.thumbnailPath))
            holder.imgThumb.visibility = View.VISIBLE
        } else {
            holder.imgThumb.visibility = View.GONE
        }

        holder.txtAngle.text = idea.angle.ifBlank { "Untitled Video Idea" }
        holder.txtTitle.text = if (idea.title.isNotBlank()) {
            "Title: ${idea.title}"
        } else {
            "Tap to create with Mobile Gemini"
        }

        val wordCount = if (idea.script.isBlank()) 0 else idea.script.trim().split(Regex("\\s+")).size
        val hasMeta = if (idea.tags.isNotBlank()) " • SEO Ready" else ""
        holder.txtMeta.text = "$wordCount words in script$hasMeta"

        holder.itemView.setOnClickListener { onClick(idea) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<VideoIdea>) {
        items = newItems
        notifyDataSetChanged()
    }
}
