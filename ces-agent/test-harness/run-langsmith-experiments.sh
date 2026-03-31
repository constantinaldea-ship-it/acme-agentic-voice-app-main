#!/usr/bin/env bash
# Created by Codex on 2026-03-27.

set -euo pipefail

HARNESS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EVAL_DIR="${HARNESS_DIR}/evaluation"
CES_AGENT_DIR="$(cd "${HARNESS_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
DEFAULT_STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"
RUNNER="${EVAL_DIR}/langsmith-live-experiments.py"
VALIDATOR="${CES_AGENT_DIR}/scripts/deploy/validate-package.py"

ROUTING_SUITE="${EVAL_DIR}/suites/langsmith-advisory-routing-suite.json"
BOOKING_SUITE="${EVAL_DIR}/suites/langsmith-advisory-booking-suite.json"
RECOVERY_SUITE="${EVAL_DIR}/suites/langsmith-advisory-recovery-suite.json"

MODE="${1:-all}"
if [[ $# -gt 0 ]]; then
  shift
fi

STATE_FILE="${DISCOVERY_PLAN_ENV:-${DEFAULT_STATE_FILE}}"

# shellcheck source=../../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

if [[ -f "${STATE_FILE}" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "${STATE_FILE}"
  set +a
fi

export GCP_LOCATION="${GCP_LOCATION:-eu}"
export CES_APP_ID="${CES_APP_ID:-acme-voice-eu}"

usage() {
  cat <<'EOF'
Usage:
  ces-agent/test-harness/run-langsmith-experiments.sh [all|routing|booking|recovery|validate] [runner args...]

Examples:
  ./run-langsmith-experiments.sh validate
  ./run-langsmith-experiments.sh routing --upload-mode never --app-id acme-voice-eu
  ./run-langsmith-experiments.sh all --upload-mode auto --app-id acme-voice-eu
EOF
}

require_gcloud() {
  if ! command -v gcloud >/dev/null 2>&1; then
    echo "ERROR: gcloud is required for remote evaluation runs but was not found in PATH." >&2
    exit 1
  fi
}

run_local_validation() {
  python3 "${VALIDATOR}"
}

case "${MODE}" in
  all)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    python3 "${RUNNER}" run \
      --suite "${ROUTING_SUITE}" \
      --suite "${BOOKING_SUITE}" \
      --suite "${RECOVERY_SUITE}" \
      "$@"
    ;;
  routing)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    python3 "${RUNNER}" run --suite "${ROUTING_SUITE}" "$@"
    ;;
  booking)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    python3 "${RUNNER}" run --suite "${BOOKING_SUITE}" "$@"
    ;;
  recovery)
    require_gcloud
    require_env_vars GCP_PROJECT_ID GCP_LOCATION CES_APP_ID
    run_local_validation
    python3 "${RUNNER}" run --suite "${RECOVERY_SUITE}" "$@"
    ;;
  validate)
    python3 "${RUNNER}" validate-suite-config \
      --suite "${ROUTING_SUITE}" \
      --suite "${BOOKING_SUITE}" \
      --suite "${RECOVERY_SUITE}"
    ;;
  *)
    usage
    exit 1
    ;;
esac
