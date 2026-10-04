package com.example.powerai.data.importer

import android.net.Uri
import java.io.File

/**
 * Copies the resources a user KB references (e.g. the PNG files under `shots/`) out of the picked
 * directory into an app-private, content-addressed version directory, and returns a stable
 * `file://` URI for each reference.
 *
 * It runs inside the package's DB transaction and copies lazily, on the first reference: a missing
 * or unsafe reference throws, which aborts the transaction, and no URI is ever returned before the
 * file exists on disk. A committed row therefore can never point at a missing image, even after the
 * source directory goes away.
 */
internal class UserKbAssetMaterializer(
    private val source: KbDirectorySource,
    private val versionDir: File,
) {
    private val copied = HashSet<String>()

    /** Number of distinct resources physically written into the mirror. */
    var copiedCount: Int = 0
        private set

    /** Validate + copy [rawRef]; throws [IllegalArgumentException] on an unsafe or missing resource. */
    fun materialize(rawRef: String): String {
        val relative =
            when (val result = KbResourcePath.validate(rawRef)) {
                is KbResourcePath.Result.Valid -> result.path
                is KbResourcePath.Result.Invalid ->
                    throw IllegalArgumentException("非法资源路径：$rawRef（${result.reason}）")
            }

        val destination = File(versionDir, relative)
        ensureInside(versionDir, destination)

        if (copied.add(relative)) {
            val input =
                source.open(relative)
                    ?: throw IllegalArgumentException("缺少必需资源：$relative")
            input.use { stream ->
                destination.parentFile?.mkdirs()
                destination.outputStream().use { out -> stream.copyTo(out) }
            }
            copiedCount++
        }
        return Uri.fromFile(destination).toString()
    }

    private fun ensureInside(
        root: File,
        file: File,
    ) {
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath
        require(filePath == rootPath || filePath.startsWith(rootPath + File.separator)) {
            "资源路径越界：${file.path}"
        }
    }
}
