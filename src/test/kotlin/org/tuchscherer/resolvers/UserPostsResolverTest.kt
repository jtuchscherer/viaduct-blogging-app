package org.tuchscherer.resolvers

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.tuchscherer.auth.RequestContext
import org.tuchscherer.database.Post
import org.tuchscherer.database.User
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.viadapp.resolvers.UserPostsResolver
import viaduct.api.grts.Query
import viaduct.api.testing.ResolverTestBase
import viaduct.api.types.Arguments.NoArguments
import java.time.LocalDateTime
import java.util.UUID
import viaduct.api.grts.User as ViaductUser

class UserPostsResolverTest : ResolverTestBase() {
    private val time = LocalDateTime.of(2026, 10, 21, 12, 0)
    private fun user(admin: Boolean = false) =
        User(UUID.randomUUID(), "viewer", "viewer@example.com", "Viewer", "hash", "salt", admin, time)

    @Test
    fun `user posts exposes drafts only to their author and admins`() = runBlocking<Unit> {
        val author = user()
        val stranger = user()
        val admin = user(admin = true)
        val published = Post(
            UUID.randomUUID(), "Public", "Body", author.id, "BLOG_POST", "PUBLISHED", time, time, time,
        )
        val draft = published.copy(id = UUID.randomUUID(), title = "Secret", status = "DRAFT", publishedAt = null)
        val posts = mockk<PostRepository>()
        every { posts.findByAuthorId(author.id) } returns listOf(published, draft)
        val resolver = UserPostsResolver(posts)

        for (viewer in listOf(null, stranger, author, admin)) {
            val result = runFieldResolver(resolver) {
                objectValue = ViaductUser.Builder(context)
                    .id(globalIDFor(ViaductUser.Reflection, author.id.toString())).build()
                queryValue = Query.Builder(context).build()
                arguments = NoArguments
                requestContext = RequestContext(viewer)
            }
            val expected = if (viewer == author || viewer == admin) listOf("Public", "Secret") else listOf("Public")
            assertEquals(expected, result.map { it.getTitleOrThrow() })
        }
    }
}
