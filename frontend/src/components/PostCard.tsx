import { Link } from 'react-router-dom';
import { isContentEmpty, getHtmlPreview, formatReadTime } from '../utils/content';
import type { Post } from '../types';

export default function PostCard({ post }: { post: Post }) {
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
            <span className="read-time">
              ⏱ {formatReadTime(post.readTimeMinutes)}
            </span>
          )}
        </div>
        <Link to={`/post/${post.id}`} className="read-more">
          Read more →
        </Link>
      </div>
    </article>
  );
}
