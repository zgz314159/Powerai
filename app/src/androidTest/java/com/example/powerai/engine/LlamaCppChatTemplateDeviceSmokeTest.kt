package com.example.powerai.engine

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.powerai.engine.ai.LlamaCppChatInferenceBuilder
import com.example.powerai.engine.ai.LlamaCppDeepSeekRuntime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * P1 真机门禁：同一证据正文、同一模型下，经 messages 模板同步生成；
 * 诊断必须声明 [LlamaCppChatInferenceBuilder.TEMPLATE_MODE]。
 * 不与检索/守卫混测；不把单次生成当事实正确性证明。
 */
@RunWith(AndroidJUnit4::class)
class LlamaCppChatTemplateDeviceSmokeTest {
    private val evidence =
        "10kV设备检修前应先拉开开关并接地，接地后应复测确保无电后方可作业。"

    private val userPrompt =
        "你是电力知识助手。\n相关本地资料片段：\n[1] 10kV接地\n内容：$evidence\n" +
            "--- 参考资料结束 ---\n只依据参考资料作答。用户问题：10KV设备检修"

    @Test
    fun messages_template_sync_generate_records_diagnostics() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val gguf = resolveGguf(ctx)
        assumeTrue("No GGUF under files/models or Download", gguf != null && gguf!!.length() > 1_000_000L)

        val runtime = LlamaCppDeepSeekRuntime()
        assertTrue(runtime.safeInitBackend("cpu"))
        assertTrue(runtime.safeInitModel(gguf!!.absolutePath, useGpu = false))
        assertTrue(runtime.safeIsReady())
        runtime.safeSetSystemPrompt("只用中文。依据资料作答。")

        try {
            val out1 = runtime.safeGenerate(userPrompt, 96)
            val diag1 = runtime.getNativeRuntimeDiagnostics().lastInitParamsJson.orEmpty()
            assertTrue("diag must declare templateMode: $diag1", diag1.contains(LlamaCppChatInferenceBuilder.TEMPLATE_MODE))
            assertTrue("diag must declare messages roles: $diag1", diag1.contains("messageRoles"))
            assertTrue("diag temperature present: $diag1", diag1.contains("temperature"))

            val out2 = runtime.safeGenerate(userPrompt, 96)
            val diag2 = runtime.getNativeRuntimeDiagnostics().lastInitParamsJson.orEmpty()
            assertTrue(diag2.contains(LlamaCppChatInferenceBuilder.TEMPLATE_MODE))

            // 至少一次非空；不要求两次逐字相同（采样非确定性）
            assertTrue("run1 blank", out1.isNotBlank() || out2.isNotBlank())
            val sample = if (out1.isNotBlank()) out1 else out2
            assertTrue(
                "expect CJK: '$sample'",
                sample.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN },
            )
            assertFalse("must not be pure latin noise", sample.matches(Regex("^[A-Za-z0-9\\s.,!?]+$")))

            // 写入 scratch 可读诊断摘要到 app files（adb pull 用）
            File(ctx.filesDir, "p1_template_smoke.txt").writeText(
                buildString {
                    appendLine("gguf=${gguf.name}")
                    appendLine("diag1=$diag1")
                    appendLine("out1_chars=${out1.length}")
                    appendLine("out2_chars=${out2.length}")
                    appendLine("sample=${sample.take(240)}")
                },
            )
        } finally {
            runtime.safeUnload()
        }
    }

    /** Prefer private models/; then app-specific external; then Download. */
    private fun resolveGguf(ctx: Context): File? {
        val dirs =
            listOfNotNull(
                File(ctx.filesDir, "models"),
                ctx.getExternalFilesDir(null)?.let { File(it, "models") },
                File("/sdcard/Android/data/com.example.powerai/files/models"),
                File("/sdcard/Download"),
            )
        return dirs
            .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
            .filter { it.isFile && it.name.endsWith(".gguf", ignoreCase = true) && it.length() > 1_000_000L }
            .maxByOrNull { it.length() }
    }
}
