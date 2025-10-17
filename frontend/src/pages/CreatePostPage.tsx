import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { gql } from '@apollo/client';
import { useMutation } from '@apollo/client/react';

const CREATE_POST = gql`
  mutation CreatePost($input: CreatePostInput!) {
    createPost(input: $input) {
      id
      title
      content
    }
  }
`;

interface CreatePostData {
  createPost: {
    id: string;
    title: string;
    content: string;
  };
}

export default function CreatePostPage() {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const [createPost, { loading }] = useMutation<CreatePostData>(CREATE_POST, {
    onCompleted: (data) => {
      navigate(`/post/${data.createPost.id}`);
    },
    onError: (err) => {
      setError(err.message);
    },
  });

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');

    if (!title.trim() || !content.trim()) {
      setError('Title and content are required');
      return;
    }

    await createPost({
      variables: {
        input: {
          title: title.trim(),
          content: content.trim()
        }
      },
    });
  };

  return (
    <div className="container">
      <div className="form-container" style={{ maxWidth: '800px' }}>
        <h2>Create New Post</h2>

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
            <label htmlFor="content">Content</label>
            <textarea
              id="content"
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="Write your post content..."
              rows={15}
              required
              disabled={loading}
              style={{
                width: '100%',
                padding: '0.75rem',
                border: '1px solid #333',
                borderRadius: '6px',
                backgroundColor: '#2a2a2a',
                color: 'inherit',
                fontFamily: 'inherit',
                fontSize: '1rem',
                resize: 'vertical',
                boxSizing: 'border-box',
              }}
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Creating...' : 'Create Post'}
            </button>
            <button
              type="button"
              onClick={() => navigate('/')}
              disabled={loading}
              style={{
                padding: '0.85rem 1.5rem',
                backgroundColor: '#6c757d',
                color: 'white',
                border: 'none',
              }}
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
