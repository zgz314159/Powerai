package com.example.powerai.core.model.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest

/**
 * Shared JSON parsing utilities for structural content blocks.
 */
object BlocksJsonUtils {

    fun sha1Hex(s: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun parseRoot(json: String): JsonElement? {
        return try {
            JsonParser().parse(json)
        } catch (_: Throwable) {
            null
        }
    }

    fun extractBlocksArray(root: JsonElement?): JsonArray? {
        if (root == null || root.isJsonNull) return null
        if (root.isJsonArray) return root.asJsonArray
        if (!root.isJsonObject) return null

        val obj = root.asJsonObject

        // Support for V2 paginated structure
        val pages = obj.get("pages")
        if (pages != null && pages.isJsonArray) {
            val allBlocks = JsonArray()
            for (p in pages.asJsonArray) {
                val pObj = p.asJsonObjectOrNull() ?: continue
                val pBlocks = pObj.get("blocks")
                if (pBlocks != null && pBlocks.isJsonArray) {
                    allBlocks.addAll(pBlocks.asJsonArray)
                }
            }
            if (allBlocks.size() > 0) return allBlocks
        }

        val keys = listOf("blocks", "contentBlocks", "content_blocks")
        for (k in keys) {
            val v = obj.get(k)
            if (v != null && v.isJsonArray) return v.asJsonArray
        }
        return null
    }

    fun JsonElement.asJsonObjectOrNull(): JsonObject? =
        if (this.isJsonObject) this.asJsonObject else null

    fun JsonObject.stringOrNull(key: String): String? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        if (!prim.isString) return null
        return prim.asString
    }

    fun JsonObject.booleanOrNull(key: String): Boolean? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        if (!prim.isBoolean) return null
        return prim.asBoolean
    }

    fun JsonObject.intOrNull(key: String): Int? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asInt
            prim.isString -> prim.asString.toIntOrNull()
            else -> null
        }
    }

    fun JsonObject.floatOrNull(key: String): Float? {
        val el = get(key) ?: return null
        if (!el.isJsonPrimitive) return null
        val prim = el.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asFloat
            prim.isString -> prim.asString.toFloatOrNull()
            else -> null
        }
    }

    fun JsonObject.boundingBoxAsStringOrNull(): String? {
        val el = get("boundingBox") ?: get("bbox") ?: return null
        if (el.isJsonNull) return null

        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
            else -> el.toString()
        }.trim().takeIf { it.isNotBlank() }
    }
}
