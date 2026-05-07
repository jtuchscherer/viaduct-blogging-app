package org.tuchscherer.analytics

import io.mockk.every
import io.mockk.mockk
import org.tuchscherer.analytics.port.PostTypeLookupPort.PostKind
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.PostRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Unit tests for [ViaductPostTypeLookupPort].
 *
 * This bridge converts raw post-type discriminator strings (stored in the Posts
 * table) into the analytics module's [PostKind] enum.  If the mapping is wrong,
 * the [TrendingQueryResolver] generates dead node-refs that surfaced as
 * NotFoundException in production — see the TrendingQueryResolverTest regression.
 */
class ViaductPostTypeLookupPortTest {

    private val postRepository = mockk<PostRepository>()
    private val port = ViaductPostTypeLookupPort(postRepository)

    private fun mockPost(id: UUID, postType: String): Post {
        val post = mockk<Post>()
        every { post.postType } returns postType
        return post
    }

    @Test
    fun `getPostTypes returns BLOG_POST for a post with BLOG_POST type`() {
        val id = UUID.randomUUID()
        every { postRepository.findByIds(listOf(id)) } returns mapOf(id to mockPost(id, PostType.BLOG_POST))

        val result = port.getPostTypes(listOf(id))

        assertEquals(PostKind.BLOG_POST, result[id])
    }

    @Test
    fun `getPostTypes returns CHECKLIST_POST for a post with CHECKED_LIST type`() {
        val id = UUID.randomUUID()
        every { postRepository.findByIds(listOf(id)) } returns mapOf(id to mockPost(id, PostType.CHECKED_LIST))

        val result = port.getPostTypes(listOf(id))

        assertEquals(PostKind.CHECKLIST_POST, result[id])
    }

    @Test
    fun `getPostTypes handles a mix of post types in one call`() {
        val blogId = UUID.randomUUID()
        val checklistId = UUID.randomUUID()
        every { postRepository.findByIds(listOf(blogId, checklistId)) } returns mapOf(
            blogId to mockPost(blogId, PostType.BLOG_POST),
            checklistId to mockPost(checklistId, PostType.CHECKED_LIST),
        )

        val result = port.getPostTypes(listOf(blogId, checklistId))

        assertEquals(PostKind.BLOG_POST, result[blogId])
        assertEquals(PostKind.CHECKLIST_POST, result[checklistId])
    }

    @Test
    fun `getPostTypes returns empty map for an empty input list without calling the repository`() {
        // No stub needed — the implementation short-circuits on empty input
        val result = port.getPostTypes(emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPostTypes omits IDs not found in the repository`() {
        val missingId = UUID.randomUUID()
        // Repository returns empty map — post was deleted
        every { postRepository.findByIds(listOf(missingId)) } returns emptyMap()

        val result = port.getPostTypes(listOf(missingId))

        assertFalse(result.containsKey(missingId))
    }

    @Test
    fun `getPostTypes returns only the subset of IDs that exist`() {
        val existingId = UUID.randomUUID()
        val missingId = UUID.randomUUID()
        every { postRepository.findByIds(listOf(existingId, missingId)) } returns
            mapOf(existingId to mockPost(existingId, PostType.BLOG_POST))

        val result = port.getPostTypes(listOf(existingId, missingId))

        assertEquals(1, result.size)
        assertEquals(PostKind.BLOG_POST, result[existingId])
        assertFalse(result.containsKey(missingId))
    }
}
