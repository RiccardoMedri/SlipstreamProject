package com.cesenahome.ui.songs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.paging.PagingDataAdapter
import com.cesenahome.domain.models.Song
import com.cesenahome.ui.databinding.ItemSongBinding
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

class SongsAdapter :
    PagingDataAdapter<Song, SongsAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(o: Song, n: Song) = o.id == n.id
            override fun areContentsTheSame(o: Song, n: Song) = o == n
        }
    }

    inner class VH(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemSongBinding.inflate(inf, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position) ?: return
        holder.binding.title.text = item.title
        holder.binding.subtitle.text = listOfNotNull(item.artist, item.album).joinToString(" — ").ifBlank { "Unknown" }
        holder.binding.trailing.text = item.durationMs?.let { formatDuration(it) } ?: ""
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}