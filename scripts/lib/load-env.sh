#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Shared loader for the repository root .env file.

ENV_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_LIB_REPO_ROOT="$(cd "${ENV_LIB_DIR}/../.." && pwd)"
ROOT_ENV_FILE="${ROOT_ENV_FILE:-${ENV_LIB_REPO_ROOT}/.env}"
ROOT_ENV_EXAMPLE_FILE="${ROOT_ENV_EXAMPLE_FILE:-${ENV_LIB_REPO_ROOT}/.env.example}"

sync_gcp_env_aliases() {
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

  # Modified by Augment Agent on 2026-03-31: allow SA_ACCOUNT_LOCATION in .env
  # to drive the standard GCP credential variables used by repo wrappers.
  if [[ -n "${SA_ACCOUNT_LOCATION:-}" ]]; then
    if [[ -z "${GCP_SERVICE_ACCOUNT_KEY:-}" ]]; then
      export GCP_SERVICE_ACCOUNT_KEY="${SA_ACCOUNT_LOCATION}"
    fi
    if [[ -z "${GOOGLE_APPLICATION_CREDENTIALS:-}" ]]; then
      export GOOGLE_APPLICATION_CREDENTIALS="${SA_ACCOUNT_LOCATION}"
    fi
  elif [[ -n "${GCP_SERVICE_ACCOUNT_KEY:-}" && -z "${SA_ACCOUNT_LOCATION:-}" ]]; then
    export SA_ACCOUNT_LOCATION="${GCP_SERVICE_ACCOUNT_KEY}"
  elif [[ -n "${GOOGLE_APPLICATION_CREDENTIALS:-}" && -z "${SA_ACCOUNT_LOCATION:-}" ]]; then
    export SA_ACCOUNT_LOCATION="${GOOGLE_APPLICATION_CREDENTIALS}"
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

  export GCP_CONTAINER_REGISTRY_HOST="${GCP_CONTAINER_REGISTRY_HOST:-gcr.io}"
}

set_gcp_env_override() {
  local name="$1"
  local value="$2"
  export "${name}=${value}"
  sync_gcp_env_aliases
}

load_root_env() {
  local env_file="${1:-${ROOT_ENV_FILE}}"

  if [[ "${ROOT_ENV_LOADED:-false}" == "true" && "${ROOT_ENV_LOADED_FILE:-}" == "${env_file}" ]]; then
    sync_gcp_env_aliases
    return 0
  fi

  if [[ -f "${env_file}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${env_file}"
    set +a
  fi

  export ROOT_ENV_LOADED="true"
  export ROOT_ENV_LOADED_FILE="${env_file}"
  sync_gcp_env_aliases
}

require_env_var() {
  local name="$1"
  local value="${!name:-}"

  if [[ -n "${value}" ]]; then
    return 0
  fi

  printf 'ERROR: %s is not set. Define it in %s or export it before running this script.\n' "${name}" "${ROOT_ENV_FILE}" >&2
  if [[ -f "${ROOT_ENV_EXAMPLE_FILE}" ]]; then
    printf '       You can copy %s to %s and fill in the required values.\n' "${ROOT_ENV_EXAMPLE_FILE}" "${ROOT_ENV_FILE}" >&2
  fi
  return 1
}

require_file_env_var_if_set() {
  local name="$1"
  local value="${!name:-}"

  if [[ -z "${value}" ]]; then
    return 0
  fi

  if [[ -f "${value}" ]]; then
    return 0
  fi

  printf 'ERROR: %s points to a file that does not exist: %s\n' "${name}" "${value}" >&2
  return 1
}

require_env_vars() {
  local name
  local failed=0

  for name in "$@"; do
    if ! require_env_var "${name}"; then
      failed=1
    fi
  done

  if [[ "${failed}" -ne 0 ]]; then
    return 1
  fi

  sync_gcp_env_aliases
  require_file_env_var_if_set GCP_SERVICE_ACCOUNT_KEY
}

require_gcp_run_env() {
  require_env_vars GCP_PROJECT_ID GCP_REGION
}

require_gcp_ces_env() {
  require_env_vars GCP_PROJECT_ID GCP_LOCATION
}

