#!/bin/bash

# Viaduct Blogs Startup Script
# This script starts all necessary services for local development

set -e

echo "🚀 Starting Viaduct Blogs Application..."
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to cleanup background processes on exit
cleanup() {
    trap - SIGINT SIGTERM EXIT  # prevent re-entrant calls
    echo ""
    echo -e "${YELLOW}🛑 Shutting down services...${NC}"

    # Kill entire process groups so child processes (JVM, node) are also terminated
    [ -n "$SERVER_PID" ] && kill -- -$SERVER_PID 2>/dev/null || true
    [ -n "$FRONTEND_PID" ] && kill -- -$FRONTEND_PID 2>/dev/null || true

    # Fallback: kill anything still holding the ports
    lsof -ti :8080 | xargs kill 2>/dev/null || true
    lsof -ti :5173 | xargs kill 2>/dev/null || true

    echo -e "${GREEN}✅ All services stopped${NC}"
    exit 0
}

# Set up trap to catch Ctrl+C and other termination signals
trap cleanup SIGINT SIGTERM EXIT

# Check if required tools are installed
echo -e "${BLUE}Checking prerequisites...${NC}"

if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 11 or higher."
    exit 1
fi

if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed. Please install Node.js and npm."
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites OK${NC}"
echo ""

# Build the backend
echo -e "${BLUE}📦 Building backend...${NC}"
./gradlew build -x test
echo -e "${GREEN}✅ Backend built successfully${NC}"
echo ""

# Install frontend dependencies if needed
if [ ! -d "frontend/node_modules" ]; then
    echo -e "${BLUE}📦 Installing frontend dependencies...${NC}"
    cd frontend
    npm install
    cd ..
    echo -e "${GREEN}✅ Frontend dependencies installed${NC}"
    echo ""
fi

# Start the backend server
echo -e "${BLUE}🌐 Starting server (GraphQL + Auth on port 8080)...${NC}"
./gradlew run > server.log 2>&1 &
SERVER_PID=$!
echo -e "${GREEN}✅ Server starting (PID: $SERVER_PID)${NC}"

# Wait for server to be ready
echo ""
echo -e "${YELLOW}⏳ Waiting for server to be ready...${NC}"
sleep 5

# Check if server is responding
if curl -s http://localhost:8080/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Server is ready at http://localhost:8080${NC}"
else
    echo -e "${YELLOW}⚠️  Server may still be starting up...${NC}"
fi

# Start the frontend dev server
echo ""
echo -e "${BLUE}⚛️  Starting frontend dev server (port 5173)...${NC}"
cd frontend
npm run dev > ../frontend-dev.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo -e "${GREEN}✅ Frontend dev server starting (PID: $FRONTEND_PID)${NC}"

echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}🎉 All services started successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}Services running:${NC}"
echo "  🌐 Backend (GraphQL + Auth): http://localhost:8080"
echo "     - GraphQL endpoint:       http://localhost:8080/graphql"
echo "     - GraphiQL endpoint:      http://localhost:8080/graphiql?path=/graphql"
echo "     - Auth endpoints:         http://localhost:8080/auth/*
     - Health endpoint:        http://localhost:8080/health
     - Metrics endpoint:       http://localhost:8080/metrics"
echo "  ⚛️  Frontend:                 http://localhost:5173"
echo ""
echo -e "${BLUE}Logs:${NC}"
echo "  Server:   tail -f server.log"
echo "  Frontend: tail -f frontend-dev.log"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# Wait for all background jobs
wait
