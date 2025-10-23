package com.example.viadapp.resolvers

import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.resolverbases.QueryResolvers
import com.example.web.GraphQLServer
import viaduct.api.Resolver
import viaduct.api.grts.Post as ViaductPost
import java.util.*

@Resolver
class PostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Posts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        return postRepository.findAll().map { post ->
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

@Resolver
class PostResolver(
    private val postRepository: PostRepository
) : QueryResolvers.Post() {
    override suspend fun resolve(ctx: Context): ViaductPost? {
        val postId = UUID.fromString(ctx.arguments.id)
        return postRepository.findById(postId)?.let { post ->
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

@Resolver
class MyPostsResolver(
    private val postRepository: PostRepository
) : QueryResolvers.MyPosts() {
    override suspend fun resolve(ctx: Context): List<ViaductPost> {
        val authenticatedUser = (ctx.requestContext as? Map<*, *>)?.get(GraphQLServer.AUTHENTICATED_USER_KEY) as? com.example.database.User
            ?: throw RuntimeException("Authentication required. Please provide a valid JWT token.")

        return postRepository.findByAuthorId(authenticatedUser.id).map { post ->
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

