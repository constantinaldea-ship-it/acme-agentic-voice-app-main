#!/usr/bin/env python3
"""Shared helpers for resource-level CES deployments."""

from __future__ import annotations

import json
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ENDPOINTS = {
    "us": "https://ces.us.rep.googleapis.com",
    "eu": "https://ces.eu.rep.googleapis.com",
}

BUILTIN_TOOLS = {"end_session"}

AGENT_ALLOWED_UPDATE_FIELDS = [
    "displayName",
    "description",
    "instruction",
    "modelSettings",
    "tools",
    "toolsets",
    "childAgents",
    "beforeAgentCallbacks",
    "beforeModelCallbacks",
    "beforeToolCallbacks",
    "afterAgentCallbacks",
    "afterModelCallbacks",
    "afterToolCallbacks",
    "guardrails",
    "transferRules",
]

TOOL_ALLOWED_UPDATE_FIELDS = [
    "displayName",
    "agentTool",
    "clientFunction",
    "connectorTool",
    "dataStoreTool",
    "executionType",
    "fileSearchTool",
    "googleSearchTool",
    "openApiTool",
    "pythonFunction",
    "widgetTool",
]

TOOLSET_ALLOWED_UPDATE_FIELDS = [
    "displayName",
    "description",
    "openApiToolset",
]


class DeployResourceError(RuntimeError):
    """Raised when CES deployment input or request construction is invalid."""


@dataclass(frozen=True)
class DeploymentRequest:
    """Resolved CES request for a single resource."""

    kind: str
    source_path: Path
    resource_id: str
    collection_url: str
    resource_url: str
    create_query: str
    resource_name: str
    display_name: str
    update_mask: str
    payload: dict[str, Any]


def detect_endpoint(location: str, explicit: str | None = None) -> str:
    if explicit:
        return explicit.rstrip("/")
    return ENDPOINTS.get(location, "https://ces.googleapis.com")


def find_app_root(start: Path) -> Path:
    current = start.resolve()
    for candidate in [current, *current.parents]:
        if (candidate / "app.json").exists() or (candidate / "app.yaml").exists():
            return candidate
    raise DeployResourceError(
        f"Could not find app root above {start}. Expected app.json or app.yaml."
    )


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(app_root: Path, relative_path: str) -> str:
    target = (app_root / relative_path).resolve()
    if not target.exists():
        raise DeployResourceError(f"Referenced file does not exist: {relative_path}")
    return target.read_text(encoding="utf-8")


def full_agent_name(project: str, location: str, app_id: str, agent_id: str) -> str:
    return f"projects/{project}/locations/{location}/apps/{app_id}/agents/{agent_id}"


def full_tool_name(project: str, location: str, app_id: str, tool_id: str) -> str:
    return f"projects/{project}/locations/{location}/apps/{app_id}/tools/{tool_id}"


def full_toolset_name(project: str, location: str, app_id: str, toolset_id: str) -> str:
    return f"projects/{project}/locations/{location}/apps/{app_id}/toolsets/{toolset_id}"


def resolve_tool_name(
    tool_id: str,
    *,
    project: str,
    location: str,
    app_id: str,
    tool_names: dict[str, str] | None = None,
) -> str:
    resolved_from_state = (tool_names or {}).get(tool_id)
    if isinstance(resolved_from_state, str) and resolved_from_state:
        return resolved_from_state
    return full_tool_name(project, location, app_id, tool_id)


def convert_callbacks(
    app_root: Path,
    callbacks: list[dict[str, Any]] | None,
) -> list[dict[str, Any]]:
    converted: list[dict[str, Any]] = []
    for callback in callbacks or []:
        callback_copy = dict(callback)
        python_code = callback_copy.get("pythonCode")
        if isinstance(python_code, str) and python_code.strip():
            callback_copy["pythonCode"] = read_text(app_root, python_code)
        converted.append(callback_copy)
    return converted


def build_update_mask(payload: dict[str, Any], allowed_fields: list[str]) -> str:
    update_fields = [field for field in allowed_fields if field in payload]
    if not update_fields:
        raise DeployResourceError("Payload does not contain any patchable CES fields.")
    return ",".join(update_fields)


def convert_agent_manifest(
    manifest: dict[str, Any],
    app_root: Path,
    project: str,
    location: str,
    app_id: str,
    agent_names: dict[str, str] | None = None,
    tool_names: dict[str, str] | None = None,
    toolset_names: dict[str, str] | None = None,
) -> dict[str, Any]:
    payload = dict(manifest)
    instruction = payload.get("instruction")
    if isinstance(instruction, str) and instruction.strip():
        payload["instruction"] = read_text(app_root, instruction)

    child_agents = payload.get("childAgents")
    if isinstance(child_agents, list):
        payload["childAgents"] = [
            (agent_names or {}).get(child, full_agent_name(project, location, app_id, child))
            for child in child_agents
            if isinstance(child, str) and child
        ]

    tools = payload.get("tools")
    if isinstance(tools, list):
        converted_tools = []
        for tool in tools:
            if not isinstance(tool, str) or not tool:
                continue
            converted_tools.append(
                resolve_tool_name(
                    tool,
                    project=project,
                    location=location,
                    app_id=app_id,
                    tool_names=tool_names,
                )
            )
        payload["tools"] = converted_tools

    toolsets = payload.get("toolsets")
    if isinstance(toolsets, list):
        converted_toolsets = []
        for toolset_entry in toolsets:
            if not isinstance(toolset_entry, dict):
                continue
            entry = dict(toolset_entry)
            toolset_id = entry.get("toolset")
            if isinstance(toolset_id, str) and toolset_id:
                entry["toolset"] = (toolset_names or {}).get(
                    toolset_id,
                    full_toolset_name(project, location, app_id, toolset_id),
                )
            converted_toolsets.append(entry)
        payload["toolsets"] = converted_toolsets

    for field in [
        "beforeAgentCallbacks",
        "beforeModelCallbacks",
        "beforeToolCallbacks",
        "afterAgentCallbacks",
        "afterModelCallbacks",
        "afterToolCallbacks",
    ]:
        if field in payload:
            payload[field] = convert_callbacks(app_root, payload.get(field))

    return payload


def convert_tool_manifest(
    manifest: dict[str, Any],
    app_root: Path,
) -> dict[str, Any]:
    payload: dict[str, Any] = {}

    if "displayName" in manifest:
        payload["displayName"] = manifest["displayName"]

    if "executionType" in manifest:
        payload["executionType"] = manifest["executionType"]

    manifest_python_function = manifest.get("pythonFunction")
    if isinstance(manifest_python_function, dict):
        python_function = {}
        function_name = manifest_python_function.get("name")
        if isinstance(function_name, str) and function_name.strip():
            python_function["name"] = function_name

        python_code_path = manifest_python_function.get("pythonCode")
        if isinstance(python_code_path, str) and python_code_path.strip():
            python_function["pythonCode"] = read_text(app_root, python_code_path)

        description = manifest_python_function.get("description")
        if isinstance(description, str) and description.strip():
            python_function["description"] = description

        if python_function:
            payload["pythonFunction"] = python_function

    for field in [
        "agentTool",
        "clientFunction",
        "connectorTool",
        "dataStoreTool",
        "fileSearchTool",
        "googleSearchTool",
        "openApiTool",
        "widgetTool",
    ]:
        if field in manifest:
            payload[field] = manifest[field]

    return payload


def convert_toolset_manifest(
    manifest: dict[str, Any],
    app_root: Path,
) -> dict[str, Any]:
    payload: dict[str, Any] = {}

    if "displayName" in manifest:
        payload["displayName"] = manifest["displayName"]
    if "description" in manifest:
        payload["description"] = manifest["description"]

    openapi_toolset = manifest.get("openApiToolset")
    if isinstance(openapi_toolset, dict):
        converted_openapi = dict(openapi_toolset)
        schema_path = converted_openapi.get("openApiSchema")
        if isinstance(schema_path, str) and schema_path.strip():
            converted_openapi["openApiSchema"] = read_text(app_root, schema_path)
        payload["openApiToolset"] = converted_openapi

    return payload


def build_request(
    *,
    kind: str,
    source_path: Path,
    project: str,
    location: str,
    app_id: str,
    resource_id: str | None = None,
    endpoint: str | None = None,
    update_mask: str | None = None,
    agent_names: dict[str, str] | None = None,
    tool_names: dict[str, str] | None = None,
    toolset_names: dict[str, str] | None = None,
) -> DeploymentRequest:
    manifest = load_json(source_path)
    app_root = find_app_root(source_path.parent)
    resolved_resource_id = resource_id or source_path.parent.name
    resolved_endpoint = detect_endpoint(location, endpoint)
    app_name = f"projects/{project}/locations/{location}/apps/{app_id}"

    if kind == "agent":
        resource_name = full_agent_name(project, location, app_id, resolved_resource_id)
        payload = convert_agent_manifest(
            manifest,
            app_root,
            project,
            location,
            app_id,
            agent_names=agent_names,
            tool_names=tool_names,
            toolset_names=toolset_names,
        )
        collection_url = f"{resolved_endpoint}/v1/{app_name}/agents"
        resource_url = f"{resolved_endpoint}/v1/{resource_name}"
        create_query = f"agentId={urllib.parse.quote(resolved_resource_id)}"
        resolved_update_mask = update_mask or build_update_mask(payload, AGENT_ALLOWED_UPDATE_FIELDS)
    elif kind == "tool":
        resource_name = full_tool_name(project, location, app_id, resolved_resource_id)
        payload = convert_tool_manifest(manifest, app_root)
        collection_url = f"{resolved_endpoint}/v1/{app_name}/tools"
        resource_url = f"{resolved_endpoint}/v1/{resource_name}"
        create_query = f"toolId={urllib.parse.quote(resolved_resource_id)}"
        resolved_update_mask = update_mask or build_update_mask(payload, TOOL_ALLOWED_UPDATE_FIELDS)
    elif kind == "toolset":
        resource_name = full_toolset_name(project, location, app_id, resolved_resource_id)
        payload = convert_toolset_manifest(manifest, app_root)
        collection_url = f"{resolved_endpoint}/v1/{app_name}/toolsets"
        resource_url = f"{resolved_endpoint}/v1/{resource_name}"
        create_query = f"toolsetId={urllib.parse.quote(resolved_resource_id)}"
        resolved_update_mask = update_mask or build_update_mask(payload, TOOLSET_ALLOWED_UPDATE_FIELDS)
    else:
        raise DeployResourceError(f"Unsupported resource kind: {kind}")

    return DeploymentRequest(
        kind=kind,
        source_path=source_path,
        resource_id=resolved_resource_id,
        collection_url=collection_url,
        resource_url=resource_url,
        create_query=create_query,
        resource_name=resource_name,
        display_name=str(payload.get("displayName", resolved_resource_id)),
        update_mask=resolved_update_mask,
        payload=payload,
    )


def get_access_token() -> str:
    commands = [
        ["gcloud", "auth", "print-access-token"],
        ["gcloud", "auth", "application-default", "print-access-token"],
    ]
    for command in commands:
        try:
            result = subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
        token = result.stdout.strip()
        if token:
            return token
    raise DeployResourceError(
        "Failed to obtain a Google Cloud access token. Run 'gcloud auth login' or "
        "'gcloud auth application-default login' first."
    )


def http_request(
    method: str,
    url: str,
    token: str,
    body: dict[str, Any] | None = None,
) -> tuple[int, str]:
    data = None
    headers = {"Authorization": f"Bearer {token}"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"

    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8")
    except urllib.error.URLError as exc:
        reason = getattr(exc, "reason", exc)
        raise DeployResourceError(
            f"Request to CES API failed for {method} {url}: {reason}"
        ) from exc
    except OSError as exc:
        raise DeployResourceError(
            f"Request to CES API failed for {method} {url}: {exc}"
        ) from exc


def parse_json_body(body: str) -> dict[str, Any]:
    if not body.strip():
        return {}
    try:
        parsed = json.loads(body)
    except json.JSONDecodeError as exc:
        raise DeployResourceError("Expected JSON response from CES API.") from exc
    if not isinstance(parsed, dict):
        raise DeployResourceError("Unexpected non-object JSON response from CES API.")
    return parsed


def collection_items_key(kind: str) -> str:
    return {
        "agent": "agents",
        "tool": "tools",
        "toolset": "toolsets",
    }[kind]


def build_resource_url(collection_url: str, resource_name: str) -> str:
    service_root, _, _ = collection_url.partition("/v1/")
    return f"{service_root}/v1/{resource_name}"


def find_existing_resource_name(
    request: DeploymentRequest,
    token: str,
    preferred_resource_name: str | None = None,
) -> str | None:
    if preferred_resource_name:
        preferred_url = build_resource_url(request.collection_url, preferred_resource_name)
        status, body = http_request("GET", preferred_url, token)
        if status == 200:
            response = parse_json_body(body)
            name = response.get("name")
            return name if isinstance(name, str) and name else preferred_resource_name
        if status != 404:
            raise DeployResourceError(
                f"Failed to determine whether preferred {request.kind} resource exists "
                f"({status}) at {preferred_url}."
            )

    status, body = http_request("GET", request.resource_url, token)
    if status == 200:
        response = parse_json_body(body)
        name = response.get("name")
        return name if isinstance(name, str) and name else request.resource_name
    if status == 404:
        page_token = ""
        while True:
            list_url = request.collection_url
            if page_token:
                list_url = (
                    f"{list_url}?pageToken={urllib.parse.quote(page_token)}"
                )
            list_status, list_body = http_request("GET", list_url, token)
            if list_status != 200:
                raise DeployResourceError(
                    f"Failed to list existing {request.kind} resources ({list_status}) "
                    f"at {request.collection_url}."
                )
            response = parse_json_body(list_body)
            matches = []
            for item in response.get(collection_items_key(request.kind), []):
                if not isinstance(item, dict):
                    continue
                if item.get("displayName") == request.display_name:
                    resource_name = item.get("name")
                    if isinstance(resource_name, str) and resource_name:
                        matches.append(resource_name)
            if len(matches) > 1:
                raise DeployResourceError(
                    f"Found multiple existing {request.kind} resources with displayName "
                    f"{request.display_name!r}; refusing to guess."
                )
            if matches:
                return matches[0]
            next_page_token = response.get("nextPageToken")
            if not isinstance(next_page_token, str) or not next_page_token:
                return None
            page_token = next_page_token
    raise DeployResourceError(
        f"Failed to determine whether resource exists ({status}) at {request.resource_url}."
    )


def apply_request(
    request: DeploymentRequest,
    token: str,
    existing_resource_name: str | None,
) -> tuple[int, str]:
    if existing_resource_name:
        resource_url = build_resource_url(request.collection_url, existing_resource_name)
        url = f"{resource_url}?updateMask={urllib.parse.quote(request.update_mask, safe=',')}"
        return http_request("PATCH", url, token, request.payload)

    url = f"{request.collection_url}?{request.create_query}"
    return http_request("POST", url, token, request.payload)
