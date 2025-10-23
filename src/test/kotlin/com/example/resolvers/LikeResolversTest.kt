package com.example.resolvers

import com.example.database.repositories.LikeRepository
import com.example.database.repositories.PostRepository
import com.example.database.repositories.UserRepository
import com.example.viadapp.resolvers.*
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Smoke tests for Like resolvers to verify proper dependency injection.
 *
 * These tests verify that resolvers can be instantiated with their dependencies.
 * The actual business logic and integration is tested by the e2e test suite.
 */
class LikeResolversTest {

    /**
     * Verify all Like mutation resolvers can be instantiated with their dependencies
     */
    @Test
    fun `LikePostMutationResolver can be instantiated with LikeRepository and PostRepository`() {
        val likeRepository = mockk<LikeRepository>()
        val postRepository = mockk<PostRepository>()
        val resolver = LikePostMutationResolver(likeRepository, postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `UnlikePostResolver can be instantiated with LikeRepository and PostRepository`() {
        val likeRepository = mockk<LikeRepository>()
        val postRepository = mockk<PostRepository>()
        val resolver = UnlikePostResolver(likeRepository, postRepository)
        assertNotNull(resolver)
    }

    /**
     * Verify Like field resolvers can be instantiated
     * Note: Field resolvers currently use direct database access and don't require DI
     */
    @Test
    fun `LikeUserResolver can be instantiated`() {
        val resolver = LikeUserResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `LikePostResolver can be instantiated`() {
        val resolver = LikePostResolver()
        assertNotNull(resolver)
    }
}
