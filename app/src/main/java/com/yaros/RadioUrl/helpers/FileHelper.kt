package com.yaros.RadioUrl.helpers

import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.core.Collection
import com.yaros.RadioUrl.core.Station
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.IO
import timber.log.Timber
import java.io.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object FileHelper {


    private val TAG: String = FileHelper::class.java.simpleName


    fun getFileSize(context: Context, uri: Uri): Long {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        return if (cursor != null) {
            val sizeIndex: Int = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            val size: Long = cursor.getLong(sizeIndex)
            cursor.close()
            size
        } else {
            0L
        }
    }


    fun getFileName(context: Context, uri: Uri): String {
        val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
        return if (cursor != null) {
            val nameIndex: Int = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            val name: String = cursor.getString(nameIndex)
            cursor.close()
            name
        } else {
            String()
        }
    }


    fun getContentType(context: Context, uri: Uri): String {
        var contentType: String = context.contentResolver.getType(uri) ?: Keys.MIME_TYPE_UNSUPPORTED
        contentType = contentType.lowercase(Locale.getDefault())
        return if (contentType != Keys.MIME_TYPE_UNSUPPORTED && !contentType.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
            contentType
        } else {
            getContentTypeFromExtension(getFileName(context, uri))
        }
    }


    fun getContentTypeFromExtension(fileName: String): String {
        Timber.tag(TAG).i("Deducing content type from file name: $fileName")
        if (fileName.endsWith("m3u", true)) return Keys.MIME_TYPE_M3U
        if (fileName.endsWith("pls", true)) return Keys.MIME_TYPE_PLS
        if (fileName.endsWith("png", true)) return Keys.MIME_TYPE_PNG
        if (fileName.endsWith("jpg", true)) return Keys.MIME_TYPE_JPG
        if (fileName.endsWith("jpeg", true)) return Keys.MIME_TYPE_JPG
        return Keys.MIME_TYPE_UNSUPPORTED
    }


    fun determineDestinationFolderPath(type: Int, stationUuid: String): String {
        val folderPath: String = when (type) {
            Keys.FILE_TYPE_PLAYLIST -> Keys.FOLDER_TEMP
            Keys.FILE_TYPE_AUDIO -> Keys.FOLDER_AUDIO + "/" + stationUuid
            Keys.FILE_TYPE_IMAGE -> Keys.FOLDER_IMAGES + "/" + stationUuid
            else -> "/"
        }
        return folderPath
    }


    fun clearFolder(folder: File?, keep: Int, deleteFolder: Boolean = false) {
        if (folder != null && folder.exists()) {
            val files = folder.listFiles()!!
            val fileCount: Int = files.size
            files.sortBy { it.lastModified() }
            for (fileNumber in files.indices) {
                if (fileNumber < fileCount - keep) {
                    files[fileNumber].delete()
                }
            }
            if (deleteFolder && keep == 0) {
                folder.delete()
            }
        }
    }


    fun saveStationImage(
        context: Context,
        stationUuid: String,
        sourceImageUri: Uri,
        size: Int,
        fileName: String
    ): Uri {
        val coverBitmap: Bitmap = ImageHelper.getScaledStationImage(context, sourceImageUri, size)
        val file = File(
            context.getExternalFilesDir(
                determineDestinationFolderPath(
                    Keys.FILE_TYPE_IMAGE,
                    stationUuid
                )
            ), fileName
        )
        writeImageFile(coverBitmap, file)
        return file.toUri()
    }


    fun saveCollection(context: Context, collection: Collection, lastSave: Date) {
        Timber.tag(TAG).v("Saving collection - Thread: ${Thread.currentThread().name}")
        val collectionSize: Int = collection.stations.size
        if (collectionSize > 0 || PreferencesHelper.loadCollectionSize() == 1) {
            val gson: Gson = getCustomGson()
            var json = String()
            try {
                json = gson.toJson(collection)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (json.isNotBlank()) {
                writeTextFile(context, json, Keys.FOLDER_COLLECTION, Keys.COLLECTION_FILE)
                PreferencesHelper.saveCollectionModificationDate(lastSave)
                PreferencesHelper.saveCollectionSize(collectionSize)
            } else {
                Timber.tag(TAG)
                    .w("Not writing collection file. Reason: JSON string was completely empty.")
            }
        } else {
            Timber.tag(TAG)
                .w("Not saving collection. Reason: Trying to override an collection with more than one station")
        }
    }


    fun readStationPlaylist(playlistInputStream: InputStream?): Station {
        val station = Station()
        if (playlistInputStream != null) {
            val reader = BufferedReader(InputStreamReader(playlistInputStream))
            reader.forEachLine { line ->
                when {
                    line.contains("#EXTINF:-1,") -> station.name = line.substring(11).trim()
                    line.contains("#EXTINF:0,") -> station.name = line.substring(10).trim()
                    line.startsWith("http") -> station.streamUris.add(0, line.trim())
                    line.matches(Regex("^Title[0-9]+=.*")) -> station.name =
                        line.substring(line.indexOf("=") + 1).trim()
                    line.matches(Regex("^File[0-9]+=http.*")) -> station.streamUris.add(
                        line.substring(
                            line.indexOf("=") + 1
                        ).trim()
                    )
                }

            }
            playlistInputStream.close()
        }
        return station
    }


    fun readCollection(context: Context): Collection {
        Timber.tag(TAG).v("Reading collection - Thread: ${Thread.currentThread().name}")
        val json: String = readTextFileFromFile(context)
        var collection = Collection()
        if (json.isNotBlank()) {
            try {
                collection = getCustomGson().fromJson(json, collection::class.java)
            } catch (e: Exception) {
                Timber.tag(TAG).e("Error Reading collection.\nContent: $json")
                e.printStackTrace()
            }
        }
        return collection
    }


    fun getM3ulUri(activity: Activity): Uri? {
        var m3ulUri: Uri? = null
        var m3uFile =
            File(activity.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_M3U_FILE)
        if (!m3uFile.exists()) {
            m3uFile = File(
                activity.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION),
                Keys.COLLECTION_M3U_FILE
            )
        }
        if (m3uFile.exists()) {
            m3ulUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.provider",
                m3uFile
            )
        }
        return m3ulUri
    }


    fun getPlslUri(activity: Activity): Uri? {
        var plslUri: Uri? = null
        var plsFile =
            File(activity.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_PLS_FILE)
        if (!plsFile.exists()) {
            plsFile = File(
                activity.getExternalFilesDir(Keys.URLRADIO_LEGACY_FOLDER_COLLECTION),
                Keys.COLLECTION_PLS_FILE
            )
        }
        if (plsFile.exists()) {
            plslUri = FileProvider.getUriForFile(
                activity,
                "${activity.applicationContext.packageName}.provider",
                plsFile
            )
        }
        return plslUri
    }


    suspend fun saveCollectionSuspended(context: Context, collection: Collection, lastSave: Date) {
    withContext(Dispatchers.IO) {
        val gson = getCustomGson()
        val json = gson.toJson(collection)
        val file = context.filesDir.resolve(Keys.COLLECTION_FILE)
        try {
            file.writeText(json)
            PreferencesHelper.saveCollectionModificationDate(lastSave)
            PreferencesHelper.saveCollectionSize(collection.stations.size)
        } catch (e: IOException) {
            Timber.tag(TAG).e("Ошибка при сохранении коллекции: ${e.message}")
        }
    }
}


    suspend fun readCollectionSuspended(context: Context): Collection {
    return withContext(Dispatchers.IO) {
        val gson = getCustomGson()
        val file = context.filesDir.resolve(Keys.COLLECTION_FILE)
        if (file.exists()) {
            try {
                val json = file.readText()
                return@withContext gson.fromJson(json, Collection::class.java)
            } catch (e: IOException) {
                Timber.tag(TAG).e("Ошибка при чтении коллекции: ${e.message}")
            } catch (e: JsonParseException) {
                Timber.tag(TAG).e("Ошибка парсинга JSON: ${e.message}")
            }
        }
        Collection()
    }
}


    suspend fun saveCopyOfFileSuspended(
        context: Context,
        originalFileUri: Uri,
        targetFileUri: Uri
    ): Boolean {
        return suspendCoroutine { cont ->
            cont.resume(copyFile(context, originalFileUri, targetFileUri))
        }
    }


    suspend fun backupCollectionAsM3uSuspended(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            Timber.tag(TAG)
                .v("Backing up collection as M3U - Thread: ${Thread.currentThread().name}")
            val m3uString: String = CollectionHelper.createM3uString(collection)
            cont.resume(
                writeTextFile(
                    context,
                    m3uString,
                    Keys.FOLDER_COLLECTION,
                    Keys.COLLECTION_M3U_FILE
                )
            )
        }
    }


    suspend fun backupCollectionAsPlsSuspended(context: Context, collection: Collection) {
        return suspendCoroutine { cont ->
            Timber.tag(TAG)
                .v("Backing up collection as PLS - Thread: ${Thread.currentThread().name}")
            val plsString: String = CollectionHelper.createPlsString(collection)
            cont.resume(
                writeTextFile(
                    context,
                    plsString,
                    Keys.FOLDER_COLLECTION,
                    Keys.COLLECTION_PLS_FILE
                )
            )
        }
    }


    private fun copyFile(
        context: Context,
        originalFileUri: Uri,
        targetFileUri: Uri,
    ): Boolean {
        var success = true
        var inputStream: InputStream? = null
        val outputStream: OutputStream?
        try {
            inputStream = context.contentResolver.openInputStream(originalFileUri)
            outputStream = context.contentResolver.openOutputStream(targetFileUri)
            if (outputStream != null && inputStream != null) {
                inputStream.copyTo(outputStream)
                outputStream.close()
            }
        } catch (exception: Exception) {
            Timber.tag(TAG).e("Unable to copy file.")
            success = false
            exception.printStackTrace()
        } finally {
            inputStream?.close()
        }
        if (success) {
            try {
                context.contentResolver.delete(originalFileUri, null, null)
            } catch (e: Exception) {
                Timber.tag(TAG).e("Unable to delete the original file. Stack trace: $e")
            }
        }
        return success
    }


    private fun getCustomGson(): Gson {
    return GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")
        .excludeFieldsWithoutExposeAnnotation()
        .create()
}


    fun createNomediaFile(folder: File?) {
        if (folder != null && folder.exists() && folder.isDirectory) {
            val nomediaFile: File = getNoMediaFile(folder)
            if (!nomediaFile.exists()) {
                val noMediaOutStream = FileOutputStream(getNoMediaFile(folder))
                noMediaOutStream.write(0)
            } else {
                Timber.tag(TAG).v(".nomedia file exists already in given folder.")
            }
        } else {
            Timber.tag(TAG).w("Unable to create .nomedia file. Given folder is not valid.")
        }
    }


    private fun readTextFileFromFile(context: Context): String {

        val file = File(context.getExternalFilesDir(Keys.FOLDER_COLLECTION), Keys.COLLECTION_FILE)
        if (!file.exists() || !file.canRead()) {
            return String()
        }
        val stream: InputStream = file.inputStream()
        val reader = BufferedReader(InputStreamReader(stream))
        val builder: StringBuilder = StringBuilder()
        reader.forEachLine {
            builder.append(it)
            builder.append("\n")
        }
        stream.close()
        return builder.toString()
    }

    fun readTextFileFromContentUri(context: Context, contentUri: Uri): List<String> {
        val lines: MutableList<String> = mutableListOf()
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(contentUri)
            if (inputStream != null) {
                val reader: InputStreamReader = inputStream.reader()
                var index = 0
                reader.forEachLine {
                    index += 1
                    if (index < 256)
                        lines.add(it)
                }
                inputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return lines
    }


    @Suppress("SameParameterValue")
    private fun writeTextFile(context: Context, text: String, folder: String, fileName: String) {
        if (text.isNotBlank()) {
            File(context.getExternalFilesDir(folder), fileName).writeText(text)
        } else {
            Timber.tag(TAG)
                .w("Writing text file $fileName failed. Empty text string text was provided.")
        }
    }


    private fun writeImageFile(
        bitmap: Bitmap,
        file: File
    ) {
        if (file.exists()) file.delete()
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            out.flush()
            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    private fun getNoMediaFile(folder: File): File {
        return File(folder, ".nomedia")
    }

}
