package com.example.docscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScanAdapter(
    private val items: MutableList<ScanItem>,
    private val onClick: (ScanItem) -> Unit
) : RecyclerView.Adapter<ScanAdapter.ScanViewHolder>() {

    private var onLongClick: ((ScanItem, Int) -> Unit)? = null

    fun setOnLongClickListener(listener: (ScanItem, Int) -> Unit) {
        onLongClick = listener
    }

    fun removeAt(position: Int) {
        items.removeAt(position)
        notifyItemRemoved(position)
    }

    class ScanViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val fileName: TextView = view.findViewById(R.id.fileName)
        val fileItem: TextView = view.findViewById(R.id.fileInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan, parent, false)
        return ScanViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
        val item = items[position]
        holder.fileName.text = item.fileName
        holder.fileItem.text = item.fileInfo
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick?.invoke(item, holder.adapterPosition)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
