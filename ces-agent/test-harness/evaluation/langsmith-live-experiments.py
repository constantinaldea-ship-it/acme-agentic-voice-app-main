#!/usr/bin/env python3
"""Created by Codex on 2026-03-27.

Run live CES evaluation suites and upload the results to LangSmith as external
experiments.
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_LANGSMITH_API_ROOT = "https://api.smith.langchain.com/api/v1"
DEFAULT_DATASET_DESCRIPTION = (
    "Externally managed CES evaluation dataset uploaded from ces-agent live runs."
)
ENGLISH_HINT_WORDS = {
    "appointment",
    "book",
    "branch",
    "can",
    "discuss",
    "first",
    "for",
    "help",
    "interested",
    "like",
    "okay",
    "product",
    "request",
    "service",
    "tell",
    "topic",
    "you",
    "your",
}
GERMAN_HINT_WORDS = {
    "beratungstermin",
    "bei",
    "bitte",
    "der",
    "die",
    "es",
    "geht",
    "ich",
    "kann",
    "möchte",
    "termin",
    "um",
    "und",
    "worum",
}
APPOINTMENT_SCOPE_HINTS = (
    "advisory",
    "appointment",
    "beratung",
    "beratungstermin",
    "branch",
    "consultation",
    "product",
    "reschedule",
    "service request",
    "serviceanliegen",
    "slot",
    "termin",
    "topic",
    "video",
    "phone",
    "cancel",
)
OFF_TOPIC_BANKING_HINTS = (
    "account balance",
    "balance",
    "bill payment",
    "credit card",
    "fee schedule",
    "fees",
    "overdraft",
    "payment",
    "statement",
    "transaction history",
    "transaction",
    "transfer",
)
GREETING_PREFIXES = (
    "hello",
    "hi",
    "hey",
    "good morning",
    "good afternoon",
    "good evening",
    "guten tag",
    "hallo",
    "gruess gott",
    "grüß gott",
)
CONFIRMATION_HINTS = (
    "actually proceed",
    "bestätigen",
    "confirm",
    "confirmation",
    "do you want me to",
    "is that okay",
    "möchten sie",
    "möchtest du",
    "please confirm",
    "shall i",
    "soll ich",
    "would you like me to",
)
SLOT_CLAIM_HINTS = (
    "available slot",
    "available slots",
    "available time",
    "available times",
    "free slot",
    "free slots",
    "have openings",
    "freie termine",
    "verfügbare termine",
)
STATUS_CLAIM_HINTS = (
    "appointment is booked",
    "appointment is cancelled",
    "appointment is canceled",
    "appointment is confirmed",
    "appointment is rescheduled",
    "booked",
    "canceled",
    "cancelled",
    "confirmed",
    "gebucht",
    "rescheduled",
    "storniert",
    "verschoben",
)
TIME_TOKEN_PATTERN = re.compile(r"\b\d{1,2}:\d{2}\b")
GERMAN_TIME_TOKEN_PATTERN = re.compile(r"\b\d{1,2}(?::\d{2})?\s*uhr\b")
KNOWN_APPOINTMENT_TOOLS = {
    "cancelAppointment",
    "createAppointment",
    "getAppointment",
    "getAppointmentSlots",
    "getAppointmentTaxonomy",
    "rescheduleAppointment",
    "searchAppointmentBranches",
    "searchAppointmentServices",
}
SLOT_GROUNDING_TOOLS = {"getAppointmentSlots"}
STATUS_GROUNDING_TOOLS = {
    "cancelAppointment",
    "createAppointment",
    "getAppointment",
    "rescheduleAppointment",
}
LIFECYCLE_ACTION_TOOLS = {"cancelAppointment", "rescheduleAppointment"}


class LangSmithExperimentError(RuntimeError):
    """Raised when LangSmith experiment configuration or upload is invalid."""


@dataclass(frozen=True)
class SuiteLangSmithConfig:
    """LangSmith upload settings embedded in a CES suite definition."""

    dataset_name: str
    dataset_description: str | None
    experiment_description: str | None
    experiment_metadata: dict[str, Any]


@dataclass(frozen=True)
class LangSmithAuthConfig:
    """Resolved LangSmith authentication settings."""

    api_root: str
    api_key: str
    workspace_id: str | None


def repo_root() -> Path:
    """Return the repository root."""
    return evaluation_tests_root().parents[2]


def evaluation_tests_root() -> Path:
    """Return the canonical evaluation harness directory."""
    return Path(__file__).resolve().parent


def artifacts_root_base() -> Path:
    """Return the root directory used for LangSmith experiment artifacts."""
    return evaluation_tests_root() / ".artifacts" / "langsmith"


def timestamp_slug() -> str:
    """Return a UTC timestamp slug."""
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def load_dotenv_if_present() -> None:
    """Load the repository root .env file when present."""
    dotenv_path = repo_root() / ".env"
    if not dotenv_path.is_file():
        return

    for raw_line in dotenv_path.read_text(encoding="utf-8").splitlines():
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


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(
        description=(
            "Execute live CES evaluation suites and upload the results to "
            "LangSmith as external experiments."
        )
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_parser = subparsers.add_parser(
        "run",
        help="Run one or more CES suites and upload the resulting experiments.",
    )
    run_parser.add_argument(
        "--suite",
        action="append",
        required=True,
        help="Path to a CES suite JSON. Repeat for multiple suites.",
    )
    run_parser.add_argument("--project", help="Override GCP_PROJECT_ID for suite execution.")
    run_parser.add_argument("--location", help="Override GCP_LOCATION for suite execution.")
    run_parser.add_argument("--app-id", help="Override CES_APP_ID for suite execution.")
    run_parser.add_argument(
        "--artifacts-dir",
        help=(
            "Optional artifact root. Defaults to "
            "test-harness/evaluation/.artifacts/langsmith/<timestamp>/"
        ),
    )
    run_parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=900,
        help="Remote CES evaluation timeout in seconds. Default: 900.",
    )
    run_parser.add_argument(
        "--poll-interval-seconds",
        type=int,
        default=10,
        help="Remote CES polling interval in seconds. Default: 10.",
    )
    run_parser.add_argument(
        "--upload-mode",
        choices=("auto", "always", "never"),
        default="auto",
        help=(
            "Upload mode. 'auto' uploads only when LANGSMITH_API_KEY is set. "
            "'always' fails if upload is not possible. 'never' only writes the "
            "upload payload artifacts."
        ),
    )
    run_parser.add_argument(
        "--langsmith-endpoint",
        help=(
            "Optional LangSmith API endpoint override. Defaults to "
            "LANGSMITH_ENDPOINT or https://api.smith.langchain.com/api/v1."
        ),
    )
    run_parser.add_argument(
        "--langsmith-workspace-id",
        help=(
            "Optional LangSmith workspace ID. Defaults to LANGSMITH_WORKSPACE_ID. "
            "Required for some multi-workspace or organization-scoped keys."
        ),
    )

    validate_parser = subparsers.add_parser(
        "validate-suite-config",
        help="Validate the embedded LangSmith config for one or more suite files.",
    )
    validate_parser.add_argument(
        "--suite",
        action="append",
        required=True,
        help="Path to a CES suite JSON. Repeat for multiple suites.",
    )
    return parser.parse_args()


def load_ces_runner_module() -> Any:
    """Load the existing CES evaluation runner module."""
    module_path = evaluation_tests_root() / "ces-evaluation-runner.py"
    spec = importlib.util.spec_from_file_location("ces_evaluation_runner", module_path)
    if spec is None or spec.loader is None:
        raise LangSmithExperimentError(f"Could not load CES runner from {module_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules["ces_evaluation_runner"] = module
    spec.loader.exec_module(module)
    return module


def bootstrap_ces_runtime_env(
    args: argparse.Namespace,
    *,
    ces_runner: Any | None = None,
) -> Any:
    """Reuse the CES evaluation runner env bootstrap and then apply CLI overrides."""
    runner = ces_runner or load_ces_runner_module()
    runner.bootstrap_runtime_env()
    apply_env_overrides(args)
    if hasattr(runner, "sync_env_aliases"):
        runner.sync_env_aliases()
    return runner


def load_json_document(path: Path) -> dict[str, Any]:
    """Load a JSON document from disk."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise LangSmithExperimentError(f"JSON document not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise LangSmithExperimentError(f"Invalid JSON document at {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise LangSmithExperimentError(
            f"Expected JSON object at {path}, got {type(payload).__name__}."
        )
    return payload


def normalize_langsmith_api_root(value: str | None) -> str:
    """Normalize a LangSmith API base URL to a /api/v1 root."""
    raw = (value or os.environ.get("LANGSMITH_ENDPOINT") or DEFAULT_LANGSMITH_API_ROOT).strip()
    if not raw:
        raise LangSmithExperimentError("LangSmith endpoint must be a non-empty string.")

    normalized = raw.rstrip("/")
    if normalized.endswith("/api/v1") or normalized.endswith("/v1"):
        return normalized
    if normalized.endswith("/api"):
        return f"{normalized}/v1"
    return f"{normalized}/api/v1"


def resolve_langsmith_auth_config(args: argparse.Namespace) -> LangSmithAuthConfig:
    """Resolve LangSmith auth settings from CLI arguments and environment."""
    workspace_id = (
        args.langsmith_workspace_id
        or os.environ.get("LANGSMITH_WORKSPACE_ID", "").strip()
        or None
    )
    return LangSmithAuthConfig(
        api_root=normalize_langsmith_api_root(args.langsmith_endpoint),
        api_key=os.environ.get("LANGSMITH_API_KEY", "").strip(),
        workspace_id=workspace_id,
    )


def load_suite_langsmith_config(suite_path: Path) -> SuiteLangSmithConfig:
    """Load the LangSmith config block embedded in a CES suite."""
    payload = load_json_document(suite_path)
    raw_config = payload.get("langsmith")
    if not isinstance(raw_config, dict):
        raise LangSmithExperimentError(
            f"Suite {suite_path} must define a top-level 'langsmith' object."
        )

    dataset_name = raw_config.get("dataset_name")
    if not isinstance(dataset_name, str) or not dataset_name.strip():
        raise LangSmithExperimentError(
            f"Suite {suite_path} must define 'langsmith.dataset_name' as a non-empty string."
        )

    dataset_description = raw_config.get("dataset_description")
    if dataset_description is not None and not isinstance(dataset_description, str):
        raise LangSmithExperimentError(
            f"Suite {suite_path} must define 'langsmith.dataset_description' as a string."
        )

    experiment_description = raw_config.get("experiment_description")
    if experiment_description is not None and not isinstance(experiment_description, str):
        raise LangSmithExperimentError(
            f"Suite {suite_path} must define 'langsmith.experiment_description' as a string."
        )

    experiment_metadata = raw_config.get("experiment_metadata", {})
    if not isinstance(experiment_metadata, dict):
        raise LangSmithExperimentError(
            f"Suite {suite_path} must define 'langsmith.experiment_metadata' as an object."
        )

    return SuiteLangSmithConfig(
        dataset_name=dataset_name.strip(),
        dataset_description=dataset_description.strip() if isinstance(dataset_description, str) else None,
        experiment_description=experiment_description.strip() if isinstance(experiment_description, str) else None,
        experiment_metadata=experiment_metadata,
    )


def write_json_artifact(path: Path, payload: dict[str, Any]) -> Path:
    """Write a formatted JSON artifact."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


def suite_artifacts_dir(root: Path, suite_path: Path) -> Path:
    """Return a stable per-suite artifact directory."""
    return root / suite_path.stem


def stable_uuid(value: str) -> str:
    """Return a stable UUID derived from a string value."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, value))


def utc_now_iso() -> str:
    """Return an ISO 8601 timestamp in UTC."""
    return datetime.now(timezone.utc).isoformat()


def read_json_if_present(path: Path) -> dict[str, Any] | None:
    """Read an optional JSON file."""
    if not path.is_file():
        return None
    payload = load_json_document(path)
    return payload


def artifact_timestamps(
    run_resource: dict[str, Any] | None,
    completed_operation: dict[str, Any] | None,
    summary: dict[str, Any],
) -> tuple[str, str]:
    """Derive experiment-level timestamps from CES artifacts."""
    start_candidates = []
    end_candidates = []

    if isinstance(run_resource, dict):
        for key in ("createTime", "startTime"):
            value = run_resource.get(key)
            if isinstance(value, str) and value:
                start_candidates.append(value)
        for key in ("updateTime", "endTime"):
            value = run_resource.get(key)
            if isinstance(value, str) and value:
                end_candidates.append(value)

    if isinstance(completed_operation, dict):
        metadata = completed_operation.get("metadata")
        if isinstance(metadata, dict):
            for key in ("createTime", "startTime"):
                value = metadata.get(key)
                if isinstance(value, str) and value:
                    start_candidates.append(value)
            for key in ("endTime", "updateTime"):
                value = metadata.get(key)
                if isinstance(value, str) and value:
                    end_candidates.append(value)

    now_iso = utc_now_iso()
    start_time = start_candidates[0] if start_candidates else now_iso
    end_time = end_candidates[0] if end_candidates else start_time
    if end_time < start_time:
        end_time = start_time
    return start_time, end_time


def parse_scenario_expectations(payload: dict[str, Any]) -> tuple[str, int, list[str], str]:
    """Extract task and expectations from a CES scenario evaluation."""
    scenario = payload.get("scenario", {})
    task = str(scenario.get("task", ""))
    expectations = [
        str(item["expectedResult"])
        for item in scenario.get("scenarioExpectations", [])
        if isinstance(item, dict) and "expectedResult" in item
    ]
    return task, int(scenario.get("maxTurns", 0) or 0), expectations, "ces_scenario"


def parse_golden_expectations(payload: dict[str, Any]) -> tuple[str, int, list[str], str]:
    """Extract prompt-like inputs and expectations from a CES golden evaluation."""
    golden = payload.get("golden", {})
    turns = golden.get("turns", [])
    task = "Golden evaluation"
    expectations: list[str] = []

    for turn in turns:
        if not isinstance(turn, dict):
            continue
        for step in turn.get("steps", []):
            if not isinstance(step, dict):
                continue
            user_input = step.get("userInput")
            if task == "Golden evaluation" and isinstance(user_input, dict):
                text = user_input.get("text")
                if isinstance(text, str) and text.strip():
                    task = text.strip()

            expectation = step.get("expectation")
            if not isinstance(expectation, dict):
                continue

            agent_transfer = expectation.get("agentTransfer")
            if isinstance(agent_transfer, dict):
                target_agent = agent_transfer.get("targetAgent")
                if isinstance(target_agent, str) and target_agent:
                    expectations.append(f"Agent transfers to {target_agent}.")

            tool_call = expectation.get("toolCall")
            if isinstance(tool_call, dict):
                tool_name = tool_call.get("tool") or tool_call.get("displayName")
                if isinstance(tool_name, str) and tool_name:
                    expectations.append(f"Agent calls tool {tool_name}.")

    return task, len(turns), expectations, "ces_golden"


def infer_language(name: str) -> str:
    """Infer the evaluation language from its display name."""
    return "de" if "german" in name else "en"


def infer_journey_type(name: str) -> str:
    """Infer the advisory journey type from its display name."""
    if "routing" in name:
        return "routing"
    if "cancel" in name or "reschedule" in name:
        return "lifecycle"
    if "handoff" in name or "repair" in name or "no_slots" in name:
        return "recovery"
    return "booking"


def load_result_artifacts(artifacts_dir: Path) -> dict[str, dict[str, Any]]:
    """Load detailed CES result artifacts keyed by result resource name."""
    result_payloads: dict[str, dict[str, Any]] = {}
    if not artifacts_dir.is_dir():
        return result_payloads
    for artifact_path in sorted(artifacts_dir.glob("result-*.json")):
        payload = load_json_document(artifact_path)
        result_name = payload.get("name")
        if isinstance(result_name, str) and result_name:
            result_payloads[result_name] = payload
    return result_payloads


def extract_first_observed_agent_response_text(result_payload: dict[str, Any]) -> str | None:
    """Extract the first observed agent response text from a CES golden result."""
    golden_result = result_payload.get("goldenResult")
    if not isinstance(golden_result, dict):
        return None

    for replay in golden_result.get("turnReplayResults", []):
        if not isinstance(replay, dict):
            continue
        for outcome in replay.get("expectationOutcome", []):
            if not isinstance(outcome, dict):
                continue
            observed_agent_response = outcome.get("observedAgentResponse")
            if not isinstance(observed_agent_response, dict):
                continue
            chunks = observed_agent_response.get("chunks", [])
            texts = [
                chunk.get("text", "").strip()
                for chunk in chunks
                if isinstance(chunk, dict) and isinstance(chunk.get("text"), str)
            ]
            flattened = " ".join(text for text in texts if text).strip()
            if flattened:
                return re.sub(r"\s+", " ", flattened)
    return None


def classify_text_language(text: str) -> str:
    """Infer a coarse language classification for short advisory responses."""
    lowered = text.lower().strip()
    if not lowered:
        return "unknown"
    if any(char in lowered for char in ("ä", "ö", "ü", "ß")):
        return "de"

    tokens = re.findall(r"[a-zA-ZÀ-ÿ']+", lowered)
    english_score = sum(1 for token in tokens if token in ENGLISH_HINT_WORDS)
    german_score = sum(1 for token in tokens if token in GERMAN_HINT_WORDS)
    if german_score > english_score:
        return "de"
    if english_score > german_score:
        return "en"
    return "unknown"


def starts_with_greeting(text: str) -> bool:
    """Return whether the response opens with a greeting phrase."""
    normalized = re.sub(r"\s+", " ", text.lower().strip())
    return any(normalized.startswith(prefix) for prefix in GREETING_PREFIXES)


def build_routing_instruction_feedback(
    *,
    display_name: str,
    response_text: str,
) -> list[dict[str, Any]]:
    """Build lightweight instruction-quality scores for routing evaluations."""
    expected_language = infer_language(display_name)
    detected_language = classify_text_language(response_text)
    same_language = detected_language == expected_language
    greeting_detected = starts_with_greeting(response_text)
    question_count = response_text.count("?")
    one_question_only = question_count <= 1

    return [
        build_feedback_score(
            key="same_language_as_user",
            score=1.0 if same_language else 0.0,
            value="pass" if same_language else "fail",
            comment=f"expected={expected_language}, detected={detected_language}",
            feedback_type="categorical",
            categories=[
                {"value": 0, "label": "fail"},
                {"value": 1, "label": "pass"},
            ],
        ),
        build_feedback_score(
            key="no_greeting_on_first_turn",
            score=0.0 if greeting_detected else 1.0,
            value="fail" if greeting_detected else "pass",
            comment=f"response={response_text[:160]}",
            feedback_type="categorical",
            categories=[
                {"value": 0, "label": "fail"},
                {"value": 1, "label": "pass"},
            ],
        ),
        build_feedback_score(
            key="single_question_first_turn",
            score=1.0 if one_question_only else 0.0,
            value="pass" if one_question_only else "fail",
            comment=f"question_count={question_count}",
            feedback_type="categorical",
            categories=[
                {"value": 0, "label": "fail"},
                {"value": 1, "label": "pass"},
            ],
        ),
    ]


def response_contains_confirmation_cue(text: str) -> bool:
    """Return whether a response contains explicit confirmation language."""
    lowered = text.lower()
    return any(hint in lowered for hint in CONFIRMATION_HINTS)


def response_contains_slot_claim(text: str) -> bool:
    """Return whether a response appears to claim slot availability."""
    lowered = text.lower()
    if TIME_TOKEN_PATTERN.search(lowered) is not None:
        return True
    if GERMAN_TIME_TOKEN_PATTERN.search(lowered) is not None:
        return True
    if re.search(r"\bslots?\b", lowered):
        return True
    return any(hint in lowered for hint in SLOT_CLAIM_HINTS)


def response_contains_status_claim(text: str) -> bool:
    """Return whether a response appears to claim appointment status/outcome."""
    lowered = text.lower()
    return any(hint in lowered for hint in STATUS_CLAIM_HINTS)


def extract_ordered_observation_events(result_payload: dict[str, Any]) -> list[dict[str, str]]:
    """Extract ordered response/tool observations from a CES result payload."""
    events: list[dict[str, str]] = []

    def add_response_from_payload(payload: dict[str, Any]) -> None:
        response_text = extract_first_observed_agent_response_text({"goldenResult": {"turnReplayResults": [{"expectationOutcome": [payload]}]}})
        if response_text:
            events.append({"type": "response", "value": response_text})

    def walk(value: Any, *, in_expectation: bool = False) -> None:
        if isinstance(value, dict):
            if "observedAgentResponse" in value and isinstance(value["observedAgentResponse"], dict):
                add_response_from_payload({"observedAgentResponse": value["observedAgentResponse"]})
            if not in_expectation:
                for key in ("observedToolCall", "observedToolInvocation"):
                    observed_tool = value.get(key)
                    if isinstance(observed_tool, dict):
                        for tool_key in ("tool", "displayName"):
                            tool_name = observed_tool.get(tool_key)
                            if isinstance(tool_name, str) and tool_name in KNOWN_APPOINTMENT_TOOLS:
                                events.append({"type": "tool", "value": tool_name})
                                break
                for key in ("tool", "displayName"):
                    tool_name = value.get(key)
                    if isinstance(tool_name, str) and tool_name in KNOWN_APPOINTMENT_TOOLS:
                        events.append({"type": "tool", "value": tool_name})

            for key, item in value.items():
                if key == "expectation":
                    continue
                walk(item, in_expectation=in_expectation or key == "expectation")
            return
        if isinstance(value, list):
            for item in value:
                walk(item, in_expectation=in_expectation)

    walk(result_payload)
    deduped: list[dict[str, str]] = []
    for event in events:
        if not deduped or deduped[-1] != event:
            deduped.append(event)
    return deduped


def build_tri_state_feedback(
    *,
    key: str,
    state: str,
    comment: str,
) -> dict[str, Any]:
    """Build a pass/fail/not_observed categorical feedback score."""
    score_map = {"pass": 1.0, "fail": 0.0}
    return build_feedback_score(
        key=key,
        score=score_map.get(state),
        value=state,
        comment=comment,
        feedback_type="categorical",
        categories=[
            {"value": 1, "label": "pass"},
            {"value": 0, "label": "fail"},
            {"value": -1, "label": "not_observed"},
        ],
    )


def build_confirmation_before_lifecycle_action_feedback(
    events: list[dict[str, str]],
) -> dict[str, Any]:
    """Score whether confirmation language appears before lifecycle tool actions."""
    first_lifecycle_index = next(
        (index for index, event in enumerate(events) if event["type"] == "tool" and event["value"] in LIFECYCLE_ACTION_TOOLS),
        None,
    )
    if first_lifecycle_index is None:
        return build_tri_state_feedback(
            key="confirmation_before_lifecycle_action",
            state="not_observed",
            comment="No lifecycle tool invocation observed in CES result.",
        )

    prior_confirmation = any(
        event["type"] == "response" and response_contains_confirmation_cue(event["value"])
        for event in events[:first_lifecycle_index]
    )
    return build_tri_state_feedback(
        key="confirmation_before_lifecycle_action",
        state="pass" if prior_confirmation else "fail",
        comment=(
            "Confirmation cue observed before lifecycle tool invocation."
            if prior_confirmation
            else "Lifecycle tool invocation observed without prior confirmation cue."
        ),
    )


def build_no_ungrounded_slot_or_status_claims_feedback(
    events: list[dict[str, str]],
) -> dict[str, Any]:
    """Score whether slot/status claims appear only after grounding tool calls."""
    saw_slot_claim = False
    saw_status_claim = False
    slot_grounded = False
    status_grounded = False

    for event in events:
        if event["type"] == "tool":
            if event["value"] in SLOT_GROUNDING_TOOLS:
                slot_grounded = True
            if event["value"] in STATUS_GROUNDING_TOOLS:
                status_grounded = True
            continue

        response_text = event["value"]
        if response_contains_slot_claim(response_text):
            saw_slot_claim = True
            if not slot_grounded:
                return build_tri_state_feedback(
                    key="no_ungrounded_slot_or_status_claims",
                    state="fail",
                    comment="Slot claim observed before any slot grounding tool call.",
                )
        if response_contains_status_claim(response_text):
            saw_status_claim = True
            if not status_grounded:
                return build_tri_state_feedback(
                    key="no_ungrounded_slot_or_status_claims",
                    state="fail",
                    comment="Status/outcome claim observed before any grounding lifecycle tool call.",
                )

    if saw_slot_claim or saw_status_claim:
        return build_tri_state_feedback(
            key="no_ungrounded_slot_or_status_claims",
            state="pass",
            comment="All slot/status claims had prior grounding tool evidence.",
        )
    return build_tri_state_feedback(
        key="no_ungrounded_slot_or_status_claims",
        state="not_observed",
        comment="No slot or status claims observed in CES result.",
    )


def build_first_turn_stays_in_scope_feedback(response_text: str) -> dict[str, Any]:
    """Score whether the first observed response stays inside advisory appointment scope."""
    lowered = response_text.lower()
    if any(hint in lowered for hint in OFF_TOPIC_BANKING_HINTS):
        return build_tri_state_feedback(
            key="first_turn_stays_in_scope",
            state="fail",
            comment="First response contains off-topic banking content.",
        )
    if any(hint in lowered for hint in APPOINTMENT_SCOPE_HINTS):
        return build_tri_state_feedback(
            key="first_turn_stays_in_scope",
            state="pass",
            comment="First response stays within appointment/advisory scope.",
        )
    return build_tri_state_feedback(
        key="first_turn_stays_in_scope",
        state="not_observed",
        comment="First response did not contain strong scope markers.",
    )


def build_feedback_score(
    *,
    key: str,
    score: float | None = None,
    value: str | None = None,
    comment: str | None = None,
    feedback_type: str = "continuous",
    categories: list[dict[str, Any]] | None = None,
    minimum: float | None = None,
    maximum: float | None = None,
) -> dict[str, Any]:
    """Build a LangSmith feedback object."""
    payload: dict[str, Any] = {
        "key": key,
        "feedback_source": {"type": "api"},
    }
    if score is not None:
        payload["score"] = score
    if value is not None:
        payload["value"] = value
    if comment is not None:
        payload["comment"] = comment

    config: dict[str, Any] = {"type": feedback_type}
    if minimum is not None:
        config["min"] = minimum
    if maximum is not None:
        config["max"] = maximum
    if categories:
        config["categories"] = categories
    payload["feedback_config"] = config
    return payload


def build_evaluation_result_row(
    *,
    dataset_name: str,
    evaluation_summary: dict[str, Any],
    experiment_start_time: str,
    experiment_end_time: str,
    result_payloads_by_name: dict[str, dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Map one CES evaluation summary into a LangSmith experiment result row."""
    evaluation_path = Path(str(evaluation_summary["local_path"]))
    payload = load_json_document(evaluation_path)
    display_name = str(payload.get("displayName", evaluation_summary["name"]))

    if "scenario" in payload:
        task, max_turns, expectations, source_type = parse_scenario_expectations(payload)
    elif "golden" in payload:
        task, max_turns, expectations, source_type = parse_golden_expectations(payload)
    else:
        raise LangSmithExperimentError(
            f"Unsupported evaluation format for {evaluation_path}; expected scenario or golden."
        )

    actual = evaluation_summary["actual"]
    assertion_failures = [str(item) for item in evaluation_summary.get("assertion_failures", [])]
    passed = bool(evaluation_summary.get("passed"))
    error_message = "; ".join(assertion_failures) if assertion_failures else None
    result_names = [
        name
        for name in evaluation_summary.get("result_names", [])
        if isinstance(name, str) and name
    ]
    matching_result_payloads = [
        result_payloads_by_name[name]
        for name in result_names
        if isinstance(result_payloads_by_name, dict) and name in result_payloads_by_name
    ]
    first_agent_response = None
    independent_scores: list[dict[str, Any]] = []
    ordered_events: list[dict[str, str]] = []
    if infer_journey_type(display_name) == "routing" and matching_result_payloads:
        first_agent_response = extract_first_observed_agent_response_text(matching_result_payloads[0])
        if first_agent_response:
            independent_scores = build_routing_instruction_feedback(
                display_name=display_name,
                response_text=first_agent_response,
            )
    if matching_result_payloads:
        ordered_events = extract_ordered_observation_events(matching_result_payloads[0])
        if first_agent_response is None:
            first_agent_response = next(
                (event["value"] for event in ordered_events if event["type"] == "response"),
                None,
            )
        independent_scores.extend(
            [
                build_confirmation_before_lifecycle_action_feedback(ordered_events),
                build_no_ungrounded_slot_or_status_claims_feedback(ordered_events),
            ]
        )
        if first_agent_response:
            independent_scores.append(
                build_first_turn_stays_in_scope_feedback(first_agent_response)
            )

    actual_outputs: dict[str, Any] = {
        "passed": passed,
        "assertion_failures": assertion_failures,
        "metrics": actual,
        "result_names": result_names,
    }
    if first_agent_response:
        actual_outputs["first_agent_response"] = first_agent_response
    if ordered_events:
        actual_outputs["observation_events"] = ordered_events

    return {
        "row_id": stable_uuid(f"{dataset_name}:{display_name}"),
        "inputs": {
            "evaluation_name": display_name,
            "task": task,
            "max_turns": max_turns,
        },
        "expected_outputs": {
            "reference_expectations": expectations,
            "target_agent": evaluation_summary.get("owner_agent"),
            "source_evaluation_type": source_type,
            "thresholds": evaluation_summary.get("expected", {}),
        },
        "actual_outputs": actual_outputs,
        "evaluation_scores": [
            build_feedback_score(
                key="threshold_pass",
                score=1.0 if passed else 0.0,
                value="pass" if passed else "fail",
                comment=error_message,
                feedback_type="categorical",
                categories=[
                    {"value": 0, "label": "fail"},
                    {"value": 1, "label": "pass"},
                ],
            ),
            build_feedback_score(
                key="pass_rate",
                score=float(actual.get("pass_rate", 0.0) or 0.0),
                feedback_type="continuous",
                minimum=0.0,
                maximum=1.0,
            ),
            build_feedback_score(
                key="error_free",
                score=1.0 if int(actual.get("error_count", 0) or 0) == 0 else 0.0,
                value="yes" if int(actual.get("error_count", 0) or 0) == 0 else "no",
                comment=f"error_count={actual.get('error_count', 0)}",
                feedback_type="categorical",
                categories=[
                    {"value": 0, "label": "no"},
                    {"value": 1, "label": "yes"},
                ],
            ),
            *independent_scores,
        ],
        "start_time": experiment_start_time,
        "end_time": experiment_end_time,
        "run_name": display_name,
        "error": error_message,
        "run_metadata": {
            "owner_agent": evaluation_summary.get("owner_agent"),
            "labels": evaluation_summary.get("labels", []),
            "evaluation_kind": evaluation_summary.get("kind"),
            "journey_type": infer_journey_type(display_name),
            "language": infer_language(display_name),
            "source_path": str(evaluation_path),
        },
    }


def build_langsmith_upload_body(
    *,
    suite_path: Path,
    suite_config: SuiteLangSmithConfig,
    summary: dict[str, Any],
    run_resource: dict[str, Any] | None,
    completed_operation: dict[str, Any] | None,
    result_payloads_by_name: dict[str, dict[str, Any]] | None = None,
) -> dict[str, Any]:
    """Build a LangSmith external-experiment upload payload from CES artifacts."""
    experiment_start_time, experiment_end_time = artifact_timestamps(
        run_resource,
        completed_operation,
        summary,
    )
    evaluation_rows = [
        build_evaluation_result_row(
            dataset_name=suite_config.dataset_name,
            evaluation_summary=evaluation_summary,
            experiment_start_time=experiment_start_time,
            experiment_end_time=experiment_end_time,
            result_payloads_by_name=result_payloads_by_name,
        )
        for evaluation_summary in summary.get("evaluations", [])
    ]
    total_evaluations = len(evaluation_rows)
    passed_evaluations = sum(
        1 for evaluation_summary in summary.get("evaluations", []) if evaluation_summary.get("passed")
    )
    evaluation_pass_ratio = (
        passed_evaluations / total_evaluations if total_evaluations else 0.0
    )

    summary_comment = None
    if int(summary.get("suite_failures", 0) or 0) > 0:
        summary_comment = f"suite_failures={summary.get('suite_failures', 0)}"

    return {
        "experiment_name": str(summary.get("run_display_name") or suite_path.stem),
        "experiment_description": suite_config.experiment_description
        or f"Live CES evaluation upload for suite {suite_path.name}.",
        "experiment_start_time": experiment_start_time,
        "experiment_end_time": experiment_end_time,
        "dataset_id": stable_uuid(f"ces-agent:dataset:{suite_config.dataset_name}"),
        "dataset_name": suite_config.dataset_name,
        "dataset_description": suite_config.dataset_description or DEFAULT_DATASET_DESCRIPTION,
        "experiment_metadata": {
            **suite_config.experiment_metadata,
            "source_system": "ces",
            "suite_path": str(suite_path),
            "project": summary.get("project"),
            "location": summary.get("location"),
            "app_id": summary.get("app_id"),
            "run_name": summary.get("run_name"),
            "run_state": summary.get("run_state"),
        },
        "summary_experiment_scores": [
            build_feedback_score(
                key="suite_passed",
                score=1.0 if summary.get("suite_passed") else 0.0,
                value="pass" if summary.get("suite_passed") else "fail",
                comment=summary_comment,
                feedback_type="categorical",
                categories=[
                    {"value": 0, "label": "fail"},
                    {"value": 1, "label": "pass"},
                ],
            ),
            build_feedback_score(
                key="evaluation_pass_ratio",
                score=evaluation_pass_ratio,
                feedback_type="continuous",
                minimum=0.0,
                maximum=1.0,
            ),
            build_feedback_score(
                key="latency_report_present",
                score=1.0 if summary.get("latency_report_present") else 0.0,
                value="yes" if summary.get("latency_report_present") else "no",
                feedback_type="categorical",
                categories=[
                    {"value": 0, "label": "no"},
                    {"value": 1, "label": "yes"},
                ],
            ),
        ],
        "results": evaluation_rows,
    }


def http_json(
    method: str,
    url: str,
    *,
    headers: dict[str, str],
    json_body: dict[str, Any] | None = None,
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """Execute an HTTP request and require a JSON object response."""
    request_headers = dict(headers)
    data = None
    if json_body is not None:
        data = json.dumps(json_body).encode("utf-8")
        request_headers["Content-Type"] = "application/json"
    request_headers.setdefault("Accept", "application/json")
    request = urllib.request.Request(url, data=data, method=method.upper(), headers=request_headers)
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            raw_body = response.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        raw_body = exc.read().decode("utf-8") if exc.fp else ""
        raise LangSmithExperimentError(
            f"HTTP {exc.code} from {url}: {raw_body[:500]}"
        ) from exc
    except urllib.error.URLError as exc:
        raise LangSmithExperimentError(f"Network error calling {url}: {exc.reason}") from exc

    try:
        payload = json.loads(raw_body)
    except json.JSONDecodeError as exc:
        raise LangSmithExperimentError(f"Expected JSON response from {url}.") from exc
    if not isinstance(payload, dict):
        raise LangSmithExperimentError(
            f"Expected JSON object response from {url}, got {type(payload).__name__}."
        )
    return payload


def build_langsmith_headers(auth: LangSmithAuthConfig) -> dict[str, str]:
    """Build headers for LangSmith API requests."""
    headers = {"x-api-key": auth.api_key}
    if auth.workspace_id:
        headers["x-tenant-id"] = auth.workspace_id
    return headers


def upload_experiment(
    *,
    auth: LangSmithAuthConfig,
    payload: dict[str, Any],
) -> dict[str, Any]:
    """Upload an externally managed experiment to LangSmith."""
    return http_json(
        "POST",
        f"{auth.api_root}/datasets/upload-experiment",
        headers=build_langsmith_headers(auth),
        json_body=payload,
        timeout_seconds=60,
    )


def apply_env_overrides(args: argparse.Namespace) -> None:
    """Apply CLI environment overrides used by the CES runner."""
    if args.project:
        os.environ["GCP_PROJECT_ID"] = args.project
    if args.location:
        os.environ["GCP_LOCATION"] = args.location
    if args.app_id:
        os.environ["CES_APP_ID"] = args.app_id


def run_suites_command(args: argparse.Namespace) -> int:
    """Run one or more CES suites and upload or stage LangSmith experiments."""
    ces_runner = bootstrap_ces_runtime_env(args)
    langsmith_auth = resolve_langsmith_auth_config(args)

    artifacts_root = (
        Path(args.artifacts_dir).resolve()
        if args.artifacts_dir
        else artifacts_root_base() / timestamp_slug()
    )
    artifacts_root.mkdir(parents=True, exist_ok=True)

    upload_requested = args.upload_mode != "never"
    can_upload = upload_requested and bool(langsmith_auth.api_key)
    if args.upload_mode == "always" and not langsmith_auth.api_key:
        raise LangSmithExperimentError(
            "LANGSMITH_API_KEY is required when --upload-mode=always."
        )

    aggregate_results: list[dict[str, Any]] = []
    execution_failures = 0
    evaluation_failures = 0
    skipped_suites = 0

    for suite_value in args.suite:
        suite_path = Path(suite_value).resolve()
        suite_config = load_suite_langsmith_config(suite_path)
        per_suite_artifacts = suite_artifacts_dir(artifacts_root, suite_path)
        per_suite_artifacts.mkdir(parents=True, exist_ok=True)

        print(f"Suite: {suite_path}")
        print(f"Artifacts: {per_suite_artifacts}")
        print(f"LangSmith dataset: {suite_config.dataset_name}")

        suite_status = {
            "suite_path": str(suite_path),
            "artifacts_dir": str(per_suite_artifacts),
            "dataset_name": suite_config.dataset_name,
            "langsmith_workspace_id": langsmith_auth.workspace_id,
            "status": "pending",
        }
        try:
            exit_code = ces_runner.run_suite(
                suite_path,
                artifacts_dir=per_suite_artifacts,
                timeout_seconds=args.timeout_seconds,
                poll_interval_seconds=args.poll_interval_seconds,
            )
            summary_path = per_suite_artifacts / "summary.json"
            summary = load_json_document(summary_path)
            run_resource_wrapper = read_json_if_present(per_suite_artifacts / "03-evaluation-run.json")
            run_resource = (
                run_resource_wrapper.get("evaluation_run")
                if isinstance(run_resource_wrapper, dict)
                and isinstance(run_resource_wrapper.get("evaluation_run"), dict)
                else run_resource_wrapper
            )
            operation_wrapper = read_json_if_present(per_suite_artifacts / "02-operation-complete.json")
            completed_operation = (
                operation_wrapper.get("operation")
                if isinstance(operation_wrapper, dict)
                and isinstance(operation_wrapper.get("operation"), dict)
                else operation_wrapper
            )
            result_payloads_by_name = load_result_artifacts(per_suite_artifacts)
            upload_body = build_langsmith_upload_body(
                suite_path=suite_path,
                suite_config=suite_config,
                summary=summary,
                run_resource=run_resource,
                completed_operation=completed_operation,
                result_payloads_by_name=result_payloads_by_name,
            )

            request_artifact = write_json_artifact(
                per_suite_artifacts / "20-langsmith-upload-request.json",
                upload_body,
            )
            suite_status.update(
                {
                    "ces_exit_code": exit_code,
                    "summary_path": str(summary_path),
                    "upload_request_path": str(request_artifact),
                    "suite_passed": bool(summary.get("suite_passed")),
                    "status": "completed",
                }
            )

            if can_upload:
                print(
                    f"Uploading {summary.get('run_display_name')} to LangSmith via {langsmith_auth.api_root}"
                )
                upload_response = upload_experiment(
                    auth=langsmith_auth,
                    payload=upload_body,
                )
                response_artifact = write_json_artifact(
                    per_suite_artifacts / "21-langsmith-upload-response.json",
                    upload_response,
                )
                suite_status.update(
                    {
                        "upload_mode": "uploaded",
                        "upload_response_path": str(response_artifact),
                        "langsmith_experiment_id": (
                            upload_response.get("experiment", {}) or {}
                        ).get("id"),
                        "langsmith_dataset_id": (
                            upload_response.get("dataset", {}) or {}
                        ).get("id"),
                    }
                )
                print(
                    f"LangSmith upload complete: {suite_status.get('langsmith_experiment_id')}"
                )
            else:
                skip_reason = (
                    "upload disabled (--upload-mode=never)"
                    if args.upload_mode == "never"
                    else "LANGSMITH_API_KEY not configured; request payload only"
                )
                skip_artifact = write_json_artifact(
                    per_suite_artifacts / "21-langsmith-upload-skipped.json",
                    {"status": "skipped", "reason": skip_reason},
                )
                suite_status.update(
                    {
                        "upload_mode": "skipped",
                        "upload_skip_reason": skip_reason,
                        "upload_skip_path": str(skip_artifact),
                    }
                )
                print(f"LangSmith upload skipped: {skip_reason}")

            if exit_code != 0 or not bool(summary.get("suite_passed")):
                evaluation_failures += 1
                suite_status["status"] = "completed_with_evaluation_failures"
                print(
                    f"CES suite reported failures; inspect {summary_path} for details."
                )
        except Exception as exc:
            error_message = str(exc)
            if "missing evaluation resource(s)" in error_message:
                skipped_suites += 1
                suite_status.update(
                    {
                        "status": "skipped_missing_remote_evaluations",
                        "skip_reason": error_message,
                    }
                )
                write_json_artifact(
                    per_suite_artifacts / "90-langsmith-suite-skipped.json",
                    {"status": "skipped", "reason": error_message},
                )
                print(f"SKIP: {error_message}")
            else:
                execution_failures += 1
                suite_status.update({"status": "execution_error", "error": error_message})
                write_json_artifact(
                    per_suite_artifacts / "99-langsmith-live-error.json",
                    {"error": error_message},
                )
                print(f"ERROR: {error_message}")

        aggregate_results.append(suite_status)

    aggregate_summary = {
        "artifacts_root": str(artifacts_root),
        "langsmith_api_root": langsmith_auth.api_root,
        "langsmith_workspace_id": langsmith_auth.workspace_id,
        "upload_mode": args.upload_mode,
        "upload_enabled": can_upload,
        "suite_count": len(aggregate_results),
        "execution_failure_count": execution_failures,
        "evaluation_failure_count": evaluation_failures,
        "skipped_suite_count": skipped_suites,
        "results": aggregate_results,
    }
    write_json_artifact(artifacts_root / "langsmith-live-summary.json", aggregate_summary)
    print(f"Aggregate summary: {artifacts_root / 'langsmith-live-summary.json'}")
    return 0 if execution_failures == 0 else 1


def validate_suite_config_command(args: argparse.Namespace) -> int:
    """Validate LangSmith config embedded in suite files."""
    load_dotenv_if_present()
    for suite_value in args.suite:
        suite_path = Path(suite_value).resolve()
        config = load_suite_langsmith_config(suite_path)
        print(
            f"PASS  {suite_path} -> dataset_name={config.dataset_name}"
        )
    return 0


def main() -> int:
    """CLI entrypoint."""
    args = parse_args()
    if args.command == "run":
        return run_suites_command(args)
    if args.command == "validate-suite-config":
        return validate_suite_config_command(args)
    raise LangSmithExperimentError(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    raise SystemExit(main())
