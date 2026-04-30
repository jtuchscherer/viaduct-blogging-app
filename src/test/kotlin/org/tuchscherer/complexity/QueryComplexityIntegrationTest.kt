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
 * End-to-end check that real queries from query-tests.sh score under [QueryComplexityInstrumentation.MAX_COMPLEXITY]
 * and that an obviously expensive nested/paginated query exceeds it.
 *
 * Builds the schema from SDL and runs graphql-java's [QueryComplexityCalculator] — no Viaduct
 * runtime needed, since instrumentation operates on the parsed AST + schema.
 */
class QueryComplexityIntegrationTest {

    private val schema: GraphQLSchema by lazy {
        val builtin = File("build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls").readText()
        val app = File("src/main/viaduct/schema/schema.graphqls").readText()
        val typeRegistry = SchemaParser().parse("$builtin\n$app")
        SchemaGenerator().makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING)
    }

    private fun score(query: String): Int =
        QueryComplexityCalculator.newCalculator()
            .schema(schema)
            .document(Parser.parse(query))
            .fieldComplexityCalculator(BlogFieldComplexityCalculator())
            .variables(CoercedVariables.emptyVariables())
            .build()
            .calculate()

    @Test
    fun `simple post lookup with author scores well under threshold`() {
        val query = "{ post(id: \"abc\") { id title author { username } } }"
        assertTrue(score(query) < QueryComplexityInstrumentation.MAX_COMPLEXITY)
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
        assertTrue(s < QueryComplexityInstrumentation.MAX_COMPLEXITY, "expected <${QueryComplexityInstrumentation.MAX_COMPLEXITY}, got $s")
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
        assertTrue(s < QueryComplexityInstrumentation.MAX_COMPLEXITY, "expected <${QueryComplexityInstrumentation.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `admin users page query is under threshold`() {
        val query = "{ admin { users(limit: 10) { totalCount users { id username } } } }"
        assertTrue(score(query) < QueryComplexityInstrumentation.MAX_COMPLEXITY)
    }

    @Test
    fun `large postsConnection first arg blows past threshold`() {
        val query = "{ postsConnection(first: 50) { edges { node { id author { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityInstrumentation.MAX_COMPLEXITY, "expected >${QueryComplexityInstrumentation.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `deeply nested list traversal blows past threshold`() {
        val query = "{ posts { comments { author { posts { id } } } } }"
        val s = score(query)
        assertTrue(s > QueryComplexityInstrumentation.MAX_COMPLEXITY, "expected >${QueryComplexityInstrumentation.MAX_COMPLEXITY}, got $s")
    }

    @Test
    fun `threshold and depth limit are the documented values`() {
        // Pin the configured limits so accidental edits to QueryComplexityInstrumentation
        // are caught here rather than only failing the existing query-tests.sh suite.
        assertEquals(150, QueryComplexityInstrumentation.MAX_COMPLEXITY)
        assertEquals(8, QueryComplexityInstrumentation.MAX_DEPTH)
    }
}
