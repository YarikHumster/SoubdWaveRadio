package com.yaros.RadioUrl.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.yaros.RadioUrl.Keys
import okhttp3.*
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import java.util.*

object NetworkHelper {

    private val TAG: String = NetworkHelper::class.java.simpleName
    private lateinit var appContext: Context

    val client: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }

        OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .protocols(listOf(Protocol.HTTP_1_1))
            .hostnameVerifier { hostname, session -> true } // Ignore hostname verification
            .build()
    }

    data class ContentType(var type: String = "", var charset: String = "")

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun isConnectedToNetwork(): Boolean {
        return try {
            val connMgr = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: Network? = connMgr.activeNetwork
            activeNetwork != null
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error checking network connection: ${e.message}")
            false
        }
    }

    suspend fun detectContentType(urlString: String): ContentType {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }

        return suspendCoroutine { cont ->
            val request = Request.Builder().url(urlString).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    when (e) {
                        is SocketTimeoutException -> Timber.tag(TAG).e("Socket timeout: ${e.message}")
                        is ConnectException -> Timber.tag(TAG).e("Connection error: ${e.message}")
                        else -> Timber.tag(TAG).e("General IO error: ${e.message}")
                    }
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                    val contentTypeHeader: String = response.header("Content-Type") ?: ""
                    Timber.tag(TAG).v("Raw content type header: $contentTypeHeader")
                    val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
                    contentTypeHeaderParts.forEachIndexed { index, part ->
                        if (index == 0 && part.isNotEmpty()) {
                            contentType.type = part.trim()
                        } else if (part.contains("charset=")) {
                            contentType.charset = part.substringAfter("charset=").trim()
                        }
                    }

                    if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                        Timber.tag(TAG).w("Special case \"application/octet-stream\"")
                        val headerFieldContentDisposition: String? = response.header("Content-Disposition")
                        if (headerFieldContentDisposition != null) {
                            val fileName: String = headerFieldContentDisposition.split("=")[1].replace("\"", "")
                            contentType.type = FileHelper.getContentTypeFromExtension(fileName)
                        } else {
                            Timber.tag(TAG).i("Unable to get file name from \"Content-Disposition\" header field.")
                        }
                    }

                    Timber.tag(TAG).i("Content type: ${contentType.type} | Character set: ${contentType.charset}")
                    cont.resume(contentType)
                }
            })
        }
    }

    suspend fun downloadPlaylist(playlistUrlString: String): List<String> {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }
        return suspendCoroutine { cont ->
            val request = Request.Builder().url(playlistUrlString).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    when (e) {
                        is SocketTimeoutException -> Timber.tag(TAG).e("Socket timeout: ${e.message}")
                        is ConnectException -> Timber.tag(TAG).e("Connection error: ${e.message}")
                        else -> Timber.tag(TAG).e("General IO error: ${e.message}")
                    }
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val lines = mutableListOf<String>()
                    response.body?.byteStream()?.bufferedReader()?.useLines { sequence ->
                        sequence.take(100).forEach { line ->
                            val trimmedLine = line.take(2000)
                            lines.add(trimmedLine)
                        }
                    }
                    cont.resume(lines)
                }
            })
        }
    }

    suspend fun detectContentTypeSuspended(urlString: String): ContentType {
        if (!isConnectedToNetwork()) {
            throw IOException("No internet connection")
        }
        return suspendCoroutine { cont ->
            client.newCall(Request.Builder().url(urlString).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    when (e) {
                        is SocketTimeoutException -> Timber.tag(TAG).e("Socket timeout: ${e.message}")
                        is ConnectException -> Timber.tag(TAG).e("Connection error: ${e.message}")
                        else -> Timber.tag(TAG).e("General IO error: ${e.message}")
                    }
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                    val contentTypeHeader: String = response.header("Content-Type") ?: ""
                    Timber.tag(TAG).v("Raw content type header: $contentTypeHeader")
                    val contentTypeHeaderParts: List<String> = contentTypeHeader.split(";")
                    contentTypeHeaderParts.forEachIndexed { index, part ->
                        if (index == 0 && part.isNotEmpty()) {
                            contentType.type = part.trim()
                        } else if (part.contains("charset=")) {
                            contentType.charset = part.substringAfter("charset=").trim()
                        }
                    }

                    if (contentType.type.contains(Keys.MIME_TYPE_OCTET_STREAM)) {
                        Timber.tag(TAG).w("Special case \"application/octet-stream\"")
                        val headerFieldContentDisposition: String? = response.header("Content-Disposition")
                        if (headerFieldContentDisposition != null) {
                            val fileName: String = headerFieldContentDisposition.split("=")[1].replace("\"", "")
                            contentType.type = FileHelper.getContentTypeFromExtension(fileName)
                        } else {
                            Timber.tag(TAG).i("Unable to get file name from \"Content-Disposition\" header field.")
                        }
                    }

                    Timber.tag(TAG).i("Content type: ${contentType.type} | Character set: ${contentType.charset}")
                    cont.resume(contentType)
                }
            })
        }
    }

    suspend fun getRadioBrowserServerSuspended(): String {
        return suspendCoroutine { cont ->
            try {
                val serverAddressList: Array<InetAddress> = InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
                val serverAddress = serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
                PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
                cont.resume(serverAddress)
            } catch (e: UnknownHostException) {
                Timber.tag(TAG).e("Error resolving server address: ${e.message}")
                cont.resumeWithException(e)
            } catch (e: SecurityException) {
                Timber.tag(TAG).e("Security exception: ${e.message}")
                cont.resumeWithException(e)
            } catch (e: Exception) {
                Timber.tag(TAG).e("General exception: ${e.message}")
                cont.resumeWithException(e)
            }
        }
    }
}
