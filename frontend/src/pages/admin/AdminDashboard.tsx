import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';

const ADMIN_STATS = gql`
  query AdminStats {
    admin {
      stats {
        userCount
        postCount
        commentCount
        likeCount
        totalViews
        topPosts {
          id
          title
          viewCount
        }
      }
    }
  }
`;

interface TopPost {
  id: string;
  title: string;
  viewCount: number;
}

interface AdminStats {
  userCount: number;
  postCount: number;
  commentCount: number;
  likeCount: number;
  totalViews: number;
  topPosts: TopPost[];
}

interface AdminStatsData {
  admin: { stats: AdminStats };
}

export default function AdminDashboard() {
  const { data, loading, error } = useQuery<AdminStatsData>(ADMIN_STATS);

  if (loading) return <div className="loading-spinner">Loading...</div>;
  if (error) return <div className="error-message">Error: {error.message}</div>;

  const stats = data?.admin?.stats;

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
        <div className="admin-stat-card">
          <h3>Total Views</h3>
          <div className="stat-value">{stats?.totalViews ?? 0}</div>
        </div>
      </div>

      <div className="admin-section">
        <h2>Most Viewed Posts</h2>
        {(!stats?.topPosts || stats.topPosts.length === 0) ? (
          <p className="empty-state">No views recorded yet.</p>
        ) : (
          <table className="admin-table" data-testid="top-posts-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Title</th>
                <th>Views</th>
              </tr>
            </thead>
            <tbody>
              {stats.topPosts.map((post, index) => (
                <tr key={post.id}>
                  <td>{index + 1}</td>
                  <td>
                    <Link to={`/post/${post.id}`}>{post.title}</Link>
                  </td>
                  <td>{post.viewCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
