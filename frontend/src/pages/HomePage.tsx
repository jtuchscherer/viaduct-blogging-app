import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';

const GET_POSTS = gql`
  query GetPosts {
    posts {
      id
      title
      content
      author {
        id
        name
        username
      }
      createdAt
      likeCount
    }
  }
`;

interface Author {
  id: string;
  name: string;
  username: string;
}

interface Post {
  id: string;
  title: string;
  content: string;
  author: Author;
  createdAt: string;
  likeCount: number;
}

interface PostsData {
  posts: Post[];
}

export default function HomePage() {
  const { loading, error, data } = useQuery<PostsData>(GET_POSTS);

  if (loading) {
    return (
      <div className="container">
        <h1>Blog Posts</h1>
        <p>Loading posts...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <h1>Blog Posts</h1>
        <div className="error-message">Error loading posts: {error.message}</div>
      </div>
    );
  }

  return (
    <div className="container">
      <h1>Blog Posts</h1>

      {data?.posts.length === 0 ? (
        <p className="empty-state">No posts yet. Be the first to create one!</p>
      ) : (
        <div className="posts-list">
          {data?.posts.map((post) => (
            <article key={post.id} className="post-card">
              <h2>
                <Link to={`/post/${post.id}`}>{post.title}</Link>
              </h2>
              <div className="post-meta">
                <span className="post-author">by {post.author.name}</span>
                <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
              </div>
              <p className="post-excerpt">
                {post.content.length > 200
                  ? `${post.content.substring(0, 200)}...`
                  : post.content}
              </p>
              <div className="post-footer">
                <span className="like-count">❤️ {post.likeCount}</span>
                <Link to={`/post/${post.id}`} className="read-more">
                  Read more →
                </Link>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
