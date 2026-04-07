import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const ADMIN_USERS = gql`
  query AdminUsers {
    adminUsers {
      id
      username
      email
      name
      isAdmin
      createdAt
    }
  }
`;

const ADMIN_USER_CONTENT_COUNTS = gql`
  query AdminUserContentCounts($userId: ID!) {
    adminUserContentCounts(userId: $userId) {
      postCount
      commentCount
      likeCount
    }
  }
`;

const ADMIN_DELETE_USER = gql`
  mutation AdminDeleteUser($id: ID!) {
    adminDeleteUser(id: $id) {
      success
      postsDeleted
      commentsDeleted
      likesDeleted
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

interface AdminUsersData {
  adminUsers: User[];
}

interface ContentCounts {
  postCount: number;
  commentCount: number;
  likeCount: number;
}

interface AdminUserContentCountsData {
  adminUserContentCounts: ContentCounts;
}

export default function AdminUsers() {
  const { data, loading, error, refetch } = useQuery<AdminUsersData>(ADMIN_USERS);
  const [deleteUser] = useMutation(ADMIN_DELETE_USER);
  const [userToDelete, setUserToDelete] = useState<User | null>(null);
  const [contentCounts, setContentCounts] = useState<ContentCounts | null>(null);
  const [loadingCounts, setLoadingCounts] = useState(false);
  const { refetch: fetchContentCounts } = useQuery<AdminUserContentCountsData>(ADMIN_USER_CONTENT_COUNTS, {
    skip: true,
  });

  const handleDeleteClick = async (user: User) => {
    setUserToDelete(user);
    setLoadingCounts(true);
    try {
      const result = await fetchContentCounts({ userId: user.id });
      setContentCounts(result.data?.adminUserContentCounts ?? null);
    } catch {
      setContentCounts({ postCount: 0, commentCount: 0, likeCount: 0 });
    }
    setLoadingCounts(false);
  };

  const confirmDelete = async () => {
    if (!userToDelete) return;
    try {
      await deleteUser({ variables: { id: userToDelete.id } });
      setUserToDelete(null);
      setContentCounts(null);
      refetch();
    } catch (err) {
      console.error('Failed to delete user:', err);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const users: User[] = data?.adminUsers ?? [];

  return (
    <div>
      <div className="admin-header">
        <h1>Users</h1>
      </div>

      <div className="admin-table">
        <table>
          <thead>
            <tr>
              <th>Username</th>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id}>
                <td>{user.username}</td>
                <td>{user.name}</td>
                <td>{user.email}</td>
                <td>
                  <span className={`admin-badge ${user.isAdmin ? 'admin' : 'user'}`}>
                    {user.isAdmin ? 'Admin' : 'User'}
                  </span>
                </td>
                <td>{new Date(user.createdAt).toLocaleDateString()}</td>
                <td>
                  <div className="admin-actions">
                    <Link to={`/admin/users/${user.id}`} className="btn-edit">
                      Edit
                    </Link>
                    <button className="btn-delete" onClick={() => handleDeleteClick(user)}>
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {userToDelete && (
        <div className="confirm-modal">
          <div className="confirm-modal-content">
            <h3>Delete User</h3>
            <p>
              Are you sure you want to delete <strong>{userToDelete.username}</strong>?
            </p>
            {loadingCounts ? (
              <p>Loading content counts...</p>
            ) : (
              contentCounts && (
                <div className="warning">
                  This will also delete:
                  <ul>
                    <li>{contentCounts.postCount} posts</li>
                    <li>{contentCounts.commentCount} comments</li>
                    <li>{contentCounts.likeCount} likes</li>
                  </ul>
                </div>
              )
            )}
            <div className="confirm-modal-actions">
              <button
                className="btn-cancel"
                onClick={() => {
                  setUserToDelete(null);
                  setContentCounts(null);
                }}
              >
                Cancel
              </button>
              <button className="btn-confirm-delete" onClick={confirmDelete} disabled={loadingCounts}>
                Delete User
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
