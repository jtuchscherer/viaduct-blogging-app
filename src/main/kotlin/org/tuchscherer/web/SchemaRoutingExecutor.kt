package org.tuchscherer.web

import org.tuchscherer.config.SchemaScopes
import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.SchemaId
import viaduct.service.api.Viaduct

/**
 * Picks the schema for a request's audience and executes against it.
 *
 * This is deliberately the **only** place that constructs a [SchemaId] or calls
 * [Viaduct.execute]. Both are marked internal to Viaduct — the compiler warns
 * "This API is internal, is not meant to be used outside Viaduct" — yet there is no public
 * alternative for choosing a schema per request: [ExecutionInput] carries no schema id, and
 * every `execute`/`executeAsync` overload except the single-argument one takes a [SchemaId].
 *
 * Since internal APIs sit outside Viaduct's public API stability contract, they can change in a
 * patch release. Concentrating them here keeps that a one-file fix instead of a change scattered
 * across the HTTP layer.
 *
 * The schema-to-scope mapping is derived from [SchemaScopes] rather than restated, so it cannot
 * drift from the scopes the Viaduct instance was actually built with.
 */
class SchemaRoutingExecutor(private val viaduct: Viaduct) {

    /** Which schema a caller should be served. */
    enum class Audience(val schemaId: String) {
        PUBLIC(SchemaScopes.PUBLIC),
        ADMIN(SchemaScopes.ADMIN),
    }

    private val schemaIds: Map<String, SchemaId> =
        SchemaScopes.servedSchemas.mapValues { (schemaId, scopes) -> SchemaId.Scoped(schemaId, scopes) }

    suspend fun execute(executionInput: ExecutionInput, audience: Audience): ExecutionResult =
        viaduct.execute(executionInput, schemaIds.getValue(audience.schemaId))
}
