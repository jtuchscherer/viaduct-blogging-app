package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.PostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikesResolver : PostResolvers.Likes() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postId = UUID.fromString(ctx.objectValue.getId().internalID)

        return likeRepository.findByPostId(postId).map { like ->
            ViaductLike.of(ctx) {
                id(ctx.globalIDFor(ViaductLike.Reflection, like.id.value.toString()))
                createdAt(like.createdAt.toString())
            }
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikeCountResolver : PostResolvers.LikeCount() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.objectValue.getId().internalID)

        return likeRepository.countByPostId(postId).toInt()
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostIsLikedByMeResolver : PostResolvers.IsLikedByMe() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postId = UUID.fromString(ctx.objectValue.getId().internalID)

        // Get authenticated user (optional for this field)
        val user = (ctx.requestContext as? RequestContext)?.user

        if (user == null) {
            return false
        }

        return likeRepository.existsByPostAndUser(postId, user.id.value)
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostCommentCountResolver : PostResolvers.CommentCount() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postId = UUID.fromString(ctx.objectValue.getId().internalID)

        return commentRepository.countByPostId(postId).toInt()
    }
}
