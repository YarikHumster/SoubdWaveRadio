package com.yaros.RadioUrl.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import com.yaros.RadioUrl.ui.search.DirectInputCheck
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.*

object CollectionHelper {

    private val TAG: String = CollectionHelper::class.java.simpleName

    fun isNewStation(collection: Collection, station: Station): Boolean {
        collection.stations.forEach {
            if (it.getStreamUri() == station.getStreamUri()) return false
        }
        return true
    }

    fun isNewStation(collection: Collection, remoteStationLocation: String): Boolean {
        collection.stations.forEach {
            if (it.remoteStationLocation == remoteStationLocation) return false
        }
        return true
    }

    fun isNewerCollectionAvailable(date: Date): Boolean {
        var newerCollectionAvailable = false
        val modificationDate: Date = PreferencesHelper.loadCollectionModificationDate()
        if (modificationDate.after(date) || date == Keys.DEFAULT_DATE) {
            newerCollectionAvailable = true
        }
        return newerCollectionAvailable
    }

    fun createStationFromPlaylistFile(
        context: Context,
        localFileUri: Uri,
        remoteFileLocation: String
    ): Station {
        val station: Station =
            FileHelper.readStationPlaylist(context.contentResolver.openInputStream(localFileUri))
        if (station.name.isEmpty()) {
            station.name = FileHelper.getFileName(context, localFileUri).substringBeforeLast(".")
        }
        station.remoteStationLocation = remoteFileLocation
        station.remoteImageLocation = getFaviconAddress(remoteFileLocation)
        station.modificationDate = GregorianCalendar.getInstance().time
        return station
    }

    fun updateStation(context: Context, collection: Collection, station: Station): Collection {
        var updatedCollection: Collection = collection

        if (station.radioBrowserStationUuid.isNotEmpty()) {
            updatedCollection.stations.forEach {
                if (it.radioBrowserStationUuid == station.radioBrowserStationUuid) {
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    it.remoteStationLocation = station.remoteStationLocation
                    it.homepage = station.homepage
                    if (!it.nameManuallySet) it.name = station.name
                    DownloadHelper.updateStationImage(context, it)
                }
            }
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        } else if (station.remoteStationLocation.isNotEmpty()) {
            updatedCollection.stations.forEach {
                if (it.remoteStationLocation == station.remoteStationLocation) {
                    it.streamUris[it.stream] = station.getStreamUri()
                    it.streamContent = station.streamContent
                    it.remoteImageLocation = station.remoteImageLocation
                    if (!it.nameManuallySet) it.name = station.name
                    if (!it.imageManuallySet) DownloadHelper.updateStationImage(context, it)
                }
            }
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
        }

        return updatedCollection
    }

    fun addStation(context: Context, collection: Collection, newStation: Station): Collection {
        if (!newStation.isValid()) {
            Toast.makeText(context, R.string.toastmessage_station_not_valid, Toast.LENGTH_LONG)
                .show()
            return collection
        } else if (!isNewStation(collection, newStation)) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, R.string.toastmessage_station_duplicate, Toast.LENGTH_LONG)
                    .show()
            }
            return collection
        } else {
            var updatedCollection: Collection = collection
            val updatedStationList: MutableList<Station> = collection.stations.toMutableList()
            updatedStationList.add(newStation)
            updatedCollection.stations = updatedStationList
            updatedCollection = sortCollection(updatedCollection)
            saveCollection(context, updatedCollection, false)
            DownloadHelper.updateStationImage(context, newStation)
            return updatedCollection
        }
    }

    fun setStationImageWithRemoteLocation(
        context: Context,
        collection: Collection,
        tempImageFileUri: String,
        remoteFileLocation: String,
        imageManuallySet: Boolean = false
    ): Collection {
        collection.stations.forEach { station ->
            if (station.remoteImageLocation.substringAfter(":") == remoteFileLocation.substringAfter(
                    ":"
                )
            ) {
                station.smallImage = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri.toUri(),
                    Keys.SIZE_STATION_IMAGE_CARD,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.image = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri.toUri(),
                    Keys.SIZE_STATION_IMAGE_MAXIMUM,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.imageColor = ImageHelper.getMainColor(context, tempImageFileUri.toUri())
                station.imageManuallySet = imageManuallySet
            }
        }
        saveCollection(context, collection)
        return collection
    }

    fun setStationImageWithStationUuid(
        context: Context,
        collection: Collection,
        tempImageFileUri: Uri,
        stationUuid: String,
        imageManuallySet: Boolean = false
    ): Collection {
        collection.stations.forEach { station ->
            if (station.uuid == stationUuid) {
                station.smallImage = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri,
                    Keys.SIZE_STATION_IMAGE_CARD,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.image = FileHelper.saveStationImage(
                    context,
                    station.uuid,
                    tempImageFileUri,
                    Keys.SIZE_STATION_IMAGE_MAXIMUM,
                    Keys.STATION_IMAGE_FILE
                ).toString()
                station.imageColor = ImageHelper.getMainColor(context, tempImageFileUri)
                station.imageManuallySet = imageManuallySet
            }
        }
        saveCollection(context, collection)
        return collection
    }

    fun clearImagesFolder(context: Context, station: Station) {
        // clear image folder
        val imagesFolder = File(
            context.getExternalFilesDir(""),
            FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid)
        )
        FileHelper.clearFolder(imagesFolder, 0)
    }

    fun deleteStationImages(context: Context, station: Station) {
        val imagesFolder = File(
            context.getExternalFilesDir(""),
            FileHelper.determineDestinationFolderPath(Keys.FILE_TYPE_IMAGE, station.uuid)
        )
        FileHelper.clearFolder(imagesFolder, 0, true)
    }

    fun getStation(collection: Collection, stationUuid: String): Station {
        collection.stations.forEach { station ->
            if (station.uuid == stationUuid) {
                return station
            }
        }
        return if (collection.stations.isNotEmpty()) {
            collection.stations.first()
        } else {
            Station()
        }
    }

    fun getNextMediaItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        return if (collection.stations.isEmpty() || currentStationPosition == -1) {
            buildMediaItem(context, Station())
        } else if (currentStationPosition < collection.stations.size - 1) {
            buildMediaItem(context, collection.stations[currentStationPosition + 1])
        } else {
            buildMediaItem(context, collection.stations.first())
        }
    }

    fun getPreviousMediaItem(
        context: Context,
        collection: Collection,
        stationUuid: String
    ): MediaItem {
        val currentStationPosition: Int = getStationPosition(collection, stationUuid)
        return if (collection.stations.isEmpty() || currentStationPosition == -1) {
            buildMediaItem(context, Station())
        } else if (currentStationPosition > 0) {
            buildMediaItem(context, collection.stations[currentStationPosition - 1])
        } else {
            buildMediaItem(context, collection.stations.last())
        }
    }

    fun getStationPosition(collection: Collection, stationUuid: String): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.uuid == stationUuid) {
                return stationId
            }
        }
        return -1
    }

    fun getStationPositionFromRadioBrowserStationUuid(
        collection: Collection,
        radioBrowserStationUuid: String
    ): Int {
        collection.stations.forEachIndexed { stationId, station ->
            if (station.radioBrowserStationUuid == radioBrowserStationUuid) {
                return stationId
            }
        }
        return -1
    }

    fun getChildren(context: Context, collection: Collection): List<MediaItem> {
        val mediaItems: MutableList<MediaItem> = mutableListOf()
        collection.stations.forEach { station ->
            mediaItems.add(buildMediaItem(context, station))
        }
        return mediaItems
    }

    fun getItem(context: Context, collection: Collection, stationUuid: String): MediaItem {
        return buildMediaItem(context, getStation(collection, stationUuid))
    }

    fun getRecent(context: Context, collection: Collection): MediaItem {
        return buildMediaItem(
            context,
            getStation(collection, PreferencesHelper.loadLastPlayedStationUuid())
        )
    }

    fun getRootItem(): MediaItem {
        val metadata: MediaMetadata = MediaMetadata.Builder()
            .setTitle("Root Folder")
            .setIsPlayable(false)
            .setIsBrowsable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId("[rootID]")
            .setMediaMetadata(metadata)
            .build()
    }

    fun savePlaybackState(
        context: Context,
        collection: Collection,
        stationUuid: String,
        isPlaying: Boolean
    ): Collection {
        collection.stations.forEach {
            it.isPlaying = false
            if (it.uuid == stationUuid) {
                it.isPlaying = isPlaying
            }
        }
        collection.modificationDate = saveCollection(context, collection)
        return collection
    }

    fun saveCollection(context: Context, collection: Collection, async: Boolean = true): Date {
        Timber.tag(TAG)
            .v("Saving collection of radio stations to storage. Async = ${async}. Size = ${collection.stations.size}")
        val date: Date = Calendar.getInstance().time
        collection.modificationDate = date
        when (async) {
            true -> {
                CoroutineScope(IO).launch {
                    FileHelper.saveCollectionSuspended(context, collection, date)
                    sendCollectionBroadcast(context, date)
                }
            }

            false -> {
                FileHelper.saveCollection(context, collection, date)
                sendCollectionBroadcast(context, date)
            }
        }
        return date
    }

    suspend fun createStationsFromUrl(
        query: String,
        lastCheckedAddress: String = String()
    ): List<Station> {
        val stationList: MutableList<Station> = mutableListOf()
        val contentType: String =
            NetworkHelper.detectContentType(query).type.lowercase(Locale.getDefault())
        val directInputCheck: DirectInputCheck? = null

        if (Keys.MIME_TYPES_M3U.contains(contentType)) {
            val lines: List<String> = NetworkHelper.downloadPlaylist(query)
            stationList.addAll(readM3uPlaylistContent(lines))
        } else if (Keys.MIME_TYPES_PLS.contains(contentType)) {
            val lines: List<String> = NetworkHelper.downloadPlaylist(query)
            stationList.addAll(readPlsPlaylistContent(lines))
        } else if (Keys.MIME_TYPES_MPEG.contains(contentType) or
            Keys.MIME_TYPES_OGG.contains(contentType) or
            Keys.MIME_TYPES_AAC.contains(contentType) or
            Keys.MIME_TYPES_HLS.contains(contentType)
        ) {
            directInputCheck?.processIcecastStream(query, stationList)
            val station = Station(
                name = query,
                streamUris = mutableListOf(query),
                streamContent = contentType,
                modificationDate = GregorianCalendar.getInstance().time,
            )
            if (lastCheckedAddress != query) {
                stationList.add(station)
            }
        }
        return stationList
    }

    suspend fun createStationListFromContentUri(context: Context, contentUri: Uri): List<Station> {
        val stationList: MutableList<Station> = mutableListOf()
        val fileType: String = FileHelper.getContentType(context, contentUri)
        if (Keys.MIME_TYPES_M3U.contains(fileType)) {
            val playlist = FileHelper.readTextFileFromContentUri(context, contentUri)
            stationList.addAll(readM3uPlaylistContent(playlist))
        } else if (Keys.MIME_TYPES_PLS.contains(fileType)) {
            val playlist = FileHelper.readTextFileFromContentUri(context, contentUri)
            stationList.addAll(readPlsPlaylistContent(playlist))
        }
        return stationList
    }

    private suspend fun readM3uPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name = String()
        var streamUri: String
        var contentType: String

        playlist.forEach { line ->
            if (line.startsWith("#EXTINF:")) {
                name = line.substringAfter(",").trim()
            } else if (line.isNotBlank() && !line.startsWith("#")) {
                streamUri = line.trim()
                if (name.isEmpty()) {
                    name = streamUri
                }
                contentType =
                    NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    val station = Station(
                        name = name,
                        streamUris = mutableListOf(streamUri),
                        streamContent = contentType,
                        modificationDate = GregorianCalendar.getInstance().time,
                    )
                    stations.add(station)
                }
                name = String()
            }
        }
        return stations
    }

    private suspend fun readPlsPlaylistContent(playlist: List<String>): List<Station> {
        val stations: MutableList<Station> = mutableListOf()
        var name = String()
        var streamUri: String
        var contentType: String

        playlist.forEachIndexed { index, line ->
            if (line.startsWith("File")) {
                streamUri = line.substringAfter("=").trim()
                contentType =
                    NetworkHelper.detectContentType(streamUri).type.lowercase(Locale.getDefault())
                if (contentType != Keys.MIME_TYPE_UNSUPPORTED) {
                    val number: String = line.substring(4 /* File */, line.indexOf("="))
                    val lineBeforeIndex: Int = index - 1
                    val lineAfterIndex: Int = index + 1
                    if (lineBeforeIndex >= 0) {
                        val lineBefore: String = playlist[lineBeforeIndex]
                        if (lineBefore.startsWith("Title$number")) {
                            name = lineBefore.substringAfter("=").trim()
                        }
                    }
                    if (name.isEmpty() && lineAfterIndex < playlist.size) {
                        val lineAfter: String = playlist[lineAfterIndex]
                        if (lineAfter.startsWith("Title$number")) {
                            name = lineAfter.substringAfter("=").trim()
                        }
                    }
                    if (name.isEmpty()) {
                        name = streamUri
                    }
                    val station = Station(
                        name = name,
                        streamUris = mutableListOf(streamUri),
                        streamContent = contentType,
                        modificationDate = GregorianCalendar.getInstance().time,
                    )
                    stations.add(station)
                }
            }
        }
        return stations
    }

    fun exportCollectionM3u(context: Context, collection: Collection) {
        Timber.tag(TAG).v("Exporting collection of stations as M3U")

        if (collection.stations.size > 0) {
            CoroutineScope(IO).launch {
                FileHelper.backupCollectionAsM3uSuspended(
                    context,
                    collection
                )
            }
        }
    }

    fun createM3uString(collection: Collection): String {
        val m3uString = StringBuilder()

        m3uString.append("#EXTM3U")
        m3uString.append("\n")

        collection.stations.forEach { station ->
            m3uString.append("\n")
            m3uString.append("#EXTINF:-1,")
            m3uString.append(station.name)
            m3uString.append("\n")
            m3uString.append(station.getStreamUri())
            m3uString.append("\n")
        }

        return m3uString.toString()
    }

    fun exportCollectionPls(context: Context, collection: Collection) {
        Timber.tag(TAG).v("Exporting collection of stations as PLS")
        if (collection.stations.size > 0) {
            CoroutineScope(IO).launch {
                FileHelper.backupCollectionAsPlsSuspended(
                    context,
                    collection
                )
            }
        }
    }

    fun createPlsString(collection: Collection): String {

        val plsString = StringBuilder()
        var counter = 1

        plsString.append("[playlist]")
        plsString.append("\n")

        collection.stations.forEach { station ->
            plsString.append("\n")
            plsString.append("Title$counter=")
            plsString.append(station.name)
            plsString.append("\n")
            plsString.append("File$counter=")
            plsString.append(station.getStreamUri())
            plsString.append("\n")
            plsString.append("Length$counter=-1")
            plsString.append("\n")
            counter++
        }

        plsString.append("\n")
        plsString.append("NumberOfEntries=${collection.stations.size}")
        plsString.append("\n")
        plsString.append("Version=2")

        return plsString.toString()
    }

    fun sendCollectionBroadcast(context: Context, modificationDate: Date) {
        Timber.tag(TAG).v("Broadcasting that collection has changed.")
        val collectionChangedIntent = Intent()
        collectionChangedIntent.action = Keys.ACTION_COLLECTION_CHANGED
        collectionChangedIntent.putExtra(
            Keys.EXTRA_COLLECTION_MODIFICATION_DATE,
            modificationDate.time
        )
        LocalBroadcastManager.getInstance(context).sendBroadcast(collectionChangedIntent)
    }

    //    /* Creates MediaMetadata for a single station - used in media session*/
//    fun buildStationMediaMetadata(context: Context, station: Station, metadata: String): MediaMetadataCompat {
//        return MediaMetadataCompat.Builder().apply {
//            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, station.name)
//            putString(MediaMetadataCompat.METADATA_KEY_TITLE, metadata)
//            putString(MediaMetadataCompat.METADATA_KEY_ALBUM, context.getString(R.string.app_name))
//            putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, station.getStreamUri())
//            putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//            //putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, station.image)
//        }.build()
//    }
//
//    /* Creates MediaItem for a station - used by collection provider */
//    fun buildStationMediaMetaItem(context: Context, station: Station): MediaBrowserCompat.MediaItem {
//        val mediaDescriptionBuilder = MediaDescriptionCompat.Builder()
//        mediaDescriptionBuilder.setMediaId(station.uuid)
//        mediaDescriptionBuilder.setTitle(station.name)
//        mediaDescriptionBuilder.setIconBitmap(ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN))
//        // mediaDescriptionBuilder.setIconUri(station.image.toUri())
//        return MediaBrowserCompat.MediaItem(mediaDescriptionBuilder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
//    }
//
//    /* Creates description for a station - used in MediaSessionConnector */
//    fun buildStationMediaDescription(context: Context, station: Station, metadata: String): MediaDescriptionCompat {
//        val coverBitmap: Bitmap = ImageHelper.getScaledStationImage(context, station.image, Keys.SIZE_COVER_LOCK_SCREEN)
//        val extras: Bundle = Bundle()
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, coverBitmap)
//        extras.putParcelable(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, coverBitmap)
//        return MediaDescriptionCompat.Builder().apply {
//            setMediaId(station.uuid)
//            setIconBitmap(coverBitmap)
//            setIconUri(station.image.toUri())
//            setTitle(metadata)
//            setSubtitle(station.name)
//            setExtras(extras)
//        }.build()
//    }

    fun buildMediaItem(context: Context, station: Station): MediaItem {
        val requestMetadata = MediaItem.RequestMetadata.Builder().apply {
            setMediaUri(station.getStreamUri().toUri())
        }.build()
        val mediaMetadata = MediaMetadata.Builder().apply {
            setArtist(station.name)
            if (station.image.isNotEmpty() && station.image.startsWith("file://")) {
                setArtworkData(
                    station.image.toUri().toFile().readBytes(),
                    MediaMetadata.PICTURE_TYPE_FRONT_COVER
                )
            } else {
                setArtworkData(
                    ImageHelper.getStationImageAsByteArray(context),
                    MediaMetadata.PICTURE_TYPE_FRONT_COVER
                )
            }
            setIsBrowsable(false)
            setIsPlayable(true)
        }.build()
        return MediaItem.Builder().apply {
            setMediaId(station.uuid)
            setRequestMetadata(requestMetadata)
            setMediaMetadata(mediaMetadata)
            setUri(station.getStreamUri().toUri())
        }.build()
    }

    fun sortCollection(collection: Collection): Collection {
        val favoriteStations = collection.stations.filter { it.starred }
        val otherStations = collection.stations.filter { !it.starred }
        collection.stations = (favoriteStations + otherStations).toMutableList()
        return collection
    }

    private fun getFaviconAddress(urlString: String): String {
        var faviconAddress = String()
        try {
            var host: String = URL(urlString).host
            if (!host.startsWith("www")) {
                val index = host.indexOf(".")
                host = "www" + host.substring(index)
            }
            faviconAddress = "http://$host/favicon.ico"
        } catch (e: Exception) {
            Timber.tag(TAG).e("Unable to get base URL from $urlString.\n$e ")
        }
        return faviconAddress
    }

}