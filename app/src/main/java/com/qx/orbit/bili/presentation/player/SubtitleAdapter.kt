package com.qx.orbit.bili.presentation.player

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import androidx.core.graphics.toColorInt

class SubtitleAdapter(
    private val subtitleNames: List<String>,
    private var selectedIndex: Int = -1,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    fun interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    fun setSelectedIndex(index: Int) {
        val old = selectedIndex
        selectedIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val button = Button(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(button)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.button.text = subtitleNames[position]
        holder.button.setTextColor(
            if (position == selectedIndex) "#ffff6699".toColorInt()
            else Color.WHITE
        )
        holder.button.setOnClickListener { onItemClickListener.onItemClick(position) }
    }

    override fun getItemCount(): Int = subtitleNames.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val button: Button = view as Button
    }
}
