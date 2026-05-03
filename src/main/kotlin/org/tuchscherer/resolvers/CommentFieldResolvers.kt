package org.tuchscherer.viadapp.resolvers

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.PostType
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.viadapp.resolvers.resolverbases.CommentResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.CheckedListPost as ViaductCheckedListPost
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentAuthorResolver : CommentResolvers.Author() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductUser {
        val commentId = UUID.fromString(ctx.objectValue.getId().internalID)
        val author = commentRepository.getAuthorForComment(commentId)
            ?: throw NotFoundException("Comment not found")
        return author.toViaductUser(ctx)
    }
}

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentPostResolver : CommentResolvers.Post() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductPost {
        val commentId = UUID.fromString(ctx.objectValue.getId().internalID)
        val post = commentRepository.getPostForComment(commentId)
            ?: throw NotFoundException("Comment not found")
        return when (post.postType) {
            PostType.CHECKED_LIST ->
                ctx.nodeRef(ctx.globalIDFor(ViaductCheckedListPost.Reflection, post.id.value.toString()))
            else -> post.toViaductBlogPost(ctx)
        }
    }
}
