import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';
import RichTextEditor from '../components/RichTextEditor';
import RephraseControls from '../components/RephraseControls';
import { isContentEmpty } from '../utils/content';
import { useAIHealth } from '../hooks/useAIHealth';
import { useRephrase } from '../hooks/useRephrase';
import { useSuggestItem } from '../hooks/useSuggestItem';
import type { PostStatus } from '../types';

// ── Mutations ─────────────────────────────────────────────────────────────────

const CREATE_BLOG_POST = gql`
  mutation CreatePost($input: CreatePostInput!) {
    createPost(input: $input) {
      id
      title
      content
      status
    }
  }
`;

const CREATE_CHECKLIST_POST = gql`
  mutation CreateCheckedListPost($input: CreateCheckedListPostInput!) {
    createCheckedListPost(input: $input) {
      id
      title
      status
    }
  }
`;

// ── Types ─────────────────────────────────────────────────────────────────────

type PostType = 'blog' | 'checklist';

// ── Shared form actions ───────────────────────────────────────────────────────

/**
 * The submit / save-draft / cancel row, shared by both create forms.
 *
 * Publishing is the primary action and drafting the secondary one, so the two are one row rather
 * than a mode the author picks up front: most posts are written to be published.
 */
function CreateFormActions({
  loading,
  publishLabel,
  onSaveDraft,
  onCancel,
}: {
  loading: boolean;
  publishLabel: string;
  onSaveDraft: () => void;
  onCancel: () => void;
}) {
  return (
    <div className="form-actions">
      <button type="submit" className="btn-primary" disabled={loading}>
        {loading ? 'Creating...' : publishLabel}
      </button>
      <button type="button" className="btn-secondary" onClick={onSaveDraft} disabled={loading}>
        Save draft
      </button>
      <button type="button" className="btn-secondary" onClick={onCancel} disabled={loading}>
        Cancel
      </button>
    </div>
  );
}

// ── Blog post form ────────────────────────────────────────────────────────────

function BlogPostForm() {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const aiHealth = useAIHealth();
  const { tone, setTone, contentKey, rephrasing, handleRephrase } = useRephrase(
    content,
    setContent,
    setError,
  );

  const [createPost, { loading }] = useMutation<{ createPost: { id: string } }>(CREATE_BLOG_POST, {
    onCompleted: (data) => navigate(`/post/${data.createPost.id}`),
    onError: (err) => setError(err.message),
  });

  /**
   * A draft is validated exactly like a published post, because the backend validates both the
   * same way: `createPost` requires a non-blank title and non-blank content whatever the status.
   */
  const create = async (status: PostStatus) => {
    setError('');
    if (!title.trim() || isContentEmpty(content)) {
      setError('Title and content are required');
      return;
    }
    await createPost({ variables: { input: { title: title.trim(), content, status } } });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await create('PUBLISHED');
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="error-message">{error}</div>}

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
          />
        </div>
        <RichTextEditor
          key={contentKey}
          initialContent={content}
          onChange={setContent}
          disabled={loading || rephrasing}
          isLoading={rephrasing}
          placeholder="Write your post content…"
        />
      </div>

      <CreateFormActions
        loading={loading}
        publishLabel="Create Post"
        onSaveDraft={() => void create('DRAFT')}
        onCancel={() => navigate('/')}
      />
    </form>
  );
}

// ── Checklist form ────────────────────────────────────────────────────────────

function ChecklistForm() {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [items, setItems] = useState<string[]>(['']);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const aiHealth = useAIHealth();
  const { suggest, suggesting } = useSuggestItem();

  const [createChecklist, { loading }] = useMutation<{ createCheckedListPost: { id: string } }>(CREATE_CHECKLIST_POST, {
    onCompleted: (data) => navigate(`/post/${data.createCheckedListPost.id}`),
    onError: (err) => setError(err.message),
  });

  const handleItemChange = (index: number, value: string) => {
    const next = [...items];
    next[index] = value;
    setItems(next);
  };

  const addItem = () => setItems([...items, '']);

  const handleSuggestItem = async () => {
    const nonEmpty = items.filter((t) => t.trim());
    const suggested = await suggest(nonEmpty);
    if (suggested) setItems([...items, suggested]);
  };

  const removeItem = (index: number) => {
    if (items.length === 1) return; // keep at least one row
    setItems(items.filter((_, i) => i !== index));
  };

  const create = async (status: PostStatus) => {
    setError('');
    if (!title.trim()) {
      setError('Title is required');
      return;
    }
    const validItems = items.map((t) => t.trim()).filter(Boolean);
    await createChecklist({
      variables: {
        input: {
          title: title.trim(),
          description: description.trim() || undefined,
          items: validItems,
          status,
        },
      },
    });
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await create('PUBLISHED');
  };

  return (
    <form onSubmit={handleSubmit}>
      {error && <div className="error-message">{error}</div>}

      <div className="form-group">
        <label htmlFor="cl-title">Title</label>
        <input
          type="text"
          id="cl-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Enter checklist title..."
          required
          disabled={loading}
        />
      </div>

      <div className="form-group">
        <label htmlFor="cl-description">Description (optional)</label>
        <textarea
          id="cl-description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Brief description of this checklist…"
          rows={2}
          disabled={loading}
          style={{ width: '100%', resize: 'vertical' }}
        />
      </div>

      <div className="form-group">
        <label>Items</label>
        <div className="checklist-items-editor">
          {items.map((item, index) => (
            <div key={index} className="checklist-item-row">
              <input
                type="text"
                value={item}
                onChange={(e) => handleItemChange(index, e.target.value)}
                placeholder={`Item ${index + 1}…`}
                disabled={loading}
              />
              {items.length > 1 && (
                <button
                  type="button"
                  className="btn-remove-item"
                  onClick={() => removeItem(index)}
                  disabled={loading}
                  aria-label="Remove item"
                >
                  ✕
                </button>
              )}
            </div>
          ))}
          <button
            type="button"
            className="btn-add-item"
            onClick={addItem}
            disabled={loading}
          >
            + Add item
          </button>
          {aiHealth?.ollamaReachable && (
            <button
              type="button"
              className="btn-suggest-item"
              onClick={handleSuggestItem}
              disabled={loading || suggesting || items.filter((t) => t.trim()).length < 3}
              title={
                items.filter((t) => t.trim()).length < 3
                  ? 'Add at least 3 items to enable AI suggestions'
                  : 'Suggest next item with AI'
              }
            >
              {suggesting ? 'Suggesting…' : '✨ Suggest item'}
            </button>
          )}
        </div>
      </div>

      <CreateFormActions
        loading={loading}
        publishLabel="Create Checklist"
        onSaveDraft={() => void create('DRAFT')}
        onCancel={() => navigate('/')}
      />
    </form>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function CreatePostPage() {
  const [postType, setPostType] = useState<PostType>('blog');

  return (
    <div className="container">
      <div className="form-container" style={{ maxWidth: '800px' }}>
        <h2>Create New Post</h2>

        <div className="post-type-selector" role="group" aria-label="Post type">
          <label className={`post-type-option${postType === 'blog' ? ' selected' : ''}`}>
            <input
              type="radio"
              name="postType"
              value="blog"
              checked={postType === 'blog'}
              onChange={() => setPostType('blog')}
            />
            ✏️ Blog Post
          </label>
          <label className={`post-type-option${postType === 'checklist' ? ' selected' : ''}`}>
            <input
              type="radio"
              name="postType"
              value="checklist"
              checked={postType === 'checklist'}
              onChange={() => setPostType('checklist')}
            />
            ☑ Checklist
          </label>
        </div>

        {postType === 'blog' ? <BlogPostForm /> : <ChecklistForm />}
      </div>
    </div>
  );
}
