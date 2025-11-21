package com.cesenahome.ui.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cesenahome.domain.models.song.Song
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ItemSongBinding
import java.util.concurrent.TimeUnit

class SongsAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onSongOptionsClick: (Song, View) -> Unit,
    private val onFavouriteClick: (Song) -> Unit,
) : PagingDataAdapter<Song, SongsAdapter.VH>(DIFF) {

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
        Glide.with(holder.binding.cover)
            .load(item.artworkUrl)
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .skipMemoryCache(true)
            .into(holder.binding.cover)

        holder.binding.downloadIndicator.isVisible = item.isDownloaded

        holder.itemView.setOnClickListener {
            onSongClick.invoke(item)
        }
        holder.binding.optionsButton.setOnClickListener { view ->
            onSongOptionsClick.invoke(item, view)
        }
        holder.binding.favouriteButton.apply {
            val isFavourite = item.isFavorite
            setImageResource(if (isFavourite) R.drawable.ic_favourite else R.drawable.ic_favourite_border)
            contentDescription = context.getString(
                if (isFavourite) {
                    R.string.song_favourite_button_content_description_selected
                } else {
                    R.string.song_favourite_button_content_description_unselected
                }
            )
            isSelected = isFavourite
            setOnClickListener { onFavouriteClick.invoke(item) }
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(ms)
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}