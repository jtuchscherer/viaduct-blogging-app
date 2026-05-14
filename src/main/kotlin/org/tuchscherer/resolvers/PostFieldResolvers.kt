package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.BlogPostResolvers
import viaduct.api.FieldValue
import viaduct.api.resolver.Resolver
import viaduct.api.grts.BlogPost as ViaductBlogPost
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostAuthorResolver(
    private val postRepository: PostRepository,
) : BlogPostResolvers.Author() {
    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val postIds = contexts.map { UUID.fromString(it.getObjectValue().getId().internalID) }
        val authorIdByPostId = postRepository.getAuthorIdsByPostIds(postIds)

        return contexts.zip(postIds).map { (ctx, postId) ->
            val authorId = authorIdByPostId[postId]
                ?: return@map FieldValue.ofError(NotFoundException("Post not found: $postId"))
            FieldValue.ofValue(ctx.nodeRef(ctx.globalIDFor(ViaductUser.Reflection, authorId.toString())))
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on BlogPost { id }")
class PostCommentsFieldResolver(
    private val commentRepository: CommentRepository,
) : BlogPostResolvers.Comments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postId = UUID.fromString(ctx.getObjectValue().getId().internalID)

        return commentRepository.findByPostId(postId).map { it.toViaductComment(ctx) }
    }
}
