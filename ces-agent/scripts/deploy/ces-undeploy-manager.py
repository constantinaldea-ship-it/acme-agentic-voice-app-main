#!/usr/bin/env python3
"""Idempotent CES undeploy manager for tools, toolsets, and agents."""

from __future__ import annotations

import argparse
import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from lib.deploy_resource_lib import (
    DeployResourceError,
    DeploymentRequest,
    detect_endpoint,
    find_existing_resource_name,
    full_agent_name,
    full_tool_name,
    full_toolset_name,
    get_access_token,
    http_request,
    load_json,
)

KIND_FOLDERS = {"agent": "agents", "tool": "tools", "toolset": "toolsets"}
UNDEPLOY_KIND_ORDER = {"agent": 0, "tool": 1, "toolset": 2}


class UndeployManagerError(RuntimeError):
    """Raised when the undeploy manager cannot build or execute a safe plan."""


@dataclass(frozen=True)
class UndeployTarget:
    key: str
    kind: str
    resource_id: str
    display_name: str
    source_path: Path | None
    resource_name: str | None
    state_only: bool


def script_dir() -> Path:
    return SCRIPT_DIR


def ces_agent_dir() -> Path:
    return script_dir().parent.parent


def default_app_root() -> Path:
    return ces_agent_dir() / "acme_voice_agent"


def default_state_file() -> Path:
    return ces_agent_dir() / ".ces-deployment-state.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Delete CES agents, tools, and toolsets idempotently."
    )
    parser.add_argument("--app-root", default=str(default_app_root()))
    parser.add_argument("--state-file", default=str(default_state_file()))
    parser.add_argument("--project", default=os.environ.get("GCP_PROJECT_ID", ""))
    parser.add_argument("--location", default=os.environ.get("GCP_LOCATION", ""))
    parser.add_argument("--app-id", default=os.environ.get("CES_APP_ID", ""))
    parser.add_argument("--endpoint", help="Override CES API endpoint.")
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Skip interactive confirmation and apply the undeploy plan immediately.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print the undeploy plan without executing remote deletions.",
    )
    return parser.parse_args()


def require_value(label: str, value: str) -> str:
    if value:
        return value
    raise UndeployManagerError(f"Missing required value: {label}")


def component_key(kind: str, resource_id: str) -> str:
    return f"{kind}:{resource_id}"


def load_state(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"schema_version": 1, "components": {}}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise UndeployManagerError(f"Invalid deployment state file: {path}") from exc
    if not isinstance(data, dict):
        raise UndeployManagerError(f"Unexpected deployment state shape in {path}")
    data.setdefault("schema_version", 1)
    data.setdefault("components", {})
    return data


def write_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def discover_local_targets(app_root: Path) -> list[UndeployTarget]:
    if not app_root.is_dir():
        return []
    targets: list[UndeployTarget] = []
    for kind, folder in KIND_FOLDERS.items():
        root = app_root / folder
        if not root.is_dir():
            continue
        for resource_dir in sorted(root.iterdir()):
            if not resource_dir.is_dir():
                continue
            manifest_path = resource_dir / f"{resource_dir.name}.json"
            if not manifest_path.is_file():
                continue
            manifest = load_json(manifest_path)
            display_name = manifest.get("displayName")
            targets.append(
                UndeployTarget(
                    key=component_key(kind, resource_dir.name),
                    kind=kind,
                    resource_id=resource_dir.name,
                    display_name=display_name if isinstance(display_name, str) and display_name else resource_dir.name,
                    source_path=manifest_path.resolve(),
                    resource_name=None,
                    state_only=False,
                )
            )
    return targets


def merge_targets(
    local_targets: list[UndeployTarget], state_components: dict[str, Any]
) -> list[UndeployTarget]:
    merged: dict[str, UndeployTarget] = {}
    for key, entry in state_components.items():
        if not isinstance(entry, dict):
            continue
        kind = entry.get("kind")
        resource_id = entry.get("resource_id")
        if not isinstance(kind, str) or kind not in UNDEPLOY_KIND_ORDER:
            continue
        if not isinstance(resource_id, str) or not resource_id:
            continue
        display_name = entry.get("display_name")
        source_path = entry.get("source_path")
        resource_name = entry.get("resource_name")
        merged[key] = UndeployTarget(
            key=key,
            kind=kind,
            resource_id=resource_id,
            display_name=display_name if isinstance(display_name, str) and display_name else resource_id,
            source_path=Path(source_path).resolve() if isinstance(source_path, str) and source_path else None,
            resource_name=resource_name if isinstance(resource_name, str) and resource_name else None,
            state_only=True,
        )

    for target in local_targets:
        previous = state_components.get(target.key, {})
        stored_resource_name = previous.get("resource_name") if isinstance(previous, dict) else None
        merged[target.key] = UndeployTarget(
            key=target.key,
            kind=target.kind,
            resource_id=target.resource_id,
            display_name=target.display_name,
            source_path=target.source_path,
            resource_name=stored_resource_name if isinstance(stored_resource_name, str) and stored_resource_name else None,
            state_only=False,
        )

    return sorted(
        merged.values(),
        key=lambda item: (UNDEPLOY_KIND_ORDER[item.kind], item.resource_id),
    )


def deterministic_resource_name(
    kind: str, project: str, location: str, app_id: str, resource_id: str
) -> str:
    if kind == "agent":
        return full_agent_name(project, location, app_id, resource_id)
    if kind == "tool":
        return full_tool_name(project, location, app_id, resource_id)
    if kind == "toolset":
        return full_toolset_name(project, location, app_id, resource_id)
    raise UndeployManagerError(f"Unsupported resource kind: {kind}")


def lookup_request_for(
    target: UndeployTarget, project: str, location: str, app_id: str, endpoint: str | None
) -> DeploymentRequest:
    endpoint_root = detect_endpoint(location, endpoint)
    app_name = f"projects/{project}/locations/{location}/apps/{app_id}"
    resource_name = deterministic_resource_name(
        target.kind, project, location, app_id, target.resource_id
    )
    return DeploymentRequest(
        kind=target.kind,
        source_path=target.source_path or Path("."),
        resource_id=target.resource_id,
        collection_url=f"{endpoint_root}/v1/{app_name}/{KIND_FOLDERS[target.kind]}",
        resource_url=f"{endpoint_root}/v1/{resource_name}",
        create_query="",
        resource_name=resource_name,
        display_name=target.display_name,
        update_mask="",
        payload={},
    )


def print_plan(targets: list[UndeployTarget]) -> None:
    groups = {kind: [] for kind in ["agent", "tool", "toolset"]}
    for target in targets:
        groups[target.kind].append(target)

    print("")
    print("Undeploy Plan")
    print("=============")
    for kind, label in [("agent", "Agents"), ("tool", "Tools"), ("toolset", "Toolsets")]:
        items = groups[kind]
        print(f"{label} ({len(items)}):")
        if not items:
            print("  - none")
            continue
        for item in items:
            state_suffix = " (state only)" if item.state_only else ""
            print(f"  - {item.resource_id:<30} displayName={item.display_name}{state_suffix}")
    print("")


def confirm_plan(auto_confirm: bool) -> bool:
    if auto_confirm:
        return True
    reply = input("Apply undeploy plan? [y/N] ").strip().lower()
    return reply in {"y", "yes"}


def is_not_found_response(status: int, body: str) -> bool:
    if status == 404:
        return True
    lowered = body.lower()
    return "not found" in lowered or "requested entity was not found" in lowered


def delete_resource_by_name(
    resource_name: str, location: str, endpoint: str | None, token: str
) -> tuple[int, str]:
    endpoint_root = detect_endpoint(location, endpoint)
    return http_request("DELETE", f"{endpoint_root}/v1/{resource_name}", token)


def execute_plan(
    targets: list[UndeployTarget],
    *,
    project: str,
    location: str,
    app_id: str,
    endpoint: str | None,
    state: dict[str, Any],
    state_file: Path,
    dry_run: bool,
) -> int:
    if not targets:
        print("No CES resources discovered for undeploy.")
        return 0
    if dry_run:
        print("Dry-run mode; no remote deletions executed.")
        return 0

    token = get_access_token()
    components_state = state.setdefault("components", {})
    failures: list[str] = []
    failed_keys: set[str] = set()
    deleted = 0
    absent = 0

    for index, target in enumerate(targets, start=1):
        print(f"[{index}/{len(targets)}] Deleting {target.kind} {target.resource_id} ({target.display_name})")
        try:
            attempted_names: list[str] = []
            if target.resource_name:
                attempted_names.append(target.resource_name)
            lookup_request = lookup_request_for(target, project, location, app_id, endpoint)
            if target.resource_name is None:
                resolved_name = find_existing_resource_name(lookup_request, token)
                if resolved_name:
                    attempted_names.append(resolved_name)
            elif target.source_path is not None:
                status, body = delete_resource_by_name(target.resource_name, location, endpoint, token)
                if 200 <= status < 300:
                    print(f"  PASS  deleted {target.resource_name}")
                    deleted += 1
                    if target.key in components_state:
                        del components_state[target.key]
                        write_state(state_file, state)
                    continue
                if not is_not_found_response(status, body):
                    failures.append(f"{target.key}: HTTP {status} {body or 'delete failed'}")
                    failed_keys.add(target.key)
                    print(f"  FAIL  HTTP {status}", file=sys.stderr)
                    continue
                resolved_name = find_existing_resource_name(lookup_request, token)
                if resolved_name and resolved_name not in attempted_names:
                    attempted_names.append(resolved_name)

            deterministic_name = lookup_request.resource_name
            if deterministic_name not in attempted_names:
                attempted_names.append(deterministic_name)

            deleted_this_target = False
            for resource_name in attempted_names:
                status, body = delete_resource_by_name(resource_name, location, endpoint, token)
                if 200 <= status < 300:
                    print(f"  PASS  deleted {resource_name}")
                    deleted += 1
                    deleted_this_target = True
                    break
                if is_not_found_response(status, body):
                    continue
                failures.append(f"{target.key}: HTTP {status} {body or 'delete failed'}")
                failed_keys.add(target.key)
                print(f"  FAIL  HTTP {status}", file=sys.stderr)
                deleted_this_target = False
                break

            if deleted_this_target or target.key not in failed_keys:
                if not deleted_this_target:
                    print("  PASS  already absent")
                    absent += 1
                if target.key in components_state:
                    del components_state[target.key]
                    write_state(state_file, state)
        except DeployResourceError as exc:
            failures.append(f"{target.key}: {exc}")
            failed_keys.add(target.key)
            print(f"  FAIL  {exc}", file=sys.stderr)

    print("")
    print(f"Undeploy summary: deleted={deleted} absent={absent} failed={len(failures)}")
    if failures:
        print("Failures:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    args = parse_args()
    project = require_value("project", args.project)
    location = require_value("location", args.location)
    app_id = require_value("app-id", args.app_id)
    app_root = Path(args.app_root).resolve()
    state_file = Path(args.state_file).resolve()
    state = load_state(state_file)
    targets = merge_targets(discover_local_targets(app_root), state.get("components", {}))
    print_plan(targets)
    if not confirm_plan(args.yes):
        print("Undeploy aborted.")
        return 0
    return execute_plan(
        targets,
        project=project,
        location=location,
        app_id=app_id,
        endpoint=args.endpoint,
        state=state,
        state_file=state_file,
        dry_run=args.dry_run,
    )


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (UndeployManagerError, DeployResourceError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)