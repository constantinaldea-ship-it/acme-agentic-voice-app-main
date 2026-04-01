#!/usr/bin/env python3
"""Created by Codex on 2026-03-27.

Reusable CES evaluation runner for deterministic suite definitions.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode


DEFAULT_ENDPOINT = "https://ces.googleapis.com"
DEFAULT_TIMEOUT_SECONDS = 900
DEFAULT_POLL_INTERVAL_SECONDS = 10
DEFAULT_ARTIFACTS_DIRNAME = ".artifacts"
DEFAULT_RUNTIME_ENV = {
    "GCP_LOCATION": "eu",
    "CES_APP_ID": "acme-voice-eu",
}
ENV_PATTERN = re.compile(r"\$\{([A-Z0-9_]+)\}")
RUN_REQUEST_ALLOWED_FIELDS = {
    "appVersion",
    "config",
    "generateLatencyReport",
    "goldenRunMethod",
    "optimizationConfig",
    "personaRunConfigs",
    "runCount",
    "scheduledEvaluationRun",
}


class EvaluationFrameworkError(RuntimeError):
    """Raised when the evaluation framework setup or API contract is invalid."""


@dataclass(frozen=True)
class EvaluationExpectation:
    """Assertion contract for a single CES evaluation."""

    min_pass_rate: float
    min_completed_results: int
    max_error_count: int


@dataclass(frozen=True)
class EvaluationSpec:
    """Resolved local specification for a single CES evaluation."""

    name: str
    kind: str
    owner_agent: str | None
    labels: tuple[str, ...]
    expected: EvaluationExpectation
    local_path: Path


@dataclass(frozen=True)
class EvaluationSuite:
    """Resolved local definition for a reproducible CES evaluation suite."""

    suite_path: Path
    package_root: Path
    project: str
    location: str
    app_id: str
    endpoint: str
    display_name_prefix: str
    require_latency_report: bool
    run_request: dict[str, Any]
    evaluations: tuple[EvaluationSpec, ...]


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(
        description="Run reproducible CES evaluation suites against a deployed app."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser(
        "validate-suite",
        help="Validate a suite definition against local evaluation assets.",
    )
    validate_parser.add_argument("--suite", required=True, help="Path to suite JSON.")

    list_parser = subparsers.add_parser(
        "list-evaluations",
        help="List remote evaluation resources in a deployed app.",
    )
    add_common_app_args(list_parser)

    run_parser = subparsers.add_parser(
        "run-suite",
        help="Run a suite remotely through the CES evaluation API.",
    )
    run_parser.add_argument("--suite", required=True, help="Path to suite JSON.")
    run_parser.add_argument(
        "--artifacts-dir",
        help=(
            "Optional artifact directory. Defaults to "
            "test-harness/evaluation/.artifacts/<timestamp>/"
        ),
    )
    run_parser.add_argument(
        "--timeout-seconds",
        type=int,
        help=f"Override run timeout in seconds. Default: {DEFAULT_TIMEOUT_SECONDS}",
    )
    run_parser.add_argument(
        "--poll-interval-seconds",
        type=int,
        help=f"Override poll interval in seconds. Default: {DEFAULT_POLL_INTERVAL_SECONDS}",
    )
    return parser.parse_args()


def add_common_app_args(parser: argparse.ArgumentParser) -> None:
    """Register common CES app arguments."""
    parser.add_argument("--project", required=True)
    parser.add_argument("--location", required=True)
    parser.add_argument("--app-id", required=True)
    parser.add_argument(
        "--endpoint",
        help="Override CES API endpoint. Default: https://ces.googleapis.com",
    )


def detect_endpoint(explicit: str | None) -> str:
    """Resolve the CES evaluation API endpoint."""
    if explicit:
        return explicit.rstrip("/")
    return DEFAULT_ENDPOINT


def framework_root() -> Path:
    """Return the framework root directory."""
    return Path(__file__).resolve().parent


def repo_root() -> Path:
    """Return the repository root directory."""
    return framework_root().parents[2]


def default_runtime_env_files() -> tuple[Path, ...]:
    """Return env files consulted before resolving suite placeholders."""
    root = repo_root()
    return (
        root / ".env",
        root / ".tmp" / "cloud-run" / "discovery-plan.env",
    )


def default_artifacts_root(base_dir: Path) -> Path:
    """Compute the default artifacts directory for a run."""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return base_dir / DEFAULT_ARTIFACTS_DIRNAME / timestamp


def resolve_path(base_dir: Path, value: str) -> Path:
    """Resolve a possibly-relative path from a base directory."""
    path = Path(value)
    if path.is_absolute():
        return path
    return (base_dir / path).resolve()


def slugify(value: str) -> str:
    """Create a filesystem-safe slug."""
    slug = re.sub(r"[^a-zA-Z0-9._-]+", "-", value.strip().lower()).strip("-")
    return slug or "evaluation"


def build_run_display_name(prefix: str) -> str:
    """Build a unique run display name for this execution."""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"{slugify(prefix)}-{timestamp}"


def maybe_parse_json(text: str) -> Any | None:
    """Best-effort JSON parsing for HTTP responses."""
    if not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def load_json_document(path: Path) -> dict[str, Any]:
    """Load a JSON document from disk."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise EvaluationFrameworkError(f"JSON document not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise EvaluationFrameworkError(f"Invalid JSON document at {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise EvaluationFrameworkError(
            f"Expected JSON object at {path}, got {type(payload).__name__}."
        )
    return payload


def load_env_file(path: Path) -> None:
    """Load KEY=VALUE pairs into the process environment without overriding existing values."""
    if not path.is_file():
        return

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not key or key in os.environ:
            continue
        value = value.strip()
        if value[:1] == value[-1:] and value[:1] in {'"', "'"}:
            value = value[1:-1]
        os.environ[key] = value


def sync_env_aliases() -> None:
    """Normalize common GCP/CES env aliases used across repo scripts."""
    if os.environ.get("PROJECT_ID") and not os.environ.get("GCP_PROJECT_ID"):
        os.environ["GCP_PROJECT_ID"] = os.environ["PROJECT_ID"]
    elif os.environ.get("GCP_PROJECT_ID") and not os.environ.get("PROJECT_ID"):
        os.environ["PROJECT_ID"] = os.environ["GCP_PROJECT_ID"]

    if os.environ.get("LOCATION") and not os.environ.get("GCP_LOCATION"):
        os.environ["GCP_LOCATION"] = os.environ["LOCATION"]
    elif os.environ.get("GCP_LOCATION") and not os.environ.get("LOCATION"):
        os.environ["LOCATION"] = os.environ["GCP_LOCATION"]


def bootstrap_runtime_env(
    *,
    env_files: tuple[Path, ...] | None = None,
    defaults: dict[str, str] | None = None,
) -> None:
    """Load repo-local env files and apply standard CES evaluation defaults."""
    for env_file in env_files or default_runtime_env_files():
        load_env_file(env_file)

    sync_env_aliases()
    for key, value in (defaults or DEFAULT_RUNTIME_ENV).items():
        os.environ.setdefault(key, value)
    sync_env_aliases()


def expand_env_placeholders(value: Any) -> Any:
    """Expand ${ENV_VAR} placeholders in nested structures."""
    if isinstance(value, str):
        def replacer(match: re.Match[str]) -> str:
            key = match.group(1)
            if key not in os.environ:
                raise EvaluationFrameworkError(
                    f"Missing required environment variable: {key}"
                )
            return os.environ[key]

        return ENV_PATTERN.sub(replacer, value)
    if isinstance(value, list):
        return [expand_env_placeholders(item) for item in value]
    if isinstance(value, dict):
        return {key: expand_env_placeholders(item) for key, item in value.items()}
    return value


def resolve_suite_env_value(suite_path: Path, field_name: str, value: Any) -> Any:
    """Expand environment placeholders and attribute failures to a suite field."""
    try:
        return expand_env_placeholders(value)
    except EvaluationFrameworkError as exc:
        raise EvaluationFrameworkError(
            f"Suite {suite_path} field '{field_name}' could not be resolved: {exc}"
        ) from exc


def get_access_token() -> str:
    """Obtain a Google Cloud access token through gcloud."""
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
    raise EvaluationFrameworkError(
        "Failed to obtain a Google Cloud access token. Run 'gcloud auth login' "
        "or 'gcloud auth application-default login' first."
    )


def http_request_raw(
    method: str,
    url: str,
    *,
    token: str | None = None,
    json_body: Any | None = None,
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """Execute an HTTP request and return a normalized response object."""
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, method=method.upper(), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            raw_body = response.read().decode("utf-8")
            return {
                "status": response.status,
                "headers": dict(response.headers.items()),
                "body": raw_body,
                "json": maybe_parse_json(raw_body),
            }
    except urllib.error.HTTPError as exc:
        raw_body = exc.read().decode("utf-8") if exc.fp else ""
        return {
            "status": exc.code,
            "headers": dict(exc.headers.items()) if exc.headers else {},
            "body": raw_body,
            "json": maybe_parse_json(raw_body),
        }
    except urllib.error.URLError as exc:
        raise EvaluationFrameworkError(f"Network error calling {url}: {exc.reason}") from exc


def http_json(
    method: str,
    url: str,
    *,
    token: str | None = None,
    body: dict[str, Any] | None = None,
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """Execute an HTTP request and require a JSON object response."""
    response = http_request_raw(
        method,
        url,
        token=token,
        json_body=body,
        timeout_seconds=timeout_seconds,
    )
    status = response["status"]
    if not 200 <= status < 300:
        message = response.get("body", "")[:500]
        parsed = response.get("json")
        if isinstance(parsed, dict):
            message = parsed.get("error", {}).get("message", message)
        raise EvaluationFrameworkError(f"HTTP {status} from {url}: {message}")
    payload = response.get("json")
    if isinstance(payload, dict):
        return payload
    raise EvaluationFrameworkError(f"Expected JSON response from {url}, got non-JSON body.")


def app_parent(project: str, location: str, app_id: str) -> str:
    """Build the app parent resource name."""
    return f"projects/{project}/locations/{location}/apps/{app_id}"


def collection_url(endpoint: str, parent: str, kind: str, *, version: str = "v1beta") -> str:
    """Build a collection URL for the CES API."""
    return f"{endpoint}/{version}/{parent}/{kind}"


def resource_url(endpoint: str, name: str, *, version: str = "v1beta") -> str:
    """Build a resource URL for the CES API."""
    return f"{endpoint}/{version}/{name}"


def list_collection(
    endpoint: str,
    token: str,
    parent: str,
    kind: str,
    response_field: str,
    *,
    query: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    """List a paginated CES collection."""
    items: list[dict[str, Any]] = []
    page_token = ""
    while True:
        params = dict(query or {})
        if page_token:
            params["pageToken"] = page_token
        url = collection_url(endpoint, parent, kind)
        if params:
            url = f"{url}?{urlencode(params, doseq=True)}"
        payload = http_json("GET", url, token=token)
        batch = payload.get(response_field, [])
        if not isinstance(batch, list):
            raise EvaluationFrameworkError(
                f"Expected '{response_field}' list in {kind} response, got "
                f"{type(batch).__name__}."
            )
        items.extend(batch)
        page_token = payload.get("nextPageToken", "")
        if not page_token:
            return items


def get_resource(
    endpoint: str,
    token: str,
    resource_name: str,
    *,
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """Fetch a single CES resource."""
    return http_json(
        "GET",
        resource_url(endpoint, resource_name),
        token=token,
        timeout_seconds=timeout_seconds,
    )


def write_artifact(artifacts_dir: Path, filename: str, payload: dict[str, Any]) -> Path:
    """Write a JSON artifact to disk."""
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    artifact_path = artifacts_dir / filename
    artifact_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return artifact_path


def parse_expected_block(
    raw_expected: dict[str, Any] | None,
    *,
    base: EvaluationExpectation | None = None,
) -> EvaluationExpectation:
    """Parse and validate an expectation block."""
    payload = dict(raw_expected or {})
    min_pass_rate = payload.get(
        "min_pass_rate",
        base.min_pass_rate if base else 1.0,
    )
    min_completed_results = payload.get(
        "min_completed_results",
        base.min_completed_results if base else 1,
    )
    max_error_count = payload.get(
        "max_error_count",
        base.max_error_count if base else 0,
    )

    if isinstance(min_pass_rate, bool) or not isinstance(min_pass_rate, (int, float)):
        raise EvaluationFrameworkError("'min_pass_rate' must be a number between 0 and 1.")
    if not 0.0 <= float(min_pass_rate) <= 1.0:
        raise EvaluationFrameworkError("'min_pass_rate' must be between 0 and 1.")
    if isinstance(min_completed_results, bool) or not isinstance(min_completed_results, int):
        raise EvaluationFrameworkError("'min_completed_results' must be a positive integer.")
    if min_completed_results < 1:
        raise EvaluationFrameworkError("'min_completed_results' must be at least 1.")
    if isinstance(max_error_count, bool) or not isinstance(max_error_count, int):
        raise EvaluationFrameworkError("'max_error_count' must be a non-negative integer.")
    if max_error_count < 0:
        raise EvaluationFrameworkError("'max_error_count' must be non-negative.")

    return EvaluationExpectation(
        min_pass_rate=float(min_pass_rate),
        min_completed_results=min_completed_results,
        max_error_count=max_error_count,
    )


def detect_local_evaluation_kind(payload: dict[str, Any], *, path: Path) -> str:
    """Infer whether a local evaluation JSON is golden or scenario."""
    if "golden" in payload:
        return "golden"
    if "scenario" in payload:
        return "scenario"
    raise EvaluationFrameworkError(
        f"Evaluation {path} must define either a 'golden' or 'scenario' block."
    )


def load_suite(suite_path: Path) -> EvaluationSuite:
    """Load and validate a local suite definition."""
    raw_suite = load_json_document(suite_path)
    if not isinstance(raw_suite, dict):
        raise EvaluationFrameworkError(f"Suite {suite_path} must be a JSON object.")

    package_root_value = raw_suite.get("package_root", "../acme_voice_agent")
    if not isinstance(package_root_value, str) or not package_root_value:
        raise EvaluationFrameworkError(f"Suite {suite_path} must define 'package_root' as a string.")
    package_root = resolve_path(suite_path.parent, package_root_value)
    if not package_root.is_dir():
        raise EvaluationFrameworkError(
            f"Suite {suite_path} references a missing package_root: {package_root}"
        )

    project = raw_suite.get("project")
    location = raw_suite.get("location")
    app_id = raw_suite.get("app_id")
    for field_name, value in {"project": project, "location": location, "app_id": app_id}.items():
        if not isinstance(value, str) or not value:
            raise EvaluationFrameworkError(
                f"Suite {suite_path} must define a non-empty '{field_name}'."
            )

    display_name_prefix = raw_suite.get("display_name_prefix", suite_path.stem)
    if not isinstance(display_name_prefix, str) or not display_name_prefix.strip():
        raise EvaluationFrameworkError(
            f"Suite {suite_path} must define 'display_name_prefix' as a non-empty string."
        )

    require_latency_report = raw_suite.get("require_latency_report", False)
    if not isinstance(require_latency_report, bool):
        raise EvaluationFrameworkError(
            f"Suite {suite_path} must define 'require_latency_report' as a boolean when present."
        )

    raw_run_request = raw_suite.get("run_request", {}) or {}
    if not isinstance(raw_run_request, dict):
        raise EvaluationFrameworkError(f"Suite {suite_path} must define 'run_request' as an object.")
    unknown_run_fields = sorted(set(raw_run_request) - RUN_REQUEST_ALLOWED_FIELDS)
    if unknown_run_fields:
        joined = ", ".join(unknown_run_fields)
        raise EvaluationFrameworkError(
            f"Suite {suite_path} uses unsupported run_request field(s): {joined}"
        )
    if "runCount" in raw_run_request:
        run_count = raw_run_request["runCount"]
        if isinstance(run_count, bool) or not isinstance(run_count, int) or run_count < 1:
            raise EvaluationFrameworkError("'run_request.runCount' must be a positive integer.")

    default_expected = parse_expected_block(raw_suite.get("default_expected", {}))

    raw_evaluations = raw_suite.get("evaluations")
    if not isinstance(raw_evaluations, list) or not raw_evaluations:
        raise EvaluationFrameworkError(f"Suite {suite_path} must define a non-empty 'evaluations' array.")

    seen_names: set[str] = set()
    evaluations: list[EvaluationSpec] = []
    for index, entry in enumerate(raw_evaluations, start=1):
        if not isinstance(entry, dict):
            raise EvaluationFrameworkError(
                f"Suite {suite_path} evaluation #{index} must be an object."
            )
        name = entry.get("name")
        if not isinstance(name, str) or not name:
            raise EvaluationFrameworkError(
                f"Suite {suite_path} evaluation #{index} must define a non-empty 'name'."
            )
        if name in seen_names:
            raise EvaluationFrameworkError(f"Suite {suite_path} references duplicate evaluation '{name}'.")
        seen_names.add(name)

        local_path = package_root / "evaluations" / name / f"{name}.json"
        local_payload = load_json_document(local_path)
        display_name = local_payload.get("displayName")
        if display_name != name:
            raise EvaluationFrameworkError(
                f"Evaluation {local_path} must use displayName '{name}', got {display_name!r}."
            )

        labels = entry.get("labels", [])
        if not isinstance(labels, list) or not all(isinstance(label, str) and label for label in labels):
            raise EvaluationFrameworkError(
                f"Suite {suite_path} evaluation '{name}' must define 'labels' as a list of strings."
            )
        owner_agent = entry.get("owner_agent")
        if owner_agent is not None and (not isinstance(owner_agent, str) or not owner_agent):
            raise EvaluationFrameworkError(
                f"Suite {suite_path} evaluation '{name}' must define 'owner_agent' as a string when present."
            )

        evaluations.append(
            EvaluationSpec(
                name=name,
                kind=detect_local_evaluation_kind(local_payload, path=local_path),
                owner_agent=owner_agent,
                labels=tuple(labels),
                expected=parse_expected_block(entry.get("expected"), base=default_expected),
                local_path=local_path,
            )
        )

    return EvaluationSuite(
        suite_path=suite_path,
        package_root=package_root,
        project=project,
        location=location,
        app_id=app_id,
        endpoint=detect_endpoint(raw_suite.get("endpoint")),
        display_name_prefix=display_name_prefix,
        require_latency_report=require_latency_report,
        run_request=raw_run_request,
        evaluations=tuple(evaluations),
    )


def resolve_runtime_suite(suite: EvaluationSuite) -> EvaluationSuite:
    """Resolve environment placeholders required only for remote execution."""
    bootstrap_runtime_env()
    return replace(
        suite,
        project=str(resolve_suite_env_value(suite.suite_path, "project", suite.project)),
        location=str(resolve_suite_env_value(suite.suite_path, "location", suite.location)),
        app_id=str(resolve_suite_env_value(suite.suite_path, "app_id", suite.app_id)),
        endpoint=detect_endpoint(
            str(resolve_suite_env_value(suite.suite_path, "endpoint", suite.endpoint))
        ),
        run_request=resolve_suite_env_value(suite.suite_path, "run_request", suite.run_request),
    )


def remote_evaluation_map(
    endpoint: str,
    token: str,
    project: str,
    location: str,
    app_id: str,
) -> dict[str, str]:
    """Map remote evaluation display names to resource names."""
    parent = app_parent(project, location, app_id)
    resources = list_collection(endpoint, token, parent, "evaluations", "evaluations")
    mapping: dict[str, str] = {}
    duplicates: list[str] = []
    for resource in resources:
        display_name = resource.get("displayName")
        name = resource.get("name")
        if not isinstance(display_name, str) or not isinstance(name, str):
            continue
        if display_name in mapping and mapping[display_name] != name:
            duplicates.append(display_name)
            continue
        mapping[display_name] = name
    if duplicates:
        joined = ", ".join(sorted(set(duplicates)))
        raise EvaluationFrameworkError(
            f"Remote app contains duplicate evaluation display names: {joined}"
        )
    return mapping


def resolve_remote_evaluations(
    suite: EvaluationSuite,
    *,
    token: str,
) -> dict[str, str]:
    """Resolve suite evaluation names to remote evaluation resource names."""
    mapping = remote_evaluation_map(
        suite.endpoint,
        token,
        suite.project,
        suite.location,
        suite.app_id,
    )
    resolved: dict[str, str] = {}
    missing: list[str] = []
    for spec in suite.evaluations:
        if spec.name not in mapping:
            missing.append(spec.name)
            continue
        resolved[spec.name] = mapping[spec.name]
    if missing:
        joined = ", ".join(sorted(missing))
        raise EvaluationFrameworkError(
            f"Remote CES app '{suite.app_id}' is missing evaluation resource(s): {joined}"
        )
    return resolved


def start_evaluation_run(
    suite: EvaluationSuite,
    *,
    token: str,
    resolved_evaluations: dict[str, str],
    run_display_name: str,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Start a CES evaluation run."""
    request_body: dict[str, Any] = {
        "displayName": run_display_name,
        "evaluations": [resolved_evaluations[spec.name] for spec in suite.evaluations],
    }
    request_body.update(suite.run_request)
    url = f"{suite.endpoint}/v1beta/{app_parent(suite.project, suite.location, suite.app_id)}:runEvaluation"
    return http_json(
        "POST",
        url,
        token=token,
        body=request_body,
        timeout_seconds=timeout_seconds,
    )


def poll_operation(
    endpoint: str,
    token: str,
    operation_name: str,
    *,
    timeout_seconds: int,
    poll_interval_seconds: int,
) -> dict[str, Any]:
    """Poll a long-running CES operation until completion."""
    deadline = time.monotonic() + timeout_seconds
    while True:
        operation = get_resource(
            endpoint,
            token,
            operation_name,
            timeout_seconds=min(poll_interval_seconds + 5, 60),
        )
        if operation.get("done"):
            return operation
        if time.monotonic() >= deadline:
            raise EvaluationFrameworkError(
                f"Timed out waiting for operation '{operation_name}' after {timeout_seconds}s."
            )
        time.sleep(poll_interval_seconds)


def extract_evaluation_run_name(operation: dict[str, Any]) -> str | None:
    """Extract an evaluation run resource name from an operation payload when possible."""
    candidates: list[str] = []

    def walk(value: Any) -> None:
        if isinstance(value, dict):
            for item in value.values():
                walk(item)
            return
        if isinstance(value, list):
            for item in value:
                walk(item)
            return
        if isinstance(value, str) and "/evaluationRuns/" in value:
            candidates.append(value)

    walk(operation)
    if not candidates:
        return None
    return candidates[0]


def locate_evaluation_run(
    suite: EvaluationSuite,
    *,
    token: str,
    run_display_name: str,
    operation: dict[str, Any],
    timeout_seconds: int,
) -> dict[str, Any]:
    """Locate the created evaluation run resource for this suite execution."""
    run_name = extract_evaluation_run_name(operation)
    if run_name:
        return get_resource(suite.endpoint, token, run_name, timeout_seconds=timeout_seconds)

    parent = app_parent(suite.project, suite.location, suite.app_id)
    runs = list_collection(
        suite.endpoint,
        token,
        parent,
        "evaluationRuns",
        "evaluationRuns",
        query={"orderBy": "createTime"},
    )
    matching_runs = [
        run
        for run in runs
        if run.get("displayName") == run_display_name
    ]
    if not matching_runs:
        raise EvaluationFrameworkError(
            f"Could not locate an evaluation run with displayName '{run_display_name}'."
        )
    matching_runs.sort(key=lambda item: item.get("createTime", ""), reverse=True)
    return matching_runs[0]


def parse_evaluation_name_from_resource(resource_name: str) -> str:
    """Extract the evaluation identifier from a CES resource name."""
    parts = resource_name.split("/")
    try:
        index = parts.index("evaluations")
        return parts[index + 1]
    except (ValueError, IndexError) as exc:
        raise EvaluationFrameworkError(
            f"Could not parse evaluation name from resource '{resource_name}'."
        ) from exc


def parse_evaluation_resource_name_from_result(result_name: str) -> str:
    """Extract the evaluation resource name from an evaluation result resource name."""
    parts = result_name.split("/")
    try:
        index = parts.index("evaluations")
        return "/".join(parts[: index + 2])
    except (ValueError, IndexError) as exc:
        raise EvaluationFrameworkError(
            f"Could not parse evaluation resource name from result '{result_name}'."
        ) from exc


def fetch_result_resource(
    endpoint: str,
    token: str,
    result_name: str,
    *,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Fetch a single evaluation result resource."""
    return get_resource(endpoint, token, result_name, timeout_seconds=timeout_seconds)


def load_results_for_run(
    suite: EvaluationSuite,
    *,
    token: str,
    run_resource: dict[str, Any],
    timeout_seconds: int,
) -> list[dict[str, Any]]:
    """Load detailed evaluation result resources for a completed run."""
    run_name = run_resource.get("name")
    if not isinstance(run_name, str) or not run_name:
        raise EvaluationFrameworkError("Evaluation run resource is missing its 'name'.")

    explicit_results = run_resource.get("evaluationResults", [])
    if isinstance(explicit_results, list) and explicit_results:
        results: list[dict[str, Any]] = []
        for result_name in explicit_results:
            if not isinstance(result_name, str) or not result_name:
                continue
            results.append(
                fetch_result_resource(
                    suite.endpoint,
                    token,
                    result_name,
                    timeout_seconds=timeout_seconds,
                )
            )
        if results:
            return results

    parent = f"{app_parent(suite.project, suite.location, suite.app_id)}/evaluations/-"
    all_results = list_collection(
        suite.endpoint,
        token,
        parent,
        "results",
        "evaluationResults",
    )
    return [
        result
        for result in all_results
        if result.get("evaluationRun") == run_name
    ]


def summarize_suite(
    suite: EvaluationSuite,
    run_resource: dict[str, Any],
    results: list[dict[str, Any]],
    *,
    resource_name_to_display_name: dict[str, str] | None = None,
) -> dict[str, Any]:
    """Summarize detailed suite results and evaluate threshold assertions."""
    resolved_names = resource_name_to_display_name or {}
    results_by_name: dict[str, list[dict[str, Any]]] = {}
    for result in results:
        result_name = result.get("name")
        if not isinstance(result_name, str):
            continue
        evaluation_resource_name = parse_evaluation_resource_name_from_result(result_name)
        evaluation_name = resolved_names.get(
            evaluation_resource_name,
            parse_evaluation_name_from_resource(evaluation_resource_name),
        )
        results_by_name.setdefault(evaluation_name, []).append(result)

    run_summaries = run_resource.get("evaluationRunSummaries", {})
    if not isinstance(run_summaries, dict):
        run_summaries = {}

    suite_results: list[dict[str, Any]] = []
    suite_failures = 0
    for spec in suite.evaluations:
        detailed_results = results_by_name.get(spec.name, [])
        passed_count = 0
        failed_count = 0
        error_count = 0
        completed_count = 0
        pending_count = 0

        if detailed_results:
            for result in detailed_results:
                execution_state = result.get("executionState")
                evaluation_status = result.get("evaluationStatus")
                if execution_state == "COMPLETED":
                    completed_count += 1
                    if evaluation_status == "PASS":
                        passed_count += 1
                    else:
                        failed_count += 1
                elif execution_state == "ERROR":
                    error_count += 1
                else:
                    pending_count += 1
        else:
            raw_summary = run_summaries.get(spec.name)
            if raw_summary is None:
                for key, value in run_summaries.items():
                    if resolved_names.get(key) == spec.name:
                        raw_summary = value
                        break
                    if parse_evaluation_name_from_resource(key) == spec.name:
                        raw_summary = value
                        break
            if isinstance(raw_summary, dict):
                passed_count = int(raw_summary.get("passedCount", 0) or 0)
                failed_count = int(raw_summary.get("failedCount", 0) or 0)
                error_count = int(raw_summary.get("errorCount", 0) or 0)
                completed_count = passed_count + failed_count

        pass_rate = (passed_count / completed_count) if completed_count else 0.0
        assertion_failures: list[str] = []
        if completed_count < spec.expected.min_completed_results:
            assertion_failures.append(
                f"completed_count={completed_count} below required {spec.expected.min_completed_results}"
            )
        if pass_rate < spec.expected.min_pass_rate:
            assertion_failures.append(
                f"pass_rate={pass_rate:.3f} below required {spec.expected.min_pass_rate:.3f}"
            )
        if error_count > spec.expected.max_error_count:
            assertion_failures.append(
                f"error_count={error_count} above allowed {spec.expected.max_error_count}"
            )

        passed = not assertion_failures
        if not passed:
            suite_failures += 1

        suite_results.append(
            {
                "name": spec.name,
                "kind": spec.kind,
                "owner_agent": spec.owner_agent,
                "labels": list(spec.labels),
                "local_path": str(spec.local_path),
                "expected": {
                    "min_pass_rate": spec.expected.min_pass_rate,
                    "min_completed_results": spec.expected.min_completed_results,
                    "max_error_count": spec.expected.max_error_count,
                },
                "actual": {
                    "results_count": len(detailed_results),
                    "completed_count": completed_count,
                    "passed_count": passed_count,
                    "failed_count": failed_count,
                    "error_count": error_count,
                    "pending_count": pending_count,
                    "pass_rate": pass_rate,
                },
                "result_names": [
                    result.get("name")
                    for result in detailed_results
                    if isinstance(result.get("name"), str)
                ],
                "passed": passed,
                "assertion_failures": assertion_failures,
            }
        )

    latency_present = isinstance(run_resource.get("latencyReport"), dict)
    if suite.require_latency_report and not latency_present:
        suite_failures += 1

    suite_passed = suite_failures == 0 and run_resource.get("state") == "COMPLETED"
    if run_resource.get("state") == "ERROR":
        suite_passed = False

    return {
        "suite_path": str(suite.suite_path),
        "package_root": str(suite.package_root),
        "project": suite.project,
        "location": suite.location,
        "app_id": suite.app_id,
        "endpoint": suite.endpoint,
        "display_name_prefix": suite.display_name_prefix,
        "require_latency_report": suite.require_latency_report,
        "latency_report_present": latency_present,
        "run_request": suite.run_request,
        "run_name": run_resource.get("name"),
        "run_display_name": run_resource.get("displayName"),
        "run_state": run_resource.get("state"),
        "run_progress": run_resource.get("progress", {}),
        "suite_passed": suite_passed,
        "suite_failures": suite_failures,
        "evaluations": suite_results,
    }


def validate_suite_command(suite_path: Path) -> int:
    """Validate a local suite definition and print a short report."""
    suite = load_suite(suite_path)
    print(f"Suite: {suite.suite_path}")
    print(f"Package root: {suite.package_root}")
    print(f"Resolved evaluations: {len(suite.evaluations)}")
    for spec in suite.evaluations:
        print(
            f"  PASS  {spec.name} ({spec.kind}) -> {spec.local_path}"
        )
    return 0


def list_evaluations_command(args: argparse.Namespace) -> int:
    """List remote evaluation resources."""
    endpoint = detect_endpoint(args.endpoint)
    token = get_access_token()
    mapping = remote_evaluation_map(endpoint, token, args.project, args.location, args.app_id)
    for display_name, resource_name in sorted(mapping.items()):
        print(f"{display_name}\t{resource_name}")
    return 0


def run_suite(
    suite_path: Path,
    *,
    artifacts_dir: Path | None,
    timeout_seconds: int,
    poll_interval_seconds: int,
) -> int:
    """Run a suite remotely and write reproducible artifacts."""
    suite = resolve_runtime_suite(load_suite(suite_path))
    token = get_access_token()
    resolved_evaluations = resolve_remote_evaluations(suite, token=token)
    run_display_name = build_run_display_name(suite.display_name_prefix)
    artifacts_root = artifacts_dir or default_artifacts_root(framework_root())
    print(f"Artifacts: {artifacts_root}")
    print(f"Run display name: {run_display_name}")

    operation = start_evaluation_run(
        suite,
        token=token,
        resolved_evaluations=resolved_evaluations,
        run_display_name=run_display_name,
        timeout_seconds=timeout_seconds,
    )
    write_artifact(
        artifacts_root,
        "01-operation-start.json",
        {
            "suite": str(suite.suite_path),
            "run_display_name": run_display_name,
            "resolved_evaluations": resolved_evaluations,
            "operation": operation,
        },
    )

    operation_name = operation.get("name")
    if not isinstance(operation_name, str) or not operation_name:
        raise EvaluationFrameworkError("runEvaluation did not return an operation name.")

    completed_operation = poll_operation(
        suite.endpoint,
        token,
        operation_name,
        timeout_seconds=timeout_seconds,
        poll_interval_seconds=poll_interval_seconds,
    )
    write_artifact(
        artifacts_root,
        "02-operation-complete.json",
        {
            "operation": completed_operation,
        },
    )

    if "error" in completed_operation:
        raise EvaluationFrameworkError(
            f"Evaluation operation failed: {json.dumps(completed_operation['error'])}"
        )

    run_resource = locate_evaluation_run(
        suite,
        token=token,
        run_display_name=run_display_name,
        operation=completed_operation,
        timeout_seconds=timeout_seconds,
    )
    write_artifact(
        artifacts_root,
        "03-evaluation-run.json",
        {
            "evaluation_run": run_resource,
        },
    )

    results = load_results_for_run(
        suite,
        token=token,
        run_resource=run_resource,
        timeout_seconds=timeout_seconds,
    )
    for index, result in enumerate(results, start=1):
        result_name = result.get("name", f"result-{index}")
        artifact_name = f"result-{index:02d}-{slugify(str(result_name))}.json"
        write_artifact(artifacts_root, artifact_name, result)

    summary = summarize_suite(
        suite,
        run_resource,
        results,
        resource_name_to_display_name={
            resource_name: display_name
            for display_name, resource_name in resolved_evaluations.items()
        },
    )
    summary["results_artifacts_count"] = len(results)
    summary["finished_at"] = datetime.now(timezone.utc).isoformat()
    summary_path = write_artifact(artifacts_root, "summary.json", summary)

    print(f"Run state: {summary['run_state']}")
    for evaluation in summary["evaluations"]:
        status = "PASS" if evaluation["passed"] else "FAIL"
        actual = evaluation["actual"]
        print(
            f"  {status}  {evaluation['name']} "
            f"(pass_rate={actual['pass_rate']:.3f}, "
            f"completed={actual['completed_count']}, "
            f"errors={actual['error_count']})"
        )
        for failure in evaluation["assertion_failures"]:
            print(f"        {failure}")
    if suite.require_latency_report and not summary["latency_report_present"]:
        print("  FAIL  latency_report missing but required by suite")
    print(f"Summary artifact: {summary_path}")

    if summary["suite_passed"]:
        print("Suite passed.")
        return 0

    print("Suite failed.")
    return 1


def main() -> int:
    """Program entry point."""
    args = parse_args()
    if args.command == "validate-suite":
        return validate_suite_command(Path(args.suite).resolve())
    if args.command == "list-evaluations":
        return list_evaluations_command(args)
    if args.command == "run-suite":
        return run_suite(
            Path(args.suite).resolve(),
            artifacts_dir=Path(args.artifacts_dir).resolve() if args.artifacts_dir else None,
            timeout_seconds=args.timeout_seconds or DEFAULT_TIMEOUT_SECONDS,
            poll_interval_seconds=args.poll_interval_seconds or DEFAULT_POLL_INTERVAL_SECONDS,
        )
    raise EvaluationFrameworkError(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except EvaluationFrameworkError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
