package org.tuchscherer.checkedlist

import org.tuchscherer.checkedlist.port.CommentView
import org.tuchscherer.checkedlist.port.LikeView
import org.tuchscherer.checkedlist.port.PostSocialPort
import org.tuchscherer.database.repositories.CommentRepository
import org.tuchscherer.database.repositories.LikeRepository
import java.util.UUID

/**
 * Root-project implementation of [PostSocialPort]. Delegates to the existing
 * [CommentRepository] and [LikeRepository] — the checkedlist module cannot import
 * root-project types, so this bridge lives in the root project and is registered via Koin.
 */
class ViaductPostSocialAccess(
    private val commentRepository: CommentRepository,
    private val likeRepository: LikeRepository,
) : PostSocialPort {

    override fun getCommentsForPost(postId: UUID): List<CommentView> =
        commentRepository.findByPostId(postId).map { comment ->
            CommentView(
                id = comment.id.value,
                content = comment.content,
                createdAt = comment.createdAt.toString(),
            )
        }

    override fun getCommentCountForPost(postId: UUID): Long =
        commentRepository.countByPostId(postId)

    override fun getLikesForPost(postId: UUID): List<LikeView> =
        likeRepository.findByPostId(postId).map { like ->
            LikeView(
                id = like.id.value,
                createdAt = like.createdAt.toString(),
            )
        }

    override fun getLikeCountForPost(postId: UUID): Long =
        likeRepository.countByPostId(postId)

    override fun isLikedByUser(postId: UUID, userId: UUID): Boolean =
        likeRepository.existsByPostAndUser(postId, userId)
}
