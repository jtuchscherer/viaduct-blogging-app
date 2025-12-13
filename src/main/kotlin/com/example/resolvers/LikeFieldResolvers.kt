package com.example.viadapp.resolvers

import com.example.auth.RequestContext
import com.example.database.Like as DatabaseLike
import com.example.database.Likes
import com.example.database.Post as DatabasePost
import com.example.viadapp.resolvers.resolverbases.PostResolvers
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Like as ViaductLike
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikesResolver : PostResolvers.Likes() {
    override suspend fun resolve(ctx: Context): List<ViaductLike> {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return transaction {
            DatabaseLike.find { Likes.postId eq postId }.map { like ->
                ViaductLike.Builder(ctx)
                    .id(like.id.value.toString())
                    .createdAt(like.createdAt.toString())
                    .build()
            }
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostLikeCountResolver : PostResolvers.LikeCount() {
    override suspend fun resolve(ctx: Context): Int {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return transaction {
            DatabaseLike.find { Likes.postId eq postId }.count().toInt()
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostIsLikedByMeResolver : PostResolvers.IsLikedByMe() {
    override suspend fun resolve(ctx: Context): Boolean {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        // Get authenticated user (optional for this field)
        val user = (ctx.requestContext as? RequestContext)?.user

        if (user == null) {
            return false
        }

        return transaction {
            DatabaseLike.find {
                (Likes.postId eq postId) and (Likes.userId eq user.id)
            }.count() > 0
        }
    }
}