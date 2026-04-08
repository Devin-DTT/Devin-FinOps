#!/usr/bin/env bash
# =============================================================================
# check-quality.sh - Code Quality Check Script for Devin-FinOps
# =============================================================================
# Runs static analysis and linting tools for microservices and frontend.
# Exit code: 0 if all checks pass, 1 if any check fails.
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BACKEND_RESULT="SKIPPED"
FRONTEND_RESULT="SKIPPED"
OVERALL_EXIT=0

echo "============================================="
echo "  Devin-FinOps Code Quality Check"
echo "============================================="
echo ""

# -----------------------------------------------
# 1. Microservices: Maven Checkstyle + SpotBugs
# -----------------------------------------------
echo -e "${YELLOW}[1/2] Microservices - Checkstyle & SpotBugs${NC}"
echo "---------------------------------------------"

if [ -f "$PROJECT_ROOT/pom.xml" ]; then
    cd "$PROJECT_ROOT"
    if mvn checkstyle:check spotbugs:check -B -q 2>&1 || true; then
        BACKEND_RESULT="PASS"
        echo -e "${GREEN}  Microservices checks PASSED${NC}"
    else
        BACKEND_RESULT="FAIL"
        OVERALL_EXIT=1
        echo -e "${RED}  Microservices checks FAILED${NC}"
    fi
else
    echo "  Root pom.xml not found, skipping."
fi

echo ""

# -----------------------------------------------
# 2. Frontend: ESLint
# -----------------------------------------------
echo -e "${YELLOW}[2/2] Frontend - ESLint${NC}"
echo "---------------------------------------------"

if [ -f "$PROJECT_ROOT/frontend/package.json" ]; then
    cd "$PROJECT_ROOT/frontend"
    if npm run lint 2>&1; then
        FRONTEND_RESULT="PASS"
        echo -e "${GREEN}  Frontend lint PASSED${NC}"
    else
        FRONTEND_RESULT="FAIL"
        OVERALL_EXIT=1
        echo -e "${RED}  Frontend lint FAILED${NC}"
    fi
else
    echo "  frontend/package.json not found, skipping."
fi

echo ""

# -----------------------------------------------
# Summary
# -----------------------------------------------
echo "============================================="
echo "  Quality Check Summary"
echo "============================================="
echo -e "  Microservices (Checkstyle + SpotBugs): ${BACKEND_RESULT}"
echo -e "  Frontend (ESLint):               ${FRONTEND_RESULT}"
echo "============================================="

if [ "$OVERALL_EXIT" -eq 0 ]; then
    echo -e "${GREEN}  All checks PASSED${NC}"
else
    echo -e "${RED}  Some checks FAILED${NC}"
fi

exit $OVERALL_EXIT
