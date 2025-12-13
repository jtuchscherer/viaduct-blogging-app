package com.example.viadapp.resolvers

import com.example.auth.RequestContext
import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.resolverbases.MutationResolvers
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.time.LocalDateTime
import java.util.*

@Resolver
class CreatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.CreatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val input = ctx.arguments.input
        val requestContext = ctx.requestContext as? RequestContext
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")
        val authenticatedUser =
            requestContext.user ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val post = postRepository.create(
            title = input.title,
            content = input.content,
            authorId = authenticatedUser.id,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return ViaductPost.Builder(ctx)
            .id(post.id.value.toString())
            .title(post.title)
            .content(post.content)
            .createdAt(post.createdAt.toString())
            .updatedAt(post.updatedAt.toString())
            .build()
    }
}

@Resolver
class UpdatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.UpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id)
        val requestContext = ctx.requestContext as? RequestContext
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")
        val authenticatedUser =
            requestContext.user ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        // First check if the post exists and user is authorized
        val existingPost = postRepository.findById(postId)
            ?: throw RuntimeException("Post not found")

        // Check if current user is the author
        if (existingPost.authorId != authenticatedUser.id) {
            throw RuntimeException("You are not authorized to update this post")
        }

        // Update the post within a transaction in the repository
        val updatedPost = postRepository.updateById(
            id = postId,
            title = input.title,
            content = input.content
        ) ?: throw RuntimeException("Failed to update post")

        return ViaductPost.Builder(ctx)
            .id(updatedPost.id.value.toString())
            .title(updatedPost.title)
            .content(updatedPost.content)
            .createdAt(updatedPost.createdAt.toString())
            .updatedAt(updatedPost.updatedAt.toString())
            .build()
    }
}

@Resolver
class DeletePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.DeletePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.id)
        val requestContext = ctx.requestContext as? RequestContext
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")
        val authenticatedUser =
            requestContext.user ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        val post = postRepository.findById(postId)
            ?: throw RuntimeException("Post not found")

        // Check if current user is the author
        if (post.authorId != authenticatedUser.id) {
            throw RuntimeException("You are not authorized to delete this post")
        }

        return postRepository.delete(postId)
    }
}
