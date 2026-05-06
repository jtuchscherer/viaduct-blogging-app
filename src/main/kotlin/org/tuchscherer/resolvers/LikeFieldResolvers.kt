package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.optionalAuth
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.BlogPostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.util.*

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostLikesResolver : BlogPostResolvers.Likes() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return likeRepository.findByPostId(postId).map { like ->
            ViaductLike.of(ctx) {
                id(ctx.globalIDFor(ViaductLike.Reflection, like.id.value.toString()))
                createdAt(like.createdAt.toString())
            }
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostLikeCountResolver : BlogPostResolvers.LikeCount() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return likeRepository.countByPostId(postId).toCountInt()
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostIsLikedByMeResolver : BlogPostResolvers.IsLikedByMe() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        val user = optionalAuth(ctx.requestContext) ?: return false
        return likeRepository.existsByPostAndUser(postId, user.id.value)
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostCommentCountResolver : BlogPostResolvers.CommentCount() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return commentRepository.countByPostId(postId).toCountInt()
    }
}
