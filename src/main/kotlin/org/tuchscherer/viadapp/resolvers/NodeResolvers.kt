package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.optionalAuth
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.resolverkit.batchNodeResolve
import org.tuchscherer.viadapp.resolvers.resolverbases.NodeResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

@Resolver
class UserNodeResolver(
    private val userRepository: UserRepository
) : NodeResolvers.User() {
    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductUser>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = userRepository::findByIds,
            transform = { user, ctx -> user.toViaductUser(ctx) },
            notFound = { id -> NotFoundException("User not found: $id") },
        )
}

@Resolver
class BlogPostNodeResolver(
    private val postRepository: PostRepository
) : NodeResolvers.BlogPost() {
    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductBlogPost>> {
        // Every context in a batch belongs to the same request, so one viewer covers them all.
        val viewer = optionalAuth(contexts.firstOrNull()?.requestContext)

        return batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            // node(id) is how the post detail and edit pages read a single post, so this is the
            // draft leak that matters most: it bypasses the filtered list queries entirely.
            findByIds = { ids -> postRepository.findByIds(ids).filterValues { it.isVisibleTo(viewer) } },
            transform = { post, ctx -> post.toViaductBlogPost(ctx) },
            // A draft the viewer may not see is reported exactly as a missing post, so the
            // response cannot be used to work out whether it exists.
            notFound = { id -> NotFoundException("Post not found: $id") },
        )
    }
}

@Resolver
class CommentNodeResolver(
    private val commentRepository: CommentRepository
) : NodeResolvers.Comment() {
    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductComment>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = commentRepository::findByIds,
            transform = { comment, ctx -> comment.toViaductComment(ctx) },
            notFound = { id -> NotFoundException("Comment not found: $id") },
        )
}

@Resolver
class LikeNodeResolver(
    private val likeRepository: LikeRepository
) : NodeResolvers.Like() {
    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductLike>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = likeRepository::findByIds,
            transform = { like, ctx -> like.toViaductLike(ctx) },
            notFound = { id -> NotFoundException("Like not found: $id") },
        )
}
