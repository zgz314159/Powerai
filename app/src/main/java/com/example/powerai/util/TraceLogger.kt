package com.example.powerai.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TraceLogger {
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun append(context: Context, tag: String, message: String) {
        try {
            val dir = context.filesDir
            val file = File(dir, "import-trace.txt")
            val ts = sdf.format(Date())
            FileWriter(file, true).use { fw ->
                fw.append("[$ts] [$tag] ")
                fw.append(message)
                fw.append('\n')
            }
        } catch (_: Throwable) {
            // best-effort only
        }
    }
}
