package org.tuchscherer.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.tuchscherer.database.PostStatus
import java.util.UUID

/**
 * The draft visibility rule, stated as a truth table.
 *
 * This is the predicate every single-post read path shares, so these cases are the spec for
 * "a draft must not leak". They are cheap and mock-free on purpose — the rule should be
 * readable at a glance rather than buried in resolver plumbing.
 */
class PostVisibilityTest {

    private val author = UUID.randomUUID()
    private val otherUser = UUID.randomUUID()

    // --- published posts are readable by anyone ---

    @Test
    fun `a published post is readable when signed out`() {
        assertThat(PostVisibility.canView(PostStatus.PUBLISHED, author, viewerId = null)).isTrue()
    }

    @Test
    fun `a published post is readable by another user`() {
        assertThat(PostVisibility.canView(PostStatus.PUBLISHED, author, viewerId = otherUser)).isTrue()
    }

    @Test
    fun `a published post is readable by its author`() {
        assertThat(PostVisibility.canView(PostStatus.PUBLISHED, author, viewerId = author)).isTrue()
    }

    // --- drafts are readable only by their author and admins ---

    @Test
    fun `a draft is hidden when signed out`() {
        assertThat(PostVisibility.canView(PostStatus.DRAFT, author, viewerId = null)).isFalse()
    }

    @Test
    fun `a draft is hidden from another user`() {
        assertThat(PostVisibility.canView(PostStatus.DRAFT, author, viewerId = otherUser)).isFalse()
    }

    @Test
    fun `a draft is readable by its author`() {
        assertThat(PostVisibility.canView(PostStatus.DRAFT, author, viewerId = author)).isTrue()
    }

    @Test
    fun `a draft is readable by an admin who is not the author`() {
        assertThat(
            PostVisibility.canView(PostStatus.DRAFT, author, viewerId = otherUser, viewerIsAdmin = true)
        ).isTrue()
    }

    @Test
    fun `admin rights do not matter for a signed-out viewer`() {
        // Defensive: an anonymous request can never be admin, so this must not become a hole
        // if a caller ever passes the flag without a viewer id.
        assertThat(
            PostVisibility.canView(PostStatus.DRAFT, author, viewerId = null, viewerIsAdmin = true)
        ).isFalse()
    }

    // --- only the author may publish or unpublish ---

    @Test
    fun `the author may change publication state`() {
        assertThat(PostVisibility.canChangeStatus(author, viewerId = author)).isTrue()
    }

    @Test
    fun `another user may not change publication state`() {
        assertThat(PostVisibility.canChangeStatus(author, viewerId = otherUser)).isFalse()
    }

    @Test
    fun `a signed-out viewer may not change publication state`() {
        assertThat(PostVisibility.canChangeStatus(author, viewerId = null)).isFalse()
    }
}
