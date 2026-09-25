package com.example.powerai.ui.screen.hybrid

import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import com.example.powerai.data.importer.DocumentImportManager
import com.example.powerai.data.settings.LocalAnswerFeedbackStore
import com.example.powerai.domain.model.SearchEntry
import com.example.powerai.domain.repository.HistoryScope
import com.example.powerai.domain.repository.SearchHistoryRepository
import com.example.powerai.domain.usecase.HybridHistoryUseCase
import com.example.powerai.domain.usecase.HybridQueryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

@OptIn(ExperimentalCoroutinesApi::class)
class HybridViewModelTest {
    private val createdViewModels = mutableListOf<HybridViewModel>()

    @After
    fun tearDown() {
        // 只触发取消，不阻塞等待 join：viewModelScope 子协程派发在 StandardTestDispatcher 上，
        // 在 runTest 线程里 runBlocking{join} 会与 testScheduler 相互等待导致死锁。
        createdViewModels.forEach { vm ->
            try {
                vm.viewModelScope.coroutineContext.job.cancel()
            } catch (_: Throwable) {}
        }
        createdViewModels.clear()
        Dispatchers.resetMain()
    }

    private fun cancelAllViewModels() {
        createdViewModels.forEach { vm ->
            try {
                vm.viewModelScope.coroutineContext.job.cancel()
            } catch (_: Throwable) {}
        }
        createdViewModels.clear()
    }

    private fun makeHistoryCase(
        initialLocal: List<SearchEntry> = emptyList(),
        initialSmart: List<SearchEntry> = emptyList()
    ): HybridHistoryUseCase {
        val data = mutableMapOf(
            HistoryScope.LOCAL to initialLocal.toMutableList(),
            HistoryScope.SMART to initialSmart.toMutableList(),
            HistoryScope.DATABASE to mutableListOf<SearchEntry>()
        )
        val repo = object : SearchHistoryRepository {
            override suspend fun loadHistory(scope: HistoryScope): List<SearchEntry> = data[scope]?.toList() ?: emptyList()
            override suspend fun saveHistory(scope: HistoryScope, history: List<SearchEntry>) {
                data[scope] = history.toMutableList()
            }
            override suspend fun clearHistory(scope: HistoryScope) { data[scope]?.clear() }
        }
        return HybridHistoryUseCase(repo)
    }

    private fun makeFeedbackStore(): LocalAnswerFeedbackStore {
        val prefs = object : SharedPreferences {
            private val map = mutableMapOf<String, Any?>()
            override fun getAll(): MutableMap<String, *> = map
            override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
            override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
                @Suppress("UNCHECKED_CAST")
                return map[key] as? MutableSet<String> ?: defValues
            }
            override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
            override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
            override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
            override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
            override fun contains(key: String?): Boolean = map.containsKey(key)
            override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { if (key != null) map[key] = values; return this }
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
                override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
                override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
                override fun remove(key: String?): SharedPreferences.Editor { map.remove(key); return this }
                override fun clear(): SharedPreferences.Editor { map.clear(); return this }
                override fun commit(): Boolean = true
                override fun apply() {}
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        }
        return LocalAnswerFeedbackStore(prefs)
    }

    private fun createVm(
        queryUseCase: HybridQueryUseCase,
        historyUse: HybridHistoryUseCase,
        importer: DocumentImportManager,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher
    ): HybridViewModel {
        val vm = HybridViewModel(
            useCase = queryUseCase,
        historyUseCase = historyUse,
        localAnswerFeedbackStore = makeFeedbackStore(),
        context = Mockito.mock(android.content.Context::class.java),
        importer = importer,
        savedStateHandle = SavedStateHandle(),
        ioDispatcher = dispatcher
    )
        createdViewModels.add(vm)
        return vm
    }

    private fun mockImporter(): DocumentImportManager {
        val importer = Mockito.mock(DocumentImportManager::class.java)
        Mockito.`when`(importer.progress).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(null))
        return importer
    }

    @Test
    fun `history flows are forwarded from use case`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val historyUse = makeHistoryCase(initialLocal = listOf(SearchEntry("q", 123L)))
        historyUse.init()

        val viewModel = createVm(
            queryUseCase = Mockito.mock(HybridQueryUseCase::class.java),
            historyUse = historyUse,
            importer = mockImporter(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        advanceUntilIdle()
        assertEquals(historyUse.localHistory.value, viewModel.uiState.value.localSearchHistory)
        historyUse.addLocal("new")
        advanceUntilIdle()
        assertEquals(historyUse.localHistory.value, viewModel.uiState.value.localSearchHistory)
        cancelAllViewModels()
        Dispatchers.resetMain()
    }

    @Test
    fun `submitQuery adds history entries`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val realHistory = makeHistoryCase()
        realHistory.init()
        val historyUse = Mockito.spy(realHistory)
        val queryUseCase = Mockito.mock(HybridQueryUseCase::class.java)
        val sanitizedQ = HybridUtils.sanitizeQuestion("hello")
        val ftsQ = HybridUtils.normalizeForFts(sanitizedQ)
        whenever(queryUseCase.invoke(ftsQ)).thenReturn(
            com.example.powerai.domain.model.QueryResult(answer = "", references = emptyList(), confidence = 0f)
        )
        val viewModel = createVm(
            queryUseCase = queryUseCase,
            historyUse = historyUse,
            importer = mockImporter(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.submitQuery("hello", com.example.powerai.ui.screen.main.DisplayMode.SMART)
        advanceUntilIdle()
        org.mockito.kotlin.verify(historyUse).addSmart("hello")
        cancelAllViewModels()
        Dispatchers.resetMain()
    }

    @Test
    fun `ai mode returns answer and clears refs`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val historyUse = Mockito.spy(makeHistoryCase())
        historyUse.init()
        val queryUseCase = Mockito.mock(HybridQueryUseCase::class.java)
        whenever(queryUseCase.aiMode("question", webSearchEnabled = false)).thenReturn("replied")
        val viewModel = createVm(
            queryUseCase = queryUseCase,
            historyUse = historyUse,
            importer = mockImporter(),
            dispatcher = StandardTestDispatcher(testScheduler)
        )

        viewModel.submitQuery("question", com.example.powerai.ui.screen.main.DisplayMode.AI)
        advanceUntilIdle()
        org.mockito.kotlin.verify(queryUseCase).aiMode("question", webSearchEnabled = false)
        assertEquals("replied", viewModel.uiState.value.answer)
        assertTrue(viewModel.uiState.value.references.isEmpty())
        cancelAllViewModels()
        Dispatchers.resetMain()
    }
}
