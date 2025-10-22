package com.example.viadapp.resolvers

import com.example.database.Comment as DatabaseComment
import com.example.database.Comments
import com.example.database.Post as DatabasePost
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import com.example.viadapp.resolvers.resolverbases.QueryResolvers
import com.example.web.GraphQLServer
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Comment as ViaductComment
import java.time.LocalDateTime
import java.util.*

@Resolver
class CreateCommentResolver : MutationResolvers.CreateComment() {
    override suspend fun resolve(ctx: Context): ViaductComment {
        val input = ctx.arguments.input
        val authenticatedUser = (ctx.requestContext as? Map<String, Any?>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val postId = UUID.fromString(input.postId)
            val post = DatabasePost.findById(postId)
                ?: throw RuntimeException("Post not found")

            val comment = DatabaseComment.new {
                content = input.content
                this.postId = post.id
                this.authorId = authenticatedUser.id
                createdAt = LocalDateTime.now()
            }

            ViaductComment.Builder(ctx)
                .id(comment.id.value.toString())
                .content(comment.content)
                .createdAt(comment.createdAt.toString())
                .build()
        }
    }
}

@Resolver
class DeleteCommentResolver : MutationResolvers.DeleteComment() {
    override suspend fun resolve(ctx: Context): Boolean {
        val commentId = UUID.fromString(ctx.arguments.id)
        val authenticatedUser = (ctx.requestContext as? Map<String, Any?>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val comment = DatabaseComment.findById(commentId)
                ?: throw RuntimeException("Comment not found")

            // Check if current user is the author
            if (comment.authorId != authenticatedUser.id) {
                throw RuntimeException("You are not authorized to delete this comment")
            }

            comment.delete()
            true
        }
    }
}

@Resolver
class PostCommentsResolver : QueryResolvers.PostComments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.arguments.postId)

        return transaction {
            DatabaseComment.find { Comments.postId eq postId }.map { comment ->
                ViaductComment.Builder(ctx)
                    .id(comment.id.value.toString())
                    .content(comment.content)
                    .createdAt(comment.createdAt.toString())
                    .build()
            }
        }
    }
}