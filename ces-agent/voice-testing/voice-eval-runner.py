#!/usr/bin/env python3
"""Created by Codex on 2026-03-29.

Minimal voice-input evaluation harness for CES-adjacent testing.
"""

from __future__ import annotations

import argparse
import ast
import base64
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
import wave
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode


DEFAULT_ARTIFACTS_DIRNAME = ".artifacts"
DEFAULT_REMOTE_TIMEOUT_SECONDS = 300
DEFAULT_REMOTE_POLL_INTERVAL_SECONDS = 5
DEFAULT_SESSION_TIMEOUT_SECONDS = 60
DEFAULT_SESSION_AUDIO_CHUNK_MS = 100
DEFAULT_SESSION_ENDPOINTING_SILENCE_MS = 400
DEFAULT_SESSION_INTERTURN_WAIT_BUFFER_MS = 250
DEFAULT_SESSION_INTERTURN_MAX_WAIT_MS = 12000
ANSI_RESET = "\033[0m"
ANSI_BOLD = "\033[1m"
ANSI_GREEN = "\033[32m"
ANSI_RED = "\033[31m"
ANSI_CYAN = "\033[36m"
ANSI_YELLOW = "\033[33m"
ENDPOINTS = {
    "us": "https://ces.us.rep.googleapis.com",
    "eu": "https://ces.eu.rep.googleapis.com",
}
ENV_ASSIGNMENT_PATTERN = re.compile(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$")
AUTO_ENV_LOADED = False


class VoiceEvaluationError(RuntimeError):
    """Raised when the suite definition or local fixture contract is invalid."""


@dataclass(frozen=True)
class SidecarTranscriberSpec:
    """Deterministic transcript provider for local voice fixtures."""

    transcript_path: Path


@dataclass(frozen=True)
class OpenAITranscriberSpec:
    """OpenAI audio transcription provider."""

    model: str
    language: str | None
    prompt: str | None
    api_key_env: str
    endpoint: str


@dataclass(frozen=True)
class VoiceTurn:
    """Resolved local voice turn within an evaluation scenario."""

    name: str
    audio_path: Path
    transcriber: SidecarTranscriberSpec | OpenAITranscriberSpec
    expected_transcript: str


@dataclass(frozen=True)
class VoiceScenario:
    """Resolved local voice evaluation scenario."""

    name: str
    turns: tuple[VoiceTurn, ...]
    linked_evaluation_name: str | None
    linked_evaluation_path: Path | None
    linked_evaluation_kind: str | None
    linked_evaluation_reference_text: str | None


@dataclass(frozen=True)
class VoiceSuite:
    """Resolved suite definition for voice-input evaluation."""

    suite_path: Path
    package_root: Path | None
    display_name: str
    scenarios: tuple[VoiceScenario, ...]


@dataclass(frozen=True)
class RemoteReplayConfig:
    """Configuration for optional remote CES replay of linked evaluations."""

    project: str
    location: str
    app_id: str
    endpoint: str
    mode: str
    timeout_seconds: int
    poll_interval_seconds: int


@dataclass(frozen=True)
class CesSessionConfig:
    """Configuration for direct CES session execution with prerecorded audio."""

    project: str
    location: str
    app_id: str
    deployment_id: str
    endpoint: str
    mode: str
    timeout_seconds: int
    entry_agent: str | None


@dataclass(frozen=True)
class RuntimeAudioPayload:
    """Resolved PCM payload for one CES session turn."""

    audio_bytes: bytes
    audio_encoding: str
    sample_rate_hertz: int
    channel_count: int
    sample_width_bytes: int
    frame_count: int


def supports_color(stream: Any | None = None) -> bool:
    """Return whether ANSI color output should be emitted."""
    target = stream or sys.stdout
    if os.environ.get("NO_COLOR"):
        return False
    isatty = getattr(target, "isatty", None)
    return bool(callable(isatty) and isatty())


def colorize(text: str, color: str, *, bold: bool = False) -> str:
    """Wrap terminal text with ANSI color codes when supported."""
    if not supports_color():
        return text
    prefix = f"{ANSI_BOLD if bold else ''}{color}"
    return f"{prefix}{text}{ANSI_RESET}"


def print_suite_banner(suite_name: str, suite_path: Path, artifacts_root: Path) -> None:
    """Print a smoke-style banner for a voice suite run."""
    banner_width = max(72, len(suite_name) + 28)
    banner = "=" * banner_width
    print(colorize(banner, ANSI_CYAN, bold=True))
    print(
        f"{colorize('Running voice suite:', ANSI_CYAN, bold=True)} "
        f"{colorize(suite_name, ANSI_YELLOW, bold=True)}"
    )
    print(f"Suite path: {suite_path}")
    print(f"Artifacts: {artifacts_root}")
    print(colorize(banner, ANSI_CYAN, bold=True))
    print("")


def format_remote_status(remote_replay_result: dict[str, Any], *, enforced: bool) -> str:
    """Return a human-friendly remote replay status fragment."""
    remote_state = "PASS" if remote_replay_result.get("passed") is True else "FAIL"
    remote_color = ANSI_GREEN if remote_state == "PASS" else ANSI_RED
    remote_prefix = "remote replay" if enforced else "remote replay observed"
    return f"{remote_prefix} {colorize(remote_state, remote_color, bold=True)}"


def format_ces_session_status(ces_session_result: dict[str, Any], *, enforced: bool) -> str:
    """Return a human-friendly direct CES session status fragment."""
    session_state = "PASS" if ces_session_result.get("passed") is True else "FAIL"
    session_color = ANSI_GREEN if session_state == "PASS" else ANSI_RED
    session_prefix = "CES session" if enforced else "CES session observed"
    return f"{session_prefix} {colorize(session_state, session_color, bold=True)}"


def describe_voice_failure(
    scenario: VoiceScenario,
    turn_results: list[dict[str, Any]],
    *,
    linked_evaluation_matches: bool | None,
    ces_session_result: dict[str, Any] | None,
    ces_session_enforced: bool,
    remote_replay_result: dict[str, Any] | None,
    remote_replay_enforced: bool,
) -> str:
    """Summarize the primary reasons a voice scenario failed."""
    details: list[str] = []

    mismatched_turns = [
        turn_result["turn"]
        for turn_result in turn_results
        if not turn_result["transcript_matches_expected"]
    ]
    if mismatched_turns:
        details.append(f"mismatched turns: {', '.join(mismatched_turns)}")

    if linked_evaluation_matches is False:
        details.append("first turn does not match linked evaluation reference")

    if ces_session_result is not None and ces_session_enforced and ces_session_result.get("passed") is not True:
        if ces_session_result.get("error"):
            details.append(f"CES session error: {ces_session_result['error']}")
        else:
            failed_turns = [
                turn_result.get("turn", "unknown")
                for turn_result in ces_session_result.get("turns", [])
                if isinstance(turn_result, dict) and turn_result.get("passed") is False
            ]
            if failed_turns:
                details.append(f"CES session failed turns: {', '.join(failed_turns)}")
            else:
                details.append(format_ces_session_status(ces_session_result, enforced=True))

    if remote_replay_result is not None and remote_replay_enforced and remote_replay_result.get("passed") is not True:
        if remote_replay_result.get("skipped"):
            details.append(str(remote_replay_result.get("reason", "remote replay skipped")))
        elif remote_replay_result.get("error"):
            details.append(f"remote replay error: {remote_replay_result['error']}")
        else:
            details.append(format_remote_status(remote_replay_result, enforced=True))

    if not details:
        details.append(
            f"{sum(1 for turn_result in turn_results if turn_result['transcript_matches_expected'])}/"
            f"{len(scenario.turns)} turns matched"
        )
    return "; ".join(details)


def repo_root() -> Path:
    """Return the repository root."""
    return framework_root().parents[1]


def default_root_env_path() -> Path:
    """Return the repository-level .env path."""
    return repo_root() / ".env"


def default_state_env_path() -> Path:
    """Return the default deployment-discovery env file path when present."""
    return repo_root() / ".tmp" / "cloud-run" / "discovery-plan.env"


def parse_env_file_line(raw_line: str) -> tuple[str, str] | None:
    """Parse a dotenv-style assignment line."""
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
    """Load environment variables from a dotenv-style file if it exists."""
    if not path.is_file():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        parsed = parse_env_file_line(raw_line)
        if parsed is None:
            continue
        key, value = parsed
        os.environ[key] = value


def sync_env_aliases() -> None:
    """Copy common project/location aliases between shell conventions."""
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


def ensure_default_env_loaded() -> None:
    """Load common local env files once so CLI defaults work naturally."""
    global AUTO_ENV_LOADED
    if AUTO_ENV_LOADED:
        sync_env_aliases()
        return
    load_env_file(default_root_env_path())
    load_env_file(default_state_env_path())
    sync_env_aliases()
    AUTO_ENV_LOADED = True


def parse_args() -> argparse.Namespace:
    """Parse CLI arguments."""
    parser = argparse.ArgumentParser(
        description="Run single-turn or multi-turn voice-input evaluations with local fixtures."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    validate_parser = subparsers.add_parser(
        "validate-suite",
        help="Validate a voice suite definition and linked fixtures.",
    )
    validate_parser.add_argument("--suite", required=True, help="Path to the suite JSON.")

    run_parser = subparsers.add_parser(
        "run-suite",
        help="Run a voice suite locally and write artifacts.",
    )
    run_parser.add_argument("--suite", required=True, help="Path to the suite JSON.")
    run_parser.add_argument(
        "--artifacts-dir",
        help=(
            "Optional artifact directory. Defaults to "
            "voice-testing/.artifacts/<timestamp>/"
        ),
    )
    run_parser.add_argument(
        "--remote-replay",
        action="store_true",
        help="Replay each linked evaluation remotely after local transcription succeeds.",
    )
    run_parser.add_argument(
        "--ces-session",
        action="store_true",
        help=(
            "Stream each WAV fixture turn directly to CES BidiRunSession using a shared session per "
            "scenario. This targets the multimodal runtime rather than only transcript-grounded replay."
        ),
    )
    run_parser.add_argument(
        "--ces-session-mode",
        choices=("observe", "assert"),
        default="assert",
        help=(
            "How direct CES session execution affects suite status. 'observe' records the CES session result "
            "without failing the suite, while 'assert' requires each CES session turn to complete successfully. "
            "Default: assert"
        ),
    )
    run_parser.add_argument(
        "--remote-replay-mode",
        choices=("observe", "assert"),
        default="observe",
        help=(
            "How remote CES replay affects suite status. 'observe' records the remote result "
            "without failing the suite, while 'assert' requires the remote evaluation to pass. "
            "Default: observe"
        ),
    )
    run_parser.add_argument("--project", help="Remote CES project. Defaults from GCP_PROJECT_ID.")
    run_parser.add_argument("--location", help="Remote CES location. Defaults from GCP_LOCATION.")
    run_parser.add_argument("--app-id", help="Remote CES app id. Defaults from CES_APP_ID.")
    run_parser.add_argument(
        "--deployment-id",
        help="CES deployment id for direct session execution. Defaults from CES_DEPLOYMENT_ID.",
    )
    run_parser.add_argument(
        "--entry-agent",
        help=(
            "Optional CES entry agent id or full resource name for the first direct session turn. "
            "Defaults from CES_ENTRY_AGENT when set."
        ),
    )
    run_parser.add_argument(
        "--endpoint",
        help="Override the remote CES endpoint. Defaults by location.",
    )
    run_parser.add_argument(
        "--session-timeout-seconds",
        type=int,
        default=DEFAULT_SESSION_TIMEOUT_SECONDS,
        help=f"Direct CES session timeout in seconds. Default: {DEFAULT_SESSION_TIMEOUT_SECONDS}",
    )
    run_parser.add_argument(
        "--remote-timeout-seconds",
        type=int,
        default=DEFAULT_REMOTE_TIMEOUT_SECONDS,
        help=f"Remote CES replay timeout in seconds. Default: {DEFAULT_REMOTE_TIMEOUT_SECONDS}",
    )
    run_parser.add_argument(
        "--remote-poll-interval-seconds",
        type=int,
        default=DEFAULT_REMOTE_POLL_INTERVAL_SECONDS,
        help=(
            "Remote CES replay poll interval in seconds. "
            f"Default: {DEFAULT_REMOTE_POLL_INTERVAL_SECONDS}"
        ),
    )

    return parser.parse_args()


def framework_root() -> Path:
    """Return the voice-testing folder."""
    return Path(__file__).resolve().parent


def default_artifacts_root() -> Path:
    """Return the default artifact directory for a new run."""
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    return framework_root() / DEFAULT_ARTIFACTS_DIRNAME / timestamp


def resolve_path(base_dir: Path, value: str) -> Path:
    """Resolve an absolute or relative path against a base directory."""
    path = Path(value)
    if path.is_absolute():
        return path
    return (base_dir / path).resolve()


def load_json_document(path: Path) -> dict[str, Any]:
    """Load a JSON object from disk."""
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise VoiceEvaluationError(f"JSON document not found: {path}") from exc
    except json.JSONDecodeError as exc:
        raise VoiceEvaluationError(f"Invalid JSON document at {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise VoiceEvaluationError(
            f"Expected JSON object at {path}, got {type(payload).__name__}."
        )
    return payload


def load_text_document(path: Path) -> str:
    """Load a UTF-8 text document from disk."""
    try:
        return path.read_text(encoding="utf-8").strip()
    except FileNotFoundError as exc:
        raise VoiceEvaluationError(f"Text document not found: {path}") from exc


def detect_endpoint(location: str, explicit: str | None) -> str:
    """Resolve the CES endpoint for a location."""
    if explicit:
        return explicit.rstrip("/")
    return ENDPOINTS.get(location, "https://ces.googleapis.com")


def resolve_remote_replay_config(args: argparse.Namespace) -> RemoteReplayConfig | None:
    """Resolve optional remote replay configuration from CLI args and env."""
    if not getattr(args, "remote_replay", False):
        return None

    project = args.project or os.environ.get("GCP_PROJECT_ID")
    location = args.location or os.environ.get("GCP_LOCATION")
    app_id = args.app_id or os.environ.get("CES_APP_ID")

    missing: list[str] = []
    if not project:
        missing.append("project")
    if not location:
        missing.append("location")
    if not app_id:
        missing.append("app-id")
    if missing:
        joined = ", ".join(missing)
        raise VoiceEvaluationError(
            "Remote replay requires the following values via CLI flags or environment: "
            f"{joined}."
        )

    if args.remote_timeout_seconds <= 0:
        raise VoiceEvaluationError("--remote-timeout-seconds must be greater than zero.")
    if args.remote_poll_interval_seconds <= 0:
        raise VoiceEvaluationError("--remote-poll-interval-seconds must be greater than zero.")

    return RemoteReplayConfig(
        project=project,
        location=location,
        app_id=app_id,
        endpoint=detect_endpoint(location, args.endpoint or os.environ.get("CES_ENDPOINT")),
        mode=getattr(args, "remote_replay_mode", "observe"),
        timeout_seconds=args.remote_timeout_seconds,
        poll_interval_seconds=args.remote_poll_interval_seconds,
    )


def resolve_ces_session_config(args: argparse.Namespace) -> CesSessionConfig | None:
    """Resolve optional direct CES session configuration from CLI args and env."""
    if not getattr(args, "ces_session", False):
        return None

    project = getattr(args, "project", None) or os.environ.get("GCP_PROJECT_ID")
    location = getattr(args, "location", None) or os.environ.get("GCP_LOCATION")
    app_id = getattr(args, "app_id", None) or os.environ.get("CES_APP_ID")
    deployment_id = getattr(args, "deployment_id", None) or os.environ.get("CES_DEPLOYMENT_ID")

    missing: list[str] = []
    if not project:
        missing.append("project")
    if not location:
        missing.append("location")
    if not app_id:
        missing.append("app-id")
    if not deployment_id:
        missing.append("deployment-id")
    if missing:
        joined = ", ".join(missing)
        raise VoiceEvaluationError(
            "Direct CES session mode requires the following values via CLI flags or environment: "
            f"{joined}."
        )

    timeout_seconds = getattr(args, "session_timeout_seconds", DEFAULT_SESSION_TIMEOUT_SECONDS)
    if timeout_seconds <= 0:
        raise VoiceEvaluationError("--session-timeout-seconds must be greater than zero.")

    raw_entry_agent = getattr(args, "entry_agent", None) or os.environ.get("CES_ENTRY_AGENT")
    entry_agent = raw_entry_agent.strip() if isinstance(raw_entry_agent, str) and raw_entry_agent.strip() else None

    return CesSessionConfig(
        project=project,
        location=location,
        app_id=app_id,
        deployment_id=deployment_id,
        endpoint=detect_endpoint(location, getattr(args, "endpoint", None) or os.environ.get("CES_ENDPOINT")),
        mode=getattr(args, "ces_session_mode", "assert"),
        timeout_seconds=timeout_seconds,
        entry_agent=entry_agent,
    )


def validate_openai_audio_extension(audio_path: Path) -> None:
    """Validate that the fixture extension is supported by OpenAI audio transcription."""
    supported_extensions = {".flac", ".mp3", ".mp4", ".mpeg", ".mpga", ".m4a", ".ogg", ".wav", ".webm"}
    if audio_path.suffix.casefold() not in supported_extensions:
        raise VoiceEvaluationError(
            f"OpenAI transcription requires one of {sorted(supported_extensions)}, got '{audio_path.suffix}' "
            f"for fixture {audio_path}."
        )


def normalize_text(value: str) -> str:
    """Normalize text for tolerant transcript comparisons."""
    lowered = value.strip().casefold()
    alnum_only = re.sub(r"[^a-z0-9]+", " ", lowered)
    return re.sub(r"\s+", " ", alnum_only).strip()


def extract_first_user_input_text(evaluation_path: Path) -> str:
    """Extract the first user-input text from a linked CES evaluation asset."""
    payload = load_json_document(evaluation_path)
    golden = payload.get("golden")
    if not isinstance(golden, dict):
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must define a 'golden' object."
        )
    turns = golden.get("turns")
    if not isinstance(turns, list) or not turns:
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must define at least one golden turn."
        )
    first_turn = turns[0]
    if not isinstance(first_turn, dict):
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must use object turns."
        )
    steps = first_turn.get("steps")
    if not isinstance(steps, list) or not steps:
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must define at least one golden step."
        )
    for step in steps:
        if not isinstance(step, dict):
            continue
        user_input = step.get("userInput")
        if not isinstance(user_input, dict):
            continue
        text = user_input.get("text")
        if isinstance(text, str) and text.strip():
            return text.strip()
    raise VoiceEvaluationError(
        f"Linked evaluation {evaluation_path} does not contain a userInput.text step."
    )


def extract_scenario_task_text(evaluation_path: Path) -> str:
    """Extract the scenario task text from a linked CES scenario evaluation asset."""
    payload = load_json_document(evaluation_path)
    scenario = payload.get("scenario")
    if not isinstance(scenario, dict):
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must define a 'scenario' object."
        )
    task = scenario.get("task")
    if not isinstance(task, str) or not task.strip():
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} must define a non-empty scenario task."
        )
    return task.strip()


def extract_scenario_start_text(evaluation_path: Path) -> str:
    """Extract the quoted starting user utterance from a scenario evaluation task."""
    task = extract_scenario_task_text(evaluation_path)
    match = re.search(
        r"(?:start with|begin with|beginne mit)\s+[\"'“”‘’](.+?)[\"'“”‘’]",
        task,
        flags=re.IGNORECASE,
    )
    if match is None:
        raise VoiceEvaluationError(
            f"Linked evaluation {evaluation_path} scenario task must contain a quoted starting utterance."
        )
    return match.group(1).strip()


def extract_linked_evaluation_reference(evaluation_path: Path) -> tuple[str, str]:
    """Extract the user utterance that anchors a linked evaluation."""
    payload = load_json_document(evaluation_path)
    if "golden" in payload:
        return "golden", extract_first_user_input_text(evaluation_path)
    if "scenario" in payload:
        return "scenario", extract_scenario_start_text(evaluation_path)
    raise VoiceEvaluationError(
        f"Linked evaluation {evaluation_path} must define either 'golden' or 'scenario'."
    )


def parse_sidecar_transcriber(raw_transcriber: dict[str, Any], *, base_dir: Path) -> SidecarTranscriberSpec:
    """Resolve the sidecar local transcriber contract."""
    transcript_value = raw_transcriber.get("transcript_path")
    if not isinstance(transcript_value, str) or not transcript_value.strip():
        raise VoiceEvaluationError("Sidecar transcriber must define 'transcript_path'.")
    transcript_path = resolve_path(base_dir, transcript_value)
    if not transcript_path.is_file():
        raise VoiceEvaluationError(f"Transcript sidecar not found: {transcript_path}")
    return SidecarTranscriberSpec(transcript_path=transcript_path)


def parse_openai_transcriber(
    raw_transcriber: dict[str, Any],
    *,
    audio_path: Path,
) -> OpenAITranscriberSpec:
    """Resolve the OpenAI transcription contract."""
    validate_openai_audio_extension(audio_path)
    model = raw_transcriber.get("model", "gpt-4o-mini-transcribe")
    if not isinstance(model, str) or not model.strip():
        raise VoiceEvaluationError("OpenAI transcriber must define 'model' as a string when present.")

    language = raw_transcriber.get("language")
    if language is not None and (not isinstance(language, str) or not language.strip()):
        raise VoiceEvaluationError("OpenAI transcriber 'language' must be a non-empty string when present.")

    prompt = raw_transcriber.get("prompt")
    if prompt is not None and not isinstance(prompt, str):
        raise VoiceEvaluationError("OpenAI transcriber 'prompt' must be a string when present.")

    api_key_env = raw_transcriber.get("api_key_env", "OPENAI_API_KEY")
    if not isinstance(api_key_env, str) or not api_key_env.strip():
        raise VoiceEvaluationError("OpenAI transcriber must define 'api_key_env' as a string when present.")

    endpoint = raw_transcriber.get("endpoint", "https://api.openai.com/v1/audio/transcriptions")
    if not isinstance(endpoint, str) or not endpoint.strip():
        raise VoiceEvaluationError("OpenAI transcriber must define 'endpoint' as a string when present.")

    return OpenAITranscriberSpec(
        model=model.strip(),
        language=language.strip() if isinstance(language, str) else None,
        prompt=prompt.strip() if isinstance(prompt, str) else None,
        api_key_env=api_key_env.strip(),
        endpoint=endpoint.rstrip("/"),
    )


def parse_transcriber(
    raw_transcriber: dict[str, Any],
    *,
    base_dir: Path,
    audio_path: Path,
) -> SidecarTranscriberSpec | OpenAITranscriberSpec:
    """Resolve one supported transcriber contract."""
    kind = raw_transcriber.get("kind")
    if kind == "sidecar":
        return parse_sidecar_transcriber(raw_transcriber, base_dir=base_dir)
    if kind == "openai":
        return parse_openai_transcriber(raw_transcriber, audio_path=audio_path)
    raise VoiceEvaluationError(
        f"Unsupported transcriber kind '{kind}'. Supported kinds: sidecar, openai."
    )


def parse_turn(
    raw_turn: dict[str, Any],
    *,
    base_dir: Path,
    scenario_name: str,
    turn_index: int,
) -> VoiceTurn:
    """Resolve one user turn within a voice scenario."""
    turn_name = raw_turn.get("name")
    if turn_name is None:
        resolved_turn_name = f"turn_{turn_index:02d}"
    elif isinstance(turn_name, str) and turn_name.strip():
        resolved_turn_name = turn_name.strip()
    else:
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' turn #{turn_index} must define 'name' as a non-empty string when present."
        )

    audio_value = raw_turn.get("audio_path")
    if not isinstance(audio_value, str) or not audio_value.strip():
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' turn '{resolved_turn_name}' must define 'audio_path'."
        )
    audio_path = resolve_path(base_dir, audio_value)
    if not audio_path.is_file():
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' turn '{resolved_turn_name}' audio fixture not found: {audio_path}"
        )

    raw_transcriber = raw_turn.get("transcriber")
    if not isinstance(raw_transcriber, dict):
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' turn '{resolved_turn_name}' must define a 'transcriber' object."
        )
    transcriber = parse_transcriber(raw_transcriber, base_dir=base_dir, audio_path=audio_path)

    expected_transcript = raw_turn.get("expected_transcript")
    if not isinstance(expected_transcript, str) or not expected_transcript.strip():
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' turn '{resolved_turn_name}' must define 'expected_transcript'."
        )

    return VoiceTurn(
        name=resolved_turn_name,
        audio_path=audio_path,
        transcriber=transcriber,
        expected_transcript=expected_transcript.strip(),
    )


def parse_scenario_turns(
    raw_scenario: dict[str, Any],
    *,
    base_dir: Path,
    scenario_name: str,
) -> tuple[VoiceTurn, ...]:
    """Resolve one or more turns for a scenario, preserving single-turn compatibility."""
    raw_turns = raw_scenario.get("turns")
    if raw_turns is None:
        return (parse_turn(raw_scenario, base_dir=base_dir, scenario_name=scenario_name, turn_index=1),)
    if not isinstance(raw_turns, list) or not raw_turns:
        raise VoiceEvaluationError(
            f"Scenario '{scenario_name}' must define 'turns' as a non-empty array when present."
        )

    turns: list[VoiceTurn] = []
    seen_turn_names: set[str] = set()
    for turn_index, raw_turn in enumerate(raw_turns, start=1):
        if not isinstance(raw_turn, dict):
            raise VoiceEvaluationError(
                f"Scenario '{scenario_name}' turn #{turn_index} must be an object."
            )
        turn = parse_turn(raw_turn, base_dir=base_dir, scenario_name=scenario_name, turn_index=turn_index)
        if turn.name in seen_turn_names:
            raise VoiceEvaluationError(
                f"Scenario '{scenario_name}' uses duplicate turn name '{turn.name}'."
            )
        seen_turn_names.add(turn.name)
        turns.append(turn)
    return tuple(turns)


def transcriber_kind(transcriber: SidecarTranscriberSpec | OpenAITranscriberSpec) -> str:
    """Return the wire-format name for a transcriber."""
    return "sidecar" if isinstance(transcriber, SidecarTranscriberSpec) else "openai"


def transcriber_transcript_path(
    transcriber: SidecarTranscriberSpec | OpenAITranscriberSpec,
) -> str | None:
    """Return the transcript sidecar path when the transcriber is deterministic."""
    if isinstance(transcriber, SidecarTranscriberSpec):
        return str(transcriber.transcript_path)
    return None


def load_suite(path: Path) -> VoiceSuite:
    """Load and validate a voice suite definition."""
    suite_payload = load_json_document(path)
    base_dir = path.parent

    display_name = suite_payload.get("display_name")
    if not isinstance(display_name, str) or not display_name.strip():
        raise VoiceEvaluationError(f"Suite {path} must define a non-empty 'display_name'.")

    package_root: Path | None = None
    raw_package_root = suite_payload.get("package_root")
    if raw_package_root is not None:
        if not isinstance(raw_package_root, str) or not raw_package_root.strip():
            raise VoiceEvaluationError(f"Suite {path} must define 'package_root' as a string.")
        package_root = resolve_path(base_dir, raw_package_root)
        if not package_root.is_dir():
            raise VoiceEvaluationError(f"Package root does not exist: {package_root}")

    raw_scenarios = suite_payload.get("scenarios")
    if not isinstance(raw_scenarios, list) or not raw_scenarios:
        raise VoiceEvaluationError(f"Suite {path} must define a non-empty 'scenarios' array.")

    scenarios: list[VoiceScenario] = []
    seen_names: set[str] = set()
    for index, raw_scenario in enumerate(raw_scenarios, start=1):
        if not isinstance(raw_scenario, dict):
            raise VoiceEvaluationError(f"Suite {path} scenario #{index} must be an object.")

        name = raw_scenario.get("name")
        if not isinstance(name, str) or not name.strip():
            raise VoiceEvaluationError(f"Suite {path} scenario #{index} must define a non-empty 'name'.")
        if name in seen_names:
            raise VoiceEvaluationError(f"Suite {path} references duplicate scenario '{name}'.")
        seen_names.add(name)

        turns = parse_scenario_turns(raw_scenario, base_dir=base_dir, scenario_name=name)

        linked_evaluation_name: str | None = None
        linked_evaluation_path: Path | None = None
        linked_evaluation_kind: str | None = None
        linked_evaluation_reference_text: str | None = None
        raw_linked_evaluation = raw_scenario.get("linked_evaluation")
        if raw_linked_evaluation is not None:
            if package_root is None:
                raise VoiceEvaluationError(
                    f"Scenario '{name}' defines 'linked_evaluation' but the suite has no 'package_root'."
                )
            if not isinstance(raw_linked_evaluation, str) or not raw_linked_evaluation.strip():
                raise VoiceEvaluationError(
                    f"Scenario '{name}' must define 'linked_evaluation' as a string."
                )
            linked_evaluation_name = raw_linked_evaluation.strip()
            linked_evaluation_path = (
                package_root
                / "evaluations"
                / linked_evaluation_name
                / f"{linked_evaluation_name}.json"
            )
            if not linked_evaluation_path.is_file():
                raise VoiceEvaluationError(
                    f"Scenario '{name}' linked evaluation not found: {linked_evaluation_path}"
                )

            linked_evaluation_kind, linked_evaluation_reference_text = extract_linked_evaluation_reference(
                linked_evaluation_path
            )
            if normalize_text(linked_evaluation_reference_text) != normalize_text(turns[0].expected_transcript):
                raise VoiceEvaluationError(
                    f"Scenario '{name}' first-turn expected transcript does not match linked evaluation "
                    f"reference text.\nExpected transcript: {turns[0].expected_transcript}\n"
                    f"Linked evaluation text: {linked_evaluation_reference_text}"
                )

        scenarios.append(
            VoiceScenario(
                name=name.strip(),
                turns=turns,
                linked_evaluation_name=linked_evaluation_name,
                linked_evaluation_path=linked_evaluation_path,
                linked_evaluation_kind=linked_evaluation_kind,
                linked_evaluation_reference_text=linked_evaluation_reference_text,
            )
        )

    return VoiceSuite(
        suite_path=path.resolve(),
        package_root=package_root,
        display_name=display_name.strip(),
        scenarios=tuple(scenarios),
    )


def transcribe_turn(turn: VoiceTurn) -> str:
    """Resolve a transcript for one user turn."""
    if isinstance(turn.transcriber, SidecarTranscriberSpec):
        return load_text_document(turn.transcriber.transcript_path)
    if isinstance(turn.transcriber, OpenAITranscriberSpec):
        return transcribe_with_openai(turn.audio_path, turn.transcriber)
    raise VoiceEvaluationError(f"Unsupported transcriber type for turn '{turn.name}'.")


def transcribe_with_openai(audio_path: Path, spec: OpenAITranscriberSpec) -> str:
    """Transcribe a local audio file through the OpenAI audio transcription API."""
    api_key = os.environ.get(spec.api_key_env)
    if not api_key:
        raise VoiceEvaluationError(
            f"OpenAI transcription requested for {audio_path} but env var '{spec.api_key_env}' is missing."
        )

    curl_command = [
        "curl",
        "--silent",
        "--show-error",
        spec.endpoint,
        "-H",
        f"Authorization: Bearer {api_key}",
        "-F",
        f"file=@{audio_path}",
        "-F",
        f"model={spec.model}",
        "-w",
        "\n%{http_code}",
    ]
    if spec.language:
        curl_command.extend(["-F", f"language={spec.language}"])
    if spec.prompt:
        curl_command.extend(["-F", f"prompt={spec.prompt}"])

    completed = subprocess.run(
        curl_command,
        check=False,
        capture_output=True,
        text=True,
    )
    stderr = completed.stderr.strip()
    stdout = completed.stdout.strip()
    if not stdout:
        detail = stderr or "empty response"
        raise VoiceEvaluationError(
            f"OpenAI transcription failed for {audio_path}: {detail}"
        )

    response_body, _, status_text = stdout.rpartition("\n")
    if not status_text.isdigit():
        raise VoiceEvaluationError(
            f"OpenAI transcription returned an unparseable HTTP status for {audio_path}: {stdout}"
        )
    status_code = int(status_text)
    if status_code >= 400:
        detail = response_body.strip() or stderr or f"HTTP {status_code}"
        raise VoiceEvaluationError(
            f"OpenAI transcription failed for {audio_path} with HTTP {status_code}: {detail}"
        )

    try:
        payload = json.loads(response_body)
    except json.JSONDecodeError as exc:
        raise VoiceEvaluationError(
            f"OpenAI transcription returned invalid JSON for {audio_path}."
        ) from exc
    text = payload.get("text")
    if not isinstance(text, str) or not text.strip():
        raise VoiceEvaluationError(
            f"OpenAI transcription response for {audio_path} did not include a non-empty 'text' field."
        )
    return text.strip()


def maybe_parse_json(text: str) -> Any | None:
    """Return parsed JSON when possible, else None."""
    if not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return None


def http_request_raw(
    method: str,
    url: str,
    *,
    token: str | None = None,
    json_body: Any | None = None,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Execute a JSON-oriented HTTP request against CES."""
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
        raise VoiceEvaluationError(f"Network error calling {url}: {exc.reason}") from exc


def http_json(
    method: str,
    url: str,
    *,
    token: str | None = None,
    body: dict[str, Any] | None = None,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Execute a JSON request and return the parsed response body."""
    response = http_request_raw(
        method,
        url,
        token=token,
        json_body=body,
        timeout_seconds=timeout_seconds,
    )
    status = response["status"]
    if not 200 <= status < 300:
        parsed = response.get("json")
        message = response.get("body", "")[:500]
        if isinstance(parsed, dict):
            message = parsed.get("error", {}).get("message", message)
        raise VoiceEvaluationError(f"HTTP {status} from {url}: {message}")
    parsed = response.get("json")
    if isinstance(parsed, dict):
        return parsed
    raise VoiceEvaluationError(f"Expected JSON response from {url}, got non-JSON body.")


def get_access_token() -> str:
    """Return a Google Cloud access token from the local gcloud auth context."""
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
    raise VoiceEvaluationError(
        "Failed to obtain a Google Cloud access token. Run 'gcloud auth login' or "
        "'gcloud auth application-default login' first."
    )


def app_parent(project: str, location: str, app_id: str) -> str:
    """Return the CES app parent resource name."""
    return f"projects/{project}/locations/{location}/apps/{app_id}"


def deployment_resource_name(config: CesSessionConfig) -> str:
    """Return the full deployment resource name for a CES session config."""
    if config.deployment_id.startswith("projects/"):
        return config.deployment_id
    return f"{app_parent(config.project, config.location, config.app_id)}/deployments/{config.deployment_id}"


def entry_agent_resource_name(config: CesSessionConfig, entry_agent: str) -> str:
    """Return the full entry-agent resource name for a CES session config."""
    if entry_agent.startswith("projects/"):
        return entry_agent
    return f"{app_parent(config.project, config.location, config.app_id)}/agents/{entry_agent}"


def create_session_resource_name(config: CesSessionConfig, scenario_name: str) -> str:
    """Create a unique CES session resource for one scenario run."""
    slug = re.sub(r"[^a-z0-9]+", "-", scenario_name.casefold()).strip("-") or "voice"
    suffix = uuid.uuid4().hex[:12]
    max_slug_len = max(1, 63 - len("voice--") - len(suffix))
    trimmed_slug = slug[:max_slug_len].rstrip("-") or "voice"
    session_id = f"voice-{trimmed_slug}-{suffix}"
    return f"{app_parent(config.project, config.location, config.app_id)}/sessions/{session_id}"


def bidi_session_url(config: CesSessionConfig) -> str:
    """Return the websocket endpoint for CES BidiRunSession."""
    endpoint = config.endpoint.rstrip("/")
    if endpoint.startswith("https://"):
        websocket_host = endpoint[len("https://") :]
        websocket_endpoint = f"wss://{websocket_host}"
    elif endpoint.startswith("http://"):
        websocket_host = endpoint[len("http://") :]
        websocket_endpoint = f"ws://{websocket_host}"
    elif endpoint.startswith("wss://") or endpoint.startswith("ws://"):
        websocket_endpoint = endpoint
        websocket_host = endpoint.split("://", 1)[1]
    else:
        raise VoiceEvaluationError(
            "CES endpoint must begin with http://, https://, ws://, or wss:// for direct session mode. "
            f"Got '{config.endpoint}'."
        )

    if websocket_host in {"ces.us.rep.googleapis.com", "ces.eu.rep.googleapis.com"}:
        websocket_endpoint = "wss://ces.googleapis.com"

    return (
        f"{websocket_endpoint}/ws/google.cloud.ces.v1.SessionService/"
        f"BidiRunSession/locations/{config.location}"
    )


def bidi_session_headers(config: CesSessionConfig, *, token: str) -> dict[str, str]:
    """Return websocket headers for CES BidiRunSession."""
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }

    endpoint = config.endpoint.rstrip("/")
    websocket_host = endpoint.split("://", 1)[1] if "://" in endpoint else endpoint
    if websocket_host in {"ces.googleapis.com", "ces.us.rep.googleapis.com", "ces.eu.rep.googleapis.com"}:
        headers["x-goog-request-params"] = f"location=locations/{config.location}"
    return headers


def get_bidi_websocket_connect() -> Any:
    """Return the optional sync websocket client used for direct CES audio sessions."""
    try:
        from websockets.sync.client import connect
    except ImportError as exc:
        raise VoiceEvaluationError(
            "Direct CES session mode requires the optional 'websockets' package. "
            "Install the dependencies from ces-agent/voice-testing/requirements.txt first."
        ) from exc
    return connect


def load_session_audio_payloads(scenario: VoiceScenario) -> tuple[RuntimeAudioPayload, ...]:
    """Load and validate the prerecorded audio payloads for one scenario."""
    payloads: list[RuntimeAudioPayload] = []
    expected_format: tuple[str, int, int, int] | None = None

    for turn in scenario.turns:
        payload = load_runtime_audio_payload(turn.audio_path)
        audio_format = (
            payload.audio_encoding,
            payload.sample_rate_hertz,
            payload.channel_count,
            payload.sample_width_bytes,
        )
        if expected_format is None:
            expected_format = audio_format
        elif audio_format != expected_format:
            raise VoiceEvaluationError(
                "Direct CES session mode requires all turns in a scenario to share the same WAV format. "
                f"Turn '{turn.name}' uses {payload.sample_rate_hertz} Hz/{payload.channel_count}ch/"
                f"{payload.sample_width_bytes * 8}-bit while the scenario started with "
                f"{expected_format[1]} Hz/{expected_format[2]}ch/{expected_format[3] * 8}-bit."
            )
        payloads.append(payload)

    return tuple(payloads)


def ces_audio_config(audio_payload: RuntimeAudioPayload) -> dict[str, Any]:
    """Return the CES audio config payload for a prerecorded WAV fixture."""
    return {
        "audioEncoding": audio_payload.audio_encoding,
        "sampleRateHertz": audio_payload.sample_rate_hertz,
    }


def create_bidi_session_config(
    config: CesSessionConfig,
    *,
    session_resource: str,
    audio_payload: RuntimeAudioPayload,
) -> dict[str, Any]:
    """Return the initial websocket config message for CES BidiRunSession."""
    session_config: dict[str, Any] = {
        "session": session_resource,
        "deployment": deployment_resource_name(config),
        "inputAudioConfig": ces_audio_config(audio_payload),
        "outputAudioConfig": ces_audio_config(audio_payload),
    }
    if config.entry_agent:
        session_config["entryAgent"] = entry_agent_resource_name(config, config.entry_agent)
    return {"config": session_config}


def iter_realtime_audio_messages(audio_payload: RuntimeAudioPayload) -> tuple[str, ...]:
    """Encode one prerecorded WAV fixture as websocket realtime-input messages."""
    frame_size = audio_payload.channel_count * audio_payload.sample_width_bytes
    chunk_frames = max(1, audio_payload.sample_rate_hertz * DEFAULT_SESSION_AUDIO_CHUNK_MS // 1000)
    chunk_size_bytes = chunk_frames * frame_size
    endpoint_silence_frames = max(
        1,
        audio_payload.sample_rate_hertz * DEFAULT_SESSION_ENDPOINTING_SILENCE_MS // 1000,
    )
    endpoint_silence_bytes = b"\x00" * (endpoint_silence_frames * frame_size)
    audio_bytes = audio_payload.audio_bytes + endpoint_silence_bytes

    messages: list[str] = []
    for offset in range(0, len(audio_bytes), chunk_size_bytes):
        chunk = audio_bytes[offset : offset + chunk_size_bytes]
        messages.append(
            json.dumps(
                {
                    "realtimeInput": {
                        "audio": base64.b64encode(chunk).decode("ascii"),
                    }
                }
            )
        )
    return tuple(messages)


def send_bidi_audio_turn(connection: Any, audio_payload: RuntimeAudioPayload) -> dict[str, Any]:
    """Stream one prerecorded audio turn to CES over the websocket session."""
    frame_size = audio_payload.channel_count * audio_payload.sample_width_bytes
    sent_message_count = 0
    sent_audio_bytes = 0

    for message in iter_realtime_audio_messages(audio_payload):
        try:
            connection.send(message)
        except Exception as exc:  # pragma: no cover - websocket library specifics
            raise VoiceEvaluationError(f"Failed to send realtime CES audio chunk: {exc}") from exc
        sent_message_count += 1

        parsed_message = json.loads(message)
        realtime_input = parsed_message.get("realtimeInput", {})
        raw_audio = realtime_input.get("audio")
        if isinstance(raw_audio, str):
            try:
                sent_audio_bytes += len(base64.b64decode(raw_audio))
            except (ValueError, TypeError):
                pass

    return {
        "chunk_count": sent_message_count,
        "sent_audio_bytes": sent_audio_bytes,
        "sent_frame_count": sent_audio_bytes // frame_size,
        "endpointing_silence_ms": DEFAULT_SESSION_ENDPOINTING_SILENCE_MS,
        "chunk_duration_ms": DEFAULT_SESSION_AUDIO_CHUNK_MS,
    }


def estimate_audio_playback_seconds(
    audio_output_bytes: int,
    *,
    sample_rate_hertz: int,
    channel_count: int,
    sample_width_bytes: int,
) -> float:
    """Estimate playback duration for CES audio output bytes."""
    if audio_output_bytes <= 0:
        return 0.0

    bytes_per_second = sample_rate_hertz * channel_count * sample_width_bytes
    if bytes_per_second <= 0:
        return 0.0

    return audio_output_bytes / bytes_per_second


def interturn_wait_seconds(turn_summary: dict[str, Any]) -> float:
    """Return how long the runner should wait before sending the next user turn."""
    audio_output_bytes = turn_summary.get("audio_output_bytes")
    sample_rate_hertz = turn_summary.get("sample_rate_hertz")
    channel_count = turn_summary.get("channel_count")
    sample_width_bytes = turn_summary.get("sample_width_bytes")
    if not all(isinstance(value, int) for value in (audio_output_bytes, sample_rate_hertz, channel_count, sample_width_bytes)):
        return 0.0

    playback_seconds = estimate_audio_playback_seconds(
        audio_output_bytes,
        sample_rate_hertz=sample_rate_hertz,
        channel_count=channel_count,
        sample_width_bytes=sample_width_bytes,
    )
    if playback_seconds <= 0:
        return 0.0

    capped_wait_seconds = min(
        playback_seconds + (DEFAULT_SESSION_INTERTURN_WAIT_BUFFER_MS / 1000),
        DEFAULT_SESSION_INTERTURN_MAX_WAIT_MS / 1000,
    )
    return capped_wait_seconds


def sanitize_bidi_server_message(message: dict[str, Any]) -> dict[str, Any]:
    """Remove large binary payloads from websocket artifacts while keeping debug context."""
    sanitized = json.loads(json.dumps(message))
    session_output = sanitized.get("sessionOutput")
    if isinstance(session_output, dict) and isinstance(session_output.get("audio"), str):
        session_output["audioBase64Length"] = len(session_output["audio"])
        session_output.pop("audio", None)
    return sanitized


def collect_bidi_turn_messages(
    connection: Any,
    *,
    turn_name: str,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Collect the CES websocket responses for one streamed audio turn."""
    deadline = time.monotonic() + timeout_seconds
    recognition_updates: list[str] = []
    output_texts: list[str] = []
    sanitized_messages: list[dict[str, Any]] = []
    session_output_count = 0
    audio_output_count = 0
    audio_output_bytes = 0
    tool_call_count = 0
    interruption_count = 0
    reported_turn_indexes: list[int] = []
    turn_completed = False
    session_ended = False
    end_session_payload: dict[str, Any] | None = None

    while True:
        remaining_seconds = deadline - time.monotonic()
        if remaining_seconds <= 0:
            raise VoiceEvaluationError(
                f"Timed out waiting for CES BidiRunSession output for turn '{turn_name}' after {timeout_seconds}s."
            )

        try:
            raw_message = connection.recv(timeout=remaining_seconds)
        except TimeoutError as exc:
            raise VoiceEvaluationError(
                f"Timed out waiting for CES BidiRunSession output for turn '{turn_name}' after {timeout_seconds}s."
            ) from exc
        except Exception as exc:  # pragma: no cover - websocket library specifics
            raise VoiceEvaluationError(
                f"CES BidiRunSession closed or errored while processing turn '{turn_name}': {exc}"
            ) from exc

        if isinstance(raw_message, bytes):
            raw_message = raw_message.decode("utf-8")
        if not isinstance(raw_message, str):
            raise VoiceEvaluationError(
                f"CES BidiRunSession returned a non-text websocket frame for turn '{turn_name}'."
            )

        message = maybe_parse_json(raw_message)
        if not isinstance(message, dict):
            raise VoiceEvaluationError(
                f"CES BidiRunSession returned non-JSON data for turn '{turn_name}': {raw_message[:200]}"
            )
        sanitized_messages.append(sanitize_bidi_server_message(message))

        recognition_result = message.get("recognitionResult")
        if isinstance(recognition_result, dict):
            transcript = recognition_result.get("transcript")
            if isinstance(transcript, str) and transcript.strip():
                recognition_updates.append(transcript.strip())

        interruption_signal = message.get("interruptionSignal")
        if isinstance(interruption_signal, dict):
            interruption_count += 1

        session_output = message.get("sessionOutput")
        if isinstance(session_output, dict):
            session_output_count += 1
            reported_turn_index = session_output.get("turnIndex")
            if isinstance(reported_turn_index, int):
                reported_turn_indexes.append(reported_turn_index)

            output_text = session_output.get("text")
            if isinstance(output_text, str) and output_text.strip():
                output_texts.append(output_text.strip())

            output_audio = session_output.get("audio")
            if isinstance(output_audio, str) and output_audio:
                audio_output_count += 1
                try:
                    audio_output_bytes += len(base64.b64decode(output_audio))
                except (ValueError, TypeError):
                    pass

            tool_calls = session_output.get("toolCalls")
            if isinstance(tool_calls, dict):
                raw_tool_calls = tool_calls.get("toolCalls")
                if isinstance(raw_tool_calls, list):
                    tool_call_count += len(raw_tool_calls)

            embedded_end_session = session_output.get("endSession")
            if isinstance(embedded_end_session, dict):
                session_ended = True
                end_session_payload = embedded_end_session

            if session_output.get("turnCompleted") is True:
                turn_completed = True
                break

        top_level_end_session = message.get("endSession")
        if isinstance(top_level_end_session, dict):
            session_ended = True
            end_session_payload = top_level_end_session
            break

        if "goAway" in message:
            raise VoiceEvaluationError(
                "CES BidiRunSession requested a reconnect via GoAway. "
                f"This runner does not yet resume streamed audio mid-scenario for turn '{turn_name}'."
            )

    return {
        "recognition_updates": recognition_updates,
        "recognition_transcript": recognition_updates[-1] if recognition_updates else None,
        "output_texts": output_texts,
        "session_output_count": session_output_count,
        "audio_output_count": audio_output_count,
        "audio_output_bytes": audio_output_bytes,
        "tool_call_count": tool_call_count,
        "interruption_count": interruption_count,
        "reported_turn_indexes": reported_turn_indexes,
        "turn_completed": turn_completed,
        "session_ended": session_ended,
        "end_session": end_session_payload,
        "messages": sanitized_messages,
        "passed": turn_completed or session_ended,
    }


def load_runtime_audio_payload(audio_path: Path) -> RuntimeAudioPayload:
    """Load a PCM WAV fixture into a direct CES session payload."""
    if audio_path.suffix.casefold() != ".wav":
        raise VoiceEvaluationError(
            "Direct CES session mode currently supports PCM WAV fixtures only. "
            f"Convert fixture '{audio_path}' to .wav first."
        )

    try:
        with wave.open(str(audio_path), "rb") as handle:
            channel_count = handle.getnchannels()
            sample_width_bytes = handle.getsampwidth()
            sample_rate_hertz = handle.getframerate()
            audio_bytes = handle.readframes(10**9)
    except (FileNotFoundError, wave.Error, EOFError) as exc:
        raise VoiceEvaluationError(
            f"Unable to load PCM WAV audio fixture '{audio_path}': {exc}"
        ) from exc

    if channel_count != 1:
        raise VoiceEvaluationError(
            f"Direct CES session mode currently expects mono WAV fixtures. Got {channel_count} channels for {audio_path}."
        )
    if sample_width_bytes != 2:
        raise VoiceEvaluationError(
            f"Direct CES session mode currently expects 16-bit PCM WAV fixtures. Got sample width {sample_width_bytes} bytes for {audio_path}."
        )
    if sample_rate_hertz not in {8000, 16000, 24000}:
        raise VoiceEvaluationError(
            "Direct CES session mode currently expects WAV fixtures at 8000, 16000, or 24000 Hz. "
            f"Got {sample_rate_hertz} Hz for {audio_path}."
        )
    if not audio_bytes:
        raise VoiceEvaluationError(f"Audio fixture '{audio_path}' produced no PCM frames.")

    frame_count = len(audio_bytes) // (channel_count * sample_width_bytes)
    return RuntimeAudioPayload(
        audio_bytes=audio_bytes,
        audio_encoding="LINEAR16",
        sample_rate_hertz=sample_rate_hertz,
        channel_count=channel_count,
        sample_width_bytes=sample_width_bytes,
        frame_count=frame_count,
    )


def summarize_ces_session(
    config: CesSessionConfig,
    *,
    token: str,
    scenario: VoiceScenario,
) -> dict[str, Any]:
    """Execute a CES runtime session directly from prerecorded audio fixtures."""
    config = preflight_ces_session_target(config, token=token)
    session_resource = create_session_resource_name(config, scenario.name)
    websocket_url = bidi_session_url(config)
    audio_payloads = load_session_audio_payloads(scenario)
    turn_summaries: list[dict[str, Any]] = []
    websocket_connect = get_bidi_websocket_connect()

    try:
        with websocket_connect(
            websocket_url,
            additional_headers=bidi_session_headers(config, token=token),
            compression=None,
            open_timeout=config.timeout_seconds,
            close_timeout=config.timeout_seconds,
            max_size=None,
            proxy=None,
        ) as connection:
            connection.send(
                json.dumps(
                    create_bidi_session_config(
                        config,
                        session_resource=session_resource,
                        audio_payload=audio_payloads[0],
                    )
                )
            )

            for turn_index, (turn, audio_payload) in enumerate(zip(scenario.turns, audio_payloads), start=1):
                send_summary = send_bidi_audio_turn(connection, audio_payload)
                turn_summary = collect_bidi_turn_messages(
                    connection,
                    turn_name=turn.name,
                    timeout_seconds=config.timeout_seconds,
                )
                turn_summary.update(
                    {
                        "turn": turn.name,
                        "turn_index": turn_index,
                        "audio_path": str(turn.audio_path),
                        "audio_encoding": audio_payload.audio_encoding,
                        "sample_rate_hertz": audio_payload.sample_rate_hertz,
                        "channel_count": audio_payload.channel_count,
                        "sample_width_bytes": audio_payload.sample_width_bytes,
                        "frame_count": audio_payload.frame_count,
                        **send_summary,
                    }
                )
                turn_summary["playback_wait_seconds"] = interturn_wait_seconds(turn_summary)
                turn_summaries.append(turn_summary)

                if turn_summary.get("passed") is not True:
                    return {
                        "requested": True,
                        "passed": False,
                        "mode": config.mode,
                        "project": config.project,
                        "location": config.location,
                        "app_id": config.app_id,
                        "deployment_id": config.deployment_id,
                        "endpoint": config.endpoint,
                        "transport": "BidiRunSession",
                        "websocket_url": websocket_url,
                        "session": session_resource,
                        "error": f"CES BidiRunSession did not complete turn '{turn.name}'.",
                        "turns": turn_summaries,
                    }

                if turn_summary.get("session_ended") and turn_index < len(scenario.turns):
                    return {
                        "requested": True,
                        "passed": False,
                        "mode": config.mode,
                        "project": config.project,
                        "location": config.location,
                        "app_id": config.app_id,
                        "deployment_id": config.deployment_id,
                        "endpoint": config.endpoint,
                        "transport": "BidiRunSession",
                        "websocket_url": websocket_url,
                        "session": session_resource,
                        "error": (
                            "CES ended the session before all prerecorded turns were sent. "
                            f"Session ended during turn '{turn.name}'."
                        ),
                        "turns": turn_summaries,
                    }

                if turn_index < len(scenario.turns):
                    wait_seconds = turn_summary.get("playback_wait_seconds", 0.0)
                    if isinstance(wait_seconds, (int, float)) and wait_seconds > 0:
                        time.sleep(wait_seconds)
    except VoiceEvaluationError as exc:
        return {
            "requested": True,
            "passed": False,
            "mode": config.mode,
            "project": config.project,
            "location": config.location,
            "app_id": config.app_id,
            "deployment_id": config.deployment_id,
            "endpoint": config.endpoint,
            "transport": "BidiRunSession",
            "websocket_url": websocket_url,
            "session": session_resource,
            "error": str(exc),
            "turns": turn_summaries,
        }
    except Exception as exc:  # pragma: no cover - websocket library specifics
        return {
            "requested": True,
            "passed": False,
            "mode": config.mode,
            "project": config.project,
            "location": config.location,
            "app_id": config.app_id,
            "deployment_id": config.deployment_id,
            "endpoint": config.endpoint,
            "transport": "BidiRunSession",
            "websocket_url": websocket_url,
            "session": session_resource,
            "error": f"CES BidiRunSession failed: {exc}",
            "turns": turn_summaries,
        }

    return {
        "requested": True,
        "passed": all(turn_summary.get("passed") is True for turn_summary in turn_summaries),
        "mode": config.mode,
        "project": config.project,
        "location": config.location,
        "app_id": config.app_id,
        "deployment_id": config.deployment_id,
        "endpoint": config.endpoint,
        "transport": "BidiRunSession",
        "websocket_url": websocket_url,
        "session": session_resource,
        "turns": turn_summaries,
    }


def get_resource(
    endpoint: str,
    token: str,
    resource_name: str,
    *,
    timeout_seconds: int,
) -> dict[str, Any]:
    """Fetch a CES resource by full resource name."""
    url = f"{endpoint}/v1beta/{resource_name}"
    return http_json("GET", url, token=token, timeout_seconds=timeout_seconds)


def list_collection(
    endpoint: str,
    token: str,
    parent: str,
    collection_name: str,
    response_key: str,
    *,
    timeout_seconds: int,
    query: dict[str, Any] | None = None,
) -> list[dict[str, Any]]:
    """List a CES collection, following pagination when necessary."""
    items: list[dict[str, Any]] = []
    next_page_token: str | None = None
    while True:
        query_params = dict(query or {})
        if next_page_token:
            query_params["pageToken"] = next_page_token
        query_string = f"?{urlencode(query_params, doseq=True)}" if query_params else ""
        url = f"{endpoint}/v1beta/{parent}/{collection_name}{query_string}"
        payload = http_json("GET", url, token=token, timeout_seconds=timeout_seconds)
        batch = payload.get(response_key, [])
        if not isinstance(batch, list):
            raise VoiceEvaluationError(
                f"Expected list response for {parent}/{collection_name}, got {type(batch).__name__}."
            )
        items.extend(item for item in batch if isinstance(item, dict))
        next_page_token = payload.get("nextPageToken")
        if not isinstance(next_page_token, str) or not next_page_token:
            break
    return items


def resource_id_from_name(resource_name: str) -> str:
    """Return the trailing resource identifier from a full CES resource name."""
    return resource_name.rsplit("/", 1)[-1] if resource_name else ""


def response_error_message(response: dict[str, Any]) -> str:
    """Extract a useful error message from an HTTP response helper payload."""
    parsed = response.get("json")
    if isinstance(parsed, dict):
        error = parsed.get("error")
        if isinstance(error, dict):
            message = error.get("message")
            if isinstance(message, str) and message.strip():
                return message.strip()
    body = response.get("body")
    if isinstance(body, str) and body.strip():
        return body.strip()[:500]
    return "Unknown CES API error"


def format_named_resources(resources: list[dict[str, Any]]) -> str:
    """Format resource identifiers and display names for troubleshooting messages."""
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


def resolve_entry_agent_name(
    config: CesSessionConfig,
    *,
    token: str,
    app_name: str,
) -> str | None:
    """Resolve an optional entry agent by resource name, id suffix, or displayName."""
    if not config.entry_agent:
        return None
    if config.entry_agent.startswith("projects/"):
        return config.entry_agent

    agents = list_collection(
        config.endpoint,
        token,
        app_name,
        "agents",
        "agents",
        timeout_seconds=config.timeout_seconds,
    )

    matches: list[str] = []
    for agent in agents:
        name = agent.get("name")
        if not isinstance(name, str) or not name:
            continue
        display_name = agent.get("displayName")
        if resource_id_from_name(name) == config.entry_agent:
            matches.append(name)
            continue
        if isinstance(display_name, str) and display_name.strip() == config.entry_agent:
            matches.append(name)

    unique_matches = list(dict.fromkeys(matches))
    if len(unique_matches) == 1:
        return unique_matches[0]
    if len(unique_matches) > 1:
        raise VoiceEvaluationError(
            "Configured CES entry agent matches multiple remote agents. "
            f"Please use a full resource name. Matches: {', '.join(unique_matches)}"
        )

    raise VoiceEvaluationError(
        f"Configured CES entry agent '{config.entry_agent}' was not found under app '{config.app_id}'. "
        f"Available agents: {format_named_resources(agents)}"
    )


def preflight_ces_session_target(config: CesSessionConfig, *, token: str) -> CesSessionConfig:
    """Validate CES app/deployment references before opening a websocket session."""
    app_name = app_parent(config.project, config.location, config.app_id)
    app_url = f"{config.endpoint}/v1beta/{app_name}"
    app_response = http_request_raw("GET", app_url, token=token, timeout_seconds=config.timeout_seconds)

    if app_response["status"] == 404:
        apps = list_collection(
            config.endpoint,
            token,
            f"projects/{config.project}/locations/{config.location}",
            "apps",
            "apps",
            timeout_seconds=config.timeout_seconds,
        )
        hint = ""
        available_app_ids = {
            resource_id_from_name(resource.get("name", ""))
            for resource in apps
            if isinstance(resource.get("name"), str)
        }
        requested_deployment_id = resource_id_from_name(config.deployment_id)
        if config.deployment_id in available_app_ids or requested_deployment_id in available_app_ids:
            hint = (
                f" CES_DEPLOYMENT_ID '{config.deployment_id}' matches an existing app ID, "
                "which usually means CES_APP_ID and CES_DEPLOYMENT_ID were swapped."
            )
        raise VoiceEvaluationError(
            f"Configured CES_APP_ID '{config.app_id}' was not found under project '{config.project}' "
            f"in location '{config.location}'. Available apps: {format_named_resources(apps)}.{hint}"
        )

    if not 200 <= app_response["status"] < 300:
        raise VoiceEvaluationError(
            f"Unable to verify CES app '{config.app_id}': {response_error_message(app_response)}"
        )

    deployments = list_collection(
        config.endpoint,
        token,
        app_name,
        "deployments",
        "deployments",
        timeout_seconds=config.timeout_seconds,
    )
    if not deployments:
        hint = ""
        requested_deployment_id = resource_id_from_name(config.deployment_id)
        if config.deployment_id == config.app_id or requested_deployment_id == config.app_id:
            hint = " CES_DEPLOYMENT_ID currently matches CES_APP_ID, which indicates the app ID was copied into the deployment slot."
        raise VoiceEvaluationError(
            f"No API access deployments were found under app '{config.app_id}'. "
            "Create one in the CES console via Deploy → API access, then set CES_DEPLOYMENT_ID to that channel/deployment ID." + hint
        )

    deployment_name = deployment_resource_name(config)
    requested_deployment_id = resource_id_from_name(config.deployment_id)
    deployment_matches = []
    for deployment in deployments:
        name = deployment.get("name")
        if not isinstance(name, str) or not name:
            continue
        deployment_id = resource_id_from_name(name)
        display_name = deployment.get("displayName")
        if (
            name == deployment_name
            or deployment_id == config.deployment_id
            or deployment_id == requested_deployment_id
            or display_name == config.deployment_id
            or display_name == requested_deployment_id
        ):
            deployment_matches.append(deployment)

    unique_deployment_names = list(
        dict.fromkeys(
            deployment.get("name")
            for deployment in deployment_matches
            if isinstance(deployment.get("name"), str) and deployment.get("name")
        )
    )
    if not unique_deployment_names:
        raise VoiceEvaluationError(
            f"Configured CES_DEPLOYMENT_ID '{config.deployment_id}' was not found under app '{config.app_id}'. "
            f"Available deployments: {format_named_resources(deployments)}"
        )
    if len(unique_deployment_names) > 1:
        raise VoiceEvaluationError(
            "Configured CES_DEPLOYMENT_ID matched multiple deployments. "
            f"Please use a full resource name or unique deployment id. Matches: {', '.join(unique_deployment_names)}"
        )

    normalized_deployment_id = resource_id_from_name(unique_deployment_names[0])
    resolved_entry_agent = resolve_entry_agent_name(config, token=token, app_name=app_name)
    if (
        resolved_entry_agent and resolved_entry_agent != config.entry_agent
    ) or normalized_deployment_id != config.deployment_id:
        return replace(
            config,
            deployment_id=normalized_deployment_id,
            entry_agent=resolved_entry_agent or config.entry_agent,
        )
    return config


def remote_evaluation_map(
    config: RemoteReplayConfig,
    *,
    token: str,
) -> dict[str, str]:
    """Map remote CES evaluation display names to resource names."""
    resources = list_collection(
        config.endpoint,
        token,
        app_parent(config.project, config.location, config.app_id),
        "evaluations",
        "evaluations",
        timeout_seconds=config.timeout_seconds,
    )
    mapping: dict[str, str] = {}
    for resource in resources:
        display_name = resource.get("displayName")
        name = resource.get("name")
        if isinstance(display_name, str) and display_name and isinstance(name, str) and name:
            mapping[display_name] = name
    return mapping


def extract_evaluation_run_name(operation: dict[str, Any]) -> str | None:
    """Extract the created evaluation run resource name from an operation payload."""
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
    return candidates[0] if candidates else None


def poll_operation(
    endpoint: str,
    token: str,
    operation_name: str,
    *,
    timeout_seconds: int,
    poll_interval_seconds: int,
) -> dict[str, Any]:
    """Poll a long-running CES operation until it completes."""
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
            raise VoiceEvaluationError(
                f"Timed out waiting for operation '{operation_name}' after {timeout_seconds}s."
            )
        time.sleep(poll_interval_seconds)


def locate_evaluation_run(
    config: RemoteReplayConfig,
    *,
    token: str,
    run_display_name: str,
    operation: dict[str, Any],
) -> dict[str, Any]:
    """Locate the concrete evaluation run resource for an operation."""
    run_name = extract_evaluation_run_name(operation)
    if run_name:
        return get_resource(
            config.endpoint,
            token,
            run_name,
            timeout_seconds=config.timeout_seconds,
        )

    runs = list_collection(
        config.endpoint,
        token,
        app_parent(config.project, config.location, config.app_id),
        "evaluationRuns",
        "evaluationRuns",
        timeout_seconds=config.timeout_seconds,
        query={"orderBy": "createTime"},
    )
    matching_runs = [
        run
        for run in runs
        if run.get("displayName") == run_display_name
    ]
    if not matching_runs:
        raise VoiceEvaluationError(
            f"Could not locate an evaluation run with displayName '{run_display_name}'."
        )
    matching_runs.sort(key=lambda item: item.get("createTime", ""), reverse=True)
    return matching_runs[0]


def load_results_for_run(
    config: RemoteReplayConfig,
    *,
    token: str,
    run_resource: dict[str, Any],
    run_name: str,
) -> list[dict[str, Any]]:
    """Load evaluation results referenced directly by a remote replay run."""
    explicit_results = run_resource.get("evaluationResults", [])
    if not isinstance(explicit_results, list) or not explicit_results:
        return []

    results: list[dict[str, Any]] = []
    for result_name in explicit_results:
        if not isinstance(result_name, str) or not result_name:
            continue
        result = get_resource(
            config.endpoint,
            token,
            result_name,
            timeout_seconds=config.timeout_seconds,
        )
        if result.get("evaluationRun") == run_name:
            results.append(result)
    return results


def summarize_remote_replay(
    config: RemoteReplayConfig,
    *,
    token: str,
    scenario: VoiceScenario,
    turn_results: list[dict[str, Any]],
) -> dict[str, Any]:
    """Replay a linked CES evaluation remotely and summarize the run."""
    if scenario.linked_evaluation_name is None:
        raise VoiceEvaluationError(
            f"Scenario '{scenario.name}' has no linked evaluation for remote replay."
        )

    evaluation_mapping = remote_evaluation_map(config, token=token)
    if scenario.linked_evaluation_name not in evaluation_mapping:
        raise VoiceEvaluationError(
            f"Remote CES app '{config.app_id}' does not contain evaluation '{scenario.linked_evaluation_name}'."
        )
    evaluation_resource = evaluation_mapping[scenario.linked_evaluation_name]
    run_display_name = (
        f"voice-replay-{scenario.name}-"
        f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')}"
    )

    start_url = (
        f"{config.endpoint}/v1beta/"
        f"{app_parent(config.project, config.location, config.app_id)}:runEvaluation"
    )
    start_operation = http_json(
        "POST",
        start_url,
        token=token,
        body={
            "displayName": run_display_name,
            "evaluations": [evaluation_resource],
        },
        timeout_seconds=config.timeout_seconds,
    )
    operation_name = start_operation.get("name")
    if not isinstance(operation_name, str) or not operation_name:
        raise VoiceEvaluationError(
            f"Remote replay for scenario '{scenario.name}' did not return an operation name."
        )

    completed_operation = poll_operation(
        config.endpoint,
        token,
        operation_name,
        timeout_seconds=config.timeout_seconds,
        poll_interval_seconds=config.poll_interval_seconds,
    )
    if isinstance(completed_operation.get("error"), dict):
        error = completed_operation["error"]
        raise VoiceEvaluationError(
            f"Remote replay for scenario '{scenario.name}' failed: {error.get('message', error)}"
        )

    run_resource = locate_evaluation_run(
        config,
        token=token,
        run_display_name=run_display_name,
        operation=completed_operation,
    )
    run_name = run_resource.get("name")
    if not isinstance(run_name, str) or not run_name:
        raise VoiceEvaluationError(
            f"Remote replay for scenario '{scenario.name}' returned a run without a name."
        )

    results = load_results_for_run(
        config,
        token=token,
        run_resource=run_resource,
        run_name=run_name,
    )
    passed_count = 0
    failed_count = 0
    error_count = 0
    pending_count = 0
    result_summaries: list[dict[str, Any]] = []
    for result in results:
        execution_state = result.get("executionState")
        evaluation_status = result.get("evaluationStatus")
        if execution_state == "COMPLETED":
            if evaluation_status == "PASS":
                passed_count += 1
            else:
                failed_count += 1
        elif execution_state == "ERROR":
            error_count += 1
        else:
            pending_count += 1
        experience_metrics = result.get("experienceMetrics")
        conversation_name = None
        if isinstance(experience_metrics, dict):
            conversation = experience_metrics.get("conversation")
            if isinstance(conversation, str) and conversation:
                conversation_name = conversation
        result_summaries.append(
            {
                "name": result.get("name"),
                "execution_state": execution_state,
                "evaluation_status": evaluation_status,
                "conversation": conversation_name,
                "changelog": result.get("changelog"),
            }
        )

    if not results:
        run_summaries = run_resource.get("evaluationRunSummaries", {})
        if isinstance(run_summaries, dict):
            summary = run_summaries.get(evaluation_resource)
            if isinstance(summary, dict):
                passed_count = int(summary.get("passedCount", 0) or 0)
                failed_count = int(summary.get("failedCount", 0) or 0)
                error_count = int(summary.get("errorCount", 0) or 0)

    completed_count = passed_count + failed_count
    passed = (
        run_resource.get("state") == "COMPLETED"
        and completed_count > 0
        and failed_count == 0
        and error_count == 0
        and pending_count == 0
    )

    return {
        "requested": True,
        "passed": passed,
        "mode": config.mode,
        "project": config.project,
        "location": config.location,
        "app_id": config.app_id,
        "endpoint": config.endpoint,
        "evaluation_display_name": scenario.linked_evaluation_name,
        "evaluation_resource": evaluation_resource,
        "run_display_name": run_display_name,
        "operation_name": operation_name,
        "operation": completed_operation,
        "evaluation_run": run_name,
        "evaluation_run_state": run_resource.get("state"),
        "source_turn_transcripts": [turn_result["transcript"] for turn_result in turn_results],
        "source_combined_transcript": " ".join(turn_result["transcript"] for turn_result in turn_results),
        "actual": {
            "completed_count": completed_count,
            "passed_count": passed_count,
            "failed_count": failed_count,
            "error_count": error_count,
            "pending_count": pending_count,
            "result_count": len(results),
        },
        "results": result_summaries,
    }


def write_json_document(path: Path, payload: dict[str, Any]) -> None:
    """Write a formatted JSON document to disk."""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")


def run_suite(
    suite: VoiceSuite,
    *,
    artifacts_dir: Path,
    ces_session: CesSessionConfig | None = None,
    remote_replay: RemoteReplayConfig | None = None,
) -> int:
    """Execute the voice suite locally and persist artifacts."""
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    print_suite_banner(suite.display_name, suite.suite_path, artifacts_dir)

    scenario_results: list[dict[str, Any]] = []
    pass_count = 0
    fail_count = 0
    ces_token = get_access_token() if remote_replay is not None or ces_session is not None else None
    ces_session_pass_count = 0
    ces_session_fail_count = 0
    remote_pass_count = 0
    remote_fail_count = 0

    for index, scenario in enumerate(suite.scenarios, start=1):
        turn_results: list[dict[str, Any]] = []
        matched_turn_count = 0
        for turn_index, turn in enumerate(scenario.turns, start=1):
            transcript = transcribe_turn(turn)
            transcript_matches_expected = (
                normalize_text(transcript) == normalize_text(turn.expected_transcript)
            )
            if transcript_matches_expected:
                matched_turn_count += 1
            turn_results.append(
                {
                    "turn": turn.name,
                    "turn_index": turn_index,
                    "audio_path": str(turn.audio_path),
                    "transcriber_kind": transcriber_kind(turn.transcriber),
                    "transcript_path": transcriber_transcript_path(turn.transcriber),
                    "transcript": transcript,
                    "expected_transcript": turn.expected_transcript,
                    "transcript_matches_expected": transcript_matches_expected,
                }
            )

        linked_evaluation_matches = None
        if scenario.linked_evaluation_reference_text is not None and turn_results:
            linked_evaluation_matches = (
                normalize_text(turn_results[0]["transcript"])
                == normalize_text(scenario.linked_evaluation_reference_text)
            )

        local_passed = (
            matched_turn_count == len(scenario.turns)
            and linked_evaluation_matches in (None, True)
        )
        ces_session_result: dict[str, Any] | None = None
        if ces_session is not None:
            try:
                ces_session_result = summarize_ces_session(
                    ces_session,
                    token=ces_token or "",
                    scenario=scenario,
                )
            except VoiceEvaluationError as exc:
                ces_session_result = {
                    "requested": True,
                    "passed": False,
                    "mode": ces_session.mode,
                    "error": str(exc),
                }

        remote_replay_result: dict[str, Any] | None = None
        if remote_replay is not None:
            if local_passed:
                try:
                    remote_replay_result = summarize_remote_replay(
                        remote_replay,
                        token=ces_token or "",
                        scenario=scenario,
                        turn_results=turn_results,
                    )
                except VoiceEvaluationError as exc:
                    remote_replay_result = {
                        "requested": True,
                        "passed": False,
                        "mode": remote_replay.mode,
                        "error": str(exc),
                        "evaluation_display_name": scenario.linked_evaluation_name,
                    }
            else:
                remote_replay_result = {
                    "requested": True,
                    "passed": False,
                    "mode": remote_replay.mode,
                    "skipped": True,
                    "reason": "Local transcript validation failed; remote replay was skipped.",
                    "evaluation_display_name": scenario.linked_evaluation_name,
                }

        ces_session_enforced = ces_session is not None and ces_session.mode == "assert"
        remote_replay_enforced = remote_replay is not None and remote_replay.mode == "assert"
        passed = (
            local_passed
            and (
                ces_session_result is None
                or not ces_session_enforced
                or ces_session_result.get("passed") is True
            )
            and (
                remote_replay_result is None
                or not remote_replay_enforced
                or remote_replay_result.get("passed") is True
            )
        )
        if passed:
            pass_count += 1
        else:
            fail_count += 1
        if ces_session_result is not None:
            if ces_session_result.get("passed") is True:
                ces_session_pass_count += 1
            else:
                ces_session_fail_count += 1
        if remote_replay_result is not None:
            if remote_replay_result.get("passed") is True:
                remote_pass_count += 1
            else:
                remote_fail_count += 1

        result_payload = {
            "scenario": scenario.name,
            "status": "PASS" if passed else "FAIL",
            "turn_count": len(scenario.turns),
            "matched_turn_count": matched_turn_count,
            "local_passed": local_passed,
            "combined_transcript": " ".join(turn_result["transcript"] for turn_result in turn_results),
            "linked_evaluation_name": scenario.linked_evaluation_name,
            "linked_evaluation_path": (
                str(scenario.linked_evaluation_path) if scenario.linked_evaluation_path else None
            ),
            "linked_evaluation_kind": scenario.linked_evaluation_kind,
            "linked_evaluation_user_input": scenario.linked_evaluation_reference_text,
            "linked_evaluation_matches_transcript": linked_evaluation_matches,
            "ces_session_mode": None if ces_session is None else ces_session.mode,
            "ces_session": ces_session_result,
            "remote_replay_mode": None if remote_replay is None else remote_replay.mode,
            "remote_replay": remote_replay_result,
            "turns": turn_results,
        }
        artifact_path = artifacts_dir / f"{index:02d}-{scenario.name}.json"
        write_json_document(artifact_path, result_payload)
        scenario_results.append(
            {
                "scenario": scenario.name,
                "status": result_payload["status"],
                "ces_session_status": (
                    None
                    if ces_session_result is None
                    else ("PASS" if ces_session_result.get("passed") is True else "FAIL")
                ),
                "remote_replay_status": (
                    None
                    if remote_replay_result is None
                    else ("PASS" if remote_replay_result.get("passed") is True else "FAIL")
                ),
                "artifact": str(artifact_path),
            }
        )
        detail_parts = [f"{matched_turn_count}/{len(scenario.turns)} turns matched"]
        if scenario.linked_evaluation_name:
            detail_parts.append(f"linked evaluation {scenario.linked_evaluation_name}")
        if ces_session_result is not None:
            detail_parts.append(
                format_ces_session_status(ces_session_result, enforced=ces_session_enforced)
            )
        if remote_replay_result is not None:
            detail_parts.append(
                format_remote_status(remote_replay_result, enforced=remote_replay_enforced)
            )

        print(f"[{index}/{len(suite.scenarios)}] {scenario.name} (voice_scenario)")
        if passed:
            print(
                f"  {colorize('PASS', ANSI_GREEN, bold=True)}  "
                f"{'; '.join(detail_parts)}"
            )
        else:
            print(
                f"  {colorize('FAIL', ANSI_RED, bold=True)}  "
                f"{describe_voice_failure(
                    scenario,
                    turn_results,
                    linked_evaluation_matches=linked_evaluation_matches,
                    ces_session_result=ces_session_result,
                    ces_session_enforced=ces_session_enforced,
                    remote_replay_result=remote_replay_result,
                    remote_replay_enforced=remote_replay_enforced,
                )}"
            )
            print(f"  Artifact: {artifact_path}")

    summary_payload = {
        "suite": str(suite.suite_path),
        "display_name": suite.display_name,
        "artifacts_dir": str(artifacts_dir),
        "scenario_count": len(suite.scenarios),
        "pass_count": pass_count,
        "fail_count": fail_count,
        "ces_session_requested": ces_session is not None,
        "ces_session_mode": None if ces_session is None else ces_session.mode,
        "ces_session_pass_count": ces_session_pass_count,
        "ces_session_fail_count": ces_session_fail_count,
        "remote_replay_requested": remote_replay is not None,
        "remote_replay_mode": None if remote_replay is None else remote_replay.mode,
        "remote_pass_count": remote_pass_count,
        "remote_fail_count": remote_fail_count,
        "results": scenario_results,
    }
    summary_path = artifacts_dir / "summary.json"
    write_json_document(summary_path, summary_payload)
    print("")
    print(f"Summary artifact: {summary_path}")

    if fail_count:
        print(
            f"{colorize('SUITE FAILED', ANSI_RED, bold=True)}: "
            f"{suite.display_name} - {fail_count} scenario(s) failed."
        )
    else:
        print(
            f"{colorize('SUITE PASSED', ANSI_GREEN, bold=True)}: "
            f"{suite.display_name} - {pass_count} scenario(s) passed."
        )

    return 0 if fail_count == 0 else 1


def main() -> int:
    """CLI entrypoint."""
    args = parse_args()

    try:
        ensure_default_env_loaded()
        suite = load_suite(resolve_path(Path.cwd(), args.suite))
        if args.command == "validate-suite":
            print(f"Suite valid: {suite.suite_path}")
            print(f"Scenarios: {len(suite.scenarios)}")
            return 0

        ces_session = resolve_ces_session_config(args)
        remote_replay = resolve_remote_replay_config(args)

        artifacts_dir = (
            resolve_path(Path.cwd(), args.artifacts_dir)
            if args.artifacts_dir
            else default_artifacts_root()
        )
        return run_suite(
            suite,
            artifacts_dir=artifacts_dir,
            ces_session=ces_session,
            remote_replay=remote_replay,
        )
    except VoiceEvaluationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("Cancelled by user.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
