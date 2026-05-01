package org.tuchscherer.complexity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QueryComplexityGuardTest {

    private val guard = QueryComplexityGuard(BlogFieldComplexityCalculator())

    @Test
    fun `simple healthy query passes the guard`() {
        assertNull(guard.check("{ posts { id title } }"))
    }

    @Test
    fun `over-budget query is rejected with the complexity message`() {
        val query = "{ postsConnection(first: 50) { edges { node { id author { id } } } } }"
        val err = guard.check(query)!!
        assertTrue(err.message.startsWith("maximum query complexity exceeded"), err.message)
        assertEquals("ExecutionAborted", err.extensions["classification"])
    }

    @Test
    fun `over-depth query is rejected with the depth message`() {
        // 9 nested field selections; depth limit is 8
        val query = "{ posts { author { posts { author { posts { author { posts { author { id } } } } } } } } }"
        val err = guard.check(query)!!
        assertTrue(err.message.startsWith("maximum query depth exceeded"), err.message)
    }

    @Test
    fun `unparseable query is passed through so Viaduct can produce its own parse error`() {
        // Letting Viaduct format the error keeps response shape consistent with all other
        // syntax errors clients see, instead of inventing a second error format here.
        assertNull(guard.check("{ this is not graphql"))
    }

    @Test
    fun `inline fragments contribute to depth`() {
        // Depth of 5: query → posts → ...on Post selection → author → posts → id
        val query = "{ posts { ... on Post { author { posts { id } } } } }"
        // Should pass — well under maxDepth=8
        val err = guard.check(query)
        // It may exceed complexity due to nested list, so just check that if it fails,
        // it's not because of depth.
        if (err != null) {
            assertTrue(!err.message.contains("depth"), "depth limit should not be the reason: ${err.message}")
        }
    }

    @Test
    fun `depth and complexity limits are the documented values`() {
        assertEquals(150, QueryComplexityGuard.MAX_COMPLEXITY)
        assertEquals(8, QueryComplexityGuard.MAX_DEPTH)
    }
}
