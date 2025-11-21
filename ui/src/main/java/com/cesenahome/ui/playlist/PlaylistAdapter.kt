package com.cesenahome.ui.playlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cesenahome.domain.models.playlist.Playlist
import com.cesenahome.ui.databinding.ItemPlaylistBinding
import java.util.concurrent.TimeUnit

class PlaylistAdapter(
    private val onPlaylistClick: (Playlist) -> Unit,
) : PagingDataAdapter<Playlist, PlaylistAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(oldItem: Playlist, newItem: Playlist): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Playlist, newItem: Playlist): Boolean = oldItem == newItem
        }
    }

    inner class VH(val binding: ItemPlaylistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        return VH(ItemPlaylistBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val playlist = getItem(position) ?: return
        with(holder.binding) {
            title.text = playlist.name
            subtitle.text = buildSubtitle(holder, playlist)
            Glide.with(cover)
                .load(playlist.artworkUrl)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(cover)
            downloadIndicator.isVisible = playlist.isDownloaded
        }
        holder.itemView.setOnClickListener { onPlaylistClick.invoke(playlist) }
    }

    private fun buildSubtitle(holder: VH, playlist: Playlist): String {
        val context = holder.binding.root.context
        val countText = playlist.songCount?.let { count ->
            context.resources.getQuantityString(
                com.cesenahome.ui.R.plurals.playlist_song_count,
                count,
                count
            )
        }
        val durationText = playlist.durationMs?.takeIf { it > 0 }?.let { formatDuration(it) }
        return when {
            countText != null && durationText != null ->
                context.getString(com.cesenahome.ui.R.string.playlist_subtitle_with_duration, countText, durationText)
            countText != null -> countText
            durationText != null -> durationText
            else -> context.getString(com.cesenahome.ui.R.string.unknown)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            val remainingSeconds = totalSeconds % 60
            String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format("%d:%02d", minutes, totalSeconds % 60)
        }
    }
}