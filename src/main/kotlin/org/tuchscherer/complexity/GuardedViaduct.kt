package org.tuchscherer.complexity

import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.GraphQLError
import viaduct.service.api.SchemaId
import viaduct.service.api.Viaduct
import java.util.concurrent.CompletableFuture

/**
 * Wraps a [Viaduct] instance with a [QueryComplexityGuard] that runs before each execution.
 * Queries exceeding the configured complexity or depth limits are rejected without ever
 * reaching Viaduct's resolvers. Sits entirely on Viaduct's @StableApi surface — the guard
 * is the only place graphql-java appears.
 */
class GuardedViaduct(
    private val delegate: Viaduct,
    private val guard: QueryComplexityGuard,
) : Viaduct {

    override fun execute(executionInput: ExecutionInput, schemaId: SchemaId): ExecutionResult =
        guard.check(executionInput.operationText, executionInput.variables)
            ?.let(::abortResult)
            ?: delegate.execute(executionInput, schemaId)

    override suspend fun executeAsync(
        executionInput: ExecutionInput,
        schemaId: SchemaId,
    ): CompletableFuture<ExecutionResult> =
        guard.check(executionInput.operationText, executionInput.variables)
            ?.let { CompletableFuture.completedFuture(abortResult(it)) }
            ?: delegate.executeAsync(executionInput, schemaId)

    override fun getAppliedScopes(schemaId: SchemaId): Set<String>? =
        delegate.getAppliedScopes(schemaId)

    private fun abortResult(error: GraphQLError): ExecutionResult = AbortedResult(error)

    private class AbortedResult(error: GraphQLError) : ExecutionResult {
        override val errors: List<GraphQLError> = listOf(error)
        override val extensions: Map<Any, Any?>? = null
        override fun getData(): Map<String, Any?>? = null
        override fun toSpecification(): Map<String, Any?> = mapOf(
            "errors" to errors.map { e ->
                buildMap<String, Any?> {
                    put("message", e.message)
                    if (e.extensions.isNotEmpty()) put("extensions", e.extensions)
                }
            },
            "data" to null,
        )
    }
}
