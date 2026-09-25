package com.example.powerai.ui.screen.pdf

import com.google.gson.JsonParser

internal data class PdfBoundingBox(
    val xMin: Float,
    val yMin: Float,
    val xMax: Float,
    val yMax: Float
)

internal fun parsePdfBoundingBoxOrNull(bboxJson: String?): PdfBoundingBox? {
    if (bboxJson.isNullOrBlank()) return null
    return try {
        val el = JsonParser().parse(bboxJson)

        if (el.isJsonArray) {
            val arr = el.asJsonArray
            if (arr.size() == 4) {
                // V2 format: [x, y, w, h]
                val x = arr[0].asFloat
                val y = arr[1].asFloat
                val w = arr[2].asFloat
                val h = arr[3].asFloat
                return PdfBoundingBox(xMin = x, yMin = y, xMax = x + w, yMax = y + h)
            }
        }

        if (!el.isJsonObject) return null
        val obj = el.asJsonObject

        fun f(key: String): Float? {
            val v = obj.get(key) ?: return null
            if (!v.isJsonPrimitive) return null
            val p = v.asJsonPrimitive
            return when {
                p.isNumber -> p.asFloat
                p.isString -> p.asString.toFloatOrNull()
                else -> null
            }
        }

        val xMin = f("xMin") ?: f("left") ?: return null
        val yMin = f("yMin") ?: f("top") ?: return null
        val xMax = f("xMax") ?: f("right") ?: return null
        val yMax = f("yMax") ?: f("bottom") ?: return null

        if (xMax <= xMin || yMax <= yMin) return null
        PdfBoundingBox(xMin = xMin, yMin = yMin, xMax = xMax, yMax = yMax)
    } catch (_: Throwable) {
        null
    }
}
