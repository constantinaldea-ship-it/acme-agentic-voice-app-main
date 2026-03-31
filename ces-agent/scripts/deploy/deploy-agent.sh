#!/usr/bin/env bash
# =============================================================================
# CX Agent Studio — Package Acme Voice Agent
# =============================================================================
# Packages acme_voice_agent/ into a ZIP ready for CX Agent Studio import.
#
# Supports both:
#  - Manual import via the Agent Builder console UI
#  - Optional REST import via CES apps:importApp
#
# Usage:
#   ./deploy-agent.sh                                      # Package acme_voice_agent
#   ./deploy-agent.sh --validate                           # Validate only (no ZIP)
#   ./deploy-agent.sh --import --location us              # Package + import via REST
#
# =============================================================================
# Author: Augment Agent — 2026-02-07
# Modified by Augment Agent on 2026-02-08 — moved to scripts/, updated paths
# Modified by Augment Agent on 2026-02-08 — added --source flag for alternate package directories
# Modified by GitHub Copilot on 2026-03-24 — added optional CES ImportApp REST workflow
# Modified by GitHub Copilot on 2026-03-28 — moved canonical deployment tooling to scripts/deploy/
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CES_AGENT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
REPO_ROOT="$(cd "${CES_AGENT_DIR}/.." && pwd)"
# shellcheck source=../../../scripts/lib/load-env.sh
source "${REPO_ROOT}/scripts/lib/load-env.sh"
load_root_env
APP_NAME="acme_voice_agent"
APP_DIR=""
VERSION="$(date +%Y%m%d-%H%M%S)"
ZIP_VERSIONED=""
ZIP_FILE=""
ZIP_LATEST=""

PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-}}"
LOCATION="${GCP_LOCATION:-${LOCATION:-}}"
VALIDATE_ONLY=false
IMPORT_TO_CES=false
APP_ID="${APP_ID:-}"
DISPLAY_NAME="${DISPLAY_NAME:-}"
IMPORT_STRATEGY="${IMPORT_STRATEGY:-REPLACE}"
IGNORE_APP_LOCK=false
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-5}"
POLL_TIMEOUT_SECONDS="${POLL_TIMEOUT_SECONDS:-300}"

provision_fee_schedule_datastore_if_needed() {
  local fee_schedule_agent_manifest="${APP_DIR}/agents/fee_schedule_agent/fee_schedule_agent.json"
  local datastore_script="${SCRIPT_DIR}/manage_datastore.sh"
  local datastore_args=(provision)

  if [[ "${SKIP_FEE_SCHEDULE_DATASTORE_PROVISION:-false}" == "true" ]]; then
    echo "=== Step 0: Provision Fee Schedule Datastore (skipped) ==="
    echo "  • SKIP_FEE_SCHEDULE_DATASTORE_PROVISION=true"
    echo ""
    return 0
  fi

  if [[ "${VALIDATE_ONLY}" == true ]]; then
    return 0
  fi

  if [[ ! -f "${fee_schedule_agent_manifest}" ]]; then
    return 0
  fi

  if [[ ! -f "${datastore_script}" ]]; then
    echo "ERROR: Expected datastore lifecycle script not found: ${datastore_script}"
    exit 1
  fi

  if [[ -n "${GCP_PROJECT_ID:-}" ]]; then
    datastore_args+=(--project "${GCP_PROJECT_ID}")
  fi

  if [[ -n "${GCP_LOCATION:-}" ]]; then
    datastore_args+=(--location "${GCP_LOCATION}")
  fi

  echo "=== Step 0: Provision Fee Schedule Datastore ==="
  bash "${datastore_script}" "${datastore_args[@]}"
  echo ""
}

usage() {
  cat <<EOF
Usage: $0 [--validate] [--project PROJECT_ID] [--source AGENT_DIR_NAME] [--import [OPTIONS]]

Flags:
  --validate              Validate files only (no ZIP created)
  --project               GCP project ID for CES import and console links
                          (default: GCP_PROJECT_ID from repository root .env)
  --location              CES location for import
                          (default: GCP_LOCATION from repository root .env)
  --source                Agent package directory name (default: acme_voice_agent)
  --import                Import the packaged ZIP via CES apps:importApp REST API
  --app-id                Target CES app ID for import/reimport
  --display-name          Display name for a newly imported app
  --import-strategy       Conflict strategy for reimport: REPLACE or OVERWRITE
                          (default: REPLACE)
  --ignore-app-lock       Ignore target app lock during reimport
  --poll-interval         Poll interval in seconds for the import operation (default: 5)
  --poll-timeout          Poll timeout in seconds for the import operation (default: 300)
  --help, -h             Show this help

Examples:
  $0
  $0 --validate
  $0 --import --location us
  $0 --import --location eu --app-id acme-voice-eu --import-strategy OVERWRITE
  $0 --source acme_voice_agent --import --app-id acme-voice-us

Notes:
  - Uses CES ImportApp: POST /v1/projects/*/locations/*/apps:importApp
  - Auth uses active gcloud credentials (user auth or ADC)
  - Dialogflow CX v3 agents:restore is NOT the correct API for CES generative apps
  - Set SKIP_FEE_SCHEDULE_DATASTORE_PROVISION=true to bypass the fee-schedule
    datastore provisioning step when it is unrelated to the current deploy
EOF
}

require_value() {
  local flag="$1"
  local value="${2:-}"
  if [[ -z "${value}" ]]; then
    echo "ERROR: ${flag} requires a value"
    exit 1
  fi
}

require_command() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: Required command not found: ${cmd}"
    exit 1
  fi
}

curl_http_code() {
  local output_file="$1"
  shift

  local curl_output=""
  local curl_status=0

  set +e
  curl_output="$(curl -sS -o "${output_file}" -w "%{http_code}" "$@" 2>&1)"
  curl_status=$?
  set -e

  if (( curl_status != 0 )); then
    if [[ -n "${curl_output}" ]]; then
      echo "${curl_output}" >&2
    fi
    return "${curl_status}"
  fi

  printf "%s" "${curl_output}"
}

validate_app_id() {
  local value="$1"
  if [[ ! "${value}" =~ ^[A-Za-z0-9][A-Za-z0-9_-]{4,35}$ ]]; then
    echo "ERROR: Invalid --app-id '${value}'"
    echo "       Must match [A-Za-z0-9][A-Za-z0-9_-]{4,35} (length 5-36)."
    exit 1
  fi
}

get_ces_endpoint() {
  case "${LOCATION}" in
    us) echo "https://ces.us.rep.googleapis.com" ;;
    eu) echo "https://ces.eu.rep.googleapis.com" ;;
    *)  echo "https://ces.googleapis.com" ;;
  esac
}

json_query() {
  local file="$1"
  local expr="$2"
  python3 - "$file" "$expr" <<'PY'
import json
import sys

path = sys.argv[2].split('.') if sys.argv[2] else []
with open(sys.argv[1], encoding='utf-8') as handle:
    data = json.load(handle)

value = data
for part in path:
    if part == '':
        continue
    if isinstance(value, dict):
        value = value.get(part)
    else:
        value = None
        break

if value is None:
    print("")
elif isinstance(value, bool):
    print("true" if value else "false")
elif isinstance(value, (dict, list)):
    print(json.dumps(value))
else:
    print(value)
PY
}

print_json_array_lines() {
  local json_payload="$1"
  python3 - "$json_payload" <<'PY'
import json
import sys

raw = sys.argv[1]
if not raw:
    sys.exit(0)

try:
    values = json.loads(raw)
except Exception:
    print(raw)
    sys.exit(0)

if isinstance(values, list):
    for value in values:
        print(value)
elif values:
    print(values)
PY
}

print_post_import_checks() {
  echo "  After import, test in the simulator:"
  echo "    1. Send: 'Hallo'              → Acme Bank greeting"
  echo "    2. Send: 'Filialen in München' → searchBranches tool call"
  echo "    3. Send: 'Danke, das war's'   → end_session"
  echo "    4. Run evaluations from the Evaluations tab"
}

print_manual_import_instructions() {
  echo ""
  echo "════════════════════════════════════════════════════════════════════"
  echo "  CX Agent Studio — Manual Import Instructions"
  echo "════════════════════════════════════════════════════════════════════"
  echo ""
  echo "  You can import the ZIP via the Agent Builder console UI."
  echo ""
  echo "  ┌─────────────────────────────────────────────────────────────┐"
  echo "  │  OPTION A: Create New Agent                                │"
  echo "  │                                                            │"
  echo "  │  1. Open Agent Builder:                                    │"
  echo "  │     https://console.cloud.google.com/agent-builder         │"
  echo "  │                                                            │"
  echo "  │  2. Select project: ${PROJECT_ID}"
  echo "  │                                                            │"
  echo "  │  3. Click 'Create agent' → 'Conversational Agent'         │"
  echo "  │                                                            │"
  echo "  │  4. In the creation dialog, look for 'Import' or          │"
  echo "  │     'Restore from file' option                             │"
  echo "  │                                                            │"
  echo "  │  5. Upload: ${ZIP_VERSIONED}"
  echo "  │     ${ZIP_FILE}"
  echo "  │                                                            │"
  echo "  │  6. Wait for import to complete                            │"
  echo "  └─────────────────────────────────────────────────────────────┘"
  echo ""
  echo "  ┌─────────────────────────────────────────────────────────────┐"
  echo "  │  OPTION B: Restore Into Existing Agent                     │"
  echo "  │                                                            │"
  echo "  │  1. Open your existing agent in Agent Builder              │"
  echo "  │                                                            │"
  echo "  │  2. Go to: Agent Settings (⚙️) → 'Restore'                │"
  echo "  │                                                            │"
  echo "  │  3. Upload: ${APP_NAME}.zip                                │"
  echo "  │                                                            │"
  echo "  │  4. Choose 'Fallback' restore mode                        │"
  echo "  │     (creates new resources, keeps unrelated existing ones) │"
  echo "  │                                                            │"
  echo "  │  5. Wait for import to complete                            │"
  echo "  └─────────────────────────────────────────────────────────────┘"
  echo ""
  print_post_import_checks
}

validate_zip_archive() {
  local zip_file="$1"
  local source_dir="$2"
  local app_name="$3"

  python3 - "$zip_file" "$source_dir" "$app_name" <<'PY'
import json
import os
import sys
import zipfile

ZIP_FILE = sys.argv[1]
SOURCE_DIR = sys.argv[2]
APP_NAME = sys.argv[3]
BUILTIN_TOOLS = {"end_session"}
CALLBACK_KEYS = [
    "afterAgentCallbacks",
    "beforeModelCallbacks",
    "afterModelCallbacks",
    "afterToolCallbacks",
    "beforeToolCallbacks",
]


def normalize(value: str) -> str:
    return value.replace("\\", "/").strip("/")


def rel_path(abs_path: str) -> str:
    return normalize(os.path.relpath(abs_path, SOURCE_DIR))


def add_existing(required: set[str], abs_path: str) -> None:
    if os.path.exists(abs_path):
        required.add(rel_path(abs_path))


def add_ref(required: set[str], raw_path) -> None:
    if not isinstance(raw_path, str) or not raw_path.strip():
        return
    normalized = normalize(raw_path)
    if os.path.isabs(normalized):
        return
    required.add(normalized)


def is_record(value) -> bool:
    return isinstance(value, dict)


def read_json(file_path: str):
    with open(file_path, encoding="utf-8") as handle:
        return json.load(handle)


def build_manifest_index(root_dir: str):
    by_folder: dict[str, str] = {}
    by_display_name: dict[str, str] = {}
    if not os.path.isdir(root_dir):
        return by_folder, by_display_name

    for folder_name in sorted(os.listdir(root_dir)):
        folder_path = os.path.join(root_dir, folder_name)
        if not os.path.isdir(folder_path):
            continue
        manifest_path = os.path.join(folder_path, f"{folder_name}.json")
        if not os.path.exists(manifest_path):
            continue
        relative_manifest = rel_path(manifest_path)
        by_folder[folder_name] = relative_manifest
        try:
            manifest = read_json(manifest_path)
        except Exception:
            continue
        if is_record(manifest):
            display_name = manifest.get("displayName")
            if isinstance(display_name, str) and display_name:
                by_display_name[display_name] = relative_manifest

    return by_folder, by_display_name


required: set[str] = set()
manifest_path = os.path.join(SOURCE_DIR, "app.yaml")
if not os.path.exists(manifest_path):
    manifest_path = os.path.join(SOURCE_DIR, "app.json")

add_existing(required, manifest_path)

environment_path = os.path.join(SOURCE_DIR, "environment.json")
add_existing(required, environment_path)

manifest = {}
if manifest_path.endswith(".json") and os.path.exists(manifest_path):
    manifest = read_json(manifest_path)
elif os.path.exists(manifest_path):
    with open(manifest_path, encoding="utf-8") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if line.startswith("rootAgent:"):
                manifest["rootAgent"] = line.split(":", 1)[1].strip().strip("\"'")
            elif line.startswith("globalInstruction:"):
                manifest["globalInstruction"] = line.split(":", 1)[1].strip().strip("\"'")

root_agent = manifest.get("rootAgent")
if isinstance(root_agent, str) and root_agent:
    required.add(normalize(f"agents/{root_agent}/{root_agent}.json"))

global_instruction = manifest.get("globalInstruction")
if isinstance(global_instruction, str) and global_instruction and (
    global_instruction.endswith(".txt") or "/" in global_instruction or "\\" in global_instruction
):
    add_ref(required, global_instruction)

tool_manifest_by_folder, tool_manifest_by_display_name = build_manifest_index(
    os.path.join(SOURCE_DIR, "tools")
)
toolset_manifest_by_folder, toolset_manifest_by_display_name = build_manifest_index(
    os.path.join(SOURCE_DIR, "toolsets")
)

agents_root = os.path.join(SOURCE_DIR, "agents")
if os.path.isdir(agents_root):
    for agent_name in sorted(os.listdir(agents_root)):
        agent_dir = os.path.join(agents_root, agent_name)
        if not os.path.isdir(agent_dir):
            continue
        agent_manifest = os.path.join(agent_dir, f"{agent_name}.json")
        add_existing(required, agent_manifest)
        if not os.path.exists(agent_manifest):
            continue
        agent = read_json(agent_manifest)
        if not is_record(agent):
            continue

        add_ref(required, agent.get("instruction"))

        for callback_key in CALLBACK_KEYS:
            callbacks = agent.get(callback_key)
            if not isinstance(callbacks, list):
                continue
            for callback in callbacks:
                if is_record(callback):
                    add_ref(required, callback.get("pythonCode"))

        child_agents = agent.get("childAgents")
        if isinstance(child_agents, list):
            for child_agent in child_agents:
                if isinstance(child_agent, str) and child_agent:
                    required.add(normalize(f"agents/{child_agent}/{child_agent}.json"))

        tools = agent.get("tools")
        if isinstance(tools, list):
            for tool_name in tools:
                if isinstance(tool_name, str) and tool_name and tool_name not in BUILTIN_TOOLS:
                    manifest_rel = tool_manifest_by_display_name.get(tool_name) or tool_manifest_by_folder.get(tool_name)
                    if manifest_rel:
                        required.add(manifest_rel)

        toolsets = agent.get("toolsets")
        if isinstance(toolsets, list):
            for toolset_name in toolsets:
                if not isinstance(toolset_name, str) or not toolset_name:
                    continue
                manifest_rel = (
                    toolset_manifest_by_display_name.get(toolset_name)
                    or toolset_manifest_by_folder.get(toolset_name)
                )
                if manifest_rel:
                    required.add(manifest_rel)

tools_root = os.path.join(SOURCE_DIR, "tools")
if os.path.isdir(tools_root):
    for tool_name in sorted(os.listdir(tools_root)):
        tool_dir = os.path.join(tools_root, tool_name)
        if not os.path.isdir(tool_dir):
            continue
        tool_manifest = os.path.join(tool_dir, f"{tool_name}.json")
        add_existing(required, tool_manifest)
        if not os.path.exists(tool_manifest):
            continue
        tool = read_json(tool_manifest)
        if not is_record(tool):
            continue
        python_function = tool.get("pythonFunction")
        if is_record(python_function):
            add_ref(required, python_function.get("pythonCode"))

toolsets_root = os.path.join(SOURCE_DIR, "toolsets")
if os.path.isdir(toolsets_root):
    for toolset_name in sorted(os.listdir(toolsets_root)):
        toolset_dir = os.path.join(toolsets_root, toolset_name)
        if not os.path.isdir(toolset_dir):
            continue
        toolset_manifest = os.path.join(toolset_dir, f"{toolset_name}.json")
        add_existing(required, toolset_manifest)
        if not os.path.exists(toolset_manifest):
            continue
        toolset = read_json(toolset_manifest)
        if not is_record(toolset):
            continue
        openapi_toolset = toolset.get("openApiToolset")
        if is_record(openapi_toolset):
            add_ref(required, openapi_toolset.get("openApiSchema"))

evaluations_root = os.path.join(SOURCE_DIR, "evaluations")
if os.path.isdir(evaluations_root):
    for evaluation_name in sorted(os.listdir(evaluations_root)):
        evaluation_dir = os.path.join(evaluations_root, evaluation_name)
        if not os.path.isdir(evaluation_dir):
            continue
        add_existing(required, os.path.join(evaluation_dir, f"{evaluation_name}.json"))

with zipfile.ZipFile(ZIP_FILE) as archive:
    archive_entries = {
        normalize(name)
        for name in archive.namelist()
        if normalize(name)
    }

wrapper_prefix = f"{APP_NAME}/"
missing = []
if not any(entry == APP_NAME or entry.startswith(wrapper_prefix) for entry in archive_entries):
    missing.append(wrapper_prefix)

for relative_path in sorted(required):
    expected = normalize(f"{APP_NAME}/{relative_path}")
    if expected not in archive_entries:
        missing.append(expected)

allowed_root_entries = {
  os.path.basename(manifest_path),
  "environment.json",
  "agents",
  "tools",
  "toolsets",
  "guardrails",
  "evaluations",
}
if isinstance(global_instruction, str) and global_instruction.strip():
  allowed_root_entries.add(os.path.basename(normalize(global_instruction)))

unsupported_root_entries = []
for entry in sorted(archive_entries):
  if not entry.startswith(wrapper_prefix):
    continue
  relative_entry = entry[len(wrapper_prefix):]
  if not relative_entry:
    continue
  root_name = relative_entry.split("/", 1)[0]
  if root_name not in allowed_root_entries:
    unsupported_root_entries.append(entry)

if missing:
    print("ERROR: Packaged ZIP archive is missing required CES resources:")
    for item in missing:
        print(f"  - {item}")
    sys.exit(1)

if unsupported_root_entries:
  print("ERROR: Packaged ZIP archive contains unsupported root-level entries:")
  for item in unsupported_root_entries:
    print(f"  - {item}")
  sys.exit(1)

print("✅ Archive validation passed")
PY
}

import_to_ces() {
  local endpoint parent import_url access_token response_file request_file
  local status_file http_code operation_name operation_done imported_app_name warnings_json error_json
  local poll_start elapsed current_state app_get_url app_exists=false

  require_command python3
  require_command curl
  require_command gcloud

  if [[ ! -f "${ZIP_FILE}" ]]; then
    echo "ERROR: ZIP file not found: ${ZIP_FILE}"
    exit 1
  fi

  if [[ "${IMPORT_STRATEGY}" != "REPLACE" && "${IMPORT_STRATEGY}" != "OVERWRITE" ]]; then
    echo "ERROR: --import-strategy must be REPLACE or OVERWRITE"
    exit 1
  fi

  if [[ -n "${APP_ID}" ]]; then
    validate_app_id "${APP_ID}"
  fi

  access_token="$(gcloud auth print-access-token 2>/dev/null || true)"
  if [[ -z "${access_token}" ]]; then
    access_token="$(gcloud auth application-default print-access-token 2>/dev/null || true)"
  fi
  if [[ -z "${access_token}" ]]; then
    echo "ERROR: Failed to obtain a Google Cloud access token."
    echo "       Run 'gcloud auth login' or 'gcloud auth application-default login' first."
    exit 1
  fi

  endpoint="$(get_ces_endpoint)"
  parent="projects/${PROJECT_ID}/locations/${LOCATION}"
  import_url="${endpoint}/v1/${parent}/apps:importApp"

  if [[ -n "${APP_ID}" ]]; then
    app_get_url="${endpoint}/v1/${parent}/apps/${APP_ID}"
    response_file="$(mktemp)"
    if ! http_code="$(curl_http_code "${response_file}" \
      -H "Authorization: Bearer ${access_token}" \
      "${app_get_url}")"; then
      echo "ERROR: Failed to query target app '${APP_ID}' before import."
      rm -f "${response_file}"
      exit 1
    fi

    if [[ "${http_code}" == "200" ]]; then
      app_exists=true
    elif [[ "${http_code}" == "404" ]]; then
      app_exists=false
    else
      echo "ERROR: Failed to determine whether target app '${APP_ID}' already exists (HTTP ${http_code})."
      cat "${response_file}"
      rm -f "${response_file}"
      exit 1
    fi
    rm -f "${response_file}"

    if [[ "${app_exists}" == true && -n "${DISPLAY_NAME}" ]]; then
      echo "ERROR: --display-name cannot be used when reimporting an existing app ID (${APP_ID})."
      echo "       Omit --display-name for reimport, or use a new --app-id."
      exit 1
    fi
  fi

  request_file="$(mktemp)"
  python3 - "${ZIP_FILE}" "${request_file}" "${APP_ID}" "${DISPLAY_NAME}" "${IMPORT_STRATEGY}" "${IGNORE_APP_LOCK}" <<'PY'
import base64
import json
import pathlib
import sys

zip_path = pathlib.Path(sys.argv[1])
request_path = pathlib.Path(sys.argv[2])
app_id = sys.argv[3]
display_name = sys.argv[4]
strategy = sys.argv[5]
ignore_lock = sys.argv[6].lower() == 'true'

payload = {
    'appContent': base64.b64encode(zip_path.read_bytes()).decode('ascii')
}

if app_id:
    payload['appId'] = app_id
if display_name:
    payload['displayName'] = display_name
if strategy:
    payload['importOptions'] = {
        'conflictResolutionStrategy': strategy
    }
if ignore_lock:
    payload['ignoreAppLock'] = True

request_path.write_text(json.dumps(payload), encoding='utf-8')
PY

  echo ""
  echo "=== Step 3: Import to CES ==="
  echo "Endpoint   : ${endpoint}"
  echo "Parent     : ${parent}"
  if [[ -n "${APP_ID}" ]]; then
    if [[ "${app_exists}" == true ]]; then
      echo "Mode       : Reimport existing app"
    else
      echo "Mode       : Import as new app with requested app ID"
    fi
    echo "App ID     : ${APP_ID}"
  else
    echo "Mode       : Import as new app with server-assigned app ID"
  fi
  if [[ -n "${DISPLAY_NAME}" ]]; then
    echo "Display    : ${DISPLAY_NAME}"
  fi
  echo "Strategy   : ${IMPORT_STRATEGY}"

  response_file="$(mktemp)"
  if ! http_code="$(curl_http_code "${response_file}" \
    -X POST \
    -H "Authorization: Bearer ${access_token}" \
    -H "Content-Type: application/json" \
    -H "x-goog-request-params: parent=${parent}" \
    --data-binary @"${request_file}" \
    "${import_url}")"; then
    echo "ERROR: CES import request failed before an HTTP response was received."
    rm -f "${request_file}" "${response_file}"
    exit 1
  fi

  rm -f "${request_file}"

  if [[ "${http_code}" != "200" ]]; then
    echo "ERROR: CES import request failed (HTTP ${http_code})."
    cat "${response_file}"
    rm -f "${response_file}"
    exit 1
  fi

  operation_name="$(json_query "${response_file}" "name")"
  operation_done="$(json_query "${response_file}" "done")"
  if [[ -z "${operation_name}" ]]; then
    echo "ERROR: CES import response did not include an operation name."
    cat "${response_file}"
    rm -f "${response_file}"
    exit 1
  fi

  echo "Operation  : ${operation_name}"

  status_file="${response_file}"
  poll_start="$(date +%s)"

  while [[ "${operation_done}" != "true" ]]; do
    sleep "${POLL_INTERVAL_SECONDS}"
    elapsed=$(( $(date +%s) - poll_start ))
    if (( elapsed > POLL_TIMEOUT_SECONDS )); then
      echo "ERROR: Timed out waiting for CES import to finish after ${POLL_TIMEOUT_SECONDS}s."
      echo "       Operation: ${operation_name}"
      rm -f "${status_file}"
      exit 1
    fi

    current_state="$(mktemp)"
    if ! http_code="$(curl_http_code "${current_state}" \
      -H "Authorization: Bearer ${access_token}" \
      "${endpoint}/v1/${operation_name}")"; then
      echo "ERROR: Failed to poll CES operation status before an HTTP response was received."
      rm -f "${current_state}" "${status_file}"
      exit 1
    fi

    if [[ "${http_code}" != "200" ]]; then
      echo "ERROR: Failed to poll CES operation status (HTTP ${http_code})."
      cat "${current_state}"
      rm -f "${current_state}" "${status_file}"
      exit 1
    fi

    rm -f "${status_file}"
    status_file="${current_state}"
    operation_done="$(json_query "${status_file}" "done")"
    echo "  ... import in progress (${elapsed}s elapsed)"
  done

  error_json="$(json_query "${status_file}" "error")"
  if [[ -n "${error_json}" && "${error_json}" != "{}" ]]; then
    echo "ERROR: CES import operation failed."
    cat "${status_file}"
    rm -f "${status_file}"
    exit 1
  fi

  imported_app_name="$(json_query "${status_file}" "response.name")"
  warnings_json="$(json_query "${status_file}" "response.warnings")"

  echo ""
  echo "✅ CES import completed successfully"
  echo "   App: ${imported_app_name:-'(response did not include app name)'}"

  if [[ -n "${warnings_json}" && "${warnings_json}" != "[]" ]]; then
    echo ""
    echo "Warnings:"
    while IFS= read -r warning; do
      [[ -n "${warning}" ]] && echo "  - ${warning}"
    done < <(print_json_array_lines "${warnings_json}")
  fi

  echo ""
  print_post_import_checks
  echo "════════════════════════════════════════════════════════════════════"
  rm -f "${status_file}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --validate)  VALIDATE_ONLY=true; shift ;;
    --import)    IMPORT_TO_CES=true; shift ;;
    --ignore-app-lock) IGNORE_APP_LOCK=true; shift ;;
    --project)   require_value "$1" "${2:-}"; PROJECT_ID="$2"; shift 2 ;;
    --location)  require_value "$1" "${2:-}"; LOCATION="$2"; shift 2 ;;
    --source)    require_value "$1" "${2:-}"; APP_NAME="$2"; shift 2 ;;
    --app-id)    require_value "$1" "${2:-}"; APP_ID="$2"; shift 2 ;;
    --display-name) require_value "$1" "${2:-}"; DISPLAY_NAME="$2"; shift 2 ;;
    --import-strategy) require_value "$1" "${2:-}"; IMPORT_STRATEGY="$2"; shift 2 ;;
    --poll-interval) require_value "$1" "${2:-}"; POLL_INTERVAL_SECONDS="$2"; shift 2 ;;
    --poll-timeout) require_value "$1" "${2:-}"; POLL_TIMEOUT_SECONDS="$2"; shift 2 ;;
    --help|-h)
      usage
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

if [[ -n "${PROJECT_ID}" ]]; then
  set_gcp_env_override GCP_PROJECT_ID "${PROJECT_ID}"
fi

if [[ -n "${LOCATION}" ]]; then
  set_gcp_env_override GCP_LOCATION "${LOCATION}"
fi

PROJECT_ID="${GCP_PROJECT_ID:-${PROJECT_ID:-<set GCP_PROJECT_ID in repo root .env>}}"

if [[ "${IMPORT_TO_CES}" == true ]]; then
  require_gcp_ces_env || exit 1
  PROJECT_ID="${GCP_PROJECT_ID}"
  LOCATION="${GCP_LOCATION}"
fi

APP_DIR="${CES_AGENT_DIR}/${APP_NAME}"
ZIP_VERSIONED="${APP_NAME}-${VERSION}.zip"
ZIP_FILE="${CES_AGENT_DIR}/${ZIP_VERSIONED}"
ZIP_LATEST="${CES_AGENT_DIR}/${APP_NAME}.zip"

echo "Agent source: ${APP_NAME}"

provision_fee_schedule_datastore_if_needed

echo "=== Step 1: Validate ==="

VALIDATOR="${SCRIPT_DIR}/validate-package.py"
if [[ -f "${VALIDATOR}" ]]; then
  if ! python3 "${VALIDATOR}" "${APP_DIR}"; then
    echo ""
    echo "Validation failed. Fix errors above before packaging."
    exit 1
  fi
else
  echo "⚠️  validate-package.py not found — skipping cross-reference validation"
  if [[ ! -d "${APP_DIR}" ]]; then
    echo "ERROR: ${APP_DIR} not found"
    exit 1
  fi
fi

echo ""

if [[ "${VALIDATE_ONLY}" == true ]]; then
  echo "Validation complete. Run without --validate to create ZIP."
  exit 0
fi

echo "=== Step 2: Package ==="

rm -f "${ZIP_FILE}"
(cd "${APP_DIR}/.." && zip -r "${ZIP_FILE}" "${APP_NAME}" \
  -x "${APP_NAME}/validate-package.py" \
  -x "${APP_NAME}/tools/get_customer_details/README.md" \
  -x "${APP_NAME}/tools/get_customer_details/get_customer_details.py" \
  -x "${APP_NAME}/tools/get_customer_details/test_*.py" \
  -x "${APP_NAME}/**/__pycache__/*")

cp -f "${ZIP_FILE}" "${ZIP_LATEST}"

ZIP_SIZE=$(ls -lh "${ZIP_FILE}" | awk '{print $5}')
ZIP_SHA=$(shasum -a 256 "${ZIP_FILE}" | awk '{print $1}')

echo ""
echo "✅ Versioned : ${ZIP_VERSIONED}"
echo "✅ Latest    : ${APP_NAME}.zip"
echo "   Size:   ${ZIP_SIZE}"
echo "   SHA256: ${ZIP_SHA}"
validate_zip_archive "${ZIP_FILE}" "${APP_DIR}" "${APP_NAME}"

GITIGNORE="${CES_AGENT_DIR}/.gitignore"
if [[ -f "${GITIGNORE}" ]]; then
  IGNORE_PATTERN="${APP_NAME}-*.zip"
  if ! grep -qF "${IGNORE_PATTERN}" "${GITIGNORE}" 2>/dev/null; then
    echo "${IGNORE_PATTERN}" >> "${GITIGNORE}"
  fi
fi

BFA_URL=$(python3 -c "
import json, sys
try:
    env = json.load(open('${APP_DIR}/environment.json'))
    url = env.get('toolsets', {}).get('location', {}).get('openApiToolset', {}).get('url', '')
    if url:
        print(url)
    else:
        print('(see environment.json or tool manifests for runtime endpoint wiring)')
except Exception:
    print('(see environment.json)')
" 2>/dev/null || echo "(see environment.json)")

if [[ "${IMPORT_TO_CES}" == true ]]; then
  import_to_ces
else
  print_manual_import_instructions
  echo ""
  echo "  BFA backend: ${BFA_URL}"
  echo "════════════════════════════════════════════════════════════════════"
fi
