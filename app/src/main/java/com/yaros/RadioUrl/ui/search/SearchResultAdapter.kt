package com.yaros.RadioUrl.ui.search

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textview.MaterialTextView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station

class SearchResultAdapter(
    private val listener: SearchResultAdapterListener,
    var searchResults: List<Station>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var selectedPosition: Int = RecyclerView.NO_POSITION
    private var exoPlayer: ExoPlayer? = null
    private var paused: Boolean = false
    private var isItemSelected: Boolean = false

    interface SearchResultAdapterListener {
        fun onSearchResultTapped(result: Station)
        fun activateAddButton()
        fun deactivateAddButton()
    }


    init {
        setHasStableIds(true)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.element_search_result, parent, false)
        return SearchResultViewHolder(v)
    }


    override fun getItemCount(): Int {
        return searchResults.size
    }


    override fun getItemId(position: Int): Long = position.toLong()


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val searchResultViewHolder: SearchResultViewHolder = holder as SearchResultViewHolder
        val searchResult: Station = searchResults[position]

        searchResultViewHolder.nameView.text = searchResult.name
        searchResultViewHolder.streamView.text = searchResult.getStreamUri()

        if (searchResult.codec.isNotEmpty()) {
            if (searchResult.bitrate == 0) {
                searchResultViewHolder.bitrateView.text = searchResult.codec
            } else {
                searchResultViewHolder.bitrateView.text = buildString {
                    append(searchResult.codec)
                    append(" | ")
                    append(searchResult.bitrate)
                    append("kbps")
                    append(" | ")
                    append(searchResult.country)
                    append(" | ")
                    append(searchResult.language)}
            }
        } else {
            searchResultViewHolder.bitrateView.visibility = View.GONE
        }

        val isSelected = selectedPosition == holder.adapterPosition
        searchResultViewHolder.searchResultLayout.isSelected = isSelected

        searchResultViewHolder.nameView.isSelected = isSelected
        searchResultViewHolder.streamView.isSelected = isSelected

        searchResultViewHolder.nameView.setFadingEdgeLength(10)
        searchResultViewHolder.streamView.setFadingEdgeLength(10)

        searchResultViewHolder.searchResultLayout.setOnClickListener {
            val previousSelectedPosition = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(previousSelectedPosition)
            notifyItemChanged(selectedPosition)

            val samePositionSelected = previousSelectedPosition == selectedPosition

            if (samePositionSelected) {
                resetSelection(false)
            } else {
                val selectedStation = searchResults[holder.adapterPosition]
                performPrePlayback(searchResultViewHolder.searchResultLayout.context, selectedStation.getStreamUri())
                listener.onSearchResultTapped(searchResult)
            }

            isItemSelected = !samePositionSelected

            if (isItemSelected) {
                listener.activateAddButton()
            } else {
                listener.deactivateAddButton()
            }
        }
    }


    private fun performPrePlayback(context: Context, streamUri: String) {
        if (streamUri.contains(".m3u8")) {
            stopPrePlayback()

            Toast.makeText(context, R.string.toastmessage_preview_playback_failed, Toast.LENGTH_SHORT).show()
        } else {
            stopRadioPlayback(context)

            stopPrePlayback()

            exoPlayer = ExoPlayer.Builder(context).build()

            val mediaItem = MediaItem.fromUri(streamUri)

            exoPlayer?.setMediaItem(mediaItem)

            exoPlayer?.prepare()
            exoPlayer?.play()

            Toast.makeText(context, R.string.toastmessage_preview_playback_started, Toast.LENGTH_SHORT).show()

            val lifecycle = (context as AppCompatActivity).lifecycle
            val lifecycleObserver = object : DefaultLifecycleObserver {
                override fun onPause(owner: LifecycleOwner) {
                    if (!paused) {
                        paused = true
                        stopPrePlayback()
                    }
                }
            }
            lifecycle.addObserver(lifecycleObserver)
        }
    }


    fun stopPrePlayback() {
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }


    private fun stopRadioPlayback(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(audioAttributes)
                .build()

            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }


    fun resetSelection(clearAdapter: Boolean) {
        val currentlySelected: Int = selectedPosition
        selectedPosition = RecyclerView.NO_POSITION
        if (clearAdapter) {
            val previousItemCount = itemCount
            searchResults = emptyList()
            notifyItemRangeRemoved(0, previousItemCount)
        } else {
            notifyItemChanged(currentlySelected)
            stopPrePlayback()
        }
    }

    private inner class SearchResultViewHolder(var searchResultLayout: View) :
        RecyclerView.ViewHolder(searchResultLayout) {
        val nameView: MaterialTextView = searchResultLayout.findViewById(R.id.station_name)
        val streamView: MaterialTextView = searchResultLayout.findViewById(R.id.station_url)
        val bitrateView: MaterialTextView = searchResultLayout.findViewById(R.id.station_bitrate)
    }

}
