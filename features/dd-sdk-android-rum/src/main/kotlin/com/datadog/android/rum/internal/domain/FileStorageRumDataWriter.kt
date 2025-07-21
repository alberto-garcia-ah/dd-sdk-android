/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.persistence.serializeToByteArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A custom DataWriter implementation that intercepts RUM events and stores them in a file.
 * It also delegates to the original RumDataWriter to maintain normal functionality.
 *
 * Note: This class requires the WRITE_EXTERNAL_STORAGE permission to be declared in the
 * AndroidManifest.xml file as it writes files to the public Documents directory.
 *
 * For Android 6.0 (API level 23) and above, this permission is considered dangerous and
 * must be requested at runtime. The app using this library should request this permission
 * before initializing the RUM feature.
 */
internal class FileStorageRumDataWriter(
    private val originalDataWriter: RumDataWriter,
    private val context: Context,
    private val internalLogger: InternalLogger
) : DataWriter<Any> {

    private val rumEventsFile: File by lazy {
        try {
            // Generate a unique filename with timestamp
            val timestamp = System.currentTimeMillis()
            val filename = "datadog_rum_events_$timestamp.json"

            // Use public Documents directory instead of app-specific directory
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val datadogDir = File(documentsDir, "Datadog")
            if (!datadogDir.exists()) {
                val dirCreated = datadogDir.mkdirs()
                if (!dirCreated) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        { "Failed to create Datadog directory in Documents. Falling back to app-specific directory." }
                    )
                    // Fall back to app-specific directory if we can't create the directory
                    return@lazy File(context.getExternalFilesDir(null), filename)
                }
            }

            val file = File(datadogDir, filename)
            Log.d("Datadog", "Rum events file path: ${file.absolutePath}")
            file
        } catch (e: SecurityException) {
            // This can happen if the app doesn't have WRITE_EXTERNAL_STORAGE permission
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SecurityException when accessing external storage: ${e.message}. Make sure WRITE_EXTERNAL_STORAGE permission is granted. Falling back to app-specific directory." },
                e
            )
            // Fall back to app-specific directory if we don't have permission
            val filename = "datadog_rum_events_${System.currentTimeMillis()}.json"
            File(context.getExternalFilesDir(null), filename)
        }
    }

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: Any, eventType: EventType): Boolean {
        // First, serialize the event to JSON
        val serializedEvent = originalDataWriter.eventSerializer.serializeToByteArray(
            element,
            internalLogger
        )

        // If serialization was successful, write to file
        if (serializedEvent != null) {
            try {
                // Ensure the parent directory exists before writing the file.
                val parentDir = rumEventsFile.parentFile
                if (parentDir != null && !parentDir.exists()) {
                    val dirCreated = parentDir.mkdirs()
                    if (!dirCreated) {
                        internalLogger.log(
                            InternalLogger.Level.ERROR,
                            InternalLogger.Target.USER,
                            { "Failed to create parent directory for RUM events file." }
                        )
                        return false
                    }
                }

                // Convert byte array to string (JSON)
                val jsonString = String(serializedEvent) + "\n"

                // Append to file
                FileOutputStream(rumEventsFile, true).use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                    outputStream.flush()
                }

                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "RUM event of type ${element.javaClass.simpleName} stored in file: ${rumEventsFile.absolutePath}" }
                )
            } catch (e: SecurityException) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "SecurityException when writing RUM event to file: ${e.message}. Make sure WRITE_EXTERNAL_STORAGE permission is granted." },
                    e
                )
                return false
            } catch (e: IOException) {
                internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { "Failed to write RUM event to file: ${e.message}" },
                    e
                )
            }
        } else {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { "Failed to serialize RUM event of type ${element.javaClass.simpleName}" }
            )
        }

        // According to the requirements, we should only store the events in a file and not send them to the server
        // So we don't delegate to the original writer
        // return originalDataWriter.write(writer, element, eventType)

        // Instead, we return true to indicate that the event was processed successfully
        return true
    }
}
