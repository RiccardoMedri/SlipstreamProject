package com.cesenahome.ui.player

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.cesenahome.domain.models.song.QueueSong
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ItemQueueSongBinding
import java.util.Collections

class QueueAdapter(
    songs: List<QueueSong>,
    currentPlayingId: String?,
) : RecyclerView.Adapter<QueueAdapter.VH>() {

    private val items = songs.toMutableList()
    private var dragStarter: ((RecyclerView.ViewHolder) -> Unit)? = null
    private var currentId: String? = currentPlayingId

    fun setDragStarter(starter: (RecyclerView.ViewHolder) -> Unit) {
        dragStarter = starter
    }

    fun updateItems(newItems: List<QueueSong>, currentPlayingId: String?) {
        items.clear()
        items.addAll(newItems)
        currentId = currentPlayingId
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        if (fromPosition < 0 || fromPosition >= items.size || toPosition < 0 || toPosition >= items.size) return
        if (fromPosition < toPosition) {
            for (index in fromPosition until toPosition) {
                Collections.swap(items, index, index + 1)
            }
        } else {
            for (index in fromPosition downTo toPosition + 1) {
                Collections.swap(items, index, index - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemQueueSongBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.bind(item, item.id == currentId)
    }

    override fun onViewRecycled(holder: VH) {
        holder.cancelPendingDrag()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("ClickableViewAccessibility")
    inner class VH(val binding: ItemQueueSongBinding) : RecyclerView.ViewHolder(binding.root) {
        private val longPressRunnable = Runnable {
            dragStarter?.invoke(this)
        }

        init {
            binding.root.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> binding.root.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.root.removeCallbacks(longPressRunnable)
                }
                false
            }
        }

        fun bind(song: QueueSong, isCurrent: Boolean) {
            val context = binding.root.context
            binding.title.text = song.title.ifBlank { context.getString(R.string.unknown) }
            val parts = listOfNotNull(song.artist?.takeIf { it.isNotBlank() }, song.album?.takeIf { it.isNotBlank() })
            binding.subtitle.text = if (parts.isNotEmpty()) {
                parts.joinToString(" — ")
            } else {
                context.getString(R.string.unknown)
            }
            binding.nowPlayingIndicator.isVisible = isCurrent
        }

        fun cancelPendingDrag() {
            binding.root.removeCallbacks(longPressRunnable)
        }
    }

    companion object {
        private const val LONG_PRESS_TIMEOUT_MS = 1000L
    }
}