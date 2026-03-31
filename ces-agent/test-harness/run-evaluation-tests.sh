#!/usr/bin/env bash
# Created by Codex on 2026-03-27.

set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVAL_DIR="${HARNESS_DIR}/evaluation"
CES_AGENT_DIR="$(cd "${HARNESS_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
DEFAULT_STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"
RUNNER="${EVAL_DIR}/ces-evaluation-runner.py"
VALIDATOR="${CES_AGENT_DIR}/scripts/deploy/validate-package.py"
GOLDEN_SUITE="${EVAL_DIR}/suites/agent-quality-golden-suite.json"
SCENARIO_SUITE="${EVAL_DIR}/suites/agent-quality-scenario-suite.json"

# shellcheck source=../../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

MODE="${1:-all}"
STATE_FILE="${2:-${DEFAULT_STATE_FILE}}"

usage() {
  cat <<'EOF'
Usage:
  ces-agent/test-harness/run-evaluation-tests.sh [all|golden|scenario|unit] [state-file]

Modes:
  all       Validate package, validate both suites, then run golden and scenario suites
  golden    Validate package, validate suite, then run golden suite
  scenario  Validate package, validate suite, then run scenario suite
  unit      Run framework unit tests only

State file:
  Defaults to ${DEFAULT_STATE_FILE}
EOF
}

require_gcloud() {
  if ! command -v gcloud >/dev/null 2>&1; then
    echo "ERROR: gcloud is required for remote evaluation runs but was not found in PATH." >&2
    exit 1
  fi
}

if [[ -f "${STATE_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${STATE_FILE}"
  set +a
fi

export GCP_LOCATION="${GCP_LOCATION:-us}"
export CES_APP_ID="${CES_APP_ID:-acme-voice-us}"

command -v python3 >/dev/null 2>&1 || {
  echo "ERROR: python3 is required but was not found in PATH." >&2
  exit 1
}

run_local_validation() {
  python3 "${VALIDATOR}"
}

run_suite() {
  local suite_path="$1"
  python3 "${RUNNER}" validate-suite --suite "${suite_path}"
  python3 "${RUNNER}" run-suite --suite "${suite_path}"
}

case "${MODE}" in
  all)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    run_suite "${GOLDEN_SUITE}"
    run_suite "${SCENARIO_SUITE}"
    ;;
  golden)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    run_suite "${GOLDEN_SUITE}"
    ;;
  scenario)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    run_suite "${SCENARIO_SUITE}"
    ;;
  unit)
    cd "${EVAL_DIR}"
    python3 -m unittest -b tests/test_ces_evaluation_runner.py
    ;;
  *)
    usage
    exit 1
    ;;
esac
