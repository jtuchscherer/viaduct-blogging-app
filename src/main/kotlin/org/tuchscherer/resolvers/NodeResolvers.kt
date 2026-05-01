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

@Resolver
class UserNodeResolver(
    private val userRepository: UserRepository
) : NodeResolvers.User() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val ids = contexts.map { UUID.fromString(it.id.internalID) }
        val byId = userRepository.findByIds(ids)
        return contexts.zip(ids).map { (ctx, id) ->
            byId[id]?.let { FieldValue.ofValue(it.toViaductUser(ctx)) }
                ?: FieldValue.ofError(NotFoundException("User not found: $id"))
        }
    }
}

@Resolver
class BlogPostNodeResolver(
    private val postRepository: PostRepository
) : NodeResolvers.BlogPost() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductBlogPost>> {
        val ids = contexts.map { UUID.fromString(it.id.internalID) }
        val byId = postRepository.findByIds(ids)
        return contexts.zip(ids).map { (ctx, id) ->
            byId[id]?.let { FieldValue.ofValue(it.toViaductBlogPost(ctx)) }
                ?: FieldValue.ofError(NotFoundException("Post not found: $id"))
        }
    }
}

@Resolver
class CommentNodeResolver(
    private val commentRepository: CommentRepository
) : NodeResolvers.Comment() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductComment>> {
        val ids = contexts.map { UUID.fromString(it.id.internalID) }
        val byId = commentRepository.findByIds(ids)
        return contexts.zip(ids).map { (ctx, id) ->
            byId[id]?.let { FieldValue.ofValue(it.toViaductComment(ctx)) }
                ?: FieldValue.ofError(NotFoundException("Comment not found: $id"))
        }
    }
}

@Resolver
class LikeNodeResolver(
    private val likeRepository: LikeRepository
) : NodeResolvers.Like() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductLike>> {
        val ids = contexts.map { UUID.fromString(it.id.internalID) }
        val byId = likeRepository.findByIds(ids)
        return contexts.zip(ids).map { (ctx, id) ->
            byId[id]?.let { FieldValue.ofValue(it.toViaductLike(ctx)) }
                ?: FieldValue.ofError(NotFoundException("Like not found: $id"))
        }
    }
}
