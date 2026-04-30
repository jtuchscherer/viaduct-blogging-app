package org.tuchscherer.complexity

import graphql.analysis.FieldComplexityEnvironment
import graphql.language.Field
import graphql.schema.GraphQLObjectType
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BlogFieldComplexityCalculatorTest {

    private val calculator = BlogFieldComplexityCalculator()

    @Test
    fun `scalar fields cost 1 plus child`() {
        assertEquals(1, calculate(parent = "Post", name = "title", child = 0))
        assertEquals(3, calculate(parent = "User", name = "username", child = 2))
    }

    @Test
    fun `single-object resolvers and wrapper fields default to 1 plus child`() {
        assertEquals(4, calculate(parent = "Query", name = "post", child = 3))
        assertEquals(3, calculate(parent = "Post", name = "author", child = 2))
        assertEquals(5, calculate(parent = "AdminUsersPage", name = "users", child = 4))
        assertEquals(3, calculate(parent = "PostEdge", name = "node", child = 2))
    }

    @Test
    fun `unbounded list resolvers multiply child by 10`() {
        assertEquals(31, calculate(parent = "Query", name = "posts", child = 3))
        assertEquals(21, calculate(parent = "Query", name = "myPosts", child = 2))
        assertEquals(41, calculate(parent = "Post", name = "comments", child = 4))
        assertEquals(31, calculate(parent = "Post", name = "likes", child = 3))
        assertEquals(21, calculate(parent = "User", name = "posts", child = 2))
    }

    @Test
    fun `paginated postsConnection multiplies child by first arg`() {
        assertEquals(151, calculate(parent = "Query", name = "postsConnection", child = 3, args = mapOf("first" to 50)))
        assertEquals(11, calculate(parent = "Query", name = "postsConnection", child = 2, args = mapOf("first" to 5)))
    }

    @Test
    fun `paginated postsConnection defaults to 10 when first is missing`() {
        assertEquals(31, calculate(parent = "Query", name = "postsConnection", child = 3, args = emptyMap()))
    }

    @Test
    fun `paginated admin fields multiply child by limit arg`() {
        assertEquals(41, calculate(parent = "AdminQueries", name = "users", child = 4, args = mapOf("limit" to 10)))
        assertEquals(11, calculate(parent = "AdminQueries", name = "posts", child = 2, args = mapOf("limit" to 5)))
        assertEquals(81, calculate(parent = "AdminQueries", name = "comments", child = 4, args = mapOf("limit" to 20)))
    }

    @Test
    fun `paginated admin fields default to 10 when limit is missing`() {
        assertEquals(41, calculate(parent = "AdminQueries", name = "users", child = 4, args = emptyMap()))
    }

    @Test
    fun `Post comments and likes are list resolvers but admin wrapper comments are not`() {
        // Post.comments → unbounded list resolver, multiplier 10
        assertEquals(21, calculate(parent = "Post", name = "comments", child = 2))
        // AdminCommentsPage.comments → wrapper field, no multiplier (parent's pagination already applied)
        assertEquals(3, calculate(parent = "AdminCommentsPage", name = "comments", child = 2))
    }

    private fun calculate(
        parent: String,
        name: String,
        child: Int,
        args: Map<String, Any?> = emptyMap(),
    ): Int {
        val field = mockk<Field>(relaxed = true) {
            every { this@mockk.name } returns name
        }
        val parentType = mockk<GraphQLObjectType>(relaxed = true) {
            every { this@mockk.name } returns parent
        }
        val env = mockk<FieldComplexityEnvironment>(relaxed = true) {
            every { this@mockk.field } returns field
            every { this@mockk.parentType } returns parentType
            every { arguments } returns args
        }
        return calculator.calculate(env, child)
    }
}
