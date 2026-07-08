import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useState } from 'react';

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
  const { data, loading, error } = useQuery<AdminUserData>(ADMIN_USER, { variables: { id } });

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;
  if (!data?.admin?.user) return <div className="error-message">User not found</div>;

  // Mount the form with the loaded user as its initial state. The `key` remounts
  // (and re-initializes) the form whenever a different user is loaded.
  return <EditUserForm key={data.admin.user.id} user={data.admin.user} />;
}

function EditUserForm({ user }: { user: User }) {
  const navigate = useNavigate();
  const [updateUser] = useMutation(ADMIN_UPDATE_USER);

  const [name, setName] = useState(user.name);
  const [email, setEmail] = useState(user.email);
  const [isAdmin, setIsAdmin] = useState(user.isAdmin);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveError('');

    try {
      await updateUser({
        variables: {
          input: {
            id: user.id,
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
