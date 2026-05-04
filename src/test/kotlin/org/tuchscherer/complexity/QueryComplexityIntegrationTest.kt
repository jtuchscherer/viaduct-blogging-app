package org.tuchscherer.complexity

import graphql.analysis.QueryComplexityCalculator
import graphql.execution.CoercedVariables
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * End-to-end check that real queries from query-tests.sh score under [QueryComplexityGuard.MAX_COMPLEXITY]
 * and that an obviously expensive nested/paginated query exceeds it.
 *
 * Builds the schema from SDL and runs graphql-java's [QueryComplexityCalculator] — no Viaduct
 * runtime needed, since instrumentation operates on the parsed AST + schema.
 */
class QueryComplexityIntegrationTest {

    private val schema: GraphQLSchema by lazy {
        val builtin = File("build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls").readText()
        // Use the same module-aware schema loader as QueryComplexityGuard so tests
        // reflect the actual schema the guard enforces at runtime.
        val modules = QueryComplexityGuard.MODULE_SCHEMA_PATHS
            .map(::File)
            .filter { it.exists() }
            .joinToString("\n") { it.readText() }
        val typeRegistry = SchemaParser().parse("$builtin\n$modules")
        SchemaGenerator().makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING)
    }

    private fun score(query: String): Int =
        QueryComplexityCalculator.newCalculator()
            .schema(schema)
            .document(Parser.parse(query))
            .fieldComplexityCalculator(QueryFieldComplexityCalculator())
            .variables(CoercedVariables.emptyVariables())
            .build()
            .calculate()

    @Test
    fun `simple post lookup with author scores well under threshold`() {
        val query = "{ post(id: \"abc\") { id title author { username } } }"
        assertTrue(score(query) < QueryComplexityGuard.MAX_COMPLEXITY)
    }

    @Test
    fun `most expensive existing query - nested post with comments and likes - is under threshold`() {
        val query = """
            { post(id: "abc") {
                id title
                author { username email }
                comments { id content author { username } }
                likes { id user { username } }
                likeCount isLikedByMe
            } }
        """.trimIndent()
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `postsConnection without first arg is under threshold using default page size`() {
        val query = """
            { postsConnection {
                totalCount
                pageInfo { hasNextPage hasPreviousPage endCursor }
                edges { cursor node { id title } }
            } }
        """.trimIndent()
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `admin users page query is under threshold`() {
        val query = "{ admin { users(limit: 10) { totalCount users { id username } } } }"
        assertTrue(score(query) < QueryComplexityGuard.MAX_COMPLEXITY)
    }

    @Test
    fun `large postsConnection first arg blows past threshold`() {
        val query = "{ postsConnection(first: 50) { edges { node { id author { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityGuard.MAX_COMPLEXITY, "expected >${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `deeply nested list traversal blows past threshold`() {
        val query = "{ posts { comments { author { posts { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityGuard.MAX_COMPLEXITY, "expected >${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `realistic frontend pagination query scores under threshold`() {
        // The HomePage GET_POSTS_CONNECTION query: interface fields + BlogPost inline fragment
        // for content + author with 3 fields, first=10. Pinned here so tightening the threshold
        // below this re-breaks the home page (which the e2e suite also catches, but the loop is
        // faster here). content accessed via ... on BlogPost { content } after Phase 0.
        val query = """
            { postsConnection(first: 10) {
                totalCount
                pageInfo { hasNextPage endCursor }
                edges {
                  node {
                    id title
                    ... on BlogPost { content }
                    author { id name username }
                    createdAt likeCount commentCount
                  }
                }
            } }
        """.trimIndent()
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `threshold and depth limit are the documented values`() {
        // Pin the configured limits so accidental edits to QueryComplexityGuard
        // are caught here rather than only failing the existing query-tests.sh suite.
        assertEquals(250, QueryComplexityGuard.MAX_COMPLEXITY)
        assertEquals(8, QueryComplexityGuard.MAX_DEPTH)
    }

    // ── Analytics module ────────────────────────────────────────────────────

    @Test
    fun `trending with default limit is under threshold`() {
        val query = "{ trending { id title } }"
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `trending with nested author and comments blows past threshold`() {
        val query = "{ trending { comments { author { posts { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityGuard.MAX_COMPLEXITY, "expected >${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `trending applies DEFAULT_LIST_MULTIPLIER to child cost`() {
        // id is a scalar leaf → childComplexity = 0, so id costs 1 + 0 = 1
        // { trending { id } } → 1 + 10 * 1 = 11
        assertEquals(1 + 10 * 1, score("{ trending { id } }"))
    }

    // ── CheckedList module ──────────────────────────────────────────────────

    @Test
    fun `checkedListPosts with scalar fields only is under threshold`() {
        val query = "{ checkedListPosts { id title createdAt } }"
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `checkedListPosts with items is under threshold`() {
        // Realistic frontend query: 10 posts × (id+title + 5 items × 4 fields).
        // items uses CHECKLIST_ITEM_MULTIPLIER=5, not DEFAULT_LIST_MULTIPLIER=10.
        // items { id text checked position } cost = 1 + 5*(1+1+1+1) = 21
        // per-post child = id(1) + title(1) + items(21) = 23
        // checkedListPosts = 1 + 10*23 = 231 < 250 ✓
        val query = "{ checkedListPosts { id title items { id text checked position } } }"
        val s = score(query)
        assertTrue(s < QueryComplexityGuard.MAX_COMPLEXITY, "expected <${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `deeply nested checkedListPost traversal blows past threshold`() {
        val query = "{ checkedListPosts { comments { author { posts { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityGuard.MAX_COMPLEXITY, "expected >${QueryComplexityGuard.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `checkedListPosts applies DEFAULT_LIST_MULTIPLIER to child cost`() {
        // id is scalar leaf → childComplexity = 0, so id costs 1
        // { checkedListPosts { id } } → 1 + 10 * 1 = 11
        assertEquals(1 + 10 * 1, score("{ checkedListPosts { id } }"))
    }

    @Test
    fun `items field on CheckedListPost uses CHECKLIST_ITEM_MULTIPLIER`() {
        // items is a checklist-item list: uses CHECKLIST_ITEM_MULTIPLIER = 5, not 10
        // id is scalar leaf → cost 1
        // items { id } = 1 + 5 * 1 = 6
        // checkedListPosts { items { id } } = 1 + 10 * 6 = 61
        val query = "{ checkedListPosts { items { id } } }"
        val s = score(query)
        assertEquals(1 + 10 * (1 + 5 * 1), s)
    }
}
