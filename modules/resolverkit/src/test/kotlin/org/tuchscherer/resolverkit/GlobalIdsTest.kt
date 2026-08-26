package org.tuchscherer.resolverkit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.UUID

/**
 * Unit tests for [decodeGlobalId].
 *
 * Moved here with the function itself, which used to exist once in the analytics module and once
 * in the root project. Every mutation taking a bare `ID!` decodes through this, so a change here
 * reaches `recordPostView`, `publishPost` and `unpublishPost` alike.
 */
class GlobalIdsTest {

    private fun encode(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())

    @Test
    fun `extracts the UUID from a valid BlogPost global ID`() {
        val uuid = UUID.randomUUID()

        assertEquals(uuid, decodeGlobalId(encode("BlogPost:$uuid")))
    }

    @Test
    fun `works for a CheckedListPost type prefix`() {
        val uuid = UUID.randomUUID()

        assertEquals(uuid, decodeGlobalId(encode("CheckedListPost:$uuid")))
    }

    @Test
    fun `works for arbitrary type prefixes`() {
        val uuid = UUID.randomUUID()

        assertEquals(uuid, decodeGlobalId(encode("User:$uuid")))
    }

    @Test
    fun `throws for non-base64 input`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId("not-valid-base64!!!") }
    }

    @Test
    fun `throws when there is no colon separator`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId(encode("nocolonhere")) }
    }

    @Test
    fun `throws when the UUID part is not a valid UUID`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId(encode("BlogPost:not-a-real-uuid")) }
    }

    @Test
    fun `throws for an empty string`() {
        assertThrows<IllegalArgumentException> { decodeGlobalId("") }
    }

    @Test
    fun `throws when the type prefix is missing but a colon is present`() {
        // A leading colon means there is no type name, which is not a well-formed global ID.
        assertThrows<IllegalArgumentException> { decodeGlobalId(encode(":${UUID.randomUUID()}")) }
    }
}
