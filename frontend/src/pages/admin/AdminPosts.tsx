import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const ADMIN_POSTS = gql`
  query AdminPosts {
    admin {
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
`;

const ADMIN_DELETE_POST = gql`
  mutation AdminDeletePost($id: ID!) {
    adminDeletePost(id: $id)
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
  admin: { posts: Post[] };
}

export default function AdminPosts() {
  const { data, loading, error, refetch } = useQuery<AdminPostsData>(ADMIN_POSTS);
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

  const posts: Post[] = data?.admin?.posts ?? [];

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
              <tr key={post.id}>
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
