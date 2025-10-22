package com.example.viadapp.resolvers

import com.example.database.Post as DatabasePost
import com.example.viadapp.resolvers.resolverbases.QueryResolvers
import com.example.web.GraphQLServer
import org.jetbrains.exposed.sql.transactions.transaction
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.util.*

@Resolver
class PostsResolver : QueryResolvers.Posts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        return transaction {
            DatabasePost.all().map { post ->
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
}

@Resolver
class PostResolver : QueryResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost? {
        val postId = UUID.fromString(ctx.arguments.id)
        return transaction {
            DatabasePost.findById(postId)?.let { post ->
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
}

@Resolver
class MyPostsResolver : QueryResolvers.MyPosts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        val authenticatedUser = (ctx.requestContext as? Map<String, Any?>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return transaction {
            DatabasePost.find { com.example.database.Posts.authorId eq authenticatedUser.id }.map { post ->
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
}

