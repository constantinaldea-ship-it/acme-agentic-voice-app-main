#!/usr/bin/env bash
# Created by GitHub Copilot on 2026-03-27.
# Manage the Vertex AI Search datastore lifecycle for fee_schedule_agent.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CES_AGENT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
# shellcheck source=../../../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env

COMMAND="${1:-help}"
if [[ $# -gt 0 ]]; then
  shift
fi

DRY_RUN=false
PROJECT_OVERRIDE=""
CES_LOCATION_OVERRIDE=""
DATASTORE_LOCATION_OVERRIDE=""
DATASTORE_ID_OVERRIDE=""
DATASTORE_DISPLAY_NAME_OVERRIDE=""
BUCKET_URI_OVERRIDE=""
BUCKET_LOCATION_OVERRIDE=""
SOURCE_URL_OVERRIDE=""
OBJECT_NAME_OVERRIDE=""
TOOL_JSON_OVERRIDE=""
DELETE_BUCKET_OVERRIDE=""
BRANCH_ID_OVERRIDE=""
COLLECTION_ID_OVERRIDE=""
TIMEOUT_OVERRIDE=""
POLL_OVERRIDE=""

usage() {
  cat <<'EOF'
Usage:
  ces-agent/scripts/deploy/manage_datastore.sh provision [options]
  ces-agent/scripts/deploy/manage_datastore.sh deprovision [options]
  ces-agent/scripts/deploy/manage_datastore.sh status [options]
  ces-agent/scripts/deploy/manage_datastore.sh sync-tool-config [options]

Commands:
  provision          Create/update the fee-schedule datastore, upload the PDF,
                     import documents, and sync fee_schedule_lookup.json.
  deprovision        Delete the datastore and, by default, the dedicated GCS bucket.
  status             Print the resolved configuration and live resource status.
  sync-tool-config   Update fee_schedule_lookup.json to the resolved dataStoreId.

Options:
  --project <id>                 Override GCP project ID (default: GCP_PROJECT_ID)
  --location <us|eu>             Override CES location (default: GCP_LOCATION)
  --datastore-location <value>   Discovery Engine datastore location (default: global)
  --datastore-id <id>            Datastore ID
  --display-name <name>          Datastore display name
  --collection <id>              Discovery Engine collection (default: default_collection)
  --branch-id <id>               Branch used for document import (default: default_branch)
  --bucket-uri <gs://bucket>     Bucket used as the PDF source
  --bucket-location <value>      Bucket location/region/multi-region
  --source-url <url>             Public PDF source URL
  --object-name <name>           Object name stored in the bucket
  --tool-json <path>             Override fee_schedule_lookup.json path
  --delete-bucket <true|false>   Delete the source bucket during deprovision
  --timeout-seconds <n>          Operation timeout (default: 900)
  --poll-seconds <n>             Operation poll interval (default: 5)
  --dry-run                      Print actions without mutating cloud resources
  --help, -h                     Show this help

Environment variables:
  GCP_PROJECT_ID                         Required for provision/deprovision/status
  GCP_LOCATION                           Optional CES location (used by wrappers)
  FEE_SCHEDULE_DATASTORE_LOCATION        Default: global
  FEE_SCHEDULE_DATASTORE_COLLECTION      Default: default_collection
  FEE_SCHEDULE_DATASTORE_ID              Default: fee-schedule-lookup_1771833548805
  FEE_SCHEDULE_DATASTORE_DISPLAY_NAME    Default: fee_schedule_lookup
  FEE_SCHEDULE_BRANCH_ID                 Default: default_branch
  FEE_SCHEDULE_BUCKET_URI                Default: gs://<project>-fee-schedule
  FEE_SCHEDULE_BUCKET_LOCATION           Default: derived from GCP_LOCATION/GCP_REGION
  FEE_SCHEDULE_SOURCE_URL                Default: Deutsche Bank fee schedule PDF URL
  FEE_SCHEDULE_OBJECT_NAME               Default: basename of source URL
  FEE_SCHEDULE_TOOL_JSON                 Default: ces-agent/acme_voice_agent/tools/fee_schedule_lookup/fee_schedule_lookup.json
  FEE_SCHEDULE_DELETE_BUCKET_ON_DEPROVISION  Default: true
EOF
}

log_info() {
  printf '  • %s\n' "$1"
}

log_warn() {
  printf '  ! %s\n' "$1" >&2
}

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

require_value() {
  local flag="$1"
  local value="${2:-}"
  if [[ -z "${value}" ]]; then
    fail "${flag} requires a value"
  fi
}

require_command() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    fail "Required command not found: ${cmd}"
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) require_value "$1" "${2:-}"; PROJECT_OVERRIDE="$2"; shift 2 ;;
    --location) require_value "$1" "${2:-}"; CES_LOCATION_OVERRIDE="$2"; shift 2 ;;
    --datastore-location) require_value "$1" "${2:-}"; DATASTORE_LOCATION_OVERRIDE="$2"; shift 2 ;;
    --datastore-id) require_value "$1" "${2:-}"; DATASTORE_ID_OVERRIDE="$2"; shift 2 ;;
    --display-name) require_value "$1" "${2:-}"; DATASTORE_DISPLAY_NAME_OVERRIDE="$2"; shift 2 ;;
    --collection) require_value "$1" "${2:-}"; COLLECTION_ID_OVERRIDE="$2"; shift 2 ;;
    --branch-id) require_value "$1" "${2:-}"; BRANCH_ID_OVERRIDE="$2"; shift 2 ;;
    --bucket-uri) require_value "$1" "${2:-}"; BUCKET_URI_OVERRIDE="$2"; shift 2 ;;
    --bucket-location) require_value "$1" "${2:-}"; BUCKET_LOCATION_OVERRIDE="$2"; shift 2 ;;
    --source-url) require_value "$1" "${2:-}"; SOURCE_URL_OVERRIDE="$2"; shift 2 ;;
    --object-name) require_value "$1" "${2:-}"; OBJECT_NAME_OVERRIDE="$2"; shift 2 ;;
    --tool-json) require_value "$1" "${2:-}"; TOOL_JSON_OVERRIDE="$2"; shift 2 ;;
    --delete-bucket) require_value "$1" "${2:-}"; DELETE_BUCKET_OVERRIDE="$2"; shift 2 ;;
    --timeout-seconds) require_value "$1" "${2:-}"; TIMEOUT_OVERRIDE="$2"; shift 2 ;;
    --poll-seconds) require_value "$1" "${2:-}"; POLL_OVERRIDE="$2"; shift 2 ;;
    --dry-run) DRY_RUN=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) fail "Unknown option: $1" ;;
  esac
done

if [[ -n "${PROJECT_OVERRIDE}" ]]; then
  set_gcp_env_override GCP_PROJECT_ID "${PROJECT_OVERRIDE}"
fi
if [[ -n "${CES_LOCATION_OVERRIDE}" ]]; then
  set_gcp_env_override GCP_LOCATION "${CES_LOCATION_OVERRIDE}"
fi

PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-}}"
CES_LOCATION="${GCP_LOCATION:-${LOCATION:-eu}}"
DATASTORE_LOCATION="${DATASTORE_LOCATION_OVERRIDE:-${FEE_SCHEDULE_DATASTORE_LOCATION:-global}}"
COLLECTION_ID="${COLLECTION_ID_OVERRIDE:-${FEE_SCHEDULE_DATASTORE_COLLECTION:-default_collection}}"
DATASTORE_ID="${DATASTORE_ID_OVERRIDE:-${FEE_SCHEDULE_DATASTORE_ID:-fee-schedule-lookup_1771833548805}}"
DATASTORE_DISPLAY_NAME="${DATASTORE_DISPLAY_NAME_OVERRIDE:-${FEE_SCHEDULE_DATASTORE_DISPLAY_NAME:-fee_schedule_lookup}}"
BRANCH_ID="${BRANCH_ID_OVERRIDE:-${FEE_SCHEDULE_BRANCH_ID:-default_branch}}"
SOURCE_URL="${SOURCE_URL_OVERRIDE:-${FEE_SCHEDULE_SOURCE_URL:-https://www.deutsche-bank.de/dam/deutschebank/de/shared/pdf/kontakt-und-service/list-of-prices-and-Services-deutsche-bank-ag.pdf}}"
OBJECT_NAME_DEFAULT="$(basename "${SOURCE_URL%%\?*}")"
OBJECT_NAME="${OBJECT_NAME_OVERRIDE:-${FEE_SCHEDULE_OBJECT_NAME:-${OBJECT_NAME_DEFAULT}}}"
TOOL_JSON="${TOOL_JSON_OVERRIDE:-${FEE_SCHEDULE_TOOL_JSON:-${CES_AGENT_DIR}/acme_voice_agent/tools/fee_schedule_lookup/fee_schedule_lookup.json}}"
DELETE_BUCKET_ON_DEPROVISION="${DELETE_BUCKET_OVERRIDE:-${FEE_SCHEDULE_DELETE_BUCKET_ON_DEPROVISION:-true}}"
OPERATION_TIMEOUT_SECONDS="${TIMEOUT_OVERRIDE:-${FEE_SCHEDULE_OPERATION_TIMEOUT_SECONDS:-900}}"
POLL_INTERVAL_SECONDS="${POLL_OVERRIDE:-${FEE_SCHEDULE_POLL_INTERVAL_SECONDS:-5}}"

if [[ -n "${BUCKET_URI_OVERRIDE}" ]]; then
  BUCKET_URI="${BUCKET_URI_OVERRIDE}"
else
  BUCKET_URI="${FEE_SCHEDULE_BUCKET_URI:-}"
  if [[ -z "${BUCKET_URI}" && -n "${PROJECT_ID}" ]]; then
    BUCKET_URI="gs://${PROJECT_ID}-fee-schedule"
  fi
fi

bucket_location_default() {
  if [[ -n "${BUCKET_LOCATION_OVERRIDE}" ]]; then
    printf '%s' "${BUCKET_LOCATION_OVERRIDE}"
    return 0
  fi
  if [[ -n "${FEE_SCHEDULE_BUCKET_LOCATION:-}" ]]; then
    printf '%s' "${FEE_SCHEDULE_BUCKET_LOCATION}"
    return 0
  fi
  case "${CES_LOCATION}" in
    eu) printf 'EU' ;;
    us) printf 'US' ;;
    *)
      if [[ -n "${GCP_REGION:-}" ]]; then
        printf '%s' "${GCP_REGION}"
      else
        printf 'US'
      fi
      ;;
  esac
}

BUCKET_LOCATION="$(bucket_location_default)"
DATASTORE_RESOURCE=""
OBJECT_URI=""
if [[ -n "${PROJECT_ID}" ]]; then
  DATASTORE_RESOURCE="projects/${PROJECT_ID}/locations/${DATASTORE_LOCATION}/collections/${COLLECTION_ID}/dataStores/${DATASTORE_ID}"
fi
if [[ -n "${BUCKET_URI}" ]]; then
  OBJECT_URI="${BUCKET_URI%/}/${OBJECT_NAME}"
fi

require_project() {
  if [[ -z "${PROJECT_ID}" ]]; then
    fail "GCP_PROJECT_ID is required. Set it in .env or pass --project."
  fi
}

run_cmd() {
  if [[ "${DRY_RUN}" == true ]]; then
    printf '[dry-run] '
    printf '%q ' "$@"
    printf '\n'
    return 0
  fi
  "$@"
}

get_access_token() {
  local token=""
  token="$(gcloud auth print-access-token 2>/dev/null || true)"
  if [[ -n "${token}" ]]; then
    printf '%s' "${token}"
    return 0
  fi
  token="$(gcloud auth application-default print-access-token 2>/dev/null || true)"
  if [[ -n "${token}" ]]; then
    printf '%s' "${token}"
    return 0
  fi
  fail "Failed to obtain a Google Cloud access token. Run 'gcloud auth login' or configure ADC."
}

api_request() {
  local method="$1"
  local url="$2"
  local body_file="${3:-}"
  local output_file="$4"
  local token="$5"
  local http_code
  local curl_args=(
    -sS
    -o "${output_file}"
    -w '%{http_code}'
    -X "${method}"
    -H "Authorization: Bearer ${token}"
    -H "x-goog-user-project: ${PROJECT_ID}"
    -H 'Accept: application/json'
  )
  if [[ -n "${body_file}" ]]; then
    curl_args+=( -H 'Content-Type: application/json' --data @"${body_file}" )
  fi
  http_code="$(curl "${curl_args[@]}" "${url}")"
  printf '%s' "${http_code}"
}

parse_json_field() {
  local file_path="$1"
  local expression="$2"
  python3 - "$file_path" "$expression" <<'PY'
import json
import sys
path = [part for part in sys.argv[2].split('.') if part]
with open(sys.argv[1], encoding='utf-8') as handle:
    payload = json.load(handle)
value = payload
for part in path:
    if isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break
if value is None:
    print("")
elif isinstance(value, bool):
    print("true" if value else "false")
else:
    print(value)
PY
}

wait_for_operation() {
  local operation_name="$1"
  local token="$2"
  local deadline=$((SECONDS + OPERATION_TIMEOUT_SECONDS))
  local output_file http_code done_value error_value
  output_file="$(mktemp)"
  while (( SECONDS < deadline )); do
    http_code="$(api_request GET "https://discoveryengine.googleapis.com/v1/${operation_name}" "" "${output_file}" "${token}")"
    if [[ ! "${http_code}" =~ ^2 ]]; then
      cat "${output_file}" >&2 || true
      rm -f "${output_file}"
      fail "Polling operation ${operation_name} failed with HTTP ${http_code}"
    fi
    done_value="$(parse_json_field "${output_file}" 'done')"
    if [[ "${done_value}" == "true" ]]; then
      error_value="$(parse_json_field "${output_file}" 'error.message')"
      if [[ -n "${error_value}" ]]; then
        cat "${output_file}" >&2 || true
        rm -f "${output_file}"
        fail "Operation ${operation_name} failed: ${error_value}"
      fi
      rm -f "${output_file}"
      return 0
    fi
    sleep "${POLL_INTERVAL_SECONDS}"
  done
  rm -f "${output_file}"
  fail "Timed out waiting for operation ${operation_name}"
}

sync_tool_config() {
  [[ -n "${DATASTORE_RESOURCE}" ]] || fail "Cannot sync tool config without a resolved datastore resource."
  [[ -f "${TOOL_JSON}" ]] || fail "Tool JSON not found: ${TOOL_JSON}"
  log_info "Syncing ${TOOL_JSON} -> ${DATASTORE_RESOURCE}"
  python3 - "${TOOL_JSON}" "${DATASTORE_RESOURCE}" <<'PY'
import json
import sys
from pathlib import Path
path = Path(sys.argv[1])
datastore = sys.argv[2]
payload = json.loads(path.read_text(encoding='utf-8'))
search_tool = payload.get('googleSearchTool')
if not isinstance(search_tool, dict):
    raise SystemExit('googleSearchTool block missing from fee_schedule_lookup.json')
search_tool['dataStoreId'] = datastore
path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
PY
}

datastore_status_code() {
  local token="$1"
  local output_file="$2"
  api_request GET "https://discoveryengine.googleapis.com/v1/${DATASTORE_RESOURCE}" "" "${output_file}" "${token}"
}

ensure_bucket() {
  require_project
  [[ -n "${BUCKET_URI}" ]] || fail "Bucket URI could not be resolved."
  if gcloud storage buckets describe "${BUCKET_URI}" >/dev/null 2>&1; then
    log_info "Bucket already exists: ${BUCKET_URI}"
    return 0
  fi
  log_info "Creating bucket ${BUCKET_URI} (${BUCKET_LOCATION})"
  run_cmd gcloud storage buckets create "${BUCKET_URI}" --project="${PROJECT_ID}" --location="${BUCKET_LOCATION}" --uniform-bucket-level-access
}

upload_source_pdf() {
  require_project
  [[ -n "${OBJECT_URI}" ]] || fail "Object URI could not be resolved."
  if [[ "${DRY_RUN}" == true ]]; then
    log_info "Would download ${SOURCE_URL} and upload it to ${OBJECT_URI}"
    return 0
  fi
  local tmp_file
  tmp_file="$(mktemp)"
  log_info "Downloading source PDF: ${SOURCE_URL}"
  curl -fsSL --retry 3 --retry-delay 2 "${SOURCE_URL}" -o "${tmp_file}"
  log_info "Uploading PDF to ${OBJECT_URI}"
  gcloud storage cp "${tmp_file}" "${OBJECT_URI}" >/dev/null
  rm -f "${tmp_file}"
}

ensure_datastore() {
  local token="$1"
  local output_file http_code body_file operation_name
  output_file="$(mktemp)"
  http_code="$(datastore_status_code "${token}" "${output_file}")"
  if [[ "${http_code}" == "200" ]]; then
    log_info "Datastore already exists: ${DATASTORE_RESOURCE}"
    rm -f "${output_file}"
    return 0
  fi
  if [[ "${http_code}" != "404" ]]; then
    cat "${output_file}" >&2 || true
    rm -f "${output_file}"
    fail "Datastore lookup failed with HTTP ${http_code}"
  fi
  rm -f "${output_file}"

  if [[ "${DRY_RUN}" == true ]]; then
    log_info "Would create datastore ${DATASTORE_RESOURCE}"
    return 0
  fi

  body_file="$(mktemp)"
  cat > "${body_file}" <<EOF
{
  "displayName": "${DATASTORE_DISPLAY_NAME}",
  "industryVertical": "GENERIC",
  "solutionTypes": ["SOLUTION_TYPE_SEARCH"],
  "contentConfig": "CONTENT_REQUIRED",
  "documentProcessingConfig": {
    "defaultParsingConfig": {
      "layoutParsingConfig": {}
    }
  }
}
EOF
  output_file="$(mktemp)"
  log_info "Creating datastore ${DATASTORE_RESOURCE}"
  http_code="$(api_request POST "https://discoveryengine.googleapis.com/v1/projects/${PROJECT_ID}/locations/${DATASTORE_LOCATION}/collections/${COLLECTION_ID}/dataStores?dataStoreId=${DATASTORE_ID}" "${body_file}" "${output_file}" "${token}")"
  rm -f "${body_file}"
  if [[ ! "${http_code}" =~ ^2 ]]; then
    cat "${output_file}" >&2 || true
    rm -f "${output_file}"
    fail "Datastore creation failed with HTTP ${http_code}"
  fi
  operation_name="$(parse_json_field "${output_file}" 'name')"
  rm -f "${output_file}"
  [[ -n "${operation_name}" ]] || fail "Datastore creation did not return an operation name."
  wait_for_operation "${operation_name}" "${token}"
}

import_documents() {
  local token="$1"
  local body_file output_file http_code operation_name
  if [[ "${DRY_RUN}" == true ]]; then
    log_info "Would import ${OBJECT_URI} into ${DATASTORE_RESOURCE}"
    return 0
  fi
  body_file="$(mktemp)"
  cat > "${body_file}" <<EOF
{
  "gcsSource": {
    "inputUris": ["${OBJECT_URI}"],
    "dataSchema": "content"
  },
  "reconciliationMode": "FULL",
  "autoGenerateIds": true
}
EOF
  output_file="$(mktemp)"
  log_info "Importing documents from ${OBJECT_URI}"
  http_code="$(api_request POST "https://discoveryengine.googleapis.com/v1/${DATASTORE_RESOURCE}/branches/${BRANCH_ID}/documents:import" "${body_file}" "${output_file}" "${token}")"
  rm -f "${body_file}"
  if [[ ! "${http_code}" =~ ^2 ]]; then
    cat "${output_file}" >&2 || true
    rm -f "${output_file}"
    fail "Document import failed with HTTP ${http_code}"
  fi
  operation_name="$(parse_json_field "${output_file}" 'name')"
  rm -f "${output_file}"
  [[ -n "${operation_name}" ]] || fail "Document import did not return an operation name."
  wait_for_operation "${operation_name}" "${token}"
}

delete_datastore() {
  local token="$1"
  local output_file http_code operation_name
  output_file="$(mktemp)"
  http_code="$(datastore_status_code "${token}" "${output_file}")"
  if [[ "${http_code}" == "404" ]]; then
    log_info "Datastore already absent: ${DATASTORE_RESOURCE}"
    rm -f "${output_file}"
    return 0
  fi
  if [[ ! "${http_code}" =~ ^2 ]]; then
    cat "${output_file}" >&2 || true
    rm -f "${output_file}"
    fail "Datastore lookup failed with HTTP ${http_code}"
  fi
  rm -f "${output_file}"

  if [[ "${DRY_RUN}" == true ]]; then
    log_info "Would delete datastore ${DATASTORE_RESOURCE}"
    return 0
  fi

  output_file="$(mktemp)"
  log_info "Deleting datastore ${DATASTORE_RESOURCE}"
  http_code="$(api_request DELETE "https://discoveryengine.googleapis.com/v1/${DATASTORE_RESOURCE}" "" "${output_file}" "${token}")"
  if [[ "${http_code}" == "404" ]]; then
    log_info "Datastore already absent: ${DATASTORE_RESOURCE}"
    rm -f "${output_file}"
    return 0
  fi
  if [[ ! "${http_code}" =~ ^2 ]]; then
    cat "${output_file}" >&2 || true
    rm -f "${output_file}"
    fail "Datastore deletion failed with HTTP ${http_code}"
  fi
  operation_name="$(parse_json_field "${output_file}" 'name')"
  rm -f "${output_file}"
  if [[ -n "${operation_name}" ]]; then
    wait_for_operation "${operation_name}" "${token}"
  fi
}

delete_bucket() {
  local output status
  [[ -n "${BUCKET_URI}" ]] || return 0
  if [[ "${DELETE_BUCKET_ON_DEPROVISION}" != "true" ]]; then
    log_info "Keeping source bucket ${BUCKET_URI} (delete disabled)"
    return 0
  fi
  if ! gcloud storage buckets describe "${BUCKET_URI}" >/dev/null 2>&1; then
    log_info "Bucket already absent: ${BUCKET_URI}"
    return 0
  fi
  log_info "Deleting bucket ${BUCKET_URI}"
  if [[ "${DRY_RUN}" == true ]]; then
    run_cmd gcloud storage rm -r "${BUCKET_URI}"
    return 0
  fi
  set +e
  output="$(gcloud storage rm -r "${BUCKET_URI}" 2>&1)"
  status=$?
  set -e
  if [[ "${status}" -eq 0 ]]; then
    return 0
  fi
  if grep -Eqi 'not found|does not exist|404' <<<"${output}"; then
    log_info "Bucket already absent: ${BUCKET_URI}"
    return 0
  fi
  printf '%s\n' "${output}" >&2
  fail "Bucket deletion failed: ${BUCKET_URI}"
}

print_status() {
  echo "project_id=${PROJECT_ID}"
  echo "ces_location=${CES_LOCATION}"
  echo "datastore_location=${DATASTORE_LOCATION}"
  echo "collection_id=${COLLECTION_ID}"
  echo "datastore_id=${DATASTORE_ID}"
  echo "datastore_resource=${DATASTORE_RESOURCE}"
  echo "bucket_uri=${BUCKET_URI}"
  echo "bucket_location=${BUCKET_LOCATION}"
  echo "object_uri=${OBJECT_URI}"
  echo "source_url=${SOURCE_URL}"
  echo "tool_json=${TOOL_JSON}"
  if [[ -f "${TOOL_JSON}" ]]; then
    echo "tool_json_data_store_id=$(parse_json_field "${TOOL_JSON}" 'googleSearchTool.dataStoreId')"
  else
    echo "tool_json_data_store_id=<missing tool json>"
  fi
  if [[ -z "${PROJECT_ID}" || "${DRY_RUN}" == true ]]; then
    return 0
  fi
  require_command gcloud
  require_command curl
  local token output_file http_code
  token="$(get_access_token)"
  output_file="$(mktemp)"
  http_code="$(datastore_status_code "${token}" "${output_file}")"
  echo "datastore_http=${http_code}"
  rm -f "${output_file}"
  if gcloud storage buckets describe "${BUCKET_URI}" >/dev/null 2>&1; then
    echo "bucket_exists=true"
  else
    echo "bucket_exists=false"
  fi
}

provision() {
  require_project
  require_command python3
  require_command gcloud
  require_command curl
  sync_tool_config
  if [[ "${DRY_RUN}" == true ]]; then
    log_info "Dry-run mode enabled"
  fi
  ensure_bucket
  upload_source_pdf
  local token
  token="$(get_access_token)"
  ensure_datastore "${token}"
  import_documents "${token}"
}

deprovision() {
  require_project
  require_command gcloud
  require_command curl
  local token
  token="$(get_access_token)"
  delete_datastore "${token}"
  delete_bucket
}

case "${COMMAND}" in
  help|-h|--help)
    usage
    ;;
  sync-tool-config)
    require_project
    require_command python3
    sync_tool_config
    ;;
  provision)
    provision
    ;;
  deprovision)
    deprovision
    ;;
  status)
    print_status
    ;;
  *)
    usage
    fail "Unsupported command: ${COMMAND}"
    ;;
esac
