@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package org.tuchscherer.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.PostField
import org.tuchscherer.database.repositories.PostProjection
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.PostContentResolver
import viaduct.api.grts.BlogPost
import viaduct.api.grts.Query
import viaduct.api.testing.ResolverTestBase
import java.util.UUID

class PostContentResolverTest : ResolverTestBase() {
    private val repository = mockk<PostRepository>()

    @Test
    fun `batched content preserves context order including duplicate posts`() = runBlocking<Unit> {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { repository.findProjectedByIds(listOf(first, second), setOf(PostField.CONTENT)) } returns mapOf(
            first to row(first, "First body"),
            second to row(second, "Second body"),
        )

        val result = runFieldBatchResolver(PostContentResolver(repository)) {
            objectValues = listOf(post(first), post(second), post(first))
            queryValues = List(3) { Query.Builder(context).build() }
        }

        assertEquals(listOf("First body", "Second body", "First body"), result.map { it.get() })
    }

    @Test
    fun `post disappearing before content lookup produces a field error`() = runBlocking<Unit> {
        val missing = UUID.randomUUID()
        every { repository.findProjectedByIds(listOf(missing), setOf(PostField.CONTENT)) } returns emptyMap()

        val result = runFieldBatchResolver(PostContentResolver(repository)) {
            objectValues = listOf(post(missing))
            queryValues = listOf(Query.Builder(context).build())
        }

        assertTrue(result.single().isError)
        assertInstanceOf(NotFoundException::class.java, runCatching { result.single().get() }.exceptionOrNull())
    }

    @Test
    fun `empty batch returns no content`() = runBlocking<Unit> {
        assertTrue(PostContentResolver(repository).batchResolve(emptyList()).isEmpty())
    }

    private fun row(id: UUID, content: String) =
        PostProjection(id, PostType.BLOG_POST, setOf(PostField.CONTENT), content = content)

    private fun post(id: UUID) = BlogPost.Builder(context)
        .id(globalIDFor(BlogPost.Reflection, id.toString())).build()
}
