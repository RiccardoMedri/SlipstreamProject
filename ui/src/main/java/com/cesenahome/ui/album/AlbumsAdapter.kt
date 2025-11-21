package com.cesenahome.ui.album

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.cesenahome.domain.models.album.Album
import com.cesenahome.ui.databinding.ItemAlbumBinding

class AlbumsAdapter(
    private val onAlbumClick: (Album) -> Unit
) : PagingDataAdapter<Album, AlbumsAdapter.VH>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(oldItem: Album, newItem: Album): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Album, newItem: Album): Boolean =
                oldItem == newItem
        }
    }

    inner class VH(val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val album = getItem(position)
        album?.let {
            holder.binding.albumTitle.text = it.title
            holder.binding.albumArtist.text = it.artist ?: "Unknown Artist"
            Glide.with(holder.binding.albumArtwork)
                .load(it.artworkUrl)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(holder.binding.albumArtwork)

            holder.binding.downloadIndicator.isVisible = it.isDownloaded

            holder.itemView.setOnClickListener { _ ->
                onAlbumClick(it)
            }
        }
    }
}
