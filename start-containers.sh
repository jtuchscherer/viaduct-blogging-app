#!/bin/bash

# Viaduct Blogging App — Podman Startup Script
# Builds the Gradle distribution and starts both backend and frontend via Podman Compose.

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
    # Stop the backgrounded `compose up` first. Otherwise it is still creating
    # containers while `down` runs, wins the race, and leaves an orphaned stack
    # running after this script has reported everything stopped.
    if [ -n "${COMPOSE_PID:-}" ] && kill -0 "$COMPOSE_PID" 2>/dev/null; then
        kill "$COMPOSE_PID" 2>/dev/null || true
        wait "$COMPOSE_PID" 2>/dev/null || true
    fi
    podman compose down 2>/dev/null || true
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
}

trap cleanup SIGINT SIGTERM EXIT

# --- Prerequisites ---

echo -e "${BLUE}Checking prerequisites...${NC}"

if ! command -v podman &>/dev/null; then
    echo -e "${RED}Podman is not installed or not in PATH.${NC}"
    exit 1
fi

if ! podman machine list 2>/dev/null | grep -q "Currently running"; then
    echo -e "${YELLOW}Podman machine is not running. Starting it...${NC}"
    podman machine start
    if ! podman machine list 2>/dev/null | grep -q "Currently running"; then
        echo -e "${RED}Failed to start Podman machine.${NC}"
        exit 1
    fi
    echo -e "${GREEN}Podman machine started${NC}"
fi

if [ ! -f ".env" ]; then
    echo -e "${RED}Missing .env file. Copy .env.example and fill in your values:${NC}"
    echo "  cp .env.example .env"
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

# Pull the remote base images first, as a separate step. On a cold machine this
# downloads ~1.5 GB for Ollama alone, which must not sit inside the backend health
# timeout below — otherwise the script gives up mid-download and tears down its own
# progress. Runs in the foreground so the download progress is visible.
echo -e "${BLUE}Pulling base images (first run downloads ~1.5 GB for Ollama — several minutes)...${NC}"
podman compose pull postgres ollama
echo -e "${GREEN}Base images ready${NC}"
echo ""

echo -e "${BLUE}Starting containers...${NC}"
podman compose up --build > podman-compose.log 2>&1 &
COMPOSE_PID=$!
echo -e "${GREEN}Containers started (PID: $COMPOSE_PID)${NC}"
echo ""

# --- Wait for backend ---

# Budget covers building the app/frontend images plus Postgres init and JVM start.
# Progress is printed periodically so a slow build is distinguishable from a hang.
BACKEND_TIMEOUT_SECS=${BACKEND_TIMEOUT_SECS:-600}
BACKEND_ATTEMPTS=$((BACKEND_TIMEOUT_SECS / 2))
echo -e "${YELLOW}Waiting for backend to be ready (up to ${BACKEND_TIMEOUT_SECS}s while images build)...${NC}"
for i in $(seq 1 "$BACKEND_ATTEMPTS"); do
    if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
        echo -e "${GREEN}Backend is ready${NC}"
        break
    fi
    # Fail fast if compose itself died rather than waiting out the whole budget.
    if ! kill -0 "$COMPOSE_PID" 2>/dev/null; then
        echo -e "${RED}Compose exited before the backend became healthy. Check logs:${NC}"
        echo "  tail -50 podman-compose.log"
        exit 1
    fi
    if [ $((i % 15)) -eq 0 ]; then
        echo -e "${YELLOW}  still waiting... ($((i * 2))s elapsed)${NC}"
    fi
    if [ "$i" -eq "$BACKEND_ATTEMPTS" ]; then
        echo -e "${RED}Backend did not become healthy after ${BACKEND_TIMEOUT_SECS}s. Check logs:${NC}"
        echo "  podman compose logs app"
        exit 1
    fi
    sleep 2
done

# --- Pull Ollama models (skip if already present) ---

echo -e "${BLUE}Ensuring Ollama models are available...${NC}"
CHAT_MODEL="${OLLAMA_CHAT_MODEL:-llama3.2}"
EMBED_MODEL="${OLLAMA_EMBEDDING_MODEL:-nomic-embed-text}"

# Wait for the Ollama container itself to be responsive before pulling models.
# On first run the container image may still be loading even after the backend is up.
echo -e "${YELLOW}Waiting for Ollama container to be ready...${NC}"
for i in $(seq 1 30); do
    if podman compose exec -T ollama ollama list >/dev/null 2>&1; then
        echo -e "${GREEN}Ollama is ready${NC}"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo -e "${YELLOW}Ollama not yet ready — AI features will be unavailable until it finishes starting.${NC}"
        echo -e "${YELLOW}Check progress with: podman compose logs ollama${NC}"
        echo ""
        # Don't exit — backend and frontend are already running fine without Ollama
        break
    fi
    sleep 2
done

pull_model() {
    local model="$1"
    if podman compose exec -T ollama ollama list 2>/dev/null | grep -q "^${model}"; then
        echo -e "${GREEN}Model ${model} already present${NC}"
    else
        echo -e "${YELLOW}Pulling ${model} (first-run download, may take a few minutes)...${NC}"
        podman compose exec -T ollama ollama pull "${model}"
        echo -e "${GREEN}Model ${model} ready${NC}"
    fi
}

pull_model "${CHAT_MODEL}"
pull_model "${EMBED_MODEL}"
echo ""

# --- Seed database ---

echo -e "${BLUE}Seeding database...${NC}"
# Load credentials from .env so the seed script can connect to the postgres container
set -a; source .env; set +a
PGHOST=localhost PGPORT=5432 \
    PGDATABASE="${POSTGRES_DB}" \
    PGUSER="${POSTGRES_USER}" \
    PGPASSWORD="${POSTGRES_PASSWORD}" \
    ./seed-database.sh || true
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
        echo "  podman compose logs frontend"
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
echo "    GraphiQL:        http://localhost:8080/graphiql"
echo "    Health:          http://localhost:8080/health"
echo "    AI Health:       http://localhost:8080/health/ai"
echo "    Metrics:         http://localhost:8080/metrics"
echo "  Ollama:            http://localhost:11434"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo "  tail -f podman-compose.log       # all container logs"
echo "  podman compose logs -f app       # backend logs only"
echo "  podman compose logs -f frontend  # frontend logs only"
echo ""
echo -e "${YELLOW}Press Ctrl+C to stop all services${NC}"
echo ""

wait
