#!/bin/bash

# Detects unintended changes to the public GraphQL contract.
#
# Starts the backend, introspects the schema clients actually see, normalises it to a sorted
# type/field listing, and diffs that against the committed snapshot. Source .graphqls files are
# not a sufficient check on their own: the effective contract also depends on @scope filtering
# and on how module partitions merge, so this asserts on the merged, scope-filtered result.
#
# Usage:
#   ./schema-check.sh            # verify the snapshot is current (CI mode — fails on drift)
#   ./schema-check.sh --update   # rewrite the snapshot after an intentional schema change

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/test-lib.sh"

SNAPSHOT=${SNAPSHOT:-schema/public-schema.snapshot}
UPDATE=false
[ "${1:-}" = "--update" ] && UPDATE=true

RUN_ID=$$
GRAPHQL_PORT=${GRAPHQL_PORT:-$(find_free_port $((8700 + RUN_ID % 200)))}
DB_FILE=${DB_FILE:-blog-schema-check-${RUN_ID}.db}
SERVER_LOG=${SERVER_LOG:-/tmp/viaduct-schema-check-${RUN_ID}.log}
SERVER_PID=""

cleanup() {
    [ -n "$SERVER_PID" ] && kill "$SERVER_PID" 2>/dev/null || true
    rm -f "${DB_FILE}"
}
trap cleanup EXIT

if port_in_use "${GRAPHQL_PORT}"; then
    echo -e "${RED}✗ Port ${GRAPHQL_PORT} is already in use${NC}"
    exit 1
fi

echo -e "${YELLOW}Building and starting backend on port ${GRAPHQL_PORT}...${NC}"
./gradlew installDist -x test -q
GRAPHQL_PORT="${GRAPHQL_PORT}" DATABASE_URL="jdbc:sqlite:${PWD}/${DB_FILE}" \
    ./gradlew run > "$SERVER_LOG" 2>&1 &
SERVER_PID=$!

for i in $(seq 1 60); do
    curl -sf "http://localhost:${GRAPHQL_PORT}/health" > /dev/null 2>&1 && break
    if [ "$i" -eq 60 ]; then
        echo -e "${RED}✗ Backend did not start. See ${SERVER_LOG}${NC}"
        exit 1
    fi
    sleep 2
done
echo -e "${GREEN}Backend ready${NC}"

# Introspect and normalise. Sorting makes the snapshot stable against field-ordering churn.
ACTUAL=$(curl -s -X POST "http://localhost:${GRAPHQL_PORT}/graphql" \
    -H 'Content-Type: application/json' \
    -d '{"query":"{ __schema { types { name kind fields { name type { name kind ofType { name kind ofType { name kind ofType { name kind ofType { name kind } } } } } } inputFields { name } enumValues { name } } } }"}' \
  | python3 "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/schema-normalize.py")

if [ -z "$ACTUAL" ]; then
    echo -e "${RED}✗ Introspection returned nothing${NC}"
    exit 1
fi

if [ "$UPDATE" = true ]; then
    mkdir -p "$(dirname "$SNAPSHOT")"
    printf '%s\n' "$ACTUAL" > "$SNAPSHOT"
    echo -e "${GREEN}✓ Snapshot updated: ${SNAPSHOT} ($(wc -l < "$SNAPSHOT" | tr -d ' ') entries)${NC}"
    exit 0
fi

if [ ! -f "$SNAPSHOT" ]; then
    echo -e "${RED}✗ No snapshot at ${SNAPSHOT}. Create it with: ./schema-check.sh --update${NC}"
    exit 1
fi

if DIFF=$(diff -u "$SNAPSHOT" <(printf '%s\n' "$ACTUAL")); then
    echo -e "${GREEN}🎉 Public schema matches the snapshot ($(wc -l < "$SNAPSHOT" | tr -d ' ') entries)${NC}"
    exit 0
fi

echo -e "${RED}✗ The public GraphQL schema changed:${NC}"
echo "$DIFF" | sed -n '3,60p'
echo ""
echo -e "${YELLOW}Removed or retyped fields break existing clients. If this change is intended,${NC}"
echo -e "${YELLOW}re-run with --update and include the snapshot diff in your PR for review.${NC}"
exit 1
