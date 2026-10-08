package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.CommentResolvers
import viaduct.api.resolver.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.UUID

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentAuthorResolver(
    private val commentRepository: CommentRepository,
) : CommentResolvers.Author() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        val commentId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        val author = commentRepository.getAuthorForComment(commentId)
            ?: throw NotFoundException("Comment not found")
        return author.toViaductUser(ctx)
    }
}

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentPostResolver(
    private val commentRepository: CommentRepository,
) : CommentResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val commentId = UUID.fromString(ctx.getObjectValue().getId().internalID)
        val post = commentRepository.getPostForComment(commentId)
            ?: throw NotFoundException("Comment not found")
        return post.toViaductPost(ctx)
    }
}
