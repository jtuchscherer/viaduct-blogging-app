import { useState } from 'react';
import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { getHtmlPreview } from '../utils/content';
import { DraftBadge } from '../components/DraftIndicators';
import type { Post, CheckedListPost, PostStatus } from '../types';

const GET_MY_POSTS = gql`
  query GetMyPosts {
    myPosts {
      id
      title
      content
      status
      createdAt
      likeCount
      commentCount
    }
    myCheckedListPosts {
      id
      title
      description
      status
      createdAt
      likeCount
      commentCount
    }
  }
`;

type MyBlogPost = Pick<Post, 'id' | 'title' | 'content' | 'status' | 'createdAt' | 'likeCount' | 'commentCount'> & { __typename?: 'BlogPost' };
type MyCheckedListPost = Pick<CheckedListPost, 'id' | 'title' | 'description' | 'status' | 'createdAt' | 'likeCount' | 'commentCount'> & { __typename?: 'CheckedListPost' };
type MyPost = (MyBlogPost & { kind: 'blog' }) | (MyCheckedListPost & { kind: 'checklist' });

interface MyPostsData {
  myPosts: MyBlogPost[];
  myCheckedListPosts: MyCheckedListPost[];
}

/** What the status filter can be set to. 'ALL' is not a status, hence the separate union. */
type StatusFilter = 'ALL' | PostStatus;

const FILTER_LABELS: Record<StatusFilter, string> = {
  ALL: 'All posts',
  PUBLISHED: 'Published',
  DRAFT: 'Drafts',
};

/** This page is the only list that carries drafts, so it is the only one that needs the filter. */
function StatusFilterSelect({
  value,
  onChange,
}: {
  value: StatusFilter;
  onChange: (value: StatusFilter) => void;
}) {
  return (
    <div className="status-filter">
      <label htmlFor="status-filter">Show</label>
      <select
        id="status-filter"
        value={value}
        onChange={(e) => onChange(e.target.value as StatusFilter)}
      >
        {(Object.keys(FILTER_LABELS) as StatusFilter[]).map((option) => (
          <option key={option} value={option}>
            {FILTER_LABELS[option]}
          </option>
        ))}
      </select>
    </div>
  );
}

/**
 * One of the author's own posts.
 *
 * Blog posts and checklists differ in exactly two places — the type badge and how the body is
 * previewed — so they share one component rather than two near-identical ones. The meta line, the
 * stats and the View/Edit actions were duplicated before, which is how the draft badge nearly
 * ended up on only one of the two.
 */
function MyPostCard({ post, authorName }: { post: MyPost; authorName: string }) {
  const isChecklist = post.kind === 'checklist';

  return (
    <article className={`post-card${isChecklist ? ' post-card--checklist' : ''}`}>
      {isChecklist && <div className="post-card-type-badge">☑ Checklist</div>}
      <h2>
        <Link to={`/post/${post.id}`}>{post.title}</Link>
        {post.status === 'DRAFT' && <DraftBadge />}
      </h2>
      <div className="post-meta">
        <span className="post-author">by {authorName}</span>
        <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
      </div>

      {post.kind === 'checklist' ? (
        post.description && <p className="post-preview post-preview--text">{post.description}</p>
      ) : (
        <div
          className="post-preview"
          dangerouslySetInnerHTML={{ __html: getHtmlPreview(post.content) }}
        />
      )}

      <div className="post-footer">
        <div className="post-stats">
          <span className="like-count">❤️ {post.likeCount}</span>
          <Link to={`/post/${post.id}#comments-section`} className="comment-count">
            💬 {post.commentCount}
          </Link>
        </div>
        <div className="post-actions-inline">
          <Link to={`/post/${post.id}`} className="read-more">
            View
          </Link>
          <Link to={`/edit/${post.id}`} className="btn-edit-small">
            Edit
          </Link>
        </div>
      </div>
    </article>
  );
}

export default function MyPostsPage() {
  const { user } = useAuth();
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const { loading, error, data } = useQuery<MyPostsData>(GET_MY_POSTS);

  if (loading) {
    return (
      <div className="container">
        <h1>My Posts</h1>
        <p>Loading your posts...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <h1>My Posts</h1>
        <div className="error-message">Error loading posts: {error.message}</div>
      </div>
    );
  }

  const blogPosts: MyPost[] = (data?.myPosts ?? []).map((p) => ({ ...p, kind: 'blog' as const }));
  const checklistPosts: MyPost[] = (data?.myCheckedListPosts ?? []).map((p) => ({ ...p, kind: 'checklist' as const }));
  const allPosts = [...blogPosts, ...checklistPosts].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
  const visiblePosts =
    statusFilter === 'ALL' ? allPosts : allPosts.filter((p) => p.status === statusFilter);

  return (
    <div className="container">
      <div className="page-header">
        <h1>My Posts</h1>
        <div className="page-header-actions">
          {/* No filter when there is nothing to filter — it would only be a dead control. */}
          {allPosts.length > 0 && (
            <StatusFilterSelect value={statusFilter} onChange={setStatusFilter} />
          )}
          <Link to="/create" className="btn-primary">
            Create New Post
          </Link>
        </div>
      </div>

      {allPosts.length === 0 ? (
        <div className="empty-state">
          <p>You haven't created any posts yet.</p>
          <Link to="/create" className="btn-primary">
            Create your first post
          </Link>
        </div>
      ) : visiblePosts.length === 0 ? (
        // Says the filter is empty rather than that the author has written nothing, which would
        // be untrue and alarming.
        <div className="empty-state">
          <p>No {FILTER_LABELS[statusFilter].toLowerCase()} yet.</p>
        </div>
      ) : (
        <div className="posts-list">
          {visiblePosts.map((post) => (
            <MyPostCard key={post.id} post={post} authorName={user?.name ?? ''} />
          ))}
        </div>
      )}
    </div>
  );
}
