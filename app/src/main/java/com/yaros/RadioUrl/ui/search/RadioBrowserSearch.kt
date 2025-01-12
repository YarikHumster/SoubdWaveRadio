package com.yaros.RadioUrl.ui.search

import android.content.Context
import android.util.Log
import com.android.volley.*
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.google.gson.GsonBuilder
import org.json.JSONArray
import com.yaros.RadioUrl.BuildConfig
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.helpers.NetworkHelper
import com.yaros.RadioUrl.helpers.PreferencesHelper
import kotlinx.coroutines.*
import timber.log.Timber

class RadioBrowserSearch(private var radioBrowserSearchListener: RadioBrowserSearchListener) {

    private val TAG: String = RadioBrowserSearch::class.java.simpleName

    interface RadioBrowserSearchListener {
        fun onRadioBrowserSearchResults(results: Array<RadioBrowserResult>)
    }

    private lateinit var radioBrowserApi: String
    private lateinit var requestQueue: RequestQueue

    init {
        CoroutineScope(Dispatchers.IO).launch {
            radioBrowserApi = PreferencesHelper.loadRadioBrowserApiAddress()
            updateRadioBrowserApi()
        }
    }

    fun searchStation(context: Context, query: String, searchType: Int) {
        Timber.tag(TAG).v("Search - Querying $radioBrowserApi for: $query")

        requestQueue = Volley.newRequestQueue(context)
        val requestUrl: String = when (searchType) {
            Keys.SEARCH_TYPE_BY_UUID -> "https://${radioBrowserApi}/json/stations/byuuid/${query}"
            else -> "https://${radioBrowserApi}/json/stations/search?name=${query.replace(" ", "+")}"
        }

        val stringRequest = object : JsonArrayRequest(Method.GET, requestUrl, null, responseListener, errorListener) {
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val params = HashMap<String, String>()
                params["User-Agent"] = "$Keys.APPLICATION_NAME ${BuildConfig.VERSION_NAME}"
                return params
            }
        }

        stringRequest.retryPolicy = object : RetryPolicy {
            override fun getCurrentTimeout(): Int {
                return 30000
            }

            override fun getCurrentRetryCount(): Int {
                return 30000
            }

            @Throws(VolleyError::class)
            override fun retry(error: VolleyError) {
                Timber.tag(TAG).w("Error: $error")
            }
        }

        requestQueue.add(stringRequest)
    }

    fun stopSearchRequest() {
        if (this::requestQueue.isInitialized) {
            requestQueue.stop()
        }
    }

    private fun createRadioBrowserResult(result: String): Array<RadioBrowserResult> {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.setDateFormat("M/d/yy hh:mm a")
        val gson = gsonBuilder.create()
        return gson.fromJson(result, Array<RadioBrowserResult>::class.java)
    }

    private fun updateRadioBrowserApi() {
        CoroutineScope(Dispatchers.IO).launch {
            val deferred: Deferred<String> = async { NetworkHelper.getRadioBrowserServerSuspended() }
            radioBrowserApi = deferred.await()
        }
    }

    private val responseListener: Response.Listener<JSONArray> = Response.Listener<JSONArray> { response ->
        if (response != null) {
            CoroutineScope(Dispatchers.Default).launch {
                val results = createRadioBrowserResult(response.toString())
                withContext(Dispatchers.Main) {
                    radioBrowserSearchListener.onRadioBrowserSearchResults(results)
                }
            }
        }
    }

    private val errorListener: Response.ErrorListener = Response.ErrorListener { error ->
        Timber.tag(TAG).w("Error: $error")
    }
}