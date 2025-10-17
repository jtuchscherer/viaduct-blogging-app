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
    echo ""
    echo -e "${YELLOW}🛑 Shutting down services...${NC}"

    # Kill all background jobs
    jobs -p | xargs -r kill 2>/dev/null || true

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

# Start the backend servers
echo -e "${BLUE}🌐 Starting GraphQL server (port 8080)...${NC}"
./gradlew run > graphql-server.log 2>&1 &
GRAPHQL_PID=$!
echo -e "${GREEN}✅ GraphQL server starting (PID: $GRAPHQL_PID)${NC}"

echo -e "${BLUE}🔐 Starting Auth server (port 8081)...${NC}"
java -cp build/libs/viaduct-blogs-1.0-SNAPSHOT.jar com.example.AuthServerKt > auth-server.log 2>&1 &
AUTH_PID=$!
echo -e "${GREEN}✅ Auth server starting (PID: $AUTH_PID)${NC}"

# Wait for servers to be ready
echo ""
echo -e "${YELLOW}⏳ Waiting for servers to be ready...${NC}"
sleep 5

# Check if servers are responding
if curl -s http://localhost:8080/graphql > /dev/null 2>&1; then
    echo -e "${GREEN}✅ GraphQL server is ready at http://localhost:8080/graphql${NC}"
else
    echo -e "${YELLOW}⚠️  GraphQL server may still be starting up...${NC}"
fi

if curl -s http://localhost:8081/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Auth server is ready at http://localhost:8081${NC}"
else
    echo -e "${YELLOW}⚠️  Auth server may still be starting up...${NC}"
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
echo "  📊 GraphQL API:    http://localhost:8080/graphql"
echo "  🔐 Auth API:       http://localhost:8081"
echo "  ⚛️  Frontend:       http://localhost:5173"
echo ""
echo -e "${BLUE}Logs:${NC}"
echo "  GraphQL: tail -f graphql-server.log"
echo "  Auth:    tail -f auth-server.log"
echo "  Frontend: tail -f frontend-dev.log"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# Wait for all background jobs
wait
