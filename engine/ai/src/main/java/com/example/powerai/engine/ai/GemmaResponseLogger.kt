package com.example.powerai.engine.ai

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Helper responsible for all of the on-disk logging and forensic persistence
 * that used to live inside [GemmaInferenceEngine].  By centralizing the code
 * we can simplify the engine logic and write focused unit tests for logging
 * behaviour (using a fake context and temporary directory).
 */
object GemmaResponseLogger {
    /**
     * Write the prompt header to the `last_res.txt` file.  This is executed
     * before model invocation so that monitoring scripts can see the input.
     */
    fun writePromptMarker(context: Context?, finalPrompt: String) {
        // Without a filesDir a relative File(...) would land in the current working
        // directory (e.g. repository files during unit tests) — skip logging instead.
        val filesDir = context?.filesDir ?: return
        try {
            val hf = File(filesDir, "last_res.txt")
            val fosh = FileOutputStream(hf, false)
            try {
                val header = "[[SYSTEM_MONITOR_ACTIVE]]\n"
                val promptEcho = "FINAL_PROMPT:\n" + finalPrompt + "\n\n"
                fosh.write((header + promptEcho).toByteArray(Charsets.UTF_8))
                fosh.flush()
                try { fosh.fd.sync() } catch (_: Throwable) {}
            } finally {
                try { fosh.close() } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {
            // best-effort only
        }
    }

    /**
     * Persist the model response along with provenance, metrics and optional
     * diagnostic dumps.  Parameters mirror the local engine output.
     */
    /**
     * Persist response details and return the token count used (based on cleaned
     * text).  Caller can use this value for the InferenceResult.
     */
    fun writeResponse(
        context: Context?,
        contextEntries: List<SparseSearcher.SafetyEntry>?,
        rawText: String,
        lastResponseObj: Any?,
        tokenIdsCaptured: Any?,
        finishReasonVal: String?
    ): Int {
        // Clean the text and compute the token count first, independently of any
        // file IO, so a null filesDir (or a failing log write) can never change
        // the returned token count.
        val cleaned = try {
            GemmaResponseSanitizer.cleanFinalText(rawText).first
        } catch (_: Throwable) {
            // preserve prior behaviour: no cleaned text means no token count
            return 0
        }
        val tokenCountForResult = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.size

        // Without a filesDir a relative File(...) would land in the current working
        // directory (e.g. repository files during unit tests) — skip logging instead.
        val filesDir = context?.filesDir ?: return tokenCountForResult
        try {
            val f = File(filesDir, "last_res.txt")
            try {
                var finalWithProv = cleaned
                try {
                    if (contextEntries != null && contextEntries.isNotEmpty()) {
                        val srcs = contextEntries.map { it.source }.distinct()
                        if (srcs.isNotEmpty()) {
                            val joined = srcs.joinToString("; ")
                            finalWithProv = "${cleaned}\n（来源：$joined）"
                        }
                    }
                } catch (_: Throwable) {}

                f.appendText("\n--- FINAL_DECODE ---\n" + finalWithProv, Charsets.UTF_8)
                try { f.appendText("\n--- END OF SESSION ---\n", Charsets.UTF_8) } catch (_: Throwable) {}
                try { f.appendText("\n[METRICS] Generated Tokens: $tokenCountForResult / Max: 512\n", Charsets.UTF_8) } catch (_: Throwable) {}
                try { f.appendText("\n[[INFERENCE_FINISHED]]\n", Charsets.UTF_8) } catch (_: Throwable) {}

                // dump response object if present
                if (lastResponseObj != null) {
                    try { f.appendText("\n--- RESPONSE_OBJECT_DUMP ---\n", Charsets.UTF_8) } catch (_: Throwable) {}
                    try { f.appendText("[DUMP] class: ${lastResponseObj.javaClass.name}\n", Charsets.UTF_8) } catch (_: Throwable) {}
                    try { f.appendText("[DUMP] toString: ${lastResponseObj.toString()}\n", Charsets.UTF_8) } catch (_: Throwable) {}
                    try {
                        for (fld in lastResponseObj.javaClass.declaredFields) {
                            try {
                                fld.isAccessible = true
                                val v = fld.get(lastResponseObj)
                                f.appendText("[DUMP] field ${fld.name} = ${v}\n", Charsets.UTF_8)
                            } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {}
                    try {
                        for (mth in lastResponseObj.javaClass.methods) {
                            try {
                                if (mth.parameterCount == 0 && (mth.name.startsWith("get") || mth.name.startsWith("is"))) {
                                    try {
                                        val gv = mth.invoke(lastResponseObj)
                                        f.appendText("[DUMP] getter ${mth.name}() = ${gv}\n", Charsets.UTF_8)
                                    } catch (_: Throwable) {}
                                }
                            } catch (_: Throwable) {}
                        }
                    } catch (_: Throwable) {}
                    try { f.appendText("--- END_RESPONSE_OBJECT_DUMP ---\n", Charsets.UTF_8) } catch (_: Throwable) {}
                } else {
                    try { f.appendText("[DUMP] response object: null\n", Charsets.UTF_8) } catch (_: Throwable) {}
                }

                // token ids
                try {
                    if (tokenIdsCaptured == null) {
                        f.appendText("[DUMP] TokenIDs: (not exposed)\n", Charsets.UTF_8)
                    } else {
                        when (tokenIdsCaptured) {
                            is IntArray -> f.appendText("[METRICS] TokenIDs: ${tokenIdsCaptured.joinToString(",")}\n", Charsets.UTF_8)
                            is Array<*> -> f.appendText("[METRICS] TokenIDs: ${tokenIdsCaptured.joinToString(",")}\n", Charsets.UTF_8)
                            is java.util.List<*> -> f.appendText("[METRICS] TokenIDs: ${(tokenIdsCaptured as java.util.List<*>).joinToString(",")}\n", Charsets.UTF_8)
                            else -> f.appendText("[METRICS] TokenIDs (raw): ${tokenIdsCaptured.toString()}\n", Charsets.UTF_8)
                        }
                        try { f.appendText("[METRICS] TokenIDs (toString): ${tokenIdsCaptured.toString()}\n", Charsets.UTF_8) } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}

                if (finishReasonVal != null) f.appendText("[METRICS] FinishReason: $finishReasonVal\n", Charsets.UTF_8)
                else f.appendText("[METRICS] FinishReason: UNKNOWN\n", Charsets.UTF_8)
            } catch (_: Throwable) {
                // ignore logging failures
            }
        } catch (_: Throwable) {
            // swallow outer errors
        }
        return tokenCountForResult
    }
}