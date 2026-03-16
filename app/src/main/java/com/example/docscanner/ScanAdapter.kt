package com.example.docscanner;

import android.view.View;
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater
import android.view.ViewGroup
class  ScanAdapter(private val items:List<ScanItem>) :
    RecyclerView.Adapter<ScanAdapter.ScanViewHolder>(){

    class ScanViewHolder(view:View) : RecyclerView.ViewHolder(view) {
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
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
