package org.tuchscherer.resolverkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

private data class Entity(val name: String)

class NodeBatchResolutionTest {

    @Test
    fun `looks up every id in a single batched call`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        var findByIdsCallCount = 0

        batchNodeResolve(
            contexts = listOf(id1, id2),
            extractId = { it },
            findByIds = { ids ->
                findByIdsCallCount++
                ids.associateWith { Entity("entity-$it") }
            },
            transform = { entity, _ -> entity.name },
            notFound = { id -> NoSuchElementException("not found: $id") },
        )

        assertEquals(1, findByIdsCallCount)
    }

    @Test
    fun `transforms found entities and keys results by context`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()

        val results = batchNodeResolve(
            contexts = listOf(id1, id2),
            extractId = { it },
            findByIds = { ids -> ids.associateWith { Entity("entity-$it") } },
            transform = { entity, ctx -> "${entity.name}-for-$ctx" },
            notFound = { id -> NoSuchElementException("not found: $id") },
        )

        assertEquals(2, results.size)
        assertEquals("entity-$id1-for-$id1", results[id1]?.get())
        assertEquals("entity-$id2-for-$id2", results[id2]?.get())
    }

    @Test
    fun `reports a not-found error for ids findByIds does not return`() {
        val missingId = UUID.randomUUID()

        val results = batchNodeResolve(
            contexts = listOf(missingId),
            extractId = { it },
            findByIds = { emptyMap() },
            transform = { entity: Entity, _: UUID -> entity.name },
            notFound = { id -> NoSuchElementException("not found: $id") },
        )

        assertEquals(1, results.size)
        assertTrue(results[missingId]!!.isError)
        val error = runCatching { results[missingId]!!.get() }.exceptionOrNull()
        assertTrue(error is NoSuchElementException, "Expected NoSuchElementException, got $error")
        assertTrue(error!!.message!!.contains(missingId.toString()))
    }

    @Test
    fun `handles a mix of found and missing ids independently`() {
        val foundId = UUID.randomUUID()
        val missingId = UUID.randomUUID()

        val results = batchNodeResolve(
            contexts = listOf(foundId, missingId),
            extractId = { it },
            findByIds = { _ -> mapOf(foundId to Entity("found")) },
            transform = { entity, _ -> entity.name },
            notFound = { id -> NoSuchElementException("not found: $id") },
        )

        assertEquals(2, results.size)
        assertEquals("found", results[foundId]?.get())
        assertTrue(results[missingId]!!.isError)
    }

    @Test
    fun `returns an empty map for an empty batch without calling findByIds`() {
        var findByIdsCallCount = 0

        val results = batchNodeResolve<UUID, Entity, String>(
            contexts = emptyList(),
            extractId = { it },
            findByIds = { ids ->
                findByIdsCallCount++
                ids.associateWith { Entity("entity-$it") }
            },
            transform = { entity, _ -> entity.name },
            notFound = { id -> NoSuchElementException("not found: $id") },
        )

        assertEquals(1, findByIdsCallCount, "findByIds should still be called once, with an empty id list")
        assertTrue(results.isEmpty())
    }
}
