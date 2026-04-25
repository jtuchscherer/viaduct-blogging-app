import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const PAGE_SIZE = 10;

const ADMIN_COMMENTS = gql`
  query AdminComments($limit: Int!, $offset: Int!) {
    admin {
      comments(limit: $limit, offset: $offset) {
        totalCount
        comments {
          id
          content
          author {
            id
            username
          }
          post {
            id
            title
          }
          createdAt
        }
      }
    }
  }
`;

const ADMIN_DELETE_COMMENT = gql`
  mutation AdminDeleteComment($id: ID!) {
    admin {
      deleteComment(id: $id)
    }
  }
`;

interface Comment {
  id: string;
  content: string;
  author: {
    id: string;
    username: string;
  };
  post: {
    id: string;
    title: string;
  };
  createdAt: string;
}

interface AdminCommentsData {
  admin: { comments: { totalCount: number; comments: Comment[] } };
}

export default function AdminComments() {
  const [offset, setOffset] = useState(0);
  const { data, loading, error, refetch } = useQuery<AdminCommentsData>(ADMIN_COMMENTS, {
    variables: { limit: PAGE_SIZE, offset },
  });
  const [deleteComment] = useMutation(ADMIN_DELETE_COMMENT);
  const [commentToDelete, setCommentToDelete] = useState<Comment | null>(null);

  const confirmDelete = async () => {
    if (!commentToDelete) return;
    try {
      await deleteComment({ variables: { id: commentToDelete.id } });
      setCommentToDelete(null);
      refetch();
    } catch (err) {
      console.error('Failed to delete comment:', err);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const comments: Comment[] = data?.admin?.comments.comments ?? [];
  const totalCount = data?.admin?.comments.totalCount ?? 0;
  const currentPage = Math.floor(offset / PAGE_SIZE) + 1;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);
  const rangeStart = totalCount === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + PAGE_SIZE, totalCount);

  return (
    <div>
      <div className="admin-header">
        <h1>Comments</h1>
      </div>

      <div className="admin-table">
        <table>
          <thead>
            <tr>
              <th>Content</th>
              <th>Author</th>
              <th>Post</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {comments.map((comment) => (
              <tr key={comment.id} data-testid="admin-table-row">
                <td className="truncate">{comment.content}</td>
                <td>{comment.author.username}</td>
                <td className="truncate">
                  <Link to={`/post/${comment.post.id}`}>{comment.post.title}</Link>
                </td>
                <td>{new Date(comment.createdAt).toLocaleDateString()}</td>
                <td>
                  <div className="admin-actions">
                    <button className="btn-delete" onClick={() => setCommentToDelete(comment)}>
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="admin-pagination">
        <span data-testid="admin-page-info">
          {totalCount === 0 ? 'No comments' : `Showing ${rangeStart}–${rangeEnd} of ${totalCount}`}
        </span>
        <div className="admin-pagination-controls">
          <button
            data-testid="btn-prev-page"
            className="btn-page"
            onClick={() => { setOffset(offset - PAGE_SIZE); refetch(); }}
            disabled={offset === 0}
          >
            Previous
          </button>
          <span className="admin-page-number">Page {currentPage} of {totalPages || 1}</span>
          <button
            data-testid="btn-next-page"
            className="btn-page"
            onClick={() => { setOffset(offset + PAGE_SIZE); refetch(); }}
            disabled={offset + PAGE_SIZE >= totalCount}
          >
            Next
          </button>
        </div>
      </div>

      {commentToDelete && (
        <div className="confirm-modal">
          <div className="confirm-modal-content">
            <h3>Delete Comment</h3>
            <p>Are you sure you want to delete this comment?</p>
            <blockquote
              style={{
                background: '#f5f5f5',
                padding: '12px',
                borderRadius: '8px',
                margin: '16px 0',
                borderLeft: '3px solid #666',
              }}
            >
              "{commentToDelete.content}"
            </blockquote>
            <div className="confirm-modal-actions">
              <button className="btn-cancel" onClick={() => setCommentToDelete(null)}>
                Cancel
              </button>
              <button className="btn-confirm-delete" onClick={confirmDelete}>
                Delete Comment
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
