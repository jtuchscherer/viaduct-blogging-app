import { useState, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation } from '@apollo/client/react';
import RichTextEditor from '../components/RichTextEditor';
import RephraseControls from '../components/RephraseControls';
import { isContentEmpty } from '../utils/content';
import { useAIHealth } from '../hooks/useAIHealth';
import { useRephrase } from '../hooks/useRephrase';
import { renderPostQueryState } from '../components/PostQueryState';
import { DraftBanner } from '../components/DraftIndicators';
import type { PostStatus } from '../types';
import { GET_NODE_FOR_EDIT, PUBLISH_POST, UNPUBLISH_POST, UPDATE_BLOG_POST, UPDATE_CHECKLIST_POST } from '../graphql/posts';

// ── Types ─────────────────────────────────────────────────────────────────────

interface BlogPostNode {
  __typename: 'BlogPost';
  id: string;
  title: string;
  content: string;
  status: PostStatus;
  author: { id: string };
}

interface CheckedListPostNode {
  __typename: 'CheckedListPost';
  id: string;
  title: string;
  description: string;
  status: PostStatus;
  author: { id: string };
}

type NodeData = BlogPostNode | CheckedListPostNode;

interface NodeQueryResult {
  node: NodeData | null;
}

// ── Publish / unpublish control ───────────────────────────────────────────────

/**
 * The one status transition that applies, plus a banner while the post is a draft.
 *
 * Only one of the two buttons is ever rendered: a draft can be published and a published post can
 * be unpublished, and showing the inapplicable one disabled would only invite a click.
 *
 * Both mutations return the post's new status, which Apollo writes into the normalised cache under
 * the same entity the edit query read. The control and the banner then re-render on their own.
 * Refetching instead would put the page back into its loading state and discard whatever the
 * author had typed into the form.
 */
function PublishControls({ postId, status }: { postId: string; status: PostStatus }) {
  const [error, setError] = useState('');
  const isDraft = status === 'DRAFT';

  const options = {
    variables: { postId },
    onCompleted: () => setError(''),
    onError: (err: Error) => setError(err.message),
  };
  const [publishPost, { loading: publishing }] = useMutation(PUBLISH_POST, options);
  const [unpublishPost, { loading: unpublishing }] = useMutation(UNPUBLISH_POST, options);
  const loading = publishing || unpublishing;

  return (
    <div className="publish-controls">
      {isDraft && <DraftBanner />}
      {error && <div className="error-message">{error}</div>}
      {/* `.btn` rather than `.btn-primary`: the app's primary action colour comes from
          `button[type="submit"]`, which this is not, and an indigo Publish beside a blue Save
          Changes looked like a different kind of control. */}
      <button
        type="button"
        className={isDraft ? 'btn' : 'btn-secondary'}
        disabled={loading}
        onClick={() => void (isDraft ? publishPost() : unpublishPost())}
      >
        {isDraft ? 'Publish' : 'Unpublish'}
      </button>
    </div>
  );
}

// ── Blog Post edit form ───────────────────────────────────────────────────────

function EditBlogPostForm({ post }: { post: BlogPostNode }) {
  const navigate = useNavigate();
  const [title, setTitle] = useState(post.title);
  const [content, setContent] = useState(post.content);
  const [error, setError] = useState('');

  const aiHealth = useAIHealth();
  const { tone, setTone, contentKey, rephrasing, handleRephrase } = useRephrase(
    content,
    setContent,
    setError,
  );

  const [updatePost, { loading }] = useMutation(UPDATE_BLOG_POST, {
    onCompleted: () => navigate(`/post/${post.id}`),
    onError: (err) => setError(err.message),
  });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!title.trim() || isContentEmpty(content)) {
      setError('Title and content are required');
      return;
    }
    await updatePost({ variables: { input: { id: post.id, title: title.trim(), content } } });
  };

  return (
    <div className="container">
      <div className="form-container" style={{ maxWidth: '800px' }}>
        <h2>Edit Post</h2>
        <PublishControls postId={post.id} status={post.status} />
        {error && <div className="error-message">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="title">Title</label>
            <input
              type="text"
              id="title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Enter post title..."
              required
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <label>Content</label>
              <RephraseControls
                aiHealth={aiHealth}
                tone={tone}
                onToneChange={setTone}
                onRephrase={handleRephrase}
                rephrasing={rephrasing}
                formLoading={loading}
                contentEmpty={isContentEmpty(content)}
              /></div>
            <RichTextEditor
              key={`${post.id}-${contentKey}`}
              initialContent={content}
              onChange={setContent}
              disabled={loading || rephrasing}
              isLoading={rephrasing}
              placeholder="Write your post content…"
            />
          </div>
          <div className="form-actions">
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={() => navigate(`/post/${post.id}`)}
              disabled={loading}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── CheckedList edit form ─────────────────────────────────────────────────────

function EditChecklistForm({ post }: { post: CheckedListPostNode }) {
  const navigate = useNavigate();
  const [title, setTitle] = useState(post.title);
  const [description, setDescription] = useState(post.description);
  const [error, setError] = useState('');

  const [updateChecklist, { loading }] = useMutation(UPDATE_CHECKLIST_POST, {
    onCompleted: () => navigate(`/post/${post.id}`),
    onError: (err) => setError(err.message),
  });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    if (!title.trim()) {
      setError('Title is required');
      return;
    }
    await updateChecklist({
      variables: {
        input: {
          id: post.id,
          title: title.trim(),
          description: description.trim(),
        },
      },
    });
  };

  return (
    <div className="container">
      <div className="form-container" style={{ maxWidth: '800px' }}>
        <h2>Edit Checklist</h2>
        <PublishControls postId={post.id} status={post.status} />
        <p style={{ color: '#6c757d', marginBottom: '1rem' }}>
          To add, remove, or reorder items, go back to the{' '}
          <span
            style={{ cursor: 'pointer', color: 'var(--color-primary)', textDecoration: 'underline' }}
            onClick={() => navigate(`/post/${post.id}`)}
          >
            checklist page
          </span>
          .
        </p>
        {error && <div className="error-message">{error}</div>}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="cl-title">Title</label>
            <input
              type="text"
              id="cl-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Checklist title..."
              required
              disabled={loading}
            />
          </div>
          <div className="form-group">
            <label htmlFor="cl-description">Description</label>
            <textarea
              id="cl-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Brief description of this checklist…"
              rows={3}
              disabled={loading}
              style={{ width: '100%', resize: 'vertical' }}
            />
          </div>
          <div className="form-actions">
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={() => navigate(`/post/${post.id}`)}
              disabled={loading}
              className="btn-secondary"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function EditPostPage() {
  const { id } = useParams<{ id: string }>();

  const { loading, error, data } = useQuery<NodeQueryResult>(GET_NODE_FOR_EDIT, {
    variables: { id },
  });

  const node = data?.node;
  if (loading || error || !node) {
    return renderPostQueryState({ loading, error, missing: !node });
  }

  if (node.__typename === 'CheckedListPost') {
    return <EditChecklistForm post={node} />;
  }

  return <EditBlogPostForm post={node as BlogPostNode} />;
}
