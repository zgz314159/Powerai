package com.example.powerai.ui.screen.detail

import com.example.powerai.core.data.dao.KnowledgeDao

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.BlocksJsonPatcher
import com.example.powerai.core.model.util.BlocksTextExtractor
import com.example.powerai.domain.util.SemanticRoleSearchTextBuilder
import com.example.powerai.core.model.util.TextSanitizer
import com.example.powerai.core.data.dao.VisionCacheDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.entity.VisionCacheEntity
import com.example.powerai.data.settings.FontSettings
import com.example.powerai.domain.usecase.DatabaseUseCase
import com.example.powerai.domain.vision.VisionBoostUseCase
import com.example.powerai.navigation.Screen
import com.example.powerai.ui.mvi.BaseMviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

sealed class KnowledgeDetailUiState {
    data object Loading : KnowledgeDetailUiState()
    data class Success(
        val entity: KnowledgeEntity,
        val sourceFileName: String?,
        val highlight: String,
        val entryNavigation: com.example.powerai.domain.model.DetailEntryNavigation?,
        val initialBlockIndex: Int?,
        val initialBlockId: String?,
        val visionMarkdownByBlockId: Map<String, String> = emptyMap(),
        val visionBoostingBlockId: String? = null,
        val visionBoostErrorBlockId: String? = null,
        val visionBoostErrorMessage: String? = null,
        val deepLogicValidationEnabled: Boolean = false
    ) : KnowledgeDetailUiState()

    data class NotFound(val id: Long) : KnowledgeDetailUiState()
    data class Error(val message: String) : KnowledgeDetailUiState()
}

sealed class KnowledgeDetailUiEffect {
    data class Toast(val message: String) : KnowledgeDetailUiEffect()
}

@HiltViewModel
class KnowledgeDetailViewModel @Inject constructor(
    private val dao: KnowledgeDao,
    private val visionCacheDao: VisionCacheDao,
    private val databaseUseCase: DatabaseUseCase,
    private val visionBoostUseCase: VisionBoostUseCase,
    private val fontSettings: FontSettings,
    savedStateHandle: SavedStateHandle
) : BaseMviViewModel<DetailIntent, KnowledgeDetailUiState, KnowledgeDetailUiEffect>(
    initialState = KnowledgeDetailUiState.Loading
) {

    private val id: Long = savedStateHandle.get<Long>(Screen.Detail.ARG_ID) ?: -1L
    private val q: String = savedStateHandle.get<String>(Screen.Detail.ARG_Q).orEmpty()
    private val blockIndexArg: Int = savedStateHandle.get<Int>(Screen.Detail.ARG_BLOCK_INDEX) ?: -1
    private val blockIdArg: String = savedStateHandle.get<String>(Screen.Detail.ARG_BLOCK_ID)
        ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.toString()) }
        .orEmpty()

    val detailContentFontScale: StateFlow<Float> = fontSettings.detailContentFontScaleFlow

    override fun onIntent(intent: DetailIntent) {
        when (intent) {
            is DetailIntent.RequestVisionBoost -> requestVisionBoost(intent.entityId, intent.blockId, intent.imageUri)
            is DetailIntent.ApplyVisionBoost -> applyVisionBoostToOriginal(intent.entityId, intent.rawBlockId, intent.cacheKey, intent.clearCacheAfter)
            is DetailIntent.SetDeepLogicValidation -> setDeepLogicValidationEnabled(intent.enabled)
            is DetailIntent.SetDetailContentFontScale -> fontSettings.setDetailContentFontScale(intent.value)
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (id <= 0) {
                    updateState { KnowledgeDetailUiState.NotFound(id) }
                    return@launch
                }
                val entity = dao.getById(id)
                if (entity == null) {
                    updateState { KnowledgeDetailUiState.NotFound(id) }
                } else {
                    val visionCache = try {
                        visionCacheDao.getAllForEntity(entity.id)
                            .filter { it.markdown.isNotBlank() && it.blockId.isNotBlank() }
                            .associate { it.blockId to it.markdown }
                    } catch (_: Throwable) { emptyMap() }

                    val sourceFileName = resolveSourceFileName(entity.id)
                    val entryNavigation = resolveEntryNavigation(entity.id, q)
                    updateState {
                        KnowledgeDetailUiState.Success(
                            entity = entity,
                            sourceFileName = sourceFileName,
                            highlight = q,
                            entryNavigation = entryNavigation,
                            initialBlockIndex = blockIndexArg.takeIf { it >= 0 },
                            initialBlockId = blockIdArg.trim().takeIf { it.isNotBlank() },
                            visionMarkdownByBlockId = visionCache
                        )
                    }
                }
            } catch (t: Throwable) {
                updateState { KnowledgeDetailUiState.Error(t.message ?: "加载失败") }
            }
        }
    }

    private fun setDeepLogicValidationEnabled(enabled: Boolean) {
        val current = currentState
        if (current is KnowledgeDetailUiState.Success) {
            updateState { (current as KnowledgeDetailUiState.Success).copy(deepLogicValidationEnabled = enabled) }
        }
    }

    private fun cacheKeyForBlock(blockId: String): String {
        val current = currentState
        val enabled = (current as? KnowledgeDetailUiState.Success)?.deepLogicValidationEnabled ?: false
        return if (enabled) "$blockId::logic" else blockId
    }

    private fun requestVisionBoost(entityId: Long, blockId: String, imageUri: String) {
        if (entityId <= 0 || blockId.isBlank() || imageUri.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val cacheKey = cacheKeyForBlock(blockId)
            val current = currentState
            if (current is KnowledgeDetailUiState.Success) {
                updateState { current.copy(visionBoostingBlockId = cacheKey, visionBoostErrorBlockId = null, visionBoostErrorMessage = null) }
            }
            try {
                val deepLogic = (current as? KnowledgeDetailUiState.Success)?.deepLogicValidationEnabled ?: false
                val markdown = visionBoostUseCase.invoke(imageUri, enableDeepLogicValidation = deepLogic)
                if (markdown.isNotBlank()) {
                    val cache = VisionCacheEntity(
                        entityId = entityId,
                        blockId = cacheKey,
                        imageUri = imageUri,
                        markdown = markdown,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    visionCacheDao.upsert(cache)
                    if (current is KnowledgeDetailUiState.Success) {
                        updateState { current.copy(visionMarkdownByBlockId = current.visionMarkdownByBlockId + (cacheKey to markdown)) }
                    }
                }
            } catch (t: Throwable) {
                if (current is KnowledgeDetailUiState.Success) {
                    updateState { current.copy(visionBoostErrorBlockId = cacheKey, visionBoostErrorMessage = t.message ?: "VisionBoost 失败") }
                }
                Log.w("PowerAi.Trace", "VisionBoost FAILED blockId=$cacheKey uri=$imageUri msg=${t.message}", t)
            } finally {
                if (current is KnowledgeDetailUiState.Success) {
                    updateState { current.copy(visionBoostingBlockId = null) }
                }
            }
        }
    }

    private fun applyVisionBoostToOriginal(entityId: Long, rawBlockId: String, cacheKey: String, clearCacheAfter: Boolean) {
        val targetId = rawBlockId.trim()
        val ck = cacheKey.trim()
        if (entityId <= 0 || targetId.isBlank() || ck.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val markdown = validateAndGetMarkdown(entityId, ck) ?: return@launch
                val updated = updateEntityWithMarkdown(entityId, targetId, markdown) ?: return@launch

                if (clearCacheAfter) {
                    try {
                        visionCacheDao.deleteOne(entityId = entityId, blockId = ck)
                        val current = currentState
                        if (current is KnowledgeDetailUiState.Success) {
                            updateState { current.copy(visionMarkdownByBlockId = current.visionMarkdownByBlockId - ck) }
                        }
                    } catch (_: Throwable) {}
                }

                refreshSuccessState(updated)
                sendEffect(KnowledgeDetailUiEffect.Toast("已保存至本地"))
            } catch (t: Throwable) {
                Log.w("PowerAi.Trace", "applyVisionBoostToOriginal FAILED entityId=$entityId blockId=$targetId cacheKey=$ck", t)
                sendEffect(KnowledgeDetailUiEffect.Toast("回填失败${t.message ?: "未知错误"}"))
            }
        }
    }

    private suspend fun validateAndGetMarkdown(entityId: Long, cacheKey: String): String? {
        val cached = visionCacheDao.getOne(entityId = entityId, blockId = cacheKey)
        val markdown = cached?.markdown?.trim().orEmpty()
        if (markdown.isBlank()) {
            sendEffect(KnowledgeDetailUiEffect.Toast("暂无可回填的 AI 结果"))
            return null
        }
        return markdown
    }

    private suspend fun updateEntityWithMarkdown(entityId: Long, targetId: String, markdown: String): KnowledgeEntity? {
        val current = dao.getById(entityId)
        val blocksJson = current?.contentBlocksJson
        if (current == null || blocksJson.isNullOrBlank()) {
            sendEffect(KnowledgeDetailUiEffect.Toast("原文不支持回填（缺少 blocks"))
            return null
        }

        val updatedBlocksJson = BlocksJsonPatcher.applyMarkdownTableToBlock(
            blocksJson = blocksJson,
            blockId = targetId,
            markdown = markdown
        )
        if (updatedBlocksJson.isNullOrBlank()) {
            sendEffect(KnowledgeDetailUiEffect.Toast("回填失败：无法匹配块或解析表"))
            return null
        }

        val normalizedSourceText = BlocksTextExtractor.extractPlainText(updatedBlocksJson)
        val normalized = TextSanitizer.normalizeForSearch(normalizedSourceText)
        val keywords = current.keywordsSerialized
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val searchContent = SemanticRoleSearchTextBuilder.buildNormalizedSearchContent(
            title = current.title,
            source = current.source,
            category = current.category,
            pageNumber = current.pageNumber,
            keywords = keywords,
            plainText = normalizedSourceText,
            blocksJson = updatedBlocksJson
        )
        dao.updateBlocksJsonAndSearchFields(
            id = entityId,
            contentBlocksJson = updatedBlocksJson,
            contentNormalized = normalized,
            searchContent = searchContent
        )

        return dao.getById(entityId) ?: current.copy(
            contentBlocksJson = updatedBlocksJson,
            contentNormalized = normalized,
            searchContent = searchContent
        )
    }

    private suspend fun resolveSourceFileName(entityId: Long): String? {
        return runCatching { databaseUseCase.resolveFileNameForItemId(entityId) }.getOrNull()
    }

    private suspend fun resolveEntryNavigation(entityId: Long, query: String): com.example.powerai.domain.model.DetailEntryNavigation? {
        return runCatching { databaseUseCase.resolveDetailEntryNavigation(entityId, query) }.getOrNull()
    }

    private suspend fun refreshSuccessState(entity: KnowledgeEntity) {
        val sourceFileName = resolveSourceFileName(entity.id)
        val entryNavigation = resolveEntryNavigation(entity.id, q)
        val prev = currentState
        if (prev is KnowledgeDetailUiState.Success) {
            updateState {
                prev.copy(
                    entity = entity,
                    sourceFileName = sourceFileName,
                    entryNavigation = entryNavigation
                )
            }
        } else {
            updateState {
                KnowledgeDetailUiState.Success(
                    entity = entity,
                    sourceFileName = sourceFileName,
                    highlight = q,
                    entryNavigation = entryNavigation,
                    initialBlockIndex = blockIndexArg.takeIf { it >= 0 },
                    initialBlockId = blockIdArg.trim().takeIf { it.isNotBlank() }
                )
            }
        }
    }
}
