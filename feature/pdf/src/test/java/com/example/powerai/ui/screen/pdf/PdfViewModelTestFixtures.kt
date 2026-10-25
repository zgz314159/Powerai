package com.example.powerai.ui.screen.pdf

import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.repository.KnowledgeRepository
import com.example.powerai.domain.usecase.PdfFigureListUseCase
import com.example.powerai.domain.usecase.PdfKnowledgeUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Runs [body] on a single virtual scheduler; no real time is involved. */
internal fun runPdfVmTest(body: suspend TestScope.() -> Unit) =
    runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            body()
        } finally {
            Dispatchers.resetMain()
        }
    }

internal fun TestScope.pdfFigureViewModel(dao: KnowledgeDao): PdfFigureListViewModel =
    PdfFigureListViewModel(
        PdfFigureListUseCase(dao),
        StandardTestDispatcher(testScheduler),
    )

internal fun TestScope.pdfKnowledgeViewModel(repository: KnowledgeRepository): PdfKnowledgeViewModel =
    PdfKnowledgeViewModel(
        PdfKnowledgeUseCase(repository),
        StandardTestDispatcher(testScheduler),
    )

internal fun pdfEntity(
    source: String = "assets/kb/doc",
    pageNumber: Int? = null,
    blocksJson: String? = null,
): KnowledgeEntity =
    KnowledgeEntity(
        title = "title",
        content = "content",
        source = source,
        pageNumber = pageNumber,
        contentBlocksJson = blocksJson,
    )

internal fun pdfItem(
    id: Long,
    blocksJson: String? = null,
): KnowledgeItem =
    KnowledgeItem(
        id = id,
        title = "title-$id",
        content = "content",
        source = "source.pdf",
        category = "分类",
        keywords = emptyList(),
        contentBlocksJson = blocksJson,
    )
