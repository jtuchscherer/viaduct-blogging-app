package com.example.viadapp.resolvers

import com.example.database.Like as DatabaseLike
import com.example.viadapp.resolvers.resolverbases.LikeResolvers
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikeUserResolver : LikeResolvers.User() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        val likeId = UUID.fromString(ctx.objectValue.getId())

        return transaction {
            val like = DatabaseLike.findById(likeId)
                ?: throw RuntimeException("Like not found")
            val user = like.user

            ViaductUser.Builder(ctx)
                .id(user.id.value.toString())
                .username(user.username)
                .email(user.email)
                .name(user.name)
                .createdAt(user.createdAt.toString())
                .build()
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikePostResolver : LikeResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val likeId = UUID.fromString(ctx.objectValue.getId())

        return transaction {
            val like = DatabaseLike.findById(likeId)
                ?: throw RuntimeException("Like not found")
            val post = like.post

            ViaductPost.Builder(ctx)
                .id(post.id.value.toString())
                .title(post.title)
                .content(post.content)
                .createdAt(post.createdAt.toString())
                .updatedAt(post.updatedAt.toString())
                .build()
        }
    }
}