# Viaduct Blogs - Quick Start Guide

## Prerequisites

- **Java 11+** - For running the Kotlin backend
- **Node.js & npm** - For the React frontend
- **Gradle** - Included via gradlew wrapper

## Starting the Application

### Option 1: One-Command Start (Recommended)

```bash
./start.sh
```

This script will:
1. Build the backend
2. Install frontend dependencies (if needed)
3. Start the GraphQL server on port 8080
4. Start the Auth server on port 8081
5. Start the frontend dev server on port 5173

**Press Ctrl+C to stop all services**

### Option 2: Manual Start

If you prefer to run services in separate terminals:

**Terminal 1 - GraphQL Server:**
```bash
./gradlew run
```

**Terminal 2 - Auth Server:**
```bash
./gradlew build
java -cp build/libs/viaduct-blogs-1.0-SNAPSHOT.jar com.example.AuthServerKt
```

**Terminal 3 - Frontend:**
```bash
cd frontend
npm install
npm run dev
```

## Accessing the Application

Once started, open your browser to:

**🌐 http://localhost:5173**

The frontend will automatically connect to:
- GraphQL API at http://localhost:8080/graphql
- Auth API at http://localhost:8081

## Using the Application

### 1. Register a New Account

1. Click **Register** in the navigation
2. Fill in:
   - Username
   - Email
   - Name
   - Password
3. Click **Register** - you'll be automatically logged in

### 2. Create Your First Post

1. Click **New Post** in the navigation
2. Enter a title and content
3. Click **Create Post**

### 3. Interact with Posts

- **View all posts** - Home page
- **Like posts** - Click the heart icon (requires login)
- **Add comments** - Go to post detail page (requires login)
- **Edit/Delete** - Your own posts only

### 4. View Your Posts

Click **My Posts** in the navigation to see all your posts with quick access to edit.

## Troubleshooting

### Ports Already in Use

If you see errors about ports being in use:

```bash
# Check what's using the ports
lsof -ti:8080  # GraphQL server
lsof -ti:8081  # Auth server
lsof -ti:5173  # Frontend

# Kill processes if needed
kill $(lsof -ti:8080)
kill $(lsof -ti:8081)
kill $(lsof -ti:5173)
```

### Build Errors

```bash
# Clean and rebuild
./gradlew clean build
```

### Frontend Not Loading

```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
npm run dev
```

## Viewing Logs

When using `./start.sh`, logs are written to:
- `server.log` - Server logs
- `frontend-dev.log` - Frontend dev server logs

View logs in real-time:
```bash
tail -f server.log
tail -f frontend-dev.log
```

## Running Tests

```bash
# Run all tests
./gradlew test

# Run E2E tests
./e2e-test.sh
```

## Technology Stack

**Backend:**
- Kotlin
- Viaduct 0.5.0 (GraphQL)
- Ktor (HTTP & REST)
- Koin
- Exposed (Database ORM)
- H2 Database (in-memory)
- JWT Authentication

**Frontend:**
- React 18
- TypeScript
- Vite
- Apollo Client (GraphQL)
- React Router

## API Endpoints

### GraphQL (Port 8080)

**Query:**
```graphql
{
  posts {
    id
    title
    content
    author { name }
    likeCount
  }
}
```

### REST Auth (Port 8081)

**Register:**
```bash
POST /auth/register
Content-Type: application/json

{
  "username": "johndoe",
  "email": "john@example.com",
  "name": "John Doe",
  "password": "secret123"
}
```

**Login:**
```bash
POST /auth/login
Content-Type: application/json

{
  "username": "johndoe",
  "password": "secret123"
}
```

## Project Structure

```
viaduct-blogs/
├── src/main/kotlin/com/example/
│   ├── Application.kt          # Main GraphQL server
│   ├── AuthServer.kt           # Auth REST server
│   ├── web/GraphQLServer.kt    # GraphQL setup
│   ├── resolvers/              # GraphQL resolvers
│   ├── services/               # Business logic
│   └── database/               # Database models
└── frontend/
    ├── src/
    │   ├── pages/              # Page components
    │   ├── components/         # Reusable components
    │   ├── contexts/           # React contexts
    │   └── apollo-client.ts    # GraphQL client config
    └── package.json
```

## Next Steps

- Check out `DEVELOPMENT_PLAN.md` for the full development roadmap
- See `README.md` for detailed project information
- Explore the GraphQL schema in the backend code
