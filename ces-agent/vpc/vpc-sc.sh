#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CES_AGENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
ROOT_ENV_FILE="${ROOT_ENV_FILE:-${REPO_ROOT}/.env}"
DEFAULT_STATE_FILE="${REPO_ROOT}/.tmp/cloud-run/discovery-plan.env"

DEFAULT_RESTRICTED_SERVICES="ces.googleapis.com,contactcenterinsights.googleapis.com,storage.googleapis.com,bigquery.googleapis.com"
DEFAULT_PERIMETER_NAME="acme_voice_us_perimeter"
DEFAULT_PERIMETER_TITLE="Acme Voice US Perimeter"
DEFAULT_PERIMETER_DESCRIPTION="VPC Service Controls perimeter for the CES banking agent project."

print_usage() {
  cat <<'EOF'
Usage:
  ./vpc-sc.sh config
  ./vpc-sc.sh status
  ./vpc-sc.sh dry-run-on
  ./vpc-sc.sh enforce-on
  ./vpc-sc.sh dry-run-off
  ./vpc-sc.sh off

Purpose:
  Manage a VPC Service Controls perimeter for the current CES project with a
  simple reversible workflow built on top of gcloud Access Context Manager.

Primary environment variables:
  GCP_PROJECT_ID                  Current Google Cloud project ID
  VPC_SC_ACCESS_POLICY_ID         Access Context Manager policy ID
  VPC_SC_PROJECT_NUMBER           Optional project number override
  VPC_SC_PROJECTS                 Optional CSV of perimeter resources in
                                  'projects/<number>' form
  VPC_SC_PERIMETER_NAME           Optional perimeter short name
  VPC_SC_PERIMETER_TITLE          Optional perimeter title
  VPC_SC_PERIMETER_DESCRIPTION    Optional perimeter description
  VPC_SC_RESTRICTED_SERVICES      Optional CSV of restricted services
  VPC_SC_ACCESS_LEVELS            Optional CSV of accessPolicies/.../accessLevels/...
  VPC_SC_INGRESS_POLICIES_FILE    Optional YAML file path
  VPC_SC_EGRESS_POLICIES_FILE     Optional YAML file path
  VPC_SC_ENABLE_VPC_ACCESSIBLE_SERVICES
                                  Set to true to enable VPC accessible services
  VPC_SC_VPC_ALLOWED_SERVICES     CSV of allowed services or RESTRICTED-SERVICES

Examples:
  source /Users/constantinaldea/IdeaProjects/ai-account-balance/loadenv.sh
  export VPC_SC_ACCESS_POLICY_ID=123456789
  /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh dry-run-on
  /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh enforce-on
  /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh off
EOF
}

load_env_file() {
  local file_path="$1"
  if [[ -f "${file_path}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${file_path}"
    set +a
  fi
}

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    echo "ERROR: Required command not found: ${command_name}" >&2
    exit 1
  fi
}

join_by_comma() {
  local joined=""
  local item
  for item in "$@"; do
    if [[ -z "${item}" ]]; then
      continue
    fi
    if [[ -n "${joined}" ]]; then
      joined+=","
    fi
    joined+="${item}"
  done
  printf '%s' "${joined}"
}

csv_to_array() {
  local csv="$1"
  local -n out_ref="$2"
  out_ref=()
  if [[ -z "${csv}" ]]; then
    return 0
  fi

  local old_ifs="${IFS}"
  IFS=',' read -r -a out_ref <<< "${csv}"
  IFS="${old_ifs}"

  local trimmed=()
  local value
  for value in "${out_ref[@]}"; do
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    if [[ -n "${value}" ]]; then
      trimmed+=("${value}")
    fi
  done
  out_ref=("${trimmed[@]}")
}

is_truthy() {
  local value="${1:-}"
  case "${value,,}" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

resolve_project_number() {
  if [[ -n "${VPC_SC_PROJECT_NUMBER:-}" ]]; then
    printf '%s' "${VPC_SC_PROJECT_NUMBER}"
    return 0
  fi

  if [[ -z "${GCP_PROJECT_ID:-}" ]]; then
    echo "ERROR: Set GCP_PROJECT_ID or VPC_SC_PROJECT_NUMBER before running vpc-sc.sh." >&2
    exit 1
  fi

  gcloud projects describe "${GCP_PROJECT_ID}" --format='value(projectNumber)'
}

perimeter_exists() {
  gcloud access-context-manager perimeters describe "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" >/dev/null 2>&1
}

dry_run_spec_exists() {
  if ! perimeter_exists; then
    return 1
  fi

  gcloud access-context-manager perimeters describe "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" \
    --format=json | python3 -c '
import json
import sys

payload = json.load(sys.stdin)
sys.exit(0 if payload.get("useExplicitDryRunSpec") else 1)
'
}

build_common_lists() {
  PROJECT_NUMBER="$(resolve_project_number)"

  if [[ -n "${VPC_SC_PROJECTS:-}" ]]; then
    csv_to_array "${VPC_SC_PROJECTS}" PERIMETER_PROJECTS
  else
    PERIMETER_PROJECTS=("projects/${PROJECT_NUMBER}")
  fi

  csv_to_array "${VPC_SC_RESTRICTED_SERVICES:-${DEFAULT_RESTRICTED_SERVICES}}" RESTRICTED_SERVICES
  csv_to_array "${VPC_SC_ACCESS_LEVELS:-}" ACCESS_LEVELS
  csv_to_array "${VPC_SC_VPC_ALLOWED_SERVICES:-}" VPC_ALLOWED_SERVICES
}

build_policy_flags() {
  COMMON_POLICY_FLAGS=()
  if [[ -f "${VPC_SC_INGRESS_POLICIES_FILE:-}" ]]; then
    COMMON_POLICY_FLAGS+=("${1}" "${VPC_SC_INGRESS_POLICIES_FILE}")
  fi
  if [[ -f "${VPC_SC_EGRESS_POLICIES_FILE:-}" ]]; then
    COMMON_POLICY_FLAGS+=("${2}" "${VPC_SC_EGRESS_POLICIES_FILE}")
  fi
}

print_config() {
  build_common_lists

  cat <<EOF
VPC Service Controls configuration
  access_policy_id: ${VPC_SC_ACCESS_POLICY_ID:-<unset>}
  perimeter_name:   ${VPC_SC_PERIMETER_NAME}
  perimeter_title:  ${VPC_SC_PERIMETER_TITLE}
  project_number:   ${PROJECT_NUMBER}
  resources:        $(join_by_comma "${PERIMETER_PROJECTS[@]}")
  restricted:       $(join_by_comma "${RESTRICTED_SERVICES[@]}")
  access_levels:    $(join_by_comma "${ACCESS_LEVELS[@]}")
  ingress_yaml:     ${VPC_SC_INGRESS_POLICIES_FILE:-<none>}
  egress_yaml:      ${VPC_SC_EGRESS_POLICIES_FILE:-<none>}
  vpc_accessible:   ${VPC_SC_ENABLE_VPC_ACCESSIBLE_SERVICES:-false}
  vpc_allowed:      $(join_by_comma "${VPC_ALLOWED_SERVICES[@]}")
EOF
}

ensure_required_config() {
  require_command gcloud

  if [[ -z "${VPC_SC_ACCESS_POLICY_ID:-}" ]]; then
    echo "ERROR: Set VPC_SC_ACCESS_POLICY_ID before running vpc-sc.sh." >&2
    exit 1
  fi

  VPC_SC_PERIMETER_NAME="${VPC_SC_PERIMETER_NAME:-${DEFAULT_PERIMETER_NAME}}"
  VPC_SC_PERIMETER_TITLE="${VPC_SC_PERIMETER_TITLE:-${DEFAULT_PERIMETER_TITLE}}"
  VPC_SC_PERIMETER_DESCRIPTION="${VPC_SC_PERIMETER_DESCRIPTION:-${DEFAULT_PERIMETER_DESCRIPTION}}"

  export VPC_SC_PERIMETER_NAME
  export VPC_SC_PERIMETER_TITLE
  export VPC_SC_PERIMETER_DESCRIPTION
}

build_create_flags() {
  local resource_flag="$1"
  local restricted_flag="$2"
  local access_flag="$3"
  local ingress_flag="$4"
  local egress_flag="$5"
  local enable_flag="$6"
  local allowed_flag="$7"

  CREATE_FLAGS=(
    "${resource_flag}" "$(join_by_comma "${PERIMETER_PROJECTS[@]}")"
    "${restricted_flag}" "$(join_by_comma "${RESTRICTED_SERVICES[@]}")"
  )

  if [[ ${#ACCESS_LEVELS[@]} -gt 0 ]]; then
    CREATE_FLAGS+=("${access_flag}" "$(join_by_comma "${ACCESS_LEVELS[@]}")")
  fi

  build_policy_flags "${ingress_flag}" "${egress_flag}"
  CREATE_FLAGS+=("${COMMON_POLICY_FLAGS[@]}")

  if is_truthy "${VPC_SC_ENABLE_VPC_ACCESSIBLE_SERVICES:-false}"; then
    CREATE_FLAGS+=("${enable_flag}")
    if [[ ${#VPC_ALLOWED_SERVICES[@]} -gt 0 ]]; then
      CREATE_FLAGS+=("${allowed_flag}" "$(join_by_comma "${VPC_ALLOWED_SERVICES[@]}")")
    fi
  fi
}

create_dry_run_perimeter() {
  build_create_flags \
    --perimeter-resources \
    --perimeter-restricted-services \
    --perimeter-access-levels \
    --perimeter-ingress-policies \
    --perimeter-egress-policies \
    --perimeter-enable-vpc-accessible-services \
    --perimeter-vpc-allowed-services

  gcloud access-context-manager perimeters dry-run create "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" \
    --perimeter-title="${VPC_SC_PERIMETER_TITLE}" \
    --perimeter-description="${VPC_SC_PERIMETER_DESCRIPTION}" \
    --perimeter-type=regular \
    "${CREATE_FLAGS[@]}"
}

create_dry_run_spec_for_existing_perimeter() {
  build_create_flags \
    --resources \
    --restricted-services \
    --access-levels \
    --ingress-policies \
    --egress-policies \
    --enable-vpc-accessible-services \
    --vpc-allowed-services

  gcloud access-context-manager perimeters dry-run create "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" \
    "${CREATE_FLAGS[@]}"
}

create_enforced_perimeter() {
  build_create_flags \
    --resources \
    --restricted-services \
    --access-levels \
    --ingress-policies \
    --egress-policies \
    --enable-vpc-accessible-services \
    --vpc-allowed-services

  gcloud access-context-manager perimeters create "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" \
    --title="${VPC_SC_PERIMETER_TITLE}" \
    --description="${VPC_SC_PERIMETER_DESCRIPTION}" \
    --perimeter-type=regular \
    "${CREATE_FLAGS[@]}"
}

update_enforced_perimeter() {
  UPDATE_FLAGS=(
    --title="${VPC_SC_PERIMETER_TITLE}"
    --description="${VPC_SC_PERIMETER_DESCRIPTION}"
    --type=regular
    --set-resources="$(join_by_comma "${PERIMETER_PROJECTS[@]}")"
    --set-restricted-services="$(join_by_comma "${RESTRICTED_SERVICES[@]}")"
  )

  if [[ ${#ACCESS_LEVELS[@]} -gt 0 ]]; then
    UPDATE_FLAGS+=(--set-access-levels="$(join_by_comma "${ACCESS_LEVELS[@]}")")
  else
    UPDATE_FLAGS+=(--clear-access-levels)
  fi

  if [[ -f "${VPC_SC_INGRESS_POLICIES_FILE:-}" ]]; then
    UPDATE_FLAGS+=(--set-ingress-policies="${VPC_SC_INGRESS_POLICIES_FILE}")
  fi

  if [[ -f "${VPC_SC_EGRESS_POLICIES_FILE:-}" ]]; then
    UPDATE_FLAGS+=(--set-egress-policies="${VPC_SC_EGRESS_POLICIES_FILE}")
  fi

  if is_truthy "${VPC_SC_ENABLE_VPC_ACCESSIBLE_SERVICES:-false}"; then
    UPDATE_FLAGS+=(--enable-vpc-accessible-services)
    if [[ ${#VPC_ALLOWED_SERVICES[@]} -gt 0 ]]; then
      UPDATE_FLAGS+=(--clear-vpc-allowed-services --add-vpc-allowed-services="$(join_by_comma "${VPC_ALLOWED_SERVICES[@]}")")
    fi
  else
    UPDATE_FLAGS+=(--clear-vpc-allowed-services)
  fi

  gcloud access-context-manager perimeters update "${VPC_SC_PERIMETER_NAME}" \
    --policy="${VPC_SC_ACCESS_POLICY_ID}" \
    "${UPDATE_FLAGS[@]}"
}

cmd_dry_run_on() {
  build_common_lists

  if perimeter_exists; then
    if dry_run_spec_exists; then
      gcloud access-context-manager perimeters dry-run drop "${VPC_SC_PERIMETER_NAME}" \
        --policy="${VPC_SC_ACCESS_POLICY_ID}"
    fi
    create_dry_run_spec_for_existing_perimeter
  else
    create_dry_run_perimeter
  fi
}

cmd_enforce_on() {
  build_common_lists

  if perimeter_exists; then
    if dry_run_spec_exists; then
      gcloud access-context-manager perimeters dry-run enforce "${VPC_SC_PERIMETER_NAME}" \
        --policy="${VPC_SC_ACCESS_POLICY_ID}"
    else
      update_enforced_perimeter
    fi
  else
    create_enforced_perimeter
  fi
}

cmd_dry_run_off() {
  if dry_run_spec_exists; then
    gcloud access-context-manager perimeters dry-run drop "${VPC_SC_PERIMETER_NAME}" \
      --policy="${VPC_SC_ACCESS_POLICY_ID}"
  else
    echo "No explicit dry-run spec found for ${VPC_SC_PERIMETER_NAME}."
  fi
}

cmd_off() {
  if perimeter_exists; then
    gcloud access-context-manager perimeters delete "${VPC_SC_PERIMETER_NAME}" \
      --policy="${VPC_SC_ACCESS_POLICY_ID}" \
      --quiet
  else
    echo "No perimeter found for ${VPC_SC_PERIMETER_NAME}."
  fi
}

cmd_status() {
  echo "=== Enforced perimeter ==="
  if perimeter_exists; then
    gcloud access-context-manager perimeters describe "${VPC_SC_PERIMETER_NAME}" \
      --policy="${VPC_SC_ACCESS_POLICY_ID}" \
      --format=yaml
  else
    echo "Perimeter not found."
  fi

  echo ""
  echo "=== Dry-run spec ==="
  if dry_run_spec_exists; then
    gcloud access-context-manager perimeters dry-run describe "${VPC_SC_PERIMETER_NAME}" \
      --policy="${VPC_SC_ACCESS_POLICY_ID}" \
      --format=yaml
  else
    echo "Dry-run spec not found."
  fi
}

COMMAND="${1:-help}"
case "${COMMAND}" in
  help|-h|--help)
    print_usage
    exit 0
    ;;
esac

load_env_file "${ROOT_ENV_FILE}"
load_env_file "${DEFAULT_STATE_FILE}"
ensure_required_config

case "${COMMAND}" in
  config)
    print_config
    ;;
  status)
    cmd_status
    ;;
  dry-run-on)
    cmd_dry_run_on
    ;;
  enforce-on)
    cmd_enforce_on
    ;;
  dry-run-off)
    cmd_dry_run_off
    ;;
  off)
    cmd_off
    ;;
  *)
    echo "ERROR: Unknown command '${COMMAND}'." >&2
    echo "" >&2
    print_usage >&2
    exit 1
    ;;
esac
