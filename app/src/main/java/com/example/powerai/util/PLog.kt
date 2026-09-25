package com.example.powerai.util

import android.util.Log
import com.example.powerai.BuildConfig

/**
 * PowerAi Log wrapper.
 * Handles debug-only logging and desensitization for release builds.
 */
object PLog {
    private const val TAG_PREFIX = "PowerAi_"

    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG_PREFIX + tag, msg)
        }
    }

    fun i(tag: String, msg: String) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG_PREFIX + tag, msg)
        }
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG_PREFIX + tag, desensitize(msg))
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(TAG_PREFIX + tag, desensitize(msg), tr)
    }

    /**
     * Basic desensitization to avoid leaking potential keys or sensitive paths in release logs.
     */
    private fun desensitize(msg: String): String {
        if (BuildConfig.DEBUG) return msg

        return msg
            // Mask UUIDs
            .replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "UUID-***")
            // Mask common API key patterns or long hex strings (>= 32 chars)
            .replace(Regex("[a-zA-Z0-9]{32,}"), "***")
            // Mask sensitive paths
            .replace(Regex("/data/user/\\d+/"), "/data/app/")
            // Mask potential IP addresses
            .replace(Regex("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b"), "IP-***")
    }
}
