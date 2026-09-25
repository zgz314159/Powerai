package com.example.powerai.engine.ai

import android.content.Context
import org.json.JSONArray

/**
 * Component responsible for scanning the app's assets/files and producing the
 * list of knowledge entries used by [SparseSearcher].
 *
 * Splitting this behaviour out makes it easier to test the matching logic in
 * isolation by injecting a fake loader, and also isolates the bulky JSON parsing
 * and asset traversal from the search routines.
 */
interface SparseSearcherLoader {
    fun loadEntries(context: Context?): List<SparseSearcher.SafetyEntry>
}

/**
 * Default implementation that mirrors the previous `init { ... }` block in
 * [SparseSearcher].  Performs asset traversal with fallbacks to built-in data.
 */
class DefaultSparseSearcherLoader : SparseSearcherLoader {
    override fun loadEntries(context: Context?): List<SparseSearcher.SafetyEntry> {
        // logic moved verbatim from original constructor
        fun mapFilenameToTitle(filename: String): String {
            val fname = filename.lowercase()
            val known = mapOf(
                "power_safety_rules.json" to "电力安全工作规程",
                "railway_power_lines.json" to "电力线路工必知必会手册",
                "铁路电力线路工.json" to "电力线路工必知必会手册"
            )
            for ((k, v) in known) {
                if (fname.contains(k)) return v
            }
            if (fname.contains("rail") || fname.contains("铁路") || fname.contains("线路")) return "电力线路工必知必会手册"
            if (fname.contains("power") || fname.contains("safety") || fname.contains("安全")) return "电力安全工作规程"
            return filename
        }

        return try {
            val root = "kb/铁路/专业知识"
            val found = mutableListOf<SparseSearcher.SafetyEntry>()
            fun scanAssets(path: String) {
                try {
                    val list = context?.assets?.list(path) ?: return
                    for (name in list) {
                        val childPath = if (path.isBlank()) name else "$path/$name"
                        val sub = context?.assets?.list(childPath)
                        if (sub != null && sub.isNotEmpty()) {
                            scanAssets(childPath)
                        } else {
                            if (name.equals("knowledge_base.json", ignoreCase = true)) {
                                try {
                                    val txt = context.assets.open(childPath).bufferedReader(Charsets.UTF_8).use { it.readText() }
                                    val arr = JSONArray(txt)
                                    for (i in 0 until arr.length()) {
                                        val o = arr.optJSONObject(i) ?: continue
                                        val title = o.optString("title", o.optString("key", "")).trim()
                                        val content = o.optString("content", "").trim()
                                        if (content.isNotBlank()) {
                                            val srcField = o.optString("source", "").trim()
                                            val fileName = childPath.substringAfterLast('/')
                                            val srcDisplay = if (srcField.isNotBlank()) srcField else mapFilenameToTitle(fileName)
                                            found.add(SparseSearcher.SafetyEntry(title.ifBlank { "(no-title)" }, content, srcDisplay))
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }

            try { scanAssets(root) } catch (_: Throwable) {}

            if (found.isEmpty()) {
                val fallback = try { context?.assets?.open("power_safety_rules.json")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } } catch (_: Throwable) { null }
                if (!fallback.isNullOrBlank()) {
                    val arr = JSONArray(fallback)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val title = o.optString("key", "").trim()
                        val content = o.optString("content", "").trim()
                        if (content.isNotBlank()) {
                            val srcDisplay = mapFilenameToTitle("power_safety_rules.json")
                            found.add(SparseSearcher.SafetyEntry(title.ifBlank { "(no-title)" }, content, srcDisplay))
                        }
                    }
                } else {
                    try {
                        val f = context?.filesDir?.resolve("power_safety_rules.json")
                        if (f != null && f.exists()) {
                            val arr = JSONArray(f.readText(Charsets.UTF_8))
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val title = o.optString("key", "").trim()
                                val content = o.optString("content", "").trim()
                                if (content.isNotBlank()) {
                                    val srcDisplay = mapFilenameToTitle(f.name)
                                    found.add(SparseSearcher.SafetyEntry(title.ifBlank { "(no-title)" }, content, srcDisplay))
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }

            if (found.isNotEmpty()) found else listOf(
                SparseSearcher.SafetyEntry("221kV", "221kV验电应使用相应电压等级且合格的接触式验电器。", "builtin"),
                SparseSearcher.SafetyEntry("验电", "验电必须遵守分级、戴绝缘手套并使用绝缘工具。", "builtin")
            )
        } catch (_: Throwable) {
            listOf(SparseSearcher.SafetyEntry("221kV", "221kV验电应使用相应电压等级且合格的接触式验电器。", "builtin"))
        }
    }
}