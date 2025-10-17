import { useState, useEffect, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';

const GET_POST = gql`
  query GetPostForEdit($id: ID!) {
    post(id: $id) {
      id
      title
      content
      author {
        id
      }
    }
  }
`;

const UPDATE_POST = gql`
  mutation UpdatePost($input: UpdatePostInput!) {
    updatePost(input: $input) {
      id
      title
      content
    }
  }
`;

interface Post {
  id: string;
  title: string;
  content: string;
  author: {
    id: string;
  };
}

interface PostData {
  post: Post;
}

export default function EditPostPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [error, setError] = useState('');

  const { loading: loadingPost, error: queryError, data } = useQuery<PostData>(GET_POST, {
    variables: { id },
  });

  const [updatePost, { loading: updating }] = useMutation(UPDATE_POST, {
    onCompleted: () => {
      navigate(`/post/${id}`);
    },
    onError: (err) => {
      setError(err.message);
    },
  });

  // Populate form when data loads
  useEffect(() => {
    if (data?.post) {
      setTitle(data.post.title);
      setContent(data.post.content);
    }
  }, [data]);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');

    if (!title.trim() || !content.trim()) {
      setError('Title and content are required');
      return;
    }

    await updatePost({
      variables: {
        input: {
          id,
          title: title.trim(),
          content: content.trim()
        }
      },
    });
  };

  if (loadingPost) {
    return (
      <div className="container">
        <p>Loading post...</p>
      </div>
    );
  }

  if (queryError) {
    return (
      <div className="container">
        <div className="error-message">Error: {queryError.message}</div>
      </div>
    );
  }

  if (!data?.post) {
    return (
      <div className="container">
        <p>Post not found</p>
      </div>
    );
  }

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
              disabled={updating}
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
              disabled={updating}
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
            <button type="submit" className="btn-primary" disabled={updating}>
              {updating ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={() => navigate(`/post/${id}`)}
              disabled={updating}
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
