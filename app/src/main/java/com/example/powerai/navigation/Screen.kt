package com.example.powerai.navigation

sealed class Screen(val route: String) {
    object Hybrid : Screen("hybrid")
    object Home : Screen("home")
    object Settings : Screen("settings")
    object JsonRepo : Screen("jsonrepo")

    object Detail : Screen("detail/{id}?q={q}&blockIndex={blockIndex}&blockId={blockId}") {
        const val ARG_ID = "id"
        const val ARG_Q = "q"
        const val ARG_BLOCK_INDEX = "blockIndex"
        const val ARG_BLOCK_ID = "blockId"

        fun createRoute(id: Long, encodedQuery: String, blockIndex: Int?, blockId: String?): String {
            val bi = blockIndex ?: -1
            val bid = blockId.orEmpty()
            return "detail/$id?q=$encodedQuery&blockIndex=$bi&blockId=$bid"
        }
    }

    object PdfViewer : Screen("pdf/{fileId}/{name}?page={page}&bbox={bbox}") {
        const val ARG_FILE_ID = "fileId"
        const val ARG_NAME = "name"
        const val ARG_PAGE = "page"
        const val ARG_BBOX = "bbox"

        fun createRoute(fileId: String, name: String, page: Int?, bboxEncoded: String?): String {
            val p = page ?: -1
            val b = bboxEncoded.orEmpty()
            return "pdf/$fileId/$name?page=$p&bbox=$b"
        }
    }
}
