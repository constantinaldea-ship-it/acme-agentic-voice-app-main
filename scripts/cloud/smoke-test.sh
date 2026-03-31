#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Compatibility wrapper that delegates Cloud Run smoke checks to the reusable
# framework in ces-agent/smoke-tests.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SMOKE_TESTS_DIR="${REPO_ROOT}/ces-agent/test-harness/smoke"
SMOKE_RUNNER="${SMOKE_TESTS_DIR}/ces-runtime-smoke.py"
REMOTE_SUITE="${SMOKE_TESTS_DIR}/suites/e2e-remote/discovery-plan-e2e-remote-smoke-suite.json"
DEFAULT_STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"

# shellcheck source=../lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

# Keep the compatibility wrapper aligned with the canonical smoke runner.
export CUSTOMER_DETAILS_API_KEY="${CUSTOMER_DETAILS_API_KEY:-YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ}"

usage() {
  cat <<USAGE
Usage:
  scripts/cloud/smoke-test.sh service <service-name> <base-url>
  scripts/cloud/smoke-test.sh stack discovery-plan [state-file]
USAGE
}

require_prereqs() {
  command -v python3 >/dev/null 2>&1 || {
    echo "ERROR: python3 is required but was not found in PATH." >&2
    exit 1
  }
  [[ -f "${SMOKE_RUNNER}" ]] || {
    echo "ERROR: Missing smoke runner: ${SMOKE_RUNNER}" >&2
    exit 1
  }
}

run_http_check() {
  python3 "${SMOKE_RUNNER}" http-request --expected-status 200 "$@"
}

smoke_mock_server() {
  local base_url="$1"
  require_env_vars CUSTOMER_DETAILS_API_KEY
  run_http_check \
    --method GET \
    --url "${base_url}/advisory-appointments/taxonomy" \
    --header "Authorization=Bearer mock-appointment-service-token" \
    --header "X-API-Key=${CUSTOMER_DETAILS_API_KEY}" \
    --header "X-BFA-Client=advisory-appointment-bff" \
    --header "X-Correlation-ID=smoke-test"
}

smoke_branch_finder() {
  local base_url="$1"
  run_http_check \
    --method GET \
    --url "${base_url}/health"
}

smoke_gateway() {
  local base_url="$1"
  run_http_check \
    --method GET \
    --url "${base_url}/api/v1/health"

  run_http_check \
    --method POST \
    --url "${base_url}/api/v1/tools/invoke" \
    --header "Authorization=Bearer smoke-user" \
    --json-body '{"toolName":"branchFinder","parameters":{"city":"Berlin"}}'
}

smoke_bfa_service_resource() {
  local base_url="$1"
  run_http_check \
    --method GET \
    --url "${base_url}/api/v1/health"

  run_http_check \
    --method GET \
    --url "${base_url}/api/v1/appointment-taxonomy" \
    --header "Authorization=Bearer mock-appointment-service-token"
}

smoke_service() {
  local service="$1"
  local base_url="$2"

  [[ -n "${base_url}" ]] || {
    echo "ERROR: A base URL is required for service smoke tests." >&2
    exit 1
  }

  case "${service}" in
    mock-server)
      smoke_mock_server "${base_url}"
      ;;
    bfa-adapter-branch-finder)
      smoke_branch_finder "${base_url}"
      ;;
    bfa-gateway)
      smoke_gateway "${base_url}"
      ;;
    bfa-service-resource)
      smoke_bfa_service_resource "${base_url}"
      ;;
    *)
      echo "ERROR: Unsupported service: ${service}" >&2
      exit 1
      ;;
  esac
}

smoke_stack() {
  local stack_name="$1"
  local state_file="${2:-${DEFAULT_STATE_FILE}}"

  [[ "${stack_name}" == "discovery-plan" ]] || {
    echo "ERROR: Unsupported stack: ${stack_name}" >&2
    exit 1
  }
  [[ -f "${state_file}" ]] || {
    echo "ERROR: State file not found: ${state_file}" >&2
    exit 1
  }
  [[ -f "${REMOTE_SUITE}" ]] || {
    echo "ERROR: Missing remote suite: ${REMOTE_SUITE}" >&2
    exit 1
  }

  set -a
  # shellcheck source=/dev/null
  source "${state_file}"
  set +a

  require_env_vars MOCK_SERVER_URL BFA_ADAPTER_BRANCH_FINDER_URL BFA_GATEWAY_URL BFA_SERVICE_RESOURCE_URL

  python3 "${SMOKE_RUNNER}" run-suite --suite "${REMOTE_SUITE}"
}

main() {
  require_prereqs

  local mode="${1:-}"
  local target="${2:-}"
  [[ -n "${mode}" && -n "${target}" ]] || {
    usage
    exit 1
  }

  case "${mode}" in
    service)
      smoke_service "${target}" "${3:-}"
      ;;
    stack)
      smoke_stack "${target}" "${3:-}"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
