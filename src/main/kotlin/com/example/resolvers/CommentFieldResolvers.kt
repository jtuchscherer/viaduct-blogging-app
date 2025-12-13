package com.example.viadapp.resolvers

import com.example.database.repositories.CommentRepository
import com.example.viadapp.resolvers.resolverbases.CommentResolvers
import org.koin.java.KoinJavaComponent.inject
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentAuthorResolver : CommentResolvers.Author() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductUser {
        val commentId = UUID.fromString(ctx.objectValue.getId())

        val author = commentRepository.getAuthorForComment(commentId)
            ?: throw RuntimeException("Comment not found")

        return ViaductUser.Builder(ctx)
            .id(author.id.value.toString())
            .username(author.username)
            .email(author.email)
            .name(author.name)
            .createdAt(author.createdAt.toString())
            .build()
    }
}

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentPostResolver : CommentResolvers.Post() {
    private val commentRepository: CommentRepository by inject(CommentRepository::class.java)

    override suspend fun resolve(ctx: Context): ViaductPost {
        val commentId = UUID.fromString(ctx.objectValue.getId())

        val post = commentRepository.getPostForComment(commentId)
            ?: throw RuntimeException("Comment not found")

        return ViaductPost.Builder(ctx)
            .id(post.id.value.toString())
            .title(post.title)
            .content(post.content)
            .createdAt(post.createdAt.toString())
            .updatedAt(post.updatedAt.toString())
            .build()
    }
}