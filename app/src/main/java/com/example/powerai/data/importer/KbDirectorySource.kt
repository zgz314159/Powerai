package com.example.powerai.data.importer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream

/** File name of the PaddleModels KB contract this flow accepts. */
const val KB_JSON_FILE_NAME = "knowledge_base.json"

/**
 * Read-only access to a user-picked knowledge-base output directory. Abstracted so the production
 * SAF `OpenDocumentTree` grant and a plain [File] directory (tests, app-private copies) share a
 * single import implementation.
 */
interface KbDirectorySource {
    /** Stable identity of the directory; used to derive an isolated package id. */
    val identity: String

    /** Human-readable directory name for diagnostics. */
    val displayName: String

    /** Opens [relativePath] under the directory root, or null when it is absent/unreadable. */
    fun open(relativePath: String): InputStream?
}

/**
 * SAF tree-backed source. Child documents are addressed through
 * [DocumentsContract.buildDocumentUriUsingTree], so no expensive directory listing is needed and
 * only the resources the KB actually references are opened.
 */
class SafKbDirectorySource(
    private val context: Context,
    private val treeUri: Uri,
) : KbDirectorySource {
    override val identity: String = treeUri.toString()

    override val displayName: String =
        runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrDefault("")
            .substringAfterLast('/')
            .substringAfterLast(':')
            .ifBlank { "selected-directory" }

    override fun open(relativePath: String): InputStream? {
        val parentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return null
        val childId = "$parentId/$relativePath"
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }
}

/** Plain filesystem directory source (tests, app-private or externally mounted copies). */
class FileKbDirectorySource(private val root: File) : KbDirectorySource {
    override val identity: String = root.canonicalPath

    override val displayName: String = root.name.ifBlank { "kb-directory" }

    override fun open(relativePath: String): InputStream? {
        val file = File(root, relativePath)
        return if (file.isFile) file.inputStream() else null
    }
}
