package com.example.viadapp.resolvers

import com.example.auth.RequestContext
import com.example.database.repositories.LikeRepository
import com.example.viadapp.resolvers.resolverbases.PostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikesResolver : PostResolvers.Likes() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return likeRepository.findByPostId(postId).map { like ->
            ViaductLike.Builder(ctx)
                .id(like.id.value.toString())
                .createdAt(like.createdAt.toString())
                .build()
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikeCountResolver : PostResolvers.LikeCount() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Int {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return likeRepository.countByPostId(postId).toInt()
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostIsLikedByMeResolver : PostResolvers.IsLikedByMe() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): Boolean {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        // Get authenticated user (optional for this field)
        val user = (ctx.requestContext as? RequestContext)?.user

        if (user == null) {
            return false
        }

        return likeRepository.existsByPostAndUser(postId, user.id.value)
    }
}