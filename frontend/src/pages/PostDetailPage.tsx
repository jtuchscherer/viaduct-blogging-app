import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, Link, useNavigate, useLocation } from 'react-router-dom';
import { useState, useEffect, type FormEvent } from 'react';
import { useAuth } from '../contexts/AuthContext';
import DOMPurify from 'dompurify';
import { formatReadTime } from '../utils/content';
import type { CheckedListItem } from '../types';
import { useAIHealth } from '../hooks/useAIHealth';
import { useSuggestItem } from '../hooks/useSuggestItem';

// ── GraphQL documents ─────────────────────────────────────────────────────────

/**
 * Uses node(id) so the same route handles both BlogPost and CheckedListPost IDs.
 * __typename drives the rendering branch below.
 */
const GET_NODE = gql`
  query GetNode($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
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
        viewCount
        readTimeMinutes
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
      ... on CheckedListPost {
        id
        title
        description
        author {
          id
          name
          username
        }
        createdAt
        likeCount
        isLikedByMe
        viewCount
        readTimeMinutes
        items {
          id
          text
          checked
          position
          createdAt
        }
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
  }
`;

const RECORD_POST_VIEW = gql`
  mutation RecordPostView($postId: ID!) {
    recordPostView(postId: $postId)
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

const DELETE_CHECKLIST_POST = gql`
  mutation DeleteCheckedListPost($id: ID!) {
    deleteCheckedListPost(id: $id)
  }
`;

const TOGGLE_ITEM = gql`
  mutation ToggleCheckedListItem($id: ID!) {
    toggleCheckedListItem(id: $id) {
      id
      checked
    }
  }
`;

const ADD_ITEM = gql`
  mutation AddCheckedListItem($input: AddCheckedListItemInput!) {
    addCheckedListItem(input: $input) {
      id
      text
      checked
      position
      createdAt
    }
  }
`;

const DELETE_ITEM = gql`
  mutation DeleteCheckedListItem($id: ID!) {
    deleteCheckedListItem(id: $id)
  }
`;

const UPDATE_ITEM = gql`
  mutation UpdateCheckedListItem($input: UpdateCheckedListItemInput!) {
    updateCheckedListItem(input: $input) {
      id
      text
      checked
      position
      createdAt
    }
  }
`;

// ── Shared types ──────────────────────────────────────────────────────────────

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

interface BlogPostData {
  __typename: 'BlogPost';
  id: string;
  title: string;
  content: string;
  author: Author;
  createdAt: string;
  likeCount: number;
  isLikedByMe: boolean;
  viewCount?: number;
  readTimeMinutes?: number;
  comments: Comment[];
}

interface CheckedListPostData {
  __typename: 'CheckedListPost';
  id: string;
  title: string;
  description: string;
  author: Author;
  createdAt: string;
  likeCount: number;
  isLikedByMe: boolean;
  viewCount?: number;
  readTimeMinutes?: number;
  items: CheckedListItem[];
  comments: Comment[];
}

type NodeData = BlogPostData | CheckedListPostData;

interface NodeQueryResult {
  node: NodeData | null;
}

// ── Hooks ─────────────────────────────────────────────────────────────────────

/** Shared like/unlike logic for any post type. */
function useLikeToggle(
  postId: string,
  isLikedByMe: boolean,
  isAuthenticated: boolean,
  refetch: () => void,
) {
  const navigate = useNavigate();
  const [likePost] = useMutation(LIKE_POST, { onCompleted: refetch });
  const [unlikePost] = useMutation(UNLIKE_POST, { onCompleted: refetch });

  const handleLikeToggle = async () => {
    if (!isAuthenticated) { navigate('/login'); return; }
    if (isLikedByMe) {
      await unlikePost({ variables: { postId } });
    } else {
      await likePost({ variables: { postId } });
    }
  };

  return { handleLikeToggle };
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/** Safely render post content — handles both legacy plain-text and rich-text HTML. */
function renderContent(content: string): string {
  if (!content) return '';
  const trimmed = content.trim();
  if (trimmed.startsWith('<')) {
    return DOMPurify.sanitize(trimmed);
  }
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

// ── Comments section (shared) ─────────────────────────────────────────────────

function CommentsSection({
  postId,
  comments,
  isAuthenticated,
  refetch,
}: {
  postId: string;
  comments: Comment[];
  isAuthenticated: boolean;
  refetch: () => void;
}) {
  const [commentContent, setCommentContent] = useState('');
  const [commentError, setCommentError] = useState('');

  const [addComment] = useMutation(ADD_COMMENT, {
    onCompleted: () => {
      setCommentContent('');
      setCommentError('');
      refetch();
    },
    onError: (err) => setCommentError(err.message),
  });

  const handleCommentSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!commentContent.trim()) return;
    await addComment({ variables: { input: { postId, content: commentContent } } });
  };

  return (
    <section className="comments-section">
      <h2 id="comments-section">Comments ({comments.length})</h2>

      {isAuthenticated ? (
        <form onSubmit={handleCommentSubmit} className="comment-form">
          {commentError && <div className="error-message">{commentError}</div>}
          <textarea
            value={commentContent}
            onChange={(e) => setCommentContent(e.target.value)}
            placeholder="Add a comment..."
            rows={3}
            required
          />
          <button type="submit" className="btn-primary">Post Comment</button>
        </form>
      ) : (
        <p className="login-prompt">
          <Link to="/login">Login</Link> to comment
        </p>
      )}

      <div className="comments-list">
        {comments.map((comment) => (
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
  );
}

// ── BlogPost detail ───────────────────────────────────────────────────────────

function BlogPostDetail({ post, refetch }: { post: BlogPostData; refetch: () => void }) {
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const isAuthor = user?.username === post.author.username;

  const { handleLikeToggle } = useLikeToggle(post.id, post.isLikedByMe, isAuthenticated, refetch);
  const [deletePost] = useMutation(DELETE_POST, { onCompleted: () => navigate('/') });

  const handleDelete = async () => {
    if (window.confirm('Are you sure you want to delete this post?')) {
      await deletePost({ variables: { id: post.id } });
    }
  };

  return (
    <article className="post-detail">
      <div className="post-header">
        <h1>{post.title}</h1>
        <div className="post-meta">
          <span>by {post.author.name}</span>
          <span>{new Date(post.createdAt).toLocaleDateString()}</span>
          {post.viewCount !== undefined && post.readTimeMinutes !== undefined && (
            <span className="post-analytics">
              👁 {post.viewCount} {post.viewCount === 1 ? 'view' : 'views'}
              {' · '}
              ⏱ {formatReadTime(post.readTimeMinutes)}
            </span>
          )}
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
            <Link to={`/edit/${post.id}`} className="btn-edit">Edit</Link>
            <button onClick={handleDelete} className="btn-delete">Delete</button>
          </>
        )}
      </div>

      <CommentsSection
        postId={post.id}
        comments={post.comments}
        isAuthenticated={isAuthenticated}
        refetch={refetch}
      />
    </article>
  );
}

// ── CheckedList detail ────────────────────────────────────────────────────────

function CheckedListDetail({ post, refetch }: { post: CheckedListPostData; refetch: () => void }) {
  const { user, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const isAuthor = user?.username === post.author.username;

  const [newItemText, setNewItemText] = useState('');
  const [addItemError, setAddItemError] = useState('');
  const [editingItemId, setEditingItemId] = useState<string | null>(null);
  const [editingItemText, setEditingItemText] = useState('');

  const aiHealth = useAIHealth();
  const { suggest, suggesting } = useSuggestItem();

  const { handleLikeToggle } = useLikeToggle(post.id, post.isLikedByMe, isAuthenticated, refetch);
  const [toggleItem] = useMutation(TOGGLE_ITEM, { onCompleted: refetch });
  const [addItem] = useMutation(ADD_ITEM, {
    onCompleted: () => { setNewItemText(''); setAddItemError(''); refetch(); },
    onError: (err) => setAddItemError(err.message),
  });
  const [deleteItem] = useMutation(DELETE_ITEM, { onCompleted: refetch });
  const [updateItem] = useMutation(UPDATE_ITEM, {
    onCompleted: () => { setEditingItemId(null); setEditingItemText(''); refetch(); },
  });
  const [deletePost] = useMutation(DELETE_CHECKLIST_POST, { onCompleted: () => navigate('/') });

  const handleAddItem = async (e: FormEvent) => {
    e.preventDefault();
    if (!newItemText.trim()) return;
    await addItem({ variables: { input: { postId: post.id, text: newItemText.trim() } } });
  };

  const handleSaveEdit = async (itemId: string) => {
    if (!editingItemText.trim()) return;
    await updateItem({ variables: { input: { id: itemId, text: editingItemText.trim() } } });
  };

  const handleDelete = async () => {
    if (window.confirm('Are you sure you want to delete this checklist?')) {
      await deletePost({ variables: { id: post.id } });
    }
  };

  const sortedItems = [...(post.items ?? [])].sort((a, b) => a.position - b.position);

  const handleSuggestItem = async () => {
    const suggested = await suggest(sortedItems.map((item) => item.text));
    if (suggested) setNewItemText(suggested);
  };

  return (
    <article className="post-detail post-detail--checklist">
      <div className="post-header">
        <div className="post-type-badge">☑ Checklist</div>
        <h1>{post.title}</h1>
        <div className="post-meta">
          <span>by {post.author.name}</span>
          <span>{new Date(post.createdAt).toLocaleDateString()}</span>
          {post.viewCount !== undefined && post.readTimeMinutes !== undefined && (
            <span className="post-analytics">
              👁 {post.viewCount} {post.viewCount === 1 ? 'view' : 'views'}
              {' · '}
              ⏱ {formatReadTime(post.readTimeMinutes)}
            </span>
          )}
        </div>
      </div>

      {post.description && (
        <p className="checklist-description">{post.description}</p>
      )}

      <div className="checklist-body">
        {sortedItems.length === 0 && (
          <p className="empty-state">No items yet.</p>
        )}
        <ul className="checklist-items">
          {sortedItems.map((item) => (
            <li
              key={item.id}
              className={`checklist-item${item.checked ? ' checklist-item--checked' : ''}`}
            >
              {editingItemId === item.id ? (
                <div className="checklist-item-edit">
                  <input
                    type="text"
                    value={editingItemText}
                    onChange={(e) => setEditingItemText(e.target.value)}
                    autoFocus
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleSaveEdit(item.id);
                      if (e.key === 'Escape') { setEditingItemId(null); setEditingItemText(''); }
                    }}
                  />
                  <button
                    type="button"
                    className="btn-save-item"
                    onClick={() => handleSaveEdit(item.id)}
                  >
                    Save
                  </button>
                  <button
                    type="button"
                    className="btn-cancel-edit"
                    onClick={() => { setEditingItemId(null); setEditingItemText(''); }}
                  >
                    Cancel
                  </button>
                </div>
              ) : (
                <div className="checklist-item-row">
                  {isAuthor ? (
                    <input
                      type="checkbox"
                      checked={item.checked}
                      onChange={() => toggleItem({ variables: { id: item.id } })}
                    />
                  ) : (
                    <input type="checkbox" checked={item.checked} disabled />
                  )}
                  <span className="checklist-item-text">{item.text}</span>
                  {isAuthor && (
                    <div className="checklist-item-actions">
                      <button
                        type="button"
                        className="btn-edit-item"
                        onClick={() => { setEditingItemId(item.id); setEditingItemText(item.text); }}
                        aria-label="Edit item"
                      >
                        ✎
                      </button>
                      <button
                        type="button"
                        className="btn-delete-item"
                        onClick={() => deleteItem({ variables: { id: item.id } })}
                        aria-label="Delete item"
                      >
                        ✕
                      </button>
                    </div>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>

        {isAuthor && (
          <form onSubmit={handleAddItem} className="add-item-form">
            {addItemError && <div className="error-message">{addItemError}</div>}
            <div className="add-item-row">
              <input
                type="text"
                value={newItemText}
                onChange={(e) => setNewItemText(e.target.value)}
                placeholder="Add a new item…"
              />
              <button type="submit" className="btn-primary btn-add-item">
                Add
              </button>
              {aiHealth?.ollamaReachable && (
                <button
                  type="button"
                  className="btn-suggest-item"
                  onClick={handleSuggestItem}
                  disabled={suggesting || sortedItems.length < 3}
                  title={
                    sortedItems.length < 3
                      ? 'Add at least 3 items to enable AI suggestions'
                      : 'Suggest next item with AI'
                  }
                >
                  {suggesting ? 'Suggesting…' : '✨ Suggest'}
                </button>
              )}
            </div>
          </form>
        )}
      </div>

      <div className="post-actions">
        <button onClick={handleLikeToggle} className={post.isLikedByMe ? 'liked' : ''}>
          ❤️ {post.likeCount}
        </button>
        {isAuthor && (
          <>
            <Link to={`/edit/${post.id}`} className="btn-edit">Edit</Link>
            <button onClick={handleDelete} className="btn-delete">Delete</button>
          </>
        )}
      </div>

      <CommentsSection
        postId={post.id}
        comments={post.comments ?? []}
        isAuthenticated={isAuthenticated}
        refetch={refetch}
      />
    </article>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function PostDetailPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();

  const { loading, error, data, refetch } = useQuery<NodeQueryResult>(GET_NODE, {
    variables: { id },
  });

  const [recordPostView] = useMutation(RECORD_POST_VIEW);

  // Fire-and-forget view tracking on mount
  useEffect(() => {
    if (id) {
      recordPostView({ variables: { postId: id } }).catch(() => {
        console.error('Failed to record post view');
      });
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Scroll to hash anchor after content loads
  useEffect(() => {
    if (!loading && data && location.hash) {
      const element = document.getElementById(location.hash.slice(1));
      if (element) element.scrollIntoView({ behavior: 'smooth' });
    }
  }, [loading, data, location.hash]);

  if (loading) return <div className="container"><p>Loading post...</p></div>;
  if (error) return <div className="container"><div className="error-message">Error: {error.message}</div></div>;
  if (!data?.node) return <div className="container"><p>Post not found</p></div>;

  const node = data.node;

  return (
    <div className="container">
      {node.__typename === 'CheckedListPost' ? (
        <CheckedListDetail post={node} refetch={refetch} />
      ) : (
        <BlogPostDetail post={node as BlogPostData} refetch={refetch} />
      )}
    </div>
  );
}
