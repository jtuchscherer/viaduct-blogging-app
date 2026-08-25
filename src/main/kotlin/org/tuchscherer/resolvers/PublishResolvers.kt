package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.PostVisibility
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.time.LocalDateTime
import java.util.UUID

/**
 * Publishes a draft.
 *
 * `postId` is a bare `ID!` rather than `@idOf(type: "BlogPost")` so one mutation serves both post
 * types — the same approach `recordPostView` takes. Both types live in the `posts` table, so the
 * status transition is type-agnostic; the return type is the `Post` interface for the same reason.
 */
@Resolver
class PublishPostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.PublishPost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val user = requireAuth(ctx.requestContext)
        val postId = decodePostGlobalId(ctx.arguments.postId)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        if (!PostVisibility.canChangeStatus(post.authorId.value, user.id.value)) {
            throw AuthorizationException("You are not authorized to publish this post")
        }

        // Stamped now, so a draft held for weeks enters the feed as new rather than backdated.
        val published = postRepository.updateStatus(postId, PostStatus.PUBLISHED, LocalDateTime.now())
            ?: throw NotFoundException("Post not found")

        return published.toViaductPost(ctx)
    }
}

/**
 * Returns a published post to draft.
 *
 * Clears `publishedAt`, so the column always means "published at, if currently published".
 * Comments, likes and view counts are left alone: unpublishing hides a post, it does not
 * destroy the engagement it accumulated, and republishing brings that back.
 */
@Resolver
class UnpublishPostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.UnpublishPost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val user = requireAuth(ctx.requestContext)
        val postId = decodePostGlobalId(ctx.arguments.postId)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        if (!PostVisibility.canChangeStatus(post.authorId.value, user.id.value)) {
            throw AuthorizationException("You are not authorized to unpublish this post")
        }

        val draft = postRepository.updateStatus(postId, PostStatus.DRAFT, null)
            ?: throw NotFoundException("Post not found")

        return draft.toViaductPost(ctx)
    }
}
