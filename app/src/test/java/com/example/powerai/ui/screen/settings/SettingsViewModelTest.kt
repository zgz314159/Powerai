package com.example.powerai.ui.screen.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.data.json.JsonRepository
import com.example.powerai.data.settings.FontSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsViewModelTest {
    private lateinit var repo: JsonRepository
    private lateinit var dao: KnowledgeDao
    private lateinit var context: Context
    private lateinit var pm: PackageManager
    private lateinit var fontSettings: FontSettings

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())

        repo = mock(JsonRepository::class.java)
        `when`(repo.importProgress).thenReturn(MutableStateFlow(null))
        dao = mock(KnowledgeDao::class.java)

        context = mock(Context::class.java)
        pm = mock(PackageManager::class.java)
        `when`(context.packageManager).thenReturn(pm)
        `when`(context.packageName).thenReturn("com.example.powerai")
        val pi = PackageInfo().apply { versionName = "1.2.3" }
        `when`(pm.getPackageInfo(anyString(), anyInt())).thenReturn(pi)

        val prefs = mock(android.content.SharedPreferences::class.java)
        val editor = mock(android.content.SharedPreferences.Editor::class.java)
        `when`(prefs.getFloat(anyString(), anyFloat())).thenReturn(1.0f)
        `when`(prefs.getBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putFloat(anyString(), anyFloat())).thenReturn(editor)
        `when`(editor.putBoolean(anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(editor)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        // Construct FontSettings only after getSharedPreferences is stubbed.
        fontSettings = FontSettings(context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = SettingsViewModel(repo, dao, fontSettings, context)

    @Test
    fun `versionName initialized from context`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(this.testScheduler))
        val vm = createVm()
        advanceUntilIdle()
        assertEquals("1.2.3", vm.uiState.value.versionName)
    }

    @Test
    fun `onVersionTapped emits after five taps`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(this.testScheduler))
        val vm = createVm()
        val events = mutableListOf<Unit>()
        val job = this.launch { vm.devBackdoor.collect { events.add(Unit) } }
        repeat(5) {
            vm.onIntent(SettingsIntent.OnVersionTapped)
            advanceUntilIdle()
        }
        assertEquals(1, events.size)
        job.cancel()
    }

    @Test
    fun `setDetailContentFontScale clamps values`() {
        val vm = createVm()
        vm.onIntent(SettingsIntent.SetDetailContentFontScale(0.1f))
        assertEquals(0.75f, vm.detailContentFontScale.value, 0.0001f)
        vm.onIntent(SettingsIntent.SetDetailContentFontScale(5f))
        assertEquals(3.0f, vm.detailContentFontScale.value, 0.0001f)
        vm.onIntent(SettingsIntent.Shutdown)
    }
}
