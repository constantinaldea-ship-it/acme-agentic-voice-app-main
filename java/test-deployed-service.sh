#!/bin/bash
# Modified by Augment Agent on 2026-03-31
# Canonical smoke test for the deployed bfa-service-resource service.
# Replaces the legacy duplicate script: java/test-deployed-service-enhanced.sh
#
# Usage:
#   ./test-deployed-service.sh [SERVICE_URL]
#
# Resolution order when SERVICE_URL is omitted:
#   1. BFA_SERVICE_RESOURCE_URL (if not a placeholder)
#   2. .tmp/cloud-run/discovery-plan.env
#   3. gcloud run services describe bfa-service-resource

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_ROOT="$(cd "${SCRIPT_DIR}" && pwd)"
# shellcheck source=../scripts/lib/load-env.sh
source "${JAVA_ROOT}/../scripts/lib/load-env.sh"
load_root_env

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0
LAST_HTTP_CODE=""
LAST_BODY=""
APP_AUTH_TOKEN="${APP_AUTH_TOKEN:-smoke-user}"
SERVICE_NAME="${SERVICE_NAME:-${BFA_SERVICE_RESOURCE_SERVICE_NAME:-bfa-service-resource}}"
STATE_FILE="${STATE_FILE:-${JAVA_ROOT}/../.tmp/cloud-run/discovery-plan.env}"
SERVERLESS_CURL_ARGS=()

print_section() {
  echo ""
  echo -e "${BLUE}========================================${NC}"
  echo -e "${BLUE}$1${NC}"
  echo -e "${BLUE}========================================${NC}"
}

is_placeholder_url() {
  local value="${1:-}"
  [[ -z "${value}" || "${value}" == *"your-"* || "${value}" == *".example"* ]]
}

is_cloud_run_url() {
  [[ "${1:-}" == https://*.run.app* ]]
}

resolve_base_url() {
  if [[ -n "${1:-}" ]]; then
    printf '%s' "$1"
    return 0
  fi

  if ! is_placeholder_url "${BFA_SERVICE_RESOURCE_URL:-}"; then
    printf '%s' "${BFA_SERVICE_RESOURCE_URL}"
    return 0
  fi

  if [[ -f "${STATE_FILE}" ]]; then
    local state_url
    state_url="$(grep -E '^BFA_SERVICE_RESOURCE_URL=' "${STATE_FILE}" | tail -1 | cut -d= -f2- || true)"
    if ! is_placeholder_url "${state_url}"; then
      printf '%s' "${state_url}"
      return 0
    fi
  fi

  require_gcp_run_env || exit 1
  local discovered_url
  discovered_url="$(gcloud run services describe "${SERVICE_NAME}" \
    --project="${GCP_PROJECT_ID}" \
    --region="${GCP_REGION}" \
    --format='value(status.url)' 2>/dev/null || true)"

  if is_placeholder_url "${discovered_url}"; then
    echo -e "${RED}ERROR: Could not resolve a deployed service URL for ${SERVICE_NAME}.${NC}" >&2
    exit 1
  fi

  printf '%s' "${discovered_url}"
}

configure_serverless_auth() {
  local base_url="$1"
  local token="${CLOUD_RUN_ID_TOKEN:-}"

  if [[ -z "${token}" ]] && is_cloud_run_url "${base_url}" && command -v gcloud >/dev/null 2>&1; then
    token="$(gcloud auth print-identity-token 2>/dev/null || true)"
  fi

  if [[ -n "${token}" ]]; then
    SERVERLESS_CURL_ARGS=(-H "X-Serverless-Authorization: Bearer ${token}")
  fi
}

perform_request() {
  local url="$1"
  local use_app_auth="${2:-true}"
  local response

  if [[ "${use_app_auth}" == "true" ]]; then
    response="$(curl -sS -w "\n%{http_code}" \
      "${SERVERLESS_CURL_ARGS[@]}" \
      -H "Authorization: Bearer ${APP_AUTH_TOKEN}" \
      -H "X-Correlation-ID: deployed-test-$(date +%s)-${RANDOM}" \
      "${url}" || true)"
  else
    response="$(curl -sS -w "\n%{http_code}" \
      "${SERVERLESS_CURL_ARGS[@]}" \
      -H "X-Correlation-ID: deployed-test-$(date +%s)-${RANDOM}" \
      "${url}" || true)"
  fi

  LAST_HTTP_CODE="$(echo "${response}" | tail -n1)"
  LAST_BODY="$(echo "${response}" | sed '$d')"
}

test_endpoint() {
  local description="$1"
  local url="$2"
  local expected_code="${3:-200}"
  local use_app_auth="${4:-true}"

  echo ""
  echo -e "${YELLOW}Testing: ${description}${NC}"
  echo "URL: ${url}"

  perform_request "${url}" "${use_app_auth}"

  if [[ "${LAST_HTTP_CODE}" == "${expected_code}" ]]; then
    echo -e "${GREEN}✓ PASSED${NC} (HTTP ${LAST_HTTP_CODE})"
    TESTS_PASSED=$((TESTS_PASSED + 1))
  else
    echo -e "${RED}✗ FAILED${NC} (Expected ${expected_code}, got ${LAST_HTTP_CODE})"
    echo "Response preview: ${LAST_BODY:0:300}"
    TESTS_FAILED=$((TESTS_FAILED + 1))
  fi
}

extract_first_branch_id() {
  printf '%s' "${LAST_BODY}" | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data.get("data",{}).get("branches",[{}])[0].get("branchId", ""))' 2>/dev/null || true
}

BASE_URL="$(resolve_base_url "${1:-}")"
configure_serverless_auth "${BASE_URL}"

echo -e "${GREEN}Starting deployed smoke tests for bfa-service-resource${NC}"
echo "Base URL: ${BASE_URL}"
echo "Timestamp: $(date)"
if [[ ${#SERVERLESS_CURL_ARGS[@]} -gt 0 ]]; then
  echo "Cloud Run auth: enabled via X-Serverless-Authorization"
else
  echo "Cloud Run auth: not configured; assuming public/local endpoint"
fi
echo ""

print_section "Health & Documentation"
test_endpoint "Health endpoint" "${BASE_URL}/api/v1/health" 200 false
test_endpoint "Location OpenAPI spec" "${BASE_URL}/api-docs/location-services" 200 false
test_endpoint "Advisory appointment OpenAPI spec" "${BASE_URL}/api-docs/advisory-appointment" 200 false

print_section "Location Services"
test_endpoint "Search branches by city (Frankfurt)" \
  "${BASE_URL}/api/v1/branches?city=Frankfurt&limit=1"

if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
  BRANCH_ID="$(extract_first_branch_id)"
  if [[ -n "${BRANCH_ID}" ]]; then
    test_endpoint "Get specific branch details" \
      "${BASE_URL}/api/v1/branches/${BRANCH_ID}"
  else
    echo -e "${YELLOW}Skipping branch detail test: could not extract branchId from response${NC}"
  fi
fi

test_endpoint "Search branches by postal code" \
  "${BASE_URL}/api/v1/branches?postalCode=60311&limit=2"
test_endpoint "Search branches by GPS coordinates" \
  "${BASE_URL}/api/v1/branches?latitude=50.1109&longitude=8.6821&radiusKm=5"

print_section "Advisory Appointment Endpoints"
test_endpoint "Get appointment taxonomy" \
  "${BASE_URL}/api/v1/appointment-taxonomy"
test_endpoint "Search advisory services" \
  "${BASE_URL}/api/v1/appointment-service-search?entryPath=PRODUCT_CONSULTATION&query=investment%20advice"
test_endpoint "Search appointment branches" \
  "${BASE_URL}/api/v1/appointment-branches?entryPath=PRODUCT_CONSULTATION&consultationChannel=BRANCH&topicCode=IN&city=Berlin"
test_endpoint "Get appointment slots" \
  "${BASE_URL}/api/v1/appointment-slots?entryPath=PRODUCT_CONSULTATION&consultationChannel=BRANCH&locationId=20286143&topicCode=IN&selectedDay=2030-06-20"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Tests Passed: ${TESTS_PASSED}${NC}"
if [[ ${TESTS_FAILED} -gt 0 ]]; then
  echo -e "${RED}Tests Failed: ${TESTS_FAILED}${NC}"
  echo -e "${RED}✗ Some tests failed${NC}"
  exit 1
fi

echo -e "${GREEN}Tests Failed: ${TESTS_FAILED}${NC}"
echo -e "${GREEN}✓ All tests passed!${NC}"
