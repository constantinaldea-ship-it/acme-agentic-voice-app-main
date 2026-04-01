#!/usr/bin/env python3
"""State-aware incremental CES deployment manager."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from lib.deploy_resource_lib import (
    DeployResourceError,
    apply_request,
    build_request,
    detect_endpoint,
    find_existing_resource_name,
    get_access_token,
    http_request,
    load_json,
    parse_json_body,
)


SCHEMA_VERSION = 1
KIND_ORDER = {"toolset": 0, "tool": 1, "agent": 2}
ENV_ASSIGNMENT_PREFIX = ("export ",)
ANSI_RESET = "\033[0m"
PLAN_LABELS = {
    "added": "Added",
    "modified": "Updated",
    "noop": "No-op",
    "removed": "Removed",
}
PLAN_COLORS = {
    "added": "32",
    "modified": "33",
    "noop": "90",
    "removed": "31",
}


class DeploymentManagerError(RuntimeError):
    """Raised when the deploy manager cannot build a safe plan."""


class MissingCesAppError(DeploymentManagerError):
    """Raised when the configured CES app parent resource does not exist."""


@dataclass(frozen=True)
class LocalComponent:
    """A deployable local CES component."""

    key: str
    kind: str
    resource_id: str
    display_name: str
    source_path: Path
    tracked_files: tuple[Path, ...]
    file_hashes: dict[str, str]
    combined_hash: str


@dataclass(frozen=True)
class ExecutionTargetStatus:
    """Resolved remote CES target state for deploy execution."""

    project: str
    location: str
    app_id: str
    endpoint: str
    app_exists: bool
    deployments: tuple[dict[str, Any], ...]
    configured_deployment_id: str
    configured_deployment_matches: bool | None


def script_dir() -> Path:
    return SCRIPT_DIR


def ces_agent_dir() -> Path:
    return script_dir().parent.parent


def repo_root() -> Path:
    return ces_agent_dir().parent


def default_app_root() -> Path:
    return ces_agent_dir() / "acme_voice_agent"


def default_state_file() -> Path:
    return ces_agent_dir() / ".ces-deployment-state.json"


def default_artifacts_dir() -> Path:
    return script_dir() / ".artifacts"


def default_root_env() -> Path:
    return repo_root() / ".env"


def default_stack_env() -> Path:
    return repo_root() / ".tmp" / "cloud-run" / "discovery-plan.env"


def parse_env_line(raw_line: str) -> tuple[str, str] | None:
    stripped = raw_line.strip()
    if not stripped or stripped.startswith("#"):
        return None

    candidate = raw_line.strip()
    for prefix in ENV_ASSIGNMENT_PREFIX:
        if candidate.startswith(prefix):
            candidate = candidate[len(prefix) :].strip()
            break

    if "=" not in candidate:
        return None
    key, value = candidate.split("=", 1)
    key = key.strip()
    value = value.strip()
    if not key:
        return None

    inline_comment = value.find(" #")
    if inline_comment != -1:
        value = value[:inline_comment].rstrip()

    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        value = value[1:-1]
    return key, value


def load_env_file(path: Path, *, override: bool = False) -> None:
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        parsed = parse_env_line(raw_line)
        if parsed is None:
            continue
        key, value = parsed
        if override or key not in os.environ:
            os.environ[key] = value


def sync_gcp_env_aliases() -> None:
    alias_pairs = [
        ("GCP_PROJECT_ID", "PROJECT_ID"),
        ("GCP_LOCATION", "LOCATION"),
        ("GCP_PROJECT_ID", "GOOGLE_CLOUD_PROJECT"),
    ]
    for primary, alias in alias_pairs:
        primary_value = os.environ.get(primary, "")
        alias_value = os.environ.get(alias, "")
        if primary_value and not alias_value:
            os.environ[alias] = primary_value
        elif alias_value and not primary_value:
            os.environ[primary] = alias_value


def ensure_env_loaded() -> None:
    load_env_file(default_root_env())
    load_env_file(default_stack_env())
    sync_gcp_env_aliases()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Incremental CES deployment manager with local state tracking."
    )
    parser.add_argument(
        "--app-root",
        default=str(default_app_root()),
        help="Path to the CES app package root.",
    )
    parser.add_argument(
        "--state-file",
        default=str(default_state_file()),
        help="Path to the local deployment state file.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default=str(default_artifacts_dir()),
        help="Directory for per-run deployment artifacts.",
    )
    parser.add_argument("--project", default=os.environ.get("GCP_PROJECT_ID", ""))
    parser.add_argument("--location", default=os.environ.get("GCP_LOCATION", ""))
    parser.add_argument("--app-id", default=os.environ.get("CES_APP_ID", ""))
    parser.add_argument("--endpoint", help="Override CES API endpoint.")
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Skip interactive confirmation and apply the plan immediately.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate and print the plan without executing remote changes.",
    )
    parser.add_argument(
        "--status",
        action="store_true",
        help="Print the latest local deployment status from state and run artifacts.",
    )
    parser.add_argument(
        "--status-json",
        action="store_true",
        help="Print the latest local deployment status as JSON.",
    )
    return parser.parse_args()


def require_value(label: str, value: str) -> str:
    if value:
        return value
    raise DeploymentManagerError(f"Missing required value: {label}")


def run_validator(app_root: Path) -> None:
    validator = script_dir() / "validate-package.py"
    result = subprocess.run(
        [sys.executable, str(validator), str(app_root)],
        capture_output=True,
        text=True,
        check=False,
    )
    if result.stdout:
        print(result.stdout, end="")
    if result.stderr:
        print(result.stderr, end="", file=sys.stderr)
    if result.returncode != 0:
        raise DeploymentManagerError("Package validation failed; refusing to deploy.")


def component_key(kind: str, resource_id: str) -> str:
    return f"{kind}:{resource_id}"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def combined_sha256(app_root: Path, files: list[Path]) -> tuple[str, dict[str, str]]:
    digest = hashlib.sha256()
    file_hashes: dict[str, str] = {}
    app_root_resolved = app_root.resolve()
    for file_path in sorted(files, key=lambda item: item.as_posix()):
        resolved_path = file_path.resolve()
        try:
            relative = resolved_path.relative_to(app_root_resolved).as_posix()
        except ValueError as exc:
            raise DeploymentManagerError(
                f"Tracked file is outside the app root: {resolved_path}"
            ) from exc
        file_digest = sha256_file(resolved_path)
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(file_digest.encode("ascii"))
        digest.update(b"\0")
        file_hashes[relative] = file_digest
    return digest.hexdigest(), file_hashes


def discover_tools(app_root: Path) -> list[LocalComponent]:
    components: list[LocalComponent] = []
    tools_root = app_root / "tools"
    for tool_dir in sorted(tools_root.iterdir()):
        if not tool_dir.is_dir():
            continue
        manifest_path = tool_dir / f"{tool_dir.name}.json"
        if not manifest_path.is_file():
            continue
        manifest = load_json(manifest_path)
        tracked = [manifest_path]
        python_function = manifest.get("pythonFunction")
        if isinstance(python_function, dict):
            python_code = python_function.get("pythonCode")
            if isinstance(python_code, str) and python_code.strip():
                tracked.append((app_root / python_code).resolve())
        combined_hash, file_hashes = combined_sha256(app_root, tracked)
        components.append(
            LocalComponent(
                key=component_key("tool", tool_dir.name),
                kind="tool",
                resource_id=tool_dir.name,
                display_name=manifest.get("displayName", tool_dir.name),
                source_path=manifest_path.resolve(),
                tracked_files=tuple(path.resolve() for path in tracked),
                file_hashes=file_hashes,
                combined_hash=combined_hash,
            )
        )
    return components


def discover_toolsets(app_root: Path) -> list[LocalComponent]:
    components: list[LocalComponent] = []
    toolsets_root = app_root / "toolsets"
    environment_path = (app_root / "environment.json").resolve()
    for toolset_dir in sorted(toolsets_root.iterdir()):
        if not toolset_dir.is_dir():
            continue
        manifest_path = toolset_dir / f"{toolset_dir.name}.json"
        if not manifest_path.is_file():
            continue
        manifest = load_json(manifest_path)
        tracked = [manifest_path]
        openapi_toolset = manifest.get("openApiToolset")
        if isinstance(openapi_toolset, dict):
            schema_path = openapi_toolset.get("openApiSchema")
            if isinstance(schema_path, str) and schema_path.strip():
                resolved_schema_path = (app_root / schema_path).resolve()
                tracked.append(resolved_schema_path)
                if environment_path.is_file() and "$env_var" in resolved_schema_path.read_text(encoding="utf-8"):
                    tracked.append(environment_path)
        combined_hash, file_hashes = combined_sha256(app_root, tracked)
        components.append(
            LocalComponent(
                key=component_key("toolset", toolset_dir.name),
                kind="toolset",
                resource_id=toolset_dir.name,
                display_name=manifest.get("displayName", toolset_dir.name),
                source_path=manifest_path.resolve(),
                tracked_files=tuple(path.resolve() for path in tracked),
                file_hashes=file_hashes,
                combined_hash=combined_hash,
            )
        )
    return components


def discover_agents(app_root: Path) -> list[LocalComponent]:
    components: list[LocalComponent] = []
    agents_root = app_root / "agents"
    callback_fields = [
        "beforeAgentCallbacks",
        "beforeModelCallbacks",
        "beforeToolCallbacks",
        "afterAgentCallbacks",
        "afterModelCallbacks",
        "afterToolCallbacks",
    ]
    for agent_dir in sorted(agents_root.iterdir()):
        if not agent_dir.is_dir():
            continue
        manifest_path = agent_dir / f"{agent_dir.name}.json"
        if not manifest_path.is_file():
            continue
        manifest = load_json(manifest_path)
        tracked = [manifest_path]
        instruction_path = manifest.get("instruction")
        if isinstance(instruction_path, str) and instruction_path.strip():
            tracked.append((app_root / instruction_path).resolve())
        for field in callback_fields:
            callbacks = manifest.get(field)
            if not isinstance(callbacks, list):
                continue
            for callback in callbacks:
                if not isinstance(callback, dict):
                    continue
                python_code = callback.get("pythonCode")
                if isinstance(python_code, str) and python_code.strip():
                    tracked.append((app_root / python_code).resolve())
        combined_hash, file_hashes = combined_sha256(app_root, tracked)
        components.append(
            LocalComponent(
                key=component_key("agent", agent_dir.name),
                kind="agent",
                resource_id=agent_dir.name,
                display_name=manifest.get("displayName", agent_dir.name),
                source_path=manifest_path.resolve(),
                tracked_files=tuple(path.resolve() for path in tracked),
                file_hashes=file_hashes,
                combined_hash=combined_hash,
            )
        )
    return components


def discover_components(app_root: Path) -> list[LocalComponent]:
    components = [
        *discover_toolsets(app_root),
        *discover_tools(app_root),
        *discover_agents(app_root),
    ]
    return sorted(components, key=lambda item: (KIND_ORDER[item.kind], item.resource_id))


def load_state(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"schema_version": SCHEMA_VERSION, "components": {}}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise DeploymentManagerError(f"Invalid deployment state file: {path}") from exc
    if not isinstance(data, dict):
        raise DeploymentManagerError(f"Unexpected deployment state shape in {path}")
    data.setdefault("schema_version", SCHEMA_VERSION)
    data.setdefault("components", {})
    return data


def classify_components(
    local_components: list[LocalComponent],
    state_components: dict[str, Any],
) -> dict[str, list[LocalComponent]]:
    plan: dict[str, list[LocalComponent]] = {
        "added": [],
        "modified": [],
        "noop": [],
    }
    for component in local_components:
        previous = state_components.get(component.key)
        if not previous:
            plan["added"].append(component)
            continue
        if previous.get("combined_sha256") != component.combined_hash:
            plan["modified"].append(component)
        else:
            plan["noop"].append(component)
    return plan


def find_removed_components(
    local_components: list[LocalComponent],
    state_components: dict[str, Any],
) -> list[str]:
    local_keys = {component.key for component in local_components}
    return sorted(key for key in state_components if key not in local_keys)


def print_plan(plan: dict[str, list[LocalComponent]], removed: list[str]) -> None:
    def env_truthy(name: str) -> bool:
        value = os.environ.get(name, "")
        return bool(value and value != "0")

    def colors_enabled() -> bool:
        if os.environ.get("NO_COLOR") is not None:
            return False
        if env_truthy("CLICOLOR_FORCE") or env_truthy("FORCE_COLOR"):
            return True
        return bool(getattr(sys.stdout, "isatty", lambda: False)())

    def colorize(text: str, status: str) -> str:
        if not colors_enabled():
            return text
        code = PLAN_COLORS[status]
        return f"\033[{code}m{text}{ANSI_RESET}"

    print("")
    print("Deployment Plan")
    print("===============")
    legend = " | ".join(
        colorize(f"{PLAN_LABELS[label]}", label)
        for label in ["added", "modified", "noop", "removed"]
    )
    print(f"Legend: {legend}")
    for label in ["added", "modified", "noop"]:
        components = plan[label]
        header = f"{PLAN_LABELS[label]} ({len(components)}):"
        print(colorize(header, label))
        if not components:
            print("  - none")
            continue
        for component in components:
            print(
                f"  - {component.kind:<7} {component.resource_id:<30} "
                f"displayName={component.display_name}"
            )
    removed_header = f"{PLAN_LABELS['removed']} ({len(removed)}):"
    print(colorize(removed_header, "removed"))
    if not removed:
        print("  - none")
    else:
        for key in removed:
            print(f"  - {key} (state only, no remote delete)")
    print("")


def app_parent(project: str, location: str, app_id: str) -> str:
    return f"projects/{project}/locations/{location}/apps/{app_id}"


def bootstrap_import_hint(project: str, location: str, app_id: str) -> str:
    return (
        "Bootstrap it once with ./deploy-agent.sh --import --project "
        f"{project} --location {location} --app-id {app_id} and then rerun "
        "ces-deploy-manager.py."
    )


def missing_app_message(project: str, location: str, app_id: str) -> str:
    return (
        f"Configured CES app '{app_id}' does not exist under "
        f"projects/{project}/locations/{location}. {bootstrap_import_hint(project, location, app_id)}"
    )


def get_ces_app(
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> dict[str, Any] | None:
    resolved_endpoint = detect_endpoint(location, endpoint)
    url = f"{resolved_endpoint}/v1/{app_parent(project, location, app_id)}"
    status, body = http_request("GET", url, token)
    if status == 404:
        return None
    if not 200 <= status < 300:
        detail = body.strip() or "no response body"
        raise DeploymentManagerError(
            f"Unable to inspect CES app '{app_id}' (HTTP {status}): {detail}"
        )
    return parse_json_body(body)


def inspect_execution_target(
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> ExecutionTargetStatus:
    resolved_endpoint = detect_endpoint(location, endpoint)
    configured_deployment_id = os.environ.get("CES_DEPLOYMENT_ID", "").strip()
    app_payload = get_ces_app(
        project=project,
        location=location,
        app_id=app_id,
        endpoint=endpoint,
        token=token,
    )
    if app_payload is None:
        return ExecutionTargetStatus(
            project=project,
            location=location,
            app_id=app_id,
            endpoint=resolved_endpoint,
            app_exists=False,
            deployments=(),
            configured_deployment_id=configured_deployment_id,
            configured_deployment_matches=None,
        )

    deployments = tuple(
        list_api_access_deployments(
            project=project,
            location=location,
            app_id=app_id,
            endpoint=endpoint,
            token=token,
        )
    )
    normalized_configured_id = resource_id_from_name(configured_deployment_id)
    configured_match = None
    if configured_deployment_id:
        configured_match = False
        for deployment in deployments:
            name = deployment.get("name")
            deployment_id = resource_id_from_name(name) if isinstance(name, str) else ""
            display_name = deployment.get("displayName")
            if configured_deployment_id in {name, deployment_id, display_name} or (
                normalized_configured_id and normalized_configured_id == deployment_id
            ):
                configured_match = True
                break

    return ExecutionTargetStatus(
        project=project,
        location=location,
        app_id=app_id,
        endpoint=resolved_endpoint,
        app_exists=True,
        deployments=deployments,
        configured_deployment_id=configured_deployment_id,
        configured_deployment_matches=configured_match,
    )


def resource_id_from_name(resource_name: str) -> str:
    return resource_name.rsplit("/", 1)[-1] if resource_name else ""


def format_named_resources(resources: list[dict[str, Any]]) -> str:
    formatted: list[str] = []
    for resource in resources:
        name = resource.get("name")
        if not isinstance(name, str) or not name:
            continue
        identifier = resource_id_from_name(name)
        display_name = resource.get("displayName")
        if isinstance(display_name, str) and display_name.strip():
            formatted.append(f"{identifier} (displayName={display_name.strip()})")
        else:
            formatted.append(identifier)
    if not formatted:
        return "none"
    if len(formatted) <= 5:
        return ", ".join(formatted)
    return ", ".join(formatted[:5]) + f", +{len(formatted) - 5} more"


def list_api_access_deployments(
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> list[dict[str, Any]]:
    resolved_endpoint = detect_endpoint(location, endpoint)
    url = f"{resolved_endpoint}/v1beta/{app_parent(project, location, app_id)}/deployments"
    status, body = http_request("GET", url, token)
    if status == 404:
        raise MissingCesAppError(missing_app_message(project, location, app_id))
    if not 200 <= status < 300:
        detail = body.strip() or "no response body"
        raise DeploymentManagerError(
            f"Unable to list CES API access deployments for app '{app_id}' (HTTP {status}): {detail}"
        )
    payload = parse_json_body(body)
    deployments = payload.get("deployments", [])
    if not isinstance(deployments, list):
        raise DeploymentManagerError(
            f"Expected 'deployments' list in CES response for app '{app_id}', got {type(deployments).__name__}."
        )
    return [item for item in deployments if isinstance(item, dict)]


def print_execution_target(
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> ExecutionTargetStatus:
    status = inspect_execution_target(
        project=project,
        location=location,
        app_id=app_id,
        endpoint=endpoint,
        token=token,
    )
    console_url = f"https://ces.cloud.google.com/projects/{project}/locations/{location}/apps/{app_id}"

    print("")
    print("CES Execution Target")
    print("====================")
    print(f"Project            : {project}")
    print(f"Location           : {location}")
    print(f"CES_APP_ID         : {app_id}")
    print(f"CES endpoint       : {status.endpoint}")
    print(f"Console URL        : {console_url}")
    print(f"CES app status     : {'exists' if status.app_exists else 'missing'}")
    if status.configured_deployment_id:
        print(f"Configured CES_DEPLOYMENT_ID: {status.configured_deployment_id}")

    if not status.app_exists:
        print("Mode preview       : bootstrap import required")
        print("Deployment IDs     : unavailable until the CES app exists")
        if status.configured_deployment_id:
            print("Configured CES_DEPLOYMENT_ID status: cannot verify because the CES app is missing")
        print("")
        return status

    if not status.deployments:
        print("Mode preview       : incremental deploy")
        print("Discovered deployment IDs: none")
        print("Suggested export    : create an API access deployment in CES and set CES_DEPLOYMENT_ID to that id")
        if status.configured_deployment_id:
            print("Configured CES_DEPLOYMENT_ID status: not found in the live app")
        print("")
        return status

    print("Mode preview       : incremental deploy")
    print("Discovered deployment IDs:")
    discovered_ids: list[str] = []
    for deployment in status.deployments:
        name = deployment.get("name")
        if not isinstance(name, str) or not name:
            continue
        deployment_id = resource_id_from_name(name)
        if not deployment_id:
            continue
        discovered_ids.append(deployment_id)
        display_name = deployment.get("displayName")
        suffix = f" (displayName={display_name})" if isinstance(display_name, str) and display_name.strip() else ""
        print(f"  - {deployment_id}{suffix}")

    if status.configured_deployment_id:
        if status.configured_deployment_matches:
            print("Configured CES_DEPLOYMENT_ID is present in the live app.")
        else:
            print(
                "Configured CES_DEPLOYMENT_ID was not found in the live app deployments. "
                f"Available deployments: {format_named_resources(list(status.deployments))}"
            )
    elif len(discovered_ids) == 1:
        print(f"Suggested export    : CES_DEPLOYMENT_ID={discovered_ids[0]}")
    else:
        print("Suggested export    : set CES_DEPLOYMENT_ID to one of the deployment ids above")
    print("")
    return status


def confirm_bootstrap_import(auto_confirm: bool, *, project: str, location: str, app_id: str) -> bool:
    if auto_confirm:
        print("Auto-confirm enabled; proceeding with bootstrap import.")
        return True

    print("")
    print("Bootstrap Import Required")
    print("=========================")
    print(f"Project            : {project}")
    print(f"Location           : {location}")
    print(f"CES_APP_ID         : {app_id}")
    print("Reason             : the CES app does not exist yet")
    print("Next step          : run the full bootstrap import via deploy-agent.sh --import")
    reply = input("Run bootstrap import now? [y/N] ").strip().lower()
    return reply in {"y", "yes"}


def deploy_agent_script() -> Path:
    return script_dir() / "deploy-agent.sh"


def deploy_agent_source_name(app_root: Path) -> str:
    resolved_app_root = app_root.resolve()
    if resolved_app_root.parent != ces_agent_dir().resolve():
        raise DeploymentManagerError(
            "Bootstrap import currently supports app roots under the ces-agent directory only. "
            f"Got app root: {resolved_app_root}"
        )
    return resolved_app_root.name


def run_bootstrap_import(
    *,
    app_root: Path,
    project: str,
    location: str,
    app_id: str,
) -> None:
    source_name = deploy_agent_source_name(app_root)
    command = [
        "bash",
        str(deploy_agent_script()),
        "--source",
        source_name,
        "--import",
        "--project",
        project,
        "--location",
        location,
        "--app-id",
        app_id,
    ]
    print("")
    print("Bootstrap Import")
    print("================")
    print("Mode               : bootstrap import")
    print(f"Command            : {' '.join(command)}")
    completed = subprocess.run(command, cwd=script_dir(), check=False)
    if completed.returncode != 0:
        raise DeploymentManagerError(
            f"Bootstrap import failed with exit code {completed.returncode}."
        )
    print("Bootstrap import completed successfully.")


def confirm_plan(auto_confirm: bool) -> bool:
    if auto_confirm:
        return True
    reply = input("Apply deployment plan? [y/N] ").strip().lower()
    return reply in {"y", "yes"}


def reconcile_added_components_with_remote(
    plan: dict[str, list[LocalComponent]],
    state_components: dict[str, Any],
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> tuple[dict[str, list[LocalComponent]], list[LocalComponent]]:
    refreshed_plan: dict[str, list[LocalComponent]] = {
        "added": [],
        "modified": list(plan["modified"]),
        "noop": list(plan["noop"]),
    }
    discovered_existing: list[LocalComponent] = []

    for component in plan["added"]:
        agent_names, tool_names, toolset_names = resolved_resource_maps(
            state_components,
            project=project,
            location=location,
            app_id=app_id,
        )
        request = build_request(
            kind=component.kind,
            source_path=component.source_path,
            project=project,
            location=location,
            app_id=app_id,
            endpoint=endpoint,
            agent_names=agent_names,
            tool_names=tool_names,
            toolset_names=toolset_names,
        )
        existing_resource_name = find_existing_resource_name(request, token)
        if existing_resource_name:
            state_components[component.key] = state_entry_for(component, existing_resource_name)
            refreshed_plan["noop"].append(component)
            discovered_existing.append(component)
            continue
        refreshed_plan["added"].append(component)

    refreshed_plan["noop"] = sorted(
        refreshed_plan["noop"], key=lambda item: (KIND_ORDER[item.kind], item.resource_id)
    )
    return refreshed_plan, discovered_existing


def timestamp_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def run_id_now() -> str:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    return f"{timestamp}-{uuid.uuid4().hex[:8]}"


def git_commit_sha(worktree: Path) -> str | None:
    try:
        result = subprocess.run(
            ["git", "-C", str(worktree), "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=True,
        )
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None
    commit_sha = result.stdout.strip()
    return commit_sha or None


def state_entry_for(
    component: LocalComponent,
    request_resource_name: str,
    *,
    remote_update_time: str | None = None,
    remote_create_time: str | None = None,
) -> dict[str, Any]:
    entry = {
        "kind": component.kind,
        "resource_id": component.resource_id,
        "display_name": component.display_name,
        "source_path": str(component.source_path),
        "tracked_files": [
            {
                "path": relative_path,
                "sha256": sha256,
            }
            for relative_path, sha256 in sorted(component.file_hashes.items())
        ],
        "combined_sha256": component.combined_hash,
        "deployed_at": timestamp_now(),
        "resource_name": request_resource_name,
    }
    if remote_update_time:
        entry["remote_update_time"] = remote_update_time
    if remote_create_time:
        entry["remote_create_time"] = remote_create_time
    return entry


def tracked_file_entries(component: LocalComponent) -> list[dict[str, str]]:
    return [
        {"path": relative_path, "sha256": sha256}
        for relative_path, sha256 in sorted(component.file_hashes.items())
    ]


def plan_status_by_key(plan: dict[str, list[LocalComponent]]) -> dict[str, str]:
    status_map: dict[str, str] = {}
    for label in ["added", "modified", "noop"]:
        for component in plan[label]:
            status_map[component.key] = label
    return status_map


def build_artifact_component_entries(
    local_components: list[LocalComponent],
    state_components: dict[str, Any],
    plan: dict[str, list[LocalComponent]],
    removed: list[str],
) -> list[dict[str, Any]]:
    entries: list[dict[str, Any]] = []
    status_map = plan_status_by_key(plan)
    for component in local_components:
        previous = state_components.get(component.key, {})
        plan_status = status_map[component.key]
        before_hash = previous.get("combined_sha256") if isinstance(previous, dict) else None
        before_resource_name = previous.get("resource_name") if isinstance(previous, dict) else None
        before_remote_update_time = (
            previous.get("remote_update_time") if isinstance(previous, dict) else None
        )
        before_remote_create_time = (
            previous.get("remote_create_time") if isinstance(previous, dict) else None
        )
        execution_status = "pending"
        operation = "apply"
        after_resource_name = None
        after_remote_update_time = None
        after_remote_create_time = None
        if plan_status == "noop":
            execution_status = "skipped"
            operation = "noop"
            after_resource_name = before_resource_name
            after_remote_update_time = before_remote_update_time
            after_remote_create_time = before_remote_create_time
        entries.append(
            {
                "key": component.key,
                "kind": component.kind,
                "resource_id": component.resource_id,
                "display_name": component.display_name,
                "source_path": str(component.source_path),
                "tracked_files": tracked_file_entries(component),
                "plan_status": plan_status,
                "before_combined_sha256": before_hash,
                "after_combined_sha256": component.combined_hash,
                "before_resource_name": before_resource_name,
                "after_resource_name": after_resource_name,
                "before_remote_update_time": before_remote_update_time,
                "after_remote_update_time": after_remote_update_time,
                "before_remote_create_time": before_remote_create_time,
                "after_remote_create_time": after_remote_create_time,
                "operation": operation,
                "execution_status": execution_status,
                "http_status": None,
                "state_updated": False,
                "deployed_at": None,
                "error": None,
            }
        )

    for key in removed:
        previous = state_components.get(key, {})
        entries.append(
            {
                "key": key,
                "kind": previous.get("kind"),
                "resource_id": previous.get("resource_id"),
                "display_name": previous.get("display_name"),
                "source_path": previous.get("source_path"),
                "tracked_files": previous.get("tracked_files", []),
                "plan_status": "removed",
                "before_combined_sha256": previous.get("combined_sha256"),
                "after_combined_sha256": None,
                "before_resource_name": previous.get("resource_name"),
                "after_resource_name": None,
                "before_remote_update_time": previous.get("remote_update_time"),
                "after_remote_update_time": None,
                "before_remote_create_time": previous.get("remote_create_time"),
                "after_remote_create_time": None,
                "operation": "none",
                "execution_status": "not_applicable",
                "http_status": None,
                "state_updated": False,
                "deployed_at": None,
                "error": None,
            }
        )
    return entries


def build_run_artifact(
    *,
    run_id: str,
    app_root: Path,
    state_file: Path,
    artifacts_dir: Path,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    validate_only: bool,
    auto_confirm: bool,
    git_commit: str | None,
    local_components: list[LocalComponent],
    state_components: dict[str, Any],
    plan: dict[str, list[LocalComponent]],
    removed: list[str],
) -> dict[str, Any]:
    components = build_artifact_component_entries(local_components, state_components, plan, removed)
    artifact = {
        "run_id": run_id,
        "started_at": timestamp_now(),
        "completed_at": None,
        "status": "planned",
        "mode": {
            "validate_only": validate_only,
            "auto_confirm": auto_confirm,
        },
        "target": {
            "project": project,
            "location": location,
            "app_id": app_id,
            "app_root": str(app_root),
            "state_file": str(state_file),
            "artifacts_dir": str(artifacts_dir),
            "endpoint": endpoint,
        },
        "git": {
            "commit_sha": git_commit,
        },
        "plan": {
            "summary": {
                "added": len(plan["added"]),
                "modified": len(plan["modified"]),
                "noop": len(plan["noop"]),
                "removed": len(removed),
                "actionable": len(plan["added"]) + len(plan["modified"]),
            },
            "components": components,
        },
        "outcome": {
            "message": None,
            "summary": {},
        },
    }
    update_outcome_summary(artifact)
    return artifact


def build_initial_run_artifact(
    *,
    run_id: str,
    app_root: Path,
    state_file: Path,
    artifacts_dir: Path,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    validate_only: bool,
    auto_confirm: bool,
    git_commit: str | None,
) -> dict[str, Any]:
    artifact = {
        "run_id": run_id,
        "started_at": timestamp_now(),
        "completed_at": None,
        "status": "starting",
        "mode": {
            "validate_only": validate_only,
            "auto_confirm": auto_confirm,
        },
        "target": {
            "project": project,
            "location": location,
            "app_id": app_id,
            "app_root": str(app_root),
            "state_file": str(state_file),
            "artifacts_dir": str(artifacts_dir),
            "endpoint": endpoint,
        },
        "git": {
            "commit_sha": git_commit,
        },
        "plan": {
            "summary": {
                "added": 0,
                "modified": 0,
                "noop": 0,
                "removed": 0,
                "actionable": 0,
            },
            "components": [],
        },
        "outcome": {
            "message": None,
            "summary": {},
        },
    }
    update_outcome_summary(artifact)
    return artifact


def artifact_component(artifact: dict[str, Any], key: str) -> dict[str, Any]:
    for entry in artifact["plan"]["components"]:
        if entry.get("key") == key:
            return entry
    raise DeploymentManagerError(f"Artifact is missing component entry for {key}")


def update_outcome_summary(artifact: dict[str, Any]) -> None:
    summary: dict[str, int] = {}
    for entry in artifact["plan"]["components"]:
        status = entry.get("execution_status", "unknown")
        summary[status] = summary.get(status, 0) + 1
    artifact["outcome"]["summary"] = summary


def finalize_artifact(artifact: dict[str, Any], *, status: str, message: str) -> None:
    artifact["status"] = status
    artifact["completed_at"] = timestamp_now()
    artifact["outcome"]["message"] = message
    update_outcome_summary(artifact)


def write_run_artifact(path: Path, artifact: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(artifact, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def write_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    temp_path.replace(path)


def latest_artifact_path(artifacts_dir: Path) -> Path | None:
    if not artifacts_dir.is_dir():
        return None
    candidates = [path for path in artifacts_dir.iterdir() if path.suffix == ".json"]
    if not candidates:
        return None
    return sorted(candidates)[-1]


def load_latest_artifact(artifacts_dir: Path) -> dict[str, Any] | None:
    artifact_path = latest_artifact_path(artifacts_dir)
    if artifact_path is None:
        return None
    return load_json(artifact_path)


def print_status_report(
    state: dict[str, Any],
    latest_artifact: dict[str, Any] | None,
    *,
    as_json: bool,
) -> None:
    components_state = state.get("components", {})
    component_entries = []
    if isinstance(components_state, dict):
        component_entries = [
            entry
            for entry in components_state.values()
            if isinstance(entry, dict)
        ]

    summary = {
        "app_root": state.get("app_root"),
        "project": state.get("project"),
        "location": state.get("location"),
        "app_id": state.get("app_id"),
        "components": sorted(
            [
                {
                    "kind": entry.get("kind"),
                    "resource_id": entry.get("resource_id"),
                    "display_name": entry.get("display_name"),
                    "deployed_at": entry.get("deployed_at"),
                    "remote_update_time": entry.get("remote_update_time"),
                    "remote_create_time": entry.get("remote_create_time"),
                    "resource_name": entry.get("resource_name"),
                }
                for entry in component_entries
            ],
            key=lambda item: (
                KIND_ORDER.get(str(item.get("kind")), 99),
                str(item.get("resource_id") or ""),
            ),
        ),
        "latest_run": None,
    }

    if latest_artifact:
        target = latest_artifact.get("target", {})
        summary["latest_run"] = {
            "run_id": latest_artifact.get("run_id"),
            "status": latest_artifact.get("status"),
            "started_at": latest_artifact.get("started_at"),
            "completed_at": latest_artifact.get("completed_at"),
            "git_commit_sha": latest_artifact.get("git", {}).get("commit_sha"),
            "message": latest_artifact.get("outcome", {}).get("message"),
            "artifact_path": str(
                latest_artifact_path(Path(target.get("artifacts_dir", "")))
            )
            if isinstance(target, dict) and target.get("artifacts_dir")
            else None,
        }

    if as_json:
        print(json.dumps(summary, indent=2, sort_keys=True))
        return

    print("")
    print("Deployment Status")
    print("=================")
    print(f"App root: {summary['app_root'] or '-'}")
    print(
        f"Target: {summary['project'] or '-'} / "
        f"{summary['location'] or '-'} / {summary['app_id'] or '-'}"
    )
    if summary["latest_run"]:
        latest_run = summary["latest_run"]
        print(
            "Latest run: "
            f"{latest_run['run_id']} "
            f"status={latest_run['status'] or '-'} "
            f"completed_at={latest_run['completed_at'] or latest_run['started_at'] or '-'}"
        )
        print(f"Latest git SHA: {latest_run['git_commit_sha'] or '-'}")
        if latest_run["artifact_path"]:
            print(f"Latest artifact: {latest_run['artifact_path']}")
        if latest_run["message"]:
            print(f"Latest outcome: {latest_run['message']}")
    else:
        print("Latest run: none")

    print("")
    print(f"Components ({len(summary['components'])})")
    print("------------")
    if not summary["components"]:
        print("  - none")
        print("")
        return

    for component in summary["components"]:
        print(
            f"- {component['kind']:<7} {component['resource_id']:<30} "
            f"displayName={component['display_name']}"
        )
        print(
            f"  deployed_at={component['deployed_at'] or '-'} "
            f"remote_update_time={component['remote_update_time'] or '-'}"
        )
    print("")


def actionable_components(plan: dict[str, list[LocalComponent]]) -> list[LocalComponent]:
    items = [*plan["added"], *plan["modified"]]
    return sorted(items, key=lambda item: (KIND_ORDER[item.kind], item.resource_id))


def reconcile_noop_components_with_remote(
    plan: dict[str, list[LocalComponent]],
    state_components: dict[str, Any],
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    token: str,
) -> tuple[dict[str, list[LocalComponent]], list[LocalComponent]]:
    refreshed_plan: dict[str, list[LocalComponent]] = {
        "added": list(plan["added"]),
        "modified": list(plan["modified"]),
        "noop": [],
    }
    stale_components: list[LocalComponent] = []

    for component in plan["noop"]:
        agent_names, tool_names, toolset_names = resolved_resource_maps(
            state_components,
            project=project,
            location=location,
            app_id=app_id,
        )
        request = build_request(
            kind=component.kind,
            source_path=component.source_path,
            project=project,
            location=location,
            app_id=app_id,
            endpoint=endpoint,
            agent_names=agent_names,
            tool_names=tool_names,
            toolset_names=toolset_names,
        )
        preferred_resource_name = preferred_resource_name_for(
            component,
            state_components,
            project=project,
            location=location,
            app_id=app_id,
        )
        existing_resource_name = find_existing_resource_name(
            request,
            token,
            preferred_resource_name=preferred_resource_name,
        )
        if existing_resource_name:
            state_entry = state_components.get(component.key)
            if isinstance(state_entry, dict):
                state_entry["resource_name"] = existing_resource_name
            refreshed_plan["noop"].append(component)
            continue

        stale_components.append(component)
        refreshed_plan["added"].append(component)

    return refreshed_plan, stale_components


def resolved_resource_maps(
    state_components: dict[str, Any],
    *,
    project: str | None = None,
    location: str | None = None,
    app_id: str | None = None,
) -> tuple[dict[str, str], dict[str, str], dict[str, str]]:
    agent_names: dict[str, str] = {}
    tool_names: dict[str, str] = {}
    toolset_names: dict[str, str] = {}
    resource_prefix = None
    if project and location and app_id:
        resource_prefix = f"projects/{project}/locations/{location}/apps/{app_id}/"
    for entry in state_components.values():
        if not isinstance(entry, dict):
            continue
        resource_name = entry.get("resource_name")
        if not isinstance(resource_name, str) or not resource_name:
            continue
        if resource_prefix and not resource_name.startswith(resource_prefix):
            continue
        targets = None
        kind = entry.get("kind")
        if kind == "agent":
            targets = agent_names
        elif kind == "tool":
            targets = tool_names
        elif kind == "toolset":
            targets = toolset_names
        if targets is None:
            continue
        resource_id = entry.get("resource_id")
        if isinstance(resource_id, str) and resource_id:
            targets[resource_id] = resource_name
        display_name = entry.get("display_name")
        if isinstance(display_name, str) and display_name:
            targets[display_name] = resource_name
    return agent_names, tool_names, toolset_names


def preferred_resource_name_for(
    component: LocalComponent,
    state_components: dict[str, Any],
    *,
    project: str,
    location: str,
    app_id: str,
) -> str | None:
    entry = state_components.get(component.key)
    if not isinstance(entry, dict):
        return None
    resource_name = entry.get("resource_name")
    if not isinstance(resource_name, str) or not resource_name:
        return None
    resource_prefix = f"projects/{project}/locations/{location}/apps/{app_id}/"
    if not resource_name.startswith(resource_prefix):
        return None
    return resource_name


def remote_metadata_from_body(body: str) -> dict[str, str | None]:
    if not body.strip():
        return {
            "name": None,
            "update_time": None,
            "create_time": None,
        }
    parsed_body = parse_json_body(body)
    metadata: dict[str, str | None] = {
        "name": None,
        "update_time": None,
        "create_time": None,
    }
    for field, target in [
        ("name", "name"),
        ("updateTime", "update_time"),
        ("createTime", "create_time"),
    ]:
        value = parsed_body.get(field)
        if isinstance(value, str) and value:
            metadata[target] = value
    return metadata


def deployment_error_message(
    component: LocalComponent,
    *,
    project: str,
    location: str,
    app_id: str,
    status: int,
    body: str,
) -> str:
    detail = None
    if body.strip():
        try:
            parsed = parse_json_body(body)
        except DeployResourceError:
            parsed = {}
        error = parsed.get("error")
        if isinstance(error, dict):
            raw_message = error.get("message")
            if isinstance(raw_message, str) and raw_message.strip():
                detail = raw_message.strip()

    base_message = (
        f"Deployment failed for {component.key} with HTTP {status}."
    )
    if status == 404:
        bootstrap_hint = f" {bootstrap_import_hint(project, location, app_id)}"
        base_message += bootstrap_hint
    if detail:
        base_message += f" CES said: {detail}"
    return base_message


def execute_plan(
    components: list[LocalComponent],
    *,
    app_root: Path,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    state: dict[str, Any],
    state_file: Path,
    artifact: dict[str, Any],
    artifact_path: Path,
    token: str | None = None,
) -> int:
    if not components:
        print("No remote changes required.")
        finalize_artifact(artifact, status="noop", message="No remote changes required.")
        write_run_artifact(artifact_path, artifact)
        return 0

    token = token or get_access_token()
    components_state = state.setdefault("components", {})
    state.setdefault("schema_version", SCHEMA_VERSION)
    state["app_root"] = str(app_root)
    state["project"] = project
    state["location"] = location
    state["app_id"] = app_id

    for index, component in enumerate(components, start=1):
        print(
            f"[{index}/{len(components)}] Deploying {component.kind} "
            f"{component.resource_id} ({component.display_name})"
        )
        artifact_entry = artifact_component(artifact, component.key)
        agent_names, tool_names, toolset_names = resolved_resource_maps(
            components_state,
            project=project,
            location=location,
            app_id=app_id,
        )
        request = build_request(
            kind=component.kind,
            source_path=component.source_path,
            project=project,
            location=location,
            app_id=app_id,
            endpoint=endpoint,
            agent_names=agent_names,
            tool_names=tool_names,
            toolset_names=toolset_names,
        )
        preferred_resource_name = preferred_resource_name_for(
            component,
            components_state,
            project=project,
            location=location,
            app_id=app_id,
        )
        existing_resource_name = find_existing_resource_name(
            request,
            token,
            preferred_resource_name=preferred_resource_name,
        )
        artifact_entry["operation"] = "patch" if existing_resource_name else "create"
        if existing_resource_name:
            artifact_entry["before_resource_name"] = existing_resource_name
        status, body = apply_request(request, token, existing_resource_name)
        artifact_entry["http_status"] = status
        if 200 <= status < 300:
            print(f"  PASS  HTTP {status}")
            metadata = remote_metadata_from_body(body)
            resolved_resource_name = (
                metadata["name"] or existing_resource_name or request.resource_name
            )
            components_state[component.key] = state_entry_for(
                component,
                resolved_resource_name,
                remote_update_time=metadata["update_time"],
                remote_create_time=metadata["create_time"],
            )
            write_state(state_file, state)
            artifact_entry["execution_status"] = "success"
            artifact_entry["state_updated"] = True
            artifact_entry["deployed_at"] = timestamp_now()
            artifact_entry["after_resource_name"] = resolved_resource_name
            artifact_entry["after_remote_update_time"] = metadata["update_time"]
            artifact_entry["after_remote_create_time"] = metadata["create_time"]
            artifact_entry["error"] = None
            update_outcome_summary(artifact)
            write_run_artifact(artifact_path, artifact)
            continue

        print(f"  FAIL  HTTP {status}", file=sys.stderr)
        if body:
            print(body, file=sys.stderr)
        error_message = deployment_error_message(
            component,
            project=project,
            location=location,
            app_id=app_id,
            status=status,
            body=body,
        )
        artifact_entry["execution_status"] = "failed"
        artifact_entry["state_updated"] = False
        artifact_entry["deployed_at"] = timestamp_now()
        artifact_entry["error"] = error_message
        finalize_artifact(
            artifact,
            status="failed",
            message=error_message,
        )
        write_run_artifact(artifact_path, artifact)
        print(
            "Stopping further deploys to avoid applying agents after a failed dependency.",
            file=sys.stderr,
        )
        return 1

    finalize_artifact(
        artifact,
        status="success",
        message=f"Applied {len(components)} resource change(s) successfully.",
    )
    write_run_artifact(artifact_path, artifact)
    return 0


def main() -> int:
    ensure_env_loaded()
    args = parse_args()

    app_root = Path(args.app_root).resolve()
    state_file = Path(args.state_file).resolve()
    artifacts_dir = Path(args.artifacts_dir).resolve()
    if args.status or args.status_json:
        state = load_state(state_file)
        latest_artifact = load_latest_artifact(artifacts_dir)
        print_status_report(state, latest_artifact, as_json=args.status_json)
        return 0

    project = args.project or os.environ.get("GCP_PROJECT_ID", "")
    location = args.location or os.environ.get("GCP_LOCATION", "")
    app_id = args.app_id or os.environ.get("CES_APP_ID", "")
    run_id = run_id_now()
    artifact_path = artifacts_dir / f"{run_id}.json"
    git_commit = git_commit_sha(repo_root())

    artifact: dict[str, Any] | None = build_initial_run_artifact(
        run_id=run_id,
        app_root=app_root,
        state_file=state_file,
        artifacts_dir=artifacts_dir,
        project=project,
        location=location,
        app_id=app_id,
        endpoint=args.endpoint,
        validate_only=args.validate_only,
        auto_confirm=args.yes,
        git_commit=git_commit,
    )
    write_run_artifact(artifact_path, artifact)
    print(f"Run artifact: {artifact_path}")

    try:
        run_validator(app_root)

        local_components = discover_components(app_root)
        state = load_state(state_file)
        state_components = state.get("components", {})
        plan = classify_components(local_components, state_components)
        removed = find_removed_components(local_components, state_components)

        token: str | None = None
        if not args.validate_only:
            project = require_value("project", project)
            location = require_value("location", location)
            app_id = require_value("app-id", app_id)
            token = get_access_token()
            target_status = print_execution_target(
                project=project,
                location=location,
                app_id=app_id,
                endpoint=args.endpoint,
                token=token,
            )
            if not target_status.app_exists:
                print(missing_app_message(project, location, app_id))
                if not confirm_bootstrap_import(
                    args.yes,
                    project=project,
                    location=location,
                    app_id=app_id,
                ):
                    print("Bootstrap import aborted.")
                    finalize_artifact(
                        artifact,
                        status="cancelled",
                        message="Bootstrap import aborted by user.",
                    )
                    write_run_artifact(artifact_path, artifact)
                    return 0
                run_bootstrap_import(
                    app_root=app_root,
                    project=project,
                    location=location,
                    app_id=app_id,
                )
                target_status = print_execution_target(
                    project=project,
                    location=location,
                    app_id=app_id,
                    endpoint=args.endpoint,
                    token=token,
                )
                if not target_status.app_exists:
                    raise DeploymentManagerError(
                        "Bootstrap import reported success but the CES app still could not be found."
                    )
                plan, discovered_existing = reconcile_added_components_with_remote(
                    plan,
                    state_components,
                    project=project,
                    location=location,
                    app_id=app_id,
                    endpoint=args.endpoint,
                    token=token,
                )
                if discovered_existing:
                    write_state(state_file, state)
                    print(
                        "Initialized local deployment state from the freshly bootstrapped app for "
                        f"{len(discovered_existing)} resource(s)."
                    )
            plan, stale_components = reconcile_noop_components_with_remote(
                plan,
                state_components,
                project=project,
                location=location,
                app_id=app_id,
                endpoint=args.endpoint,
                token=token,
            )
            if stale_components:
                print(
                    "Detected stale local deployment state for "
                    f"{len(stale_components)} resource(s); redeploying missing CES resources."
                )

        artifact = build_run_artifact(
            run_id=run_id,
            app_root=app_root,
            state_file=state_file,
            artifacts_dir=artifacts_dir,
            project=project,
            location=location,
            app_id=app_id,
            endpoint=args.endpoint,
            validate_only=args.validate_only,
            auto_confirm=args.yes,
            git_commit=git_commit,
            local_components=local_components,
            state_components=state_components,
            plan=plan,
            removed=removed,
        )
        write_run_artifact(artifact_path, artifact)
        print_plan(plan, removed)

        actions = actionable_components(plan)
        if not actions:
            print("Everything is up to date.")
            finalize_artifact(artifact, status="noop", message="Everything is up to date.")
            write_run_artifact(artifact_path, artifact)
            return 0

        if args.validate_only:
            print("Validation-only mode; no remote changes executed.")
            finalize_artifact(
                artifact,
                status="validate_only",
                message="Validation-only mode; no remote changes executed.",
            )
            write_run_artifact(artifact_path, artifact)
            return 0

        if not confirm_plan(args.yes):
            print("Deployment aborted.")
            finalize_artifact(artifact, status="cancelled", message="Deployment aborted by user.")
            write_run_artifact(artifact_path, artifact)
            return 0

        return execute_plan(
            actions,
            app_root=app_root,
            project=project,
            location=location,
            app_id=app_id,
            endpoint=args.endpoint,
            state=state,
            state_file=state_file,
            artifact=artifact,
            artifact_path=artifact_path,
            token=token,
        )
    except (DeploymentManagerError, DeployResourceError) as exc:
        if artifact is not None:
            finalize_artifact(artifact, status="failed", message=str(exc))
            write_run_artifact(artifact_path, artifact)
        raise


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (DeploymentManagerError, DeployResourceError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
