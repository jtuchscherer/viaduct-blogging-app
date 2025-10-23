package com.example.viadapp.resolvers

import com.example.database.repositories.LikeRepository
import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import com.example.web.GraphQLServer
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.time.LocalDateTime
import java.util.*

@Resolver
class LikePostMutationResolver(
    private val likeRepository: LikeRepository,
    private val postRepository: PostRepository
) : MutationResolvers.LikePost() {
    override suspend fun resolve(ctx: Context): ViaductLike {
        val postId = UUID.fromString(ctx.arguments.postId)
        val authenticatedUser = (ctx.requestContext as? Map<*, *>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val post = postRepository.findById(postId)
            ?: throw RuntimeException("Post not found")

        // Check if user already liked the post
        val existingLike = likeRepository.findByPostAndUser(post.id, authenticatedUser.id)

        if (existingLike != null) {
            // Already liked, just return the existing like
            return ViaductLike.Builder(ctx)
                .id(existingLike.id.value.toString())
                .createdAt(existingLike.createdAt.toString())
                .build()
        } else {
            // Create new like
            val like = likeRepository.create(
                postId = post.id,
                userId = authenticatedUser.id,
                createdAt = LocalDateTime.now()
            )

            return ViaductLike.Builder(ctx)
                .id(like.id.value.toString())
                .createdAt(like.createdAt.toString())
                .build()
        }
    }
}

@Resolver
class UnlikePostResolver(
    private val likeRepository: LikeRepository,
    private val postRepository: PostRepository
) : MutationResolvers.UnlikePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.postId)
        val authenticatedUser = (ctx.requestContext as? Map<*, *>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val post = postRepository.findById(postId)
            ?: throw RuntimeException("Post not found")

        return likeRepository.deleteByPostAndUser(post.id, authenticatedUser.id)
    }
}
