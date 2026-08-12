package org.tuchscherer.config

import viaduct.service.SchemaScopeInfo
import viaduct.service.api.SchemaId

/**
 * The scoped schemas registered with Viaduct, declared exactly once.
 *
 * [KoinModules] passes [all] to `ViaductBuilder.withScopedSchemas`, and the HTTP layer routes a
 * request by looking up the matching [SchemaId] with [schemaIdFor]. Both read the same
 * declarations, so the registered schemas and the routable schemas cannot disagree.
 *
 * The [SchemaId] is taken from `SchemaScopeInfo.schemaId` rather than built with
 * `SchemaId.Scoped(...)`. Constructing a `SchemaId` directly warns that the API is internal to
 * Viaduct, and it would mean declaring the same id and scope set twice. This mirrors the Viaduct
 * demo app, which declares `SchemaScopeInfo.Scoped(...)` values and later routes on their
 * `.schemaId`.
 *
 * Scope names come from [SchemaScopes], which is where the vocabulary is validated.
 */
object ViaductSchemas {

    private val byId: Map<String, SchemaScopeInfo.Scoped> =
        SchemaScopes.servedSchemas.mapValues { (schemaId, scopes) ->
            SchemaScopeInfo.Scoped(schemaId, scopes)
        }

    /** Every scoped schema to register with Viaduct. */
    val all: List<SchemaScopeInfo> = byId.values.toList()

    /** The [SchemaId] to execute against for a registered schema id. */
    fun schemaIdFor(schemaId: String): SchemaId = byId.getValue(schemaId).schemaId
}
