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
        return user.toViaductUser(ctx)
    }
}

@Resolver(objectValueFragment = "fragment _ on Like { id }")
class LikePostResolver : LikeResolvers.Post() {
    private val likeRepository: LikeRepository by inject(LikeRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductPost {
        val likeId = UUID.fromString(ctx.objectValue.getId().internalID)
        val post = likeRepository.getPostForLike(likeId)
            ?: throw NotFoundException("Like not found")
        return post.toViaductBlogPost(ctx)
    }
}
