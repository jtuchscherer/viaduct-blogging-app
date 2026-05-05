package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.NodeResolvers
import viaduct.api.FieldValue
import viaduct.api.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.Like as ViaductLike
import viaduct.api.grts.User as ViaductUser

import java.util.UUID

private fun <C, E, G> batchNodeResolve(
    contexts: List<C>,
    extractId: (C) -> UUID,
    findByIds: (List<UUID>) -> Map<UUID, E>,
    transform: (E, C) -> G,
    entityName: String,
): List<FieldValue<G>> {
    val ids = contexts.map(extractId)
    val byId = findByIds(ids)
    return contexts.zip(ids).map { (ctx, id) ->
        byId[id]?.let { FieldValue.ofValue(transform(it, ctx)) }
            ?: FieldValue.ofError(NotFoundException("$entityName not found: $id"))
    }
}

@Resolver
class UserNodeResolver(
    private val userRepository: UserRepository
) : NodeResolvers.User() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = userRepository::findByIds,
            transform = { user, ctx -> user.toViaductUser(ctx) },
            entityName = "User",
        )
}

@Resolver
class BlogPostNodeResolver(
    private val postRepository: PostRepository
) : NodeResolvers.BlogPost() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductBlogPost>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = postRepository::findByIds,
            transform = { post, ctx -> post.toViaductBlogPost(ctx) },
            entityName = "Post",
        )
}

@Resolver
class CommentNodeResolver(
    private val commentRepository: CommentRepository
) : NodeResolvers.Comment() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductComment>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = commentRepository::findByIds,
            transform = { comment, ctx -> comment.toViaductComment(ctx) },
            entityName = "Comment",
        )
}

@Resolver
class LikeNodeResolver(
    private val likeRepository: LikeRepository
) : NodeResolvers.Like() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductLike>> =
        batchNodeResolve(
            contexts = contexts,
            extractId = { UUID.fromString(it.id.internalID) },
            findByIds = likeRepository::findByIds,
            transform = { like, ctx -> like.toViaductLike(ctx) },
            entityName = "Like",
        )
}
