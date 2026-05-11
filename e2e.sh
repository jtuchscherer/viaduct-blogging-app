#!/bin/bash

# End-to-End Browser Test Script for Viaduct Blogging App
# Starts backend + frontend, runs Playwright tests, then cleans up.

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Override via env vars to run alongside other test suites in parallel:
#   GRAPHQL_PORT=8082 FRONTEND_PORT=5174 DB_FILE=blog-e2e.db ./e2e.sh
GRAPHQL_PORT=${GRAPHQL_PORT:-8082}
FRONTEND_PORT=${FRONTEND_PORT:-5174}
DB_FILE=${DB_FILE:-blog-e2e.db}

SERVER_PID=""
FRONTEND_PID=""
SERVER_LOG="/tmp/viaduct-server-e2e-${GRAPHQL_PORT}.log"
FRONTEND_LOG="/tmp/viaduct-frontend-e2e-${FRONTEND_PORT}.log"

cleanup() {
    echo ""
    echo -e "${YELLOW}Shutting down services...${NC}"
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    [ -n "$SERVER_PID" ]   && kill "$SERVER_PID"   2>/dev/null || true
    rm -f "${DB_FILE}"
    echo -e "${GREEN}Cleanup complete${NC}"
}

trap cleanup EXIT

wait_for_url() {
    local url=$1
    local label=$2
    local max_attempts=30
    local attempt=0
    echo -e "${YELLOW}Waiting for $label...${NC}"
    until curl -s "$url" > /dev/null 2>&1; do
        attempt=$((attempt + 1))
        if [ "$attempt" -ge "$max_attempts" ]; then
            echo -e "${RED}✗ $label did not start in time${NC}"
            return 1
        fi
        sleep 2
    done
    echo -e "${GREEN}✓ $label is ready${NC}"
}

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Viaduct Browser E2E Tests${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# --- Clean slate ---
rm -f "${DB_FILE}"
lsof -ti:"${GRAPHQL_PORT}" | xargs kill -9 2>/dev/null || true
lsof -ti:"${FRONTEND_PORT}" | xargs kill -9 2>/dev/null || true
sleep 1

# --- Build backend ---
echo -e "${BLUE}Building backend...${NC}"
./gradlew build -x test > /dev/null 2>&1
echo -e "${GREEN}✓ Backend built${NC}"

# --- Install frontend deps if needed ---
if [ ! -d "frontend/node_modules" ]; then
    echo -e "${BLUE}Installing frontend dependencies...${NC}"
    cd frontend && npm install && cd ..
fi

# --- Install Playwright browsers if needed ---
if [ ! -d "$(npx --prefix frontend playwright --version > /dev/null 2>&1; echo ok)" ]; then
    echo -e "${BLUE}Installing Playwright browsers...${NC}"
    cd frontend && npx playwright install --with-deps chromium firefox webkit 2>/dev/null || npx playwright install && cd ..
fi

# --- Start backend ---
echo -e "${BLUE}Starting backend server...${NC}"
GRAPHQL_PORT="${GRAPHQL_PORT}" \
  CORS_ORIGIN="localhost:${FRONTEND_PORT}" \
  DATABASE_URL="jdbc:sqlite:${PWD}/${DB_FILE}" \
  ./gradlew run > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!
wait_for_url "http://localhost:${GRAPHQL_PORT}/health" "Backend (port ${GRAPHQL_PORT})"

# --- Seed E2E admin user ---
echo -e "${BLUE}Seeding E2E admin user...${NC}"
curl -s -X POST "http://localhost:${GRAPHQL_PORT}/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"e2e_admin","email":"e2e_admin@test.com","name":"E2E Admin","password":"e2eAdminPass1"}' > /dev/null
sqlite3 "${DB_FILE}" "UPDATE users SET is_admin = 1 WHERE username = 'e2e_admin';"
echo -e "${GREEN}✓ Admin user seeded${NC}"

# --- Start frontend ---
echo -e "${BLUE}Starting frontend dev server...${NC}"
cd frontend && VITE_API_URL="http://localhost:${GRAPHQL_PORT}" npm run dev -- --port "${FRONTEND_PORT}" > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
wait_for_url "http://localhost:${FRONTEND_PORT}" "Frontend (port ${FRONTEND_PORT})"

echo ""
echo -e "${BLUE}Running Playwright tests...${NC}"
echo ""

# Run tests; capture exit code without triggering set -e
cd frontend
set +e
API_URL="http://127.0.0.1:${GRAPHQL_PORT}" FRONTEND_URL="http://localhost:${FRONTEND_PORT}" npx playwright test "$@"
TEST_EXIT=$?
set -e
cd ..

echo ""
if [ $TEST_EXIT -eq 0 ]; then
    echo -e "${GREEN}✓ All browser E2E tests passed${NC}"
else
    echo -e "${RED}✗ Some browser E2E tests failed (exit code: $TEST_EXIT)${NC}"
    echo "  Server log:   $SERVER_LOG"
    echo "  Frontend log: $FRONTEND_LOG"
    echo "  Playwright report: frontend/playwright-report/index.html"
fi

exit $TEST_EXIT
