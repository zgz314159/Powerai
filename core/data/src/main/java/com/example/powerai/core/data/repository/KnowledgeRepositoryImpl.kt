package com.example.powerai.core.data.repository

import android.content.Context
import android.net.Uri
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.ImportedFileEntity as ImportedFileEntityLocal
import com.example.powerai.core.data.mapper.KnowledgeEntityMapper
import com.example.powerai.core.model.util.BlocksTextExtractor
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.LocalHighlightTarget
import com.example.powerai.core.model.ImportedFile
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.core.repository.EmbeddingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import javax.inject.Inject

class KnowledgeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: KnowledgeDao,
    private val embeddingRepository: EmbeddingRepository
) : KnowledgeRepository {

    private fun entityToItem(e: KnowledgeEntity): KnowledgeItem =
        KnowledgeEntityMapper.toItem(e)

    override suspend fun searchLocal(query: String): List<KnowledgeItem> {
        return KnowledgeLocalSearch.searchLocal(
            dao = dao,
            query = query,
            entityToItem = ::entityToItem
        )
    }

    override suspend fun getLocalItemById(id: Long): KnowledgeItem? {
        if (id <= 0L) return null
        return dao.getById(id)?.let(::entityToItem)
    }

    override suspend fun importDocuments(uris: List<String>): Result<Unit> {
        try {
            for (s in uris) {
                val uri = Uri.parse(s)
                val input = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Cannot open uri")
                val content = input.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)
                val title = uri.lastPathSegment ?: "imported"
                val entity = KnowledgeEntity(title = title, content = content, source = s)
                dao.insert(entity)
            }
            return Result.success(Unit)
        } catch (t: Throwable) {
            return Result.failure(t)
        }
    }

    override suspend fun insertBatch(items: List<KnowledgeItem>) {
        val entities = items.map { item ->
            KnowledgeEntityMapper.toEntity(item)
        }
        dao.insertBatch(entities)
        try {
            dao.rebuildFts()
        } catch (_: Throwable) {}

        try {
            embeddingRepository.enqueueForEmbedding(items)
        } catch (_: Throwable) {}
    }

    override suspend fun isFileImported(fileId: String): Boolean {
        return dao.importedFileExists(fileId) > 0
    }

    override suspend fun markFileImported(fileId: String, fileName: String, timestamp: Long, status: String) {
        dao.insertImportedFile(ImportedFileEntityLocal(fileId = fileId, fileName = fileName, timestamp = timestamp, status = status))
    }

    override suspend fun resolveHighlightTarget(itemId: Long, highlight: String): LocalHighlightTarget? {
        if (itemId <= 0 || highlight.isBlank()) return null
        val entity = dao.getById(itemId) ?: return null
        val blocksJson = entity.contentBlocksJson?.takeIf { it.isNotBlank() } ?: return null
        val target = BlocksTextExtractor.findBestMatchingBlockTarget(
            blocksJson = blocksJson,
            highlight = highlight,
            fallbackContext = buildString {
                append(entity.title)
                append(' ')
                append(entity.source)
            }
        )
        val blockIndex = target?.index
        val blockId = target?.id
        if (blockIndex == null && blockId.isNullOrBlank()) return null
        return LocalHighlightTarget(blockIndex = blockIndex, blockId = blockId)
    }

    override suspend fun countKnowledgeByPage(fileId: String, page: Int): Int {
        return dao.countByPage(fileId, page)
    }

    override suspend fun getItemsByPage(fileId: String, page: Int): List<KnowledgeItem> {
        return dao.getByPage(fileId, page).map(::entityToItem)
    }

    override suspend fun getImportedFiles(): List<ImportedFile> {
        return dao.getImportedFiles().map {
            ImportedFile(
                fileId = it.fileId,
                fileName = it.fileName,
                timestamp = it.timestamp,
                status = it.status
            )
        }
    }

    override suspend fun getAll(): List<KnowledgeItem> {
        return dao.getAll().map(::entityToItem)
    }

    override suspend fun searchByKeyword(query: String): List<KnowledgeItem> {
        return dao.searchByKeywordInContent(query).map(::entityToItem)
    }
}
