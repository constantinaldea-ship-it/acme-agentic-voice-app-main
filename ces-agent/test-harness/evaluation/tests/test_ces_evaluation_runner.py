"""Created by Codex on 2026-03-27.

Unit tests for the CES evaluation framework.
"""

import importlib.util
import io
import json
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch


SCRIPT_DIR = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIR.parent / "ces-evaluation-runner.py"
SPEC = importlib.util.spec_from_file_location("ces_evaluation_runner", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load evaluation module from {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules["ces_evaluation_runner"] = MODULE
SPEC.loader.exec_module(MODULE)

EvaluationFrameworkError = MODULE.EvaluationFrameworkError
bootstrap_runtime_env = MODULE.bootstrap_runtime_env
build_run_display_name = MODULE.build_run_display_name
load_suite = MODULE.load_suite
resolve_runtime_suite = MODULE.resolve_runtime_suite
run_suite = MODULE.run_suite
summarize_suite = MODULE.summarize_suite
validate_suite_command = MODULE.validate_suite_command


class CesEvaluationRunnerUnitTests(unittest.TestCase):
    def create_temp_dir(self) -> Path:
        path = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: path.exists() and __import__("shutil").rmtree(path))
        return path

    def write_json(self, path: Path, payload: dict) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def create_package_root(self) -> Path:
        root = self.create_temp_dir() / "acme_voice_agent"
        (root / "evaluations").mkdir(parents=True, exist_ok=True)
        return root

    def create_suite_file(self, root_dir: Path, payload: dict) -> Path:
        suite_path = root_dir / "suite.json"
        suite_path.write_text(json.dumps(payload), encoding="utf-8")
        return suite_path

    def add_local_evaluation(self, package_root: Path, name: str, kind: str) -> None:
        body = {"displayName": name, kind: {}}
        self.write_json(package_root / "evaluations" / name / f"{name}.json", body)

    def write_env_file(self, path: Path, lines: list[str]) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return path

    def test_load_suite_resolves_local_evaluations(self) -> None:
        base_dir = self.create_temp_dir()
        package_root = base_dir / "acme_voice_agent"
        self.add_local_evaluation(package_root, "agent_handover_roundtrip", "golden")
        suite_path = self.create_suite_file(
            base_dir,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "display_name_prefix": "golden",
                "default_expected": {
                    "min_pass_rate": 1.0,
                    "min_completed_results": 1,
                    "max_error_count": 0,
                },
                "evaluations": [
                    {
                        "name": "agent_handover_roundtrip",
                        "owner_agent": "voice_banking_agent",
                        "labels": ["root", "critical"],
                    }
                ],
            },
        )

        suite = load_suite(suite_path)
        self.assertEqual(suite.project, "voice-banking-poc")
        self.assertEqual(len(suite.evaluations), 1)
        self.assertEqual(suite.evaluations[0].kind, "golden")
        self.assertEqual(suite.evaluations[0].labels, ("root", "critical"))

    def test_load_suite_rejects_duplicate_evaluations(self) -> None:
        base_dir = self.create_temp_dir()
        package_root = base_dir / "acme_voice_agent"
        self.add_local_evaluation(package_root, "fee_lookup_basic", "golden")
        suite_path = self.create_suite_file(
            base_dir,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "evaluations": [
                    {"name": "fee_lookup_basic"},
                    {"name": "fee_lookup_basic"},
                ],
            },
        )

        with self.assertRaises(EvaluationFrameworkError):
            load_suite(suite_path)

    def test_validate_suite_command_prints_passes(self) -> None:
        base_dir = self.create_temp_dir()
        package_root = base_dir / "acme_voice_agent"
        self.add_local_evaluation(package_root, "session_end_live_agent", "golden")
        suite_path = self.create_suite_file(
            base_dir,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "evaluations": [{"name": "session_end_live_agent"}],
            },
        )

        stdout = io.StringIO()
        with redirect_stdout(stdout):
            result = validate_suite_command(suite_path)
        self.assertEqual(result, 0)
        self.assertIn("session_end_live_agent", stdout.getvalue())

    def test_repo_suite_files_resolve_checked_in_package_root(self) -> None:
        suites_dir = SCRIPT_DIR.parent / "suites"
        suite_names = (
            "agent-quality-golden-suite.json",
            "agent-quality-scenario-suite.json",
            "langsmith-advisory-routing-suite.json",
            "langsmith-advisory-booking-suite.json",
            "langsmith-advisory-recovery-suite.json",
        )

        for suite_name in suite_names:
            with self.subTest(suite=suite_name):
                suite = load_suite(suites_dir / suite_name)
                self.assertTrue(suite.package_root.is_dir())
                self.assertTrue((suite.package_root / "app.json").is_file())

    def test_bootstrap_runtime_env_loads_state_file_and_defaults(self) -> None:
        base_dir = self.create_temp_dir()
        state_file = self.write_env_file(
            base_dir / ".tmp" / "cloud-run" / "discovery-plan.env",
            ["PROJECT_ID=voice-banking-poc"],
        )

        with patch.dict(os.environ, {}, clear=True):
            bootstrap_runtime_env(
                env_files=(state_file,),
                defaults={"GCP_LOCATION": "us", "CES_APP_ID": "acme-voice-us"},
            )
            self.assertEqual(os.environ["PROJECT_ID"], "voice-banking-poc")
            self.assertEqual(os.environ["GCP_PROJECT_ID"], "voice-banking-poc")
            self.assertEqual(os.environ["GCP_LOCATION"], "us")
            self.assertEqual(os.environ["CES_APP_ID"], "acme-voice-us")

    def test_resolve_runtime_suite_reports_missing_env_field_context(self) -> None:
        base_dir = self.create_temp_dir()
        package_root = base_dir / "acme_voice_agent"
        self.add_local_evaluation(package_root, "agent_handover_roundtrip", "golden")
        suite_path = self.create_suite_file(
            base_dir,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "${CES_APP_ID}",
                "evaluations": [{"name": "agent_handover_roundtrip"}],
            },
        )
        suite = load_suite(suite_path)

        with (
            patch.object(MODULE, "bootstrap_runtime_env", return_value=None),
            patch.dict(os.environ, {}, clear=True),
        ):
            with self.assertRaises(EvaluationFrameworkError) as context:
                resolve_runtime_suite(suite)

        self.assertIn("field 'app_id'", str(context.exception))
        self.assertIn("Missing required environment variable: CES_APP_ID", str(context.exception))

    def test_summarize_suite_uses_result_details(self) -> None:
        package_root = self.create_package_root()
        self.add_local_evaluation(package_root, "branch_search_munich", "scenario")
        suite_path = self.create_suite_file(
            package_root.parent,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "default_expected": {
                    "min_pass_rate": 0.5,
                    "min_completed_results": 2,
                    "max_error_count": 0,
                },
                "evaluations": [{"name": "branch_search_munich"}],
            },
        )
        suite = load_suite(suite_path)
        run_resource = {
            "name": "projects/p/locations/us/apps/a/evaluationRuns/run-1",
            "displayName": "agent-quality-scenario-20260327T120000Z",
            "state": "COMPLETED",
            "progress": {
                "totalCount": 3,
                "completedCount": 2,
                "passedCount": 1,
                "failedCount": 1,
                "errorCount": 1,
            },
            "evaluationRunSummaries": {
                "projects/p/locations/us/apps/a/evaluations/branch_search_munich": {
                    "passedCount": 1,
                    "failedCount": 1,
                    "errorCount": 1,
                }
            },
            "latencyReport": {"sessionCount": 3},
        }
        results = [
            {
                "name": "projects/p/locations/us/apps/a/evaluations/branch_search_munich/results/1",
                "evaluationRun": run_resource["name"],
                "executionState": "COMPLETED",
                "evaluationStatus": "PASS",
            },
            {
                "name": "projects/p/locations/us/apps/a/evaluations/branch_search_munich/results/2",
                "evaluationRun": run_resource["name"],
                "executionState": "COMPLETED",
                "evaluationStatus": "FAIL",
            },
            {
                "name": "projects/p/locations/us/apps/a/evaluations/branch_search_munich/results/3",
                "evaluationRun": run_resource["name"],
                "executionState": "ERROR",
            },
        ]

        summary = summarize_suite(suite, run_resource, results)
        evaluation = summary["evaluations"][0]
        self.assertFalse(summary["suite_passed"])
        self.assertEqual(evaluation["actual"]["completed_count"], 2)
        self.assertEqual(evaluation["actual"]["error_count"], 1)
        self.assertIn("error_count=1 above allowed 0", evaluation["assertion_failures"])

    def test_summarize_suite_maps_remote_resource_names_back_to_display_names(self) -> None:
        package_root = self.create_package_root()
        self.add_local_evaluation(package_root, "appointment_routing_english", "golden")
        suite_path = self.create_suite_file(
            package_root.parent,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "default_expected": {
                    "min_pass_rate": 1.0,
                    "min_completed_results": 1,
                    "max_error_count": 0,
                },
                "evaluations": [{"name": "appointment_routing_english"}],
            },
        )
        suite = load_suite(suite_path)
        evaluation_resource = (
            "projects/p/locations/us/apps/a/evaluations/"
            "2e10fdf4-43aa-42d0-bd5b-9c37b9d95315"
        )
        run_resource = {
            "name": "projects/p/locations/us/apps/a/evaluationRuns/run-1",
            "displayName": "routing-20260327T120000Z",
            "state": "COMPLETED",
            "progress": {
                "totalCount": 1,
                "completedCount": 1,
                "passedCount": 0,
                "failedCount": 1,
                "errorCount": 0,
            },
            "evaluationRunSummaries": {
                evaluation_resource: {
                    "passedCount": 0,
                    "failedCount": 1,
                    "errorCount": 0,
                }
            },
        }
        results = [
            {
                "name": f"{evaluation_resource}/results/result-1",
                "evaluationRun": run_resource["name"],
                "executionState": "COMPLETED",
                "evaluationStatus": "FAIL",
            }
        ]

        summary = summarize_suite(
            suite,
            run_resource,
            results,
            resource_name_to_display_name={evaluation_resource: "appointment_routing_english"},
        )

        evaluation = summary["evaluations"][0]
        self.assertEqual(evaluation["actual"]["completed_count"], 1)
        self.assertEqual(evaluation["actual"]["failed_count"], 1)
        self.assertAlmostEqual(evaluation["actual"]["pass_rate"], 0.0)

    def test_run_suite_executes_mocked_remote_flow(self) -> None:
        base_dir = self.create_temp_dir()
        package_root = base_dir / "acme_voice_agent"
        self.add_local_evaluation(package_root, "fee_lookup_basic", "golden")
        suite_path = self.create_suite_file(
            base_dir,
            {
                "package_root": "./acme_voice_agent",
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "display_name_prefix": "golden",
                "run_request": {"runCount": 1},
                "evaluations": [{"name": "fee_lookup_basic"}],
            },
        )
        artifacts_dir = self.create_temp_dir() / "artifacts"

        run_resource = {
            "name": "projects/p/locations/us/apps/a/evaluationRuns/run-1",
            "displayName": "golden-20260327T120000Z",
            "state": "COMPLETED",
            "progress": {
                "totalCount": 1,
                "completedCount": 1,
                "passedCount": 1,
                "failedCount": 0,
                "errorCount": 0,
            },
            "evaluationRunSummaries": {
                "projects/p/locations/us/apps/a/evaluations/fee_lookup_basic": {
                    "passedCount": 1,
                    "failedCount": 0,
                    "errorCount": 0,
                }
            },
        }
        results = [
            {
                "name": "projects/p/locations/us/apps/a/evaluations/fee_lookup_basic/results/1",
                "evaluationRun": run_resource["name"],
                "executionState": "COMPLETED",
                "evaluationStatus": "PASS",
            }
        ]

        with (
            patch.object(MODULE, "get_access_token", return_value="token"),
            patch.object(
                MODULE,
                "resolve_remote_evaluations",
                return_value={
                    "fee_lookup_basic": "projects/p/locations/us/apps/a/evaluations/fee_lookup_basic"
                },
            ),
            patch.object(
                MODULE,
                "start_evaluation_run",
                return_value={"name": "projects/p/locations/us/operations/op-1"},
            ),
            patch.object(
                MODULE,
                "poll_operation",
                return_value={
                    "name": "projects/p/locations/us/operations/op-1",
                    "done": True,
                    "response": {"name": run_resource["name"]},
                },
            ),
            patch.object(MODULE, "locate_evaluation_run", return_value=run_resource),
            patch.object(MODULE, "load_results_for_run", return_value=results),
        ):
            result = run_suite(
                suite_path,
                artifacts_dir=artifacts_dir,
                timeout_seconds=60,
                poll_interval_seconds=1,
            )

        self.assertEqual(result, 0)
        self.assertTrue((artifacts_dir / "summary.json").exists())

    def test_build_run_display_name_is_slugged(self) -> None:
        value = build_run_display_name("Agent Quality Scenario")
        self.assertTrue(value.startswith("agent-quality-scenario-"))


if __name__ == "__main__":
    unittest.main()
