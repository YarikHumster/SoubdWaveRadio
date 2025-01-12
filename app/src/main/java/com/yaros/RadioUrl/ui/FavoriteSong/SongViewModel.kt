package com.yaros.RadioUrl.ui.FavoriteSong

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yaros.RadioUrl.data.Song
import java.lang.reflect.Type

class SongViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences =
        application.getSharedPreferences("favorite_songs_prefs", Context.MODE_PRIVATE)
    private val songsLiveData = MutableLiveData<List<Song>>()

    init {
        loadFavoriteSongs()
    }

    val favoriteSongs: LiveData<List<Song>> get() = songsLiveData

    fun addFavoriteSong(song: Song) {
        val updatedList = songsLiveData.value.orEmpty().toMutableList().apply { add(song) }
        songsLiveData.value = updatedList
        saveFavoriteSongs(updatedList)
    }

    fun removeFavoriteSong(song: Song) {
        val updatedList = songsLiveData.value.orEmpty().toMutableList().apply { remove(song) }
        songsLiveData.value = updatedList
        saveFavoriteSongs(updatedList)
    }

    private fun loadFavoriteSongs() {
        val json = sharedPreferences.getString("favorite_songs", "[]")
        val type: Type = object : TypeToken<List<Song>>() {}.type
        val songs: List<Song> = Gson().fromJson(json, type)
        songsLiveData.value = songs
    }

    private fun saveFavoriteSongs(songs: List<Song>) {
        val json = Gson().toJson(songs)
        sharedPreferences.edit().putString("favorite_songs", json).apply()
    }
}