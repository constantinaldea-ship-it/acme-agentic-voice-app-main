"""Created by Codex on 2026-03-29."""

from __future__ import annotations

import base64
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from argparse import Namespace
from pathlib import Path
from unittest import mock


def load_runner_module():
    runner_path = (
        Path(__file__).resolve().parents[1] / "voice-eval-runner.py"
    )
    spec = importlib.util.spec_from_file_location("voice_eval_runner", runner_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load runner module from {runner_path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


voice_eval_runner = load_runner_module()


class FakeBidiConnection:
    def __init__(self, recv_messages: list[str]) -> None:
        self.recv_messages = list(recv_messages)
        self.sent_messages: list[str] = []

    def send(self, message: str) -> None:
        self.sent_messages.append(message)

    def recv(self, timeout: float | None = None) -> str:
        if not self.recv_messages:
            raise TimeoutError(f"No more fake websocket messages available (timeout={timeout}).")
        return self.recv_messages.pop(0)


class FakeBidiConnector:
    def __init__(self, connection: FakeBidiConnection) -> None:
        self.connection = connection
        self.args: tuple[object, ...] = ()
        self.kwargs: dict[str, object] = {}

    def __call__(self, *args: object, **kwargs: object) -> "FakeBidiConnector":
        self.args = args
        self.kwargs = kwargs
        return self

    def __enter__(self) -> FakeBidiConnection:
        return self.connection

    def __exit__(self, exc_type, exc, tb) -> bool:
        return False


class VoiceEvalRunnerTests(unittest.TestCase):
    def test_normalize_text_collapses_case_and_punctuation(self) -> None:
        self.assertEqual(
            voice_eval_runner.normalize_text("I'd like to book an advisory appointment."),
            "i d like to book an advisory appointment",
        )

    def test_load_suite_accepts_sidecar_fixture_and_linked_evaluation(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "simple-voice-routing-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        self.assertEqual(suite.display_name, "simple-voice-routing")
        self.assertEqual(len(suite.scenarios), 1)
        self.assertEqual(suite.scenarios[0].linked_evaluation_name, "appointment_routing_english")
        self.assertEqual(len(suite.scenarios[0].turns), 1)

    def test_extract_first_user_input_text_returns_golden_user_turn(self) -> None:
        evaluation_path = (
            Path(__file__).resolve().parents[2]
            / "acme_voice_agent"
            / "evaluations"
            / "appointment_routing_english"
            / "appointment_routing_english.json"
        )
        text = voice_eval_runner.extract_first_user_input_text(evaluation_path)
        self.assertEqual(text, "I'd like to book an advisory appointment")

    def test_extract_scenario_start_text_returns_quoted_task_utterance(self) -> None:
        evaluation_path = (
            Path(__file__).resolve().parents[2]
            / "acme_voice_agent"
            / "evaluations"
            / "appointment_booking_branch_flow"
            / "appointment_booking_branch_flow.json"
        )
        text = voice_eval_runner.extract_scenario_start_text(evaluation_path)
        self.assertEqual(text, "I want to book an advisory appointment at a branch in Berlin")

    def test_load_suite_accepts_multistep_scenario_and_scenario_linkage(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        scenario = suite.scenarios[0]
        self.assertEqual(suite.display_name, "multistep-appointment-booking")
        self.assertEqual(scenario.linked_evaluation_name, "appointment_booking_branch_flow")
        self.assertEqual(scenario.linked_evaluation_kind, "scenario")
        self.assertEqual(len(scenario.turns), 6)
        self.assertEqual(scenario.turns[0].name, "request_branch_booking")
        self.assertEqual(scenario.turns[2].name, "confirm_branch_location")
        self.assertEqual(scenario.turns[2].expected_transcript, "Yes, that's correct.")

    def test_load_suite_accepts_german_multistep_scenario(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-german-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        scenario = suite.scenarios[0]
        self.assertEqual(suite.display_name, "multistep-appointment-booking-german")
        self.assertEqual(scenario.linked_evaluation_name, "appointment_booking_branch_flow_german")
        self.assertEqual(scenario.linked_evaluation_kind, "scenario")
        self.assertEqual(scenario.turns[0].expected_transcript, "Ich möchte einen Beratungstermin in einer Filiale in Berlin buchen")

    def test_load_suite_accepts_openai_transcriber(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "simple-voice-routing-openai-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        self.assertEqual(suite.scenarios[0].turns[0].transcriber.model, "gpt-4o-mini-transcribe")

    def test_transcribe_with_openai_uses_curl_response_text(self) -> None:
        audio_path = (
            Path(__file__).resolve().parents[1]
            / "fixtures"
            / "appointment-routing-english"
            / "utterance.wav"
        )
        spec = voice_eval_runner.OpenAITranscriberSpec(
            model="gpt-4o-mini-transcribe",
            language="en",
            prompt=None,
            api_key_env="OPENAI_API_KEY",
            endpoint="https://api.openai.com/v1/audio/transcriptions",
        )
        with mock.patch.dict("os.environ", {"OPENAI_API_KEY": "test-key"}, clear=False):
            with mock.patch.object(voice_eval_runner.subprocess, "run") as mocked_run:
                mocked_run.return_value = mock.Mock(
                    stdout="{\"text\":\"I'd like to book an advisory appointment\"}\n200",
                    stderr="",
                )
                transcript = voice_eval_runner.transcribe_with_openai(audio_path, spec)
        self.assertEqual(transcript, "I'd like to book an advisory appointment")

    def test_run_suite_writes_summary_and_passes_simple_fixture(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "simple-voice-routing-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = voice_eval_runner.run_suite(suite, artifacts_dir=Path(temp_dir))
            self.assertEqual(exit_code, 0)
            summary_path = Path(temp_dir) / "summary.json"
            self.assertTrue(summary_path.is_file())

    def test_run_suite_writes_multistep_turn_artifacts(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        with tempfile.TemporaryDirectory() as temp_dir:
            exit_code = voice_eval_runner.run_suite(suite, artifacts_dir=Path(temp_dir))
            self.assertEqual(exit_code, 0)
            scenario_artifact = Path(temp_dir) / "01-appointment_booking_branch_flow_voice.json"
            payload = voice_eval_runner.load_json_document(scenario_artifact)
            self.assertEqual(payload["turn_count"], len(suite.scenarios[0].turns))
            self.assertEqual(payload["matched_turn_count"], len(suite.scenarios[0].turns))
            self.assertEqual(payload["linked_evaluation_kind"], "scenario")
            self.assertEqual(len(payload["turns"]), len(suite.scenarios[0].turns))
            self.assertEqual(payload["turns"][0]["turn"], "request_branch_booking")
            self.assertEqual(payload["turns"][2]["turn"], "confirm_branch_location")

    def test_run_suite_formats_colorized_pass_output_like_smoke_runner(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "simple-voice-routing-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)

        with tempfile.TemporaryDirectory() as temp_dir:
            stdout = io.StringIO()
            with mock.patch.object(voice_eval_runner, "supports_color", return_value=True):
                with mock.patch("sys.stdout", stdout):
                    exit_code = voice_eval_runner.run_suite(suite, artifacts_dir=Path(temp_dir))

        self.assertEqual(exit_code, 0)
        output = stdout.getvalue()
        self.assertIn("Running voice suite:", output)
        self.assertIn("\033[1m\033[32mPASS\033[0m", output)
        self.assertIn("\033[1m\033[32mSUITE PASSED\033[0m", output)

    def test_run_suite_formats_colorized_fail_output_like_smoke_runner(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "simple-voice-routing-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)

        with tempfile.TemporaryDirectory() as temp_dir:
            stdout = io.StringIO()
            with mock.patch.object(voice_eval_runner, "supports_color", return_value=True):
                with mock.patch.object(voice_eval_runner, "transcribe_turn", return_value="wrong transcript"):
                    with mock.patch("sys.stdout", stdout):
                        exit_code = voice_eval_runner.run_suite(suite, artifacts_dir=Path(temp_dir))

        self.assertEqual(exit_code, 1)
        output = stdout.getvalue()
        self.assertIn("\033[1m\033[31mFAIL\033[0m", output)
        self.assertIn("Artifact:", output)
        self.assertIn("\033[1m\033[31mSUITE FAILED\033[0m", output)

    def test_resolve_remote_replay_config_uses_env_defaults(self) -> None:
        args = Namespace(
            remote_replay=True,
            remote_replay_mode="observe",
            project=None,
            location=None,
            app_id=None,
            endpoint=None,
            remote_timeout_seconds=123,
            remote_poll_interval_seconds=7,
        )
        with mock.patch.dict(
            "os.environ",
            {
                "GCP_PROJECT_ID": "voice-banking-poc",
                "GCP_LOCATION": "eu",
                "CES_APP_ID": "acme-voice-eu",
                "CES_ENDPOINT": "https://ces.eu.rep.googleapis.com",
            },
            clear=False,
        ):
            config = voice_eval_runner.resolve_remote_replay_config(args)
        self.assertEqual(config.project, "voice-banking-poc")
        self.assertEqual(config.location, "eu")
        self.assertEqual(config.app_id, "acme-voice-eu")
        self.assertEqual(config.endpoint, "https://ces.eu.rep.googleapis.com")
        self.assertEqual(config.mode, "observe")
        self.assertEqual(config.timeout_seconds, 123)
        self.assertEqual(config.poll_interval_seconds, 7)

    def test_resolve_ces_session_config_uses_env_defaults(self) -> None:
        args = Namespace(
            ces_session=True,
            ces_session_mode="assert",
            project=None,
            location=None,
            app_id=None,
            deployment_id=None,
            endpoint=None,
            entry_agent=None,
            session_timeout_seconds=42,
        )
        with mock.patch.dict(
            "os.environ",
            {
                "GCP_PROJECT_ID": "voice-banking-poc",
                "GCP_LOCATION": "eu",
                "CES_APP_ID": "acme-voice-eu",
                "CES_DEPLOYMENT_ID": "api-access-1",
                "CES_ENDPOINT": "https://ces.eu.rep.googleapis.com",
            },
            clear=False,
        ):
            config = voice_eval_runner.resolve_ces_session_config(args)
        self.assertEqual(config.project, "voice-banking-poc")
        self.assertEqual(config.location, "eu")
        self.assertEqual(config.app_id, "acme-voice-eu")
        self.assertEqual(config.deployment_id, "api-access-1")
        self.assertEqual(config.endpoint, "https://ces.eu.rep.googleapis.com")
        self.assertEqual(config.mode, "assert")
        self.assertEqual(config.timeout_seconds, 42)

    def test_bidi_session_url_uses_websocket_transport(self) -> None:
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="assert",
            timeout_seconds=60,
            entry_agent=None,
        )
        self.assertEqual(
            voice_eval_runner.bidi_session_url(config),
            "wss://ces.googleapis.com/ws/google.cloud.ces.v1.SessionService/BidiRunSession/locations/eu",
        )

    def test_preflight_ces_session_target_reports_swapped_app_and_deployment_ids(self) -> None:
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            entry_agent=None,
        )

        with mock.patch.object(
            voice_eval_runner,
            "http_request_raw",
            return_value={
                "status": 404,
                "body": '{"error":{"message":"not found"}}',
                "json": {"error": {"message": "not found"}},
            },
        ):
            with mock.patch.object(
                voice_eval_runner,
                "list_collection",
                return_value=[
                    {
                        "name": "projects/voice-banking-poc/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
                        "displayName": "acme_voice_agent_20260329_082016",
                    }
                ],
            ):
                with self.assertRaises(voice_eval_runner.VoiceEvaluationError) as ctx:
                    voice_eval_runner.preflight_ces_session_target(config, token="token")

        self.assertIn("Configured CES_APP_ID 'acme-voice-eu' was not found", str(ctx.exception))
        self.assertIn("were swapped", str(ctx.exception))

    def test_preflight_ces_session_target_reports_missing_api_access_deployments(self) -> None:
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
            deployment_id="e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            entry_agent=None,
        )

        with mock.patch.object(
            voice_eval_runner,
            "http_request_raw",
            return_value={"status": 200, "body": "{}", "json": {}},
        ):
            with mock.patch.object(
                voice_eval_runner,
                "list_collection",
                return_value=[],
            ):
                with self.assertRaises(voice_eval_runner.VoiceEvaluationError) as ctx:
                    voice_eval_runner.preflight_ces_session_target(config, token="token")

        self.assertIn("No API access deployments were found", str(ctx.exception))
        self.assertIn("app ID was copied into the deployment slot", str(ctx.exception))

    def test_preflight_ces_session_target_resolves_entry_agent_display_name(self) -> None:
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            entry_agent="voice_banking_agent",
        )

        with mock.patch.object(
            voice_eval_runner,
            "http_request_raw",
            return_value={"status": 200, "body": "{}", "json": {}},
        ):
            with mock.patch.object(
                voice_eval_runner,
                "list_collection",
                side_effect=[
                    [
                        {
                            "name": "projects/voice-banking-poc/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb/deployments/api-access-1",
                            "displayName": "API access",
                        }
                    ],
                    [
                        {
                            "name": "projects/voice-banking-poc/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb/agents/2591b63c-a380-47ce-9391-cc26e550e3ae",
                            "displayName": "voice_banking_agent",
                        }
                    ],
                ],
            ):
                resolved = voice_eval_runner.preflight_ces_session_target(config, token="token")

        self.assertEqual(
            resolved.entry_agent,
            "projects/voice-banking-poc/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb/agents/2591b63c-a380-47ce-9391-cc26e550e3ae",
        )

    def test_preflight_ces_session_target_normalizes_full_deployment_resource_name(self) -> None:
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="e88e13e5-14d0-4f87-93cd-0ee92ec318eb",
            deployment_id="projects/1041912723804/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb/deployments/e1a8528c-02a0-449d-9221-e802679419e6",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            entry_agent=None,
        )

        with mock.patch.object(
            voice_eval_runner,
            "http_request_raw",
            return_value={"status": 200, "body": "{}", "json": {}},
        ):
            with mock.patch.object(
                voice_eval_runner,
                "list_collection",
                return_value=[
                    {
                        "name": "projects/voice-banking-poc/locations/eu/apps/e88e13e5-14d0-4f87-93cd-0ee92ec318eb/deployments/e1a8528c-02a0-449d-9221-e802679419e6",
                        "displayName": "testing",
                    }
                ],
            ):
                resolved = voice_eval_runner.preflight_ces_session_target(config, token="token")

        self.assertEqual(resolved.deployment_id, "e1a8528c-02a0-449d-9221-e802679419e6")

    def test_load_session_audio_payloads_requires_consistent_audio_format(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        scenario = suite.scenarios[0]
        payload_24k = voice_eval_runner.RuntimeAudioPayload(
            audio_bytes=b"\x00\x01" * 240,
            audio_encoding="LINEAR16",
            sample_rate_hertz=24000,
            channel_count=1,
            sample_width_bytes=2,
            frame_count=240,
        )
        payload_16k = voice_eval_runner.RuntimeAudioPayload(
            audio_bytes=b"\x00\x01" * 160,
            audio_encoding="LINEAR16",
            sample_rate_hertz=16000,
            channel_count=1,
            sample_width_bytes=2,
            frame_count=160,
        )

        with mock.patch.object(
            voice_eval_runner,
            "load_runtime_audio_payload",
            side_effect=[payload_24k, payload_16k],
        ):
            with self.assertRaises(voice_eval_runner.VoiceEvaluationError):
                voice_eval_runner.load_session_audio_payloads(scenario)

    def test_summarize_ces_session_streams_audio_turns_over_bidi_session(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        scenario = suite.scenarios[0]
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="assert",
            timeout_seconds=60,
            entry_agent="voice_banking_agent",
        )
        payload = voice_eval_runner.RuntimeAudioPayload(
            audio_bytes=b"\x00\x01" * 2400,
            audio_encoding="LINEAR16",
            sample_rate_hertz=24000,
            channel_count=1,
            sample_width_bytes=2,
            frame_count=2400,
        )
        fake_messages = []
        for turn_index in range(1, len(scenario.turns) + 1):
            fake_messages.append(
                json.dumps({"recognitionResult": {"transcript": f"turn {turn_index}"}})
            )
            fake_messages.append(
                json.dumps(
                    {
                        "sessionOutput": {
                            "text": f"Agent reply {turn_index}",
                            "turnCompleted": True,
                            "turnIndex": turn_index,
                        }
                    }
                )
            )
        fake_connection = FakeBidiConnection(fake_messages)
        fake_connector = FakeBidiConnector(fake_connection)

        with mock.patch.object(
            voice_eval_runner,
            "load_session_audio_payloads",
            return_value=tuple(payload for _ in scenario.turns),
        ):
            with mock.patch.object(
                voice_eval_runner,
                "preflight_ces_session_target",
                return_value=config,
            ):
                with mock.patch.object(
                voice_eval_runner,
                "get_bidi_websocket_connect",
                return_value=fake_connector,
                ):
                    result = voice_eval_runner.summarize_ces_session(
                        config,
                        token="token",
                        scenario=scenario,
                    )

        self.assertTrue(result["passed"])
        self.assertEqual(len(result["turns"]), len(scenario.turns))
        self.assertEqual(
            fake_connector.args[0],
            "wss://ces.googleapis.com/ws/google.cloud.ces.v1.SessionService/BidiRunSession/locations/eu",
        )
        self.assertEqual(
            fake_connector.kwargs["additional_headers"],
            {
                "Authorization": "Bearer token",
                "Content-Type": "application/json",
                "x-goog-request-params": "location=locations/eu",
            },
        )
        self.assertIsNone(fake_connector.kwargs["proxy"])
        self.assertIsNone(fake_connector.kwargs["compression"])

        first_message = json.loads(fake_connection.sent_messages[0])
        self.assertIn("config", first_message)
        self.assertEqual(first_message["config"]["session"], result["session"])
        self.assertTrue(first_message["config"]["deployment"].endswith("/deployments/api-access-1"))
        self.assertTrue(first_message["config"]["entryAgent"].endswith("/agents/voice_banking_agent"))
        self.assertEqual(first_message["config"]["inputAudioConfig"]["audioEncoding"], "LINEAR16")
        self.assertEqual(first_message["config"]["inputAudioConfig"]["sampleRateHertz"], 24000)
        self.assertEqual(first_message["config"]["outputAudioConfig"]["sampleRateHertz"], 24000)

        realtime_messages = [json.loads(message) for message in fake_connection.sent_messages[1:]]
        self.assertTrue(realtime_messages)
        self.assertTrue(all("realtimeInput" in message for message in realtime_messages))
        self.assertTrue(all(message["realtimeInput"]["audio"] for message in realtime_messages))
        self.assertEqual(result["transport"], "BidiRunSession")
        self.assertEqual(result["turns"][0]["recognition_transcript"], "turn 1")
        self.assertEqual(result["turns"][0]["output_texts"], ["Agent reply 1"])

    def test_summarize_ces_session_waits_for_output_audio_between_turns(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        scenario = suite.scenarios[0]
        config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="assert",
            timeout_seconds=60,
            entry_agent="voice_banking_agent",
        )
        payload = voice_eval_runner.RuntimeAudioPayload(
            audio_bytes=b"\x00\x01" * 2400,
            audio_encoding="LINEAR16",
            sample_rate_hertz=24000,
            channel_count=1,
            sample_width_bytes=2,
            frame_count=2400,
        )

        audio_chunk = base64.b64encode(b"\x00\x01" * 24000).decode("ascii")
        fake_messages = []
        for turn_index in range(1, len(scenario.turns) + 1):
            fake_messages.append(
                json.dumps({"recognitionResult": {"transcript": f"turn {turn_index}"}})
            )
            fake_messages.append(
                json.dumps(
                    {
                        "sessionOutput": {
                            "text": f"Agent reply {turn_index}",
                            "audio": audio_chunk,
                            "turnIndex": turn_index,
                        }
                    }
                )
            )
            fake_messages.append(
                json.dumps(
                    {
                        "sessionOutput": {
                            "turnCompleted": True,
                            "turnIndex": turn_index,
                        }
                    }
                )
            )
        fake_connection = FakeBidiConnection(fake_messages)
        fake_connector = FakeBidiConnector(fake_connection)

        with mock.patch.object(
            voice_eval_runner,
            "load_session_audio_payloads",
            return_value=tuple(payload for _ in scenario.turns),
        ):
            with mock.patch.object(
                voice_eval_runner,
                "preflight_ces_session_target",
                return_value=config,
            ):
                with mock.patch.object(
                    voice_eval_runner,
                    "get_bidi_websocket_connect",
                    return_value=fake_connector,
                ):
                    with mock.patch.object(voice_eval_runner.time, "sleep") as mocked_sleep:
                        result = voice_eval_runner.summarize_ces_session(
                            config,
                            token="token",
                            scenario=scenario,
                        )

        self.assertTrue(result["passed"])
        self.assertEqual(mocked_sleep.call_count, len(scenario.turns) - 1)
        for call in mocked_sleep.call_args_list:
            self.assertAlmostEqual(call.args[0], 1.25, places=2)
        self.assertAlmostEqual(result["turns"][0]["playback_wait_seconds"], 1.25, places=2)

    def test_interturn_wait_seconds_caps_long_playback_waits(self) -> None:
        turn_summary = {
            "audio_output_bytes": 48_000 * 30,
            "sample_rate_hertz": 24_000,
            "channel_count": 1,
            "sample_width_bytes": 2,
        }

        self.assertEqual(
            voice_eval_runner.interturn_wait_seconds(turn_summary),
            voice_eval_runner.DEFAULT_SESSION_INTERTURN_MAX_WAIT_MS / 1000,
        )

    def test_run_suite_writes_remote_replay_payload_in_observe_mode(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        remote_config = voice_eval_runner.RemoteReplayConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            poll_interval_seconds=1,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(voice_eval_runner, "summarize_remote_replay") as mocked_remote:
                    mocked_remote.return_value = {
                        "requested": True,
                        "passed": True,
                        "evaluation_display_name": "appointment_booking_branch_flow",
                        "evaluation_run": "projects/p/locations/eu/apps/a/evaluationRuns/run-1",
                    }
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        remote_replay=remote_config,
                    )

            self.assertEqual(exit_code, 0)
            summary_payload = voice_eval_runner.load_json_document(Path(temp_dir) / "summary.json")
            scenario_payload = voice_eval_runner.load_json_document(
                Path(temp_dir) / "01-appointment_booking_branch_flow_voice.json"
            )
            self.assertTrue(summary_payload["remote_replay_requested"])
            self.assertEqual(summary_payload["remote_replay_mode"], "observe")
            self.assertEqual(summary_payload["remote_pass_count"], 1)
            self.assertEqual(scenario_payload["remote_replay_mode"], "observe")
            self.assertEqual(scenario_payload["remote_replay"]["evaluation_run"], "projects/p/locations/eu/apps/a/evaluationRuns/run-1")
            mocked_remote.assert_called_once()

    def test_run_suite_writes_ces_session_payload_in_observe_mode(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        ces_session_config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            entry_agent=None,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(voice_eval_runner, "summarize_ces_session") as mocked_session:
                    mocked_session.return_value = {
                        "requested": True,
                        "passed": True,
                        "mode": "observe",
                        "session": "projects/p/locations/eu/apps/a/sessions/s-1",
                    }
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        ces_session=ces_session_config,
                    )

            self.assertEqual(exit_code, 0)
            summary_payload = voice_eval_runner.load_json_document(Path(temp_dir) / "summary.json")
            scenario_payload = voice_eval_runner.load_json_document(
                Path(temp_dir) / "01-appointment_booking_branch_flow_voice.json"
            )
            self.assertTrue(summary_payload["ces_session_requested"])
            self.assertEqual(summary_payload["ces_session_mode"], "observe")
            self.assertEqual(summary_payload["ces_session_pass_count"], 1)
            self.assertEqual(scenario_payload["ces_session_mode"], "observe")
            self.assertEqual(scenario_payload["ces_session"]["session"], "projects/p/locations/eu/apps/a/sessions/s-1")
            mocked_session.assert_called_once()

    def test_run_suite_assert_mode_fails_on_ces_session_failure(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        ces_session_config = voice_eval_runner.CesSessionConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            deployment_id="api-access-1",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="assert",
            timeout_seconds=60,
            entry_agent=None,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(
                    voice_eval_runner,
                    "summarize_ces_session",
                    return_value={"requested": True, "passed": False, "mode": "assert"},
                ):
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        ces_session=ces_session_config,
                    )

        self.assertEqual(exit_code, 1)

    def test_run_suite_observe_mode_does_not_fail_on_remote_replay_failure(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        remote_config = voice_eval_runner.RemoteReplayConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            poll_interval_seconds=1,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(voice_eval_runner, "summarize_remote_replay", return_value={"requested": True, "passed": False}):
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        remote_replay=remote_config,
                    )

        self.assertEqual(exit_code, 0)

    def test_run_suite_observe_mode_records_remote_replay_exception(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        remote_config = voice_eval_runner.RemoteReplayConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            poll_interval_seconds=1,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(
                    voice_eval_runner,
                    "summarize_remote_replay",
                    side_effect=voice_eval_runner.VoiceEvaluationError("timed out waiting for remote CES replay"),
                ):
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        remote_replay=remote_config,
                    )

            scenario_payload = voice_eval_runner.load_json_document(
                Path(temp_dir) / "01-appointment_booking_branch_flow_voice.json"
            )

        self.assertEqual(exit_code, 0)
        self.assertEqual(scenario_payload["remote_replay"]["error"], "timed out waiting for remote CES replay")

    def test_run_suite_assert_mode_fails_on_remote_replay_failure(self) -> None:
        suite_path = (
            Path(__file__).resolve().parents[1]
            / "suites"
            / "multistep-appointment-booking-suite.json"
        )
        suite = voice_eval_runner.load_suite(suite_path)
        remote_config = voice_eval_runner.RemoteReplayConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="assert",
            timeout_seconds=60,
            poll_interval_seconds=1,
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            with mock.patch.object(voice_eval_runner, "get_access_token", return_value="token"):
                with mock.patch.object(voice_eval_runner, "summarize_remote_replay", return_value={"requested": True, "passed": False}):
                    exit_code = voice_eval_runner.run_suite(
                        suite,
                        artifacts_dir=Path(temp_dir),
                        remote_replay=remote_config,
                    )

        self.assertEqual(exit_code, 1)

    def test_load_results_for_run_uses_explicit_run_results(self) -> None:
        remote_config = voice_eval_runner.RemoteReplayConfig(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            mode="observe",
            timeout_seconds=60,
            poll_interval_seconds=1,
        )
        run_resource = {
            "evaluationResults": [
                "projects/p/locations/eu/apps/a/evaluations/e-1/results/result-1",
                "projects/p/locations/eu/apps/a/evaluations/e-2/results/result-2",
            ]
        }
        with mock.patch.object(voice_eval_runner, "get_resource") as mocked_get_resource:
            mocked_get_resource.side_effect = [
                {"name": "result-1", "evaluationRun": "projects/p/locations/eu/apps/a/evaluationRuns/run-1"},
                {"name": "result-2", "evaluationRun": "projects/p/locations/eu/apps/a/evaluationRuns/run-2"},
            ]
            results = voice_eval_runner.load_results_for_run(
                remote_config,
                token="token",
                run_resource=run_resource,
                run_name="projects/p/locations/eu/apps/a/evaluationRuns/run-1",
            )
        self.assertEqual(
            results,
            [{"name": "result-1", "evaluationRun": "projects/p/locations/eu/apps/a/evaluationRuns/run-1"}],
        )
        self.assertEqual(mocked_get_resource.call_count, 2)

    def test_main_returns_130_on_keyboard_interrupt(self) -> None:
        args = Namespace(
            command="run-suite",
            suite="./suites/simple-voice-routing-suite.json",
            artifacts_dir=None,
            ces_session=False,
            ces_session_mode="assert",
            remote_replay=False,
            remote_replay_mode="observe",
            project=None,
            location=None,
            app_id=None,
            deployment_id=None,
            endpoint=None,
            entry_agent=None,
            session_timeout_seconds=60,
            remote_timeout_seconds=300,
            remote_poll_interval_seconds=5,
        )
        stderr = io.StringIO()
        with mock.patch.object(voice_eval_runner, "parse_args", return_value=args):
            with mock.patch.object(voice_eval_runner, "load_suite", side_effect=KeyboardInterrupt):
                with mock.patch("sys.stderr", stderr):
                    exit_code = voice_eval_runner.main()
        self.assertEqual(exit_code, 130)
        self.assertIn("Cancelled by user.", stderr.getvalue())


if __name__ == "__main__":
    unittest.main()
