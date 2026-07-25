package com.denoy.karaoke.ui.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.denoy.karaoke.data.model.SongEntry

class SongAdapter(
    private var entries: List<SongEntry>,
    private val onClick: (SongEntry) -> Unit
) : RecyclerView.Adapter<SongAdapter.ViewHolder>() {

    fun updateEntries(newEntries: List<SongEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        holder.titleView.text = entry.title
        holder.subtitleView.text = "ID: ${entry.songId}"
        holder.itemView.setOnClickListener { onClick(entry) }
    }

    override fun getItemCount() = entries.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(android.R.id.text1)
        val subtitleView: TextView = view.findViewById(android.R.id.text2)
    }
}
