package com.example.powerai.engine

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.example.powerai.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P5 UI：截图回归问句。P3 后澄清/局部资料走 directAnswer，不再出现「停止生成」。
 * 打开/粘贴/提交流程对齐 [SmartDeepSeekUiAcceptanceTest]。
 */
@RunWith(AndroidJUnit4::class)
class SmartRagScreenshotUiAcceptanceTest {
    private lateinit var device: UiDevice
    private val pkg = "com.example.powerai"

    private companion object {
        const val TAG_SMART_ICON = "bottom_tab_smart_icon"
        const val TAG_OPEN_PANEL = "smart_ds_open_panel"
        const val TAG_LOAD = "smart_ds_load_btn"
        const val TAG_SEARCH = "smart_ds_search_btn"
        const val TAG_QUERY = "smart_ds_query_field"
    }

    @Before
    fun launchApp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        val ctx = instrumentation.targetContext
        ensureGgufPresent(ctx.filesDir)
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
        )
        assertTrue("app visible", device.wait(Until.hasObject(By.pkg(pkg).depth(0)), 15_000))
        Thread.sleep(1_500)
        requireSafeForeground()
    }

    @Test
    fun short_entity_shows_clarification() {
        openSmartSearchBar()
        ensureModelLoaded()
        typeAndSubmit("变压器")
        assertTrue(
            "expect clarification",
            waitForAnyText(listOf("请补充", "问题过短", "对象 + 要问的点", "例如"), 60_000),
        )
        assertFalse(device.hasObject(By.pkg(pkg).textContains("100000")))
    }

    @Test
    fun outage_ask_keeps_duty_facts_not_rewrite() {
        openSmartSearchBar()
        ensureModelLoaded()
        typeAndSubmit("停电怎么操作")
        val ok =
            waitForAnyText(
                listOf("一人操作", "一人监护", "局部资料", "资料仅覆盖", "资料不足", "请补充"),
                90_000,
            )
        assertTrue("expect partial/clarify/insufficient UI", ok)
        assertFalse(
            "duty rewrite forbidden",
            device.hasObject(By.pkg(pkg).textContains("轮流操作")) ||
                device.hasObject(By.pkg(pkg).textContains("两人轮流")),
        )
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        File(ctx.filesDir, "p5_screenshot_ui.txt").writeText(
            "hasDuty=${device.hasObject(By.pkg(pkg).textContains("一人操作"))} " +
                "hasPartial=${device.hasObject(By.pkg(pkg).textContains("资料仅覆盖"))} " +
                "hasInsufficient=${device.hasObject(By.pkg(pkg).textContains("资料不足"))}",
        )
    }

    private fun ensureGgufPresent(filesDir: File) {
        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        val preferred =
            preferDistill(
                (modelsDir.listFiles()?.filter { isUsableGguf(it) }.orEmpty()) +
                    (File("/data/local/tmp/powerai_models").listFiles()?.filter { isUsableGguf(it) }.orEmpty()),
            )
        requireNotNull(preferred) { "No GGUF" }
        modelsDir.listFiles()?.filter { isUsableGguf(it) && it.name != preferred.name }?.forEach { it.delete() }
        val dest = File(modelsDir, preferred.name)
        if (!dest.exists() || dest.length() != preferred.length()) {
            preferred.copyTo(dest, overwrite = true)
        }
    }

    private fun preferDistill(files: List<File>): File? {
        if (files.isEmpty()) return null
        return files.firstOrNull { it.name.contains("Distill", ignoreCase = true) }
            ?: files.firstOrNull { it.name.contains("1.5B", ignoreCase = true) }
            ?: files.minByOrNull { it.length() }
    }

    private fun isUsableGguf(f: File): Boolean =
        f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.length() > 1_000_000L

    private fun requireSafeForeground() {
        val cur = device.currentPackageName ?: ""
        assertTrue("refuse outside PowerAi (current=$cur)", cur == pkg)
    }

    private fun openSmartSearchBar() {
        requireSafeForeground()
        if (isSearchBarOpen()) return
        if (!device.hasObject(By.res(TAG_OPEN_PANEL))) {
            val icon = waitForObject(By.res(TAG_SMART_ICON), 10_000)
            requireNotNull(icon) { "missing $TAG_SMART_ICON; foreground=${device.currentPackageName}" }
            requireSafeForeground()
            icon.click()
            Thread.sleep(800)
            if (isSearchBarOpen()) return
        }
        val openBtn = waitForObject(By.res(TAG_OPEN_PANEL), 10_000)
        requireNotNull(openBtn) { "missing $TAG_OPEN_PANEL" }
        requireSafeForeground()
        openBtn.click()
        assertTrue("smart search bar opened", waitForRes(TAG_LOAD, 12_000))
        dismissSmartIme()
    }

    private fun isSearchBarOpen(): Boolean =
        device.hasObject(By.res(TAG_LOAD)) ||
            device.hasObject(By.res(TAG_SEARCH)) ||
            device.hasObject(By.pkg(pkg).text("模型已加载")) ||
            device.hasObject(By.pkg(pkg).text("模型未加载"))

    private fun dismissSmartIme() {
        for (t in listOf("模型未加载", "模型已加载", "展开高级设置", "搜索知识")) {
            val obj = device.findObject(By.pkg(pkg).text(t))
            if (obj != null) {
                requireSafeForeground()
                obj.click()
                Thread.sleep(400)
                return
            }
        }
    }

    private fun ensureModelLoaded() {
        requireSafeForeground()
        assertTrue("load button visible", waitForRes(TAG_LOAD, 12_000) || device.hasObject(By.pkg(pkg).text("加载")))
        if (device.hasObject(By.pkg(pkg).text("模型已加载"))) return
        dismissSmartIme()
        if (device.hasObject(By.res(TAG_LOAD))) {
            clickRes(TAG_LOAD)
        } else {
            val loadText = waitForObject(By.pkg(pkg).text("加载"), 8_000)
            requireNotNull(loadText) { "missing load control" }
            requireSafeForeground()
            loadText.click()
            Thread.sleep(500)
        }
        assertTrue("model loaded", device.wait(Until.hasObject(By.pkg(pkg).text("模型已加载")), 180_000))
    }

    private fun typeAndSubmit(text: String) {
        requireSafeForeground()
        assertTrue("model must be loaded before typing", device.hasObject(By.pkg(pkg).text("模型已加载")))
        val field = waitForObject(By.res(TAG_QUERY), 8_000)
        requireNotNull(field) { "missing $TAG_QUERY" }
        requireSafeForeground()
        field.click()
        Thread.sleep(300)
        val cm =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("powerai_q", text))
        field.setText("")
        Thread.sleep(150)
        device.pressKeyCode(KeyEvent.KEYCODE_PASTE)
        Thread.sleep(500)
        val applied =
            device.hasObject(By.pkg(pkg).textContains(text.take(4))) ||
                (waitForObject(By.res(TAG_QUERY), 2_000)?.text?.contains(text.take(2)) == true)
        if (!applied) {
            field.setText(text)
            Thread.sleep(400)
        }
        assertTrue(
            "query not applied: '$text'",
            device.hasObject(By.pkg(pkg).textContains(text.take(4))) ||
                (waitForObject(By.res(TAG_QUERY), 2_000)?.text?.contains(text.take(2)) == true),
        )
        dismissSmartIme()
        requireSafeForeground()
        val search =
            waitForObject(By.res(TAG_SEARCH), 8_000)
                ?: waitForObject(By.pkg(pkg).text("搜索"), 5_000)
                ?: waitForObject(By.desc("搜索"), 3_000)
        requireNotNull(search) { "missing search control after query entered" }
        requireSafeForeground()
        search.click()
        Thread.sleep(1_000)
        val submitted =
            !isSearchBarOpen() ||
                waitForAnyText(
                    listOf("检索", "生成", "等待", "思考", "请补充", "局部资料", "资料不足", "一人操作", "问题过短"),
                    8_000,
                )
        assertTrue("search did not start", submitted)
    }

    private fun clickRes(tag: String) {
        requireSafeForeground()
        val obj = waitForObject(By.res(tag), 8_000)
        requireNotNull(obj) { "missing res='$tag'" }
        requireSafeForeground()
        obj.click()
        Thread.sleep(500)
    }

    private fun waitForRes(
        tag: String,
        timeoutMs: Long,
    ): Boolean = device.wait(Until.hasObject(By.res(tag)), timeoutMs)

    private fun waitForObject(
        selector: androidx.test.uiautomator.BySelector,
        timeoutMs: Long,
    ): UiObject2? {
        if (!device.wait(Until.hasObject(selector), timeoutMs)) return null
        return device.findObject(selector)
    }

    private fun waitForAnyText(
        texts: List<String>,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (texts.any { device.hasObject(By.pkg(pkg).textContains(it)) }) return true
            Thread.sleep(400)
        }
        return false
    }
}
