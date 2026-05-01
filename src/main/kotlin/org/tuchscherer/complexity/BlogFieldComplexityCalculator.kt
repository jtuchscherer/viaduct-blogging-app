package org.tuchscherer.complexity

import graphql.analysis.FieldComplexityCalculator
import graphql.analysis.FieldComplexityEnvironment

/**
 * Per-field cost weights for query complexity analysis. Plugged into graphql-java's
 * MaxQueryComplexityInstrumentation, which aborts execution before any resolver runs
 * if the total query cost exceeds the configured threshold.
 *
 * Cost rules:
 *   - Paginated connections multiply child cost by `first` / `limit` (default 10 if absent).
 *   - Unbounded list resolvers multiply child cost by [DEFAULT_LIST_MULTIPLIER].
 *   - Everything else (scalars, single-object resolvers, wrapper fields like
 *     AdminUsersPage.users or PostEdge.node) costs 1 + child.
 */
class BlogFieldComplexityCalculator : FieldComplexityCalculator {

    override fun calculate(env: FieldComplexityEnvironment, childComplexity: Int): Int {
        val name = env.field.name
        val parent = env.parentType.name

        if (isPaginatedConnection(parent, name)) {
            val mult = paginationMultiplier(env.arguments)
            return 1 + mult * childComplexity
        }

        if (isUnboundedListResolver(parent, name)) {
            return 1 + DEFAULT_LIST_MULTIPLIER * childComplexity
        }

        return 1 + childComplexity
    }

    private fun isPaginatedConnection(parent: String, name: String): Boolean =
        (parent == "Query" && name == "postsConnection") ||
            (parent == "AdminQueries" && name in PAGINATED_ADMIN_FIELDS)

    private fun isUnboundedListResolver(parent: String, name: String): Boolean =
        when (parent) {
            "Query" -> name in UNBOUNDED_QUERY_LISTS
            "BlogPost" -> name in UNBOUNDED_POST_LISTS
            "User" -> name == "posts"
            else -> false
        }

    private fun paginationMultiplier(args: Map<String, Any?>): Int {
        val first = args["first"] as? Int
        val limit = args["limit"] as? Int
        return first ?: limit ?: DEFAULT_PAGINATION_SIZE
    }

    companion object {
        const val DEFAULT_LIST_MULTIPLIER = 10
        const val DEFAULT_PAGINATION_SIZE = 10

        private val PAGINATED_ADMIN_FIELDS = setOf("users", "posts", "comments")
        private val UNBOUNDED_QUERY_LISTS = setOf("posts", "myPosts", "postComments", "trending")
        private val UNBOUNDED_POST_LISTS = setOf("comments", "likes")
    }
}
