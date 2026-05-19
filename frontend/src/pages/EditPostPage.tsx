import { useState, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import RichTextEditor from '../components/RichTextEditor';
import { isContentEmpty } from '../utils/content';
import { useAIHealth } from '../hooks/useAIHealth';

// ── Queries & Mutations ───────────────────────────────────────────────────────

/**
 * Uses node(id) to support editing both BlogPost and CheckedListPost from the
 * same route. __typename drives the form variant below.
 */
const GET_NODE_FOR_EDIT = gql`
  query GetNodeForEdit($id: ID!) {
    node(id: $id) {
      __typename
      ... on BlogPost {
        id
        title
        content
        author {
          id
        }
      }
      ... on CheckedListPost {
        id
        title
        description
        author {
          id
        }
      }
    }
  }
`;

const UPDATE_BLOG_POST = gql`
  mutation UpdatePost($input: UpdatePostInput!) {
    updatePost(input: $input) {
      id
      title
      content
    }
  }
`;

const REPHRASE_CONTENT = gql`
  mutation RephraseContent($content: String!, $tone: RephraseTone) {
    rephraseContent(content: $content, tone: $tone) {
      rephrasedContent
    }
  }
`;

const UPDATE_CHECKLIST_POST = gql`
  mutation UpdateCheckedListPost($input: UpdateCheckedListPostInput!) {
    updateCheckedListPost(input: $input) {
      id
      title
      description
    }
  }
`;

// ── Types ─────────────────────────────────────────────────────────────────────

interface BlogPostNode {
  __typename: 'BlogPost';
  id: string;
  title: string;
  content: string;
  author: { id: string };
}

interface CheckedListPostNode {
  __typename: 'CheckedListPost';
  id: string;
  title: string;
  description: string;
  author: { id: string };
}

type NodeData = BlogPostNode | CheckedListPostNode;

interface NodeQueryResult {
  node: NodeData | null;
}

// ── Blog Post edit form ───────────────────────────────────────────────────────

function EditBlogPostForm({ post }: { post: BlogPostNode }) {
  const navigate = useNavigate();
  const [title, setTitle] = useState(post.title);
  const [content, setContent] = useState(post.content);
  const [error, setError] = useState('');
  const [tone, setTone] = useState<'PROFESSIONAL' | 'CASUAL' | 'CONCISE'>('PROFESSIONAL');
  const [contentKey, setContentKey] = useState(0);

  const aiHealth = useAIHealth();
  const aiAvailable = aiHealth?.ollamaReachable ?? false;

  const [updatePost, { loading }] = useMutation(UPDATE_BLOG_POST, {
    onCompleted: () => navigate(`/post/${post.id}`),
    onError: (err) => setError(err.message),
  });

  const [rephraseContent, { loading: rephrasing }] = useMutation(REPHRASE_CONTENT, {
    onCompleted: (data) => {
      setContent(data.rephraseContent.rephrasedContent);
      setContentKey((k) => k + 1);
    },
    onError: (err) => setError(`Rephrase failed: ${err.message}`),
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

  const handleRephrase = () => {
    rephraseContent({ variables: { content, tone } });
  };

  return (
    <div className="container">
      <div className="form-container" style={{ maxWidth: '800px' }}>
        <h2>Edit Post</h2>
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
              {aiAvailable && (
                <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                  <select
                    value={tone}
                    onChange={(e) => setTone(e.target.value as typeof tone)}
                    disabled={loading || rephrasing}
                    style={{ fontSize: '0.85rem', padding: '0.25rem 0.5rem' }}
                  >
                    <option value="PROFESSIONAL">Professional</option>
                    <option value="CASUAL">Casual</option>
                    <option value="CONCISE">Concise</option>
                  </select>
                  <button
                    type="button"
                    onClick={handleRephrase}
                    disabled={loading || rephrasing || isContentEmpty(content)}
                    className="btn-secondary"
                    style={{ fontSize: '0.85rem', padding: '0.25rem 0.75rem' }}
                  >
                    {rephrasing ? 'Rephrasing…' : '✨ Rephrase'}
                  </button>
                </div>
              )}
            </div>
            <RichTextEditor
              key={`${post.id}-${contentKey}`}
              initialContent={content}
              onChange={setContent}
              disabled={loading || rephrasing}
              placeholder="Write your post content…"
            />
          </div>
          <div style={{ display: 'flex', gap: '1rem' }}>
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
          <div style={{ display: 'flex', gap: '1rem' }}>
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

  if (loading) {
    return <div className="container"><p>Loading post...</p></div>;
  }

  if (error) {
    return <div className="container"><div className="error-message">Error: {error.message}</div></div>;
  }

  if (!data?.node) {
    return <div className="container"><p>Post not found</p></div>;
  }

  const node = data.node;

  if (node.__typename === 'CheckedListPost') {
    return <EditChecklistForm post={node} />;
  }

  return <EditBlogPostForm post={node as BlogPostNode} />;
}
