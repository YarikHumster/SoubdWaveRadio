package com.yaros.RadioUrl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.data.Song
import com.yaros.RadioUrl.helpers.DateTimeHelper
import com.yaros.RadioUrl.helpers.ImageHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import com.yaros.RadioUrl.helpers.UiHelper
import com.yaros.RadioUrl.ui.FavoriteSong.SongViewModel
import timber.log.Timber

data class LayoutHolder(var rootView: View) {

    var recyclerView: RecyclerView = rootView.findViewById(R.id.station_list)
    val layoutManager: LinearLayoutManager
    private var bottomSheet: ConstraintLayout = rootView.findViewById(R.id.bottom_sheet)
    private var sleepTimerRunningViews: Group = rootView.findViewById(R.id.sleep_timer_running_views)
    private var downloadProgressIndicator: ProgressBar = rootView.findViewById(R.id.download_progress_indicator)
    private var stationImageView: ImageView = rootView.findViewById(R.id.station_icon)
    private var stationNameView: TextView = rootView.findViewById(R.id.player_station_name)
    private var metadataView: TextView = rootView.findViewById(R.id.player_station_metadata)
    var playButtonView: ImageButton = rootView.findViewById(R.id.player_play_button)
    private var bufferingIndicator: ProgressBar = rootView.findViewById(R.id.player_buffering_indicator)
    private var sheetStreamingLinkHeadline: TextView = rootView.findViewById(R.id.sheet_streaming_link_headline)
    private var sheetStreamingLinkView: TextView = rootView.findViewById(R.id.sheet_streaming_link)
    private var sheetMetadataHistoryHeadline: TextView = rootView.findViewById(R.id.sheet_metadata_headline)
    private var sheetMetadataHistoryView: TextView = rootView.findViewById(R.id.sheet_metadata_history)
    private var sheetNextMetadataView: ImageButton = rootView.findViewById(R.id.sheet_next_metadata_button)
    private var sheetPreviousMetadataView: ImageButton = rootView.findViewById(R.id.sheet_previous_metadata_button)
    private var sheetCopyMetadataButtonView: ImageButton = rootView.findViewById(R.id.copy_station_metadata_button)
    private var sheetShareLinkButtonView: ImageView = rootView.findViewById(R.id.sheet_share_link_button)
    private var sheetBitrateView: TextView = rootView.findViewById(R.id.sheet_bitrate_view)
    private var favoriteButton: ImageButton = rootView.findViewById(R.id.favorite_button)
    var sheetSleepTimerStartButtonView: ImageButton = rootView.findViewById(R.id.sleep_timer_start_button)
    var sheetSleepTimerCancelButtonView: ImageButton = rootView.findViewById(R.id.sleep_timer_cancel_button)
    private var sheetSleepTimerRemainingTimeView: TextView = rootView.findViewById(R.id.sleep_timer_remaining_time)
    private var onboardingLayout: ConstraintLayout = rootView.findViewById(R.id.onboarding_layout)
    private var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout> = BottomSheetBehavior.from(bottomSheet)
    private var metadataHistory: MutableList<String>
    private var metadataHistoryPosition: Int
    private var isBuffering: Boolean
    private val songViewModel: SongViewModel by lazy {
        ViewModelProvider(rootView.context as ViewModelStoreOwner).get(SongViewModel::class.java)
    }

    init {
        metadataHistory = PreferencesHelper.loadMetadataHistory()
        metadataHistoryPosition = metadataHistory.size - 1
        isBuffering = false

        layoutManager = CustomLayoutManager(rootView.context)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()

        sheetPreviousMetadataView.setOnClickListener {
            if (metadataHistory.isNotEmpty()) {
                if (metadataHistoryPosition > 0) {
                    metadataHistoryPosition -= 1
                } else {
                    metadataHistoryPosition = metadataHistory.size - 1
                }
                sheetMetadataHistoryView.text = metadataHistory[metadataHistoryPosition]
            }
        }
        sheetNextMetadataView.setOnClickListener {
            if (metadataHistory.isNotEmpty()) {
                if (metadataHistoryPosition < metadataHistory.size - 1) {
                    metadataHistoryPosition += 1
                } else {
                    metadataHistoryPosition = 0
                }
                sheetMetadataHistoryView.text = metadataHistory[metadataHistoryPosition]
            }
        }
        sheetMetadataHistoryView.setOnLongClickListener {
            copyMetadataHistoryToClipboard()
            return@setOnLongClickListener true
        }
        sheetMetadataHistoryHeadline.setOnLongClickListener {
            copyMetadataHistoryToClipboard()
            return@setOnLongClickListener true
        }

        setupBottomSheet()
    }

    fun updatePlayerViews(context: Context, station: Station, isPlaying: Boolean) {

        if (!isPlaying) {
            metadataView.text = station.name
            sheetMetadataHistoryView.text = station.name
        }

        stationNameView.text = station.name

        stationNameView.isSelected = isPlaying

        stationNameView.setFadingEdgeLength(8)

        if (station.imageColor != -1) {
            stationImageView.setBackgroundColor(station.imageColor)
        }
        stationImageView.setImageBitmap(ImageHelper.getStationImage(context, station.smallImage))
        stationImageView.contentDescription = "${context.getString(R.string.descr_player_station_image)}: ${station.name}"

        sheetStreamingLinkView.text = station.getStreamUri()

        val bitrateText: CharSequence = if (station.codec.isNotEmpty()) {
            if (station.bitrate == 0) {
                station.codec
                station.language
                station.country
            } else {
                buildString {
                    append(station.codec)
                    append(" | ")
                    append(station.bitrate)
                    append("kbps")
                    append(" | ")
                    append(station.language)
                    append(" | ")
                    append(station.country)
                }
            }
        } else {
            ""
        }

        sheetBitrateView.text = bitrateText

        sheetStreamingLinkHeadline.setOnClickListener {
            copyToClipboard(
                context,
                sheetStreamingLinkView.text
            )
        }
        sheetStreamingLinkView.setOnClickListener {
            copyToClipboard(
                context,
                sheetStreamingLinkView.text
            )
        }
        sheetMetadataHistoryHeadline.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetMetadataHistoryView.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetCopyMetadataButtonView.setOnClickListener {
            copyToClipboard(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetBitrateView.setOnClickListener {
            copyToClipboard(
                context,
                sheetBitrateView.text
            )
        }
        favoriteButton.setOnClickListener{
            onFavoriteButtonClick(
                context,
                sheetMetadataHistoryView.text
            )
        }
        sheetShareLinkButtonView.setOnClickListener {
            val share = Intent.createChooser(Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TITLE, stationNameView.text)
                putExtra(Intent.EXTRA_TEXT, sheetStreamingLinkView.text)
                type = "text/plain"
            }, null)
            context.startActivity(share)
        }
            val logMessage = buildString {
                append("UUID: ${station.uuid}\n")
                append("Starred: ${station.starred}\n")
                append("Name: ${station.name}\n")
                append("Name Manually Set: ${station.nameManuallySet}\n")
                append("Stream URIs: ${station.streamUris.joinToString(", ")}\n")
                append("Current Stream: ${station.getStreamUri()}\n")
                append("Stream Content: ${station.streamContent}\n")
                append("Homepage: ${station.homepage}\n")
                append("Image: ${station.image}\n")
                append("Small Image: ${station.smallImage}\n")
                append("Image Color: ${station.imageColor}\n")
                append("Image Manually Set: ${station.imageManuallySet}\n")
                append("Remote Image Location: ${station.remoteImageLocation}\n")
                append("Remote Station Location: ${station.remoteStationLocation}\n")
                append("Modification Date: ${station.modificationDate}\n")
                append("Is Playing: ${station.isPlaying}\n")
                append("Radio Browser Station UUID: ${station.radioBrowserStationUuid}\n")
                append("Radio Browser Change UUID: ${station.radioBrowserChangeUuid}\n")
                append("Bitrate: ${station.bitrate}\n")
                append("Codec: ${station.codec}\n")
                append("Country Code: ${station.countrycode}\n")
                append("Country: ${station.country}\n")
                append("Language: ${station.language}\n")
                append("Language Codes: ${station.languagecodes}\n")
            }
        Timber.tag("StationDetails").d(logMessage)
    }

    private fun onFavoriteButtonClick(context: Context, text: CharSequence) {
        // Логирование текста для проверки
        Timber.tag("onFavoriteButtonClick").d("Received text: $text")

        // Разбор строки по шаблону "Artist - Title"
        val parts = text.split(" - ")
        if (parts.size == 2) {
            val artist = parts[0].trim()
            val title = parts[1].trim()
            val radioStation = "" // Дополнительную информацию можно оставить пустой или задать дефолтным значением

            if (artist.isNotEmpty() && title.isNotEmpty()) {
                val song = Song(
                    title = title,
                    artist = artist,
                    radioStation = radioStation
                )
                songViewModel.addFavoriteSong(song)
                Toast.makeText(context, R.string.toastaddsong, Toast.LENGTH_SHORT).show()
            } else {
                // Логирование ошибки для отладки
                Timber.tag("onFavoriteButtonClick").e("Empty artist or title: $text")
                Toast.makeText(context, R.string.toastfailedsong, Toast.LENGTH_SHORT).show()
            }
        } else {
            // Логирование ошибки для отладки
            Timber.tag("onFavoriteButtonClick").e("Incorrect data format: $text")
            Toast.makeText(context, R.string.toastfailedsong, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(context: Context, clipString: CharSequence) {
        val clip: ClipData = ClipData.newPlainText("simple text", clipString)
        val cm: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(clip)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Snackbar.make(rootView, R.string.toastmessage_copied_to_clipboard, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun copyMetadataHistoryToClipboard() {
        val metadataHistory: MutableList<String> = PreferencesHelper.loadMetadataHistory()
        val stringBuilder: StringBuilder = StringBuilder()
        metadataHistory.forEach { stringBuilder.append("${it.trim()}\n") }
        copyToClipboard(rootView.context, stringBuilder.toString())
    }

    fun updateMetadata(metadataHistoryList: MutableList<String>?) {
        if (!metadataHistoryList.isNullOrEmpty()) {
            metadataHistory = metadataHistoryList
            if (metadataHistory.last() != metadataView.text) {
                metadataHistoryPosition = metadataHistory.size - 1
                val metadataString = metadataHistory[metadataHistoryPosition]
                metadataView.text = metadataString
                sheetMetadataHistoryView.text = metadataString
            }
        }
    }

    fun updateSleepTimer(context: Context, timeRemaining: Long = 0L) {
        when (timeRemaining) {
            0L -> {
                sleepTimerRunningViews.isGone = true
            }
            else -> {
                sleepTimerRunningViews.isVisible = true
                val sleepTimerTimeRemaining = DateTimeHelper.convertToHoursMinutesSeconds(timeRemaining)
                sheetSleepTimerRemainingTimeView.text = sleepTimerTimeRemaining
                sheetSleepTimerRemainingTimeView.contentDescription = "${context.getString(R.string.descr_expanded_player_sleep_timer_remaining_time)}: $sleepTimerTimeRemaining"
                stationNameView.isSelected = false
            }
        }
    }

    fun togglePlayButton(isPlaying: Boolean) {
        if (isPlaying) {
            playButtonView.setImageResource(R.drawable.ic_audio_waves_animated)
            val animatedVectorDrawable = playButtonView.drawable as? AnimatedVectorDrawable
            animatedVectorDrawable?.start()
            sheetSleepTimerStartButtonView.isVisible = true
            favoriteButton.isVisible = true
        } else {
            playButtonView.setImageResource(R.drawable.ic_player_play_symbol_42dp)
            sheetSleepTimerStartButtonView.isVisible = false
            favoriteButton.isVisible = false
        }
    }

    fun showBufferingIndicator(buffering: Boolean) {
        bufferingIndicator.isVisible = buffering
        isBuffering = buffering
    }

//    fun togglePlayerVisibility(context: Context, playbackState: Int): Boolean {
//        when (playbackState) {
//            PlaybackStateCompat.STATE_STOPPED -> return hidePlayer(context)
//            PlaybackStateCompat.STATE_NONE -> return hidePlayer(context)
//            PlaybackStateCompat.STATE_ERROR -> return hidePlayer(context)
//            else -> return showPlayer(context)
//        }
//    }

    fun toggleDownloadProgressIndicator() {
        when (PreferencesHelper.loadActiveDownloads()) {
            Keys.ACTIVE_DOWNLOADS_EMPTY -> downloadProgressIndicator.isGone = true
            else -> downloadProgressIndicator.isVisible = true
        }
    }

    fun toggleOnboarding(context: Context, collectionSize: Int): Boolean {
        return if (collectionSize == 0 && PreferencesHelper.loadCollectionSize() <= 0) {
            onboardingLayout.isVisible = true
            hidePlayer(context)
            true
        } else {
            onboardingLayout.isGone = true
            showPlayer(context)
            false
        }
    }

    fun animatePlaybackButtonStateTransition(context: Context, isPlaying: Boolean) {
        when (isPlaying) {
            true -> {
                val rotateClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_clockwise_slow)
                rotateClockwise.setAnimationListener(createAnimationListener(true))
                playButtonView.startAnimation(rotateClockwise)
            }
            false -> {
                val rotateCounterClockwise = AnimationUtils.loadAnimation(context, R.anim.rotate_counterclockwise_fast)
                rotateCounterClockwise.setAnimationListener(createAnimationListener(false))
                playButtonView.startAnimation(rotateCounterClockwise)
            }

        }
    }

    fun showPlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0, 0, 0, Keys.BOTTOM_SHEET_PEEK_HEIGHT)
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN && onboardingLayout.visibility == View.GONE) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        return true
    }

    private fun hidePlayer(context: Context): Boolean {
        UiHelper.setViewMargins(context, recyclerView, 0, 0, 0, 0)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        return true
    }

    fun minimizePlayerIfExpanded(): Boolean {
        return if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            true
        } else {
            false
        }
    }

    private fun createAnimationListener(isPlaying: Boolean): Animation.AnimationListener {
        return object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                togglePlayButton(isPlaying)
            }

            override fun onAnimationRepeat(animation: Animation) {}
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(view: View, slideOffset: Float) {
            }

            override fun onStateChanged(view: View, state: Int) {
                when (state) {
                    BottomSheetBehavior.STATE_COLLAPSED -> Unit
                    BottomSheetBehavior.STATE_DRAGGING -> Unit
                    BottomSheetBehavior.STATE_EXPANDED -> Unit
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> Unit
                    BottomSheetBehavior.STATE_SETTLING -> Unit
                    BottomSheetBehavior.STATE_HIDDEN -> showPlayer(rootView.context)
                }
            }
        })
        bottomSheet.setOnClickListener { toggleBottomSheetState() }
        stationImageView.setOnClickListener { toggleBottomSheetState() }
        stationNameView.setOnClickListener { toggleBottomSheetState() }
        metadataView.setOnClickListener { toggleBottomSheetState() }
    }

    private fun toggleBottomSheetState() {
        when (bottomSheetBehavior.state) {
            BottomSheetBehavior.STATE_COLLAPSED -> bottomSheetBehavior.state =
                BottomSheetBehavior.STATE_EXPANDED
            else -> bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    private inner class CustomLayoutManager(context: Context) :
        LinearLayoutManager(context, VERTICAL, false) {
        override fun supportsPredictiveItemAnimations(): Boolean {
            return true
        }
    }
}