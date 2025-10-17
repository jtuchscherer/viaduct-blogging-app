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
- `App.tsx` - Main app with routing and protected routes
- `Header.tsx` - Navigation header with auth state and user menu
- `AuthContext.tsx` - React context for authentication state management

### Pages
- `/` - Home page (HomePage.tsx) - Display all posts with like counts
- `/login` - Login page (LoginPage.tsx) - Username/password authentication
- `/register` - Registration page (RegisterPage.tsx) - New user signup
- `/create` - Create post page (CreatePostPage.tsx) - Protected route
- `/edit/:id` - Edit post page (EditPostPage.tsx) - Protected route, author only
- `/post/:id` - Post detail page (PostDetailPage.tsx) - View post with comments and likes
- `/my-posts` - My posts page (MyPostsPage.tsx) - Protected route, user's posts

### Key Libraries
- **React Router** - Client-side routing with protected routes
- **Apollo Client** - GraphQL client with authentication middleware
- **Vite** - Fast build tool and dev server
- **TypeScript** - Type safety throughout the application

### Features
- **Authentication**: JWT token stored in localStorage, auto-included in GraphQL requests
- **Protected Routes**: Redirect to login for authenticated-only pages
- **Real-time Updates**: Apollo Client cache management for instant UI updates
- **Error Handling**: User-friendly error messages throughout
- **Loading States**: Visual feedback during async operations
- **Responsive Design**: Works on desktop and mobile devices

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

### Frontend Integration
- JWT token stored in localStorage
- Apollo Client middleware automatically includes token in Authorization header
- React Context manages authentication state across the app
- Login/logout handled with immediate state updates

### Authentication Flow
1. User registers/logs in via REST API (`POST /auth/register` or `/auth/login`)
2. Backend validates credentials and returns JWT token + user info
3. Frontend stores token in localStorage and user in React Context
4. Apollo Client automatically includes token in all GraphQL requests
5. GraphQL resolvers extract JWT from header and verify for protected operations
6. User info passed through Viaduct's `ExecutionInput.requestContext` to resolvers

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

### Phase 4: Frontend Setup ✅ COMPLETED
- [x] Create React app with TypeScript and Vite
- [x] Set up Apollo Client for GraphQL
- [x] Implement authentication context with JWT
- [x] Configure routing with React Router
- [x] Create protected routes

### Phase 5: UI Implementation ✅ COMPLETED
- [x] Build login and registration pages
- [x] Create post list (home page) and detail views
- [x] Add post creation and editing forms
- [x] Implement comments UI with add functionality
- [x] Add likes UI with like/unlike toggle
- [x] Build "My Posts" page for user's own posts
- [x] Add navigation header with auth state

### Phase 6: Integration & Testing ✅ COMPLETED
- [x] Connect frontend to backend GraphQL and REST APIs
- [x] Implement all user flows (register, login, CRUD posts, comments, likes)
- [x] Handle error states throughout UI
- [x] Add responsive styling with dark/light mode support
- [x] Create startup script for easy local development

## Current Status

### Backend (Complete)
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

### Frontend (Complete)
- ✅ React 18 + TypeScript + Vite
- ✅ Apollo Client configured with authentication middleware
- ✅ JWT token management with localStorage persistence
- ✅ React Router with protected routes
- ✅ Authentication pages (Login, Register)
- ✅ Post pages (Home, Detail, Create, Edit, My Posts)
- ✅ Comments section with add functionality
- ✅ Like/unlike functionality with visual feedback
- ✅ Responsive UI with dark/light mode support
- ✅ Error handling and loading states
- ✅ Type-safe TypeScript implementation

### Auth API Endpoints
- `POST /auth/register` - Create new user account
- `POST /auth/login` - Authenticate and get JWT token
- `GET /auth/me` - Get current user info (requires JWT)

### GraphQL API (Port 8080)
- Queries: `posts`, `post(id)`, `myPosts`, `postComments(postId)`
- Mutations: All post, comment, and like operations
- Authentication: JWT token via Authorization header
- Context: Authenticated user passed through `ExecutionInput.requestContext`

### Frontend (Port 5173)
- React application with TypeScript
- Apollo Client for GraphQL communication
- Pages: Home, Login, Register, Create Post, Edit Post, Post Detail, My Posts
- Features: Authentication, post CRUD, comments, likes
- Automatic JWT token inclusion in GraphQL requests

## Getting Started

### Quick Start (Recommended)
```bash
./start.sh
```

This single command will:
1. Build the backend
2. Install frontend dependencies
3. Start all services (GraphQL, Auth, Frontend)

Then open **http://localhost:5173** in your browser.

### Manual Start
See `QUICKSTART.md` for detailed instructions and troubleshooting.

### Running Tests
```bash
# Backend e2e tests
./e2e-test.sh

# Backend unit tests
./gradlew test
```

## Project Complete! 🎉

All planned phases have been implemented:
- ✅ Backend with GraphQL and REST authentication
- ✅ Database schema and migrations
- ✅ Full-featured React frontend
- ✅ Complete user authentication flow
- ✅ All CRUD operations for posts, comments, and likes
- ✅ Authorization and security
- ✅ Responsive UI with modern design

### Potential Enhancements
- Add pagination for post lists
- Implement search functionality
- Add user profiles with avatars
- Email notifications for comments/likes
- Rich text editor for post content
- Image upload support
- Post categories/tags
- Social sharing features

### Architecture Notes
- Clean separation: REST for auth, GraphQL for content
- JWT tokens for stateless authentication
- Viaduct 0.5.0 request context for auth propagation
- Schema-first development with Viaduct
- SQLite for simplicity in demo environment
- Showcases Viaduct platform capabilities