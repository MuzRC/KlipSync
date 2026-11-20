package com.muzrc.klipsync.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExportDebugLogger {

    private const val LOG_FILE_NAME = "klipsync_export_debug.log"
    private const val MAX_LOG_CHARS = 45_000

    private val _logsFlow = MutableStateFlow("Tiada rekod log eksport lagi.")
    val logsFlow: StateFlow<String> = _logsFlow.asStateFlow()

    private var appContext: Context? = null
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
        reloadFromDisk()
    }

    private fun getLogFile(): File? {
        val ctx = appContext ?: return null
        return File(ctx.filesDir, LOG_FILE_NAME)
    }

    private fun reloadFromDisk() {
        val file = getLogFile() ?: return
        if (file.exists()) {
            val content = try {
                file.readText()
            } catch (_: Exception) {
                ""
            }
            _logsFlow.value = content.ifBlank { "Tiada rekod log eksport lagi." }
        }
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val ts = timeFormat.format(Date())
        val line = "[$ts] [$tag] $message\n"
        appendRaw(line)
    }

    @Synchronized
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val ts = timeFormat.format(Date())
        val sb = StringBuilder()
        sb.append("[$ts] [ERROR][$tag] $message\n")
        if (throwable != null) {
            sb.append("--- STACK TRACE ---\n")
            sb.append(Log.getStackTraceString(throwable))
            sb.append("-------------------\n")
        }
        appendRaw(sb.toString())
    }

    fun deviceHeader(): String {
        return "Peranti: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private fun appendRaw(text: String) {
        val file = getLogFile()
        if (file != null) {
            try {
                val existing = if (file.exists()) file.readText() else ""
                val combined = (existing + text).takeLast(MAX_LOG_CHARS)
                file.writeText(combined)
                _logsFlow.value = combined
                return
            } catch (_: Exception) {
            }
        }
        val fallback = (_logsFlow.value + "\n" + text).takeLast(MAX_LOG_CHARS)
        _logsFlow.value = fallback
    }

    @Synchronized
    fun clear(context: Context? = null) {
        if (context != null && appContext == null) {
            appContext = context.applicationContext
        }
        val file = getLogFile()
        try {
            if (file != null && file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
        _logsFlow.value = "Log telah dikosongkan."
    }
}
