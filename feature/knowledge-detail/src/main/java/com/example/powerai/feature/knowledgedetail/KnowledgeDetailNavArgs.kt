package com.example.powerai.feature.knowledgedetail

/**
 * SavedStateHandle / Nav argument keys for the detail route.
 * Values must stay identical to app Screen.Detail route args.
 */
object KnowledgeDetailNavArgs {
    const val ARG_ID = "id"
    const val ARG_Q = "q"
    const val ARG_BLOCK_INDEX = "blockIndex"
    const val ARG_BLOCK_ID = "blockId"

    fun createRoute(
        id: Long,
        encodedQuery: String,
        blockIndex: Int?,
        blockId: String?,
    ): String {
        val bi = blockIndex ?: -1
        val bid = blockId.orEmpty()
        return "detail/$id?q=$encodedQuery&blockIndex=$bi&blockId=$bid"
    }
}

/**
 * PDF viewer route builder — format must stay identical to app Screen.PdfViewer.
 */
object PdfViewerRoute {
    fun create(
        fileId: String,
        name: String,
        page: Int?,
        bboxEncoded: String?
    ): String {
        val p = page ?: -1
        val b = bboxEncoded.orEmpty()
        return "pdf/$fileId/$name?page=$p&bbox=$b"
    }
}
