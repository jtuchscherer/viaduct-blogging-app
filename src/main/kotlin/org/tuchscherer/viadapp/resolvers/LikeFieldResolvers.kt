package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.optionalAuth
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.BlogPostResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.util.UUID

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostLikesResolver(
    private val likeRepository: LikeRepository,
) : BlogPostResolvers.Likes() {
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
class PostLikeCountResolver(
    private val likeRepository: LikeRepository,
) : BlogPostResolvers.LikeCount() {
    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return likeRepository.countByPostId(postId).toCountInt()
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostIsLikedByMeResolver(
    private val likeRepository: LikeRepository,
) : BlogPostResolvers.IsLikedByMe() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        val user = optionalAuth(ctx.requestContext) ?: return false
        return likeRepository.existsByPostAndUser(postId, user.id.value)
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostCommentCountResolver(
    private val commentRepository: CommentRepository,
) : BlogPostResolvers.CommentCount() {
    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return commentRepository.countByPostId(postId).toCountInt()
    }
}
