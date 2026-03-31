#!/usr/bin/env python3
"""Export advisory appointment CES scenario evals as a LangSmith dataset seed.

Author: Codex
Date: 2026-03-27
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_evaluations_root() -> Path:
    return repo_root() / "ces-agent" / "acme_voice_agent" / "evaluations"


def default_output_path() -> Path:
    return repo_root() / "ces-agent" / "docs" / "langsmith-advisory-appointment-dataset-seed.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Export advisory appointment CES evaluations as LangSmith dataset examples."
    )
    parser.add_argument(
        "--evaluations-root",
        default=str(default_evaluations_root()),
        help="Directory containing CES evaluation folders.",
    )
    parser.add_argument(
        "--output",
        default=str(default_output_path()),
        help="Output JSON path. Use '-' to print to stdout.",
    )
    parser.add_argument(
        "--dataset-name",
        default="advisory-appointment-offline-eval",
        help="Dataset name to embed in the exported JSON payload.",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError(f"Expected JSON object at {path}, got {type(payload).__name__}.")
    return payload


def advisory_evaluation_paths(evaluations_root: Path) -> list[Path]:
    return sorted(evaluations_root.glob("appointment_*/*.json"))


def infer_journey_type(display_name: str) -> str:
    if "routing" in display_name:
        return "routing"
    if "cancel" in display_name or "reschedule" in display_name:
        return "lifecycle"
    if "no_slots" in display_name or "handoff" in display_name or "repair" in display_name:
        return "recovery"
    return "booking"


def infer_language(display_name: str) -> str:
    if "german" in display_name:
        return "de"
    if "english" in display_name:
        return "en"
    return "en"


def safe_relative_path(path: Path, root: Path) -> str:
    try:
        return str(path.resolve().relative_to(root.resolve())).replace("\\", "/")
    except ValueError:
        return str(path.resolve())


def extract_from_scenario(payload: dict[str, Any]) -> tuple[str, int, list[str], str]:
    scenario = payload.get("scenario", {})
    task = str(scenario["task"])
    expectations = [
        str(item["expectedResult"])
        for item in scenario.get("scenarioExpectations", [])
        if isinstance(item, dict) and "expectedResult" in item
    ]
    return task, int(scenario.get("maxTurns", 0)), expectations, "ces_scenario"


def extract_from_golden(payload: dict[str, Any]) -> tuple[str, int, list[str], str]:
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
            user_input = step.get("userInput", {})
            if task == "Golden evaluation" and isinstance(user_input, dict) and "text" in user_input:
                task = str(user_input["text"])

            expectation = step.get("expectation", {})
            if not isinstance(expectation, dict):
                continue
            agent_transfer = expectation.get("agentTransfer")
            if isinstance(agent_transfer, dict) and agent_transfer.get("targetAgent"):
                expectations.append(
                    f"Agent transfers to {agent_transfer['targetAgent']}."
                )

            tool_call = expectation.get("toolCall")
            if isinstance(tool_call, dict):
                tool_name = tool_call.get("tool") or tool_call.get("displayName")
                if tool_name:
                    expectations.append(f"Agent calls tool {tool_name}.")

    return task, len(turns), expectations, "ces_golden"


def build_example(path: Path, package_root: Path) -> dict[str, Any]:
    payload = load_json(path)
    display_name = str(payload["displayName"])
    if "scenario" in payload:
        task, max_turns, expectations, source_evaluation_type = extract_from_scenario(payload)
    elif "golden" in payload:
        task, max_turns, expectations, source_evaluation_type = extract_from_golden(payload)
    else:
        raise ValueError(f"Unsupported evaluation format in {path}: expected 'scenario' or 'golden'.")
    relative_source_path = safe_relative_path(path, package_root)

    return {
        "name": display_name,
        "inputs": {
            "task": task,
            "max_turns": max_turns,
        },
        "outputs": {
            "reference_expectations": expectations,
            "target_agent": "advisory_appointment_agent",
            "source_evaluation_type": source_evaluation_type,
        },
        "metadata": {
            "display_name": display_name,
            "journey_type": infer_journey_type(display_name),
            "language": infer_language(display_name),
            "source_path": relative_source_path,
        },
    }


def build_dataset_seed(evaluations_root: Path, dataset_name: str) -> dict[str, Any]:
    package_root = evaluations_root.resolve().parents[1]
    examples = [build_example(path, package_root) for path in advisory_evaluation_paths(evaluations_root)]
    return {
        "dataset_name": dataset_name,
        "description": (
            "LangSmith dataset seed generated from ces-agent advisory appointment "
            "scenario evaluations."
        ),
        "examples": examples,
    }


def write_output(payload: dict[str, Any], output_path: str) -> None:
    rendered = json.dumps(payload, indent=2, ensure_ascii=False)
    if output_path == "-":
        print(rendered)
        return
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(rendered + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    evaluations_root = Path(args.evaluations_root).resolve()
    payload = build_dataset_seed(evaluations_root, args.dataset_name)
    write_output(payload, args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
