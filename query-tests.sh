#!/bin/bash

# End-to-End Test Script for Viaduct Blogging App
# This script tests all API endpoints including authentication, posts, comments, and likes

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Configuration
AUTH_URL="http://localhost:8080"
GRAPHQL_URL="http://localhost:8080/graphql"
SERVER_LOG="/tmp/viaduct-server.log"

# Test results
TESTS_PASSED=0
TESTS_FAILED=0

# Helper functions
print_header() {
    echo -e "\n${YELLOW}========================================${NC}"
    echo -e "${YELLOW}$1${NC}"
    echo -e "${YELLOW}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
    TESTS_PASSED=$((TESTS_PASSED + 1))
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
}

print_info() {
    echo -e "ℹ $1"
}

# Cleanup function
cleanup() {
    print_header "Cleaning up..."

    # Stop the server
    if [ ! -z "$SERVER_PID" ]; then
        print_info "Stopping server (PID: $SERVER_PID)"
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
    fi

    # Kill any remaining gradle processes
    pkill -f "gradle run" 2>/dev/null || true

    # Remove database
    rm -f blog.db

    print_info "Cleanup complete"
}

# Set up trap to cleanup on exit
trap cleanup EXIT

# Start test
print_header "Viaduct Blogging App - End-to-End Test"

# Step 1: Clean database
print_header "Step 1: Clean Database"
rm -f blog.db
print_success "Database cleaned"

# Step 2: Build the application
print_header "Step 2: Build Application"
./gradlew build -x test > /dev/null 2>&1
if [ $? -eq 0 ]; then
    print_success "Application built successfully"
else
    print_error "Application build failed"
    exit 1
fi

# Step 3: Start the server
print_header "Step 3: Start Server"

# Kill any existing servers on the port
print_info "Checking for existing servers on port 8080..."
lsof -ti:8080 | xargs kill -9 2>/dev/null || true
pkill -f "gradle run" 2>/dev/null || true
sleep 2

./gradlew run > $SERVER_LOG 2>&1 &
SERVER_PID=$!
print_info "Server starting (PID: $SERVER_PID)..."

# Wait for server to start
sleep 10

# Check if server is running
if ps -p $SERVER_PID > /dev/null; then
    print_success "Server started successfully"
else
    print_error "Server failed to start"
    cat $SERVER_LOG
    exit 1
fi

# Step 4: Test Authentication
print_header "Step 4: Test Authentication"

# Register User 1
print_info "Registering user1..."
USER1_RESPONSE=$(curl -s -X POST $AUTH_URL/auth/register \
    -H "Content-Type: application/json" \
    -d '{
        "username": "alice",
        "email": "alice@example.com",
        "name": "Alice Smith",
        "password": "password123"
    }')

USER1_TOKEN=$(echo $USER1_RESPONSE | grep -o '"token":"[^"]*' | sed 's/"token":"//')
USER1_ID=$(echo $USER1_RESPONSE | grep -o '"id":"[^"]*' | grep -o '[^"]*$')

if [ ! -z "$USER1_TOKEN" ]; then
    print_success "User1 (alice) registered successfully"
else
    print_error "User1 registration failed"
    echo "Response: $USER1_RESPONSE"
fi

# Register User 2
print_info "Registering user2..."
USER2_RESPONSE=$(curl -s -X POST $AUTH_URL/auth/register \
    -H "Content-Type: application/json" \
    -d '{
        "username": "bob",
        "email": "bob@example.com",
        "name": "Bob Jones",
        "password": "password456"
    }')

USER2_TOKEN=$(echo $USER2_RESPONSE | grep -o '"token":"[^"]*' | sed 's/"token":"//')
USER2_ID=$(echo $USER2_RESPONSE | grep -o '"id":"[^"]*' | grep -o '[^"]*$')

if [ ! -z "$USER2_TOKEN" ]; then
    print_success "User2 (bob) registered successfully"
else
    print_error "User2 registration failed"
    echo "Response: $USER2_RESPONSE"
fi

# Test login
print_info "Testing login for user1..."
LOGIN_RESPONSE=$(curl -s -X POST $AUTH_URL/auth/login \
    -H "Content-Type: application/json" \
    -d '{
        "username": "alice",
        "password": "password123"
    }')

if echo $LOGIN_RESPONSE | grep -q "token"; then
    print_success "User1 login successful"
else
    print_error "User1 login failed"
fi

# Test /auth/me endpoint
print_info "Testing /auth/me endpoint..."
ME_RESPONSE=$(curl -s -X GET $AUTH_URL/auth/me \
    -H "Authorization: Bearer $USER1_TOKEN")

if echo $ME_RESPONSE | grep -q "alice"; then
    print_success "/auth/me endpoint works"
else
    print_error "/auth/me endpoint failed"
fi

# Step 5: Test Post Operations
print_header "Step 5: Test Post Operations"

# Create post by user1
print_info "Creating post by user1..."
CREATE_POST_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d '{
        "query": "mutation { createPost(input: {title: \"My First Post\", content: \"This is my first blog post!\"}) { id title content author { username } } }"
    }')

POST1_ID=$(echo $CREATE_POST_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$POST1_ID" ]; then
    print_success "Post created by user1 (ID: $POST1_ID)"
else
    print_error "Post creation failed"
    echo "Response: $CREATE_POST_RESPONSE"
fi

# Create another post by user1
print_info "Creating second post by user1..."
CREATE_POST2_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d '{
        "query": "mutation { createPost(input: {title: \"Second Post\", content: \"Another great post\"}) { id title } }"
    }')

POST2_ID=$(echo $CREATE_POST2_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$POST2_ID" ]; then
    print_success "Second post created by user1"
else
    print_error "Second post creation failed"
fi

# Create post by user2
print_info "Creating post by user2..."
CREATE_POST3_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d '{
        "query": "mutation { createPost(input: {title: \"Bob Post\", content: \"Bob is here\"}) { id title author { username } } }"
    }')

POST3_ID=$(echo $CREATE_POST3_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$POST3_ID" ]; then
    print_success "Post created by user2"
else
    print_error "Post creation by user2 failed"
fi

# Query all posts
print_info "Querying all posts..."
ALL_POSTS_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d '{
        "query": "{ posts { id title author { username } } }"
    }')

if echo $ALL_POSTS_RESPONSE | grep -q "$POST1_ID" && echo $ALL_POSTS_RESPONSE | grep -q "$POST3_ID"; then
    print_success "All posts query successful"
else
    print_error "All posts query failed"
fi

# Query myPosts for user1
print_info "Querying myPosts for user1..."
MY_POSTS_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d '{
        "query": "{ myPosts { id title } }"
    }')

if echo $MY_POSTS_RESPONSE | grep -q "$POST1_ID" && ! echo $MY_POSTS_RESPONSE | grep -q "$POST3_ID"; then
    print_success "myPosts query successful (returns only user1's posts)"
else
    print_error "myPosts query failed"
fi

# Update post
print_info "Updating post by user1..."
UPDATE_POST_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d "{
        \"query\": \"mutation { updatePost(input: {id: \\\"$POST1_ID\\\", title: \\\"Updated Title\\\"}) { id title } }\"
    }")

if echo $UPDATE_POST_RESPONSE | grep -q "Updated Title"; then
    print_success "Post updated successfully"
else
    print_error "Post update failed"
fi

# Try to update another user's post (should fail)
print_info "Testing authorization: user2 trying to update user1's post..."
UNAUTHORIZED_UPDATE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { updatePost(input: {id: \\\"$POST1_ID\\\", title: \\\"Hacked\\\"}) { id title } }\"
    }")

if echo $UNAUTHORIZED_UPDATE | grep -q "not authorized"; then
    print_success "Authorization check works (user2 cannot update user1's post)"
else
    print_error "Authorization check failed"
fi

# Step 6: Test Comment Operations
print_header "Step 6: Test Comment Operations"

# Create comment by user2 on user1's post
print_info "Creating comment by user2 on user1's post..."
CREATE_COMMENT_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { createComment(input: {postId: \\\"$POST1_ID\\\", content: \\\"Great post!\\\"}) { id content author { username } } }\"
    }")

COMMENT1_ID=$(echo $CREATE_COMMENT_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$COMMENT1_ID" ]; then
    print_success "Comment created by user2"
else
    print_error "Comment creation failed"
    echo "Response: $CREATE_COMMENT_RESPONSE"
fi

# Create another comment by user1
print_info "Creating comment by user1..."
CREATE_COMMENT2_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d "{
        \"query\": \"mutation { createComment(input: {postId: \\\"$POST1_ID\\\", content: \\\"Thanks!\\\"}) { id content } }\"
    }")

COMMENT2_ID=$(echo $CREATE_COMMENT2_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$COMMENT2_ID" ]; then
    print_success "Second comment created"
else
    print_error "Second comment creation failed"
fi

# Query post comments
print_info "Querying comments for post..."
POST_COMMENTS_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ postComments(postId: \\\"$POST1_ID\\\") { id content author { username } } }\"
    }")

if echo $POST_COMMENTS_RESPONSE | grep -q "$COMMENT1_ID" && echo $POST_COMMENTS_RESPONSE | grep -q "bob"; then
    print_success "Post comments query successful"
else
    print_error "Post comments query failed"
fi

# Delete own comment
print_info "Deleting comment by user2..."
DELETE_COMMENT_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { deleteComment(id: \\\"$COMMENT1_ID\\\") }\"
    }")

if echo $DELETE_COMMENT_RESPONSE | grep -q "true"; then
    print_success "Comment deleted successfully"
else
    print_error "Comment deletion failed"
fi

# Try to delete another user's comment (should fail)
print_info "Testing authorization: user2 trying to delete user1's comment..."
UNAUTHORIZED_DELETE_COMMENT=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { deleteComment(id: \\\"$COMMENT2_ID\\\") }\"
    }")

if echo $UNAUTHORIZED_DELETE_COMMENT | grep -q "not authorized"; then
    print_success "Authorization check works (user2 cannot delete user1's comment)"
else
    print_error "Authorization check failed for comment deletion"
fi

# Step 7: Test Like Operations
print_header "Step 7: Test Like Operations"

# User2 likes user1's post
print_info "User2 liking user1's post..."
LIKE_POST_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { likePost(postId: \\\"$POST1_ID\\\") { id } }\"
    }")

LIKE1_ID=$(echo $LIKE_POST_RESPONSE | grep -o '"id":"[^"]*' | head -1 | sed 's/"id":"//')

if [ ! -z "$LIKE1_ID" ]; then
    print_success "Post liked by user2"
else
    print_error "Like post failed"
fi

# User1 likes their own post
print_info "User1 liking their own post..."
LIKE_POST2_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d "{
        \"query\": \"mutation { likePost(postId: \\\"$POST1_ID\\\") { id } }\"
    }")

if echo $LIKE_POST2_RESPONSE | grep -q '"id"'; then
    print_success "Post liked by user1"
else
    print_error "Like post by user1 failed"
fi

# Query post with like count and isLikedByMe
print_info "Querying post with like information..."
POST_WITH_LIKES_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"{ post(id: \\\"$POST1_ID\\\") { id title likeCount isLikedByMe } }\"
    }")

if echo $POST_WITH_LIKES_RESPONSE | grep -q '"likeCount":2' && echo $POST_WITH_LIKES_RESPONSE | grep -q '"isLikedByMe":true'; then
    print_success "Post like information correct (likeCount=2, isLikedByMe=true for user2)"
else
    print_error "Post like information incorrect"
    echo "Response: $POST_WITH_LIKES_RESPONSE"
fi

# Test idempotent like (liking again should not create duplicate)
print_info "Testing idempotent like (user2 likes same post again)..."
LIKE_AGAIN_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { likePost(postId: \\\"$POST1_ID\\\") { id } }\"
    }")

# Verify like count is still 2
POST_LIKE_COUNT_CHECK=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ post(id: \\\"$POST1_ID\\\") { likeCount } }\"
    }")

if echo $POST_LIKE_COUNT_CHECK | grep -q '"likeCount":2'; then
    print_success "Idempotent like works (like count still 2)"
else
    print_error "Idempotent like failed"
fi

# Unlike post
print_info "User2 unliking post..."
UNLIKE_POST_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER2_TOKEN" \
    -d "{
        \"query\": \"mutation { unlikePost(postId: \\\"$POST1_ID\\\") }\"
    }")

if echo $UNLIKE_POST_RESPONSE | grep -q "true"; then
    print_success "Post unliked successfully"
else
    print_error "Unlike post failed"
fi

# Verify like count decreased
POST_LIKE_COUNT_AFTER_UNLIKE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ post(id: \\\"$POST1_ID\\\") { likeCount } }\"
    }")

if echo $POST_LIKE_COUNT_AFTER_UNLIKE | grep -q '"likeCount":1'; then
    print_success "Like count decreased after unlike (now 1)"
else
    print_error "Like count did not decrease properly"
fi

# Step 8: Test Post with nested queries
print_header "Step 8: Test Nested Queries"

print_info "Querying post with author, comments, and likes..."
NESTED_QUERY_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d "{
        \"query\": \"{ post(id: \\\"$POST1_ID\\\") { id title author { username email } comments { id content author { username } } likes { id user { username } } likeCount isLikedByMe } }\"
    }")

if echo $NESTED_QUERY_RESPONSE | grep -q "alice" && echo $NESTED_QUERY_RESPONSE | grep -q "Thanks"; then
    print_success "Nested query successful (includes author, comments, likes)"
else
    print_error "Nested query failed"
    echo "Response: $NESTED_QUERY_RESPONSE"
fi

# Step 9: Test Delete Post
print_header "Step 9: Test Delete Post"

# Delete post by user1
print_info "Deleting second post by user1..."
DELETE_POST_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $USER1_TOKEN" \
    -d "{
        \"query\": \"mutation { deletePost(id: \\\"$POST2_ID\\\") }\"
    }")

if echo $DELETE_POST_RESPONSE | grep -q "true"; then
    print_success "Post deleted successfully"
else
    print_error "Post deletion failed"
fi

# Verify post is deleted
VERIFY_DELETE_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ post(id: \\\"$POST2_ID\\\") { id } }\"
    }")

if echo $VERIFY_DELETE_RESPONSE | grep -q "null"; then
    print_success "Post deletion verified (post not found)"
else
    print_error "Post deletion verification failed"
fi

# Step 10: Test Pagination (postsConnection)
print_header "Step 10: Test Cursor Pagination (postsConnection)"

# At this point 2 posts remain: POST1 (Alice's "Updated Title") and POST3 (Bob's "Bob Post")

# Basic postsConnection query
print_info "Querying postsConnection (no args)..."
CONN_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ postsConnection { totalCount pageInfo { hasNextPage hasPreviousPage endCursor } edges { cursor node { id title } } } }\"
    }")

if echo $CONN_RESPONSE | grep -q "totalCount" && echo $CONN_RESPONSE | grep -q "edges"; then
    print_success "postsConnection query returned connection structure"
else
    print_error "postsConnection query failed"
    echo "Response: $CONN_RESPONSE"
fi

if echo $CONN_RESPONSE | grep -q "pageInfo"; then
    print_success "postsConnection includes pageInfo"
else
    print_error "postsConnection missing pageInfo"
fi

# Verify totalCount = 2 (POST1 and POST3 remain after POST2 was deleted)
TOTAL_COUNT=$(echo $CONN_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['postsConnection']['totalCount'])" 2>/dev/null || echo "")
if [ "$TOTAL_COUNT" = "2" ]; then
    print_success "postsConnection totalCount=2 (correct)"
else
    print_error "postsConnection totalCount expected 2, got: $TOTAL_COUNT"
fi

# Paginated query: first=1
print_info "Querying postsConnection(first: 1)..."
CONN_PAGED_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
    -H "Content-Type: application/json" \
    -d "{
        \"query\": \"{ postsConnection(first: 1) { totalCount pageInfo { hasNextPage endCursor } edges { cursor node { id title } } } }\"
    }")

PAGE1_TITLE=$(echo $CONN_PAGED_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); edges=d['data']['postsConnection']['edges']; print(edges[0]['node']['title'] if edges else '')" 2>/dev/null || echo "")
HAS_NEXT=$(echo $CONN_PAGED_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['postsConnection']['pageInfo']['hasNextPage'])" 2>/dev/null || echo "")
END_CURSOR=$(echo $CONN_PAGED_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['postsConnection']['pageInfo']['endCursor'] or '')" 2>/dev/null || echo "")

if [ -n "$PAGE1_TITLE" ]; then
    print_success "postsConnection(first: 1) returned first post: \"$PAGE1_TITLE\""
else
    print_error "postsConnection(first: 1) returned no post title"
    echo "Response: $CONN_PAGED_RESPONSE"
fi

if [ "$HAS_NEXT" = "True" ]; then
    print_success "postsConnection(first: 1) hasNextPage=true (more posts exist)"
else
    print_error "postsConnection(first: 1) expected hasNextPage=true, got: $HAS_NEXT"
fi

if [ -n "$END_CURSOR" ]; then
    print_success "postsConnection(first: 1) returned endCursor"
else
    print_error "postsConnection(first: 1) missing endCursor"
fi

# Fetch second page using cursor and verify different content
if [ -n "$END_CURSOR" ]; then
    print_info "Querying postsConnection(first: 1, after: cursor) for second page..."
    CONN_AFTER_RESPONSE=$(curl -s -X POST $GRAPHQL_URL \
        -H "Content-Type: application/json" \
        -d "{
            \"query\": \"{ postsConnection(first: 1, after: \\\"$END_CURSOR\\\") { totalCount pageInfo { hasNextPage } edges { node { id title } } } }\"
        }")

    PAGE2_TITLE=$(echo $CONN_AFTER_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); edges=d['data']['postsConnection']['edges']; print(edges[0]['node']['title'] if edges else '')" 2>/dev/null || echo "")
    HAS_NEXT_PAGE2=$(echo $CONN_AFTER_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['postsConnection']['pageInfo']['hasNextPage'])" 2>/dev/null || echo "")

    if [ -n "$PAGE2_TITLE" ]; then
        print_success "Second page returned post: \"$PAGE2_TITLE\""
    else
        print_error "Second page returned no post"
        echo "Response: $CONN_AFTER_RESPONSE"
    fi

    if [ -n "$PAGE1_TITLE" ] && [ -n "$PAGE2_TITLE" ] && [ "$PAGE1_TITLE" != "$PAGE2_TITLE" ]; then
        print_success "Pages contain different posts (\"$PAGE1_TITLE\" vs \"$PAGE2_TITLE\")"
    else
        print_error "Pages contain same or missing content (page1=\"$PAGE1_TITLE\", page2=\"$PAGE2_TITLE\")"
    fi

    if [ "$HAS_NEXT_PAGE2" = "False" ]; then
        print_success "Second page hasNextPage=false (no more posts)"
    else
        print_error "Second page expected hasNextPage=false, got: $HAS_NEXT_PAGE2"
    fi

    # Verify totalCount is consistent across both pages
    TOTAL_COUNT_PAGE2=$(echo $CONN_AFTER_RESPONSE | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['postsConnection']['totalCount'])" 2>/dev/null || echo "")
    if [ "$TOTAL_COUNT_PAGE2" = "2" ]; then
        print_success "totalCount=2 consistent on second page"
    else
        print_error "totalCount expected 2 on second page, got: $TOTAL_COUNT_PAGE2"
    fi
else
    print_error "Skipping second page test — no cursor returned from first page"
fi

# Test Summary
print_header "Test Summary"
echo -e "${GREEN}Tests Passed: $TESTS_PASSED${NC}"
echo -e "${RED}Tests Failed: $TESTS_FAILED${NC}"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}❌ Some tests failed. Check the output above for details.${NC}"
    exit 1
fi