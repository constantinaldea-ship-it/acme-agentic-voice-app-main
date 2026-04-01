"""Created by Codex on 2026-03-27.

Unit tests for the LangSmith live experiment bridge.
"""

import importlib.util
import json
import os
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest.mock import patch


SCRIPT_DIR = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIR.parent / "langsmith-live-experiments.py"
SPEC = importlib.util.spec_from_file_location("langsmith_live_experiments", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load LangSmith module from {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules["langsmith_live_experiments"] = MODULE
SPEC.loader.exec_module(MODULE)

LangSmithExperimentError = MODULE.LangSmithExperimentError
build_langsmith_upload_body = MODULE.build_langsmith_upload_body
bootstrap_ces_runtime_env = MODULE.bootstrap_ces_runtime_env
build_langsmith_headers = MODULE.build_langsmith_headers
build_confirmation_before_lifecycle_action_feedback = MODULE.build_confirmation_before_lifecycle_action_feedback
build_first_turn_stays_in_scope_feedback = MODULE.build_first_turn_stays_in_scope_feedback
build_no_ungrounded_slot_or_status_claims_feedback = MODULE.build_no_ungrounded_slot_or_status_claims_feedback
build_routing_instruction_feedback = MODULE.build_routing_instruction_feedback
classify_text_language = MODULE.classify_text_language
extract_ordered_observation_events = MODULE.extract_ordered_observation_events
extract_first_observed_agent_response_text = MODULE.extract_first_observed_agent_response_text
LangSmithAuthConfig = MODULE.LangSmithAuthConfig
load_suite_langsmith_config = MODULE.load_suite_langsmith_config
normalize_langsmith_api_root = MODULE.normalize_langsmith_api_root
resolve_langsmith_auth_config = MODULE.resolve_langsmith_auth_config
run_suites_command = MODULE.run_suites_command
SuiteLangSmithConfig = MODULE.SuiteLangSmithConfig


class LangSmithLiveExperimentTests(unittest.TestCase):
    def create_temp_dir(self) -> Path:
        path = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: path.exists() and __import__("shutil").rmtree(path))
        return path

    def write_json(self, path: Path, payload: dict) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def test_bootstrap_ces_runtime_env_uses_runner_defaults(self) -> None:
        class FakeRunner:
            def bootstrap_runtime_env(self) -> None:
                os.environ.setdefault("GCP_PROJECT_ID", "voice-banking-poc")
                os.environ.setdefault("GCP_LOCATION", "us")
                os.environ.setdefault("CES_APP_ID", "acme-voice-us")

            def sync_env_aliases(self) -> None:
                if os.environ.get("GCP_PROJECT_ID") and not os.environ.get("PROJECT_ID"):
                    os.environ["PROJECT_ID"] = os.environ["GCP_PROJECT_ID"]
                if os.environ.get("GCP_LOCATION") and not os.environ.get("LOCATION"):
                    os.environ["LOCATION"] = os.environ["GCP_LOCATION"]

        args = Namespace(project=None, location=None, app_id=None)
        with patch.dict(os.environ, {}, clear=True):
            bootstrap_ces_runtime_env(args, ces_runner=FakeRunner())
            self.assertEqual(os.environ["GCP_PROJECT_ID"], "voice-banking-poc")
            self.assertEqual(os.environ["GCP_LOCATION"], "us")
            self.assertEqual(os.environ["CES_APP_ID"], "acme-voice-us")
            self.assertEqual(os.environ["PROJECT_ID"], "voice-banking-poc")
            self.assertEqual(os.environ["LOCATION"], "us")

    def test_bootstrap_ces_runtime_env_prefers_cli_overrides(self) -> None:
        class FakeRunner:
            def bootstrap_runtime_env(self) -> None:
                os.environ.setdefault("GCP_PROJECT_ID", "voice-banking-poc")
                os.environ.setdefault("GCP_LOCATION", "us")
                os.environ.setdefault("CES_APP_ID", "acme-voice-us")

            def sync_env_aliases(self) -> None:
                if os.environ.get("GCP_PROJECT_ID"):
                    os.environ["PROJECT_ID"] = os.environ["GCP_PROJECT_ID"]
                if os.environ.get("GCP_LOCATION"):
                    os.environ["LOCATION"] = os.environ["GCP_LOCATION"]

        args = Namespace(project="override-project", location="eu", app_id="custom-app")
        with patch.dict(os.environ, {}, clear=True):
            bootstrap_ces_runtime_env(args, ces_runner=FakeRunner())
            self.assertEqual(os.environ["GCP_PROJECT_ID"], "override-project")
            self.assertEqual(os.environ["GCP_LOCATION"], "eu")
            self.assertEqual(os.environ["CES_APP_ID"], "custom-app")
            self.assertEqual(os.environ["PROJECT_ID"], "override-project")
            self.assertEqual(os.environ["LOCATION"], "eu")

    def test_normalize_langsmith_api_root(self) -> None:
        self.assertEqual(
            normalize_langsmith_api_root("https://api.smith.langchain.com"),
            "https://api.smith.langchain.com/api/v1",
        )
        self.assertEqual(
            normalize_langsmith_api_root("https://eu.api.smith.langchain.com/api"),
            "https://eu.api.smith.langchain.com/api/v1",
        )
        self.assertEqual(
            normalize_langsmith_api_root("https://api.smith.langchain.com/api/v1"),
            "https://api.smith.langchain.com/api/v1",
        )
        self.assertEqual(
            normalize_langsmith_api_root("https://self-hosted.example/api/v1"),
            "https://self-hosted.example/api/v1",
        )

    def test_resolve_langsmith_auth_config_reads_workspace_id(self) -> None:
        args = Namespace(langsmith_endpoint=None, langsmith_workspace_id=None)
        with patch.dict(
            os.environ,
            {
                "LANGSMITH_API_KEY": "secret",
                "LANGSMITH_WORKSPACE_ID": "12345678-1234-5678-1234-567812345678",
            },
            clear=True,
        ):
            auth = resolve_langsmith_auth_config(args)
        self.assertEqual(auth.api_root, "https://api.smith.langchain.com/api/v1")
        self.assertEqual(auth.api_key, "secret")
        self.assertEqual(auth.workspace_id, "12345678-1234-5678-1234-567812345678")

    def test_resolve_langsmith_auth_config_ignores_invalid_workspace_id(self) -> None:
        args = Namespace(langsmith_endpoint=None, langsmith_workspace_id=None)
        with patch.dict(
            os.environ,
            {"LANGSMITH_API_KEY": "secret", "LANGSMITH_WORKSPACE_ID": "ces-bw"},
            clear=True,
        ):
            auth = resolve_langsmith_auth_config(args)
        self.assertIsNone(auth.workspace_id)

    def test_build_langsmith_headers_adds_workspace_header_when_present(self) -> None:
        auth = LangSmithAuthConfig(
            api_root="https://api.smith.langchain.com/api/v1",
            api_key="secret",
            workspace_id="12345678-1234-5678-1234-567812345678",
        )
        self.assertEqual(
            build_langsmith_headers(auth),
            {
                "x-api-key": "secret",
                "x-tenant-id": "12345678-1234-5678-1234-567812345678",
            },
        )
        auth_without_workspace = LangSmithAuthConfig(
            api_root="https://api.smith.langchain.com/api/v1",
            api_key="secret",
            workspace_id=None,
        )
        self.assertEqual(build_langsmith_headers(auth_without_workspace), {"x-api-key": "secret"})

    def test_routing_instruction_helpers_extract_response_and_language(self) -> None:
        result_payload = {
            "goldenResult": {
                "turnReplayResults": [
                    {
                        "expectationOutcome": [
                            {
                                "observedAgentResponse": {
                                    "chunks": [{"text": "Worum geht es bei dem Beratungstermin?"}]
                                }
                            }
                        ]
                    }
                ]
            }
        }
        self.assertEqual(
            extract_first_observed_agent_response_text(result_payload),
            "Worum geht es bei dem Beratungstermin?",
        )
        self.assertEqual(classify_text_language("Worum geht es bei dem Beratungstermin?"), "de")
        self.assertEqual(classify_text_language("Can you tell me the topic?"), "en")

    def test_build_routing_instruction_feedback_flags_greeting_and_multi_question_first_turn(self) -> None:
        scores = build_routing_instruction_feedback(
            display_name="appointment_routing_english",
            response_text="Hello. Can you tell me the topic? What city are you in?",
        )
        scores_by_key = {score["key"]: score for score in scores}
        self.assertEqual(scores_by_key["same_language_as_user"]["score"], 1.0)
        self.assertEqual(scores_by_key["no_greeting_on_first_turn"]["score"], 0.0)
        self.assertEqual(scores_by_key["single_question_first_turn"]["score"], 0.0)

    def test_confirmation_before_lifecycle_action_feedback(self) -> None:
        events = [
            {"type": "response", "value": "Would you like me to cancel the appointment?"},
            {"type": "tool", "value": "cancelAppointment"},
        ]
        score = build_confirmation_before_lifecycle_action_feedback(events)
        self.assertEqual(score["value"], "pass")

        no_confirmation = [
            {"type": "response", "value": "I have cancelled the appointment."},
            {"type": "tool", "value": "cancelAppointment"},
        ]
        score = build_confirmation_before_lifecycle_action_feedback(no_confirmation)
        self.assertEqual(score["value"], "fail")

    def test_no_ungrounded_slot_or_status_claims_feedback(self) -> None:
        ungrounded_slot = [
            {"type": "response", "value": "I have an available slot at 10:30 tomorrow."},
        ]
        score = build_no_ungrounded_slot_or_status_claims_feedback(ungrounded_slot)
        self.assertEqual(score["value"], "fail")

        grounded_status = [
            {"type": "tool", "value": "createAppointment"},
            {"type": "response", "value": "Your appointment is confirmed."},
        ]
        score = build_no_ungrounded_slot_or_status_claims_feedback(grounded_status)
        self.assertEqual(score["value"], "pass")

        generic_appointment_question = [
            {"type": "response", "value": "Worum geht es bei dem Beratungstermin?"},
        ]
        score = build_no_ungrounded_slot_or_status_claims_feedback(generic_appointment_question)
        self.assertEqual(score["value"], "not_observed")

    def test_first_turn_stays_in_scope_feedback(self) -> None:
        pass_score = build_first_turn_stays_in_scope_feedback(
            "What topic would you like to discuss in the advisory appointment?"
        )
        self.assertEqual(pass_score["value"], "pass")
        fail_score = build_first_turn_stays_in_scope_feedback(
            "I can help you check your account balance."
        )
        self.assertEqual(fail_score["value"], "fail")

    def test_extract_ordered_observation_events_preserves_response_and_tool_order(self) -> None:
        result_payload = {
            "goldenResult": {
                "turnReplayResults": [
                    {
                        "expectationOutcome": [
                            {
                                "observedAgentResponse": {
                                    "chunks": [{"text": "Would you like me to cancel the appointment?"}]
                                }
                            },
                            {
                                "observedToolCall": {
                                    "tool": "cancelAppointment"
                                }
                            },
                        ]
                    }
                ]
            }
        }
        events = extract_ordered_observation_events(result_payload)
        self.assertEqual(
            events,
            [
                {"type": "response", "value": "Would you like me to cancel the appointment?"},
                {"type": "tool", "value": "cancelAppointment"},
            ],
        )

    def test_run_suites_command_skips_missing_remote_evaluations_without_failing_process(self) -> None:
        temp_dir = self.create_temp_dir()
        suite_path = self.write_json(
            temp_dir / "suite.json",
            {
                "langsmith": {
                    "dataset_name": "demo-dataset",
                    "experiment_metadata": {},
                }
            },
        )
        args = Namespace(
            suite=[str(suite_path)],
            project=None,
            location=None,
            app_id=None,
            artifacts_dir=str(temp_dir / "artifacts"),
            timeout_seconds=1,
            poll_interval_seconds=1,
            upload_mode="never",
            langsmith_endpoint=None,
            langsmith_workspace_id=None,
        )

        class FakeRunner:
            def bootstrap_runtime_env(self) -> None:
                return None

            def sync_env_aliases(self) -> None:
                return None

            def run_suite(self, *_args, **_kwargs) -> int:
                raise RuntimeError(
                    "Remote CES app 'acme-voice-us' is missing evaluation resource(s): appointment_no_slots_recovery"
                )

        with patch.object(MODULE, "load_ces_runner_module", return_value=FakeRunner()):
            result = run_suites_command(args)

        self.assertEqual(result, 0)
        summary = json.loads((temp_dir / "artifacts" / "langsmith-live-summary.json").read_text())
        self.assertEqual(summary["execution_failure_count"], 0)
        self.assertEqual(summary["skipped_suite_count"], 1)
        self.assertEqual(summary["results"][0]["status"], "skipped_missing_remote_evaluations")

    def test_run_suites_command_treats_failed_evaluations_as_nonfatal_when_execution_succeeds(self) -> None:
        temp_dir = self.create_temp_dir()
        evaluation_path = self.write_json(
            temp_dir / "appointment_routing_english.json",
            {
                "displayName": "appointment_routing_english",
                "golden": {
                    "turns": [
                        {
                            "steps": [
                                {
                                    "userInput": {"text": "I want an appointment"},
                                    "expectation": {
                                        "agentTransfer": {"targetAgent": "advisory_appointment_agent"}
                                    },
                                }
                            ]
                        }
                    ]
                },
            },
        )
        suite_path = self.write_json(
            temp_dir / "suite.json",
            {
                "langsmith": {
                    "dataset_name": "demo-dataset",
                    "experiment_metadata": {},
                }
            },
        )
        artifacts_dir = temp_dir / "artifacts" / "suite"
        summary_payload = {
            "project": "voice-banking-poc",
            "location": "us",
            "app_id": "acme-voice-us",
            "run_name": "run-1",
            "run_display_name": "routing-run",
            "run_state": "COMPLETED",
            "suite_passed": False,
            "suite_failures": 1,
            "latency_report_present": True,
            "evaluations": [
                {
                    "name": "appointment_routing_english",
                    "kind": "golden",
                    "owner_agent": "voice_banking_agent",
                    "labels": ["routing"],
                    "local_path": str(evaluation_path),
                    "expected": {
                        "min_pass_rate": 1.0,
                        "min_completed_results": 1,
                        "max_error_count": 0,
                    },
                    "actual": {
                        "results_count": 1,
                        "completed_count": 1,
                        "passed_count": 0,
                        "failed_count": 1,
                        "error_count": 0,
                        "pending_count": 0,
                        "pass_rate": 0.0,
                    },
                    "result_names": ["r1"],
                    "passed": False,
                    "assertion_failures": ["pass_rate=0.000 below required 1.000"],
                }
            ],
        }

        class FakeRunner:
            def bootstrap_runtime_env(self) -> None:
                return None

            def sync_env_aliases(self) -> None:
                return None

            def run_suite(self, _suite_path, *, artifacts_dir, **_kwargs) -> int:
                artifacts_dir.mkdir(parents=True, exist_ok=True)
                (artifacts_dir / "summary.json").write_text(json.dumps(summary_payload))
                (artifacts_dir / "03-evaluation-run.json").write_text(
                    json.dumps({"evaluation_run": {"createTime": "2026-03-27T12:00:00Z"}})
                )
                (artifacts_dir / "02-operation-complete.json").write_text(
                    json.dumps({"operation": {"metadata": {"endTime": "2026-03-27T12:05:00Z"}}})
                )
                return 1

        args = Namespace(
            suite=[str(suite_path)],
            project=None,
            location=None,
            app_id=None,
            artifacts_dir=str(temp_dir / "artifacts"),
            timeout_seconds=1,
            poll_interval_seconds=1,
            upload_mode="never",
            langsmith_endpoint=None,
            langsmith_workspace_id=None,
        )

        with patch.object(MODULE, "load_ces_runner_module", return_value=FakeRunner()):
            result = run_suites_command(args)

        self.assertEqual(result, 0)
        summary = json.loads((temp_dir / "artifacts" / "langsmith-live-summary.json").read_text())
        self.assertEqual(summary["execution_failure_count"], 0)
        self.assertEqual(summary["evaluation_failure_count"], 1)
        self.assertEqual(summary["results"][0]["status"], "completed_with_evaluation_failures")

    def test_load_suite_langsmith_config_requires_dataset_name(self) -> None:
        temp_dir = self.create_temp_dir()
        suite_path = self.write_json(
            temp_dir / "suite.json",
            {
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "evaluations": [{"name": "appointment_routing_english"}],
                "langsmith": {},
            },
        )
        with self.assertRaises(LangSmithExperimentError):
            load_suite_langsmith_config(suite_path)

    def test_build_langsmith_upload_body_maps_summary_to_external_experiment_payload(self) -> None:
        temp_dir = self.create_temp_dir()
        evaluation_path = self.write_json(
            temp_dir / "appointment_booking_branch_flow.json",
            {
                "displayName": "appointment_booking_branch_flow",
                "scenario": {
                    "task": "Book an appointment in Berlin",
                    "scenarioExpectations": [
                        {"expectedResult": "Routes to advisory_appointment_agent."},
                        {"expectedResult": "Asks for confirmation before booking."},
                    ],
                    "maxTurns": 10,
                },
            },
        )
        summary = {
            "project": "voice-banking-poc",
            "location": "us",
            "app_id": "acme-voice-us",
            "run_name": "projects/p/locations/us/apps/a/evaluationRuns/run-1",
            "run_display_name": "langsmith-advisory-booking-20260327T120000Z",
            "run_state": "COMPLETED",
            "suite_passed": True,
            "suite_failures": 0,
            "latency_report_present": True,
            "evaluations": [
                {
                    "name": "appointment_booking_branch_flow",
                    "kind": "scenario",
                    "owner_agent": "advisory_appointment_agent",
                    "labels": ["booking", "langsmith"],
                    "local_path": str(evaluation_path),
                    "expected": {
                        "min_pass_rate": 0.67,
                        "min_completed_results": 3,
                        "max_error_count": 0,
                    },
                    "actual": {
                        "results_count": 3,
                        "completed_count": 3,
                        "passed_count": 3,
                        "failed_count": 0,
                        "error_count": 0,
                        "pending_count": 0,
                        "pass_rate": 1.0,
                    },
                    "result_names": ["r1", "r2", "r3"],
                    "passed": True,
                    "assertion_failures": [],
                }
            ],
        }
        run_resource = {
            "createTime": "2026-03-27T12:00:00Z",
            "updateTime": "2026-03-27T12:05:00Z",
        }
        config = SuiteLangSmithConfig(
            dataset_name="ces-advisory-booking-live",
            dataset_description="Live booking dataset",
            experiment_description="Booking experiment",
            experiment_metadata={"journey_type": "booking"},
        )

        payload = build_langsmith_upload_body(
            suite_path=temp_dir / "suite.json",
            suite_config=config,
            summary=summary,
            run_resource=run_resource,
            completed_operation=None,
            result_payloads_by_name={},
        )

        self.assertEqual(payload["dataset_name"], "ces-advisory-booking-live")
        self.assertEqual(payload["experiment_name"], "langsmith-advisory-booking-20260327T120000Z")
        self.assertEqual(payload["experiment_start_time"], "2026-03-27T12:00:00Z")
        self.assertEqual(payload["experiment_end_time"], "2026-03-27T12:05:00Z")
        self.assertEqual(payload["experiment_metadata"]["journey_type"], "booking")
        self.assertEqual(len(payload["results"]), 1)

        row = payload["results"][0]
        self.assertEqual(row["inputs"]["task"], "Book an appointment in Berlin")
        self.assertEqual(row["expected_outputs"]["target_agent"], "advisory_appointment_agent")
        self.assertTrue(row["actual_outputs"]["passed"])
        self.assertEqual(row["run_metadata"]["journey_type"], "booking")
        self.assertEqual(
            [score["key"] for score in row["evaluation_scores"]],
            ["threshold_pass", "pass_rate", "error_free"],
        )

    def test_build_langsmith_upload_body_uses_operation_metadata_when_run_resource_has_no_end_time(self) -> None:
        temp_dir = self.create_temp_dir()
        evaluation_path = self.write_json(
            temp_dir / "appointment_routing_english.json",
            {
                "displayName": "appointment_routing_english",
                "golden": {
                    "turns": [
                        {
                            "steps": [
                                {
                                    "userInput": {"text": "I want an appointment"},
                                    "expectation": {
                                        "agentTransfer": {"targetAgent": "advisory_appointment_agent"}
                                    },
                                }
                            ]
                        }
                    ]
                },
            },
        )
        summary = {
            "project": "voice-banking-poc",
            "location": "us",
            "app_id": "acme-voice-us",
            "run_name": "projects/p/locations/us/apps/a/evaluationRuns/run-1",
            "run_display_name": "langsmith-advisory-routing-20260327T120000Z",
            "run_state": "COMPLETED",
            "suite_passed": False,
            "suite_failures": 1,
            "latency_report_present": True,
            "evaluations": [
                {
                    "name": "appointment_routing_english",
                    "kind": "golden",
                    "owner_agent": "voice_banking_agent",
                    "labels": ["routing"],
                    "local_path": str(evaluation_path),
                    "expected": {
                        "min_pass_rate": 1.0,
                        "min_completed_results": 1,
                        "max_error_count": 0,
                    },
                    "actual": {
                        "results_count": 1,
                        "completed_count": 1,
                        "passed_count": 0,
                        "failed_count": 1,
                        "error_count": 0,
                        "pending_count": 0,
                        "pass_rate": 0.0,
                    },
                    "result_names": ["r1"],
                    "passed": False,
                    "assertion_failures": ["pass_rate=0.000 below required 1.000"],
                }
            ],
        }
        run_resource = {
            "createTime": "2026-03-27T12:00:00Z",
        }
        completed_operation = {
            "metadata": {
                "createTime": "2026-03-27T11:59:58Z",
                "endTime": "2026-03-27T12:05:00Z",
            }
        }
        config = SuiteLangSmithConfig(
            dataset_name="ces-advisory-routing-live",
            dataset_description="Live routing dataset",
            experiment_description="Routing experiment",
            experiment_metadata={"journey_type": "routing"},
        )

        payload = build_langsmith_upload_body(
            suite_path=temp_dir / "suite.json",
            suite_config=config,
            summary=summary,
            run_resource=run_resource,
            completed_operation=completed_operation,
            result_payloads_by_name={},
        )

        self.assertEqual(payload["experiment_start_time"], "2026-03-27T12:00:00Z")
        self.assertEqual(payload["experiment_end_time"], "2026-03-27T12:05:00Z")

    def test_build_langsmith_upload_body_adds_routing_instruction_scores_when_result_payload_is_available(self) -> None:
        temp_dir = self.create_temp_dir()
        evaluation_path = self.write_json(
            temp_dir / "appointment_routing_german.json",
            {
                "displayName": "appointment_routing_german",
                "golden": {
                    "turns": [
                        {
                            "steps": [
                                {
                                    "userInput": {"text": "Ich möchte einen Beratungstermin vereinbaren"},
                                    "expectation": {
                                        "agentTransfer": {"targetAgent": "advisory_appointment_agent"}
                                    },
                                }
                            ]
                        }
                    ]
                },
            },
        )
        summary = {
            "project": "voice-banking-poc",
            "location": "us",
            "app_id": "acme-voice-us",
            "run_name": "run-2",
            "run_display_name": "routing-german-run",
            "run_state": "COMPLETED",
            "suite_passed": False,
            "suite_failures": 1,
            "latency_report_present": True,
            "evaluations": [
                {
                    "name": "appointment_routing_german",
                    "kind": "golden",
                    "owner_agent": "voice_banking_agent",
                    "labels": ["routing"],
                    "local_path": str(evaluation_path),
                    "expected": {
                        "min_pass_rate": 1.0,
                        "min_completed_results": 1,
                        "max_error_count": 0,
                    },
                    "actual": {
                        "results_count": 1,
                        "completed_count": 1,
                        "passed_count": 0,
                        "failed_count": 1,
                        "error_count": 0,
                        "pending_count": 0,
                        "pass_rate": 0.0,
                    },
                    "result_names": ["result-1"],
                    "passed": False,
                    "assertion_failures": ["pass_rate=0.000 below required 1.000"],
                }
            ],
        }
        result_payload = {
            "name": "result-1",
            "goldenResult": {
                "turnReplayResults": [
                    {
                        "expectationOutcome": [
                            {
                                "observedAgentResponse": {
                                    "chunks": [{"text": "Worum geht es bei dem Beratungstermin?"}]
                                }
                            }
                        ]
                    }
                ]
            },
        }
        config = SuiteLangSmithConfig(
            dataset_name="ces-advisory-routing-live",
            dataset_description="Live routing dataset",
            experiment_description="Routing experiment",
            experiment_metadata={"journey_type": "routing"},
        )

        payload = build_langsmith_upload_body(
            suite_path=temp_dir / "suite.json",
            suite_config=config,
            summary=summary,
            run_resource={"createTime": "2026-03-27T12:00:00Z"},
            completed_operation={"metadata": {"endTime": "2026-03-27T12:05:00Z"}},
            result_payloads_by_name={"result-1": result_payload},
        )

        row = payload["results"][0]
        self.assertEqual(
            row["actual_outputs"]["first_agent_response"],
            "Worum geht es bei dem Beratungstermin?",
        )
        self.assertEqual(
            [score["key"] for score in row["evaluation_scores"]],
            [
                "threshold_pass",
                "pass_rate",
                "error_free",
                "same_language_as_user",
                "no_greeting_on_first_turn",
                "single_question_first_turn",
                "confirmation_before_lifecycle_action",
                "no_ungrounded_slot_or_status_claims",
                "first_turn_stays_in_scope",
            ],
        )
        scores_by_key = {score["key"]: score for score in row["evaluation_scores"]}
        self.assertEqual(scores_by_key["same_language_as_user"]["score"], 1.0)
        self.assertEqual(scores_by_key["no_greeting_on_first_turn"]["score"], 1.0)
        self.assertEqual(scores_by_key["single_question_first_turn"]["score"], 1.0)
        self.assertEqual(scores_by_key["confirmation_before_lifecycle_action"]["value"], "not_observed")
        self.assertEqual(scores_by_key["no_ungrounded_slot_or_status_claims"]["value"], "not_observed")
        self.assertEqual(scores_by_key["first_turn_stays_in_scope"]["value"], "pass")


if __name__ == "__main__":
    unittest.main()
