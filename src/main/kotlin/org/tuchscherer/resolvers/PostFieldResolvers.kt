package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.PostResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.FieldValue
import viaduct.api.Resolver
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostAuthorResolver : PostResolvers.Author() {
    private val postRepository: PostRepository by inject(PostRepository::class.java)

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val postIds = contexts.map { UUID.fromString(it.objectValue.getId().internalID) }
        val authorIdByPostId = postRepository.getAuthorIdsByPostIds(postIds)

        return contexts.zip(postIds).map { (ctx, postId) ->
            val authorId = authorIdByPostId[postId]
                ?: return@map FieldValue.ofError(NotFoundException("Post not found: $postId"))
            FieldValue.ofValue(ctx.nodeFor(ctx.globalIDFor(ViaductUser.Reflection, authorId.toString())))
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostCommentsFieldResolver : PostResolvers.Comments() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.objectValue.getId().internalID)

        return commentRepository.findByPostId(postId).map { it.toViaductComment(ctx) }
    }
}
