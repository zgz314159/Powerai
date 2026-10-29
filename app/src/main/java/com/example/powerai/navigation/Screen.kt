package com.example.powerai.navigation

import com.example.powerai.feature.knowledgedetail.KnowledgeDetailNavArgs
import com.example.powerai.feature.knowledgedetail.PdfViewerRoute

/**
 * App NavHost route table. Detail/Pdf builders delegate to feature nav contracts
 * so route strings stay single-sourced.
 */
sealed class Screen(val route: String) {
    object Hybrid : Screen("hybrid")

    object Home : Screen("home")

    object Settings : Screen("settings")

    object JsonRepo : Screen("jsonrepo")

    /** 用户知识库目录导入界面。 */
    object KbImport : Screen("kb-import")

    /** Sparse / 无 Room id 的条文正文详情。 */
    object AiTextDetail : Screen("ai-detail?q={q}") {
        const val ARG_Q = "q"
    }

    object Detail : Screen("detail/{id}?q={q}&blockIndex={blockIndex}&blockId={blockId}") {
        const val ARG_ID = KnowledgeDetailNavArgs.ARG_ID
        const val ARG_Q = KnowledgeDetailNavArgs.ARG_Q
        const val ARG_BLOCK_INDEX = KnowledgeDetailNavArgs.ARG_BLOCK_INDEX
        const val ARG_BLOCK_ID = KnowledgeDetailNavArgs.ARG_BLOCK_ID

        fun createRoute(
            id: Long,
            encodedQuery: String,
            blockIndex: Int?,
            blockId: String?,
        ): String = KnowledgeDetailNavArgs.createRoute(id, encodedQuery, blockIndex, blockId)
    }

    object PdfViewer : Screen("pdf/{fileId}/{name}?page={page}&bbox={bbox}") {
        const val ARG_FILE_ID = "fileId"
        const val ARG_NAME = "name"
        const val ARG_PAGE = "page"
        const val ARG_BBOX = "bbox"

        fun createRoute(
            fileId: String,
            name: String,
            page: Int?,
            bboxEncoded: String?,
        ): String = PdfViewerRoute.create(fileId, name, page, bboxEncoded)
    }
}
