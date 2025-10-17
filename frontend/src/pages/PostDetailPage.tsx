import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useState, type FormEvent } from 'react';
import { useAuth } from '../contexts/AuthContext';

const GET_POST = gql`
  query GetPost($id: ID!) {
    post(id: $id) {
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
      isLikedByCurrentUser
      comments {
        id
        content
        author {
          id
          name
          username
        }
        createdAt
      }
    }
  }
`;

const LIKE_POST = gql`
  mutation LikePost($postId: ID!) {
    likePost(postId: $postId) {
      id
      likeCount
      isLikedByCurrentUser
    }
  }
`;

const UNLIKE_POST = gql`
  mutation UnlikePost($postId: ID!) {
    unlikePost(postId: $postId) {
      id
      likeCount
      isLikedByCurrentUser
    }
  }
`;

const ADD_COMMENT = gql`
  mutation AddComment($postId: ID!, $content: String!) {
    addComment(postId: $postId, content: $content) {
      id
      content
      author {
        id
        name
        username
      }
      createdAt
    }
  }
`;

const DELETE_POST = gql`
  mutation DeletePost($id: ID!) {
    deletePost(id: $id)
  }
`;

interface Author {
  id: string;
  name: string;
  username: string;
}

interface Comment {
  id: string;
  content: string;
  author: Author;
  createdAt: string;
}

interface Post {
  id: string;
  title: string;
  content: string;
  author: Author;
  createdAt: string;
  likeCount: number;
  isLikedByCurrentUser: boolean;
  comments: Comment[];
}

interface PostData {
  post: Post;
}

export default function PostDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [commentContent, setCommentContent] = useState('');

  const { loading, error, data, refetch } = useQuery<PostData>(GET_POST, {
    variables: { id },
  });

  const [likePost] = useMutation(LIKE_POST, {
    onCompleted: () => refetch(),
  });

  const [unlikePost] = useMutation(UNLIKE_POST, {
    onCompleted: () => refetch(),
  });

  const [addComment] = useMutation(ADD_COMMENT, {
    onCompleted: () => {
      setCommentContent('');
      refetch();
    },
  });

  const [deletePost] = useMutation(DELETE_POST, {
    onCompleted: () => {
      navigate('/');
    },
  });

  const handleLikeToggle = async () => {
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }

    if (data?.post.isLikedByCurrentUser) {
      await unlikePost({ variables: { postId: id } });
    } else {
      await likePost({ variables: { postId: id } });
    }
  };

  const handleCommentSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!commentContent.trim()) return;

    await addComment({
      variables: { postId: id, content: commentContent },
    });
  };

  const handleDelete = async () => {
    if (window.confirm('Are you sure you want to delete this post?')) {
      await deletePost({ variables: { id } });
    }
  };

  if (loading) return <div className="container"><p>Loading post...</p></div>;
  if (error) return <div className="container"><div className="error-message">Error: {error.message}</div></div>;
  if (!data?.post) return <div className="container"><p>Post not found</p></div>;

  const post = data.post;
  const isAuthor = user?.id === post.author.id;

  return (
    <div className="container">
      <article className="post-detail">
        <div className="post-header">
          <h1>{post.title}</h1>
          <div className="post-meta">
            <span>by {post.author.name}</span>
            <span>{new Date(post.createdAt).toLocaleDateString()}</span>
          </div>
        </div>

        <div className="post-content">
          {post.content.split('\n').map((paragraph, idx) => (
            <p key={idx}>{paragraph}</p>
          ))}
        </div>

        <div className="post-actions">
          <button onClick={handleLikeToggle} className={post.isLikedByCurrentUser ? 'liked' : ''}>
            ❤️ {post.likeCount}
          </button>

          {isAuthor && (
            <>
              <Link to={`/edit/${post.id}`} className="btn-edit">
                Edit
              </Link>
              <button onClick={handleDelete} className="btn-delete">
                Delete
              </button>
            </>
          )}
        </div>

        <section className="comments-section">
          <h2>Comments ({post.comments.length})</h2>

          {isAuthenticated && (
            <form onSubmit={handleCommentSubmit} className="comment-form">
              <textarea
                value={commentContent}
                onChange={(e) => setCommentContent(e.target.value)}
                placeholder="Add a comment..."
                rows={3}
                required
              />
              <button type="submit" className="btn-primary">
                Post Comment
              </button>
            </form>
          )}

          {!isAuthenticated && (
            <p className="login-prompt">
              <Link to="/login">Login</Link> to comment
            </p>
          )}

          <div className="comments-list">
            {post.comments.map((comment) => (
              <div key={comment.id} className="comment">
                <div className="comment-header">
                  <strong>{comment.author.name}</strong>
                  <span className="comment-date">
                    {new Date(comment.createdAt).toLocaleDateString()}
                  </span>
                </div>
                <p className="comment-content">{comment.content}</p>
              </div>
            ))}
          </div>
        </section>
      </article>
    </div>
  );
}
