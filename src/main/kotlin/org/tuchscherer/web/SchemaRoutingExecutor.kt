package org.tuchscherer.web

import org.tuchscherer.config.SchemaScopes
import org.tuchscherer.config.ViaductSchemas
import viaduct.service.api.ExecutionInput
import viaduct.service.api.ExecutionResult
import viaduct.service.api.Viaduct

/**
 * Picks the schema for a request's audience and executes against it.
 *
 * This is deliberately the only place that calls [Viaduct.execute], which warns that it is
 * internal to Viaduct. There is no public alternative for choosing a schema per request:
 * [ExecutionInput] carries no schema id, and every `execute`/`executeAsync` overload except the
 * single-argument one takes a schema id. Keeping the call here means an upstream change is a
 * one-file fix rather than a change scattered across the HTTP layer.
 *
 * Schema ids come from [ViaductSchemas], which holds the same declarations registered with the
 * Viaduct instance, so the registered and routable schemas cannot drift.
 */
class SchemaRoutingExecutor(private val viaduct: Viaduct) {

    /** Which schema a caller should be served. */
    enum class Audience(val schemaId: String) {
        PUBLIC(SchemaScopes.PUBLIC),
        ADMIN(SchemaScopes.ADMIN),
    }

    suspend fun execute(executionInput: ExecutionInput, audience: Audience): ExecutionResult =
        viaduct.execute(executionInput, ViaductSchemas.schemaIdFor(audience.schemaId))
}
