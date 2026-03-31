#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Remove Cloud Run services individually or as a stack, with optional image cleanup.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
# shellcheck source=./lib/services.sh
source "${SCRIPT_DIR}/lib/services.sh"

usage() {
  cat <<'EOF'
Usage:
  scripts/cloud/undeploy.sh service <service-name> [project-id] [--delete-images] [--force]
  scripts/cloud/undeploy.sh stack discovery-plan [project-id] [--delete-images] [--force]

The optional project-id argument overrides GCP_PROJECT_ID from the repository root .env.
EOF
}

delete_service_image() {
  local image_uri="$1"
  if gcloud container images describe "${image_uri}" >/dev/null 2>&1; then
    log_info "Deleting image ${image_uri}"
    gcloud container images delete "${image_uri}" --quiet --force-delete-tags
  else
    log_warn "Image not found, skipping: ${image_uri}"
  fi
}

undeploy_service() {
  local service="$1"
  local project_id="$2"
  local region="$3"
  local delete_images="$4"

  load_service_config "${service}" || fail "Unsupported service: ${service}"
  local image_uri="gcr.io/${project_id}/${CLOUD_RUN_SERVICE}"

  log_section "Undeploying ${service}"
  if service_exists "${CLOUD_RUN_SERVICE}" "${region}" "${project_id}"; then
    gcloud run services delete "${CLOUD_RUN_SERVICE}" \
      --project "${project_id}" \
      --region "${region}" \
      --quiet
    log_info "Deleted Cloud Run service ${CLOUD_RUN_SERVICE}"
  else
    log_warn "Cloud Run service not found, skipping: ${CLOUD_RUN_SERVICE}"
  fi

  if [[ "${delete_images}" == "true" ]]; then
    delete_service_image "${image_uri}"
  fi
}

undeploy_stack() {
  local stack_name="$1"
  local project_id="$2"
  local region="$3"
  local delete_images="$4"
  local services=()
  local service

  while IFS= read -r service; do
    services+=("${service}")
  done < <(stack_services "${stack_name}")

  for (( idx=${#services[@]}-1; idx>=0; idx-- )); do
    undeploy_service "${services[idx]}" "${project_id}" "${region}" "${delete_images}"
  done

  local state_file="${STATE_DIR}/${stack_name}.env"
  if [[ -f "${state_file}" ]]; then
    rm -f "${state_file}"
    log_info "Removed stack state file ${state_file}"
  fi
}

main() {
  require_commands gcloud

  local mode="${1:-}"
  local target="${2:-}"
  local project_arg=""
  local region
  local delete_images="false"
  local force="false"
  shift 2 || true

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --delete-images)
        delete_images="true"
        ;;
      --force)
        force="true"
        ;;
      *)
        if [[ -z "${project_arg}" ]]; then
          project_arg="$1"
        else
          fail "Unexpected argument: $1"
        fi
        ;;
    esac
    shift
  done

  [[ -n "${mode}" && -n "${target}" ]] || {
    usage
    exit 1
  }

  if [[ -n "${project_arg}" ]]; then
    set_gcp_env_override GCP_PROJECT_ID "${project_arg}"
  fi

  require_cloud_run_env

  local project_id
  project_id="$(resolve_project_id)" || fail "No GCP project configured. Set GCP_PROJECT_ID in ${ROOT_ENV_FILE}."
  region="$(resolve_region)" || fail "No GCP region configured. Set GCP_REGION in ${ROOT_ENV_FILE}."

  if [[ "${force}" != "true" ]]; then
    fail "Refusing to undeploy without --force. This guard helps prevent accidental cost-impacting deletions."
  fi

  case "${mode}" in
    service)
      undeploy_service "${target}" "${project_id}" "${region}" "${delete_images}"
      ;;
    stack)
      undeploy_stack "${target}" "${project_id}" "${region}" "${delete_images}"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"

