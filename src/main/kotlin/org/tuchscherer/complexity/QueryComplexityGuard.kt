package org.tuchscherer.complexity

import graphql.analysis.QueryComplexityCalculator
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import org.slf4j.LoggerFactory
import viaduct.service.api.GraphQLError
import java.io.File

/**
 * Pre-execution check that scores incoming GraphQL queries with [BlogFieldComplexityCalculator]
 * and rejects those exceeding [maxComplexity] or [maxDepth]. Sits *above* Viaduct so we never
 * touch graphql-java types in Viaduct's stable API surface — the guard is our own concern,
 * implemented with graphql-java directly.
 */
class QueryComplexityGuard(
    private val calculator: BlogFieldComplexityCalculator,
    private val maxComplexity: Int = MAX_COMPLEXITY,
    private val maxDepth: Int = MAX_DEPTH,
) {
    private val logger = LoggerFactory.getLogger(QueryComplexityGuard::class.java)

    private val schema: GraphQLSchema by lazy {
        val builtin = File("build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls").readText()
        val app = File("src/main/viaduct/schema/schema.graphqls").readText()
        val typeRegistry = SchemaParser().parse("$builtin\n$app")
        SchemaGenerator().makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING)
    }

    /**
     * Returns a [GraphQLError] explaining the rejection, or null if the query is within limits.
     * Hand the returned error to [GuardedViaduct] which packages it into an ExecutionResult.
     *
     * @param variables the operation's variables, needed so [QueryComplexityCalculator] can
     *        resolve NonNull arguments without throwing (e.g. `mutation($input: CreatePostInput!)`).
     */
    fun check(query: String, variables: Map<String, Any?> = emptyMap()): GraphQLError? {
        val doc = try {
            Parser.parse(query)
        } catch (_: Exception) {
            // Invalid syntax — let Viaduct produce its own (better-formatted) parse error.
            return null
        }

        val depth = depthOf(doc)
        if (depth > maxDepth) {
            logger.warn("query rejected: depth $depth > $maxDepth")
            return abortError("maximum query depth exceeded $depth > $maxDepth")
        }

        val score = try {
            QueryComplexityCalculator.newCalculator()
                .schema(schema)
                .document(doc)
                .fieldComplexityCalculator(calculator)
                .variables(CoercedVariables.of(variables))
                .build()
                .calculate()
        } catch (e: Exception) {
            // The calculator can throw on schema mismatches the validator would catch (unknown
            // fields, wrong arg types, etc). Let Viaduct produce the canonical validation error
            // rather than masking it as a complexity failure.
            logger.debug("complexity calc skipped: ${e.message}")
            return null
        }
        if (score > maxComplexity) {
            logger.warn("query rejected: complexity $score > $maxComplexity")
            return abortError("maximum query complexity exceeded $score > $maxComplexity")
        }

        return null
    }

    private fun abortError(message: String): GraphQLError =
        GraphQLError(
            message = message,
            extensions = mapOf("classification" to "ExecutionAborted"),
        )

    private fun depthOf(doc: Document): Int {
        val fragments = doc.definitions
            .filterIsInstance<FragmentDefinition>()
            .associateBy { it.name }

        fun walk(selSet: SelectionSet?): Int {
            if (selSet == null) return 0
            return selSet.selections.maxOfOrNull { sel ->
                when (sel) {
                    is Field -> 1 + walk(sel.selectionSet)
                    is InlineFragment -> walk(sel.selectionSet)
                    is FragmentSpread -> walk(fragments[sel.name]?.selectionSet)
                    else -> 0
                }
            } ?: 0
        }

        return doc.definitions
            .filterIsInstance<OperationDefinition>()
            .maxOfOrNull { walk(it.selectionSet) } ?: 0
    }

    companion object {
        const val MAX_COMPLEXITY = 250
        const val MAX_DEPTH = 8
    }
}
