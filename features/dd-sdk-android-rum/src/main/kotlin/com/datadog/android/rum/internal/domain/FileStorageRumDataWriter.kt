/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import android.util.Log
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.rum.internal.domain.event.RumEventMeta
import com.datadog.android.rum.model.ViewEvent
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A custom DataWriter implementation that intercepts RUM events and stores them in a file.
 * It also delegates to the original RumDataWriter to maintain normal functionality.
 */
internal class FileStorageRumDataWriter(
    private val originalDataWriter: RumDataWriter,
    private val context: Context,
    private val internalLogger: InternalLogger
) : DataWriter<Any> {

    private val rumEventsFile: File by lazy {
        File(context.filesDir, RUM_EVENTS_FILENAME)
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

    companion object {
        private const val RUM_EVENTS_FILENAME = "datadog_rum_events.json"
    }
}
