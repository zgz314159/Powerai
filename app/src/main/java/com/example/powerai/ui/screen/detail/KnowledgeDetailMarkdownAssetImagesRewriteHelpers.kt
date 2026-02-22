package com.example.powerai.ui.screen.detail

import android.content.Context
import android.util.Log

internal object KnowledgeDetailMarkdownAssetImagesRewriteHelpers {
    fun listAssetImagesLower(context: Context): Set<String> {
        return try {
            context.assets.list("images")?.map { it.lowercase() }?.toSet() ?: emptySet()
        } catch (t: Throwable) {
            Log.w("KnowledgeDetailScreen", "convertAssetPathsToImages: failed to list assets/images", t)
            emptySet()
        }
    }

    fun extractFilename(rawPath: String): String {
        var path = rawPath
        // remove any src/main/assets fragments and leading slashes
        path = path.replaceFirst(Regex(".*src/main/assets/"), "").trimStart('/')
        return path.split('/').last()
    }

    fun isAlreadyInsideMarkdownImage(markdown: String, start: Int): Boolean {
        val before = markdown.substring(0, start)
        val lastImgStart = before.lastIndexOf("![")
        if (lastImgStart < 0) return false
        val closing = before.indexOf("](", lastImgStart)
        return closing >= 0 && closing < start
    }

    fun buildReplacementText(
        sb: StringBuilder,
        markdown: String,
        end: Int,
        body: String
    ): String {
        // Ensure a blank line before and after image to keep AST correct
        val prefix = if (sb.endsWith("\n\n") || sb.isEmpty()) "" else "\n\n"
        val suffixNext2 = markdown.substring(end, minOf(end + 2, markdown.length))
        val suffix = if (suffixNext2.startsWith("\n\n")) "" else "\n\n"
        return prefix + body + suffix
    }
}
