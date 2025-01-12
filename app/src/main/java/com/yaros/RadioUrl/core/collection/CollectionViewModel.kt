package com.yaros.RadioUrl.core.collection

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.helpers.FileHelper
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG: String = CollectionViewModel::class.java.simpleName

    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    val collectionSizeLiveData: MutableLiveData<Int> = MutableLiveData<Int>()
    private var modificationDateViewModel: Date = Date()
    private var collectionChangedReceiver: BroadcastReceiver


    init {
        loadCollection()
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(
            collectionChangedReceiver,
            IntentFilter(Keys.ACTION_COLLECTION_CHANGED)
        )
    }


    override fun onCleared() {
        super.onCleared()
        LocalBroadcastManager.getInstance(getApplication())
            .unregisterReceiver(collectionChangedReceiver)
    }


    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE)) {
                    val date =
                        Date(intent.getLongExtra(Keys.EXTRA_COLLECTION_MODIFICATION_DATE, 0L))
                    if (date.after(modificationDateViewModel)) {
                        Timber.tag(TAG)
                            .v("CollectionViewModel - reload collection after broadcast received.")
                        loadCollection()
                    }
                }
            }
        }
    }

    private fun loadCollection() {
        Timber.tag(TAG).v("Loading collection of stations from storage")
        viewModelScope.launch {
            val collection: Collection = FileHelper.readCollectionSuspended(getApplication())
            modificationDateViewModel = collection.modificationDate
            collectionLiveData.value = collection
            collectionSizeLiveData.value = collection.stations.size
        }
    }

}
