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

    # Only stop Ollama if we started it ourselves
    [ -n "$OLLAMA_PID" ] && kill "$OLLAMA_PID" 2>/dev/null || true

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

if ! command -v ollama &> /dev/null; then
    echo -e "${YELLOW}⚠️  Ollama is not installed — AI features will be disabled.${NC}"
    echo "     Install from https://ollama.com to enable rephrase and suggestions."
    OLLAMA_AVAILABLE=false
else
    OLLAMA_AVAILABLE=true
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

# Start Ollama if available
if [ "$OLLAMA_AVAILABLE" = true ]; then
    echo -e "${BLUE}🤖 Starting Ollama...${NC}"
    CHAT_MODEL="${OLLAMA_CHAT_MODEL:-llama3.2}"
    EMBED_MODEL="${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"

    if curl -sf http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Ollama already running${NC}"
        OLLAMA_PID=""
    else
        ollama serve > ollama.log 2>&1 &
        OLLAMA_PID=$!
        echo -e "${GREEN}✅ Ollama starting (PID: $OLLAMA_PID)${NC}"
        echo -e "${YELLOW}⏳ Waiting for Ollama to be ready...${NC}"
        for i in $(seq 1 15); do
            if curl -sf http://localhost:11434/api/tags > /dev/null 2>&1; then
                echo -e "${GREEN}✅ Ollama is ready${NC}"
                break
            fi
            if [ "$i" -eq 15 ]; then
                echo -e "${YELLOW}⚠️  Ollama did not respond in time — AI features may be unavailable${NC}"
            fi
            sleep 2
        done
    fi

    pull_model() {
        local model="$1"
        if ollama list 2>/dev/null | grep -q "^${model}"; then
            echo -e "${GREEN}✅ Model ${model} already present${NC}"
        else
            echo -e "${YELLOW}⏳ Pulling ${model} (first-run download, may take a few minutes)...${NC}"
            ollama pull "${model}"
            echo -e "${GREEN}✅ Model ${model} ready${NC}"
        fi
    }

    pull_model "${CHAT_MODEL}"
    pull_model "${EMBED_MODEL}"
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
for i in $(seq 1 30); do
    if curl -sf http://localhost:8080/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Server is ready at http://localhost:8080${NC}"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo -e "${YELLOW}⚠️  Server did not respond after 60s — check server.log${NC}"
    fi
    sleep 2
done

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
echo "     - Auth endpoints:         http://localhost:8080/auth/*"
echo "     - Health endpoint:        http://localhost:8080/health"
echo "     - AI health endpoint:     http://localhost:8080/health/ai"
echo "     - Metrics endpoint:       http://localhost:8080/metrics"
echo "  ⚛️  Frontend:                 http://localhost:5173"
if [ "$OLLAMA_AVAILABLE" = true ]; then
echo "  🤖 Ollama:                   http://localhost:11434"
fi
echo ""
echo -e "${BLUE}Logs:${NC}"
echo "  Server:   tail -f server.log"
echo "  Frontend: tail -f frontend-dev.log"
if [ "$OLLAMA_AVAILABLE" = true ] && [ -n "$OLLAMA_PID" ]; then
echo "  Ollama:   tail -f ollama.log"
fi
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

# Wait for all background jobs
wait
