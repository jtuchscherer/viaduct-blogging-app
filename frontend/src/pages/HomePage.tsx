import { useState } from 'react';
import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { Link } from 'react-router-dom';
import { isContentEmpty, getExcerpt } from '../utils/content';
import type { Post } from '../types';

const PAGE_SIZE = 10;

const GET_POSTS_CONNECTION = gql`
  query GetPostsConnection($first: Int, $after: String) {
    postsConnection(first: $first, after: $after) {
      totalCount
      pageInfo {
        hasNextPage
        endCursor
      }
      edges {
        node {
          id
          title
          content
          author {
            id
            name
            username
          }
          createdAt
          likeCount
        }
      }
    }
  }
`;

interface PostsConnectionData {
  postsConnection: {
    totalCount: number;
    pageInfo: {
      hasNextPage: boolean;
      endCursor: string | null;
    };
    edges: Array<{ node: Post }>;
  };
}


export default function HomePage() {
  const [loadingMore, setLoadingMore] = useState(false);

  const { loading, error, data, fetchMore } = useQuery<PostsConnectionData>(GET_POSTS_CONNECTION, {
    variables: { first: PAGE_SIZE, after: null },
  });

  const handleLoadMore = async () => {
    if (!data?.postsConnection.pageInfo.endCursor) return;
    setLoadingMore(true);
    await fetchMore({
      variables: {
        first: PAGE_SIZE,
        after: data.postsConnection.pageInfo.endCursor,
      },
      updateQuery(prev, { fetchMoreResult }) {
        if (!fetchMoreResult) return prev;
        return {
          postsConnection: {
            ...fetchMoreResult.postsConnection,
            edges: [
              ...prev.postsConnection.edges,
              ...fetchMoreResult.postsConnection.edges,
            ],
          },
        };
      },
    });
    setLoadingMore(false);
  };

  if (loading) {
    return (
      <div className="container">
        <h1>Blog Posts</h1>
        <p>Loading posts...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="container">
        <h1>Blog Posts</h1>
        <div className="error-message">Error loading posts: {error.message}</div>
      </div>
    );
  }

  const connection = data?.postsConnection;
  const posts = connection?.edges.map((e) => e.node) ?? [];
  const totalCount = connection?.totalCount ?? 0;
  const hasNextPage = connection?.pageInfo.hasNextPage ?? false;

  return (
    <div className="container">
      <h1>Blog Posts</h1>

      {posts.length === 0 ? (
        <p className="empty-state">No posts yet. Be the first to create one!</p>
      ) : (
        <>
          <div className="posts-list">
            {posts.map((post) => (
              <article key={post.id} className="post-card">
                <h2>
                  <Link to={`/post/${post.id}`}>{post.title}</Link>
                </h2>
                <div className="post-meta">
                  <span className="post-author">by {post.author.name}</span>
                  <span className="post-date">{new Date(post.createdAt).toLocaleDateString()}</span>
                </div>
                {!isContentEmpty(post.content) && (
                  <p className="post-excerpt">{getExcerpt(post.content)}</p>
                )}
                <div className="post-footer">
                  <span className="like-count">❤️ {post.likeCount}</span>
                  <Link to={`/post/${post.id}`} className="read-more">
                    Read more →
                  </Link>
                </div>
              </article>
            ))}
          </div>

          <div className="pagination-footer">
            <span className="post-count">
              Showing {posts.length} of {totalCount} posts
            </span>
            {hasNextPage && (
              <button
                className="btn-load-more"
                onClick={handleLoadMore}
                disabled={loadingMore}
              >
                {loadingMore ? 'Loading...' : 'Load More'}
              </button>
            )}
          </div>
        </>
      )}
    </div>
  );
}
