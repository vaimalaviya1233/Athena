/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2017 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.kin.athena.presentation.screens.settings.subSettings.dns.hosts

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kin.athena.R
import com.kin.athena.core.logging.Logger
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Date
import javax.net.ssl.HttpsURLConnection

class RuleDatabaseItemUpdate(
    private val context: Context,
    private val worker: RuleDatabaseUpdateWorker,
    private val item: HostFile,
) {
    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 3000
        private const val READ_TIMEOUT_MILLIS = 10000
    }

    private lateinit var url: URL
    private var file: File? = null

    fun shouldDownload(): Boolean {
        if (item.data.startsWith("content://")) {
            return true
        }

        file = FileHelper.getItemFile(item)
        if (file == null || !item.isDownloadable()) {
            return false
        }

        try {
            url = URL(item.data)
        } catch (e: MalformedURLException) {
            worker.addError(item, context.getString(R.string.invalid_url_s) + item.data)
            return false
        }

        return true
    }

    fun run() {
        if (item.data.startsWith("content://")) {
            try {
                val uri = parseUri(item.data)
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                context.contentResolver.openInputStream(uri)?.close()
                Logger.error("run: Permission requested for ${item.data}")
            } catch (e: SecurityException) {
                Logger.error("run: Error taking permission", e)
                worker.addError(item, context.getString(R.string.permission_denied))
            } catch (e: FileNotFoundException) {
                Logger.error("run: File not found", e)
                worker.addError(item, context.getString(R.string.file_not_found))
            } catch (e: IOException) {
                worker.addError(
                    item,
                    context.getString(R.string.unknown_error_s) + e.localizedMessage
                )
            }
            return
        }

        val singleWriterMultipleReaderFile = SingleWriterMultipleReaderFile(file!!)
        var connection: HttpURLConnection? = null
        worker.addBegin(item)
        try {
            connection = getHttpURLConnection(file!!, singleWriterMultipleReaderFile, url)

            if (!validateResponse(connection)) {
                // Handle 304 Not Modified as success (file already up to date)
                if (connection.responseCode == 304) {
                    worker.addDone(item)
                }
                return
            }
            downloadFile(file!!, singleWriterMultipleReaderFile, connection)
            // Only mark as done if download succeeded (no exception thrown)
            worker.addDone(item)
        } catch (_: SocketTimeoutException) {
            worker.addError(item, context.getString(R.string.requested_timed_out))
        } catch (e: IOException) {
            worker.addError(item, context.getString(R.string.unknown_error_s) + e.toString())
        } catch (e: NullPointerException) {
            worker.addError(item, context.getString(R.string.unknown_error_s) + e.toString())
        } finally {
            connection?.disconnect()
        }
    }

    fun parseUri(uri: String): Uri = Uri.parse(uri)

    @Throws(IOException::class)
    fun getHttpURLConnection(
        file: File,
        singleWriterMultipleReaderFile: SingleWriterMultipleReaderFile,
        url: URL
    ): HttpURLConnection {
        val connection = internalOpenHttpConnection(url)
        connection.apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }

        try {
            singleWriterMultipleReaderFile.openRead().close()
            connection.ifModifiedSince = file.lastModified()
        } catch (_: IOException) {
        }

        connection.connect()
        return connection
    }

    @Throws(IOException::class)
    fun internalOpenHttpConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpsURLConnection

    @Throws(IOException::class)
    fun validateResponse(connection: HttpURLConnection): Boolean {
        Logger.error(
            """
                validateResponse: ${item.title}
                local = ${Date(connection.ifModifiedSince)}
                remote = ${Date(connection.lastModified)}
            """.trimIndent()
        )
        if (connection.responseCode != 200) {
            Logger.error(
                """
                    validateResponse: ${item.title}: Skipping
                    Server responded with ${connection.responseCode} for ${item.data}"
                """.trimIndent()
            )

            if (connection.responseCode == 404) {
                worker.addError(item, context.getString(R.string.file_not_found))
            } else if (connection.responseCode != 304) {
                context.resources.getString(R.string.host_update_error_item)
                worker.addError(
                    item,
                    context.resources.getString(R.string.host_update_error_item) + connection.getResponseCode() + connection.getResponseMessage()
                )
            }
            return false
        }
        return true
    }

    @Throws(IOException::class)
    fun downloadFile(
        file: File,
        singleWriterMultipleReaderFile: SingleWriterMultipleReaderFile,
        connection: HttpURLConnection
    ) {
        val inStream = connection.inputStream
        var outStream: FileOutputStream? = singleWriterMultipleReaderFile.startWrite()
        try {
            copyStream(inStream, outStream!!)

            singleWriterMultipleReaderFile.finishWrite(outStream)
            outStream = null

            if (connection.lastModified == 0L || !file.setLastModified(connection.lastModified)) {
                Logger.error("downloadFile: Could not set last modified")
            }
        } finally {
            if (outStream != null) {
                singleWriterMultipleReaderFile.failWrite(outStream)
            }
        }
    }

    @Throws(IOException::class)
    fun copyStream(inStream: InputStream, outStream: OutputStream) {
        val buffer = ByteArray(4096)
        var rc = inStream.read(buffer)
        while (rc != -1) {
            outStream.write(buffer, 0, rc)
            rc = inStream.read(buffer)
        }
    }
}