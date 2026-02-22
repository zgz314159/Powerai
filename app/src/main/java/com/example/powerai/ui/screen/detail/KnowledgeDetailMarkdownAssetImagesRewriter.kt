package com.example.powerai.ui.screen.detail

import android.content.Context
import android.util.Log

internal object KnowledgeDetailMarkdownAssetImagesRewriter {
    // Find image-like asset paths and normalize them to file:///android_asset/images/[filename]
    private val assetImagePattern = Regex(
        "(?:file:///android_asset/images/|file:///android_asset/src/main/assets/images/|/android_asset/images/|assets/images/|images/)([\\w\\-./%]+?\\.(?:png|jpg|jpeg|webp|gif|png|x-emf))",
        RegexOption.IGNORE_CASE
    )

    fun rewrite(markdown: String, context: Context): AssetImageRewriteResult {
        val matches = assetImagePattern.findAll(markdown).toList()
        if (matches.isEmpty()) {
            return AssetImageRewriteResult(
                text = markdown,
                candidateCount = 0,
                replacedCount = 0,
                firstConverted = null
            )
        }

        val assetFilesLower = KnowledgeDetailMarkdownAssetImagesRewriteHelpers.listAssetImagesLower(context)

        val sb = StringBuilder()
        var lastIndex = 0
        var replaced = 0
        var firstConverted: String? = null

        for (m in matches) {
            val start = m.range.first
            val end = m.range.last + 1
            sb.append(markdown.substring(lastIndex, start))

            val filename = KnowledgeDetailMarkdownAssetImagesRewriteHelpers.extractFilename(m.groupValues[1])

            if (KnowledgeDetailMarkdownAssetImagesRewriteHelpers.isAlreadyInsideMarkdownImage(markdown, start)) {
                sb.append(markdown.substring(start, end))
                lastIndex = end
                continue
            }

            val exists = assetFilesLower.contains(filename.lowercase())

            val replacedText = if (exists) {
                replaced++
                val candidate = "file:///android_asset/images/$filename"
                KnowledgeDetailMarkdownAssetImagesRewriteHelpers.buildReplacementText(sb, markdown, end, "![img]($candidate)")
            } else {
                val alt1 = "images/$filename"
                val alt2 = "android.resource://${context.packageName}/assets/images/$filename"

                // If asset listing failed earlier, still try to be helpful and produce canonical path
                replaced++
                val candidate = "file:///android_asset/images/$filename"

                Log.w(
                    "KnowledgeDetailScreen",
                    "convertAssetPathsToImages: asset not found in list: images/$filename — emitting canonical + alt hints"
                )

                KnowledgeDetailMarkdownAssetImagesRewriteHelpers.buildReplacementText(
                    sb,
                    markdown,
                    end,
                    "![img]($candidate)<!-- alt:$alt1|$alt2 -->"
                )
            }

            if (firstConverted == null) firstConverted = replacedText
            sb.append(replacedText)
            lastIndex = end
        }

        sb.append(markdown.substring(lastIndex))

        return AssetImageRewriteResult(
            text = sb.toString(),
            candidateCount = matches.size,
            replacedCount = replaced,
            firstConverted = firstConverted
        )
    }
}
