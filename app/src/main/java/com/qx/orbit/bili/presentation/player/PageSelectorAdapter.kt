package com.qx.orbit.bili.presentation.player

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

class PageSelectorAdapter(
    private val pageNames: List<String>,
    private var selectedIndex: Int = 0,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<PageSelectorAdapter.ViewHolder>() {

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setSelectedIndex(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        notifyItemChanged(old)
        notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val label = "P${position + 1} ${pageNames[position]}"
        holder.textView.text = label
        holder.textView.setTextColor(
            if (position == selectedIndex) "#ffff6699".toColorInt()
            else Color.WHITE
        )
        holder.itemView.setOnClickListener { onItemClickListener.onItemClick(position) }
    }

    override fun getItemCount(): Int = pageNames.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }
}
