package com.yaros.RadioUrl.core.dialogs

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.ui.search.SearchResultAdapter

class AddStationDialog (
    private val context: Context,
    private val stationList: List<Station>,
    private val listener: AddStationDialogListener
) :
    SearchResultAdapter.SearchResultAdapterListener {

    interface AddStationDialogListener {
        fun onAddStationDialog(station: Station)
    }

    private val TAG = AddStationDialog::class.java.simpleName

    private lateinit var dialog: AlertDialog
    private lateinit var stationSearchResultList: RecyclerView
    private lateinit var searchResultAdapter: SearchResultAdapter
    private var station: Station = Station()

    override fun onSearchResultTapped(result: Station) {
        station = result
        activateAddButton()
    }

    fun show() {
        val builder = MaterialAlertDialogBuilder(context)

        builder.setTitle(R.string.dialog_add_station_title)

        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_add_station, null)
        stationSearchResultList = view.findViewById(R.id.station_list)

        setupRecyclerView(context)

        builder.setPositiveButton(R.string.dialog_find_station_button_add) { _, _ ->
            listener.onAddStationDialog(station)
            searchResultAdapter.stopPrePlayback()
        }
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            searchResultAdapter.stopPrePlayback()
        }
        builder.setOnCancelListener {
            searchResultAdapter.stopPrePlayback()
        }

        builder.setView(view)

        dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
    }

    private fun setupRecyclerView(context: Context) {
        searchResultAdapter = SearchResultAdapter(this, stationList)
        stationSearchResultList.adapter = searchResultAdapter
        val layoutManager: LinearLayoutManager = object: LinearLayoutManager(context) {
            override fun supportsPredictiveItemAnimations(): Boolean {
                return true
            }
        }
        stationSearchResultList.layoutManager = layoutManager
        stationSearchResultList.itemAnimator = DefaultItemAnimator()
    }

    override fun activateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
    }

    override fun deactivateAddButton() {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
    }

}