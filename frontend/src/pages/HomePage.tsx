import { useState } from 'react';
import { gql } from '@apollo/client';
import { useQuery } from '@apollo/client/react';
import { appendConnectionEdges } from '../utils/pagination';
import PostCard from '../components/PostCard';
import type { BlogPostCard, CheckedListPost, FeedPost } from '../types';

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
          commentCount
          readTimeMinutes
        }
      }
    }
  }
`;

const GET_CHECKLIST_POSTS = gql`
  query GetCheckedListPosts {
    checkedListPosts {
      id
      title
      description
      author {
        id
        name
        username
      }
      createdAt
      likeCount
      commentCount
      readTimeMinutes
      items {
        id
        text
        checked
        position
      }
    }
  }
`;

const GET_TRENDING = gql`
  query GetTrending($limit: Int) {
    trending(limit: $limit) {
      id
      __typename
      title
      ... on BlogPost {
        content
        readTimeMinutes
      }
      ... on CheckedListPost {
        description
        readTimeMinutes
        items {
          id
          text
          checked
          position
        }
      }
      author {
        id
        name
        username
      }
      createdAt
      likeCount
      commentCount
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
    edges: Array<{ node: Omit<BlogPostCard, '__typename'> }>;
  };
}

interface CheckedListPostsData {
  checkedListPosts: Array<Omit<CheckedListPost, '__typename'>>;
}

interface TrendingData {
  trending: FeedPost[];
}

type SortMode = 'new' | 'trending';

function NewPostsFeed() {
  const [loadingMore, setLoadingMore] = useState(false);

  const {
    loading: blogLoading,
    error: blogError,
    data: blogData,
    fetchMore,
  } = useQuery<PostsConnectionData>(GET_POSTS_CONNECTION, {
    variables: { first: PAGE_SIZE, after: null },
  });

  const {
    loading: clLoading,
    error: clError,
    data: clData,
  } = useQuery<CheckedListPostsData>(GET_CHECKLIST_POSTS);

  const handleLoadMore = async () => {
    if (!blogData?.postsConnection.pageInfo.endCursor) return;
    setLoadingMore(true);
    await fetchMore({
      variables: {
        first: PAGE_SIZE,
        after: blogData.postsConnection.pageInfo.endCursor,
      },
      updateQuery(prev, { fetchMoreResult }) {
        if (!fetchMoreResult) return prev;
        return {
          postsConnection: appendConnectionEdges(
            prev.postsConnection,
            fetchMoreResult.postsConnection,
          ),
        };
      },
    });
    setLoadingMore(false);
  };

  if (blogLoading || clLoading) return <p>Loading posts...</p>;
  if (blogError) return <div className="error-message">Error loading posts: {blogError.message}</div>;
  if (clError) return <div className="error-message">Error loading checklists: {clError.message}</div>;

  const blogPosts: BlogPostCard[] = (blogData?.postsConnection.edges.map((e) => ({
    ...e.node,
    __typename: 'BlogPost' as const,
  })) ?? []);

  const checklistPosts: CheckedListPost[] = (clData?.checkedListPosts.map((p) => ({
    ...p,
    __typename: 'CheckedListPost' as const,
  })) ?? []);

  // Merge and sort by createdAt descending
  const allPosts: FeedPost[] = [...blogPosts, ...checklistPosts].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );

  const connection = blogData?.postsConnection;
  const totalBlogPosts = connection?.totalCount ?? 0;
  const totalChecklistPosts = checklistPosts.length;
  const totalCount = totalBlogPosts + totalChecklistPosts;
  const hasNextPage = connection?.pageInfo.hasNextPage ?? false;

  return allPosts.length === 0 ? (
    <p className="empty-state">No posts yet. Be the first to create one!</p>
  ) : (
    <>
      <div className="posts-list">
        {allPosts.map((post) => (
          <PostCard key={post.id} post={post} />
        ))}
      </div>
      <div className="pagination-footer">
        <span className="post-count">
          Showing {blogPosts.length + totalChecklistPosts} of {totalCount} posts
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
  );
}

function TrendingFeed() {
  const { loading, error, data } = useQuery<TrendingData>(GET_TRENDING, {
    variables: { limit: PAGE_SIZE },
  });

  if (loading) return <p>Loading posts...</p>;
  if (error) return <div className="error-message">Error loading trending posts: {error.message}</div>;

  const posts = data?.trending ?? [];

  return posts.length === 0 ? (
    <p className="empty-state">No trending posts yet.</p>
  ) : (
    <div className="posts-list">
      {posts.map((post) => (
        <PostCard key={post.id} post={post} />
      ))}
    </div>
  );
}

export default function HomePage() {
  const [sortMode, setSortMode] = useState<SortMode>('new');

  return (
    <div className="container">
      <h1>Posts</h1>

      <div className="sort-control" role="group" aria-label="Sort posts by">
        <button
          className={`sort-btn${sortMode === 'new' ? ' active' : ''}`}
          onClick={() => setSortMode('new')}
          aria-pressed={sortMode === 'new'}
        >
          New
        </button>
        <button
          className={`sort-btn${sortMode === 'trending' ? ' active' : ''}`}
          onClick={() => setSortMode('trending')}
          aria-pressed={sortMode === 'trending'}
        >
          Trending
        </button>
      </div>

      {sortMode === 'new' ? <NewPostsFeed /> : <TrendingFeed />}
    </div>
  );
}
