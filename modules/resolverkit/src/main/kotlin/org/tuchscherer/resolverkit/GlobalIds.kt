package org.tuchscherer.resolverkit

import java.util.Base64
import java.util.UUID

/**
 * Decodes a Viaduct global ID (base64 `TypeName:uuid`) into its UUID component.
 *
 * The type prefix is discarded — the caller knows the type. This is what a mutation needs when its
 * argument is a bare `ID!` rather than `@idOf(type: ...)`, which is how one mutation comes to serve
 * both post types: `recordPostView`, `publishPost` and `unpublishPost` all take that shape.
 *
 * It lives here, next to [batchNodeResolve], because the root project and the tenant modules both
 * need it and neither can see a helper private to the other: the app depends on the modules, never
 * the other way round. Before this it existed twice, once on each side of that boundary.
 *
 * @throws IllegalArgumentException if [encodedId] is not a valid Viaduct global ID
 */
fun decodeGlobalId(encodedId: String): UUID {
    val decoded = runCatching { String(Base64.getDecoder().decode(encodedId)) }
        .getOrElse { throw IllegalArgumentException("Invalid post ID: $encodedId") }
    val colonIdx = decoded.indexOf(':')
    require(colonIdx > 0) { "Invalid post ID format: $encodedId" }
    val internalId = decoded.substring(colonIdx + 1)
    return runCatching { UUID.fromString(internalId) }
        .getOrElse { throw IllegalArgumentException("Invalid UUID in post ID: $internalId") }
}
