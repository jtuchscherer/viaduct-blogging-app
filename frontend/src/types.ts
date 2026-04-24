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
}
