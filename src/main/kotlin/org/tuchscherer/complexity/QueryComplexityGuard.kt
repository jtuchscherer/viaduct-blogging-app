package org.tuchscherer.complexity

import graphql.analysis.QueryComplexityCalculator
import graphql.GraphQLException
import graphql.GraphQLError as JavaGraphQLError
import graphql.execution.CoercedVariables
import graphql.language.Document
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.OperationDefinition
import graphql.language.SelectionSet
import graphql.parser.Parser
import graphql.parser.InvalidSyntaxException
import graphql.schema.GraphQLSchema
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.SchemaGenerator
import graphql.schema.idl.SchemaParser
import graphql.validation.Validator
import org.slf4j.LoggerFactory
import viaduct.service.api.GraphQLError
import java.io.File
import java.util.Locale

/**
 * Pre-execution check that scores incoming GraphQL queries with [QueryFieldComplexityCalculator]
 * and rejects those exceeding [maxComplexity] or [maxDepth]. Sits *above* Viaduct so we never
 * touch graphql-java types in Viaduct's stable API surface — the guard is our own concern,
 * implemented with graphql-java directly.
 */
class QueryComplexityGuard(
    private val calculator: QueryFieldComplexityCalculator,
    private val maxComplexity: Int = MAX_COMPLEXITY,
    private val maxDepth: Int = MAX_DEPTH,
) {
    private val logger = LoggerFactory.getLogger(QueryComplexityGuard::class.java)

    private val schema: GraphQLSchema by lazy {
        val builtin = File("build/viaduct/centralSchema/BUILTIN_SCHEMA.graphqls").readText()
        val modules = loadModuleSchemas()
        val typeRegistry = SchemaParser().parse("$builtin\n$modules")
        SchemaGenerator().makeExecutableSchema(typeRegistry, RuntimeWiring.MOCKED_WIRING)
    }

    private fun loadModuleSchemas(): String =
        MODULE_SCHEMA_PATHS
            .map(::File)
            .filter { it.exists() }
            .joinToString("\n") { it.readText() }

    /**
     * Returns a [GraphQLError] explaining the rejection, or null if the query is within limits.
     * Hand the returned error to [GuardedViaduct] which packages it into an ExecutionResult.
     *
     * @param variables the operation's variables, needed so [QueryComplexityCalculator] can
     *        resolve NonNull arguments without throwing (e.g. `mutation($input: CreatePostInput!)`).
     */
    // Parsing, introspection, depth and score checks read naturally as independent early exits.
    @Suppress("ReturnCount")
    fun check(query: String, variables: Map<String, Any?> = emptyMap()): GraphQLError? {
        val doc = try {
            Parser.parse(query)
        } catch (_: InvalidSyntaxException) {
            // Invalid syntax — let Viaduct produce its own (better-formatted) parse error.
            return null
        }

        if (isIntrospectionQuery(doc)) return null

        val depth = depthOf(doc)
        if (depth > maxDepth) {
            logger.warn("query rejected: depth $depth > $maxDepth")
            return abortError("maximum query depth exceeded $depth > $maxDepth")
        }

        val score = calculateScore(doc, variables) ?: return null
        if (score > maxComplexity) {
            logger.warn("query rejected: complexity $score > $maxComplexity")
            return abortError("maximum query complexity exceeded $score > $maxComplexity")
        }

        return null
    }

    // GraphQL's client-error classification is an interface, which Kotlin cannot catch directly.
    @Suppress("InstanceOfCheckForException")
    private fun calculateScore(doc: Document, variables: Map<String, Any?>): Int? {
        // Invalid documents belong to Viaduct's validator. Schema loading and unexpected
        // calculator failures must propagate rather than silently bypassing the guard.
        if (Validator().validateDocument(schema, doc, Locale.ROOT).isNotEmpty()) return null

        return try {
            QueryComplexityCalculator.newCalculator()
                .schema(schema)
                .document(doc)
                .fieldComplexityCalculator(calculator)
                .variables(CoercedVariables.of(variables as Map<String, Any>))
                .build()
                .calculate()
        } catch (e: GraphQLException) {
            // Only client-facing GraphQL errors (e.g. invalid arguments or operation selection)
            // delegate to Viaduct. Internal assertions and programming errors remain failures.
            if (e !is JavaGraphQLError) throw e
            logger.debug("Complexity calculation deferred to GraphQL validation", e)
            null
        }
    }

    private fun abortError(message: String): GraphQLError =
        GraphQLError(
            message = message,
            extensions = mapOf("classification" to "ExecutionAborted"),
        )

    /**
     * Returns true when every root field in every operation is a GraphQL meta-field
     * (name starts with `__`, e.g. `__schema`, `__type`, `__typename`). These are
     * introspection queries issued by tooling such as GraphiQL and must not be blocked
     * by depth or complexity limits.
     */
    private fun isIntrospectionQuery(doc: Document): Boolean {
        val operations = doc.definitions.filterIsInstance<OperationDefinition>()
        return operations.isNotEmpty() && operations.all { op ->
            val rootFields = op.selectionSet?.selections.orEmpty().filterIsInstance<Field>()
            rootFields.isNotEmpty() && rootFields.all { it.name.startsWith("__") }
        }
    }

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

        /**
         * Schema files to merge into the complexity-check schema.
         * Paths that don't exist yet are silently skipped so the guard works correctly
         * before all modules are present (e.g. checkedlist not yet added).
         */
        internal val MODULE_SCHEMA_PATHS = listOf(
            "src/main/viaduct/schema/schema.graphqls",
            "modules/analytics/src/main/viaduct/schema/PostAnalytics.graphqls",
            "modules/checkedlist/src/main/viaduct/schema/CheckedList.graphqls",
        )
    }
}
