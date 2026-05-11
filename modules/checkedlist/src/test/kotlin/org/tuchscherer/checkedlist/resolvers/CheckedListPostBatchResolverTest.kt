package org.tuchscherer.checkedlist.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.tuchscherer.checkedlist.port.PostCreationPort
import org.tuchscherer.viadapp.checkedlist.resolvers.CheckedListPostBatchResolver
import org.tuchscherer.viadapp.checkedlist.resolverbases.NodeResolvers
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.util.UUID
import viaduct.api.context.NodeExecutionContext
import viaduct.api.globalid.GlobalID
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.internal.InternalContext

/**
 * Unit tests for [CheckedListPostBatchResolver].
 *
 * Success-path return value verification (building ViaductCheckedListPost requires a real
 * InternalContext) is covered end-to-end by query-tests.sh. Here we pin:
 *   - error FieldValue is returned for missing IDs
 *   - results list length matches batch input length
 */
class CheckedListPostBatchResolverTest {

    private lateinit var postCreationPort: PostCreationPort

    @BeforeEach
    fun setup() {
        postCreationPort = mockk()

        GlobalContext.getOrNull()?.let { GlobalContext.stopKoin() }
        org.koin.core.context.startKoin {
            modules(module {
                single<PostCreationPort> { postCreationPort }
            })
        }
    }

    private fun mockContext(id: UUID): NodeResolvers.CheckedListPost.Context {
        val globalId = mockk<GlobalID<ViaductCheckedListPost>>()
        every { globalId.internalID } returns id.toString()

        val inner = mockk<NodeExecutionContext<ViaductCheckedListPost>>(
            relaxed = true,
            moreInterfaces = arrayOf(InternalContext::class),
        )
        every { inner.id } returns globalId
        return NodeResolvers.CheckedListPost.Context(inner)
    }

    @Test
    fun `returns error FieldValue when post not found`() = runBlocking {
        every { postCreationPort.getPostsData(any()) } returns emptyMap()

        val id = UUID.randomUUID()
        val results = CheckedListPostBatchResolver().batchResolve(listOf(mockContext(id)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
    }

    @Test
    fun `returns error for each missing ID in a batch`() = runBlocking {
        every { postCreationPort.getPostsData(any()) } returns emptyMap()

        val ids = List(3) { UUID.randomUUID() }
        val results = CheckedListPostBatchResolver().batchResolve(ids.map(::mockContext))

        assertEquals(3, results.size)
        assertTrue(results.all { it.isError })
    }
}
