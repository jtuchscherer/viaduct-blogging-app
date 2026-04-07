import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';

const ADMIN_STATS = gql`
  query AdminStats {
    adminStats {
      userCount
      postCount
      commentCount
      likeCount
    }
  }
`;

interface AdminStats {
  userCount: number;
  postCount: number;
  commentCount: number;
  likeCount: number;
}

interface AdminStatsData {
  adminStats: AdminStats;
}

export default function AdminDashboard() {
  const { data, loading, error } = useQuery<AdminStatsData>(ADMIN_STATS);

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const stats = data?.adminStats;

  return (
    <div>
      <div className="admin-header">
        <h1>Dashboard</h1>
      </div>

      <div className="admin-stats-grid">
        <Link to="/admin/users" className="admin-stat-card">
          <h3>Users</h3>
          <div className="stat-value">{stats?.userCount ?? 0}</div>
        </Link>
        <Link to="/admin/posts" className="admin-stat-card">
          <h3>Posts</h3>
          <div className="stat-value">{stats?.postCount ?? 0}</div>
        </Link>
        <Link to="/admin/comments" className="admin-stat-card">
          <h3>Comments</h3>
          <div className="stat-value">{stats?.commentCount ?? 0}</div>
        </Link>
        <div className="admin-stat-card">
          <h3>Likes</h3>
          <div className="stat-value">{stats?.likeCount ?? 0}</div>
        </div>
      </div>
    </div>
  );
}
