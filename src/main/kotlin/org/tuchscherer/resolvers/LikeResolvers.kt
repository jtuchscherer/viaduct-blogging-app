package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.auth.requireAuth
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.MutationResolvers
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
        val user = requireAuth(ctx.requestContext)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        val existingLike = likeRepository.findByPostAndUser(post.id, user.id)

        if (existingLike != null) {
            return ViaductLike.Builder(ctx)
                .id(existingLike.id.value.toString())
                .createdAt(existingLike.createdAt.toString())
                .build()
        } else {
            val like = likeRepository.create(
                postId = post.id,
                userId = user.id,
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
        val user = requireAuth(ctx.requestContext)

        val post = postRepository.findById(postId)
            ?: throw NotFoundException("Post not found")

        return likeRepository.deleteByPostAndUser(post.id, user.id)
    }
}
