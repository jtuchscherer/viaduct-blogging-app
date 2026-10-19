package org.tuchscherer.config

import viaduct.service.SchemaScopeInfo
import viaduct.service.api.SchemaId

/**
 * Schema views loaded from the manifest generated from `src/main/viaduct/scopes.yaml`.
 * Viaduct validates both the scope vocabulary and each view during schema assembly.
 * Runtime registration and HTTP routing use these same generated declarations.
 */
object ViaductSchemas {

    const val PUBLIC = "public"
    const val ADMIN = "admin"

    /** Every scoped schema to register with Viaduct. */
    val all: List<SchemaScopeInfo> = SchemaScopeInfo.fromResources()

    private val byId = all.associateBy { it.schemaId.id }

    /** The [SchemaId] to execute against for a registered schema id. */
    fun schemaIdFor(schemaId: String): SchemaId = byId.getValue(schemaId).schemaId
}
