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

        if (isChecklistItemList(parent, name)) {
            return 1 + CHECKLIST_ITEM_MULTIPLIER * childComplexity
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
            "CheckedListPost" -> name in UNBOUNDED_POST_LISTS
            "User" -> name == "posts"
            else -> false
        }

    /**
     * Returns true for list fields that use [CHECKLIST_ITEM_MULTIPLIER] instead of
     * [DEFAULT_LIST_MULTIPLIER]. Checklist items are lightweight scalar containers
     * (id, text, checked, position) so a lower multiplier reflects typical usage.
     */
    private fun isChecklistItemList(parent: String, name: String): Boolean =
        parent == "CheckedListPost" && name == "items"

    private fun paginationMultiplier(args: Map<String, Any?>): Int {
        val first = args["first"] as? Int
        val limit = args["limit"] as? Int
        return first ?: limit ?: DEFAULT_PAGINATION_SIZE
    }

    companion object {
        const val DEFAULT_LIST_MULTIPLIER = 10
        const val DEFAULT_PAGINATION_SIZE = 10

        /**
         * Lower multiplier used for [CheckedListPost.items]. Checklist items are lightweight
         * scalars (id, text, checked, position) — a factor of 5 caps a full-item query at
         * roughly 10 posts × (title + 5 × 4 items) = 210, safely under [MAX_COMPLEXITY].
         */
        const val CHECKLIST_ITEM_MULTIPLIER = 5

        private val PAGINATED_ADMIN_FIELDS = setOf("users", "posts", "comments")
        private val UNBOUNDED_QUERY_LISTS = setOf("posts", "myPosts", "postComments", "trending", "checkedListPosts")
        private val UNBOUNDED_POST_LISTS = setOf("comments", "likes")
    }
}
