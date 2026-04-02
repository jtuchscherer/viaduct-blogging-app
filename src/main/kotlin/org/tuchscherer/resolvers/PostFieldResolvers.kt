package org.tuchscherer.viadapp.resolvers

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

    override suspend fun resolve(ctx: Context): ViaductUser {
        val postId = UUID.fromString(ctx.objectValue.getId())

        val author = postRepository.getAuthorForPost(postId)
            ?: throw RuntimeException("Post not found")

        return author.toViaductUser(ctx)
    }

    override suspend fun batchResolve(contexts: List<Context>): List<FieldValue<ViaductUser>> {
        val postIds = contexts.map { UUID.fromString(it.objectValue.getId()) }
        val authorsById = postRepository.getAuthorsByPostIds(postIds)

        return contexts.map { ctx ->
            val postId = UUID.fromString(ctx.objectValue.getId())
            val author = authorsById[postId]
            if (author != null) {
                FieldValue.ofValue(author.toViaductUser(ctx))
            } else {
                FieldValue.ofError(RuntimeException("Post not found: $postId"))
            }
        }
    }

    private fun org.tuchscherer.database.User.toViaductUser(ctx: Context) =
        ViaductUser.Builder(ctx)
            .id(id.value.toString())
            .username(username)
            .email(email)
            .name(name)
            .createdAt(createdAt.toString())
            .build()
}

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostCommentsFieldResolver : PostResolvers.Comments() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return commentRepository.findByPostId(postId).map { comment ->
            ViaductComment.Builder(ctx)
                .id(comment.id.value.toString())
                .content(comment.content)
                .createdAt(comment.createdAt.toString())
                .build()
        }
    }
}