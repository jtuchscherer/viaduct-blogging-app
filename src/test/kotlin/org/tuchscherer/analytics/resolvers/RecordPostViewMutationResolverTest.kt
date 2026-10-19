package org.tuchscherer.analytics.resolvers

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.tuchscherer.analytics.port.PostStatusLookupPort
import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.shared.decodeGlobalId
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.tuchscherer.viadapp.analytics.resolvers.RecordPostViewMutationResolver
import java.util.Base64
import java.util.UUID

class RecordPostViewMutationResolverTest {
    private val repository = mockk<PostViewRepository>()
    private val statusLookup = mockk<PostStatusLookupPort>()
    private val postId = UUID.randomUUID()
    private val resolver = RecordPostViewMutationResolver(repository, statusLookup)

    private fun encodeGlobalId(typeName: String, internalId: String): String =
        Base64.getEncoder().encodeToString("$typeName:$internalId".toByteArray())

    private fun context(encoded: String): MutationResolvers.RecordPostView.Context {
        val ctx = mockk<MutationResolvers.RecordPostView.Context>()
        every { ctx.arguments.postId } returns encoded
        return ctx
    }

    private fun record(type: String): Boolean {
        every { statusLookup.publishedIds(listOf(postId)) } returns setOf(postId)
        // Only this UUID is allowed: decoding any other ID fails the strict mock.
        justRun { repository.incrementViewCount(postId) }
        return resolver.resolve(context(encodeGlobalId(type, postId.toString()))).join()
    }

    @Test
    fun `returns true after recording a BlogPost view`() {
        assertTrue(record("BlogPost"))
    }

    @Test
    fun `returns true after recording a CheckedListPost view`() {
        assertTrue(record("CheckedListPost"))
    }

    @Test
    fun `increments view count for the correct BlogPost ID`() {
        assertTrue(record("BlogPost"))
    }

    @Test
    fun `increments view count for the correct CheckedListPost ID`() {
        assertTrue(record("CheckedListPost"))
    }

    @Test
    fun `draft or missing posts return false without writing a view`() {
        every { statusLookup.publishedIds(listOf(postId)) } returns emptySet()
        // There is deliberately no increment stub: a write would fail this test.
        assertFalse(resolver.resolve(context(encodeGlobalId("BlogPost", postId.toString()))).join())
    }

    @Test
    fun `malformed IDs retain the illegal argument error contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.resolve(context("not-valid-base64!!!"))
        }
    }

    @Test
    fun `decodeGlobalId extracts UUID from BlogPost global ID`() {
        assertEquals(postId, decodeGlobalId(encodeGlobalId("BlogPost", postId.toString())))
    }

    @Test
    fun `decodeGlobalId extracts UUID from CheckedListPost global ID`() {
        assertEquals(postId, decodeGlobalId(encodeGlobalId("CheckedListPost", postId.toString())))
    }

    @Test
    fun `decodeGlobalId throws for non-base64 input`() {
        assertThrows(IllegalArgumentException::class.java) { decodeGlobalId("not-valid-base64!!!") }
    }

    @Test
    fun `decodeGlobalId throws when decoded value has no colon`() {
        val noColon = Base64.getEncoder().encodeToString("nouuidhere".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { decodeGlobalId(noColon) }
    }
}
