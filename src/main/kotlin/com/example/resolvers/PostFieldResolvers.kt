package com.example.viadapp.resolvers

import com.example.database.repositories.CommentRepository
import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.resolverbases.PostResolvers
import org.koin.java.KoinJavaComponent.inject
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

        return ViaductUser.Builder(ctx)
            .id(author.id.value.toString())
            .username(author.username)
            .email(author.email)
            .name(author.name)
            .createdAt(author.createdAt.toString())
            .build()
    }
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