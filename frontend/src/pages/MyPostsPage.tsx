import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { getHtmlPreview } from '../utils/content';
import type { Post, CheckedListPost } from '../types';

const GET_MY_POSTS = gql`
  query GetMyPosts {
    myPosts {
      id
      title
      content
      createdAt
      likeCount
      commentCount
    }
    myCheckedListPosts {
      id
      title
      description
      createdAt
      likeCount
      commentCount
    }
  }
`;

type MyBlogPost = Pick<Post, 'id' | 'title' | 'content' | 'createdAt' | 'likeCount' | 'commentCount'> & { __typename?: 'BlogPost' };
type MyCheckedListPost = Pick<CheckedListPost, 'id' | 'title' | 'description' | 'createdAt' | 'likeCount' | 'commentCount'> & { __typename?: 'CheckedListPost' };
type MyPost = (MyBlogPost & { kind: 'blog' }) | (MyCheckedListPost & { kind: 'checklist' });

interface MyPostsData {
  myPosts: MyBlogPost[];
  myCheckedListPosts: MyCheckedListPost[];
}

function BlogPostCard({ post, authorName }: { post: MyBlogPost; authorName: string }) {
  return (
    <article key={post.id} className="post-card">
      <h2>
        <Link to={`/post/${post.id}`}>{post.title}</Link>
      </h2>
      <div className="post-meta">
        <span className="post-author">by {authorName}</span>
        <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      <div
        className="post-preview"
        dangerouslySetInnerHTML={{ __html: getHtmlPreview(post.content) }}
      />
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

function CheckedListPostCard({ post, authorName }: { post: MyCheckedListPost; authorName: string }) {
  return (
    <article key={post.id} className="post-card post-card--checklist">
      <div className="post-card-type-badge">☑ Checklist</div>
      <h2>
        <Link to={`/post/${post.id}`}>{post.title}</Link>
      </h2>
      <div className="post-meta">
        <span className="post-author">by {authorName}</span>
        <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      {post.description && (
        <p className="post-preview post-preview--text">{post.description}</p>
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

  return (
    <div className="container">
      <div className="page-header">
        <h1>My Posts</h1>
        <Link to="/create" className="btn-primary">
          Create New Post
        </Link>
      </div>

      {allPosts.length === 0 ? (
        <div className="empty-state">
          <p>You haven't created any posts yet.</p>
          <Link to="/create" className="btn-primary">
            Create your first post
          </Link>
        </div>
      ) : (
        <div className="posts-list">
          {allPosts.map((post) =>
            post.kind === 'checklist' ? (
              <CheckedListPostCard key={post.id} post={post} authorName={user?.name ?? ''} />
            ) : (
              <BlogPostCard key={post.id} post={post} authorName={user?.name ?? ''} />
            )
          )}
        </div>
      )}
    </div>
  );
}
