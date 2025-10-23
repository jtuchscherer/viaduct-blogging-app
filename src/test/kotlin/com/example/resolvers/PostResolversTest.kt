package com.example.resolvers

import com.example.database.repositories.PostRepository
import com.example.viadapp.resolvers.*
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Smoke tests for Post resolvers to verify proper dependency injection.
 *
 * These tests verify that resolvers can be instantiated with their dependencies.
 * The actual business logic and integration is tested by the e2e test suite.
 */
class PostResolversTest {

    /**
     * Verify all Post mutation resolvers can be instantiated with their dependencies
     */
    @Test
    fun `CreatePostResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = CreatePostResolver(postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `UpdatePostResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = UpdatePostResolver(postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `DeletePostResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = DeletePostResolver(postRepository)
        assertNotNull(resolver)
    }

    /**
     * Verify all Post query resolvers can be instantiated with their dependencies
     */
    @Test
    fun `PostsResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = PostsResolver(postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `PostResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = PostResolver(postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `MyPostsResolver can be instantiated with PostRepository`() {
        val postRepository = mockk<PostRepository>()
        val resolver = MyPostsResolver(postRepository)
        assertNotNull(resolver)
    }

    /**
     * Verify Post field resolvers can be instantiated
     * Note: Field resolvers currently use direct database access and don't require DI
     */
    @Test
    fun `PostAuthorResolver can be instantiated`() {
        val resolver = PostAuthorResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `PostCommentsFieldResolver can be instantiated`() {
        val resolver = PostCommentsFieldResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `PostLikesResolver can be instantiated`() {
        val resolver = PostLikesResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `PostLikeCountResolver can be instantiated`() {
        val resolver = PostLikeCountResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `PostIsLikedByMeResolver can be instantiated`() {
        val resolver = PostIsLikedByMeResolver()
        assertNotNull(resolver)
    }
}
