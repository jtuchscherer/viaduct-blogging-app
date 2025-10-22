package com.example.viadapp.resolvers

import com.example.database.Like as DatabaseLike
import com.example.database.Likes
import com.example.database.Post as DatabasePost
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import com.example.web.GraphQLServer
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.time.LocalDateTime
import java.util.*

@Resolver
class LikePostMutationResolver : MutationResolvers.LikePost() {
    override suspend fun resolve(ctx: Context): ViaductLike {
        val postId = UUID.fromString(ctx.arguments.postId)
        val authenticatedUser = (ctx.requestContext as? Map<String, Any?>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val post = DatabasePost.findById(postId)
                ?: throw RuntimeException("Post not found")

            // Check if user already liked the post
            val existingLike = DatabaseLike.find {
                (Likes.postId eq postId) and (Likes.userId eq authenticatedUser.id)
            }.firstOrNull()

            if (existingLike != null) {
                // Already liked, just return the existing like
                ViaductLike.Builder(ctx)
                    .id(existingLike.id.value.toString())
                    .createdAt(existingLike.createdAt.toString())
                    .build()
            } else {
                // Create new like
                val like = DatabaseLike.new {
                    this.postId = post.id
                    userId = authenticatedUser.id
                    createdAt = LocalDateTime.now()
                }

                ViaductLike.Builder(ctx)
                    .id(like.id.value.toString())
                    .createdAt(like.createdAt.toString())
                    .build()
            }
        }
    }
}

@Resolver
class UnlikePostResolver : MutationResolvers.UnlikePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.postId)
        val authenticatedUser = (ctx.requestContext as? Map<String, Any?>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            val like = DatabaseLike.find {
                (Likes.postId eq postId) and (Likes.userId eq authenticatedUser.id)
            }.firstOrNull()

            if (like != null) {
                like.delete()
                true
            } else {
                // No like to remove
                false
            }
        }
    }
}