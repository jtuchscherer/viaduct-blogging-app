# Simple Blogging App Development Plan

A web-based blogging application using React frontend and Viaduct/Kotlin GraphQL backend.

## Features
- **Authors**: Username/password authentication, write/edit/delete posts
- **Readers**: Username/password authentication, like posts, comment on posts, delete own comments
- **Tech Stack**: React frontend, Kotlin/Viaduct GraphQL backend, SQLite database, REST auth endpoints

## Database Schema

### Users Table
- id (UUID, primary key)
- username (String, unique)
- email (String)
- name (String)
- passwordHash (String)
- salt (String)
- createdAt (Timestamp)

### Posts Table
- id (UUID, primary key)
- authorId (UUID, foreign key to Users)
- title (String)
- content (Text)
- createdAt (Timestamp)
- updatedAt (Timestamp)

### Comments Table
- id (UUID, primary key)
- postId (UUID, foreign key to Posts)
- authorId (UUID, foreign key to Users)
- content (Text)
- createdAt (Timestamp)

### Likes Table
- id (UUID, primary key)
- postId (UUID, foreign key to Posts)
- userId (UUID, foreign key to Users)
- createdAt (Timestamp)
- Unique constraint on (postId, userId)

## GraphQL Schema

### Types
```graphql
type User {
  id: ID!
  username: String!
  email: String!
  name: String!
  posts: [Post!]!
  createdAt: String!
}

type Post {
  id: ID!
  title: String!
  content: String!
  author: User!
  comments: [Comment!]!
  likes: [Like!]!
  likeCount: Int!
  createdAt: String!
  updatedAt: String!
}

type Comment {
  id: ID!
  content: String!
  author: User!
  post: Post!
  createdAt: String!
}

type Like {
  id: ID!
  user: User!
  post: Post!
  createdAt: String!
}
```

### Queries
```graphql
type Query {
  posts: [Post!]!
  post(id: ID!): Post
  myPosts: [Post!]!
  me: User
}
```

### Mutations
```graphql
type Mutation {
  createPost(title: String!, content: String!): Post!
  updatePost(id: ID!, title: String, content: String): Post!
  deletePost(id: ID!): Boolean!

  createComment(postId: ID!, content: String!): Comment!
  deleteComment(id: ID!): Boolean!

  likePost(postId: ID!): Like!
  unlikePost(postId: ID!): Boolean!
}
```

## React Frontend Structure

### Components
- `App.tsx` - Main app with routing
- `LoginPage.tsx` - Google OAuth login
- `PostList.tsx` - Display all posts
- `PostItem.tsx` - Individual post display
- `PostForm.tsx` - Create/edit post form
- `CommentList.tsx` - Display comments for a post
- `CommentForm.tsx` - Add comment form
- `Header.tsx` - Navigation and user info

### Pages
- `/` - Home page with all posts
- `/login` - Login page
- `/create` - Create new post
- `/edit/:id` - Edit existing post
- `/post/:id` - View single post with comments

### Key Libraries
- React Router for navigation
- Apollo Client for GraphQL
- JWT token handling for authentication

## Authentication System

### Backend (Kotlin/Ktor REST API)
- Username/password authentication with salted SHA-256 hashing
- JWT token generation and validation
- REST endpoints: `/auth/register`, `/auth/login`, `/auth/me`
- Runs on port 8081 alongside Viaduct GraphQL

### Password Security
- SHA-256 hashing with random salt
- Salt stored separately in database
- Password verification using constant-time comparison

### Frontend Integration (Future)
- Store JWT token in localStorage
- Include token in Authorization header for GraphQL requests
- Handle login/logout states

### Authentication Flow
1. User registers/logs in via REST API
2. Backend validates credentials and returns JWT token
3. Frontend stores token and includes in GraphQL requests
4. GraphQL resolvers verify JWT for protected operations

## Implementation Roadmap

### Phase 1: Schema & Backend Setup ✅ COMPLETED
- [x] Clone Viaduct CLI starter
- [x] **Define GraphQL schema file (.graphql)** - This becomes the contract
- [x] Set up SQLite database with schema migrations
- [x] Generate/implement GraphQL types and resolvers based on schema

### Phase 2: Database & Authentication ✅ COMPLETED
- [x] Create SQLite tables (Users, Posts, Comments, Likes)
- [x] Switch to username/password authentication with salted hashing
- [x] Create REST auth endpoints (login/register) with Ktor
- [x] Implement JWT token authentication
- [x] Test basic authentication flow

### Phase 3: Core GraphQL Operations ✅ COMPLETED
- [x] Implement post queries and mutations
- [x] Add comment functionality
- [x] Implement like/unlike operations
- [x] Add authorization checks (users can only edit/delete their own posts)

### Phase 4: Frontend Setup
- [ ] Create React app with TypeScript
- [ ] Set up Apollo Client
- [ ] Configure Google OAuth for frontend
- [ ] Implement routing

### Phase 5: UI Implementation
- [ ] Build login page
- [ ] Create post list and detail views
- [ ] Add post creation/editing forms
- [ ] Implement comments and likes UI

### Phase 6: Integration & Testing
- [ ] Connect frontend to backend
- [ ] Test all user flows
- [ ] Handle error states
- [ ] Add basic styling

## Current Status

### What's Implemented
- ✅ Complete database schema with SQLite
- ✅ Username/password authentication with salted hashing
- ✅ REST API for auth endpoints with JWT tokens
- ✅ GraphQL schema fully defined and implemented
- ✅ Viaduct 0.5.0 with proper request context authentication
- ✅ All GraphQL post queries and mutations
- ✅ Comment functionality with authorization
- ✅ Like/unlike operations with idempotency
- ✅ Authorization checks (users can only edit/delete their own content)
- ✅ Complete e2e test suite (28/28 tests passing)

### Auth API Endpoints
- `POST /auth/register` - Create new user account
- `POST /auth/login` - Authenticate and get JWT token
- `GET /auth/me` - Get current user info (requires JWT)

### GraphQL API (Port 8080)
- Queries: `posts`, `post(id)`, `myPosts`, `postComments(postId)`
- Mutations: All post, comment, and like operations
- Authentication: JWT token via Authorization header
- Context: Authenticated user passed through `ExecutionInput.requestContext`

### Next Steps
1. Begin React frontend development (Phase 4)
2. Set up Apollo Client for GraphQL integration
3. Build UI components for posts, comments, and likes
4. Implement frontend authentication flow

### Architecture Notes
- Clean separation: REST for auth, GraphQL for content
- JWT tokens for stateless authentication
- Viaduct 0.5.0 request context for auth propagation
- Schema-first development with Viaduct
- SQLite for simplicity in demo environment
- Showcases Viaduct platform capabilities