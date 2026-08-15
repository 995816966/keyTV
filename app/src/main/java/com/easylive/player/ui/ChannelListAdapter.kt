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

private val CURRENT_COLOR = Color.parseColor("#FFD54F") // 正在播放：琥珀黄
private val NORMAL_COLOR = Color.parseColor("#EEEEEE")

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
            val pos = bindingAdapterPosition
            val isCurrent = ch.id == currentId
            // 序号：与遥控数字选台一一对应（第 N 个频道）
            b.index.text = if (pos >= 0) (pos + 1).toString() else ""
            // 当前频道：前缀 ▶ + 高亮色（不覆盖根背景的焦点框）
            b.name.text = (if (isCurrent) "▶ " else "") + ch.name
            b.name.setTextColor(if (isCurrent) CURRENT_COLOR else NORMAL_COLOR)
            b.root.isSelected = isCurrent

            // 占位：首字圆块
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
