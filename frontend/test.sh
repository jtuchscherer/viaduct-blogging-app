#!/bin/bash

# Frontend verification script for Viaduct Blogging App.
# Runs all standalone checks in order: TypeScript type-check, ESLint, Vitest unit tests.
# Playwright e2e tests are not included — they require live servers and are run by ../e2e.sh.

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Run from the frontend directory regardless of where the script was invoked from.
cd "$(dirname "$0")"

PASSED=0
FAILED=0
FAILED_STEPS=()

print_header() {
    echo ""
    echo -e "${BLUE}======================================${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}======================================${NC}"
    echo ""
}

run_step() {
    local label="$1"
    shift
    echo -e "${YELLOW}▶ $label${NC}"
    if "$@"; then
        echo -e "${GREEN}✓ $label passed${NC}"
        PASSED=$((PASSED + 1))
    else
        echo -e "${RED}✗ $label failed${NC}"
        FAILED=$((FAILED + 1))
        FAILED_STEPS+=("$label")
    fi
    echo ""
}

print_header "Frontend checks"

run_step "TypeScript type-check" npm run typecheck
run_step "ESLint"                npm run lint
run_step "Vitest unit tests"     npm run test -- --run

# ── Summary ──────────────────────────────────────────────────────────────────

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  Summary${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""
echo -e "${GREEN}Passed: $PASSED${NC}"

if [ "$FAILED" -gt 0 ]; then
    echo -e "${RED}Failed: $FAILED${NC}"
    for step in "${FAILED_STEPS[@]}"; do
        echo -e "${RED}  ✗ $step${NC}"
    done
    echo ""
    exit 1
else
    echo -e "${GREEN}Failed: 0${NC}"
    echo ""
    echo -e "${GREEN}🎉 All frontend checks passed${NC}"
    echo ""
fi
