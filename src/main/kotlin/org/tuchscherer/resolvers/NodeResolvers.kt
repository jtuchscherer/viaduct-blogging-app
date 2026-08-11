package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
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
    override suspend fun batchResolve(contexts: List<Context>): Map<Context, FieldValue<ViaductBlogPost>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = postRepository::findByIds,
            transform = { post, ctx -> post.toViaductBlogPost(ctx) },
            notFound = { id -> NotFoundException("Post not found: $id") },
        )
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
