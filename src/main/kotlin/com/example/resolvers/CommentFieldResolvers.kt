package com.example.viadapp.resolvers

import com.example.database.Comment as DatabaseComment
import com.example.database.Post as DatabasePost
import com.example.database.User as DatabaseUser
import com.example.viadapp.resolvers.resolverbases.CommentResolvers
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentAuthorResolver : CommentResolvers.Author() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        val commentId = UUID.fromString(ctx.objectValue.getId())

        return transaction {
            val comment = DatabaseComment.findById(commentId)
                ?: throw RuntimeException("Comment not found")
            val author = comment.author

            ViaductUser.Builder(ctx)
                .id(author.id.value.toString())
                .username(author.username)
                .email(author.email)
                .name(author.name)
                .createdAt(author.createdAt.toString())
                .build()
        }
    }
}

@Resolver(objectValueFragment = "fragment _ on Comment { id }")
class CommentPostResolver : CommentResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost {
        val commentId = UUID.fromString(ctx.objectValue.getId())

        return transaction {
            val comment = DatabaseComment.findById(commentId)
                ?: throw RuntimeException("Comment not found")
            val post = comment.post

            ViaductPost.Builder(ctx)
                .id(post.id.value.toString())
                .title(post.title)
                .content(post.content)
                .createdAt(post.createdAt.toString())
                .updatedAt(post.updatedAt.toString())
                .build()
        }
    }
}