#!/bin/bash
# Test script to verify agent context headers are properly logged

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=../../scripts/lib/load-env.sh
source "${JAVA_ROOT}/../scripts/lib/load-env.sh"
load_root_env

STACK_STATE_FILE="${JAVA_ROOT}/../.tmp/cloud-run/discovery-plan.env"
if [[ -f "${STACK_STATE_FILE}" ]]; then
  # shellcheck source=/dev/null
  source "${STACK_STATE_FILE}"
fi

BASE_URL="${1:-${BFA_SERVICE_RESOURCE_URL:-}}"
TOKEN="${2:-test}"

if [[ -z "${BASE_URL}" ]]; then
  echo "ERROR: Could not resolve BFA service URL. Pass it as the first argument, export BFA_SERVICE_RESOURCE_URL, or deploy the stack so ${STACK_STATE_FILE} is available."
  exit 1
fi

echo "============================================"
echo "Testing Agent Context Header Propagation"
echo "============================================"
echo ""
echo "Base URL: ${BASE_URL}"
echo ""

# Test 1: Search branches with all agent context headers
echo "Test 1: Search branches with agent context headers"
echo "---------------------------------------------------"
CORRELATION_ID=$(uuidgen)
echo "  Correlation ID: ${CORRELATION_ID}"

curl -s -w "\n  HTTP Status: %{http_code}\n" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  -H "X-Agent-Id: ces-agent" \
  -H "X-Tool-Id: searchBranches" \
  -H "X-Session-Id: test-session-001" \
  "${BASE_URL}/api/v1/branches?city=Frankfurt&limit=2" \
  | jq -r '.meta.correlationId, .data.branches[].name' 2>/dev/null || echo "  (JSON parse failed - check logs)"

echo ""

# Test 2: Get branch details with agent context headers
echo "Test 2: Get branch details with agent context headers"
echo "-----------------------------------------------------"
CORRELATION_ID=$(uuidgen)
echo "  Correlation ID: ${CORRELATION_ID}"

curl -s -w "\n  HTTP Status: %{http_code}\n" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "X-Correlation-ID: ${CORRELATION_ID}" \
  -H "X-Agent-Id: ces-agent" \
  -H "X-Tool-Id: getBranch" \
  -H "X-Session-Id: test-session-001" \
  "${BASE_URL}/api/v1/branches/686496" \
  | jq -r '.meta.correlationId, .data.name' 2>/dev/null || echo "  (JSON parse failed - check logs)"

echo ""
echo "============================================"
echo "To verify audit logs, check Cloud Logging:"
echo "============================================"
echo ""
echo "  gcloud logging read 'resource.type=cloud_run_revision AND resource.labels.service_name=bfa-service-resource AND jsonPayload.logger=AUDIT' --project ${GCP_PROJECT_ID:-<your-project>} --limit 10 --format=json | jq -r '.[] | [.timestamp, .jsonPayload.message] | @tsv'"
echo ""
echo "Expected log format:"
echo "  REQUEST_START  | corr={uuid} | user={userId} | agent=ces-agent | tool=searchBranches | session=test-session-001 | op={operation} | GET {path}"
echo "  REQUEST_COMPLETE | corr={uuid} | user={userId} | agent=ces-agent | tool=searchBranches | session=test-session-001 | op={operation} | outcome=SUCCESS | status=200 | duration={ms}ms"
echo ""
