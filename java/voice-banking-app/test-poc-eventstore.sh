#!/bin/bash

# Integration Test Script for POC Event Store
# Tests the H2-backed event persistence endpoints
# Reuses patterns from java/test-deployed-service-enhanced.sh
# Created: 2026-03-10

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL — override with env var or first arg
BASE_URL="${1:-${POC_BASE_URL:-http://localhost:8080}}"

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0

# --------------------------------------------------------
# Helper functions (adapted from test-deployed-service-enhanced.sh)
# --------------------------------------------------------

print_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Test a GET endpoint
test_get() {
    local description=$1
    local url=$2
    local expected_code=${3:-200}

    echo ""
    echo -e "${YELLOW}Testing: $description${NC}"
    echo "GET $url"

    response=$(curl -s -w "\n%{http_code}" "$url")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}✓ PASSED${NC} (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        if command -v jq &> /dev/null && echo "$body" | jq -e . >/dev/null 2>&1; then
            echo "$body" | jq -C '.' | head -c 800
            echo ""
        else
            echo "$body" | head -c 800
            echo ""
        fi
    else
        echo -e "${RED}✗ FAILED${NC} (Expected $expected_code, got $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo "Response: $body" | head -c 500
        echo ""
    fi
    # Store body for caller
    LAST_BODY="$body"
}

# Test a POST endpoint
test_post() {
    local description=$1
    local url=$2
    local payload=$3
    local expected_code=${4:-201}

    echo ""
    echo -e "${YELLOW}Testing: $description${NC}"
    echo "POST $url"

    response=$(curl -s -w "\n%{http_code}" -X POST "$url" \
        -H "Content-Type: application/json" \
        -d "$payload")
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}✓ PASSED${NC} (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        if command -v jq &> /dev/null && echo "$body" | jq -e . >/dev/null 2>&1; then
            echo "$body" | jq -C '.' | head -c 800
            echo ""
        else
            echo "$body" | head -c 800
            echo ""
        fi
    else
        echo -e "${RED}✗ FAILED${NC} (Expected $expected_code, got $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo "Response: $body" | head -c 500
        echo ""
    fi
    LAST_BODY="$body"
}

# Assert a JSON value via jq expression
assert_json() {
    local description=$1
    local json=$2
    local jq_expr=$3
    local expected=$4

    actual=$(echo "$json" | jq -r "$jq_expr" 2>/dev/null)

    if [ "$actual" = "$expected" ]; then
        echo -e "  ${GREEN}✓ $description${NC} ($actual)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
    else
        echo -e "  ${RED}✗ $description — expected '$expected', got '$actual'${NC}"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

# ========================================================
# Pre-flight: wait for service to be ready
# ========================================================
echo -e "${GREEN}POC Event Store — Integration Test Suite${NC}"
echo "Base URL : $BASE_URL"
echo "Timestamp: $(date)"

# Unique run ID so the script is idempotent against a persistent DB
RUN_ID="run-$(date +%s)"
echo "Run ID   : $RUN_ID"
echo ""

echo -e "${YELLOW}Waiting for service to be ready...${NC}"
for i in $(seq 1 30); do
    if curl -sf "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${GREEN}Service is up!${NC}"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo -e "${RED}Service did not become ready within 30 seconds.${NC}"
        exit 1
    fi
    sleep 1
done

# Record event count before this run so assertions are relative
BASELINE=$(curl -s "$BASE_URL/api/poc/events" | jq 'length')
echo -e "${BLUE}Baseline event count: $BASELINE${NC}"

# ========================================================
# 1. Health Check
# ========================================================
print_section "1 — Health Check"
test_get "Actuator health" "$BASE_URL/actuator/health"

# ========================================================
# 2. Write Events
# ========================================================
print_section "2 — Write Events (POST /api/poc/events)"

test_post "Write audit event" \
    "$BASE_URL/api/poc/events" \
    "{\"eventType\":\"audit-${RUN_ID}\",\"payload\":\"{\\\"action\\\":\\\"balance_inquiry\\\",\\\"accountId\\\":\\\"acc-001\\\",\\\"outcome\\\":\\\"success\\\"}\",\"metadata\":\"{\\\"source\\\":\\\"branch-search-tool\\\",\\\"agentId\\\":\\\"ces-agent\\\"}\"}"

AUDIT_ID=$(echo "$LAST_BODY" | jq -r '.id // empty')
echo -e "  ${BLUE}Captured audit event id: $AUDIT_ID${NC}"

test_post "Write metric event" \
    "$BASE_URL/api/poc/events" \
    "{\"eventType\":\"metric-${RUN_ID}\",\"payload\":\"{\\\"toolCallLatencyMs\\\":142,\\\"tool\\\":\\\"searchBranches\\\"}\",\"metadata\":\"{\\\"region\\\":\\\"europe-west1\\\"}\"}"

METRIC_ID=$(echo "$LAST_BODY" | jq -r '.id // empty')

test_post "Write log event" \
    "$BASE_URL/api/poc/events" \
    "{\"eventType\":\"log-${RUN_ID}\",\"payload\":\"{\\\"level\\\":\\\"warn\\\",\\\"message\\\":\\\"Upstream timeout after 3000ms\\\"}\",\"metadata\":null}"

test_post "Write second audit event" \
    "$BASE_URL/api/poc/events" \
    "{\"eventType\":\"audit-${RUN_ID}\",\"payload\":\"{\\\"action\\\":\\\"transfer\\\",\\\"from\\\":\\\"acc-001\\\",\\\"to\\\":\\\"acc-002\\\",\\\"amount\\\":250}\",\"metadata\":null}"

# ========================================================
# 3. Validate Written Events
# ========================================================
print_section "3 — Validate Written Event Fields"

# Verify total count grew by 4
test_get "Read all events" "$BASE_URL/api/poc/events"
ALL_EVENTS="$LAST_BODY"
EXPECTED_TOTAL=$((BASELINE + 4))

assert_json "Total event count is baseline+4" "$ALL_EVENTS" 'length' "$EXPECTED_TOTAL"

# ========================================================
# 4. Filter by Event Type (run-scoped)
# ========================================================
print_section "4 — Filter by Event Type"

test_get "Filter by type=audit-${RUN_ID}" "$BASE_URL/api/poc/events?eventType=audit-${RUN_ID}"
assert_json "Audit count is 2" "$LAST_BODY" 'length' "2"

test_get "Filter by type=metric-${RUN_ID}" "$BASE_URL/api/poc/events?eventType=metric-${RUN_ID}"
assert_json "Metric count is 1" "$LAST_BODY" 'length' "1"

test_get "Filter by type=log-${RUN_ID}" "$BASE_URL/api/poc/events?eventType=log-${RUN_ID}"
assert_json "Log count is 1" "$LAST_BODY" 'length' "1"

test_get "Filter by nonexistent type" "$BASE_URL/api/poc/events?eventType=nonexistent-${RUN_ID}"
assert_json "No results for unknown type" "$LAST_BODY" 'length' "0"

# ========================================================
# 5. Filter by Time Range
# ========================================================
print_section "5 — Filter by Time Range"

YESTERDAY=$(date -u -v-1d +"%Y-%m-%dT00:00:00Z" 2>/dev/null || date -u -d "1 day ago" +"%Y-%m-%dT00:00:00Z")
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT23:59:59Z" 2>/dev/null || date -u -d "1 day" +"%Y-%m-%dT23:59:59Z")

# Date range + run-scoped type to be precise
test_get "Filter audit-${RUN_ID} by date range" \
    "$BASE_URL/api/poc/events?eventType=audit-${RUN_ID}&from=${YESTERDAY}&to=${TOMORROW}"
assert_json "Date-range audit returns 2" "$LAST_BODY" 'length' "2"

# Future range should return nothing
FUTURE_FROM="2099-01-01T00:00:00Z"
FUTURE_TO="2099-12-31T23:59:59Z"
test_get "Filter by future date range (expect 0)" \
    "$BASE_URL/api/poc/events?from=${FUTURE_FROM}&to=${FUTURE_TO}"
assert_json "Future range returns 0" "$LAST_BODY" 'length' "0"

# ========================================================
# 6. Combined Filters (type + time range)
# ========================================================
print_section "6 — Combined Filters (type + time range)"

test_get "Filter metric-${RUN_ID} + date range" \
    "$BASE_URL/api/poc/events?eventType=metric-${RUN_ID}&from=${YESTERDAY}&to=${TOMORROW}"
assert_json "Combined filter returns 1 metric event" "$LAST_BODY" 'length' "1"

# ========================================================
# 7. Validation — Bad Requests
# ========================================================
print_section "7 — Input Validation (expect 400)"

test_post "Missing eventType" \
    "$BASE_URL/api/poc/events" \
    '{"payload":"{\"data\":1}"}' \
    400

test_post "Missing payload" \
    "$BASE_URL/api/poc/events" \
    '{"eventType":"audit"}' \
    400

test_post "Empty eventType" \
    "$BASE_URL/api/poc/events" \
    '{"eventType":"","payload":"{}"}' \
    400

# ========================================================
# 8. H2 Console Availability
# ========================================================
print_section "8 — H2 Console Availability"

test_get "H2 console endpoint (302 redirect expected)" "$BASE_URL/h2-console" 302

# ========================================================
# Summary
# ========================================================
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Tests Passed: $TESTS_PASSED${NC}"
if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Tests Failed: $TESTS_FAILED${NC}"
else
    echo -e "${GREEN}Tests Failed: $TESTS_FAILED${NC}"
fi
TOTAL=$((TESTS_PASSED + TESTS_FAILED))
echo "Total      : $TOTAL"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All POC Event Store tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed — see details above${NC}"
    exit 1
fi
