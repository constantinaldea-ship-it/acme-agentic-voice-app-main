#!/usr/bin/env bash
# Modified by Augment Agent on 2026-03-28.
# Unified CES cleanup entrypoint for agents, tools, toolsets, and the fee schedule datastore.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

RESOURCE_MANAGER="${SCRIPT_DIR}/scripts/deploy/ces-undeploy-manager.py"
DATASTORE_SCRIPT="${SCRIPT_DIR}/scripts/deploy/manage_datastore.sh"

resource_args=()
datastore_args=(deprovision)
run_datastore_cleanup=true

require_value() {
  local flag="$1"
  local value="${2:-}"
  if [[ -z "${value}" ]]; then
    printf 'ERROR: %s requires a value\n' "${flag}" >&2
    exit 1
  fi
}

usage() {
  cat <<'EOF'
Usage:
  ces-agent/undeploy.sh [options]

Deletes all locally defined CES agents, tools, and toolsets for the configured app,
then deprovisions the fee schedule datastore cleanup helper.

Shared CES resource options:
  --project <id>          Override GCP project ID
  --location <id>         Override CES location
  --app-id <id>           Override CES app ID
  --app-root <path>       Override CES app package root
  --state-file <path>     Override local CES deployment state file
  --endpoint <url>        Override CES API endpoint
  --yes                   Skip confirmation prompt
  --dry-run               Print the plan without deleting remote resources

Datastore cleanup options:
  --datastore-location <id>
  --datastore-id <id>
  --display-name <name>
  --collection <id>
  --branch-id <id>
  --bucket-uri <gs://...>
  --bucket-location <id>
  --source-url <url>
  --object-name <name>
  --tool-json <path>
  --delete-bucket <true|false>
  --timeout-seconds <n>
  --poll-seconds <n>

Behavior options:
  --skip-datastore        Skip the datastore/bucket deprovision helper
  --help, -h              Show this help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project|--location|--app-id|--app-root|--state-file|--endpoint)
      require_value "$1" "${2:-}"
      resource_args+=("$1" "$2")
      if [[ "$1" == "--project" || "$1" == "--location" ]]; then
        datastore_args+=("$1" "$2")
      fi
      shift 2
      ;;
    --datastore-location|--datastore-id|--display-name|--collection|--branch-id|--bucket-uri|--bucket-location|--source-url|--object-name|--tool-json|--delete-bucket|--timeout-seconds|--poll-seconds)
      require_value "$1" "${2:-}"
      datastore_args+=("$1" "$2")
      shift 2
      ;;
    --yes)
      resource_args+=("$1")
      shift
      ;;
    --dry-run)
      resource_args+=("$1")
      datastore_args+=("$1")
      shift
      ;;
    --skip-datastore)
      run_datastore_cleanup=false
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      printf 'ERROR: Unknown option: %s\n' "$1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ ! -f "${RESOURCE_MANAGER}" ]]; then
  printf 'ERROR: CES undeploy manager not found: %s\n' "${RESOURCE_MANAGER}" >&2
  exit 1
fi
if [[ ! -f "${DATASTORE_SCRIPT}" ]]; then
  printf 'ERROR: Datastore cleanup script not found: %s\n' "${DATASTORE_SCRIPT}" >&2
  exit 1
fi

resource_exit=0
python3 "${RESOURCE_MANAGER}" "${resource_args[@]}" || resource_exit=$?

datastore_exit=0
if [[ "${run_datastore_cleanup}" == true ]]; then
  bash "${DATASTORE_SCRIPT}" "${datastore_args[@]}" || datastore_exit=$?
fi

if [[ "${resource_exit}" -ne 0 || "${datastore_exit}" -ne 0 ]]; then
  exit 1
fi
