#!/bin/bash
# Smoke test for deployed BFA MCP Spike service and the CES runtime endpoints
# used by ces-agent/acme_voice_agent.
#
# Usage:
#   ./test-deployed.sh [MCP_SERVICE_URL]
#
# Notes:
# - Validates MCP transport on bfa-mcp-spike.
# - Validates the HTTP endpoints attached to the CES package's OpenAPI toolsets
#   and direct customer-details Python tool flow.
# - fee_schedule_lookup is intentionally excluded because it is a CES
#   googleSearchTool/data-store integration, not an HTTP endpoint.

set -euo pipefail

# Modified by Augment Agent on 2026-03-31: expand coverage to all CES HTTP endpoints.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${JAVA_ROOT}/.." && pwd)"
CES_ENVIRONMENT_JSON="${REPO_ROOT}/ces-agent/acme_voice_agent/environment.json"
STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"
# shellcheck source=../../scripts/lib/load-env.sh
source "${JAVA_ROOT}/../scripts/lib/load-env.sh"
load_root_env

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

TESTS_PASSED=0
TESTS_FAILED=0
DETECTED_TRANSPORT="unknown"
LAST_HTTP_CODE=""
LAST_BODY=""
SERVERLESS_ID_TOKEN="${CLOUD_RUN_ID_TOKEN:-}"
APP_AUTH_TOKEN="${APP_AUTH_TOKEN:-smoke-user}"
MOCK_API_KEY_VALUE="${MOCK_API_KEY:-YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ}"
CORR_PREFIX="mcp-spike-smoke-$(date +%s)"

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

print_section() {
  echo ""
  echo -e "${BLUE}=== $1 ===${NC}"
}

is_placeholder_url() {
  local value="${1:-}"
  [[ -z "${value}" || "${value}" == *"<hash>"* || "${value}" == *"your-"* || "${value}" == *".example"* || "${value}" == *'\$env_var'* ]]
}

is_cloud_run_url() {
  [[ "${1:-}" == https://*.run.app* ]]
}

ensure_serverless_token() {
  if [[ -n "${SERVERLESS_ID_TOKEN}" ]]; then
    return 0
  fi

  if ! command -v gcloud >/dev/null 2>&1; then
    return 0
  fi

  SERVERLESS_ID_TOKEN="$(gcloud auth print-identity-token 2>/dev/null || true)"
}

environment_toolset_url() {
  local toolset_name="$1"
  if [[ ! -f "${CES_ENVIRONMENT_JSON}" ]]; then
    return 0
  fi

  python3 - "$toolset_name" "${CES_ENVIRONMENT_JSON}" <<'PY' 2>/dev/null || true
import json, sys
toolset_name = sys.argv[1]
path = sys.argv[2]
with open(path, encoding='utf-8') as handle:
    data = json.load(handle)
toolsets = data.get('toolsets', {})
entry = toolsets.get(toolset_name, {})
openapi = entry.get('openApiToolset', {})
url = openapi.get('url', '')
print(url.rstrip('/'))
PY
}

state_file_url() {
  local key="$1"
  if [[ ! -f "${STATE_FILE}" ]]; then
    return 0
  fi
  grep -E "^${key}=" "${STATE_FILE}" | tail -1 | cut -d= -f2- || true
}

gcloud_service_url() {
  local service_name="$1"
  if ! command -v gcloud >/dev/null 2>&1; then
    return 0
  fi
  if ! require_gcp_run_env >/dev/null 2>&1; then
    return 0
  fi

  gcloud run services describe "${service_name}" \
    --project="${GCP_PROJECT_ID}" \
    --region="${GCP_REGION}" \
    --format='value(status.url)' 2>/dev/null || true
}

resolve_dependency_url() {
  local env_name="$1"
  local service_name="$2"
  local toolset_name="$3"
  local value="${!env_name:-}"

  if ! is_placeholder_url "${value}"; then
    printf '%s' "${value%/}"
    return 0
  fi

  value="$(state_file_url "${env_name}")"
  if ! is_placeholder_url "${value}"; then
    printf '%s' "${value%/}"
    return 0
  fi

  value="$(gcloud_service_url "${service_name}")"
  if ! is_placeholder_url "${value}"; then
    printf '%s' "${value%/}"
    return 0
  fi

  value="$(environment_toolset_url "${toolset_name}")"
  if ! is_placeholder_url "${value}"; then
    printf '%s' "${value%/}"
    return 0
  fi

  return 1
}

resolve_mcp_url() {
  if [[ -n "${1:-}" ]]; then
    printf '%s' "${1%/}"
    return 0
  fi

  if ! is_placeholder_url "${BFA_MCP_SPIKE_URL:-}"; then
    printf '%s' "${BFA_MCP_SPIKE_URL%/}"
    return 0
  fi

  local discovered
  discovered="$(gcloud_service_url "${SERVICE_NAME:-bfa-mcp-spike}")"
  if ! is_placeholder_url "${discovered}"; then
    printf '%s' "${discovered%/}"
    return 0
  fi

  return 1
}

perform_request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local content_type="${4:-}"
  local use_app_auth="${5:-false}"
  shift 5 || true

  local -a curl_args
  local response
  curl_args=(-sS -w "\n%{http_code}" --max-time 25 -X "${method}")

  if is_cloud_run_url "${url}"; then
    ensure_serverless_token
    if [[ -n "${SERVERLESS_ID_TOKEN}" ]]; then
      curl_args+=(-H "X-Serverless-Authorization: Bearer ${SERVERLESS_ID_TOKEN}")
    fi
  fi

  curl_args+=(-H "X-Correlation-ID: ${CORR_PREFIX}-${RANDOM}")

  if [[ "${use_app_auth}" == "true" ]]; then
    curl_args+=(-H "Authorization: Bearer ${APP_AUTH_TOKEN}")
  fi

  if [[ -n "${content_type}" ]]; then
    curl_args+=(-H "Content-Type: ${content_type}")
  fi

  while [[ $# -gt 0 ]]; do
    curl_args+=(-H "$1")
    shift
  done

  if [[ -n "${body}" ]]; then
    curl_args+=(--data "${body}")
  fi

  response="$(curl "${curl_args[@]}" "${url}" 2>&1 || true)"
  LAST_HTTP_CODE="$(echo "${response}" | tail -n1)"
  LAST_BODY="$(echo "${response}" | sed '$d')"
}

assert_status() {
  local label="$1"
  local expected="$2"
  if [[ "${LAST_HTTP_CODE}" == "${expected}" ]]; then
    pass "${label} (HTTP ${LAST_HTTP_CODE})"
  else
    fail "${label} (expected ${expected}, got ${LAST_HTTP_CODE:-unknown})"
    if [[ -n "${LAST_BODY}" ]]; then
      echo "  Response: ${LAST_BODY:0:240}"
    fi
  fi
}

extract_branch_id() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("data",{}).get("branches",[{}])[0].get("branchId",""))' 2>/dev/null || true
}

extract_access_token() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("access_token", ""))' 2>/dev/null || true
}

extract_authz_token() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("authorization_token", ""))' 2>/dev/null || true
}

extract_appointment_id() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("data",{}).get("appointment",{}).get("appointmentId", ""))' 2>/dev/null || true
}

extract_appointment_access_token() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("data",{}).get("appointmentAccessToken", ""))' 2>/dev/null || true
}

extract_first_slot_id() {
  printf '%s' "$1" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("data",{}).get("slots",[{}])[0].get("slotId", ""))' 2>/dev/null || true
}

test_streamable_http() {
  local payload
  payload='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"smoke-test","version":"1.0.0"}}}'
  echo -e "${YELLOW}Testing: Streamable HTTP endpoint (/mcp)${NC}"
  perform_request "POST" "${MCP_URL}/mcp" "${payload}" "application/json" "false" "Accept: application/json, text/event-stream"
  if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
    pass "Streamable HTTP initialize accepted (HTTP 200)"
    DETECTED_TRANSPORT="streamable"
    if [[ -n "${LAST_BODY}" ]]; then
      echo "  Response: ${LAST_BODY:0:200}"
    fi
  else
    fail "Streamable HTTP not available at /mcp (HTTP ${LAST_HTTP_CODE:-unknown})"
    if [[ -n "${LAST_BODY}" ]]; then
      echo "  Response: ${LAST_BODY:0:200}"
    fi
  fi
}

test_location_toolset() {
  print_section "CES location toolset -> bfa-service-resource"
  perform_request "GET" "${RESOURCE_URL}/api/v1/branches?city=Frankfurt&limit=1" "" "" "true"
  assert_status "searchBranches" "200"
  local branch_id=""
  if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
    branch_id="$(extract_branch_id "${LAST_BODY}")"
  fi
  if [[ -n "${branch_id}" ]]; then
    perform_request "GET" "${RESOURCE_URL}/api/v1/branches/${branch_id}" "" "" "true"
    assert_status "getBranch" "200"
  else
    fail "getBranch (could not extract branchId from searchBranches response)"
  fi
}

test_advisory_toolset() {
  print_section "CES advisory_appointment toolset -> bfa-service-resource"
  local create_slot_id=""
  local reschedule_slot_id=""

  perform_request "GET" "${RESOURCE_URL}/api/v1/appointment-taxonomy" "" "" "true"
  assert_status "getAppointmentTaxonomy" "200"

  perform_request "GET" "${RESOURCE_URL}/api/v1/appointment-service-search?entryPath=SERVICE_REQUEST&query=standing%20order" "" "" "true"
  assert_status "searchAppointmentServices" "200"

  perform_request "GET" "${RESOURCE_URL}/api/v1/appointment-branches?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&city=berlin" "" "" "true"
  assert_status "searchAppointmentBranches" "200"

  perform_request "GET" "${RESOURCE_URL}/api/v1/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&locationId=20286143&selectedDay=2030-06-18" "" "" "true"
  assert_status "getAppointmentSlots" "200"
  if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
    create_slot_id="$(extract_first_slot_id "${LAST_BODY}")"
  fi

  perform_request "GET" "${RESOURCE_URL}/api/v1/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&locationId=20286143&selectedDay=2030-06-19" "" "" "true"
  assert_status "getAppointmentSlots (reschedule day)" "200"
  if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
    reschedule_slot_id="$(extract_first_slot_id "${LAST_BODY}")"
  fi

  local create_body
  local create_status
  local appointment_id=""
  local appointment_token=""
  if [[ -z "${create_slot_id}" ]]; then
    fail "createAppointment (skipped because no slotId could be extracted for 2030-06-18)"
    create_status=""
  else
    create_body='{"entryPath":"SERVICE_REQUEST","consultationChannel":"BRANCH","serviceCode":"standing-order","locationId":"20286143","selectedDay":"2030-06-18","selectedTimeSlotId":"'"${create_slot_id}"'","customer":{"salutation":"HERR","firstName":"Hans","lastName":"Mueller","email":"hans.mueller@example.com","phone":"+49 800 1234567","isExistingCustomer":true},"summaryConfirmed":true}'
    perform_request "POST" "${RESOURCE_URL}/api/v1/appointments" "${create_body}" "application/json" "true"
    create_status="${LAST_HTTP_CODE}"
    assert_status "createAppointment" "201"
  fi

  if [[ "${create_status}" == "201" ]]; then
    appointment_id="$(extract_appointment_id "${LAST_BODY}")"
    appointment_token="$(extract_appointment_access_token "${LAST_BODY}")"
  fi

  if [[ -n "${appointment_id}" && -n "${appointment_token}" ]]; then
    perform_request "GET" "${RESOURCE_URL}/api/v1/appointments/${appointment_id}?appointmentAccessToken=${appointment_token}" "" "" "true"
    assert_status "getAppointment" "200"

    if [[ -n "${reschedule_slot_id}" ]]; then
      perform_request "POST" "${RESOURCE_URL}/api/v1/appointments/${appointment_id}/reschedule" '{"appointmentAccessToken":"'"${appointment_token}"'","selectedDay":"2030-06-19","selectedTimeSlotId":"'"${reschedule_slot_id}"'","summaryConfirmed":true}' "application/json" "true"
      assert_status "rescheduleAppointment" "200"
    else
      fail "rescheduleAppointment (skipped because no slotId could be extracted for 2030-06-19)"
    fi

    perform_request "POST" "${RESOURCE_URL}/api/v1/appointments/${appointment_id}/cancel" '{"appointmentAccessToken":"'"${appointment_token}"'","reason":"schedule conflict","summaryConfirmed":true}' "application/json" "true"
    assert_status "cancelAppointment" "200"
  else
    fail "getAppointment (skipped because createAppointment did not return appointment identifiers)"
    fail "rescheduleAppointment (skipped because createAppointment did not return appointment identifiers)"
    fail "cancelAppointment (skipped because createAppointment did not return appointment identifiers)"
  fi
}

test_customer_details_endpoints() {
  print_section "CES customer_details tool chain -> mock-server"
  local eidp_token=""
  local authz_token=""

  perform_request "POST" "${MOCK_URL}/oauth/token" "grant_type=client_credentials&client_id=ces-agent-service&client_secret=mock-secret" "application/x-www-form-urlencoded" "false" \
    "X-API-Key: ${MOCK_API_KEY_VALUE}" \
    "X-Agent-Id: customer_details_agent" \
    "X-Tool-Id: getEidpToken"
  assert_status "getEidpToken" "200"
  if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
    eidp_token="$(extract_access_token "${LAST_BODY}")"
  fi

  if [[ -n "${eidp_token}" ]]; then
    perform_request "POST" "${MOCK_URL}/authz/authorize" '{"resource":"customers:personal-data","action":"read"}' "application/json" "false" \
      "X-API-Key: ${MOCK_API_KEY_VALUE}" \
      "X-Agent-Id: customer_details_agent" \
      "X-Tool-Id: getAuthzToken" \
      "Authorization: Bearer ${eidp_token}"
    assert_status "getAuthzToken" "200"
    if [[ "${LAST_HTTP_CODE}" == "200" ]]; then
      authz_token="$(extract_authz_token "${LAST_BODY}")"
    fi
  else
    fail "getAuthzToken (skipped because getEidpToken did not return access_token)"
  fi

  if [[ -n "${authz_token}" ]]; then
    perform_request "GET" "${MOCK_URL}/customers/1234567890/personal-data" "" "" "false" \
      "X-API-Key: ${MOCK_API_KEY_VALUE}" \
      "X-Agent-Id: customer_details_agent" \
      "X-Tool-Id: getCustomerPersonalData" \
      "Authorization: Bearer ${authz_token}" \
      "deuba-client-id: pb-banking" \
      "DB-ID: acme-banking-db-01" \
      "Accept: application/json"
    assert_status "getCustomerPersonalData" "200"
  else
    fail "getCustomerPersonalData (skipped because getAuthzToken did not return authorization_token)"
  fi
}

test_adapter_endpoints() {
  print_section "CES branch_finder_adapter toolset -> bfa-adapter-branch-finder"
  perform_request "GET" "${ADAPTER_URL}/health" "" "" "false"
  assert_status "getBranchFinderAdapterHealth" "200"

  perform_request "POST" "${ADAPTER_URL}/actions/search" '{"correlationId":"adapter-smoke","parameters":{"city":"Berlin"}}' "application/json" "false"
  assert_status "searchBranchesViaAdapter" "200"
}

test_gateway_endpoints() {
  print_section "CES bfa_gateway toolset -> bfa-gateway"
  perform_request "GET" "${GATEWAY_URL}/api/v1/tools" "" "" "true"
  assert_status "listGatewayTools" "200"

  perform_request "POST" "${GATEWAY_URL}/api/v1/tools/invoke" '{"toolName":"branchFinder","parameters":{"city":"Berlin"}}' "application/json" "true"
  assert_status "invokeGatewayTool" "200"
}

MCP_URL="$(resolve_mcp_url "${1:-}" || true)"
RESOURCE_URL="$(resolve_dependency_url "BFA_SERVICE_RESOURCE_URL" "bfa-service-resource" "location" || true)"
MOCK_URL="$(resolve_dependency_url "MOCK_SERVER_URL" "mock-server" "customer_details_openapi" || true)"
GATEWAY_URL="$(resolve_dependency_url "BFA_GATEWAY_URL" "bfa-gateway" "bfa_gateway" || true)"
ADAPTER_URL="$(resolve_dependency_url "ADAPTER_BRANCH_FINDER_URL" "bfa-adapter-branch-finder" "branch_finder_adapter" || true)"

echo -e "${BLUE}=== BFA MCP Spike + CES Dependency Smoke Tests ===${NC}"
echo "MCP service URL: ${MCP_URL:-UNRESOLVED}"
echo "CES location/advisory URL: ${RESOURCE_URL:-UNRESOLVED}"
echo "CES customer details URL: ${MOCK_URL:-UNRESOLVED}"
echo "CES gateway URL: ${GATEWAY_URL:-UNRESOLVED}"
echo "CES adapter URL: ${ADAPTER_URL:-UNRESOLVED}"
echo ""

if [[ -n "${MCP_URL}" ]]; then
  test_streamable_http

  echo -e "${YELLOW}Testing: SSE endpoint disabled (/sse)${NC}"
  perform_request "GET" "${MCP_URL}/sse" "" "" "false"
  if [[ "${LAST_HTTP_CODE}" == "404" || "${LAST_HTTP_CODE}" == "405" ]]; then
    pass "SSE endpoint is disabled (HTTP ${LAST_HTTP_CODE})"
  else
    fail "SSE endpoint still reachable (HTTP ${LAST_HTTP_CODE:-unknown})"
  fi
else
  fail "Could not resolve bfa-mcp-spike URL (pass it as first arg or export BFA_MCP_SPIKE_URL)"
fi

if [[ -n "${RESOURCE_URL}" ]]; then
  test_location_toolset
  test_advisory_toolset
else
  fail "Could not resolve CES location/advisory backend URL"
fi

if [[ -n "${MOCK_URL}" ]]; then
  test_customer_details_endpoints
else
  fail "Could not resolve CES customer-details backend URL"
fi

if [[ -n "${ADAPTER_URL}" ]]; then
  test_adapter_endpoints
else
  fail "Could not resolve CES branch-finder adapter URL"
fi

if [[ -n "${GATEWAY_URL}" ]]; then
  test_gateway_endpoints
else
  fail "Could not resolve CES gateway URL"
fi

echo ""
echo -e "${BLUE}=== Results ===${NC}"
echo -e "Detected transport: ${DETECTED_TRANSPORT}"
echo -e "Passed: ${GREEN}${TESTS_PASSED}${NC}"
echo -e "Failed: ${RED}${TESTS_FAILED}${NC}"
echo ""

if [[ ${TESTS_FAILED} -gt 0 ]]; then
  echo -e "${RED}Some tests failed!${NC}"
  exit 1
fi

echo -e "${GREEN}All tests passed!${NC}"
echo ""
echo "Next: Test with MCP Inspector:"
echo "  npx @modelcontextprotocol/inspector"
echo "  Enter MCP URL: ${MCP_URL}/mcp"
