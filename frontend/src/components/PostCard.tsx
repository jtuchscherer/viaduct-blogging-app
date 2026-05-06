import { Link } from 'react-router-dom';
import { isContentEmpty, getHtmlPreview, formatReadTime } from '../utils/content';
import type { FeedPost, BlogPostCard, CheckedListPost } from '../types';

function BlogPostCardView({ post }: { post: BlogPostCard }) {
  return (
    <article className="post-card">
      <h2>
        <Link to={`/post/${post.id}`}>{post.title}</Link>
      </h2>
      <div className="post-meta">
        <span className="post-author">by {post.author.name}</span>
        <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      {post.content && !isContentEmpty(post.content) && (
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
          {post.readTimeMinutes !== undefined && (
            <span className="read-time">⏱ {formatReadTime(post.readTimeMinutes)}</span>
          )}
        </div>
        <Link to={`/post/${post.id}`} className="read-more">
          Read more →
        </Link>
      </div>
    </article>
  );
}

function CheckedListPostCardView({ post }: { post: CheckedListPost }) {
  const totalItems = post.items?.length ?? 0;
  const checkedItems = post.items?.filter((i) => i.checked).length ?? 0;

  return (
    <article className="post-card post-card--checklist">
      <div className="post-card-type-badge">☑ Checklist</div>
      <h2>
        <Link to={`/post/${post.id}`}>{post.title}</Link>
      </h2>
      <div className="post-meta">
        <span className="post-author">by {post.author.name}</span>
        <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
      </div>
      {post.description && (
        <p className="post-preview post-preview--text">{post.description}</p>
      )}
      {totalItems > 0 && (
        <div className="checklist-progress">
          <span className="checklist-count">
            {checkedItems}/{totalItems} items checked
          </span>
        </div>
      )}
      <div className="post-footer">
        <div className="post-stats">
          <span className="like-count">❤️ {post.likeCount}</span>
          <Link to={`/post/${post.id}#comments-section`} className="comment-count">
            💬 {post.commentCount}
          </Link>
          {post.readTimeMinutes !== undefined && (
            <span className="read-time">⏱ {formatReadTime(post.readTimeMinutes)}</span>
          )}
        </div>
        <Link to={`/post/${post.id}`} className="read-more">
          View list →
        </Link>
      </div>
    </article>
  );
}

export default function PostCard({ post }: { post: FeedPost }) {
  if (post.__typename === 'CheckedListPost') {
    return <CheckedListPostCardView post={post} />;
  }
  return <BlogPostCardView post={post} />;
}
