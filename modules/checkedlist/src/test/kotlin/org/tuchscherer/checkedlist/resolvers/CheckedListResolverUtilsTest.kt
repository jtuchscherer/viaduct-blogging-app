package org.tuchscherer.checkedlist.resolvers

import org.tuchscherer.viadapp.checkedlist.resolvers.toCountInt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Unit tests for the module-local [toCountInt] extension.
 *
 * This mirrors the root project's ResolverUtilsTest but covers the copy that
 * lives inside the checkedlist module.  Both copies must behave identically —
 * if one is changed, the corresponding test in the other module will catch drift.
 */
class CheckedListResolverUtilsTest {

    @Test
    fun `toCountInt converts a typical count`() {
        assertEquals(42, 42L.toCountInt())
    }

    @Test
    fun `toCountInt converts zero`() {
        assertEquals(0, 0L.toCountInt())
    }

    @Test
    fun `toCountInt accepts Int MAX_VALUE as the inclusive upper bound`() {
        assertEquals(Int.MAX_VALUE, Int.MAX_VALUE.toLong().toCountInt())
    }

    @Test
    fun `toCountInt throws IllegalArgumentException just above Int MAX_VALUE`() {
        val tooBig = Int.MAX_VALUE.toLong() + 1L
        val ex = assertThrows<IllegalArgumentException> { tooBig.toCountInt() }
        assert(ex.message!!.contains(tooBig.toString()))
        assert(ex.message!!.contains(Int.MAX_VALUE.toString()))
    }

    @Test
    fun `toCountInt throws for Long MAX_VALUE`() {
        assertThrows<IllegalArgumentException> { Long.MAX_VALUE.toCountInt() }
    }
}
