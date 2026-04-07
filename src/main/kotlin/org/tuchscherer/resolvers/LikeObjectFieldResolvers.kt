package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.LikeResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikeUserResolver : LikeResolvers.User() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductUser {
        val likeId = UUID.fromString(ctx.objectValue.getId().internalID)

        val user = likeRepository.getUserForLike(likeId)
            ?: throw NotFoundException("Like not found")

        return ViaductUser.of(ctx) {
            id(ctx.globalIDFor(ViaductUser.Reflection, user.id.value.toString()))
            username(user.username)
            email(user.email)
            name(user.name)
            createdAt(user.createdAt.toString())
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikePostResolver : LikeResolvers.Post() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductPost {
        val likeId = UUID.fromString(ctx.objectValue.getId().internalID)

        val post = likeRepository.getPostForLike(likeId)
            ?: throw NotFoundException("Like not found")

        return ViaductPost.of(ctx) {
            id(ctx.globalIDFor(ViaductPost.Reflection, post.id.value.toString()))
            title(post.title)
            content(post.content)
            createdAt(post.createdAt.toString())
            updatedAt(post.updatedAt.toString())
        }
    }
}
