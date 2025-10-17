package com.example.viadapp.resolvers

import com.example.database.Comment as DatabaseComment
import com.example.database.Comments
import com.example.database.Post as DatabasePost
import com.example.database.User as DatabaseUser
import com.example.viadapp.resolvers.resolverbases.PostResolvers
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Comment as ViaductComment
import viaduct.api.grts.User as ViaductUser
import java.util.*

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostAuthorResolver : PostResolvers.Author() {
    override suspend fun resolve(ctx: Context): ViaductUser {
        val postId = UUID.fromString(ctx.objectValue.getId())

        return transaction {
            val post = DatabasePost.findById(postId)
                ?: throw RuntimeException("Post not found")
            val author = post.author

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

@Resolver(objectValueFragment = "fragment _ on Post { id }")
class PostCommentsFieldResolver : PostResolvers.Comments() {
    override suspend fun resolve(ctx: Context): List<ViaductComment> {
        val postIdString = ctx.objectValue.getId()
        val postId = UUID.fromString(postIdString)

        return transaction {
            DatabaseComment.find { Comments.postId eq postId }.map { comment ->
                ViaductComment.Builder(ctx)
                    .id(comment.id.value.toString())
                    .content(comment.content)
                    .createdAt(comment.createdAt.toString())
                    .build()
            }
        }
    }
}