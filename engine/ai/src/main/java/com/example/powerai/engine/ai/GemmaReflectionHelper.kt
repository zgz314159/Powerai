package com.example.powerai.engine.ai

/**
 * Helper responsible for reflectively invoking generation methods on a MediaPipe
 * Gemma engine instance. Factored out of [GemmaInferenceEngine] to reduce class
 * size and enable targeted unit testing.
 */
object GemmaReflectionHelper {
    /**
     * Result from a reflective invocation.
     */
    data class InvocationResult(
        val text: String?,
        val finishReason: String?,
        val tokenIds: Any?,
        val raw: Any?
    )

    private val methodNames =
        listOf("generate", "generateResponse", "generateResponseAsync", "run", "predict", "createResponse")

    /**
     * Attempt to invoke a textual generation on the given engine object.  Returns
     * an [InvocationResult] capturing the first successful response or nulls if
     * nothing could be called.
     */
    fun invokeGenerate(engineObj: Any, prompt: String, genOpts: Map<String, Any>): InvocationResult {
        val cls = engineObj.javaClass
        var respText: String? = null
        var finishReasonVal: String? = null
        var tokenIdsCaptured: Any? = null
        var lastResponseObj: Any? = null

        for (mn in methodNames) {
            try {
                val methods = cls.methods.filter { it.name == mn }
                for (m in methods) {
                    try {
                        val pts = m.parameterTypes
                        val args = mutableListOf<Any?>()
                        when {
                            pts.isEmpty() -> Unit
                            pts.size == 1 ->
                                if (pts[0].isAssignableFrom(String::class.java)) args.add(prompt) else args.add(null)
                            pts.size == 2 -> {
                                if (pts[0].isAssignableFrom(String::class.java)) {
                                    args.add(prompt)
                                    if (java.util.Map::class.java.isAssignableFrom(pts[1])) {
                                        val map = java.util.HashMap<String, Any>()
                                        map.putAll(genOpts)
                                        args.add(map)
                                    } else {
                                        args.add(null)
                                    }
                                } else continue
                            }
                            else -> continue
                        }

                        // handle potential async listener parameter by injecting a simple proxy
                        if (mn == "generateResponseAsync" && args.size <= 2 && pts.size >= 2) {
                            val listenerType = pts[1]
                            val proxy = java.lang.reflect.Proxy.newProxyInstance(
                                listenerType.classLoader,
                                arrayOf(listenerType)
                            ) { _, method, pArgs ->
                                // When callback invoked, attempt to pull text/finish reason
                                try {
                                    if (pArgs != null && pArgs.isNotEmpty()) {
                                        // first argument often contains a response object
                                        lastResponseObj = pArgs[0]
                                        // try to call toString() for text
                                        respText = pArgs[0]?.toString()
                                    }
                                } catch (_: Throwable) {}
                                null
                            }
                            if (args.size == 1) args.add(proxy) else args[1] = proxy
                        }

                        val ret = m.invoke(engineObj, *args.toTypedArray())
                        lastResponseObj = ret ?: lastResponseObj
                        // attempt to extract text field
                        respText = respText ?: ret?.toString()
                        // try to read finishReason field reflectively
                        try {
                            val fr = ret?.javaClass?.getDeclaredField("finishReason")
                            if (fr != null) {
                                fr.isAccessible = true
                                finishReasonVal = fr.get(ret) as? String
                            }
                        } catch (_: Throwable) {}
                        // try token ids
                        try {
                            val ti = ret?.javaClass?.getDeclaredField("tokenIds")
                            if (ti != null) {
                                ti.isAccessible = true
                                tokenIdsCaptured = ti.get(ret)
                            }
                        } catch (_: Throwable) {}

                        if (respText != null) return InvocationResult(respText, finishReasonVal, tokenIdsCaptured, lastResponseObj)
                    } catch (_: Throwable) {
                        // ignore individual method invocation failures
                    }
                }
            } catch (_: Throwable) {
                // ignore
            }
        }
        return InvocationResult(respText, finishReasonVal, tokenIdsCaptured, lastResponseObj)
    }

    /**
     * Apply sampling-related settings to a generic builder via reflection.  This
     * mirrors the logic previously buried in [GemmaModelLoader].
     */
    fun applySampling(builder: Any) {
        try {
            val bt = builder.javaClass
            try {
                val mTemp = bt.getMethod("setTemperature", java.lang.Float.TYPE)
                mTemp.invoke(builder, 0.75f)
            } catch (_: Throwable) {
            }
            try {
                val mTopK = bt.getMethod("setTopK", Integer.TYPE)
                mTopK.invoke(builder, 40)
            } catch (_: Throwable) {
            }
            try {
                val mRep = bt.getMethod("setRepetitionPenalty", java.lang.Float.TYPE)
                mRep.invoke(builder, 1.2f)
            } catch (_: Throwable) {
            }
        } catch (_: Throwable) {
            // ignore overall reflection failures
        }
    }
}
