#!/usr/bin/env bash
# Created by GitHub Copilot on 2026-03-27
# Consolidated wrapper for CES runtime smoke suites, e2e-remote HTTP/OpenAPI
# smoke suites, and local framework unit tests.

set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SMOKE_TESTS_DIR="${HARNESS_DIR}/smoke"
CES_AGENT_DIR="$(cd "${HARNESS_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
DEFAULT_STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"
CES_SUITES_DIR="${SMOKE_TESTS_DIR}/suites/ces"
E2E_REMOTE_SUITES_DIR="${SMOKE_TESTS_DIR}/suites/e2e-remote"

# shellcheck source=../../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

MODE="${1:-all}"
STATE_FILE="${2:-${DEFAULT_STATE_FILE}}"

usage() {
  cat <<USAGE
Usage:
  ces-agent/test-harness/run-smoke-tests.sh [all|ces|e2e-remote|remote|unit] [state-file]

Modes:
  all         Run CES suites, e2e-remote HTTP/OpenAPI suites, and unit tests
  ces     Run only CES runtime suite + unit tests
  e2e-remote  Run only e2e-remote HTTP/OpenAPI suites + unit tests
  remote      Backward-compatible alias for e2e-remote
  unit        Run only unit tests

State file:
  Defaults to ${DEFAULT_STATE_FILE}
USAGE
}

discover_suite_files() {
  local suite_dir="$1"

  [[ -d "${suite_dir}" ]] || return 0

  find "${suite_dir}" -maxdepth 1 -type f -name '*-smoke-suite.json' | sort
}

require_suite_group() {
  local suite_dir="$1"
  local label="$2"

  [[ -d "${suite_dir}" ]] || {
    echo "ERROR: Missing ${label} suite directory: ${suite_dir}" >&2
    exit 1
  }

  local suite_count
  suite_count="$(discover_suite_files "${suite_dir}" | wc -l | tr -d ' ')"
  [[ "${suite_count}" != "0" ]] || {
    echo "ERROR: No ${label} smoke suites found in ${suite_dir}" >&2
    exit 1
  }
}

run_suite_group() {
  local suite_dir="$1"
  local label="$2"
  local suite_path

  while IFS= read -r suite_path; do
    [[ -n "${suite_path}" ]] || continue
    echo
    echo "==> Running ${label} smoke suite: $(basename "${suite_path}")"
    python3 ces-runtime-smoke.py run-suite \
      --suite "${suite_path}"
  done < <(discover_suite_files "${suite_dir}")
}

case "${MODE}" in
  all|ces|e2e-remote|remote|unit)
    ;;
  *)
    usage
    exit 1
    ;;
esac

if [[ "${MODE}" == "remote" ]]; then
  MODE="e2e-remote"
fi

if [[ -f "${STATE_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${STATE_FILE}"
  set +a
fi

export GCP_PROJECT_ID="${GCP_PROJECT_ID:-voice-banking-poc}"
export GCP_LOCATION="${GCP_LOCATION:-eu}"
export CES_APP_ID="${CES_APP_ID:-acme-voice-eu}"
export CUSTOMER_DETAILS_API_KEY="${CUSTOMER_DETAILS_API_KEY:-YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ}"

command -v python3 >/dev/null 2>&1 || {
  echo "ERROR: python3 is required but was not found in PATH." >&2
  exit 1
}

[[ -f "${SMOKE_TESTS_DIR}/ces-runtime-smoke.py" ]] || {
  echo "ERROR: Missing smoke runner: ${SMOKE_TESTS_DIR}/ces-runtime-smoke.py" >&2
  exit 1
}
[[ -f "${SMOKE_TESTS_DIR}/tests/test_ces_runtime_smoke.py" ]] || {
  echo "ERROR: Missing smoke unit tests: ${SMOKE_TESTS_DIR}/tests/test_ces_runtime_smoke.py" >&2
  exit 1
}

cd "${SMOKE_TESTS_DIR}"

if [[ "${MODE}" == "all" || "${MODE}" == "ces" ]]; then
  command -v gcloud >/dev/null 2>&1 || {
    echo "ERROR: gcloud is required for CES runtime smoke tests but was not found in PATH." >&2
    exit 1
  }

  require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID CUSTOMER_DETAILS_API_KEY
  require_suite_group "${CES_SUITES_DIR}" "CES"

  run_suite_group "${CES_SUITES_DIR}" "CES"
fi

if [[ "${MODE}" == "all" || "${MODE}" == "e2e-remote" ]]; then
  require_env_vars MOCK_SERVER_URL BFA_ADAPTER_BRANCH_FINDER_URL BFA_GATEWAY_URL BFA_SERVICE_RESOURCE_URL
  require_suite_group "${E2E_REMOTE_SUITES_DIR}" "e2e-remote HTTP/OpenAPI"

  run_suite_group "${E2E_REMOTE_SUITES_DIR}" "e2e-remote HTTP/OpenAPI"
fi

if [[ "${MODE}" == "all" || "${MODE}" == "ces" || "${MODE}" == "e2e-remote" || "${MODE}" == "unit" ]]; then
  echo
  echo "==> Running smoke framework unit tests"
  python3 -m unittest -b tests/test_ces_runtime_smoke.py
fi

echo
echo "✅ Smoke checks completed successfully"
