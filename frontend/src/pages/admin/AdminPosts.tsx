import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const PAGE_SIZE = 10;

const ADMIN_POSTS = gql`
  query AdminPosts($limit: Int!, $offset: Int!) {
    admin {
      posts(limit: $limit, offset: $offset) {
        totalCount
        posts {
          id
          title
          author {
            id
            username
          }
          createdAt
          updatedAt
        }
      }
    }
  }
`;

const ADMIN_DELETE_POST = gql`
  mutation AdminDeletePost($id: ID!) {
    admin {
      deletePost(id: $id)
    }
  }
`;

interface Post {
  id: string;
  title: string;
  author: {
    id: string;
    username: string;
  };
  createdAt: string;
  updatedAt: string;
}

interface AdminPostsData {
  admin: { posts: { totalCount: number; posts: Post[] } };
}

export default function AdminPosts() {
  const [offset, setOffset] = useState(0);
  const { data, loading, error, refetch } = useQuery<AdminPostsData>(ADMIN_POSTS, {
    variables: { limit: PAGE_SIZE, offset },
  });
  const [deletePost] = useMutation(ADMIN_DELETE_POST);
  const [postToDelete, setPostToDelete] = useState<Post | null>(null);

  const confirmDelete = async () => {
    if (!postToDelete) return;
    try {
      await deletePost({ variables: { id: postToDelete.id } });
      setPostToDelete(null);
      refetch();
    } catch (err) {
      console.error('Failed to delete post:', err);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const posts: Post[] = data?.admin?.posts.posts ?? [];
  const totalCount = data?.admin?.posts.totalCount ?? 0;
  const currentPage = Math.floor(offset / PAGE_SIZE) + 1;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);
  const rangeStart = totalCount === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + PAGE_SIZE, totalCount);

  return (
    <div>
      <div className="admin-header">
        <h1>Posts</h1>
      </div>

      <div className="admin-table">
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Author</th>
              <th>Created</th>
              <th>Updated</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {posts.map((post) => (
              <tr key={post.id} data-testid="admin-table-row">
                <td className="truncate">{post.title}</td>
                <td>{post.author.username}</td>
                <td>{new Date(post.createdAt).toLocaleDateString()}</td>
                <td>{new Date(post.updatedAt).toLocaleDateString()}</td>
                <td>
                  <div className="admin-actions">
                    <Link to={`/post/${post.id}`} className="btn-view">
                      View
                    </Link>
                    <Link to={`/admin/posts/${post.id}`} className="btn-edit">
                      Edit
                    </Link>
                    <button className="btn-delete" onClick={() => setPostToDelete(post)}>
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
          {totalCount === 0 ? 'No posts' : `Showing ${rangeStart}–${rangeEnd} of ${totalCount}`}
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

      {postToDelete && (
        <div className="confirm-modal">
          <div className="confirm-modal-content">
            <h3>Delete Post</h3>
            <p>
              Are you sure you want to delete <strong>"{postToDelete.title}"</strong>?
            </p>
            <p className="warning">This will also delete all comments and likes on this post.</p>
            <div className="confirm-modal-actions">
              <button className="btn-cancel" onClick={() => setPostToDelete(null)}>
                Cancel
              </button>
              <button className="btn-confirm-delete" onClick={confirmDelete}>
                Delete Post
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
