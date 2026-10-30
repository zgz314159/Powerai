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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A2–A4 UI acceptance (no `adb input tap/swipe`).
 *
 * Opens Smart search via [By.res] `smart_ds_open_panel` (「打开本地模型」).
 * Chinese via clipboard paste (Compose controlled [OutlinedTextField] often ignores
 * [UiObject2.setText] for [onValueChange]). Prefers Distill GGUF when multiple staged.
 */
@RunWith(AndroidJUnit4::class)
class SmartDeepSeekUiAcceptanceTest {
    private lateinit var device: UiDevice
    private val pkg = "com.example.powerai"

    private companion object {
        const val TAG_SMART_ICON = "bottom_tab_smart_icon"
        const val TAG_OPEN_PANEL = "smart_ds_open_panel"
        const val TAG_LOAD = "smart_ds_load_btn"
        const val TAG_SEARCH = "smart_ds_search_btn"
        const val TAG_STOP = "smart_ds_stop_btn"
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

    private fun ensureGgufPresent(filesDir: File) {
        val modelsDir = File(filesDir, "models").apply { mkdirs() }
        // Prefer Distill across app models + staged (avoid keeping sole 3B when Distill is staged).
        val preferred =
            preferDistill(
                (modelsDir.listFiles()?.filter { isUsableGguf(it) }.orEmpty()) + stagedGgufs(),
            )
        requireNotNull(preferred) { "No GGUF in files/models or /data/local/tmp/powerai_models" }
        // Keep a single preferred GGUF so engine's first-file pick is deterministic (Distill).
        modelsDir.listFiles()?.filter { isUsableGguf(it) && it.name != preferred.name }?.forEach { it.delete() }
        val dest = File(modelsDir, preferred.name)
        if (!dest.exists() || dest.length() != preferred.length()) {
            preferred.copyTo(dest, overwrite = true)
        }
    }

    private fun stagedGgufs(): List<File> =
        File("/data/local/tmp/powerai_models").listFiles()?.filter { isUsableGguf(it) }.orEmpty()

    private fun isUsableGguf(f: File): Boolean =
        f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && f.length() > 1_000_000L

    /** Prefer Distill / 1.5B over larger Instruct when both exist. */
    private fun preferDistill(files: List<File>): File? {
        if (files.isEmpty()) return null
        return files.firstOrNull { it.name.contains("Distill", ignoreCase = true) }
            ?: files.firstOrNull { it.name.contains("1.5B", ignoreCase = true) }
            ?: files.minByOrNull { it.length() }
    }

    @Test
    fun a234_chinese_stream_stop_and_restart() {
        openSmartSearchBar()
        ensureModelLoaded()

        // P3 后：无合格证据走 directAnswer（资料不足），有证据 FULL 才流式。
        // 本用例同时接受「流式可停」与「直答完成」两种合法终态。
        typeAndSubmit("发电机允许温升是多少")
        val streamed1 = waitForStopControl(45_000)
        val direct1 =
            waitForAnyText(listOf("资料不足", "局部资料", "请补充", "允许温升", "温升"), 20_000)
        assertTrue("round1 streaming or directAnswer", streamed1 || direct1)
        if (streamed1) {
            clickStop()
            assertTrue(
                "entered stopping/cancelled UI",
                waitForAnyText(listOf("已取消", "停止中", "已发送停止"), 30_000) ||
                    !device.wait(Until.hasObject(By.res(TAG_STOP)), 8_000),
            )
            Thread.sleep(1_500)
        }

        openSmartSearchBar()
        typeAndSubmit("什么是变压器短路阻抗")
        val streamed2 = waitForStopControl(45_000)
        val direct2 =
            waitForAnyText(listOf("资料不足", "局部资料", "请补充", "短路阻抗", "变压器"), 20_000)
        assertTrue("round2 streaming or directAnswer", streamed2 || direct2)
        if (streamed2) {
            Thread.sleep(2_000)
            if (hasStopControl()) {
                clickStop()
                Thread.sleep(1_000)
            }
        }
    }

    private fun requireSafeForeground() {
        val cur = device.currentPackageName ?: ""
        assertTrue("refuse outside PowerAi (current=$cur)", cur == pkg)
        val risky =
            cur.contains("inputmethod", true) ||
                cur.contains("keyboard", true) ||
                cur.contains("settings", true) ||
                cur.contains("theme", true)
        assertTrue("refuse IME/settings foreground ($cur)", !risky)
    }

    private fun openSmartSearchBar() {
        requireSafeForeground()
        if (isSearchBarOpen()) return

        // Select SMART if open_panel not yet composed (default launch already has SMART + closed bar).
        if (!device.hasObject(By.res(TAG_OPEN_PANEL))) {
            val icon = waitForObject(By.res(TAG_SMART_ICON), 10_000)
            requireNotNull(icon) { "missing $TAG_SMART_ICON; foreground=${device.currentPackageName}" }
            requireSafeForeground()
            icon.click()
            Thread.sleep(800)
            if (isSearchBarOpen()) return
        }

        val openBtn = waitForObject(By.res(TAG_OPEN_PANEL), 10_000)
        requireNotNull(openBtn) {
            "missing $TAG_OPEN_PANEL after SMART select; foreground=${device.currentPackageName}"
        }
        requireSafeForeground()
        openBtn.click()
        // Require load controls (not only query field): autofocus IME can race hierarchy.
        assertTrue("smart search bar opened", waitForRes(TAG_LOAD, 12_000))
        dismissSmartIme()
    }

    private fun isSearchBarOpen(): Boolean =
        device.hasObject(By.res(TAG_LOAD)) ||
            device.hasObject(By.res(TAG_SEARCH)) ||
            device.hasObject(By.pkg(pkg).text("模型已加载")) ||
            device.hasObject(By.pkg(pkg).text("模型未加载"))

    /** Unfocus SearchBar without Back (BackHandler closes the panel when query blank). */
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
        assertTrue(
            "model loaded",
            device.wait(Until.hasObject(By.pkg(pkg).text("模型已加载")), 180_000),
        )
    }

    /**
     * Load-first path already done by caller. Paste Chinese into Compose field so
     * [onValueChange] updates [MainScreenState.searchQuery]; blank query makes
     * submitSmartQuery no-op → no stop button.
     */
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
        // Clear then paste via InputConnection (updates Compose state).
        field.setText("")
        Thread.sleep(150)
        device.pressKeyCode(KeyEvent.KEYCODE_PASTE)
        Thread.sleep(500)
        val applied =
            device.hasObject(By.pkg(pkg).textContains(text.take(4))) ||
                (waitForObject(By.res(TAG_QUERY), 2_000)?.text?.contains(text.take(2)) == true)
        if (!applied) {
            // Last resort: a11y setText (may still miss Compose state on some builds).
            field.setText(text)
            Thread.sleep(400)
        }
        assertTrue(
            "query not applied to field/state: '$text'",
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
        // Successful submit closes panel; blank submit leaves it open.
        val submitted = !isSearchBarOpen() || hasStopControl() ||
            waitForAnyText(listOf("检索", "生成", "等待", "思考", "停止生成"), 8_000)
        assertTrue("search did not start generation (empty Compose query?)", submitted)
    }

    private fun hasStopControl(): Boolean =
        device.hasObject(By.res(TAG_STOP)) || device.hasObject(By.pkg(pkg).text("停止生成"))

    private fun waitForStopControl(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (hasStopControl()) return true
            // Progress labels prove generation started even if stop tag lags.
            if (device.hasObject(By.pkg(pkg).text("停止生成"))) return true
            Thread.sleep(400)
        }
        return hasStopControl()
    }

    private fun clickStop() {
        dismissSmartIme()
        if (device.hasObject(By.res(TAG_STOP))) {
            clickRes(TAG_STOP)
            return
        }
        val byText = waitForObject(By.pkg(pkg).text("停止生成"), 8_000)
        requireNotNull(byText) { "missing stop control" }
        requireSafeForeground()
        byText.click()
        Thread.sleep(500)
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
