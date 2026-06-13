package com.mamvid.kiosko.core.utils

import android.content.Context
import android.util.Log
import com.mamvid.kiosko.core.config.AppConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Logger {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileNameFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val logDir = File(context.filesDir, "logs")
            logDir.mkdirs()
            val today = fileNameFormat.format(Date())
            logFile = File(logDir, "kiosko_$today.log")
            cleanOldLogs(logDir)
        } catch (e: Exception) {
            Log.e(AppConfig.LOG_TAG, "Logger init failed", e)
        }
    }

    fun d(tag: String, message: String) {
        Log.d("${AppConfig.LOG_TAG}/$tag", message)
        write("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i("${AppConfig.LOG_TAG}/$tag", message)
        write("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("${AppConfig.LOG_TAG}/$tag", message, throwable)
        write("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("${AppConfig.LOG_TAG}/$tag", message, throwable)
        write("E", tag, message, throwable)
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = dateFormat.format(Date())
            val stackTrace = throwable?.let { "\n${it.stackTraceToString()}" } ?: ""
            val line = "$timestamp [$level] $tag: $message$stackTrace\n"
            logFile?.appendText(line)
        } catch (_: Exception) {}
    }

    private fun cleanOldLogs(logDir: File) {
        val logs = logDir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        if (logs.size > AppConfig.MAX_LOG_FILES) {
            logs.drop(AppConfig.MAX_LOG_FILES).forEach { it.delete() }
        }
    }
}
