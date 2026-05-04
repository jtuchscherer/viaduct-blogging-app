# Simple Blogging App Development Plan

**Last Updated**: 2026-05-03

A web-based blogging application using React frontend and Viaduct/Kotlin GraphQL backend.

## Features
- **Authors**: Username/password authentication, write/edit/delete posts (rich text via Lexical)
- **Readers**: Username/password authentication, like posts, comment on posts, delete own comments
- **Admins**: Full CRUD over all users, posts, and comments; dashboard with stats
- **CheckedList Posts**: A second post type — ordered checklists with toggleable items
- **Analytics**: View counts, read-time estimates, and trending posts query
- **Tech Stack**: React frontend, Kotlin/Viaduct GraphQL backend, SQLite database, REST auth endpoints

## Database Schema

### Users Table
- id (UUID, primary key)
- username (String, unique)
- email (String)
- name (String)
- passwordHash (String)
- salt (String)
- isAdmin (Boolean, default false)
- createdAt (Timestamp)

### Posts Table (BlogPost in GraphQL)
- id (UUID, primary key)
- authorId (UUID, foreign key to Users)
- title (String)
- content (Text) — HTML from Lexical rich-text editor
- postType (String) — `BLOG_POST` or `CHECKED_LIST`
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

### CheckedListItems Table (checkedlist module)
- id (UUID, primary key)
- postId (UUID, foreign key to Posts)
- text (String)
- checked (Boolean)
- position (Int)
- createdAt (Timestamp)

### PostViews Table (analytics module)
- id (UUID, primary key)
- postId (UUID)
- viewedAt (Timestamp)

## GraphQL Schema

### Types (abridged — see schema.graphqls for full definition)
```graphql
interface Post { id: ID!  title: String!  createdAt: String!  updatedAt: String! }

type BlogPost implements Post {
  id: ID!  title: String!  content: String!
  author: User!  comments: [Comment!]!  likes: [Like!]!
  likeCount: Int!  commentCount: Int!  isLikedByMe: Boolean!
  viewCount: Int!  readTimeMinutes: Float!
  createdAt: String!  updatedAt: String!
}

type CheckedListPost implements Post {
  id: ID!  title: String!
  items: [CheckedListItem!]!  author: User!
  comments: [Comment!]!  likes: [Like!]!
  likeCount: Int!  commentCount: Int!  isLikedByMe: Boolean!
  viewCount: Int!  readTimeMinutes: Float!
  createdAt: String!  updatedAt: String!
}

type CheckedListItem { id: ID!  text: String!  checked: Boolean!  position: Int!  createdAt: String! }

type User { id: ID!  username: String!  email: String!  name: String!  isAdmin: Boolean!  createdAt: String! }
type Comment { id: ID!  content: String!  author: User!  createdAt: String! }
type Like { id: ID!  user: User!  createdAt: String! }
```

### Queries
```graphql
type Query {
  posts: [BlogPost!]!
  post(id: ID!): BlogPost
  myPosts: [BlogPost!]!
  me: User
  postComments(postId: ID!): [Comment!]!
  postsConnection(first: Int, after: String): PostsConnection
  trending(limit: Int): [Post!]!
  checkedListPosts: [CheckedListPost!]!
  adminStats: AdminStats!
  adminUsers: [User!]!  adminUser(id: ID!): User
  adminPosts: [BlogPost!]!  adminPost(id: ID!): BlogPost
  adminComments: [Comment!]!  adminPostComments(postId: ID!): [Comment!]!
  adminUserContentCounts(userId: ID!): UserContentCounts!
}
```

### Mutations
```graphql
type Mutation {
  createPost(title: String!, content: String!): BlogPost!
  updatePost(id: ID!, title: String, content: String): BlogPost!
  deletePost(id: ID!): Boolean!
  createComment(postId: ID!, content: String!): Comment!
  deleteComment(id: ID!): Boolean!
  likePost(postId: ID!): Like!
  unlikePost(postId: ID!): Boolean!
  recordPostView(postId: ID!): Boolean!
  createCheckedListPost(input: CreateCheckedListPostInput!): CheckedListPost!
  addCheckedListItem(input: AddCheckedListItemInput!): CheckedListItem!
  toggleCheckedListItem(id: ID!): CheckedListItem!
  deleteCheckedListItem(id: ID!): Boolean!
  adminUpdateUser(input: AdminUpdateUserInput!): User!
  adminDeleteUser(id: ID!): AdminDeleteUserResult!
  adminUpdatePost(input: AdminUpdatePostInput!): BlogPost!
  adminDeletePost(id: ID!): Boolean!
  adminDeleteComment(id: ID!): Boolean!
}
```

## React Frontend Structure

### Components
- `App.tsx` - Main app with routing and protected routes
- `Header.tsx` - Navigation header with auth state and user menu
- `AuthContext.tsx` - React context for authentication state management

### Pages
- `/` - Home page (HomePage.tsx) - Display all posts with like counts, "Load More" pagination
- `/login` - Login page (LoginPage.tsx) - Username/password authentication
- `/register` - Registration page (RegisterPage.tsx) - New user signup
- `/create` - Create post page (CreatePostPage.tsx) - Protected route, Lexical rich-text editor
- `/edit/:id` - Edit post page (EditPostPage.tsx) - Protected route, author only
- `/post/:id` - Post detail page (PostDetailPage.tsx) - View post with comments and likes
- `/my-posts` - My posts page (MyPostsPage.tsx) - Protected route, user's posts
- `/admin` - Admin dashboard (AdminDashboard.tsx) - Stats cards, admin-only
- `/admin/users` - User management (AdminUsersPage.tsx)
- `/admin/posts` - Post management (AdminPostsPage.tsx)
- `/admin/comments` - Comment management (AdminCommentsPage.tsx)

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
- Runs on port 8080 alongside Viaduct GraphQL (single consolidated server)

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
- ✅ Viaduct 0.31.0 with proper request context authentication
- ✅ All GraphQL post queries and mutations
- ✅ Comment functionality with authorization
- ✅ Like/unlike operations with idempotency
- ✅ Authorization checks (users can only edit/delete their own content)
- ✅ Complete e2e test suite (API tests + Playwright browser tests passing)
- ✅ Relay-style cursor pagination (`postsConnection` with `first`/`after`)
- ✅ Batch author resolver (`batchResolve`) eliminates N+1 queries on post lists
- ✅ Admin section: full CRUD over users, posts, and comments; dashboard stats
- ✅ CheckedList post type with toggleable ordered items
- ✅ Analytics: view counts, read-time estimates, trending posts query
- ✅ 394 unit + integration tests (all passing)

### Frontend (Complete)
- ✅ React 19 + TypeScript + Vite
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
- Queries: `posts`, `post(id)`, `myPosts`, `postComments(postId)`, `postsConnection(first, after)`, `trending(limit)`, `checkedListPosts`, admin queries
- Mutations: All post, comment, like, checkedlist, and admin operations
- Authentication: JWT token via Authorization header
- Context: Authenticated user passed through `ExecutionInput.requestContext`

### Frontend (Port 5173)
- React application with TypeScript
- Apollo Client for GraphQL communication
- Pages: Home, Login, Register, Create Post, Edit Post, Post Detail, My Posts, Admin dashboard + users/posts/comments
- Features: Authentication, post CRUD, comments, likes, checkedlist posts, admin panel
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
./gradlew test        # backend unit + integration tests
./query-tests.sh      # curl-based GraphQL/auth API tests (requires server running)
./e2e.sh              # start servers + run Playwright browser tests
```

## Core App Complete ✅

The core blogging application is fully implemented and tested. See `TODO.md` for in-progress enhancement phases.

### Pending Enhancements
- **Phase 10**: Docker deployment (multi-stage Dockerfile, env-var configuration)
- **Phase 16**: Production database support (PostgreSQL/RDS, connection pooling, Flyway migrations)
- Search functionality
- User profiles with avatars
- Post categories/tags

See `CODE_QUALITY_PLAN.md` for tech debt and code quality items identified in code review.

### Architecture Notes
- Single Ktor server on port 8080: REST auth routes + Viaduct GraphQL colocated
- JWT tokens for stateless authentication; stored in `localStorage` as `authToken`/`authUser`
- Viaduct 0.31.0 request context for auth propagation into resolvers
- Schema-first development: `schema.graphqls` is the source of truth
- SQLite via Exposed ORM; repository pattern abstracts all DB access
- Koin DI wires everything; `KoinTenantCodeInjector` bridges Viaduct ↔ Koin
- Package: `org.tuchscherer`