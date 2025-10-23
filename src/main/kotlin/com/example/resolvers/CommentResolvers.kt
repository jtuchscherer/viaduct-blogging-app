package com.example.viadapp.resolvers

import com.example.database.repositories.CommentRepository
import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import com.example.viadapp.resolvers.resolverbases.QueryResolvers
import com.example.web.GraphQLServer
import org.jetbrains.exposed.dao.id.EntityID
import viaduct.api.Resolver
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
        val authenticatedUser = (ctx.requestContext as? Map<*, *>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val postId = UUID.fromString(input.postId)
        val post = postRepository.findById(postId)
            ?: throw RuntimeException("Post not found")

        val comment = commentRepository.create(
            content = input.content,
            postId = post.id,
            authorId = authenticatedUser.id,
            createdAt = LocalDateTime.now()
        )

        return ViaductComment.Builder(ctx)
            .id(comment.id.value.toString())
            .content(comment.content)
            .createdAt(comment.createdAt.toString())
            .build()
    }
}

@Resolver
class DeleteCommentResolver(
    private val commentRepository: CommentRepository
) : MutationResolvers.DeleteComment() {
    override suspend fun resolve(ctx: Context): Boolean {
        val commentId = UUID.fromString(ctx.arguments.id)
        val authenticatedUser = (ctx.requestContext as? Map<*, *>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val comment = commentRepository.findById(commentId)
            ?: throw RuntimeException("Comment not found")

        // Check if current user is the author
        if (comment.authorId != authenticatedUser.id) {
            throw RuntimeException("You are not authorized to delete this comment")
        }

        return commentRepository.delete(commentId)
    }
}

@Resolver
class PostCommentsResolver(
    private val commentRepository: CommentRepository
) : QueryResolvers.PostComments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.arguments.postId)

        return commentRepository.findByPostId(EntityID(postId, com.example.database.Posts)).map { comment ->
            ViaductComment.Builder(ctx)
                .id(comment.id.value.toString())
                .content(comment.content)
                .createdAt(comment.createdAt.toString())
                .build()
        }
    }
}
