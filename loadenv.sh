#!/usr/bin/env bash
# Source this file to load the repository environment into the current shell.
#
# Usage:
#   source ./loadenv.sh
#   source ./loadenv.sh /absolute/path/to/discovery-plan.env

if [[ -n "${BASH_VERSION:-}" ]]; then
  _LOADENV_SELF="${BASH_SOURCE[0]}"
elif [[ -n "${ZSH_VERSION:-}" ]]; then
  _LOADENV_SELF="${(%):-%N}"
else
  _LOADENV_SELF="$0"
fi

_LOADENV_ROOT="$(cd "$(dirname "${_LOADENV_SELF}")" && pwd)"
_LOADENV_DEFAULT_STATE_FILE="${_LOADENV_ROOT}/.tmp/cloud-run/discovery-plan.env"
_LOADENV_STATE_FILE="${1:-${_LOADENV_DEFAULT_STATE_FILE}}"
ROOT_ENV_FILE="${ROOT_ENV_FILE:-${_LOADENV_ROOT}/.env}"

_loadenv_sync_gcp_env_aliases() {
  if [[ -n "${GCP_PROJECT_ID:-}" && -z "${PROJECT_ID:-}" ]]; then
    export PROJECT_ID="${GCP_PROJECT_ID}"
  elif [[ -n "${PROJECT_ID:-}" && -z "${GCP_PROJECT_ID:-}" ]]; then
    export GCP_PROJECT_ID="${PROJECT_ID}"
  fi

  if [[ -n "${GCP_REGION:-}" && -z "${REGION:-}" ]]; then
    export REGION="${GCP_REGION}"
  elif [[ -n "${REGION:-}" && -z "${GCP_REGION:-}" ]]; then
    export GCP_REGION="${REGION}"
  fi

  if [[ -n "${GCP_ZONE:-}" && -z "${ZONE:-}" ]]; then
    export ZONE="${GCP_ZONE}"
  elif [[ -n "${ZONE:-}" && -z "${GCP_ZONE:-}" ]]; then
    export GCP_ZONE="${ZONE}"
  fi

  if [[ -n "${GCP_LOCATION:-}" && -z "${LOCATION:-}" ]]; then
    export LOCATION="${GCP_LOCATION}"
  elif [[ -n "${LOCATION:-}" && -z "${GCP_LOCATION:-}" ]]; then
    export GCP_LOCATION="${LOCATION}"
  fi

  if [[ -n "${GCP_SERVICE_ACCOUNT_KEY:-}" && -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
    export GOOGLE_APPLICATION_CREDENTIALS="${GCP_SERVICE_ACCOUNT_KEY}"
  elif [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" && -z "${GCP_SERVICE_ACCOUNT_KEY:-}" ]]; then
    export GCP_SERVICE_ACCOUNT_KEY="${GOOGLE_APPLICATION_CREDENTIALS}"
  fi

  if [[ -n "${GCP_PROJECT_ID:-}" && -z "${GOOGLE_CLOUD_PROJECT:-}" ]]; then
    export GOOGLE_CLOUD_PROJECT="${GCP_PROJECT_ID}"
  elif [[ -n "${GOOGLE_CLOUD_PROJECT:-}" && -z "${GCP_PROJECT_ID:-}" ]]; then
    export GCP_PROJECT_ID="${GOOGLE_CLOUD_PROJECT}"
  fi
}

_loadenv_source_file() {
  local file_path="$1"
  if [[ -f "${file_path}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${file_path}"
    set +a
  fi
}

_loadenv_get_var() {
  local name="$1"
  eval "printf '%s' \"\${${name}:-}\""
}

_loadenv_print_summary() {
  local name
  local summary_vars=(
    GCP_PROJECT_ID
    GCP_REGION
    GCP_LOCATION
    CES_APP_ID
    CES_ENDPOINT
    MOCK_SERVER_URL
    BFA_GATEWAY_URL
    BFA_ADAPTER_BRANCH_FINDER_URL
    BFA_SERVICE_RESOURCE_URL
    LANGSMITH_ENDPOINT
    LANGSMITH_WORKSPACE_ID
  )

  echo "Loaded environment from:"
  echo "  - ${ROOT_ENV_FILE}"
  if [[ -f "${_LOADENV_STATE_FILE}" ]]; then
    echo "  - ${_LOADENV_STATE_FILE}"
  fi
  echo ""
  echo "Active variables:"

  for name in "${summary_vars[@]}"; do
    if [[ -n "$(_loadenv_get_var "${name}")" ]]; then
      printf '  %s=%s\n' "${name}" "$(_loadenv_get_var "${name}")"
    fi
  done

  echo ""
  echo "Secrets such as API keys are loaded but intentionally not printed."
}

_loadenv_source_file "${ROOT_ENV_FILE}"
_loadenv_source_file "${_LOADENV_STATE_FILE}"
_loadenv_sync_gcp_env_aliases
_loadenv_print_summary

unset _LOADENV_SELF
unset _LOADENV_ROOT
unset _LOADENV_DEFAULT_STATE_FILE
unset _LOADENV_STATE_FILE
unset ROOT_ENV_FILE
unset -f _loadenv_get_var
unset -f _loadenv_source_file
unset -f _loadenv_sync_gcp_env_aliases
unset -f _loadenv_print_summary
