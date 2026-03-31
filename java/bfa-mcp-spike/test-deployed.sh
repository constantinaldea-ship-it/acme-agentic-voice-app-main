#!/bin/bash
# Smoke test for deployed BFA MCP Spike service.
# Tests MCP Streamable HTTP transport and protocol handshake.
#
# Usage:
#   ./test-deployed.sh [SERVICE_URL]
#
# If no URL provided, queries Cloud Run for the service URL.

set -euo pipefail

# Modified by Codex on 2026-02-12: enforce strict Streamable HTTP transport checks.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=../../scripts/lib/load-env.sh
source "${JAVA_ROOT}/../scripts/lib/load-env.sh"
load_root_env

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0
DETECTED_TRANSPORT="unknown"

# Resolve service URL
if [[ -n "${1:-}" ]]; then
  BASE_URL="$1"
elif [[ -n "${BFA_MCP_SPIKE_URL:-}" ]]; then
  BASE_URL="${BFA_MCP_SPIKE_URL}"
else
  SERVICE_NAME="${SERVICE_NAME:-bfa-mcp-spike}"
  require_gcp_run_env || exit 1
  BASE_URL=$(gcloud run services describe "${SERVICE_NAME}" \
    --project="${GCP_PROJECT_ID}" \
    --region="${GCP_REGION}" \
    --format="value(status.url)" 2>/dev/null || true)
  if [[ -z "${BASE_URL}" ]]; then
    echo -e "${RED}ERROR: Could not resolve service URL. Pass it as first arg, export BFA_MCP_SPIKE_URL, or set GCP_PROJECT_ID/GCP_REGION in the repo root .env and deploy first.${NC}"
    exit 1
  fi
fi

echo -e "${BLUE}=== BFA MCP Spike Smoke Tests ===${NC}"
echo "Service URL: ${BASE_URL}"
echo ""

pass() {
  local msg="$1"
  echo -e "  ${GREEN}✓ PASSED${NC} ${msg}"
  TESTS_PASSED=$((TESTS_PASSED + 1))
}

fail() {
  local msg="$1"
  echo -e "  ${RED}✗ FAILED${NC} ${msg}"
  TESTS_FAILED=$((TESTS_FAILED + 1))
}

test_streamable_http() {
  local payload
  local response
  local http_code
  local body

  echo -e "${YELLOW}Testing: Streamable HTTP endpoint (/mcp)${NC}"
  payload='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'

  response=$(curl -s -w "\n%{http_code}" --max-time 10 \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -d "${payload}" \
    "${BASE_URL}/mcp" 2>&1 || true)

  http_code=$(echo "$response" | tail -n1)
  body=$(echo "$response" | sed '$d')

  if [[ "$http_code" == "200" ]]; then
    pass "Streamable HTTP initialize accepted (HTTP 200)"
    if [[ -n "$body" ]]; then
      echo "  Response: ${body:0:200}"
    fi
    DETECTED_TRANSPORT="streamable"
    return 0
  fi

  fail "Streamable HTTP not available at /mcp (HTTP ${http_code:-unknown})"
  if [[ -n "$body" ]]; then
    echo "  Response: ${body:0:200}"
  fi
  return 1
}

# -----------------------------------------------
# Tests
# -----------------------------------------------

test_streamable_http || true

echo -e "${YELLOW}Testing: SSE endpoint disabled (/sse)${NC}"
sse_status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${BASE_URL}/sse" 2>&1 || true)
if [[ "$sse_status" == "404" || "$sse_status" == "405" ]]; then
  pass "SSE endpoint is disabled (HTTP ${sse_status})"
else
  fail "SSE endpoint still reachable (HTTP ${sse_status})"
fi

# -----------------------------------------------
# Summary
# -----------------------------------------------
echo ""
echo -e "${BLUE}=== Results ===${NC}"
echo -e "Detected transport: ${DETECTED_TRANSPORT}"
echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
echo ""

if [[ $TESTS_FAILED -gt 0 ]]; then
  echo -e "${RED}Some tests failed!${NC}"
  exit 1
else
  echo -e "${GREEN}All tests passed!${NC}"
  echo ""
  echo "Next: Test with MCP Inspector:"
  echo "  npx @modelcontextprotocol/inspector"
  echo "  Enter MCP URL: ${BASE_URL}/mcp"
  exit 0
fi
