package com.cesenahome.ui.artist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.cesenahome.domain.models.artist.Artist
import com.cesenahome.ui.databinding.ItemArtistBinding

class ArtistsAdapter(
    private val onArtistClick: (Artist) -> Unit
) : PagingDataAdapter<Artist, ArtistsAdapter.VH>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Artist>() {
            override fun areItemsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Artist, newItem: Artist): Boolean =
                oldItem == newItem
        }
    }

    inner class VH(val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val artist = getItem(position)
        artist?.let {
            holder.binding.artistName.text = it.name
            Glide.with(holder.binding.artistArtwork)
                .load(it.artworkUrl)
                .circleCrop()
                .into(holder.binding.artistArtwork)

            holder.itemView.setOnClickListener { _ ->
                onArtistClick(it)
            }
        }
    }
}
