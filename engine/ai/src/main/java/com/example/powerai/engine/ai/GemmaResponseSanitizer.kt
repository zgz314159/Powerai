package com.example.powerai.engine.ai

import android.content.Context

object GemmaResponseSanitizer {
    fun isRepetitive(text: String): Boolean {
        try {
            val lastPart = if (text.length > 30) text.takeLast(30) else text
            val pattern = Regex("(\\d+kV|221|222)|(\\d)\\2{2,}")
            val matches = pattern.findAll(lastPart).count()
            if (matches > 3) return true
            val digits = lastPart.count { it.isDigit() }
            if (lastPart.isNotEmpty() && digits.toDouble() / lastPart.length > 0.5) return true
        } catch (_: Throwable) {}
        return false
    }

    fun cleanFinalText(input: String?): Pair<String, Boolean> {
        if (input == null) return Pair("", false)
        try {
            var text = input
            val repRun = Regex("(?:\\?21kV){5,}|(?:21kV){5,}|(?:([0-9])\\1{10,})")
            val m = repRun.find(text)
            if (m != null) {
                val idx = m.range.first
                if (idx > 0) {
                    text = text.substring(0, idx)
                    return Pair(text + "\n[系统已截断：检测到复读]\n", true)
                } else {
                    return Pair("[系统已截断：输出以复读开始]\n", true)
                }
            }
            val maxLen = 3000
            if (text.length > maxLen) {
                return Pair(text.take(maxLen) + "\n[系统已截断：输出过长]\n", true)
            }
            return Pair(text, false)
        } catch (_: Throwable) {}
        return Pair(input ?: "", false)
    }

    fun tokenCollapseDetected(accumulated: String, part: String?): Boolean {
        try {
            if (part == null) return false
            val combined = (accumulated + part).let { if (it.length > 50) it.takeLast(50) else it }
            if (combined.isEmpty()) return false
            val kvCount = Regex("kV", RegexOption.IGNORE_CASE).findAll(combined).count()
            if (kvCount >= 3) return true
            val rep5 = Regex("(.{5})\\1\\1")
            if (rep5.containsMatchIn(combined)) return true
        } catch (_: Throwable) {}
        return false
    }

    fun writeEmergencyStop(context: Context?) {
        try {
            val safeContext = context ?: return
            val filesDir = safeContext.filesDir ?: return
            val resultFile = java.io.File(filesDir, "last_res.txt")
            val fos = java.io.FileOutputStream(resultFile, true)
            try {
                val marker = "\n[[EMERGENCY_SHUTDOWN_TRIGGERED_AT_21KV]]\n"
                fos.write(marker.toByteArray(Charsets.UTF_8))
                fos.flush()
                try { fos.fd.sync() } catch (_: Throwable) { }
            } finally {
                try { fos.close() } catch (_: Throwable) { }
            }
            try {
                val extDir = safeContext.getExternalFilesDir(null)
                if (resultFile.exists() && extDir != null) {
                    val dst = java.io.File(extDir, "1b_final_CRASHED.txt")
                    try {
                        val ins = java.io.FileInputStream(resultFile)
                        try {
                            val out = java.io.FileOutputStream(dst, false)
                            try {
                                ins.copyTo(out)
                                out.fd.sync()
                            } finally { try { out.close() } catch (_: Throwable) {} }
                        } finally { try { ins.close() } catch (_: Throwable) {} }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) { }
    }
}
