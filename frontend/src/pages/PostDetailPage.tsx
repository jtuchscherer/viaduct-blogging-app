import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect, type FormEvent } from 'react';
import { useAuth } from '../contexts/AuthContext';
import DOMPurify from 'dompurify';
import type { Post } from '../types';

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
      isLikedByMe
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
      createdAt
    }
  }
`;

const UNLIKE_POST = gql`
  mutation UnlikePost($postId: ID!) {
    unlikePost(postId: $postId)
  }
`;

const ADD_COMMENT = gql`
  mutation AddComment($input: CreateCommentInput!) {
    createComment(input: $input) {
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

/** Safely render post content — handles both legacy plain-text and rich-text HTML. */
function renderContent(content: string): string {
  if (!content) return '';
  const trimmed = content.trim();
  if (trimmed.startsWith('<')) {
    // Rich-text HTML from Lexical editor — sanitize before rendering
    return DOMPurify.sanitize(trimmed);
  }
  // Legacy plain-text — convert newlines to paragraphs
  return trimmed
    .split('\n')
    .map((line) => {
      const escaped = line
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
      return `<p>${escaped}</p>`;
    })
    .join('');
}

interface PostData {
  post: Post;
}

export default function PostDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [commentContent, setCommentContent] = useState('');
  const [commentError, setCommentError] = useState('');

  const { loading, error, data, refetch } = useQuery<PostData>(GET_POST, {
    variables: { id },
  });

  // Scroll to hash anchor after content loads
  useEffect(() => {
    if (!loading && data && location.hash) {
      const element = document.getElementById(location.hash.slice(1));
      if (element) {
        element.scrollIntoView({ behavior: 'smooth' });
      }
    }
  }, [loading, data, location.hash]);

  const [likePost] = useMutation(LIKE_POST, {
    onCompleted: () => refetch(),
  });

  const [unlikePost] = useMutation(UNLIKE_POST, {
    onCompleted: () => refetch(),
  });

  const [addComment] = useMutation(ADD_COMMENT, {
    onCompleted: () => {
      setCommentContent('');
      setCommentError('');
      refetch();
    },
    onError: (err) => {
      setCommentError(err.message);
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

    if (data?.post.isLikedByMe) {
      await unlikePost({ variables: { postId: id } });
    } else {
      await likePost({ variables: { postId: id } });
    }
  };

  const handleCommentSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!commentContent.trim()) return;

    await addComment({
      variables: {
        input: {
          postId: id,
          content: commentContent
        }
      },
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
  // Compare by username since auth returns raw UUIDs but GraphQL returns GlobalIDs
  const isAuthor = user?.username === post.author.username;

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

        <div
          className="post-content"
          dangerouslySetInnerHTML={{ __html: renderContent(post.content) }}
        />

        <div className="post-actions">
          <button onClick={handleLikeToggle} className={post.isLikedByMe ? 'liked' : ''}>
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
          <h2 id="comments-section">Comments ({post.comments?.length ?? 0})</h2>

          {isAuthenticated && (
            <form onSubmit={handleCommentSubmit} className="comment-form">
              {commentError && <div className="error-message">{commentError}</div>}
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
            {(post.comments ?? []).map((comment) => (
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
