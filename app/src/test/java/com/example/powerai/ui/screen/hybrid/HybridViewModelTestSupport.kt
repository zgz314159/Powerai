package com.example.powerai.ui.screen.hybrid

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.powerai.core.data.importer.ImportProgress
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.data.importer.AssetImportDiagnostics
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.settings.LocalAnswerFeedbackStore
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import com.example.powerai.domain.usecase.HybridHistoryUseCase
import com.example.powerai.domain.usecase.HybridQueryUseCase
import com.example.powerai.domain.usecase.LocalAnswerPlanner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mockito.Mockito

/** Fake SharedPreferences-backed feedback store; no real files are touched. */
internal fun fakeFeedbackStore(): LocalAnswerFeedbackStore {
    val prefs =
        object : SharedPreferences {
            private val map = mutableMapOf<String, Any?>()

            override fun getAll(): MutableMap<String, *> = map

            override fun getString(
                key: String?,
                defValue: String?,
            ): String? = map[key] as? String ?: defValue

            override fun getStringSet(
                key: String?,
                defValues: MutableSet<String>?,
            ): MutableSet<String>? {
                @Suppress("UNCHECKED_CAST")
                return map[key] as? MutableSet<String> ?: defValues
            }

            override fun getInt(
                key: String?,
                defValue: Int,
            ): Int = map[key] as? Int ?: defValue

            override fun getLong(
                key: String?,
                defValue: Long,
            ): Long = map[key] as? Long ?: defValue

            override fun getFloat(
                key: String?,
                defValue: Float,
            ): Float = map[key] as? Float ?: defValue

            override fun getBoolean(
                key: String?,
                defValue: Boolean,
            ): Boolean = map[key] as? Boolean ?: defValue

            override fun contains(key: String?): Boolean = map.containsKey(key)

            override fun edit(): SharedPreferences.Editor =
                object : SharedPreferences.Editor {
                    override fun putString(
                        key: String?,
                        value: String?,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = value
                        return this
                    }

                    override fun putStringSet(
                        key: String?,
                        values: MutableSet<String>?,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = values
                        return this
                    }

                    override fun putInt(
                        key: String?,
                        value: Int,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = value
                        return this
                    }

                    override fun putLong(
                        key: String?,
                        value: Long,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = value
                        return this
                    }

                    override fun putFloat(
                        key: String?,
                        value: Float,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = value
                        return this
                    }

                    override fun putBoolean(
                        key: String?,
                        value: Boolean,
                    ): SharedPreferences.Editor {
                        if (key != null) map[key] = value
                        return this
                    }

                    override fun remove(key: String?): SharedPreferences.Editor {
                        map.remove(key)
                        return this
                    }

                    override fun clear(): SharedPreferences.Editor {
                        map.clear()
                        return this
                    }

                    override fun commit(): Boolean = true

                    override fun apply() {}
                }

            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
    return LocalAnswerFeedbackStore(prefs)
}

internal fun fakeHistoryUseCase(
    initialLocal: List<SearchEntry> = emptyList(),
    initialSmart: List<SearchEntry> = emptyList(),
): HybridHistoryUseCase {
    val data =
        mutableMapOf(
            HistoryScope.LOCAL to initialLocal.toMutableList(),
            HistoryScope.SMART to initialSmart.toMutableList(),
            HistoryScope.DATABASE to mutableListOf<SearchEntry>(),
        )
    val repo =
        object : SearchHistoryRepository {
            override suspend fun loadHistory(scope: HistoryScope): List<SearchEntry> = data[scope]?.toList() ?: emptyList()

            override suspend fun saveHistory(
                scope: HistoryScope,
                history: List<SearchEntry>,
            ) {
                data[scope] = history.toMutableList()
            }

            override suspend fun clearHistory(scope: HistoryScope) {
                data[scope]?.clear()
            }
        }
    return HybridHistoryUseCase(repo)
}

internal fun knowledgeItem(
    id: Long,
    title: String = "t$id",
    source: String = "src.pdf",
): KnowledgeItem =
    KnowledgeItem(
        id = id,
        title = title,
        content = "content-$id",
        source = source,
        category = "分类",
        keywords = emptyList(),
    )

/** Builds a LocalModeResult whose retrievals mirror the given items. */
internal fun localModeResult(
    query: String,
    items: List<KnowledgeItem> = emptyList(),
    gemmaJob: kotlinx.coroutines.Job? = null,
): HybridQueryUseCase.LocalModeResult {
    val retrievals = items.map { RetrievalResult(item = it, source = "local", score = 1f) }
    return HybridQueryUseCase.LocalModeResult(
        query =
            HybridQueryUseCase.LocalQueryResult(
                retrievals = retrievals,
                items = items,
                totalCandidateCount = items.size,
                refinedCandidateCount = items.size,
                discardedCandidates = emptyList(),
                extractiveAnswer = null,
            ),
        assessment = LocalAnswerPlanner.assess(query, retrievals),
        gemmaJob = gemmaJob,
    )
}

/**
 * Per-test collaborator bundle for [HybridViewModel]; everything is a fake or
 * mock on a single virtual scheduler.
 */
internal class HybridVmHarness(
    seedLocalHistory: List<SearchEntry> = emptyList(),
    seedSmartHistory: List<SearchEntry> = emptyList(),
    val feedbackStore: LocalAnswerFeedbackStore = fakeFeedbackStore(),
    historyOverride: HybridHistoryUseCase? = null,
) {
    val queryUseCase: HybridQueryUseCase = Mockito.mock(HybridQueryUseCase::class.java)
    val historyUseCase: HybridHistoryUseCase = historyOverride ?: fakeHistoryUseCase(seedLocalHistory, seedSmartHistory)
    val context: android.content.Context = Mockito.mock(android.content.Context::class.java)
    val importer: DocumentImportManager =
        Mockito.mock(DocumentImportManager::class.java).also { manager ->
            Mockito.`when`(manager.progress).thenReturn(
                kotlinx.coroutines.flow.MutableStateFlow<ImportProgress?>(null),
            )
            Mockito.`when`(manager.importDiagnostics).thenReturn(
                kotlinx.coroutines.flow.MutableStateFlow(AssetImportDiagnostics()),
            )
        }

    private val created = mutableListOf<HybridViewModel>()

    fun create(
        scope: TestScope,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(scope.testScheduler),
    ): HybridViewModel {
        val viewModel =
            HybridViewModel(
                useCase = queryUseCase,
                historyUseCase = historyUseCase,
                localAnswerFeedbackStore = feedbackStore,
                context = context,
                importer = importer,
                savedStateHandle = SavedStateHandle(),
                ioDispatcher = dispatcher,
            )
        created += viewModel
        return viewModel
    }

    fun cancelAll() {
        created.forEach { viewModel ->
            try {
                viewModel.viewModelScope.coroutineContext.job.cancel()
            } catch (_: Throwable) {
            }
        }
        created.clear()
    }
}

/** Runs [body] with Main bound to a single virtual scheduler. */
internal fun runHybridTest(
    factory: () -> HybridVmHarness = { HybridVmHarness() },
    body: suspend TestScope.(HybridVmHarness) -> Unit,
) = runTest {
    Dispatchers.setMain(StandardTestDispatcher(testScheduler))
    val harness = factory()
    try {
        body(harness)
    } finally {
        harness.cancelAll()
        Dispatchers.resetMain()
    }
}
