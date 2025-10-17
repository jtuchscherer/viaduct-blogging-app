import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const GET_MY_POSTS = gql`
  query GetMyPosts {
    myPosts {
      id
      title
      content
      createdAt
      likeCount
    }
  }
`;

interface Post {
  id: string;
  title: string;
  content: string;
  createdAt: string;
  likeCount: number;
}

interface MyPostsData {
  myPosts: Post[];
}

export default function MyPostsPage() {
  const { user } = useAuth();
  const { loading, error, data } = useQuery<MyPostsData>(GET_MY_POSTS);

  if (loading) {
    return (
      <div className="container">
        <h1>My Posts</h1>
        <p>Loading your posts...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <h1>My Posts</h1>
        <div className="error-message">Error loading posts: {error.message}</div>
      </div>
    );
  }

  return (
    <div className="container">
      <div className="page-header">
        <h1>My Posts</h1>
        <Link to="/create" className="btn-primary">
          Create New Post
        </Link>
      </div>

      {data?.myPosts.length === 0 ? (
        <div className="empty-state">
          <p>You haven't created any posts yet.</p>
          <Link to="/create" className="btn-primary">
            Create your first post
          </Link>
        </div>
      ) : (
        <div className="posts-list">
          {data?.myPosts.map((post) => (
            <article key={post.id} className="post-card">
              <h2>
                <Link to={`/post/${post.id}`}>{post.title}</Link>
              </h2>
              <div className="post-meta">
                <span className="post-author">by {user?.name}</span>
                <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
              </div>
              <p className="post-excerpt">
                {post.content.length > 200
                  ? `${post.content.substring(0, 200)}...`
                  : post.content}
              </p>
              <div className="post-footer">
                <span className="like-count">❤️ {post.likeCount}</span>
                <div className="post-actions-inline">
                  <Link to={`/post/${post.id}`} className="read-more">
                    View
                  </Link>
                  <Link to={`/edit/${post.id}`} className="btn-edit-small">
                    Edit
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
