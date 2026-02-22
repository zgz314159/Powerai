package com.example.powerai.ui.screen.detail

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.powerai.data.importer.BlocksJsonPatcher
import com.example.powerai.data.importer.BlocksTextExtractor
import com.example.powerai.data.importer.TextSanitizer
import com.example.powerai.data.local.dao.KnowledgeDao
import com.example.powerai.data.local.dao.VisionCacheDao
import com.example.powerai.data.local.entity.KnowledgeEntity
import com.example.powerai.data.local.entity.VisionCacheEntity
import com.example.powerai.domain.vision.VisionBoostUseCase
import com.example.powerai.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class KnowledgeDetailUiState {
    data object Loading : KnowledgeDetailUiState()
    data class Success(
        val entity: KnowledgeEntity,
        val highlight: String,
        val initialBlockIndex: Int?,
        val initialBlockId: String?
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
    private val visionBoostUseCase: VisionBoostUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>(Screen.Detail.ARG_ID) ?: -1L
    private val q: String = savedStateHandle.get<String>(Screen.Detail.ARG_Q).orEmpty()
    private val blockIndexArg: Int = savedStateHandle.get<Int>(Screen.Detail.ARG_BLOCK_INDEX) ?: -1
    private val blockIdArg: String = savedStateHandle.get<String>(Screen.Detail.ARG_BLOCK_ID).orEmpty()

    private val _uiState = MutableStateFlow<KnowledgeDetailUiState>(KnowledgeDetailUiState.Loading)
    val uiState: StateFlow<KnowledgeDetailUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<KnowledgeDetailUiEffect>(extraBufferCapacity = 4)
    val uiEffect = _uiEffect.asSharedFlow()

    private val _visionMarkdownByBlockId = MutableStateFlow<Map<String, String>>(emptyMap())
    val visionMarkdownByBlockId: StateFlow<Map<String, String>> = _visionMarkdownByBlockId.asStateFlow()

    private val _visionBoostingBlockId = MutableStateFlow<String?>(null)
    val visionBoostingBlockId: StateFlow<String?> = _visionBoostingBlockId.asStateFlow()

    private val _visionBoostErrorBlockId = MutableStateFlow<String?>(null)
    val visionBoostErrorBlockId: StateFlow<String?> = _visionBoostErrorBlockId.asStateFlow()

    private val _visionBoostErrorMessage = MutableStateFlow<String?>(null)
    val visionBoostErrorMessage: StateFlow<String?> = _visionBoostErrorMessage.asStateFlow()

    private val _deepLogicValidationEnabled = MutableStateFlow(false)
    val deepLogicValidationEnabled: StateFlow<Boolean> = _deepLogicValidationEnabled.asStateFlow()

    fun setDeepLogicValidationEnabled(enabled: Boolean) {
        _deepLogicValidationEnabled.value = enabled
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (id <= 0) {
                    _uiState.value = KnowledgeDetailUiState.NotFound(id)
                    return@launch
                }
                val entity = dao.getById(id)
                if (entity == null) {
                    _uiState.value = KnowledgeDetailUiState.NotFound(id)
                } else {
                    try {
                        val cached = visionCacheDao.getAllForEntity(entity.id)
                        _visionMarkdownByBlockId.value = cached
                            .filter { it.markdown.isNotBlank() && it.blockId.isNotBlank() }
                            .associate { it.blockId to it.markdown }
                    } catch (_: Throwable) {
                    }

                    _uiState.value = KnowledgeDetailUiState.Success(
                        entity = entity,
                        highlight = q,
                        initialBlockIndex = blockIndexArg.takeIf { it >= 0 },
                        initialBlockId = blockIdArg.trim().takeIf { it.isNotBlank() }
                    )
                }
            } catch (t: Throwable) {
                _uiState.value = KnowledgeDetailUiState.Error(t.message ?: "加载失败")
            }
        }
    }

    private fun cacheKeyForBlock(blockId: String): String {
        return if (_deepLogicValidationEnabled.value) "$blockId::logic" else blockId
    }

    fun requestVisionBoost(entityId: Long, blockId: String, imageUri: String) {
        if (entityId <= 0 || blockId.isBlank() || imageUri.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val cacheKey = cacheKeyForBlock(blockId)
            _visionBoostingBlockId.value = cacheKey
            _visionBoostErrorBlockId.value = null
            _visionBoostErrorMessage.value = null
            try {
                val markdown = visionBoostUseCase.invoke(imageUri, enableDeepLogicValidation = _deepLogicValidationEnabled.value)
                if (markdown.isNotBlank()) {
                    val cache = VisionCacheEntity(
                        entityId = entityId,
                        blockId = cacheKey,
                        imageUri = imageUri,
                        markdown = markdown,
                        updatedAtMs = System.currentTimeMillis()
                    )
                    visionCacheDao.upsert(cache)
                    _visionMarkdownByBlockId.value = _visionMarkdownByBlockId.value + (cacheKey to markdown)
                }
            } catch (t: Throwable) {
                _visionBoostErrorBlockId.value = cacheKey
                _visionBoostErrorMessage.value = t.message ?: "VisionBoost 失败"
                Log.w("PowerAi.Trace", "VisionBoost FAILED blockId=$cacheKey uri=$imageUri msg=${t.message}", t)
            } finally {
                _visionBoostingBlockId.value = null
            }
        }
    }

    fun applyVisionBoostToOriginal(
        entityId: Long,
        rawBlockId: String,
        cacheKey: String,
        clearCacheAfter: Boolean
    ) {
        val targetId = rawBlockId.trim()
        val ck = cacheKey.trim()
        if (entityId <= 0 || targetId.isBlank() || ck.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = visionCacheDao.getOne(entityId = entityId, blockId = ck)
                val markdown = cached?.markdown?.trim().orEmpty()
                if (markdown.isBlank()) {
                    _uiEffect.tryEmit(KnowledgeDetailUiEffect.Toast("暂无可回填的 AI 结果"))
                    return@launch
                }

                val current = dao.getById(entityId)
                if (current == null || current.contentBlocksJson.isNullOrBlank()) {
                    _uiEffect.tryEmit(KnowledgeDetailUiEffect.Toast("原文不支持回填（缺少 blocks）"))
                    return@launch
                }

                val updatedBlocksJson = BlocksJsonPatcher.applyMarkdownTableToBlock(
                    blocksJson = current.contentBlocksJson,
                    blockId = targetId,
                    markdown = markdown
                )
                if (updatedBlocksJson.isNullOrBlank()) {
                    _uiEffect.tryEmit(KnowledgeDetailUiEffect.Toast("回填失败：无法匹配块或解析表格"))
                    return@launch
                }

                val normalizedSourceText = BlocksTextExtractor.extractPlainText(updatedBlocksJson)
                val normalized = TextSanitizer.normalizeForSearch(normalizedSourceText)
                dao.updateBlocksJsonAndSearchFields(
                    id = entityId,
                    contentBlocksJson = updatedBlocksJson,
                    contentNormalized = normalized,
                    searchContent = normalized
                )

                if (clearCacheAfter) {
                    try {
                        visionCacheDao.deleteOne(entityId = entityId, blockId = ck)
                        _visionMarkdownByBlockId.value = _visionMarkdownByBlockId.value - ck
                    } catch (_: Throwable) {
                    }
                }

                val refreshed = dao.getById(entityId) ?: current.copy(
                    contentBlocksJson = updatedBlocksJson,
                    contentNormalized = normalized,
                    searchContent = normalized
                )

                val prev = _uiState.value
                if (prev is KnowledgeDetailUiState.Success) {
                    _uiState.value = prev.copy(entity = refreshed)
                } else {
                    _uiState.value = KnowledgeDetailUiState.Success(
                        entity = refreshed,
                        highlight = q,
                        initialBlockIndex = blockIndexArg.takeIf { it >= 0 },
                        initialBlockId = blockIdArg.trim().takeIf { it.isNotBlank() }
                    )
                }

                _uiEffect.tryEmit(KnowledgeDetailUiEffect.Toast("已保存至本地"))
            } catch (t: Throwable) {
                Log.w("PowerAi.Trace", "applyVisionBoostToOriginal FAILED entityId=$entityId blockId=$targetId cacheKey=$ck", t)
                _uiEffect.tryEmit(KnowledgeDetailUiEffect.Toast("回填失败：${t.message ?: "未知错误"}"))
            }
        }
    }
}
