package com.easylive.player.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.easylive.player.data.Channel
import com.easylive.player.databinding.ItemChannelBinding
import com.easylive.player.player.LogoLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 频道列表适配器。大字号、大行高，适老化。
 */
class ChannelListAdapter(
    private val onItemClick: (Channel) -> Unit
) : ListAdapter<Channel, ChannelListAdapter.VH>(DIFF) {

    private var currentId: Long = -1L

    fun setCurrent(id: Long) {
        if (currentId != id) {
            val old = currentId
            currentId = id
            // 简单全量刷新高亮，列表不大，性能可接受
            notifyDataSetChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemChannelBinding) : RecyclerView.ViewHolder(b.root) {
        init {
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick(getItem(pos))
            }
        }

        fun bind(ch: Channel) {
            b.name.text = ch.name
            val isCurrent = ch.id == currentId
            b.root.isSelected = isCurrent
            b.root.setBackgroundColor(if (isCurrent) Color.parseColor("#335577FF") else Color.TRANSPARENT)

            // 占位：首字圆形
            b.logo.setImageDrawable(null)
            b.logoPlaceholder.text = ch.name.firstOrNull()?.toString() ?: "?"
            if (!ch.logo.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.Main).launch {
                    LogoLoader.load(b.logo, ch.logo)
                }
                b.logoPlaceholder.text = ""
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }
    }
}
