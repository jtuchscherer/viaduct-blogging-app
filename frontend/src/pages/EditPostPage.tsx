import { useState, type FormEvent } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import RichTextEditor from '../components/RichTextEditor';

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
  author: { id: string };
}

interface PostData {
  post: Post;
}

function isContentEmpty(html: string): boolean {
  return !html || html.replace(/<[^>]*>/g, '').trim() === '';
}

// ── Inner form — receives post data after it has loaded ───────────────────────

function EditPostForm({ post }: { post: Post }) {
  const navigate = useNavigate();
  const [title, setTitle] = useState(post.title);
  const [content, setContent] = useState(post.content);
  const [error, setError] = useState('');

  const [updatePost, { loading: updating }] = useMutation(UPDATE_POST, {
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

    await updatePost({
      variables: {
        input: { id: post.id, title: title.trim(), content },
      },
    });
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
              disabled={updating}
            />
          </div>

          <div className="form-group">
            <label>Content</label>
            {/*
              key=post.id ensures the editor remounts if we ever navigate
              between different edit pages without unmounting this component.
            */}
            <RichTextEditor
              key={post.id}
              initialContent={post.content}
              onChange={setContent}
              disabled={updating}
              placeholder="Write your post content…"
            />
          </div>

          <div style={{ display: 'flex', gap: '1rem' }}>
            <button type="submit" className="btn-primary" disabled={updating}>
              {updating ? 'Saving...' : 'Save Changes'}
            </button>
            <button
              type="button"
              onClick={() => navigate(`/post/${post.id}`)}
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

// ── Page — handles loading / error states ─────────────────────────────────────

export default function EditPostPage() {
  const { id } = useParams<{ id: string }>();

  const { loading, error, data } = useQuery<PostData>(GET_POST, {
    variables: { id },
  });

  if (loading) {
    return (
      <div className="container">
        <p>Loading post...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <div className="error-message">Error: {error.message}</div>
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

  return <EditPostForm post={data.post} />;
}
