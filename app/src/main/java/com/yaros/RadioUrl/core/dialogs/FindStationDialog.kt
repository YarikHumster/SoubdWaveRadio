package com.yaros.RadioUrl.core.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.inputmethod.InputMethodManager
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textview.MaterialTextView
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.ui.search.DirectInputCheck
import com.yaros.RadioUrl.ui.search.RadioBrowserResult
import com.yaros.RadioUrl.ui.search.RadioBrowserSearch
import com.yaros.RadioUrl.ui.search.SearchResultAdapter

class FindStationDialog (
    private val context: Context,
    private val listener: FindStationDialogListener
):
    SearchResultAdapter.SearchResultAdapterListener,
    RadioBrowserSearch.RadioBrowserSearchListener,
    DirectInputCheck.DirectInputCheckListener {

    interface FindStationDialogListener {
        fun onFindStationDialog(station: Station) {
        }
    }


    private lateinit var dialog: AlertDialog
    private lateinit var stationSearchBoxView: SearchView
    private lateinit var searchRequestProgressIndicator: ProgressBar
    private lateinit var noSearchResultsTextView: MaterialTextView
    private lateinit var stationSearchResultList: RecyclerView
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var radioBrowserSearch: RadioBrowserSearch
    private lateinit var directInputCheck: DirectInputCheck
    private var currentSearchString: String = String()
    private val handler: Handler = Handler(Looper.getMainLooper())
    private var station: Station = Station()


    override fun onSearchResultTapped(result: Station) {
        station = result
        val imm: InputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(stationSearchBoxView.windowToken, 0)
        activateAddButton()
    }


    @SuppressLint("NotifyDataSetChanged")
    override fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>) {
        if (results.isNotEmpty()) {
            val stationList: List<Station> = results.map {it.toStation()}
            searchResultAdapter.searchResults = stationList
            searchResultAdapter.notifyDataSetChanged()
            resetLayout(clearAdapter = false)
        } else {
            showNoResultsError()
        }
    }


    override fun onDirectInputCheck(stationList: MutableList<Station>) {
        if (stationList.isNotEmpty()) {
            val startPosition = searchResultAdapter.searchResults.size
            searchResultAdapter.searchResults = stationList
            searchResultAdapter.notifyItemRangeInserted(startPosition, stationList.size)
            resetLayout(clearAdapter = false)
        } else {
            showNoResultsError()
        }
    }


    fun show() {
        radioBrowserSearch = RadioBrowserSearch(this)
        directInputCheck = DirectInputCheck(this)

        val builder = MaterialAlertDialogBuilder(context)

        builder.setTitle(R.string.dialog_find_station_title)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_find_station, null)
        stationSearchBoxView = view.findViewById(R.id.station_search_box_view)
        searchRequestProgressIndicator = view.findViewById(R.id.search_request_progress_indicator)
        stationSearchResultList = view.findViewById(R.id.station_search_result_list)
        noSearchResultsTextView = view.findViewById(R.id.no_results_text_view)
        noSearchResultsTextView.isGone = true

        setupRecyclerView(context)

        builder.setPositiveButton(R.string.dialog_find_station_button_add) { _, _ ->
            listener.onFindStationDialog(station)
            searchResultAdapter.stopPrePlayback()
        }
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            radioBrowserSearch.stopSearchRequest()
            searchResultAdapter.stopPrePlayback()
        }
        builder.setOnCancelListener {
            radioBrowserSearch.stopSearchRequest()
            searchResultAdapter.stopPrePlayback()
        }

        stationSearchBoxView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                handleSearchBoxLiveInput(context, query)
                searchResultAdapter.stopPrePlayback()
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                handleSearchBoxInput(context, query)
                searchResultAdapter.stopPrePlayback()
                return true
            }
        })

        builder.setView(view)

        dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = true
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = true
    }

    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = SearchResultAdapter(this, listOf())
        stationSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object : LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        stationSearchResultList.layoutManager = layoutManager
        stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }


    private fun handleSearchBoxInput(context: Context, query: String) {
        when {
            query.isEmpty() -> {
                resetLayout(clearAdapter = true)
            }
            query.startsWith("http") -> {
                directInputCheck.checkStationAddress(context, query)
            }
            else -> {
                showProgressIndicator()
                radioBrowserSearch.searchStation(context, query, Keys.SEARCH_TYPE_BY_KEYWORD)
            }
        }
    }


    private fun handleSearchBoxLiveInput(context: Context, query: String) {
        currentSearchString = query
        if (query.startsWith("htt")) {
            directInputCheck.checkStationAddress(context, query)
        } else if (query.contains(" ") || query.length > 2) {
            showProgressIndicator()
            handler.postDelayed({
                if (currentSearchString == query) radioBrowserSearch.searchStation(
                    context,
                    query,
                    Keys.SEARCH_TYPE_BY_KEYWORD
                )
            }, 100)
        } else if (query.isEmpty()) {
            resetLayout(clearAdapter = true)
        }
    }


    override fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
    }

    override fun deactivateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
    }


    private fun resetLayout(clearAdapter: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isGone = true
        searchResultAdapter.resetSelection(clearAdapter)
    }


    private fun showNoResultsError() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isGone = true
        noSearchResultsTextView.isVisible = true
    }


    private fun showProgressIndicator() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        searchRequestProgressIndicator.isVisible = true
        noSearchResultsTextView.isGone = true
    }

}
