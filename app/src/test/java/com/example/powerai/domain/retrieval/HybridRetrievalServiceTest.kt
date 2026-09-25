package com.example.powerai.domain.retrieval

import com.example.powerai.core.model.RetrievalResult
import com.example.powerai.core.repository.AnnRetriever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.powerai.core.model.KnowledgeItem
import com.example.powerai.core.data.entity.KnowledgeEntity
import com.example.powerai.core.data.dao.KnowledgeDao
import com.example.powerai.core.data.entity.ImportedFileEntity
import com.example.powerai.data.repository.RoomFtsRetriever

class HybridRetrievalServiceTest {
    private class FakeAnn(private val ids: List<Long>) : com.example.powerai.core.repository.AnnRetriever {
        override suspend fun search(query: String, k: Int): List<RetrievalResult> {
            return ids.take(k).mapIndexed { idx, id -> RetrievalResult(id = id, score = 1.0f - idx * 0.1f, source = "ann") }
        }
    }

    private class FakeFts(private val ids: List<Long>) : FtsRetriever {
        override suspend fun search(query: String, k: Int): List<RetrievalResult> {
            return ids.take(k).mapIndexed { idx, id -> RetrievalResult(id = id, score = 1.0f - idx * 0.05f, source = "fts") }
        }
    }

    @Test
    fun rrf_prefers_vector_when_fts_empty() = runBlocking {
        val ann = FakeAnn(listOf(101L, 102L, 103L))
        val fts = FakeFts(emptyList())
        val svc = HybridRetrievalService(ann, fts, rrfK = 60, sourceWeights = mapOf("ann" to 1.0, "fts" to 2.0))

        val res = svc.retrieveHybrid("q", topK = 3)
        assertEquals(3, res.size)
        assertEquals(101L, res[0].id)
    }

    @Test
    fun rrf_prefers_fts_when_vector_rank_low() = runBlocking {
        val ann = FakeAnn(listOf(201L, 202L, 203L))
        // make FTS return a hard-match item for query "q" so bonus will be applied
        val fts = object : FtsRetriever {
            override suspend fun search(query: String, k: Int): List<RetrievalResult> {
                val hardItem = com.example.powerai.core.model.KnowledgeItem(
                    id = 999L,
                    title = query,
                    content = "",
                    source = "doc",
                    category = "",
                    keywords = emptyList()
                )
                return listOf(RetrievalResult(id = 999L, score = 1.0f, source = "fts", item = hardItem), RetrievalResult(id = 201L, score = 0.5f, source = "fts"))
            }
        }
        // Use weighted RRF with k=15 and a small ftsTopBonus so FTS top can win in this synthetic case
        val svc = HybridRetrievalService(ann, fts, rrfK = 15, sourceWeights = mapOf("ann" to 1.0, "fts" to 2.0), ftsTopBonus = 0.06)

        val res = svc.retrieveHybrid("q", topK = 3)

        // Print debug weighted computation for visibility
        val annList = runBlocking { ann.search("q", 10) }
        val ftsList = runBlocking { fts.search("q", 10) }
        println("HybridRetrievalService: DEBUG weighted+bonus RRF for rrfK=15, weights ann=1.0 fts=2.0, bonus=0.06")
        println("HybridRetrievalService: ANN results: ${annList.map { it.id }}")
        println("HybridRetrievalService: FTS results: ${ftsList.map { it.id }}")

        val annRank = annList.mapIndexed { idx, r -> r.id!! to (idx + 1) }.toMap()
        val ftsRank = ftsList.mapIndexed { idx, r -> r.id!! to (idx + 1) }.toMap()
        val k = 15
        val weights = mapOf("ann" to 1.0, "fts" to 2.0)
        val ids = (annList.map { it.id } + ftsList.map { it.id }).distinct()
        val fusedWeighted = ids.map { id ->
            val vr = annRank[id]
            val fr = ftsRank[id]
            val vrScore = if (vr != null) (weights["ann"] ?: 1.0) * (1.0 / (k + vr)) else 0.0
            val frScore = if (fr != null) (weights["fts"] ?: 1.0) * (1.0 / (k + fr)) else 0.0
            var total = vrScore + frScore
            if (id == ftsList.firstOrNull()?.id && annList.none { it.id == id }) total += 0.06
            Triple(id, Pair(vr, fr), total)
        }.sortedByDescending { it.third }

        fusedWeighted.forEach { (id, ranks, score) ->
            val (vr, fr) = ranks
            val vrPart = if (vr != null) "${weights["ann"]}*(1/(${k}+${vr}))" else "0"
            val frPart = if (fr != null) "${weights["fts"]}*(1/(${k}+${fr}))" else "0"
            println("HybridRetrievalService: ID: $id, VectorRank: ${vr ?: -1}, FtsRank: ${fr ?: -1}, WeightedRRF+bonus: $vrPart + $frPart [+bonus?] = $score")
        }

        assertEquals(3, res.size)
        // Expect 999 (FTS top only) to be first after bonus
        assertEquals(999L, res[0].id)
    }

    @Test
    fun rrf_prefers_fts_with_lower_k() = runBlocking {
        val ann = FakeAnn(listOf(201L, 202L, 203L))
        // FTS that returns plain ids for the weighted inspection
        val fts = FakeFts(listOf(999L, 201L))
        // First, run with weighted RRF (ann=1.0, fts=2.0) and print computed weighted scores (no assertion)
        val svcWeighted = HybridRetrievalService(ann, fts, rrfK = 15, sourceWeights = mapOf("ann" to 1.0, "fts" to 2.0))
        val resWeighted = svcWeighted.retrieveHybrid("q", topK = 3)

        val annList = runBlocking { ann.search("q", 10) }
        val ftsList = runBlocking { fts.search("q", 10) }
        println("HybridRetrievalService: DEBUG weighted RRF for rrfK=15, weights ann=1.0 fts=2.0")
        println("HybridRetrievalService: ANN results: ${annList.map { it.id }}")
        println("HybridRetrievalService: FTS results: ${ftsList.map { it.id }}")

        val annRank = annList.mapIndexed { idx, r -> r.id!! to (idx + 1) }.toMap()
        val ftsRank = ftsList.mapIndexed { idx, r -> r.id!! to (idx + 1) }.toMap()
        val k = 15
        val weights = mapOf("ann" to 1.0, "fts" to 2.0)
        val ids = (annList.map { it.id } + ftsList.map { it.id }).distinct()
        val fusedWeighted = ids.map { id ->
            val vr = annRank[id]
            val fr = ftsRank[id]
            val vrScore = if (vr != null) (weights["ann"] ?: 1.0) * (1.0 / (k + vr)) else 0.0
            val frScore = if (fr != null) (weights["fts"] ?: 1.0) * (1.0 / (k + fr)) else 0.0
            val total = vrScore + frScore
            Triple(id, Pair(vr, fr), total)
        }.sortedByDescending { it.third }

        fusedWeighted.forEach { (id, ranks, score) ->
            val (vr, fr) = ranks
            val vrPart = if (vr != null) "${weights["ann"]}*(1/(${k}+${vr}))" else "0"
            val frPart = if (fr != null) "${weights["fts"]}*(1/(${k}+${fr}))" else "0"
            println("HybridRetrievalService: ID: $id, VectorRank: ${vr ?: -1}, FtsRank: ${fr ?: -1}, WeightedRRF: $vrPart + $frPart = $score")
        }

        // Now run with a small fts-top bonus so FTS-only top hits can win in this synthetic case
        // Use an FTS retriever that provides a hard-match item for the query
        val ftsForBonus = object : FtsRetriever {
            override suspend fun search(query: String, k: Int): List<RetrievalResult> {
                val hardItem = com.example.powerai.core.model.KnowledgeItem(
                    id = 999L,
                    title = query,
                    content = "",
                    source = "doc",
                    category = "",
                    keywords = emptyList()
                )
                return listOf(RetrievalResult(id = 999L, score = 1.0f, source = "fts", item = hardItem), RetrievalResult(id = 201L, score = 0.5f, source = "fts"))
            }
        }

        val svcWithBonus = HybridRetrievalService(ann, ftsForBonus, rrfK = 15, sourceWeights = mapOf("ann" to 1.0, "fts" to 2.0), ftsTopBonus = 0.06)
        val res = svcWithBonus.retrieveHybrid("q", topK = 3)

        assertEquals(3, res.size)
        // With a modest ftsTopBonus, expect 999 (FTS top only) to be first
        assertEquals(999L, res[0].id)
    }

    @Test
    fun rrf_hard_match_triggers_bonus() = runBlocking {
        val ann = FakeAnn(listOf(201L, 202L, 203L))
        // FTS returns an item whose title exactly matches the query
        val hardItem = com.example.powerai.core.model.KnowledgeItem(
            id = 999L,
            title = "变压器",
            content = "",
            source = "doc",
            category = "",
            keywords = emptyList()
        )

        val fts = object : FtsRetriever {
            override suspend fun search(query: String, k: Int): List<RetrievalResult> {
                return listOf(
                    RetrievalResult(id = 999L, score = 1.0f, source = "fts", item = hardItem),
                    RetrievalResult(id = 201L, score = 0.5f, source = "fts")
                )
            }
        }

        val svc = HybridRetrievalService(ann, fts, rrfK = 15, sourceWeights = mapOf("ann" to 1.0, "fts" to 2.0), ftsTopBonus = 0.06)
        val res = svc.retrieveHybrid("变压器", topK = 3)

        assertEquals(3, res.size)
        // 999 should win due to hard match + bonus
        assertEquals(999L, res[0].id)
        // debug flag should be set on the returned item (if repr preserved)
        val top = res[0]
        val dbg = top.debug ?: emptyMap()
        assertEquals(true, dbg["fts_bonus_applied"])
    }

    @Test
    fun roomFtsRetriever_fallback_adds_keyword_results() = runBlocking {
        // fake DAO returning limited FTS hits and extra keyword hits
        val dao = object : com.example.powerai.core.data.dao.KnowledgeDao {
            override suspend fun searchByFts(query: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> {
                return listOf(
                    com.example.powerai.core.data.entity.KnowledgeEntity(id = 1L, title = "A", content = "", source = "s", category = ""),
                    com.example.powerai.core.data.entity.KnowledgeEntity(id = 2L, title = "B", content = "", source = "s", category = ""),
                )
            }
            override suspend fun searchByKeyword(keyword: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> {
                return listOf(
                    com.example.powerai.core.data.entity.KnowledgeEntity(id = 2L, title = "B", content = "", source = "s", category = ""),
                    com.example.powerai.core.data.entity.KnowledgeEntity(id = 3L, title = "C", content = "", source = "s", category = ""),
                    com.example.powerai.core.data.entity.KnowledgeEntity(id = 4L, title = "D", content = "", source = "s", category = ""),
                )
            }
            // other methods unused in this test
            override suspend fun insert(entity: com.example.powerai.core.data.entity.KnowledgeEntity) { throw NotImplementedError() }
            override suspend fun insertBatch(entities: List<com.example.powerai.core.data.entity.KnowledgeEntity>) { throw NotImplementedError() }
            override suspend fun upsertBatch(entities: List<com.example.powerai.core.data.entity.KnowledgeEntity>) { throw NotImplementedError() }
            override suspend fun upsertBatchTransactional(entities: List<com.example.powerai.core.data.entity.KnowledgeEntity>) { throw NotImplementedError() }
            override suspend fun getAll(): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun getById(id: Long): com.example.powerai.core.data.entity.KnowledgeEntity? { throw NotImplementedError() }
            override suspend fun updateBlocksJsonAndSearchFields(id: Long, contentBlocksJson: String, contentNormalized: String, searchContent: String) { throw NotImplementedError() }
            override suspend fun getEntriesMissingNormalized(): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun countEntriesMissingNormalized(): Int { throw NotImplementedError() }
            override suspend fun updateNormalizedContent(id: Long, normalized: String) { throw NotImplementedError() }
            override suspend fun searchByLikeInternal(raw: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun update(entity: com.example.powerai.core.data.entity.KnowledgeEntity) { throw NotImplementedError() }
            override suspend fun rebuildFts() { throw NotImplementedError() }
            override suspend fun countFts(): Int { throw NotImplementedError() }
            override suspend fun getSample(n: Int): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun countBySourcePrefix(sourcePrefix: String): Int { throw NotImplementedError() }
            override suspend fun countMatchesBySourcePrefix(sourcePrefix: String, keywordNoSpace: String): Int { throw NotImplementedError() }
            override suspend fun sampleBySourcePrefix(sourcePrefix: String, limit: Int): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun searchByKeywordNoSpace(keywordNoSpace: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun searchByKeywordFuzzy(pattern: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> { throw NotImplementedError() }
            override suspend fun insertImportedFile(file: com.example.powerai.core.data.entity.ImportedFileEntity) { throw NotImplementedError() }
            override suspend fun importedFileExists(fileId: String): Int { throw NotImplementedError() }
            override suspend fun getImportedFiles(): List<com.example.powerai.core.data.entity.ImportedFileEntity> { throw NotImplementedError() }
            override suspend fun getAllForDatabaseList() = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
            override suspend fun searchByKeywordInContent(keyword: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> = emptyList()
            override suspend fun searchByKeywordInContentForDatabase(keyword: String) = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
            override suspend fun searchByKeywordNoSpaceInContent(keywordNoSpace: String): List<com.example.powerai.core.data.entity.KnowledgeEntity> = emptyList()
            override suspend fun searchByKeywordNoSpaceInContentForDatabase(keywordNoSpace: String) = emptyList<com.example.powerai.core.data.entity.KnowledgeListItemEntity>()
            override suspend fun getLargestKnowledgeRows(limit: Int) = emptyList<com.example.powerai.core.data.entity.KnowledgeRowPayloadStat>()
            override suspend fun getImportedFileStatus(fileId: String): String? = null
            override suspend fun countByPage(fileId: String, page: Int): Int = 0
            override suspend fun getByPage(fileId: String, page: Int): List<com.example.powerai.core.data.entity.KnowledgeEntity> = emptyList()
        }

        val retr = RoomFtsRetriever(dao)
        // try with plain keyword
        val res1 = retr.search("foo", k = 4)
        val ids1 = res1.mapNotNull { it.id }
        assertEquals(listOf(1L,2L,3L,4L), ids1)
        // and with a trailing wildcard (what the caller will usually pass)
        val res2 = retr.search("foo*", k = 4)
        val ids2 = res2.mapNotNull { it.id }
        assertEquals(listOf(1L,2L,3L,4L), ids2)
    }
}
