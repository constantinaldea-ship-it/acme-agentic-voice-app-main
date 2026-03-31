#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Deploy individual Cloud Run services or the full discovery-plan stack.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
# shellcheck source=./lib/services.sh
source "${SCRIPT_DIR}/lib/services.sh"

usage() {
  cat <<'EOF'
Usage:
  scripts/cloud/deploy.sh service <service-name> [project-id]
  scripts/cloud/deploy.sh stack discovery-plan [project-id]

The optional project-id argument overrides GCP_PROJECT_ID from the repository root .env.

Supported services:
  mock-server
  bfa-adapter-branch-finder
  bfa-gateway
  bfa-service-resource
EOF
}

collect_invoker_members() {
  local runtime_service_account="$1"
  local ces_service_agent="$2"
  local members=""
  local extra_members="${CLOUD_RUN_INVOKER_MEMBERS:-}"
  local member

  members="$(append_csv_value "${members}" "serviceAccount:${runtime_service_account}")"
  members="$(append_csv_value "${members}" "serviceAccount:${ces_service_agent}")"

  if [[ -n "${extra_members}" ]]; then
    IFS=',' read -r -a extra_list <<< "${extra_members}"
    for member in "${extra_list[@]}"; do
      member="${member#${member%%[![:space:]]*}}"
      member="${member%${member##*[![:space:]]}}"
      [[ -n "${member}" ]] || continue
      members="$(append_csv_value "${members}" "${member}")"
    done
  fi

  printf '%s' "${members}"
}

grant_invoker_bindings() {
  local service_name="$1"
  local project_id="$2"
  local region="$3"
  local members_csv="$4"
  local member

  IFS=',' read -r -a members <<< "${members_csv}"
  for member in "${members[@]}"; do
    [[ -n "${member}" ]] || continue
    log_info "Granting Cloud Run invoker to ${member}"
    gcloud run services add-iam-policy-binding "${service_name}" \
      --project "${project_id}" \
      --region "${region}" \
      --member "${member}" \
      --role roles/run.invoker >/dev/null
  done
}

resolve_dependency_env() {
  local service="$1"
  local region="$2"
  local project_id="$3"
  local env_vars="${STATIC_ENV_VARS:-}"
  local dependency_url

  case "${service}" in
    bfa-gateway)
      dependency_url="${ADAPTER_BRANCH_FINDER_URL:-}"
      if [[ -z "${dependency_url}" ]]; then
        dependency_url="$(gcloud_service_url "${BFA_ADAPTER_BRANCH_FINDER_SERVICE_NAME:-bfa-adapter-branch-finder}" "${region}" "${project_id}")"
      fi
      [[ -n "${dependency_url}" ]] || fail "Unable to resolve ADAPTER_BRANCH_FINDER_URL. Deploy bfa-adapter-branch-finder first or export ADAPTER_BRANCH_FINDER_URL."
      env_vars="$(append_env_var "${env_vars}" "ADAPTER_BRANCH_FINDER_URL=${dependency_url}")"
      ;;
    bfa-service-resource)
      dependency_url="${BFA_APPOINTMENT_UPSTREAM_BASE_URL:-${MOCK_SERVER_URL:-}}"
      if [[ -z "${dependency_url}" ]]; then
        dependency_url="$(gcloud_service_url "${MOCK_SERVER_SERVICE_NAME:-mock-server}" "${region}" "${project_id}")"
      fi
      [[ -n "${dependency_url}" ]] || fail "Unable to resolve BFA_APPOINTMENT_UPSTREAM_BASE_URL. Deploy mock-server first or export BFA_APPOINTMENT_UPSTREAM_BASE_URL."
      env_vars="$(append_env_var "${env_vars}" "BFA_APPOINTMENT_UPSTREAM_BASE_URL=${dependency_url}")"
      ;;
  esac

  if [[ "${service}" == "mock-server" ]]; then
    if [[ -n "${MOCK_API_KEY:-}" ]]; then
      env_vars="$(append_env_var "${env_vars}" "MOCK_API_KEY=${MOCK_API_KEY}")"
    else
      log_warn "MOCK_API_KEY is not set; mock-server will deploy without API-key protection."
    fi
  fi

  printf '%s' "${env_vars}"
}

deploy_service() {
  local service="$1"
  local project_id="$2"
  local region="$3"
  local project_number="$4"
  local runtime_service_account="$5"
  local ces_service_agent="$6"

  load_service_config "${service}" || fail "Unsupported service: ${service}"
  local image_uri="gcr.io/${project_id}/${CLOUD_RUN_SERVICE}"
  local env_vars
  local invoker_members

  log_section "Deploying ${service}"
  log_info "Cloud Run service: ${CLOUD_RUN_SERVICE}"
  log_info "Maven module: ${MAVEN_MODULE}"
  log_info "Image: ${image_uri}"
  log_info "Region: ${region}"
  log_info "Runtime service account: ${runtime_service_account}"

  env_vars="$(resolve_dependency_env "${service}" "${region}" "${project_id}")"
  invoker_members="$(collect_invoker_members "${runtime_service_account}" "${ces_service_agent}")"

  if [[ "${EXPORT_OPENAPI:-false}" == "true" ]]; then
    log_info "Refreshing exported OpenAPI specs"
    (
      cd "${REPO_ROOT}/java"
      mvn -pl "${MAVEN_MODULE}" -am \
        -Dtest="${OPENAPI_TEST_SELECTOR}" \
        -Dsurefire.failIfNoSpecifiedTests=false \
        test
    ) >&2
  fi

  log_info "Building module with Maven"
  (
    cd "${REPO_ROOT}/java"
    mvn -pl "${MAVEN_MODULE}" -am clean package -DskipTests
  ) >&2

  log_info "Building Docker image"
  docker build --platform linux/amd64 \
    -f "${REPO_ROOT}/${DOCKERFILE_PATH}" \
    -t "${image_uri}" \
    "${REPO_ROOT}/${BUILD_CONTEXT}" >&2

  log_info "Pushing image"
  docker push "${image_uri}" >&2

  log_info "Deploying to Cloud Run"
  local deploy_args=(
    run deploy "${CLOUD_RUN_SERVICE}"
    --project "${project_id}"
    --image "${image_uri}"
    --platform managed
    --region "${region}"
    --port "${CLOUD_RUN_PORT}"
    --memory "${MEMORY}"
    --cpu "${CPU}"
    --timeout "${TIMEOUT}"
    --max-instances "${MAX_INSTANCES}"
    --min-instances "${MIN_INSTANCES}"
    --execution-environment "${EXECUTION_ENVIRONMENT}"
    --service-account "${runtime_service_account}"
  )

  if [[ "${ALLOW_UNAUTHENTICATED}" == "true" ]]; then
    deploy_args+=(--allow-unauthenticated)
  else
    deploy_args+=(--no-allow-unauthenticated)
  fi

  if [[ -n "${env_vars}" ]]; then
    deploy_args+=(--set-env-vars "${env_vars}")
  fi

  gcloud "${deploy_args[@]}" >&2
  grant_invoker_bindings "${CLOUD_RUN_SERVICE}" "${project_id}" "${region}" "${invoker_members}"

  local service_url
  service_url="$(gcloud_service_url "${CLOUD_RUN_SERVICE}" "${region}" "${project_id}")"
  [[ -n "${service_url}" ]] || fail "Deployment finished but Cloud Run URL could not be resolved for ${service}."
  log_info "Service URL: ${service_url}"
  printf '%s\n' "${service_url}"
}

deploy_stack() {
  local stack_name="$1"
  local project_id="$2"
  local region="$3"
  local project_number="$4"
  local runtime_service_account="$5"
  local ces_service_agent="$6"
  local services_line
  local mock_url=""
  local adapter_url=""
  local gateway_url=""
  local resource_url=""

  services_line="$(stack_services "${stack_name}")" || fail "Unsupported stack: ${stack_name}"

  for service in ${services_line}; do
    case "${service}" in
      mock-server)
        mock_url="$(deploy_service "${service}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}")"
        export MOCK_SERVER_URL="${mock_url}"
        ;;
      bfa-adapter-branch-finder)
        adapter_url="$(deploy_service "${service}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}")"
        export ADAPTER_BRANCH_FINDER_URL="${adapter_url}"
        ;;
      bfa-gateway)
        gateway_url="$(deploy_service "${service}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}")"
        export BFA_GATEWAY_URL="${gateway_url}"
        ;;
      bfa-service-resource)
        resource_url="$(deploy_service "${service}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}")"
        export BFA_SERVICE_RESOURCE_URL="${resource_url}"
        ;;
    esac
  done

  local state_file
  state_file="$(write_stack_state "${stack_name}" "${project_id}" "${region}" "${mock_url}" "${adapter_url}" "${gateway_url}" "${resource_url}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}")"
  log_section "Stack deployment complete"
  log_info "State file: ${state_file}"
  log_info "mock-server: ${mock_url}"
  log_info "bfa-adapter-branch-finder: ${adapter_url}"
  log_info "bfa-gateway: ${gateway_url}"
  log_info "bfa-service-resource: ${resource_url}"
}

main() {
  require_commands gcloud docker mvn

  local mode="${1:-}"
  local target="${2:-}"
  local project_id
  local project_number
  local region
  local runtime_service_account
  local ces_service_agent

  [[ -n "${mode}" && -n "${target}" ]] || {
    usage
    exit 1
  }

  if [[ -n "${3:-}" ]]; then
    set_gcp_env_override GCP_PROJECT_ID "${3}"
  fi

  require_cloud_run_env

  project_id="$(resolve_project_id)" || fail "No GCP project configured. Set GCP_PROJECT_ID in ${ROOT_ENV_FILE}."
  region="$(resolve_region)" || fail "No GCP region configured. Set GCP_REGION in ${ROOT_ENV_FILE}."
  project_number="$(resolve_project_number "${project_id}")"
  [[ -n "${project_number}" ]] || fail "Unable to resolve project number for ${project_id}."
  runtime_service_account="$(resolve_cloud_run_runtime_service_account "${project_id}" "${project_number}")"
  ces_service_agent="$(resolve_ces_service_agent "${project_number}")"

  case "${mode}" in
    service)
      deploy_service "${target}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}"
      ;;
    stack)
      deploy_stack "${target}" "${project_id}" "${region}" "${project_number}" "${runtime_service_account}" "${ces_service_agent}"
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"

