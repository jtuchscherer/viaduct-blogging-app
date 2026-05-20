export interface Author {
  id: string;
  name: string;
  username: string;
}

export interface Comment {
  id: string;
  content: string;
  author: Author;
  createdAt: string;
}

export interface Post {
  id: string;
  title: string;
  content: string;
  author: Author;
  createdAt: string;
  updatedAt?: string;
  likeCount: number;
  commentCount: number;
  isLikedByMe?: boolean;
  comments?: Comment[];
  viewCount?: number;
  readTimeMinutes?: number;
}

export interface CheckedListItem {
  id: string;
  text: string;
  checked: boolean;
  position: number;
  createdAt: string;
}

export interface CheckedListPost {
  __typename: 'CheckedListPost';
  id: string;
  title: string;
  description: string;
  author: Author;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  commentCount: number;
  isLikedByMe?: boolean;
  items?: CheckedListItem[];
  comments?: Comment[];
  viewCount?: number;
  readTimeMinutes?: number;
}

/** A card-level view of a BlogPost — only the fields needed for list rendering. */
export interface BlogPostCard {
  __typename: 'BlogPost';
  id: string;
  title: string;
  content: string;
  author: Author;
  createdAt: string;
  likeCount: number;
  commentCount: number;
  readTimeMinutes?: number;
}

/** Union of all post types that can appear in the homepage feed. */
export type FeedPost = BlogPostCard | CheckedListPost;

/** Response shape from GET /health/ai. */
export interface AIHealth {
  ollamaReachable: boolean;
  chatModel: string;
  embeddingModel: string;
}

/** Tone options for the rephrase mutation, mirroring the GraphQL RephraseTone enum. */
export type RephraseTone = 'PROFESSIONAL' | 'CASUAL' | 'CONCISE';

