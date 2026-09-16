package com.smbmusic.player.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smbmusic.player.R
import com.smbmusic.player.media.AudioFormats
import com.smbmusic.player.model.RemoteEntry
import java.text.DateFormat
import java.util.Date

class FileAdapter(
    private val onClick: (RemoteEntry) -> Unit
) : RecyclerView.Adapter<FileAdapter.Holder>() {

    private val items = mutableListOf<RemoteEntry>()
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    fun submit(newItems: List<RemoteEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.row_remote_entry, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.type.text = if (item.isDirectory) "DIR" else AudioFormats.typeLabel(item.name)
        holder.name.text = item.name
        holder.date.text = if (item.modified > 0L) dateFormat.format(Date(item.modified)) else ""
        holder.itemView.setOnClickListener { onClick(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.typeLabel)
        val name: TextView = view.findViewById(R.id.nameText)
        val date: TextView = view.findViewById(R.id.dateText)
    }
}
