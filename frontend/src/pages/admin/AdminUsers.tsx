import { gql } from '@apollo/client';
import { useQuery, useMutation } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useState } from 'react';

const PAGE_SIZE = 10;

const ADMIN_USERS = gql`
  query AdminUsers($limit: Int!, $offset: Int!) {
    admin {
      users(limit: $limit, offset: $offset) {
        totalCount
        users {
          id
          username
          email
          name
          isAdmin
          createdAt
        }
      }
    }
  }
`;

const ADMIN_USER_CONTENT_COUNTS = gql`
  query AdminUserContentCounts($userId: ID!) {
    admin {
      userContentCounts(userId: $userId) {
        postCount
        commentCount
        likeCount
      }
    }
  }
`;

const ADMIN_DELETE_USER = gql`
  mutation AdminDeleteUser($id: ID!) {
    admin {
      deleteUser(id: $id) {
        success
        postsDeleted
        commentsDeleted
        likesDeleted
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

interface AdminUsersData {
  admin: { users: { totalCount: number; users: User[] } };
}

interface ContentCounts {
  postCount: number;
  commentCount: number;
  likeCount: number;
}

interface AdminUserContentCountsData {
  admin: { userContentCounts: ContentCounts };
}

export default function AdminUsers() {
  const [offset, setOffset] = useState(0);
  const { data, loading, error, refetch } = useQuery<AdminUsersData>(ADMIN_USERS, {
    variables: { limit: PAGE_SIZE, offset },
  });
  const [deleteUser] = useMutation(ADMIN_DELETE_USER);
  const [userToDelete, setUserToDelete] = useState<User | null>(null);
  const [contentCounts, setContentCounts] = useState<ContentCounts | null>(null);
  const [loadingCounts, setLoadingCounts] = useState(false);
  const [countsError, setCountsError] = useState<string | null>(null);
  const { refetch: fetchContentCounts } = useQuery<AdminUserContentCountsData>(ADMIN_USER_CONTENT_COUNTS, {
    skip: true,
  });

  const resetDeleteDialog = () => {
    setUserToDelete(null);
    setContentCounts(null);
    setCountsError(null);
  };

  const handleDeleteClick = async (user: User) => {
    setUserToDelete(user);
    setContentCounts(null);
    setCountsError(null);
    setLoadingCounts(true);
    try {
      const result = await fetchContentCounts({ userId: user.id });
      setContentCounts(result.data?.admin?.userContentCounts ?? null);
    } catch (err) {
      // Don't silently fall back to zeros — that would show the admin a
      // misleadingly-empty impact summary and let them delete a user with
      // lots of content thinking there's none. Surface the error and keep
      // the confirm button disabled until the admin retries or cancels.
      console.error('Failed to fetch user content counts:', err);
      setCountsError(
        err instanceof Error ? err.message : 'Failed to load content counts'
      );
    }
    setLoadingCounts(false);
  };

  const confirmDelete = async () => {
    if (!userToDelete) return;
    try {
      await deleteUser({ variables: { id: userToDelete.id } });
      resetDeleteDialog();
      refetch();
    } catch (err) {
      console.error('Failed to delete user:', err);
    }
  };

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const users: User[] = data?.admin?.users.users ?? [];
  const totalCount = data?.admin?.users.totalCount ?? 0;
  const currentPage = Math.floor(offset / PAGE_SIZE) + 1;
  const totalPages = Math.ceil(totalCount / PAGE_SIZE);
  const rangeStart = totalCount === 0 ? 0 : offset + 1;
  const rangeEnd = Math.min(offset + PAGE_SIZE, totalCount);

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
              <tr key={user.id} data-testid="admin-table-row">
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

      <div className="admin-pagination">
        <span data-testid="admin-page-info">
          {totalCount === 0 ? 'No users' : `Showing ${rangeStart}–${rangeEnd} of ${totalCount}`}
        </span>
        <div className="admin-pagination-controls">
          <button
            data-testid="btn-prev-page"
            className="btn-page"
            onClick={() => { setOffset(offset - PAGE_SIZE); refetch(); }}
            disabled={offset === 0}
          >
            Previous
          </button>
          <span className="admin-page-number">Page {currentPage} of {totalPages || 1}</span>
          <button
            data-testid="btn-next-page"
            className="btn-page"
            onClick={() => { setOffset(offset + PAGE_SIZE); refetch(); }}
            disabled={offset + PAGE_SIZE >= totalCount}
          >
            Next
          </button>
        </div>
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
            ) : countsError ? (
              <div className="error-message" data-testid="content-counts-error">
                Couldn't load what would be deleted: {countsError}. Please retry or cancel.
              </div>
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
                onClick={resetDeleteDialog}
              >
                Cancel
              </button>
              {countsError && (
                <button
                  className="btn-page"
                  data-testid="btn-retry-counts"
                  onClick={() => userToDelete && handleDeleteClick(userToDelete)}
                >
                  Retry
                </button>
              )}
              <button
                className="btn-confirm-delete"
                onClick={confirmDelete}
                disabled={loadingCounts || contentCounts === null}
              >
                Delete User
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
