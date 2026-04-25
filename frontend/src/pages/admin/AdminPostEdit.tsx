import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useState } from 'react';
import RichTextEditor from '../../components/RichTextEditor';

const ADMIN_POST = gql`
  query AdminPost($id: ID!) {
    admin {
      post(id: $id) {
        id
        title
        content
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

const ADMIN_UPDATE_POST = gql`
  mutation AdminUpdatePost($input: AdminUpdatePostInput!) {
    admin {
      updatePost(input: $input) {
        id
        title
        content
        updatedAt
      }
    }
  }
`;

interface Post {
  id: string;
  title: string;
  content: string;
  author: {
    id: string;
    username: string;
  };
  createdAt: string;
  updatedAt: string;
}

interface AdminPostData {
  admin: { post: Post | null };
}

export default function AdminPostEdit() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, loading, error } = useQuery<AdminPostData>(ADMIN_POST, { variables: { id } });
  const [updatePost] = useMutation(ADMIN_UPDATE_POST);

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [initialized, setInitialized] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  // Initialize form when data loads
  if (data?.admin?.post && !initialized) {
    setTitle(data.admin.post.title);
    setContent(data.admin.post.content);
    setInitialized(true);
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError('');

    try {
      await updatePost({
        variables: {
          input: {
            id,
            title,
            content,
          },
        },
      });
      navigate('/admin/posts');
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to update post');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;
  if (!data?.admin?.post) return <div className="error-message">Post not found</div>;

  const post = data.admin.post;

  return (
    <div>
      <div className="admin-header">
        <h1>Edit Post</h1>
      </div>

      <p style={{ marginBottom: '16px', color: '#666' }}>
        By <strong>{post.author.username}</strong> on{' '}
        {new Date(post.createdAt).toLocaleDateString()}
      </p>

      {saveError && <div className="error-message">{saveError}</div>}

      <form className="admin-form" onSubmit={handleSubmit} style={{ maxWidth: '100%' }}>
        <div className="form-group">
          <label htmlFor="title">Title</label>
          <input
            type="text"
            id="title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label>Content</label>
          {initialized && (
            <RichTextEditor
              key={id}
              initialContent={content}
              onChange={setContent}
            />
          )}
        </div>

        <div className="admin-form-actions">
          <Link to="/admin/posts" className="btn-cancel">
            Cancel
          </Link>
          <button type="submit" className="btn-primary" disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </button>
        </div>
      </form>
    </div>
  );
}
