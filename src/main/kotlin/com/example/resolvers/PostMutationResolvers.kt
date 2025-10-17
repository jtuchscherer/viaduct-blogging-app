package com.example.viadapp.resolvers

import com.example.database.Post as DatabasePost
import com.example.database.User as DatabaseUser
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.time.LocalDateTime
import java.util.*

@Resolver
class CreatePostResolver : MutationResolvers.CreatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val input = ctx.arguments.input
        val authenticatedUser = (ctx.requestContext as? DatabaseUser)
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val post = DatabasePost.new {
                title = input.title
                content = input.content
                authorId = authenticatedUser.id
                createdAt = LocalDateTime.now()
                updatedAt = LocalDateTime.now()
            }

            ViaductPost.Builder(ctx)
                .id(post.id.value.toString())
                .title(post.title)
                .content(post.content)
                .createdAt(post.createdAt.toString())
                .updatedAt(post.updatedAt.toString())
                .build()
        }
    }
}

@Resolver
class UpdatePostResolver : MutationResolvers.UpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id)
        val authenticatedUser = (ctx.requestContext as? DatabaseUser)
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val post = DatabasePost.findById(postId)
                ?: throw RuntimeException("Post not found")

            // Check if current user is the author
            if (post.authorId != authenticatedUser.id) {
                throw RuntimeException("You are not authorized to update this post")
            }

            input.title?.let { post.title = it }
            input.content?.let { post.content = it }
            post.updatedAt = LocalDateTime.now()

            ViaductPost.Builder(ctx)
                .id(post.id.value.toString())
                .title(post.title)
                .content(post.content)
                .createdAt(post.createdAt.toString())
                .updatedAt(post.updatedAt.toString())
                .build()
        }
    }
}

@Resolver
class DeletePostResolver : MutationResolvers.DeletePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.id)
        val authenticatedUser = (ctx.requestContext as? DatabaseUser)
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val post = DatabasePost.findById(postId)
                ?: throw RuntimeException("Post not found")

            // Check if current user is the author
            if (post.authorId != authenticatedUser.id) {
                throw RuntimeException("You are not authorized to delete this post")
            }

            post.delete()
            true
        }
    }
}