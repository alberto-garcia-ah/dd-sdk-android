/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentUris
import com.datadog.android.api.InternalLogger
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.InputStreamReader
import java.io.IOException

/**
 * A provider for RUM events file name and location.
 * This class is responsible for creating the file with the proper name based on the testMethodName
 * or a default name if not provided.
 */
internal class PerformanceTestConfigProvider(
    private val context: Context,
    private val internalLogger: InternalLogger,
) {

    /**
     * Reads the performance test configuration from
     * /storage/emulated/0/Documents/Datadog/performance_test_device_config.json,
     * logs the result, and returns the parsed configuration.
     */
    private fun loadPerformanceTestConfig(): PerformanceTestConfig? {
        val fileName = "performance_test_device_config.json"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val downloadsUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME
                )
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)

                context.contentResolver.query(downloadsUri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    return if (cursor.moveToFirst()) {
                        val id = cursor.getLong(idIndex)
                        val contentUri = ContentUris.withAppendedId(downloadsUri, id)
                        context.contentResolver.openInputStream(contentUri)?.use { inputStream ->
                            InputStreamReader(inputStream).use { reader ->
                                val config = Gson().fromJson(reader, PerformanceTestConfig::class.java)
                                internalLogger.log(
                                    InternalLogger.Level.INFO,
                                    InternalLogger.Target.USER,
                                    { "Loaded performance test config MediaStore: methodName=${config.methodName}, mode=${config.mode}" }
                                )
                                config
                            }
                        }
                    } else {
                        internalLogger.log(
                            InternalLogger.Level.INFO,
                            InternalLogger.Target.USER,
                            { "Performance test config not found in Downloads via MediaStore: $fileName" }
                        )
                        null
                    }
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val configFile = File(downloadsDir, fileName)
                if (!configFile.exists()) {
                    internalLogger.log(
                        InternalLogger.Level.INFO,
                        InternalLogger.Target.USER,
                        { "Performance test config not found at: ${configFile.absolutePath}" }
                    )
                    null
                } else {
                    configFile.inputStream().use { inputStream ->
                        InputStreamReader(inputStream).use { reader ->
                            val config = Gson().fromJson(reader, PerformanceTestConfig::class.java)
                            internalLogger.log(
                                InternalLogger.Level.INFO,
                                InternalLogger.Target.USER,
                                { "Loaded performance test config: methodName=${config.methodName}, mode=${config.mode}" }
                            )
                            config
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SecurityException when reading performance test config: ${e.message}" },
                e
            )
            null
        } catch (e: JsonSyntaxException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "Invalid JSON in performance test config: ${e.message}" },
                e
            )
            null
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "IOException when reading performance test config: ${e.message}" },
                e
            )
            null
        }
    }

    /**
     * Creates and returns a File object for storing RUM events.
     * If testMethodName is provided, it will be used as the file name with .jsonl extension.
     * Otherwise, a default name "datadog_rum_events.jsonl" will be used.
     *
     * @return a File object for storing RUM events
     */
    fun getRumEventsFile(): File? {
        try {
            val config = loadPerformanceTestConfig()
            if (config == null) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "Performance test config not found." }
                )
                return null
            }

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                val dirCreated = downloadsDir.mkdirs()
                if (!dirCreated) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        { "Failed to access Downloads directory." }
                    )
                    return null
                }
            }

            val fileName = "${config.methodName}-${config.mode}.jsonl"
            val file = File(downloadsDir, fileName)

            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { "Rum events file path: ${file.absolutePath}" }
            )
            return file

        } catch (e: SecurityException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SecurityException when accessing external storage: ${e.message}. Make sure WRITE_EXTERNAL_STORAGE permission is granted." },
                e
            )
            return null
        }
    }
}
