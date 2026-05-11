@file:Suppress("DEPRECATION")

package org.tuchscherer.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.Comment
import org.tuchscherer.database.Like
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.BlogPostNodeResolver
import org.tuchscherer.viadapp.resolvers.CommentNodeResolver
import org.tuchscherer.viadapp.resolvers.LikeNodeResolver
import org.tuchscherer.viadapp.resolvers.UserNodeResolver
import org.tuchscherer.viadapp.resolvers.resolverbases.NodeResolvers
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser
import viaduct.api.testing.ResolverTestBase
import java.util.UUID

/**
 * Unit tests for the four NodeResolvers (User/Post/Comment/Like).
 *
 * These are the resolvers that power the Relay `node(id: ID!)` root query. They exist
 * only because the schema declares `type X implements Node @resolver` — drop the
 * `@resolver` directive and Viaduct stops generating the `NodeResolvers.X` base class,
 * breaking the build.
 *
 * Full result verification (the Viaduct builder call in the success path) requires a
 * real framework InternalContext; that is covered end-to-end by query-tests.sh's node(id)
 * probes. Here we verify:
 *   - the batch repository method is called exactly once per request
 *   - missing ids surface a NotFoundException FieldValue (not a RuntimeException)
 */
class NodeResolversTest : ResolverTestBase() {

    // ── UserNodeResolver ───────────────────────────────────────────────────────

    @Test
    fun `UserNodeResolver calls findByIds once for the whole batch`() = runBlocking {
        val repo = mockk<UserRepository>()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { repo.findByIds(listOf(id1, id2)) } returns mapOf(id1 to mockk(relaxed = true), id2 to mockk(relaxed = true))

        val resolver = UserNodeResolver(repo)
        runCatching { resolver.batchResolve(listOf(userNodeCtx(id1), userNodeCtx(id2))) }

        verify(exactly = 1) { repo.findByIds(listOf(id1, id2)) }
    }

    @Test
    fun `UserNodeResolver returns NotFoundException FieldValue for missing id`() = runBlocking {
        val repo = mockk<UserRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns emptyMap()

        val results = UserNodeResolver(repo).batchResolve(listOf(userNodeCtx(id)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        val err = runCatching { results[0].get() }.exceptionOrNull()
        assertTrue(err is NotFoundException, "Expected NotFoundException, got $err")
        assertTrue(err!!.message!!.contains(id.toString()))
    }

    // ── BlogPostNodeResolver ───────────────────────────────────────────────────

    @Test
    fun `BlogPostNodeResolver calls findByIds once for the whole batch`() = runBlocking {
        val repo = mockk<PostRepository>()
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        every { repo.findByIds(listOf(id1, id2)) } returns mapOf(id1 to mockk(relaxed = true), id2 to mockk(relaxed = true))

        runCatching { BlogPostNodeResolver(repo).batchResolve(listOf(postNodeCtx(id1), postNodeCtx(id2))) }

        verify(exactly = 1) { repo.findByIds(listOf(id1, id2)) }
    }

    @Test
    fun `BlogPostNodeResolver returns NotFoundException FieldValue for missing id`() = runBlocking {
        val repo = mockk<PostRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns emptyMap()

        val results = BlogPostNodeResolver(repo).batchResolve(listOf(postNodeCtx(id)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        val err = runCatching { results[0].get() }.exceptionOrNull()
        assertTrue(err is NotFoundException, "Expected NotFoundException, got $err")
    }

    // ── CommentNodeResolver ────────────────────────────────────────────────────

    @Test
    fun `CommentNodeResolver calls findByIds once for the whole batch`() = runBlocking {
        val repo = mockk<CommentRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns mapOf(id to mockk<Comment>(relaxed = true))

        runCatching { CommentNodeResolver(repo).batchResolve(listOf(commentNodeCtx(id))) }

        verify(exactly = 1) { repo.findByIds(listOf(id)) }
    }

    @Test
    fun `CommentNodeResolver returns NotFoundException FieldValue for missing id`() = runBlocking {
        val repo = mockk<CommentRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns emptyMap()

        val results = CommentNodeResolver(repo).batchResolve(listOf(commentNodeCtx(id)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        val err = runCatching { results[0].get() }.exceptionOrNull()
        assertTrue(err is NotFoundException, "Expected NotFoundException, got $err")
    }

    // ── LikeNodeResolver ───────────────────────────────────────────────────────

    @Test
    fun `LikeNodeResolver calls findByIds once for the whole batch`() = runBlocking {
        val repo = mockk<LikeRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns mapOf(id to mockk<Like>(relaxed = true))

        runCatching { LikeNodeResolver(repo).batchResolve(listOf(likeNodeCtx(id))) }

        verify(exactly = 1) { repo.findByIds(listOf(id)) }
    }

    @Test
    fun `LikeNodeResolver returns NotFoundException FieldValue for missing id`() = runBlocking {
        val repo = mockk<LikeRepository>()
        val id = UUID.randomUUID()
        every { repo.findByIds(listOf(id)) } returns emptyMap()

        val results = LikeNodeResolver(repo).batchResolve(listOf(likeNodeCtx(id)))

        assertEquals(1, results.size)
        assertTrue(results[0].isError)
        val err = runCatching { results[0].get() }.exceptionOrNull()
        assertTrue(err is NotFoundException, "Expected NotFoundException, got $err")
    }

    // ── Context builders ───────────────────────────────────────────────────────

    private fun userNodeCtx(id: UUID): NodeResolvers.User.Context {
        val ctx = mockk<NodeResolvers.User.Context>(relaxed = true)
        coEvery { ctx.id } returns globalIDFor(ViaductUser.Reflection, id.toString())
        return ctx
    }

    private fun postNodeCtx(id: UUID): NodeResolvers.BlogPost.Context {
        val ctx = mockk<NodeResolvers.BlogPost.Context>(relaxed = true)
        coEvery { ctx.id } returns globalIDFor(ViaductBlogPost.Reflection, id.toString())
        return ctx
    }

    private fun commentNodeCtx(id: UUID): NodeResolvers.Comment.Context {
        val ctx = mockk<NodeResolvers.Comment.Context>(relaxed = true)
        coEvery { ctx.id } returns globalIDFor(ViaductComment.Reflection, id.toString())
        return ctx
    }

    private fun likeNodeCtx(id: UUID): NodeResolvers.Like.Context {
        val ctx = mockk<NodeResolvers.Like.Context>(relaxed = true)
        coEvery { ctx.id } returns globalIDFor(ViaductLike.Reflection, id.toString())
        return ctx
    }
}
