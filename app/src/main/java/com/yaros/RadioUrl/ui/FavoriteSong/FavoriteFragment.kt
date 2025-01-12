package com.yaros.RadioUrl.ui.FavoriteSong

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.data.Song

class FavoriteFragment : Fragment() {

    private val songViewModel: SongViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorite, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerViewFavoriteSongs)
        val adapter = FavoriteAdapter(
            onItemClicked = { song -> copyToClipboard(song) },
            onItemDeleteClicked = { song -> songViewModel.removeFavoriteSong(song) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Подписка на изменения избранных песен
        songViewModel.favoriteSongs.observe(viewLifecycleOwner) { favoriteSongs ->
            adapter.submitList(favoriteSongs)
        }

        return view
    }

    private fun copyToClipboard(song: Song) {
        val clipboard = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Favorite Song", "${song.title} - ${song.artist}")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.toastmessage_copied_to_clipboard, Toast.LENGTH_SHORT)
            .show()
    }
}
