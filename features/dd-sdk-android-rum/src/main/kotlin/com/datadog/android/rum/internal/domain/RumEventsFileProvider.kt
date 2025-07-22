/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import android.os.Environment
import com.datadog.android.api.InternalLogger
import java.io.File

/**
 * A provider for RUM events file name and location.
 * This class is responsible for creating the file with the proper name based on the testMethodName
 * or a default name if not provided.
 */
internal class RumEventsFileProvider(
    private val context: Context,
    private val internalLogger: InternalLogger,
    private val testMethodName: String? = null
) {

    /**
     * Creates and returns a File object for storing RUM events.
     * If testMethodName is provided, it will be used as the file name with .jsonl extension.
     * Otherwise, a default name "datadog_rum_events.jsonl" will be used.
     *
     * @return a File object for storing RUM events
     */
    fun getRumEventsFile(): File {
        try {
            // Generate filename based on testMethodName or use default
            val filename = if (!testMethodName.isNullOrBlank()) {
                "$testMethodName.jsonl"
            } else {
                "datadog_rum_events.jsonl"
            }

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
                    return File(context.getExternalFilesDir(null), filename)
                }
            }

            val file = File(datadogDir, filename)

            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { "Rum events file path: ${file.absolutePath}" }
            )
            return file
        } catch (e: SecurityException) {
            // This can happen if the app doesn't have WRITE_EXTERNAL_STORAGE permission
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SecurityException when accessing external storage: ${e.message}. Make sure WRITE_EXTERNAL_STORAGE permission is granted. Falling back to app-specific directory." },
                e
            )
            // Fall back to app-specific directory if we don't have permission
            val filename = if (!testMethodName.isNullOrBlank()) {
                "$testMethodName.jsonl"
            } else {
                "datadog_rum_events.jsonl"
            }
            return File(context.getExternalFilesDir(null), filename)
        }
    }
}