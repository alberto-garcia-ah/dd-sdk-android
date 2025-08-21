/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import android.content.Context
import android.os.Environment
import com.datadog.android.api.InternalLogger
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.File
import java.io.FileReader
import java.io.IOException

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

    internal data class PerformanceTestConfig(
        val methodName: String? = null,
        val mode: String? = null
    )

    /**
     * Reads the performance test configuration from
     * /storage/emulated/0/Documents/Datadog/performance_test_device_config.json,
     * logs the result, and returns the parsed configuration.
     */
    fun loadPerformaceTestConfig(): PerformanceTestConfig? {
        return try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val datadogDir = File(documentsDir, "Datadog")
            val configFile = File(datadogDir, "performance_test_device_config.json")

            if (!configFile.exists()) {
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Performance test config not found at: ${configFile.absolutePath}" }
                )
                null
            } else {
                FileReader(configFile).use { reader ->
                    val config = Gson().fromJson(reader, PerformanceTestConfig::class.java)
                    internalLogger.log(
                        InternalLogger.Level.INFO,
                        InternalLogger.Target.USER,
                        { "Loaded performance test config: methodName=${config?.methodName}, mode=${config?.mode}" }
                    )
                    config
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
    fun getRumEventsFile(): File {
        try {
            // Use public Documents directory instead of app-specific directory
            // Load and log performance test configuration (if present)
            loadPerformaceTestConfig()

            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val datadogDir = File(documentsDir, "Datadog")

            // Create Datadog directory if it doesn't exist
            if (!datadogDir.exists()) {
                val dirCreated = datadogDir.mkdirs()
                if (!dirCreated) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.USER,
                        { "Failed to create Datadog directory in Documents. Falling back to app-specific directory." }
                    )
                    // Fall back to app-specific directory if we can't create the directory
                    return createAppSpecificFile()
                }
            }



            // Generate filename based on testMethodName or use default
            if (!testMethodName.isNullOrBlank()) {
                val baselineFilename = "$testMethodName-baseline.jsonl"
                val baselineFile = File(datadogDir, baselineFilename)

                val filename = if (baselineFile.exists()) {
                    "$testMethodName-replay.jsonl"
                } else {
                    baselineFilename
                }

                val file = File(datadogDir, filename)

                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Rum events file path: ${file.absolutePath}" }
                )
                return file
            } else {
                val file = File(datadogDir, "datadog_rum_events.jsonl")

                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Rum events file path: ${file.absolutePath}" }
                )
                return file
            }
        } catch (e: SecurityException) {
            // This can happen if the app doesn't have WRITE_EXTERNAL_STORAGE permission
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "SecurityException when accessing external storage: ${e.message}. Make sure WRITE_EXTERNAL_STORAGE permission is granted. Falling back to app-specific directory." },
                e
            )
            // Fall back to app-specific directory if we don't have permission
            return createAppSpecificFile()
        }
    }

    /**
     * Creates a file in the app-specific directory with the appropriate naming strategy.
     * This is used as a fallback when the public Documents directory is not accessible.
     *
     * @return a File object for storing RUM events in the app-specific directory
     */
    private fun createAppSpecificFile(): File {
        if (!testMethodName.isNullOrBlank()) {
            val baselineFilename = "$testMethodName-baseline.jsonl"
            val baselineFile = File(context.getExternalFilesDir(null), baselineFilename)

            return if (baselineFile.exists()) {
                val replayFile = File(context.getExternalFilesDir(null), "$testMethodName-replay.jsonl")
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Rum events file path: ${replayFile.absolutePath}" }
                )
                replayFile
            } else {
                internalLogger.log(
                    InternalLogger.Level.INFO,
                    InternalLogger.Target.USER,
                    { "Rum events file path: ${baselineFile.absolutePath}" }
                )
                baselineFile
            }
        } else {
            val file = File(context.getExternalFilesDir(null), "datadog_rum_events.jsonl")
            internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                { "Rum events file path: ${file.absolutePath}" }
            )
            return file
        }
    }
}
