#!/bin/bash

# Viaduct Blogging App — Docker Startup Script
# Builds the Gradle distribution and starts both backend and frontend via Docker Compose.

set -e

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

cleanup() {
    trap - SIGINT SIGTERM EXIT
    echo ""
    echo -e "${YELLOW}Shutting down...${NC}"
    docker compose down 2>/dev/null || true
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# --- Prerequisites ---

echo -e "${BLUE}Checking prerequisites...${NC}"

if ! command -v docker &>/dev/null; then
    echo -e "${RED}Docker is not installed or not in PATH.${NC}"
    exit 1
fi

if ! docker info &>/dev/null; then
    echo -e "${RED}Docker daemon is not running. Please start Docker Desktop.${NC}"
    exit 1
fi

echo -e "${GREEN}Prerequisites OK${NC}"
echo ""

# --- Build Gradle distribution ---

echo -e "${BLUE}Building backend distribution...${NC}"
./gradlew installDist -x test --no-daemon -q
echo -e "${GREEN}Backend distribution built${NC}"
echo ""

# --- Start all containers ---

echo -e "${BLUE}Starting containers...${NC}"
docker compose up --build > docker-compose.log 2>&1 &
COMPOSE_PID=$!
echo -e "${GREEN}Containers started (PID: $COMPOSE_PID)${NC}"
echo ""

# --- Wait for backend ---

echo -e "${YELLOW}Waiting for backend to be ready...${NC}"
for i in $(seq 1 20); do
    if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
        echo -e "${GREEN}Backend is ready${NC}"
        break
    fi
    if [ "$i" -eq 20 ]; then
        echo -e "${RED}Backend did not become healthy after 40s. Check logs:${NC}"
        echo "  docker compose logs app"
        exit 1
    fi
    sleep 2
done

# --- Seed database ---

echo -e "${BLUE}Seeding database...${NC}"
./seed-database.sh data/blog.db
echo ""

# --- Wait for frontend ---

echo -e "${YELLOW}Waiting for frontend to be ready...${NC}"
for i in $(seq 1 15); do
    if curl -sf http://localhost:5173 >/dev/null 2>&1; then
        echo -e "${GREEN}Frontend is ready${NC}"
        break
    fi
    if [ "$i" -eq 15 ]; then
        echo -e "${RED}Frontend did not become ready after 30s. Check logs:${NC}"
        echo "  docker compose logs frontend"
        exit 1
    fi
    sleep 2
done

echo ""

# --- Summary ---

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}  All services running               ${NC}"
echo -e "${GREEN}======================================${NC}"
echo ""
echo -e "${BLUE}Services:${NC}"
echo "  Frontend:          http://localhost:5173"
echo "  Backend:           http://localhost:8080"
echo "    GraphQL:         http://localhost:8080/graphql"
echo "    Health:          http://localhost:8080/health"
echo "    Metrics:         http://localhost:8080/metrics"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "  tail -f docker-compose.log       # all container logs"
echo "  docker compose logs -f app       # backend logs only"
echo "  docker compose logs -f frontend  # frontend logs only"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

wait
