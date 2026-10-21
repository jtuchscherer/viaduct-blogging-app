package org.tuchscherer.admin

import org.tuchscherer.auth.NotFoundException
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import org.tuchscherer.database.repositories.PostRepository
import org.tuchscherer.database.repositories.UserRepository
import org.tuchscherer.shared.TransactionRunner
import java.util.UUID

data class UserDeletionResult(val success: Boolean, val posts: Int, val comments: Int, val likes: Int)

class AdminUserDeletionService(
    private val users: UserRepository,
    private val posts: PostRepository,
    private val comments: CommentRepository,
    private val likes: LikeRepository,
    private val transactions: TransactionRunner,
) {
    fun delete(userId: UUID): UserDeletionResult = transactions.run {
        users.findById(userId) ?: throw NotFoundException("User not found")
        val likesDeleted = likes.deleteByUserId(userId)
        val commentsDeleted = comments.deleteByUserId(userId)
        val postsDeleted = posts.deleteByAuthorId(userId)
        check(users.delete(userId)) { "User disappeared during deletion" }
        UserDeletionResult(true, postsDeleted, commentsDeleted, likesDeleted)
    }
}
