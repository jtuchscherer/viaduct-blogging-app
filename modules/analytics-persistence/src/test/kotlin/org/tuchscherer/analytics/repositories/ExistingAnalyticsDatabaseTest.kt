package org.tuchscherer.analytics.repositories

import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.util.UUID

class ExistingAnalyticsDatabaseTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `Java repository contract preserves an existing SQLite analytics table`() {
        val url = "jdbc:sqlite:${directory.resolve("existing.db")}"
        val postId = UUID.randomUUID()
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE post_views (post_id VARCHAR(36) NOT NULL PRIMARY KEY, view_count BIGINT NOT NULL DEFAULT 0)"
                )
            }
            connection.prepareStatement("INSERT INTO post_views (post_id, view_count) VALUES (?, ?)").use { statement ->
                statement.setString(1, postId.toString())
                statement.setLong(2, 12L)
                statement.executeUpdate()
            }
        }

        Database.connect(url, driver = "org.sqlite.JDBC")
        val repository: PostViewRepository = ExposedPostViewRepository()

        assertEquals(mapOf(postId to 12L), repository.bulkGetViewCounts(listOf(postId)))
        repository.incrementViewCount(postId)
        assertEquals(13L, repository.getTotalViews())
        assertEquals(listOf(postId), repository.getMostViewed(10))
    }
}
