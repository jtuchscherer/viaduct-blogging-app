package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import org.tuchscherer.viadapp.resolvers.resolverbases.QueryResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Comment as ViaductComment
import java.time.LocalDateTime
import java.util.*

@Resolver
class CreateCommentResolver(
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository
) : MutationResolvers.CreateComment() {
    override suspend fun resolve(ctx: Context): ViaductComment {
        val input = ctx.arguments.input
        val user = requireAuth(ctx.requestContext)

        require(input.content.isNotBlank()) { "Content cannot be blank" }
        require(input.content.length <= 10_000) { "Content cannot exceed 10,000 characters" }

        val postId = UUID.fromString(input.postId.internalID)
        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        val comment = commentRepository.create(
            content = input.content,
            postId = post.id.value,
            authorId = user.id.value,
            createdAt = LocalDateTime.now()
        )

        return comment.toViaductComment(ctx)
    }
}

@Resolver
class DeleteCommentResolver(
    private val commentRepository: CommentRepository
) : MutationResolvers.DeleteComment() {
    override suspend fun resolve(ctx: Context): Boolean {
        val commentId = UUID.fromString(ctx.arguments.id.internalID)
        val user = requireAuth(ctx.requestContext)

        val comment = commentRepository.findById(commentId)
            ?: throw NotFoundException("Comment not found")

        if (comment.authorId != user.id) {
            throw AuthorizationException("You are not authorized to delete this comment")
        }

        return commentRepository.delete(commentId)
    }
}

@Resolver
class PostCommentsResolver(
    private val commentRepository: CommentRepository
) : QueryResolvers.PostComments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.arguments.postId.internalID)

        return commentRepository.findByPostId(postId).map { it.toViaductComment(ctx) }
    }
}
