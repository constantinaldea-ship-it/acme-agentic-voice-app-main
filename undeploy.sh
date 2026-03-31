#!/usr/bin/env bash
# Modified by Codex on 2026-03-28
# Root wrapper for Cloud Run teardown. Defaults to the full discovery-plan stack,
# but still allows explicit passthrough usage for `service ...` or `stack ...`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "service" || "${1:-}" == "stack" ]]; then
  exec "${SCRIPT_DIR}/scripts/cloud/undeploy.sh" "$@"
fi

exec "${SCRIPT_DIR}/scripts/cloud/undeploy.sh" stack discovery-plan "$@"
