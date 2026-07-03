package com.example

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic-only crash logger.
 *
 * Catches any uncaught exception, writes the full stack trace to a plain-text
 * file in the public Downloads folder (visible in any Files app, no computer
 * or adb needed), then hands off to the previous default handler so the app
 * still crashes/closes exactly as before. This is meant to be removed once
 * the underlying bug is found and fixed.
 */
class CrashLogger(
    private val context: Context,
    private val previousHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashLog(throwable)
        } catch (loggingError: Throwable) {
            // Never let the logger itself cause a secondary crash or an infinite loop.
            Log.e("CrashLogger", "Failed to write crash log", loggingError)
        } finally {
            // Preserve normal crash behavior (app still closes as before).
            previousHandler?.uncaughtException(thread, throwable)
                ?: run {
                    Runtime.getRuntime().exit(10)
                }
        }
    }

    private fun writeCrashLog(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
        val fileName = "screenpulse_crash_$timestamp.txt"
        val content = buildString {
            appendLine("ScreenPulse crash log")
            appendLine("Time: $timestamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine(sw.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Scoped storage: writing an app-created file into the public
            // Downloads collection via MediaStore needs no extra permission.
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(content.toByteArray())
                }
            }
        } else {
            // Pre-scoped-storage fallback (Android 9 and below).
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { out ->
                out.write(content.toByteArray())
            }
        }

        // Always also write a copy to app-private storage as a backup
        // (no permission needed on any Android version).
        val privateFile = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(privateFile).use { out ->
            out.write(content.toByteArray())
        }
    }
}
