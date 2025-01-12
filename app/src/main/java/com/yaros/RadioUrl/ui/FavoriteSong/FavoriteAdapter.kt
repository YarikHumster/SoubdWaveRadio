package com.yaros.RadioUrl.ui.FavoriteSong

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.data.Song

class FavoriteAdapter(
    private val onItemClicked: (Song) -> Unit,
    private val onItemDeleteClicked: (Song) -> Unit // Renamed for clarity
) : ListAdapter<Song, FavoriteAdapter.FavoriteViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.song_item, parent, false)
        return FavoriteViewHolder(itemView, onItemClicked, onItemDeleteClicked)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FavoriteViewHolder(
        itemView: View,
        private val onClick: (Song) -> Unit,
        private val onDeleteClick: (Song) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val artistTextView: TextView = itemView.findViewById(R.id.artistTextView)
        private val radioStationTextView: TextView = itemView.findViewById(R.id.stationTextView)

        // Reference to the delete button
        private val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        private lateinit var currentSong: Song

        init {
            itemView.setOnClickListener {
                if (::currentSong.isInitialized) {
                    onClick(currentSong)
                }
            }
            // Set OnClickListener for the delete button
            deleteButton.setOnClickListener {
                if (::currentSong.isInitialized) {
                    onDeleteClick(currentSong)
                }
            }
        }

        fun bind(song: Song) {
            currentSong = song
            titleTextView.text = song.title
            artistTextView.text = song.artist
            radioStationTextView.text = song.radioStation
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem.title == newItem.title &&
                        oldItem.artist == newItem.artist &&

                        oldItem.radioStation == newItem.radioStation
            }

            override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean {
                return oldItem == newItem
            }
        }
    }
}
