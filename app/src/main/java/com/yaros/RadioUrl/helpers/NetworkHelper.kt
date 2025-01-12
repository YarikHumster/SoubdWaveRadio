package com.yaros.RadioUrl.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.yaros.RadioUrl.Keys
import okhttp3.*
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
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
            .hostnameVerifier { hostname, session -> true } // Игнорируем проверку имени хоста
            .build()
    }

    data class ContentType(var type: String = String(), var charset: String = String())

    fun isConnectedToNetwork(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: Network? = connMgr.activeNetwork
        return activeNetwork != null
    }

    suspend fun detectContentType(urlString: String): ContentType {
        return suspendCoroutine { cont ->
            val request = Request.Builder().url(urlString).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.tag(TAG).e("Error fetching content type: ${e.message}")
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                    val contentTypeHeader: String = response.header("Content-Type") ?: String()
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
        return suspendCoroutine { cont ->
            val request = Request.Builder().url(playlistUrlString).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.tag(TAG).e("Error downloading playlist: ${e.message}")
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
        return suspendCoroutine { cont ->
            // Используем асинхронный метод detectContentType
            client.newCall(Request.Builder().url(urlString).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Timber.tag(TAG).e("Error fetching content type: ${e.message}")
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val contentType = ContentType(Keys.MIME_TYPE_UNSUPPORTED, Keys.CHARSET_UNDEFINDED)
                    val contentTypeHeader: String = response.header("Content-Type") ?: String()
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
            val serverAddress: String = try {
                val serverAddressList: Array<InetAddress> =
                    InetAddress.getAllByName(Keys.RADIO_BROWSER_API_BASE)
                serverAddressList[Random().nextInt(serverAddressList.size)].canonicalHostName
            } catch (e: UnknownHostException) {
                Keys.RADIO_BROWSER_API_DEFAULT
            }
            PreferencesHelper.saveRadioBrowserApiAddress(serverAddress)
            cont.resume(serverAddress)
        }
    }
}


