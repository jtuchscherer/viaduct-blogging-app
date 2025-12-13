package com.example.viadapp.resolvers

import com.example.database.repositories.LikeRepository
import com.example.viadapp.resolvers.resolverbases.LikeResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikeUserResolver : LikeResolvers.User() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductUser {
        val likeId = UUID.fromString(ctx.objectValue.getId())

        val user = likeRepository.getUserForLike(likeId)
            ?: throw RuntimeException("Like not found")

        return ViaductUser.Builder(ctx)
            .id(user.id.value.toString())
            .username(user.username)
            .email(user.email)
            .name(user.name)
            .createdAt(user.createdAt.toString())
            .build()
    }
}

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikePostResolver : LikeResolvers.Post() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductPost {
        val likeId = UUID.fromString(ctx.objectValue.getId())

        val post = likeRepository.getPostForLike(likeId)
            ?: throw RuntimeException("Like not found")

        return ViaductPost.Builder(ctx)
            .id(post.id.value.toString())
            .title(post.title)
            .content(post.content)
            .createdAt(post.createdAt.toString())
            .updatedAt(post.updatedAt.toString())
            .build()
    }
}