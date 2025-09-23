package com.cesenahome.ui.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cesenahome.domain.models.Song
import com.cesenahome.ui.R
import com.cesenahome.ui.databinding.ItemSongBinding
// import com.bumptech.glide.Glide  // uncomment if you add artwork

class SongsAdapter(
    private val dataset: MutableList<Song> = mutableListOf()
) : RecyclerView.Adapter<SongsAdapter.SongViewHolder>() {

    fun updateList(newList: List<Song>) {
        dataset.clear()
        dataset.addAll(newList)
        notifyDataSetChanged()
    }

    abstract class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        abstract fun bind(song: Song)
    }

    class ViewHolderSong(
        private val binding: ItemSongBinding
    ) : SongViewHolder(binding.root) {

        override fun bind(song: Song) {
            binding.title.text = song.title
            binding.subtitle.text = listOfNotNull(song.artist, song.album)
                .joinToString(" — ")
                .ifBlank { binding.root.context.getString(R.string.unknown) }
            binding.trailing.text = song.durationMs?.let { formatDuration(it) } ?: ""
        }

        private fun formatDuration(ms: Long): String {
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolderSong(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(dataset[position])
    }

    override fun getItemCount(): Int = dataset.size
}