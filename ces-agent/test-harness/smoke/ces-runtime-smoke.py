#!/usr/bin/env python3
"""Reusable remote smoke tests for CES runtime resources and HTTP endpoints.

This runner is the canonical smoke-test framework for
`ces-agent/test-harness/smoke`.
It supports two main classes of checks:

- CES runtime checks through `projects.locations.apps.executeTool`
- generic remote HTTP checks for OpenAPI/backend endpoints

Every suite run writes structured debug artifacts so failures are inspectable
without rerunning commands blind.
"""

from __future__ import annotations

import argparse
import ast
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any
from urllib.parse import quote, urlencode, urlparse


ENDPOINTS = {
    "us": "https://ces.us.rep.googleapis.com",
    "eu": "https://ces.eu.rep.googleapis.com",
}

ANSI_RESET = "\033[0m"
ANSI_BOLD = "\033[1m"
ANSI_GREEN = "\033[32m"
ANSI_RED = "\033[31m"
ANSI_CYAN = "\033[36m"
ANSI_YELLOW = "\033[33m"

ENV_PATTERN = re.compile(r"\$\{([A-Z0-9_]+)\}")
DEFAULT_TIMEOUT_SECONDS = 30
DEFAULT_ARTIFACTS_DIRNAME = ".artifacts"
DEFAULT_STATE_FILE_RELATIVE = Path(".tmp/cloud-run/discovery-plan.env")
DEFAULT_GCP_PROJECT_ID = "voice-banking-poc"
DEFAULT_GCP_LOCATION = "us"
DEFAULT_CES_APP_ID = "acme-voice-us"
DEFAULT_CUSTOMER_DETAILS_API_KEY = "YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ"
ENV_ASSIGNMENT_PATTERN = re.compile(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$")
AUTO_ENV_LOADED = False
SERVERLESS_AUTH_HEADER = "X-Serverless-Authorization"
METADATA_IDENTITY_URL = (
    "http://metadata.google.internal/computeMetadata/v1/instance/"
    "service-accounts/default/identity"
)


class SmokeError(RuntimeError):
    """Raised when the smoke test setup or API contract is invalid."""


def repo_root() -> Path:
    return framework_root().parents[2]


def default_root_env_path() -> Path:
    return repo_root() / ".env"


def default_state_env_path() -> Path:
    return repo_root() / DEFAULT_STATE_FILE_RELATIVE


def parse_env_file_line(raw_line: str) -> tuple[str, str] | None:
    stripped = raw_line.strip()
    if not stripped or stripped.startswith("#"):
        return None

    match = ENV_ASSIGNMENT_PATTERN.match(raw_line)
    if not match:
        return None

    key, raw_value = match.groups()
    value = raw_value.strip()

    if value and value[0] in {"'", '"'}:
        quote_char = value[0]
        closing_index = value.find(quote_char, 1)
        while closing_index != -1 and value[closing_index - 1] == "\\":
            closing_index = value.find(quote_char, closing_index + 1)
        if closing_index != -1:
            quoted_value = value[: closing_index + 1]
            trailing = value[closing_index + 1 :].strip()
            if not trailing or trailing.startswith("#"):
                try:
                    parsed = ast.literal_eval(quoted_value)
                except (SyntaxError, ValueError):
                    parsed = quoted_value[1:-1]
                return key, str(parsed)

    inline_comment = value.find(" #")
    if inline_comment != -1:
        value = value[:inline_comment].rstrip()

    return key, value


def load_env_file(path: Path) -> None:
    if not path.is_file():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        parsed = parse_env_file_line(raw_line)
        if parsed is None:
            continue
        key, value = parsed
        os.environ[key] = value


def sync_gcp_env_aliases() -> None:
    alias_pairs = [
        ("GCP_PROJECT_ID", "PROJECT_ID"),
        ("GCP_REGION", "REGION"),
        ("GCP_ZONE", "ZONE"),
        ("GCP_LOCATION", "LOCATION"),
        ("GCP_SERVICE_ACCOUNT_KEY", "GOOGLE_APPLICATION_CREDENTIALS"),
        ("GCP_PROJECT_ID", "GOOGLE_CLOUD_PROJECT"),
    ]
    for primary, alias in alias_pairs:
        primary_value = os.environ.get(primary, "")
        alias_value = os.environ.get(alias, "")
        if primary_value and not alias_value:
            os.environ[alias] = primary_value
        elif alias_value and not primary_value:
            os.environ[primary] = alias_value

    os.environ.setdefault("GCP_CONTAINER_REGISTRY_HOST", "gcr.io")


def apply_default_smoke_env() -> None:
    os.environ.setdefault("GCP_PROJECT_ID", DEFAULT_GCP_PROJECT_ID)
    os.environ.setdefault("GCP_LOCATION", DEFAULT_GCP_LOCATION)
    os.environ.setdefault("CES_APP_ID", DEFAULT_CES_APP_ID)
    os.environ.setdefault("CUSTOMER_DETAILS_API_KEY", DEFAULT_CUSTOMER_DETAILS_API_KEY)


def ensure_default_env_loaded() -> None:
    global AUTO_ENV_LOADED
    if AUTO_ENV_LOADED:
        apply_default_smoke_env()
        sync_gcp_env_aliases()
        return

    load_env_file(default_root_env_path())
    load_env_file(default_state_env_path())
    apply_default_smoke_env()
    sync_gcp_env_aliases()
    AUTO_ENV_LOADED = True


def supports_color(stream: Any | None = None) -> bool:
    target = stream or sys.stdout
    if os.environ.get("NO_COLOR"):
        return False
    isatty = getattr(target, "isatty", None)
    return bool(callable(isatty) and isatty())


def colorize(text: str, color: str, *, bold: bool = False) -> str:
    if not supports_color():
        return text
    prefix = f"{ANSI_BOLD if bold else ''}{color}"
    return f"{prefix}{text}{ANSI_RESET}"


def suite_display_name(suite_path: Path) -> str:
    return suite_path.name


def suite_qualified_test_name(suite_name: str, test_name: str) -> str:
    return f"{test_name} [{suite_name}]"


def print_suite_banner(suite_name: str, suite_path: Path, artifacts_root: Path) -> None:
    banner_width = max(72, len(suite_name) + 28)
    banner = "=" * banner_width
    print(colorize(banner, ANSI_CYAN, bold=True))
    print(
        f"{colorize('Running smoke suite:', ANSI_CYAN, bold=True)} "
        f"{colorize(suite_name, ANSI_YELLOW, bold=True)}"
    )
    print(f"Suite path: {suite_path}")
    print(f"Artifacts: {artifacts_root}")
    print(colorize(banner, ANSI_CYAN, bold=True))
    print("")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run reusable smoke tests against deployed CES apps and remote endpoints."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    list_parser = subparsers.add_parser(
        "list-resources",
        help="List deployed CES resources in an app.",
    )
    add_common_app_args(list_parser)
    list_parser.add_argument(
        "--kind",
        choices=["agents", "tools", "toolsets"],
        default="tools",
        help="Resource collection to list.",
    )

    execute_tool_parser = subparsers.add_parser(
        "execute-tool",
        help="Execute a deployed direct tool by display name.",
    )
    add_common_app_args(execute_tool_parser)
    execute_tool_parser.add_argument("--tool-display-name", required=True)
    execute_tool_parser.add_argument(
        "--arg",
        action="append",
        default=[],
        help="Tool argument in key=value form. May be passed multiple times.",
    )
    execute_tool_parser.add_argument(
        "--variable",
        action="append",
        default=[],
        help="Execution variable in key=value form. May be passed multiple times.",
    )

    execute_toolset_parser = subparsers.add_parser(
        "execute-toolset",
        help="Execute a deployed OpenAPI toolset operation through apps:executeTool.",
    )
    add_common_app_args(execute_toolset_parser)
    execute_toolset_parser.add_argument("--toolset-display-name", required=True)
    execute_toolset_parser.add_argument("--tool-id", required=True)
    execute_toolset_parser.add_argument(
        "--arg",
        action="append",
        default=[],
        help="Toolset tool argument in key=value form. May be passed multiple times.",
    )
    execute_toolset_parser.add_argument(
        "--variable",
        action="append",
        default=[],
        help="Execution variable in key=value form. May be passed multiple times.",
    )

    http_parser = subparsers.add_parser(
        "http-request",
        help="Execute a generic remote HTTP request smoke check.",
    )
    add_http_request_args(http_parser)

    suite_parser = subparsers.add_parser(
        "run-suite",
        help="Run a JSON-defined smoke suite.",
    )
    suite_parser.add_argument("--suite", required=True, help="Path to suite JSON file.")
    suite_parser.add_argument(
        "--fail-fast",
        action="store_true",
        help="Stop on the first failed test.",
    )
    suite_parser.add_argument(
        "--artifacts-dir",
        help=(
            "Optional artifact directory. Defaults to "
            "test-harness/smoke/.artifacts/<timestamp>/"
        ),
    )

    return parser.parse_args()


def add_common_app_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--project", required=True)
    parser.add_argument("--location", required=True)
    parser.add_argument("--app-id", required=True)
    parser.add_argument(
        "--endpoint",
        help="Override CES API endpoint. Defaults by location.",
    )


def add_http_request_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--method", default="GET", help="HTTP method. Default: GET")
    parser.add_argument("--url", required=True)
    parser.add_argument(
        "--header",
        action="append",
        default=[],
        help="HTTP header in key=value form. May be passed multiple times.",
    )
    parser.add_argument(
        "--json-body",
        help="JSON string to send as the request body.",
    )
    parser.add_argument(
        "--body",
        help="Raw string body to send.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=DEFAULT_TIMEOUT_SECONDS,
        help=f"HTTP timeout in seconds. Default: {DEFAULT_TIMEOUT_SECONDS}",
    )
    parser.add_argument(
        "--expected-status",
        type=int,
        help="Optional expected HTTP status. If set, mismatches exit non-zero.",
    )


def detect_endpoint(location: str, explicit: str | None) -> str:
    if explicit:
        return explicit.rstrip("/")
    return ENDPOINTS.get(location, "https://ces.googleapis.com")


def get_access_token() -> str:
    commands = [
        ["gcloud", "auth", "print-access-token"],
        ["gcloud", "auth", "application-default", "print-access-token"],
    ]
    for cmd in commands:
        try:
            result = subprocess.run(
                cmd,
                check=True,
                capture_output=True,
                text=True,
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            continue
        token = result.stdout.strip()
        if token:
            return token
    raise SmokeError(
        "Failed to obtain a Google Cloud access token. Run 'gcloud auth login' "
        "or 'gcloud auth application-default login' first."
    )


def parse_key_value_args(items: list[str]) -> dict[str, Any]:
    parsed: dict[str, Any] = {}
    for item in items:
        if "=" not in item:
            raise SmokeError(f"Expected key=value argument, got: {item}")
        key, value = item.split("=", 1)
        parsed[key] = coerce_cli_value(value)
    return parsed


def coerce_cli_value(value: str) -> Any:
    stripped = value.strip()
    if stripped.lower() == "true":
        return True
    if stripped.lower() == "false":
        return False
    if stripped.lower() == "null":
        return None
    try:
        return json.loads(stripped)
    except json.JSONDecodeError:
        return value


def expand_env_placeholders(value: Any) -> Any:
    if isinstance(value, str):
        def replacer(match: re.Match[str]) -> str:
            key = match.group(1)
            if key not in os.environ:
                raise SmokeError(f"Missing required environment variable: {key}")
            return os.environ[key]

        return ENV_PATTERN.sub(replacer, value)
    if isinstance(value, list):
        return [expand_env_placeholders(item) for item in value]
    if isinstance(value, dict):
        return {key: expand_env_placeholders(item) for key, item in value.items()}
    return value


def app_parent(project: str, location: str, app_id: str) -> str:
    return f"projects/{project}/locations/{location}/apps/{app_id}"


def collection_url(endpoint: str, project: str, location: str, app_id: str, kind: str) -> str:
    return f"{endpoint}/v1/{app_parent(project, location, app_id)}/{kind}"


def maybe_parse_json(text: str) -> Any | None:
    if not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def load_json_document(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SmokeError(f"JSON document not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise SmokeError(f"Invalid JSON document at {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise SmokeError(f"Expected JSON object at {path}, got {type(payload).__name__}.")
    return payload


def resolve_path(base_dir: Path, value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    return (base_dir / path).resolve()


def expand_with_context(value: Any, context: str) -> Any:
    try:
        return expand_env_placeholders(value)
    except SmokeError as exc:
        raise SmokeError(f"{context}: {exc}") from exc


def require_env_var(name: str, *, context: str) -> str:
    value = os.environ.get(name, "")
    if not value:
        raise SmokeError(f"{context} requires environment variable '{name}' but it is not set.")
    return value


def cloud_run_audience(url: str) -> str | None:
    parsed = urlparse(url)
    if not parsed.hostname or not parsed.hostname.endswith(".run.app"):
        return None
    scheme = parsed.scheme or "https"
    return f"{scheme}://{parsed.hostname}"


@lru_cache(maxsize=32)
def get_cloud_run_identity_token(audience: str) -> str:
    static_token = os.environ.get("CLOUD_RUN_ID_TOKEN", "").strip()
    if static_token:
        return static_token

    metadata_url = (
        f"{METADATA_IDENTITY_URL}?{urlencode({'audience': audience, 'format': 'full'})}"
    )
    metadata_request = urllib.request.Request(
        metadata_url,
        headers={"Metadata-Flavor": "Google"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(metadata_request, timeout=5) as response:
            token = response.read().decode("utf-8").strip()
            if token:
                return token
    except Exception:
        pass

    commands = [
        ["gcloud", "auth", "print-identity-token", f"--audiences={audience}"],
        [
            "gcloud",
            "auth",
            "application-default",
            "print-identity-token",
            f"--audiences={audience}",
        ],
        ["gcloud", "auth", "print-identity-token"],
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

    raise SmokeError(
        "Unable to obtain a Cloud Run identity token. Set CLOUD_RUN_ID_TOKEN, "
        "run on Google Cloud with metadata server access, or authenticate with gcloud."
    )


def with_cloud_run_auth(url: str, headers: dict[str, str]) -> dict[str, str]:
    audience = cloud_run_audience(url)
    if not audience or SERVERLESS_AUTH_HEADER in headers:
        return headers

    return {
        **headers,
        SERVERLESS_AUTH_HEADER: f"Bearer {get_cloud_run_identity_token(audience)}",
    }


def http_request_raw(
    method: str,
    url: str,
    *,
    headers: dict[str, Any] | None = None,
    token: str | None = None,
    json_body: Any | None = None,
    body: str | None = None,
    timeout_seconds: int = DEFAULT_TIMEOUT_SECONDS,
) -> dict[str, Any]:
    if json_body is not None and body is not None:
        raise SmokeError("Provide either json_body or body, not both.")

    request_headers = {"Accept": "application/json"}
    if headers:
        request_headers.update({key: str(value) for key, value in headers.items()})
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    request_headers = with_cloud_run_auth(url, request_headers)

    data = None
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        request_headers.setdefault("Content-Type", "application/json")
    elif body is not None:
        data = body.encode("utf-8")

    request = urllib.request.Request(url, data=data, method=method.upper(), headers=request_headers)

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            raw_body = response.read().decode("utf-8")
            return {
                "method": method.upper(),
                "url": url,
                "status": response.status,
                "headers": dict(response.headers.items()),
                "body": raw_body,
                "json": maybe_parse_json(raw_body),
            }
    except urllib.error.HTTPError as exc:
        raw_body = exc.read().decode("utf-8") if exc.fp else ""
        return {
            "method": method.upper(),
            "url": url,
            "status": exc.code,
            "headers": dict(exc.headers.items()) if exc.headers else {},
            "body": raw_body,
            "json": maybe_parse_json(raw_body),
        }
    except urllib.error.URLError as exc:
        raise SmokeError(f"Network error calling {url}: {exc.reason}") from exc


def http_json(
    method: str,
    url: str,
    token: str | None = None,
    body: dict[str, Any] | None = None,
) -> dict[str, Any]:
    response = http_request_raw(method, url, token=token, json_body=body)
    status = response["status"]
    if not 200 <= status < 300:
        parsed = response.get("json")
        message = response.get("body", "")[:300]
        if isinstance(parsed, dict):
            message = parsed.get("error", {}).get("message", message)
        if status == 429:
            message = (
                f"{message} "
                "This usually indicates CES or model quota exhaustion. "
                "Try a single targeted command first (for example list-resources or execute-tool), "
                "wait briefly before rerunning the full suite, and verify project/location quotas in Google Cloud."
            )
        raise SmokeError(f"HTTP {status} from {url}: {message}")
    if isinstance(response.get("json"), dict):
        return response["json"]
    raise SmokeError(f"Expected JSON response from {url}, got non-JSON body.")


@lru_cache(maxsize=64)
def _list_resources_cached(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    kind: str,
) -> tuple[dict[str, Any], ...]:
    payload = http_json("GET", collection_url(endpoint, project, location, app_id, kind), token)
    resources = payload.get(kind, [])
    if not isinstance(resources, list):
        raise SmokeError(
            f"Expected '{kind}' collection in CES response for app '{app_id}', got {type(resources).__name__}."
        )
    return tuple(resources)


def list_resources(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    kind: str,
) -> list[dict[str, Any]]:
    return list(_list_resources_cached(endpoint, token, project, location, app_id, kind))


def normalize_service_definition(
    alias: str,
    raw_service: dict[str, Any],
    *,
    base_dir: Path,
    source_path: Path,
) -> dict[str, Any]:
    base_url_env = raw_service.get("base_url_env")
    if not isinstance(base_url_env, str) or not base_url_env:
        raise SmokeError(
            f"Service '{alias}' in {source_path} must define a non-empty 'base_url_env'."
        )

    openapi_spec = raw_service.get("openapi_spec")
    if not isinstance(openapi_spec, str) or not openapi_spec:
        raise SmokeError(
            f"Service '{alias}' in {source_path} must define a non-empty 'openapi_spec'."
        )

    openapi_spec_path = resolve_path(base_dir, openapi_spec)
    if not openapi_spec_path.is_file():
        raise SmokeError(
            f"Service '{alias}' in {source_path} references a missing OpenAPI spec: "
            f"{openapi_spec_path}"
        )

    default_headers = raw_service.get("default_headers", {}) or {}
    if not isinstance(default_headers, dict):
        raise SmokeError(
            f"Service '{alias}' in {source_path} must define 'default_headers' as an object."
        )

    required_env = raw_service.get("required_env", []) or []
    if not isinstance(required_env, list) or not all(
        isinstance(item, str) and item for item in required_env
    ):
        raise SmokeError(
            f"Service '{alias}' in {source_path} must define 'required_env' as a list of "
            "non-empty strings."
        )

    return {
        "alias": alias,
        "display_name": raw_service.get("display_name", alias),
        "base_url_env": base_url_env,
        "required_env": required_env,
        "openapi_spec_path": str(openapi_spec_path),
        "default_headers": default_headers,
        "source_path": str(source_path),
    }


def load_service_definitions(suite_path: Path, suite: dict[str, Any]) -> dict[str, dict[str, Any]]:
    inline_services = suite.get("services")
    services_file = suite.get("services_file")
    if inline_services and services_file:
        raise SmokeError(
            f"Suite {suite_path} cannot define both 'services' and 'services_file'."
        )
    if not inline_services and not services_file:
        return {}

    if services_file:
        catalog_path = resolve_path(suite_path.parent, str(services_file))
        catalog = load_json_document(catalog_path)
        raw_services = catalog.get("services")
        if not isinstance(raw_services, dict):
            raise SmokeError(
                f"Service catalog {catalog_path} must contain a top-level 'services' object."
            )
        base_dir = catalog_path.parent
        source_path = catalog_path
    else:
        if not isinstance(inline_services, dict):
            raise SmokeError(
                f"Suite {suite_path} must define 'services' as an object when used inline."
            )
        raw_services = inline_services
        base_dir = suite_path.parent
        source_path = suite_path

    return {
        alias: normalize_service_definition(
            alias,
            raw_service,
            base_dir=base_dir,
            source_path=source_path,
        )
        for alias, raw_service in raw_services.items()
    }


@lru_cache(maxsize=None)
def load_openapi_spec(path_str: str) -> dict[str, Any]:
    return load_json_document(Path(path_str))


def resolve_openapi_operation(
    service_alias: str,
    service_definition: dict[str, Any],
    operation_id: str,
) -> tuple[dict[str, Any], str, str, dict[str, Any]]:
    spec = load_openapi_spec(service_definition["openapi_spec_path"])
    matches: list[tuple[str, str, dict[str, Any]]] = []
    for path, methods in spec.get("paths", {}).items():
        if not isinstance(methods, dict):
            continue
        for method, operation in methods.items():
            if isinstance(operation, dict) and operation.get("operationId") == operation_id:
                matches.append((path, method.upper(), operation))

    if not matches:
        raise SmokeError(
            f"Service '{service_alias}' contract {service_definition['openapi_spec_path']} "
            f"does not contain operationId '{operation_id}'."
        )
    if len(matches) > 1:
        raise SmokeError(
            f"Service '{service_alias}' contract {service_definition['openapi_spec_path']} "
            f"contains multiple operations with operationId '{operation_id}'."
        )
    return matches[0][2], matches[0][0], matches[0][1], spec


def resolve_openapi_service(
    service_definitions: dict[str, dict[str, Any]],
    service_alias: str,
) -> dict[str, Any]:
    if service_alias not in service_definitions:
        known = ", ".join(sorted(service_definitions)) or "(none)"
        raise SmokeError(f"Unknown service '{service_alias}'. Known services: {known}")

    definition = dict(service_definitions[service_alias])
    context = f"Service '{service_alias}'"
    definition["base_url"] = require_env_var(definition["base_url_env"], context=context)
    for env_name in definition.get("required_env", []):
        require_env_var(env_name, context=context)
    definition["default_headers"] = expand_with_context(
        definition.get("default_headers", {}),
        context,
    )
    return definition


def render_path_template(path_template: str, path_params: dict[str, Any]) -> str:
    def replace(match: re.Match[str]) -> str:
        key = match.group(1)
        if key not in path_params:
            raise SmokeError(
                f"Missing required path parameter '{key}' for path template '{path_template}'."
            )
        return quote(str(path_params[key]), safe="")

    rendered = re.sub(r"\{([^{}]+)\}", replace, path_template)
    if "{" in rendered or "}" in rendered:
        raise SmokeError(f"Unresolved path template after parameter rendering: {rendered}")
    return rendered


def build_openapi_request(
    service_alias: str,
    service_definition: dict[str, Any],
    operation_id: str,
    test: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    request_block = test.get("request", {}) or {}
    if not isinstance(request_block, dict):
        raise SmokeError(
            f"Test '{test.get('name', service_alias)}' must define 'request' as an object."
        )

    operation, path_template, method, spec = resolve_openapi_operation(
        service_alias,
        service_definition,
        operation_id,
    )

    path_params = expand_with_context(
        request_block.get("path_params", {}) or {},
        f"Service '{service_alias}' path parameters",
    )
    if not isinstance(path_params, dict):
        raise SmokeError(f"Service '{service_alias}' path parameters must be an object.")

    query_params = expand_with_context(
        request_block.get("query_params", {}) or {},
        f"Service '{service_alias}' query parameters",
    )
    if not isinstance(query_params, dict):
        raise SmokeError(f"Service '{service_alias}' query parameters must be an object.")

    headers = dict(service_definition.get("default_headers", {}))
    request_headers = expand_with_context(
        request_block.get("headers", {}) or {},
        f"Service '{service_alias}' request headers",
    )
    if not isinstance(request_headers, dict):
        raise SmokeError(f"Service '{service_alias}' request headers must be an object.")
    headers.update(request_headers)

    rendered_path = render_path_template(path_template, path_params)
    url = f"{service_definition['base_url'].rstrip('/')}{rendered_path}"
    if query_params:
        url = f"{url}?{urlencode(query_params, doseq=True)}"

    json_body = request_block.get("json_body")
    if json_body is not None:
        json_body = expand_with_context(
            json_body,
            f"Service '{service_alias}' JSON body",
        )

    body = request_block.get("body")
    if body is not None:
        body = expand_with_context(
            body,
            f"Service '{service_alias}' request body",
        )

    timeout_seconds = request_block.get("timeout_seconds", DEFAULT_TIMEOUT_SECONDS)
    request_meta = {
        "type": "openapi_operation",
        "service": service_alias,
        "service_display_name": service_definition["display_name"],
        "service_source": service_definition["source_path"],
        "contract_title": spec.get("info", {}).get("title", service_definition["openapi_spec_path"]),
        "contract_path": service_definition["openapi_spec_path"],
        "base_url_env": service_definition["base_url_env"],
        "base_url": service_definition["base_url"],
        "required_env": service_definition.get("required_env", []),
        "operation_id": operation_id,
        "operation_summary": operation.get("summary", ""),
        "operation_method": method,
        "operation_path": path_template,
        "url": url,
        "path_params": path_params,
        "query_params": query_params,
        "headers": headers,
        "json_body": json_body,
        "body": body,
        "timeout_seconds": timeout_seconds,
    }
    response = http_request_raw(
        method,
        url,
        headers=headers,
        json_body=json_body,
        body=body,
        timeout_seconds=timeout_seconds,
    )
    return request_meta, response


def resolve_resource_by_display_name(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    kind: str,
    display_name: str,
) -> dict[str, Any]:
    matches = [
        item
        for item in list_resources(endpoint, token, project, location, app_id, kind)
        if item.get("displayName") == display_name
    ]
    if not matches:
        raise SmokeError(
            f"No {kind[:-1]} with displayName '{display_name}' found in app '{app_id}'."
        )
    if len(matches) > 1:
        raise SmokeError(
            f"Multiple {kind} with displayName '{display_name}' found in app '{app_id}'."
        )
    return matches[0]


def execute_tool(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    tool_name: str,
    args: dict[str, Any],
    variables: dict[str, Any] | None = None,
) -> dict[str, Any]:
    url = f"{endpoint}/v1/{app_parent(project, location, app_id)}:executeTool"
    payload: dict[str, Any] = {"tool": tool_name, "args": args}
    if variables:
        payload["variables"] = variables
    return http_json("POST", url, token, payload)


def execute_toolset_tool(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    toolset_name: str,
    tool_id: str,
    args: dict[str, Any],
    variables: dict[str, Any] | None = None,
) -> dict[str, Any]:
    url = f"{endpoint}/v1/{app_parent(project, location, app_id)}:executeTool"
    payload: dict[str, Any] = {
        "toolsetTool": {
            "toolset": toolset_name,
            "toolId": tool_id,
        },
        "args": args,
    }
    if variables:
        payload["variables"] = variables
    return http_json("POST", url, token, payload)


def get_value_at_path(payload: Any, path: str) -> Any:
    current = payload
    for part in path.split("."):
        if isinstance(current, list):
            try:
                current = current[int(part)]
            except (ValueError, IndexError) as exc:
                raise SmokeError(f"Invalid list path segment '{part}' in '{path}'.") from exc
            continue
        if not isinstance(current, dict) or part not in current:
            raise SmokeError(f"Path '{path}' not found in response.")
        current = current[part]
    return current


def evaluate_expectations(response: dict[str, Any], expect: dict[str, Any] | None) -> list[str]:
    if not expect:
        return []

    failures: list[str] = []
    for path in expect.get("exists", []):
        try:
            get_value_at_path(response, path)
        except SmokeError as exc:
            failures.append(str(exc))

    for path, expected in expect.get("equals", {}).items():
        try:
            actual = get_value_at_path(response, path)
        except SmokeError as exc:
            failures.append(str(exc))
            continue
        if actual != expected:
            failures.append(f"Path '{path}' expected {expected!r} but got {actual!r}.")

    for path, substring in expect.get("contains", {}).items():
        try:
            actual = get_value_at_path(response, path)
        except SmokeError as exc:
            failures.append(str(exc))
            continue
        if substring not in str(actual):
            failures.append(
                f"Path '{path}' expected to contain {substring!r} but got {actual!r}."
            )

    return failures


def merge_expectations(test: dict[str, Any]) -> dict[str, Any] | None:
    expect = dict(test.get("expect", {}) or {})
    if "expected_status" in test:
        equals = dict(expect.get("equals", {}) or {})
        equals.setdefault("status", test["expected_status"])
        expect["equals"] = equals
    return expect or None


def evaluate_toolset_contract(toolset: dict[str, Any], test: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    schema = toolset.get("openApiToolset", {}).get("openApiSchema") or ""
    for operation_id in test.get("expect_operation_ids", []):
        if operation_id not in schema:
            failures.append(f"Toolset schema missing expected operationId '{operation_id}'.")
    for snippet in test.get("expect_schema_contains", []):
        if snippet not in schema:
            failures.append(f"Toolset schema missing expected snippet {snippet!r}.")
    return failures


def slugify(value: str) -> str:
    slug = re.sub(r"[^a-zA-Z0-9._-]+", "-", value.strip().lower()).strip("-")
    return slug or "smoke-test"


def framework_root() -> Path:
    return Path(__file__).resolve().parent


def default_artifacts_root(base_dir: Path) -> Path:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return base_dir / DEFAULT_ARTIFACTS_DIRNAME / timestamp


def preview_response(response: Any) -> str:
    if response is None:
        return "<no response captured>"
    text = json.dumps(response, indent=2, ensure_ascii=False)[:1200]
    return text


def write_artifact(artifacts_dir: Path, filename: str, payload: dict[str, Any]) -> Path:
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    artifact_path = artifacts_dir / filename
    artifact_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return artifact_path


def execute_suite_test(
    *,
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
    service_definitions: dict[str, dict[str, Any]],
    test: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any] | None]:
    test_type = test.get("type")

    if test_type == "resource_exists":
        resource = resolve_resource_by_display_name(
            endpoint, token, project, location, app_id, test["kind"], test["display_name"]
        )
        request_meta = {
            "type": test_type,
            "kind": test["kind"],
            "display_name": test["display_name"],
        }
        return request_meta, {"resource": resource}

    if test_type == "toolset_schema":
        toolset = resolve_resource_by_display_name(
            endpoint, token, project, location, app_id, "toolsets", test["toolset_display_name"]
        )
        request_meta = {
            "type": test_type,
            "toolset_display_name": test["toolset_display_name"],
        }
        return request_meta, {"toolset": toolset}

    if test_type == "execute_tool":
        tool = resolve_resource_by_display_name(
            endpoint, token, project, location, app_id, "tools", test["tool_display_name"]
        )
        request_meta = {
            "type": test_type,
            "tool_display_name": test["tool_display_name"],
            "resolved_tool": tool["name"],
            "args": test.get("args", {}),
            "variables": test.get("variables", {}),
        }
        response = execute_tool(
            endpoint,
            token,
            project,
            location,
            app_id,
            tool["name"],
            test.get("args", {}),
            test.get("variables"),
        )
        return request_meta, response

    if test_type == "execute_toolset_tool":
        toolset = resolve_resource_by_display_name(
            endpoint, token, project, location, app_id, "toolsets", test["toolset_display_name"]
        )
        request_meta = {
            "type": test_type,
            "toolset_display_name": test["toolset_display_name"],
            "resolved_toolset": toolset["name"],
            "tool_id": test["tool_id"],
            "args": test.get("args", {}),
            "variables": test.get("variables", {}),
        }
        response = execute_toolset_tool(
            endpoint,
            token,
            project,
            location,
            app_id,
            toolset["name"],
            test["tool_id"],
            test.get("args", {}),
            test.get("variables"),
        )
        return request_meta, response

    if test_type == "http_request":
        request_meta = {
            "type": test_type,
            "method": test.get("method", "GET"),
            "url": test["url"],
            "headers": test.get("headers", {}),
            "json_body": test.get("json_body"),
            "body": test.get("body"),
            "timeout_seconds": test.get("timeout_seconds", DEFAULT_TIMEOUT_SECONDS),
        }
        response = http_request_raw(
            request_meta["method"],
            request_meta["url"],
            headers=request_meta["headers"],
            json_body=request_meta["json_body"],
            body=request_meta["body"],
            timeout_seconds=request_meta["timeout_seconds"],
        )
        return request_meta, response

    if test_type == "openapi_operation":
        service_alias = test.get("service")
        operation_id = test.get("operation_id")
        if not isinstance(service_alias, str) or not service_alias:
            raise SmokeError("openapi_operation tests must define a non-empty 'service'.")
        if not isinstance(operation_id, str) or not operation_id:
            raise SmokeError("openapi_operation tests must define a non-empty 'operation_id'.")
        service_definition = resolve_openapi_service(service_definitions, service_alias)
        return build_openapi_request(service_alias, service_definition, operation_id, test)

    raise SmokeError(f"Unsupported test type: {test_type}")


def run_suite(suite_path: Path, fail_fast: bool, artifacts_dir: Path | None = None) -> int:
    ensure_default_env_loaded()
    suite = expand_env_placeholders(load_json_document(suite_path))
    suite_name = suite_display_name(suite_path)
    project = suite.get("project")
    location = suite.get("location")
    app_id = suite.get("app_id")
    endpoint = detect_endpoint(location, suite.get("endpoint")) if location else None
    token = get_access_token() if location and app_id and project else None
    service_definitions = load_service_definitions(suite_path, suite)

    tests: list[dict[str, Any]] = suite.get("tests", [])
    if not tests:
        raise SmokeError(f"Suite {suite_path} does not contain any tests.")

    artifacts_root = artifacts_dir or default_artifacts_root(framework_root())
    summary: dict[str, Any] = {
        "suite_name": suite_name,
        "suite": str(suite_path),
        "started_at": datetime.now(timezone.utc).isoformat(),
        "artifacts_dir": str(artifacts_root),
        "results": [],
    }

    failures = 0
    print_suite_banner(suite_name, suite_path, artifacts_root)

    for index, raw_test in enumerate(tests, start=1):
        test = expand_env_placeholders(raw_test)
        name = test.get("name", f"test-{index}")
        display_name = suite_qualified_test_name(suite_name, name)
        test_type = test.get("type")
        dsl_ref = ""
        if test_type == "openapi_operation":
            service_alias = test.get("service", "")
            operation_id = test.get("operation_id", "")
            if service_alias and operation_id:
                dsl_ref = f" [{service_alias}::{operation_id}]"
        request_meta: dict[str, Any] | None = None
        response_payload: dict[str, Any] | None = None
        error_message: str | None = None
        passed = False

        print(f"[{index}/{len(tests)}] {display_name} ({test_type}){dsl_ref}")
        try:
            request_meta, response_payload = execute_suite_test(
                endpoint=endpoint or "",
                token=token or "",
                project=project or "",
                location=location or "",
                app_id=app_id or "",
                service_definitions=service_definitions,
                test=test,
            )

            if test_type == "toolset_schema":
                contract_failures = evaluate_toolset_contract(
                    response_payload["toolset"],
                    test,
                )
                if contract_failures:
                    raise SmokeError("; ".join(contract_failures))
            else:
                expectation_failures = evaluate_expectations(
                    response_payload or {},
                    merge_expectations(test),
                )
                if expectation_failures:
                    raise SmokeError("; ".join(expectation_failures))

            passed = True
            success_ref = ""
            if request_meta:
                success_ref = request_meta.get("resolved_tool") or request_meta.get("resolved_toolset") or ""
            if not success_ref and response_payload and "resource" in response_payload:
                success_ref = response_payload["resource"].get("name", "")
            if not success_ref and response_payload and "toolset" in response_payload:
                success_ref = response_payload["toolset"].get("name", "")
            if test_type == "execute_toolset_tool" and request_meta:
                success_ref = f"{success_ref}::{request_meta['tool_id']}"
            if test_type == "openapi_operation" and request_meta:
                success_ref = (
                    f"{request_meta['service']}::{request_meta['operation_id']} -> "
                    f"{request_meta['url']}"
                )
            print(f"  {colorize('PASS', ANSI_GREEN, bold=True)}  {success_ref}")
        except SmokeError as exc:
            failures += 1
            error_message = str(exc)
            print(f"  {colorize('FAIL', ANSI_RED, bold=True)}  {error_message}")
            if response_payload is not None:
                print("  Response preview:")
                for line in preview_response(response_payload).splitlines():
                    print(f"    {line}")

        artifact_payload = {
            "index": index,
            "name": name,
            "display_name": display_name,
            "suite_name": suite_name,
            "type": test_type,
            "passed": passed,
            "error": error_message,
            "request": request_meta,
            "response": response_payload,
        }
        artifact_name = f"{index:02d}-{slugify(name)}.json"
        artifact_path = write_artifact(artifacts_root, artifact_name, artifact_payload)
        if not passed:
            print(f"  Artifact: {artifact_path}")

        summary["results"].append(
            {
                "index": index,
                "name": name,
                "display_name": display_name,
                "type": test_type,
                "passed": passed,
                "service": request_meta.get("service") if request_meta else test.get("service"),
                "operation_id": request_meta.get("operation_id") if request_meta else test.get("operation_id"),
                "artifact": str(artifact_path),
            }
        )

        if failures and fail_fast:
            break

    summary["finished_at"] = datetime.now(timezone.utc).isoformat()
    summary["failed"] = failures
    summary["passed"] = len(summary["results"]) - failures
    summary_path = write_artifact(artifacts_root, "summary.json", summary)

    print("")
    print(f"Summary artifact: {summary_path}")
    if failures:
        print(
            f"{colorize('SUITE FAILED', ANSI_RED, bold=True)}: "
            f"{suite_name} - {failures} test(s) failed."
        )
        return 1

    print(
        f"{colorize('SUITE PASSED', ANSI_GREEN, bold=True)}: "
        f"{suite_name} - {len(summary['results'])} test(s) passed."
    )
    return 0


def cmd_list_resources(args: argparse.Namespace) -> int:
    endpoint = detect_endpoint(args.location, args.endpoint)
    token = get_access_token()
    resources = list_resources(endpoint, token, args.project, args.location, args.app_id, args.kind)
    for item in resources:
        print(f"{item.get('displayName', '')}\t{item.get('name', '')}")
    return 0


def cmd_execute_tool(args: argparse.Namespace) -> int:
    endpoint = detect_endpoint(args.location, args.endpoint)
    token = get_access_token()
    tool = resolve_resource_by_display_name(
        endpoint, token, args.project, args.location, args.app_id, "tools", args.tool_display_name
    )
    response = execute_tool(
        endpoint,
        token,
        args.project,
        args.location,
        args.app_id,
        tool["name"],
        parse_key_value_args(args.arg),
        parse_key_value_args(args.variable),
    )
    print(json.dumps(response, indent=2, ensure_ascii=False))
    return 0


def cmd_execute_toolset(args: argparse.Namespace) -> int:
    endpoint = detect_endpoint(args.location, args.endpoint)
    token = get_access_token()
    toolset = resolve_resource_by_display_name(
        endpoint, token, args.project, args.location, args.app_id, "toolsets", args.toolset_display_name
    )
    response = execute_toolset_tool(
        endpoint,
        token,
        args.project,
        args.location,
        args.app_id,
        toolset["name"],
        args.tool_id,
        parse_key_value_args(args.arg),
        parse_key_value_args(args.variable),
    )
    print(json.dumps(response, indent=2, ensure_ascii=False))
    return 0


def cmd_http_request(args: argparse.Namespace) -> int:
    json_body = json.loads(args.json_body) if args.json_body else None
    response = http_request_raw(
        args.method,
        expand_env_placeholders(args.url),
        headers=expand_env_placeholders(parse_key_value_args(args.header)),
        json_body=expand_env_placeholders(json_body),
        body=expand_env_placeholders(args.body) if args.body is not None else None,
        timeout_seconds=args.timeout_seconds,
    )
    if args.expected_status is not None and response["status"] != args.expected_status:
        print(json.dumps(response, indent=2, ensure_ascii=False))
        raise SmokeError(
            f"HTTP request expected status {args.expected_status} but got {response['status']}."
        )
    print(json.dumps(response, indent=2, ensure_ascii=False))
    return 0


def main() -> int:
    ensure_default_env_loaded()
    args = parse_args()
    if args.command == "list-resources":
        return cmd_list_resources(args)
    if args.command == "execute-tool":
        return cmd_execute_tool(args)
    if args.command == "execute-toolset":
        return cmd_execute_toolset(args)
    if args.command == "http-request":
        return cmd_http_request(args)
    if args.command == "run-suite":
        artifacts_dir = Path(args.artifacts_dir).resolve() if args.artifacts_dir else None
        return run_suite(Path(args.suite).resolve(), args.fail_fast, artifacts_dir)
    raise SmokeError(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
