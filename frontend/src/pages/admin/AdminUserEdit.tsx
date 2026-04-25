import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useState, useEffect } from 'react';

const ADMIN_USER = gql`
  query AdminUser($id: ID!) {
    admin {
      user(id: $id) {
        id
        username
        email
        name
        isAdmin
        createdAt
      }
    }
  }
`;

const ADMIN_UPDATE_USER = gql`
  mutation AdminUpdateUser($input: AdminUpdateUserInput!) {
    admin {
      updateUser(input: $input) {
        id
        username
        email
        name
        isAdmin
      }
    }
  }
`;

interface User {
  id: string;
  username: string;
  email: string;
  name: string;
  isAdmin: boolean;
  createdAt: string;
}

interface AdminUserData {
  admin: { user: User | null };
}

export default function AdminUserEdit() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { data, loading, error } = useQuery<AdminUserData>(ADMIN_USER, { variables: { id } });
  const [updateUser] = useMutation(ADMIN_UPDATE_USER);

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [isAdmin, setIsAdmin] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    if (data?.admin?.user) {
      setName(data.admin.user.name);
      setEmail(data.admin.user.email);
      setIsAdmin(data.admin.user.isAdmin);
    }
  }, [data]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError('');

    try {
      await updateUser({
        variables: {
          input: {
            id,
            name,
            email,
            isAdmin,
          },
        },
      });
      navigate('/admin/users');
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : 'Failed to update user');
    } finally {
      setSaving(false);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;
  if (!data?.admin?.user) return <div className="error-message">User not found</div>;

  const user = data.admin.user;

  return (
    <div>
      <div className="admin-header">
        <h1>Edit User: {user.username}</h1>
      </div>

      {saveError && <div className="error-message">{saveError}</div>}

      <form className="admin-form" onSubmit={handleSubmit}>
        <div className="form-group">
          <label>Username</label>
          <input type="text" value={user.username} disabled />
        </div>

        <div className="form-group">
          <label htmlFor="name">Name</label>
          <input
            type="text"
            id="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="email">Email</label>
          <input
            type="email"
            id="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div className="form-group">
          <div className="checkbox-group">
            <input
              type="checkbox"
              id="isAdmin"
              checked={isAdmin}
              onChange={(e) => setIsAdmin(e.target.checked)}
            />
            <label htmlFor="isAdmin">Administrator</label>
          </div>
        </div>

        <div className="admin-form-actions">
          <Link to="/admin/users" className="btn-cancel">
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
