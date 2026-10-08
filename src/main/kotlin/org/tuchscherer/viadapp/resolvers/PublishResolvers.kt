package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.PostVisibility
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.Post
import org.tuchscherer.database.PostStatus
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.resolverkit.decodeGlobalId
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.time.LocalDateTime
import java.util.UUID

/**
 * Moves a post between draft and published, once the viewer is allowed to.
 *
 * Publishing and unpublishing differ only in the status they move to, the timestamp they leave
 * behind, and the verb in the refusal message — so the checks live here rather than being written
 * out twice. That matters beyond tidiness: the authorization check is the whole security of these
 * mutations, and two copies can drift apart.
 *
 * @param postId the post to move
 * @param viewerId the authenticated viewer
 * @param status the [PostStatus] to move to
 * @param publishedAt the publication timestamp to record, or null to clear it
 * @param action the verb used in the refusal message ("publish" / "unpublish")
 */
private fun PostRepository.transitionStatus(
    postId: UUID,
    viewerId: UUID,
    status: String,
    publishedAt: LocalDateTime?,
    action: String,
): Post {
    val post = findById(postId) ?: throw NotFoundException("Post not found")

    if (!PostVisibility.canChangeStatus(post.authorId.value, viewerId)) {
        throw AuthorizationException("You are not authorized to $action this post")
    }

    // Null only if the row disappeared between the two calls, which is a genuine not-found.
    return updateStatus(postId, status, publishedAt) ?: throw NotFoundException("Post not found")
}

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

        return postRepository.transitionStatus(
            postId = decodeGlobalId(ctx.arguments.postId),
            viewerId = user.id.value,
            status = PostStatus.PUBLISHED,
            // Stamped now, so a draft held for weeks enters the feed as new rather than backdated.
            publishedAt = LocalDateTime.now(),
            action = "publish",
        ).toViaductPost(ctx)
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

        return postRepository.transitionStatus(
            postId = decodeGlobalId(ctx.arguments.postId),
            viewerId = user.id.value,
            status = PostStatus.DRAFT,
            publishedAt = null,
            action = "unpublish",
        ).toViaductPost(ctx)
    }
}
