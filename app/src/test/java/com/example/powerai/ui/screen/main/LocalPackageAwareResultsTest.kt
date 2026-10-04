package com.example.powerai.ui.screen.main

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.powerai.core.data.database.AppDatabase
import com.example.powerai.core.data.importer.StreamingJsonResourceImporter
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.model.KnowledgePackages
import com.example.powerai.data.repository.RoomFtsRetriever
import com.example.powerai.domain.usecase.LocalEvidenceRefiner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: two KB packages can legitimately carry the **same** title/content and the same PDF
 * `source` (e.g. a legacy built-in 103号(2) asset and a freshly imported user directory of the same
 * document). They are distinct knowledge and must not be silently merged — otherwise the user's
 * imported package becomes unreachable from the formal "本地" search UI.
 *
 * Covers the three places that used to merge purely on content/title:
 *  1. the local evidence refiner (`LocalEvidenceRefiner.clusterKey`),
 *  2. the local result list selection (`LocalSearchArea` / `applySafeFiltering`),
 *  3. the result → detail click target (each package keeps its own row id).
 */
@Config(sdk = [28], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class LocalPackageAwareResultsTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                AppDatabase::class.java,
            ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Imports the same KB JSON into a distinct package id (packageId == [packageId]). */
    private fun importPackage(packageId: String) {
        runBlocking {
            StreamingJsonResourceImporter(db.knowledgeDao()).importFromJson(
                inputStream = SHARED_KB.byteInputStream(Charsets.UTF_8),
                batchSize = 16,
                trace = null,
                fallbackFileName = "103号(2).pdf",
                fallbackFileId = packageId,
            ).collect { }
        }
    }

    @Test
    fun `retrieval and refiner keep identical content from two packages`() {
        importPackage("asset-legacy-103")
        importPackage("user:pkg103")

        val results = runBlocking { RoomFtsRetriever(db.knowledgeDao()).search(QUERY, 10) }
        assertEquals("both packages must be retrieved", 2, results.size)
        assertEquals(
            setOf("asset-legacy-103", "user:pkg103"),
            results.mapNotNull { it.item?.packageId }.toSet(),
        )

        val refined = LocalEvidenceRefiner.refine(QUERY, results, displayLimit = 10)
        assertEquals("refiner must not collapse two packages", 2, refined.retrievals.size)
        assertEquals(
            setOf("asset-legacy-103", "user:pkg103"),
            refined.retrievals.mapNotNull { it.item?.packageId }.toSet(),
        )
    }

    @Test
    fun `local list selection keeps cross-package rows and collapses same-package duplicates`() {
        val user = item(id = 1L, packageId = "user:pkg103")
        val asset = item(id = 2L, packageId = "asset-legacy-103")
        val userDuplicate = item(id = 3L, packageId = "user:pkg103")

        val cross = selectLocalDisplayResults(listOf(asset, user), pageSize = 10)
        assertEquals("identical content in different packages stays visible", 2, cross.size)

        val samePackage = selectLocalDisplayResults(listOf(user, userDuplicate), pageSize = 10)
        assertEquals("true duplicates inside one package still collapse", 1, samePackage.size)

        assertEquals(2, applySafeFiltering(listOf(asset, user)).size)
        assertEquals(1, applySafeFiltering(listOf(user, userDuplicate)).size)
    }

    @Test
    fun `each package result opens its own detail row`() {
        importPackage("asset-legacy-103")
        importPackage("user:pkg103")

        val refined =
            LocalEvidenceRefiner.refine(
                QUERY,
                runBlocking { RoomFtsRetriever(db.knowledgeDao()).search(QUERY, 10) },
                displayLimit = 10,
            )
        val items = refined.retrievals.mapNotNull { it.item }
        assertEquals(2, items.size)

        val userItem = items.first { KnowledgePackages.isUserPackage(it.packageId) }
        val assetItem = items.first { !KnowledgePackages.isUserPackage(it.packageId) }
        assertTrue("user and asset rows must have distinct ids", userItem.id != assetItem.id)

        val userTarget = knowledgeDetailTargetOrNull(userItem, QUERY)
        val assetTarget = knowledgeDetailTargetOrNull(assetItem, QUERY)
        assertNotNull(userTarget)
        assertNotNull(assetTarget)
        assertEquals("clicking the user row opens the user row", userItem.id, userTarget!!.id)
        assertEquals("clicking the asset row opens the asset row", assetItem.id, assetTarget!!.id)

        // The clicked ids resolve to rows owned by different packages.
        val userPackage = runBlocking { db.knowledgeDao().getById(userTarget.id) }?.packageId
        val assetPackage = runBlocking { db.knowledgeDao().getById(assetTarget.id) }?.packageId
        assertEquals("user:pkg103", userPackage)
        assertFalse("asset row must not land on the user package", assetPackage == "user:pkg103")
    }

    @Test
    fun `rows without a package id still collapse by content`() {
        val legacyA = item(id = 1L, packageId = null)
        val legacyB = item(id = 2L, packageId = null)
        assertEquals(1, selectLocalDisplayResults(listOf(legacyA, legacyB), pageSize = 10).size)
    }

    private fun item(
        id: Long,
        packageId: String?,
    ): KnowledgeItem =
        KnowledgeItem(
            id = id,
            title = "Page 29",
            content = "内燃机带负荷磨合的运转时间表",
            source = SHARED_SOURCE,
            category = "",
            keywords = emptyList(),
            packageId = packageId,
        )

    private companion object {
        const val QUERY = "内燃机带负荷磨合的运转时间表"
        const val SHARED_SOURCE =
            "pdf:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa::103号(2).pdf"

        val SHARED_KB = """
        {
          "fileMetadata": { "fileName": "103号(2).pdf", "source": "$SHARED_SOURCE" },
          "entries": [
            {
              "entryId": "103_p29_tbl1", "jobTitle": "Page 29", "pageNumber": 29, "position": 1, "kind": "table",
              "contentMarkdown": "内燃机带负荷磨合的运转时间表\n\n| 顺号 | 负荷占额定容量的% |\n| --- | --- |\n| 1 | 25 |",
              "contentNormalized": "内燃机带负荷磨合的运转时间表 顺号 负荷 1 25",
              "blocks": [
                { "id": "p29_cap", "type": "code", "code": "内燃机带负荷磨合的运转时间表 表1", "pageNumber": 29, "semanticRole": "caption", "searchable": true,
                  "bbox": { "left": 40, "top": 194, "right": 202, "bottom": 203 } },
                { "id": "p29_tbl1", "type": "table", "rows": [["顺号", "负荷占额定容量的%"], ["1", "25"]], "pageNumber": 29, "semanticRole": "table", "searchable": true,
                  "bbox": { "left": 22, "top": 206, "right": 221, "bottom": 292 }, "imageUri": "shots/p29_tbl1.png", "src": "shots/p29_tbl1.png" }
              ]
            }
          ]
        }
        """
    }
}
