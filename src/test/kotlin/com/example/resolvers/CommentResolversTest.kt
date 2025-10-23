package com.example.resolvers

import com.example.database.repositories.CommentRepository
import com.example.database.repositories.PostRepository
import com.example.database.repositories.UserRepository
import com.example.viadapp.resolvers.*
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Smoke tests for Comment resolvers to verify proper dependency injection.
 *
 * These tests verify that resolvers can be instantiated with their dependencies.
 * The actual business logic and integration is tested by the e2e test suite.
 */
class CommentResolversTest {

    /**
     * Verify all Comment mutation resolvers can be instantiated with their dependencies
     */
    @Test
    fun `CreateCommentResolver can be instantiated with CommentRepository and PostRepository`() {
        val commentRepository = mockk<CommentRepository>()
        val postRepository = mockk<PostRepository>()
        val resolver = CreateCommentResolver(commentRepository, postRepository)
        assertNotNull(resolver)
    }

    @Test
    fun `DeleteCommentResolver can be instantiated with CommentRepository`() {
        val commentRepository = mockk<CommentRepository>()
        val resolver = DeleteCommentResolver(commentRepository)
        assertNotNull(resolver)
    }

    /**
     * Verify all Comment query resolvers can be instantiated with their dependencies
     */
    @Test
    fun `PostCommentsResolver can be instantiated with CommentRepository`() {
        val commentRepository = mockk<CommentRepository>()
        val resolver = PostCommentsResolver(commentRepository)
        assertNotNull(resolver)
    }

    /**
     * Verify Comment field resolvers can be instantiated
     * Note: Field resolvers currently use direct database access and don't require DI
     */
    @Test
    fun `CommentAuthorResolver can be instantiated`() {
        val resolver = CommentAuthorResolver()
        assertNotNull(resolver)
    }

    @Test
    fun `CommentPostResolver can be instantiated`() {
        val resolver = CommentPostResolver()
        assertNotNull(resolver)
    }
}
