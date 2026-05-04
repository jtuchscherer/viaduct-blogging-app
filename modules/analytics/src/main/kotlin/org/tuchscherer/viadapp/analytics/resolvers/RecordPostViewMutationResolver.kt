package org.tuchscherer.viadapp.analytics.resolvers

import org.tuchscherer.analytics.repositories.PostViewRepository
import org.tuchscherer.viadapp.analytics.resolverbases.MutationResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import java.util.Base64
import java.util.UUID

/**
 * Resolver for Mutation.recordPostView.
 *
 * Increments the view count for the given post (any type — BlogPost or CheckedListPost).
 * No authentication required — view tracking is a public, unauthenticated operation.
 *
 * The [postId] argument is a raw GraphQL `ID!` containing the Viaduct-encoded global ID.
 * Global IDs are base64-encoded strings with the format `TypeName:internalUUID`. We decode
 * the UUID from it and record the view; the type information is not needed here because the
 * [PostViews] table is type-agnostic (it stores UUIDs only).
 */
@Resolver
class RecordPostViewMutationResolver : MutationResolvers.RecordPostView() {
    private val postViewRepository: PostViewRepository by inject(PostViewRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = decodeInternalId(ctx.arguments.postId)
        postViewRepository.incrementViewCount(postId)
        return true
    }

    companion object {
        /**
         * Decodes a Viaduct global ID string (base64 `TypeName:uuid`) into its UUID component.
         * Throws [IllegalArgumentException] if the string is not a valid encoded global ID.
         */
        internal fun decodeInternalId(encodedId: String): UUID {
            val decoded = runCatching { String(Base64.getDecoder().decode(encodedId)) }
                .getOrElse { throw IllegalArgumentException("Invalid post ID: $encodedId") }
            val colonIdx = decoded.indexOf(':')
            require(colonIdx > 0) { "Invalid post ID format: $encodedId" }
            val internalId = decoded.substring(colonIdx + 1)
            return runCatching { UUID.fromString(internalId) }
                .getOrElse { throw IllegalArgumentException("Invalid UUID in post ID: $internalId") }
        }
    }
}
