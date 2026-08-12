package org.tuchscherer.config

/**
 * The schema scope vocabulary, and which scopes each served schema exposes.
 *
 * This is the single source of truth. [KoinModules] builds the Viaduct scoped schemas from it,
 * and `SchemaScopeVocabularyTest` checks that every `@scope(to: [...])` name appearing in the
 * `.graphqls` files is declared here.
 *
 * That check matters because an undeclared scope name fails silently. A type annotated
 * `@scope(to: ["publicc"])` still compiles and still assembles — it is simply absent from every
 * served schema, so the field quietly disappears from the API rather than producing an error.
 */
object SchemaScopes {

    /** Visible to all clients. */
    const val PUBLIC = "public"

    /** Admin-only surface, layered on top of [PUBLIC]. */
    const val ADMIN = "admin"

    /**
     * Schemas Viaduct serves, as `schema id` to the scopes it includes.
     *
     * The admin schema deliberately includes [PUBLIC] as well, so admin clients see the whole
     * graph rather than only admin-specific fields.
     */
    val servedSchemas: Map<String, Set<String>> = mapOf(
        PUBLIC to setOf(PUBLIC),
        ADMIN to setOf(PUBLIC, ADMIN),
    )

    /** Every scope name legal in a `@scope(to: [...])` directive. */
    val declared: Set<String> = servedSchemas.values.flatten().toSet()
}
