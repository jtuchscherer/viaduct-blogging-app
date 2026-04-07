import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const ADMIN_COMMENTS = gql`
  query AdminComments {
    adminComments {
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
`;

const ADMIN_DELETE_COMMENT = gql`
  mutation AdminDeleteComment($id: ID!) {
    adminDeleteComment(id: $id)
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
  adminComments: Comment[];
}

export default function AdminComments() {
  const { data, loading, error, refetch } = useQuery<AdminCommentsData>(ADMIN_COMMENTS);
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

  const comments: Comment[] = data?.adminComments ?? [];

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
              <tr key={comment.id}>
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
