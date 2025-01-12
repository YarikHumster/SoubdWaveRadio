package com.yaros.RadioUrl.helpers

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.core.extensions.copy
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import timber.log.Timber
import java.util.*


object DownloadHelper {


    private val TAG: String = DownloadHelper::class.java.simpleName


    private lateinit var collection: Collection
    private lateinit var downloadManager: DownloadManager
    private lateinit var activeDownloads: ArrayList<Long>
    private lateinit var modificationDate: Date


    fun downloadPlaylists(context: Context, playlistUrlStrings: Array<String>) {
        initialize(context)
        val uris: Array<Uri> =
            Array(playlistUrlStrings.size) { index -> playlistUrlStrings[index].toUri() }
        enqueueDownload(context, uris, Keys.FILE_TYPE_PLAYLIST)
    }


    fun updateStationImage(context: Context, station: Station) {
        initialize(context)
        if (station.remoteImageLocation.isNotEmpty()) {
            CollectionHelper.clearImagesFolder(context, station)
            val uris: Array<Uri> = Array(1) { station.remoteImageLocation.toUri() }
            enqueueDownload(context, uris, Keys.FILE_TYPE_IMAGE)
        }
    }


    fun updateStationImages(context: Context) {
        initialize(context)
        PreferencesHelper.saveLastUpdateCollection()
        val uris: MutableList<Uri> = mutableListOf()
        collection.stations.forEach { station ->
            station.radioBrowserStationUuid
            if (!station.imageManuallySet) {
                uris.add(station.remoteImageLocation.toUri())
            }
        }
        enqueueDownload(context, uris.toTypedArray(), Keys.FILE_TYPE_IMAGE)
        Timber.tag(TAG).i("Updating all station images.")
    }


    fun processDownload(context: Context, downloadId: Long) {
        initialize(context)
        val downloadResult: Uri? = downloadManager.getUriForDownloadedFile(downloadId)
        if (downloadResult == null) {
            val downloadErrorCode: Int = getDownloadError(downloadId)
            val downloadErrorFileName: String = getDownloadFileName(downloadManager, downloadId)
            Toast.makeText(
                context,
                "${context.getString(R.string.toastmessage_error_download_error)}: $downloadErrorFileName ($downloadErrorCode)",
                Toast.LENGTH_LONG
            ).show()
            Timber.tag(TAG)
                .w("Download not successful: File name = $downloadErrorFileName Error code = $downloadErrorCode")
            removeFromActiveDownloads(arrayOf(downloadId), deleteDownload = true)
            return
        } else {
            val localFileUri: Uri = downloadResult
            val remoteFileLocation: String = getRemoteFileLocation(downloadManager, downloadId)
            val fileType = FileHelper.getContentType(context, localFileUri)
            if ((fileType in Keys.MIME_TYPES_M3U || fileType in Keys.MIME_TYPES_PLS) && CollectionHelper.isNewStation(
                    collection,
                    remoteFileLocation
                )
            ) {
                addStation(context, localFileUri, remoteFileLocation)
            } else if ((fileType in Keys.MIME_TYPES_M3U || fileType in Keys.MIME_TYPES_PLS) && !CollectionHelper.isNewStation(
                    collection,
                    remoteFileLocation
                )
            ) {
                updateStation(context, localFileUri, remoteFileLocation)
            } else if (fileType in Keys.MIME_TYPES_IMAGE) {
                collection = CollectionHelper.setStationImageWithRemoteLocation(
                    context,
                    collection,
                    localFileUri.toString(),
                    remoteFileLocation,
                    false
                )
            } else if (fileType in Keys.MIME_TYPES_FAVICON) {
                collection = CollectionHelper.setStationImageWithRemoteLocation(
                    context,
                    collection,
                    localFileUri.toString(),
                    remoteFileLocation,
                    false
                )
            }
            removeFromActiveDownloads(arrayOf(downloadId))
        }
    }


    private fun initialize(context: Context) {
        if (!this::modificationDate.isInitialized) {
            modificationDate = PreferencesHelper.loadCollectionModificationDate()
        }
        if (!this::collection.isInitialized || CollectionHelper.isNewerCollectionAvailable(
                modificationDate
            )
        ) {
            collection = FileHelper.readCollection(context)
            modificationDate = PreferencesHelper.loadCollectionModificationDate()
        }
        if (!this::downloadManager.isInitialized) {
            FileHelper.clearFolder(context.getExternalFilesDir(Keys.FOLDER_TEMP), 0)
            downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        }
        if (!this::activeDownloads.isInitialized) {
            activeDownloads = getActiveDownloads()
        }
    }


    private fun enqueueDownload(
        context: Context,
        uris: Array<Uri>,
        type: Int,
        ignoreWifiRestriction: Boolean = false
    ) {
        val allowedNetworkTypes: Int = determineAllowedNetworkTypes(type, ignoreWifiRestriction)
        val newIds = LongArray(uris.size)
        for (i in uris.indices) {
            Timber.tag(TAG).v("DownloadManager enqueue: ${uris[i]}")
            val uri: Uri = uris[i]
            val scheme: String = uri.scheme ?: ""
            val pathSegments: List<String> = uri.pathSegments
            if (scheme.isEmpty() || !scheme.startsWith("http")) {
                Timber.tag(TAG).e("Unsupported URI scheme: $scheme for URI: $uri")
                val errorMessage = context.getString(R.string.dialog_download_unsupport, scheme, uri)
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                continue // Пропускаем этот URI, так как он неподдерживаемый
            }
            if (isNotInDownloadQueue(uri.toString()) && pathSegments.isNotEmpty()) {
                val fileName: String = pathSegments.last()
                try {
                    val request: DownloadManager.Request = DownloadManager.Request(uri)
                        .setAllowedNetworkTypes(allowedNetworkTypes)
                        .setTitle(fileName)
                        .setDestinationInExternalFilesDir(context, Keys.FOLDER_TEMP, fileName)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    newIds[i] = downloadManager.enqueue(request)
                    activeDownloads.add(newIds[i])
                } catch (e: IllegalArgumentException) {
                    Timber.tag(TAG).e(e, "Error enqueuing download for URI: $uri")
                    val errorMessage = context.getString(R.string.dialog_download_error, uri)
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } else {
                Timber.tag(TAG)
                    .e("URI is already in download queue or path segments are empty for URI: $uri")
                val reapeatMessage = context.getString(R.string.dialog_download_repeat, uri)
                Toast.makeText(context, reapeatMessage, Toast.LENGTH_SHORT).show()
            }
        }
        setActiveDownloads(activeDownloads)
    }



    private fun isNotInDownloadQueue(remoteFileLocation: String): Boolean {
        val activeDownloadsCopy = activeDownloads.copy()
        activeDownloadsCopy.forEach { downloadId ->
            if (getRemoteFileLocation(downloadManager, downloadId) == remoteFileLocation) {
                Timber.tag(TAG).w("File is already in download queue: $remoteFileLocation")
                return false
            }
        }
        Timber.tag(TAG).v("File is not in download queue.")
        return true
    }


    private fun removeFromActiveDownloads(
        downloadIds: Array<Long>,
        deleteDownload: Boolean = false
    ): Boolean {
        val success: Boolean =
            activeDownloads.removeAll { downloadId -> downloadIds.contains(downloadId) }
        if (success) {
            setActiveDownloads(activeDownloads)
        }
        if (deleteDownload) {
            downloadIds.forEach { downloadId -> downloadManager.remove(downloadId) }
        }
        return success
    }


    private fun addStation(context: Context, localFileUri: Uri, remoteFileLocation: String) {
        val station: Station = CollectionHelper.createStationFromPlaylistFile(
            context,
            localFileUri,
            remoteFileLocation
        )
        CoroutineScope(IO).launch {
            val deferred: Deferred<NetworkHelper.ContentType> =
                async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(station.getStreamUri()) }
            val contentType: NetworkHelper.ContentType = deferred.await()
            station.streamContent = contentType.type
            withContext(Main) {
                collection = CollectionHelper.addStation(context, collection, station)
            }
        }
    }


    private fun updateStation(context: Context, localFileUri: Uri, remoteFileLocation: String) {
        val station: Station = CollectionHelper.createStationFromPlaylistFile(
            context,
            localFileUri,
            remoteFileLocation
        )
        CoroutineScope(IO).launch {
            val deferred: Deferred<NetworkHelper.ContentType> =
                async(Dispatchers.Default) { NetworkHelper.detectContentTypeSuspended(station.getStreamUri()) }
            val contentType: NetworkHelper.ContentType = deferred.await()
            station.streamContent = contentType.type
            withContext(Main) {
                collection = CollectionHelper.updateStation(context, collection, station)
            }
        }
    }

    private fun setActiveDownloads(activeDownloads: ArrayList<Long>) {
        val builder = StringBuilder()
        for (i in activeDownloads.indices) {
            builder.append(activeDownloads[i]).append(",")
        }
        var activeDownloadsString: String = builder.toString()
        if (activeDownloadsString.isEmpty()) {
            activeDownloadsString = Keys.ACTIVE_DOWNLOADS_EMPTY
        }
        PreferencesHelper.saveActiveDownloads(activeDownloadsString)
    }

    private fun getActiveDownloads(): ArrayList<Long> {
        var inactiveDownloadsFound = false
        val activeDownloadsList: ArrayList<Long> = arrayListOf()
        val activeDownloadsString: String = PreferencesHelper.loadActiveDownloads()
        val count = activeDownloadsString.split(",").size - 1
        val tokenizer = StringTokenizer(activeDownloadsString, ",")
        repeat(count) {
            val token = tokenizer.nextToken().toLong()
            when (isDownloadActive(token)) {
                true -> activeDownloadsList.add(token)
                false -> inactiveDownloadsFound = true
            }
        }
        if (inactiveDownloadsFound) setActiveDownloads(activeDownloadsList)
        return activeDownloadsList
    }

    private fun getRemoteFileLocation(downloadManager: DownloadManager, downloadId: Long): String {
        var remoteFileLocation = ""
        val cursor: Cursor =
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation =
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
        }
        return remoteFileLocation
    }

    private fun getDownloadFileName(downloadManager: DownloadManager, downloadId: Long): String {
        var remoteFileLocation = ""
        val cursor: Cursor =
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            remoteFileLocation =
                cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE))
        }
        return remoteFileLocation
    }

    private fun isDownloadActive(downloadId: Long): Boolean {
        var downloadStatus: Int = -1
        val cursor: Cursor =
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            downloadStatus =
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
        return downloadStatus == DownloadManager.STATUS_RUNNING
    }


    private fun getDownloadError(downloadId: Long): Int {
        var reason: Int = -1
        val cursor: Cursor =
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        if (cursor.count > 0) {
            cursor.moveToFirst()
            val downloadStatus =
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (downloadStatus == DownloadManager.STATUS_FAILED) {
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            }
        }
        return reason
    }


    private fun determineAllowedNetworkTypes(type: Int, ignoreWifiRestriction: Boolean): Int {
        var allowedNetworkTypes: Int =
            (DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        if (type == Keys.FILE_TYPE_AUDIO) {
            if (!ignoreWifiRestriction && !PreferencesHelper.downloadOverMobile()) {
                allowedNetworkTypes = DownloadManager.Request.NETWORK_WIFI
            }
        }
        return allowedNetworkTypes
    }

}
