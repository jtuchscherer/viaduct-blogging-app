package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.AuthorizationException
import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import java.time.LocalDateTime
import java.util.UUID

@Resolver
class CreatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.CreatePost() {
    override suspend fun resolve(ctx: Context): ViaductBlogPost {
        val input = ctx.arguments.input
        val user = requireAuth(ctx.requestContext)

        PostValidation.validateTitle(input.title)
        PostValidation.validateContent(input.content)

        val post = postRepository.create(
            title = input.title,
            content = input.content,
            authorId = user.id.value,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        return post.toViaductBlogPost(ctx)
    }
}

@Resolver
class UpdatePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.UpdatePost() {
    override suspend fun resolve(ctx: Context): ViaductBlogPost {
        val input = ctx.arguments.input
        val postId = UUID.fromString(input.id.internalID)
        val user = requireAuth(ctx.requestContext)

        input.title?.let(PostValidation::validateTitle)
        input.content?.let(PostValidation::validateContent)

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

        return updatedPost.toViaductBlogPost(ctx)
    }
}

@Resolver
class DeletePostResolver(
    private val postRepository: PostRepository
) : MutationResolvers.DeletePost() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.arguments.id.internalID)
        val user = requireAuth(ctx.requestContext)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        if (post.authorId != user.id) {
            throw AuthorizationException("You are not authorized to delete this post")
        }

        return postRepository.delete(postId)
    }
}
