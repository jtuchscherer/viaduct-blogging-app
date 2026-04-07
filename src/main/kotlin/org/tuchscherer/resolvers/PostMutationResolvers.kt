package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
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
        val user = requireAuth(ctx.requestContext)

        require(input.title.isNotBlank()) { "Title cannot be blank" }
        require(input.content.isNotBlank()) { "Content cannot be blank" }

        val post = postRepository.create(
            title = input.title,
            content = input.content,
            authorId = user.id,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return ViaductPost.of(ctx) {
            id(post.id.value.toString())
            title(post.title)
            content(post.content)
            createdAt(post.createdAt.toString())
            updatedAt(post.updatedAt.toString())
        }
    }
}

@Resolver
class UpdatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.UpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id)
        val user = requireAuth(ctx.requestContext)

        input.title?.let { require(it.isNotBlank()) { "Title cannot be blank" } }

        val existingPost = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        if (existingPost.authorId != user.id) {
            throw AuthorizationException("You are not authorized to update this post")
        }

        val updatedPost = postRepository.updateById(
            id = postId,
            title = input.title,
            content = input.content
        ) ?: throw NotFoundException("Failed to update post")

        return ViaductPost.of(ctx) {
            id(updatedPost.id.value.toString())
            title(updatedPost.title)
            content(updatedPost.content)
            createdAt(updatedPost.createdAt.toString())
            updatedAt(updatedPost.updatedAt.toString())
        }
    }
}

@Resolver
class DeletePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.DeletePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.id)
        val user = requireAuth(ctx.requestContext)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        if (post.authorId != user.id) {
            throw AuthorizationException("You are not authorized to delete this post")
        }

        return postRepository.delete(postId)
    }
}
