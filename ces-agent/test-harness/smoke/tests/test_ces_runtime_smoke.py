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
MODULE_PATH = SCRIPT_DIR.parent / "ces-runtime-smoke.py"
SPEC = importlib.util.spec_from_file_location("ces_runtime_smoke", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load smoke module from {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules["ces_runtime_smoke"] = MODULE
SPEC.loader.exec_module(MODULE)

SmokeError = MODULE.SmokeError
coerce_cli_value = MODULE.coerce_cli_value
evaluate_expectations = MODULE.evaluate_expectations
evaluate_toolset_contract = MODULE.evaluate_toolset_contract
expand_env_placeholders = MODULE.expand_env_placeholders
get_value_at_path = MODULE.get_value_at_path
list_resources = MODULE.list_resources
merge_expectations = MODULE.merge_expectations
parse_key_value_args = MODULE.parse_key_value_args
parse_env_file_line = MODULE.parse_env_file_line
run_suite = MODULE.run_suite
with_cloud_run_auth = MODULE.with_cloud_run_auth


class CesRuntimeSmokeUnitTests(unittest.TestCase):
    def test_get_value_at_path_reads_nested_dict(self) -> None:
        payload = {"response": {"result": {"success": True, "items": [{"id": "1"}]}}}
        self.assertTrue(get_value_at_path(payload, "response.result.success"))
        self.assertEqual(get_value_at_path(payload, "response.result.items.0.id"), "1")

    def test_evaluate_expectations_reports_mismatches(self) -> None:
        response = {"response": {"result": {"success": True, "summary": "Found Maria"}}}
        failures = evaluate_expectations(
            response,
            {
                "equals": {"response.result.success": False},
                "contains": {"response.result.summary": "Hans"},
                "exists": ["response.result.customer"],
            },
        )
        self.assertEqual(len(failures), 3)

    def test_expand_env_placeholders_rewrites_nested_values(self) -> None:
        os.environ["SMOKE_VALUE"] = "resolved"
        payload = {"args": {"header": "${SMOKE_VALUE}"}}
        self.assertEqual(
            expand_env_placeholders(payload),
            {"args": {"header": "resolved"}},
        )

    def test_expand_env_placeholders_requires_missing_variables(self) -> None:
        with self.assertRaises(SmokeError):
            expand_env_placeholders({"args": {"header": "${MISSING_SMOKE_VALUE}"}})

    def test_parse_env_file_line_supports_export_and_inline_comments(self) -> None:
        self.assertEqual(
            parse_env_file_line('export GCP_PROJECT_ID="voice-banking-poc" # comment'),
            ("GCP_PROJECT_ID", "voice-banking-poc"),
        )

    def test_coerce_cli_value_parses_scalars_and_json(self) -> None:
        self.assertTrue(coerce_cli_value("true"))
        self.assertFalse(coerce_cli_value("false"))
        self.assertIsNone(coerce_cli_value("null"))
        self.assertEqual(coerce_cli_value("42"), 42)
        self.assertEqual(coerce_cli_value('{"partner_id": "123"}'), {"partner_id": "123"})

    def test_parse_key_value_args_rejects_invalid_items(self) -> None:
        with self.assertRaises(SmokeError):
            parse_key_value_args(["missing-separator"])

    def test_parse_key_value_args_supports_json_values(self) -> None:
        self.assertEqual(
            parse_key_value_args(["enabled=true", "count=3", 'payload={"city":"Berlin"}']),
            {"enabled": True, "count": 3, "payload": {"city": "Berlin"}},
        )

    def test_evaluate_toolset_contract_reports_missing_operation(self) -> None:
        failures = evaluate_toolset_contract(
            {"openApiToolset": {"openApiSchema": "operationId: getEidpToken\npaths:\n  /oauth/token:"}},
            {
                "expect_operation_ids": ["getEidpToken", "getAuthzToken"],
                "expect_schema_contains": ["/oauth/token", "/authz/authorize"],
            },
        )
        self.assertEqual(len(failures), 2)

    def test_http_json_wraps_429_with_quota_guidance(self) -> None:
        with patch.object(
            MODULE,
            "http_request_raw",
            return_value={
                "status": 429,
                "body": '{"error":{"message":"Resource has been exhausted (e.g. check quota)."}}',
                "json": {"error": {"message": "Resource has been exhausted (e.g. check quota)."}},
                "headers": {},
            },
        ):
            with self.assertRaises(SmokeError) as exc:
                MODULE.http_json("GET", "https://example.test")
        self.assertIn("quota exhaustion", str(exc.exception))

    def test_list_resources_uses_cache_for_identical_requests(self) -> None:
        MODULE._list_resources_cached.cache_clear()
        with patch.object(
            MODULE,
            "http_json",
            return_value={"tools": [{"displayName": "get_customer_details_wrapper", "name": "projects/x/tools/y"}]},
        ) as http_json_mock:
            first = list_resources("https://ces.us.rep.googleapis.com", "token", "p", "us", "a", "tools")
            second = list_resources("https://ces.us.rep.googleapis.com", "token", "p", "us", "a", "tools")
        self.assertEqual(first, second)
        self.assertEqual(http_json_mock.call_count, 1)

    def test_with_cloud_run_auth_adds_serverless_header_for_run_app(self) -> None:
        with patch.object(MODULE, "get_cloud_run_identity_token", return_value="id-token"):
            headers = with_cloud_run_auth(
                "https://private-service-abc-uc.a.run.app/health",
                {"Authorization": "Bearer app-token"},
            )

        self.assertEqual(headers["Authorization"], "Bearer app-token")
        self.assertEqual(headers["X-Serverless-Authorization"], "Bearer id-token")

    def test_get_cloud_run_identity_token_falls_back_to_plain_gcloud_identity_token(self) -> None:
        MODULE.get_cloud_run_identity_token.cache_clear()

        def fake_run(command, check, capture_output, text):
            if command == [
                "gcloud",
                "auth",
                "print-identity-token",
                "--audiences=https://service-abc-uc.a.run.app",
            ]:
                raise MODULE.subprocess.CalledProcessError(1, command)
            if command == [
                "gcloud",
                "auth",
                "application-default",
                "print-identity-token",
                "--audiences=https://service-abc-uc.a.run.app",
            ]:
                raise FileNotFoundError()
            if command == ["gcloud", "auth", "print-identity-token"]:
                return MODULE.subprocess.CompletedProcess(command, 0, stdout="plain-user-token\n")
            raise AssertionError(f"Unexpected command: {command}")

        with (
            patch.object(MODULE.urllib.request, "urlopen", side_effect=Exception("no metadata")),
            patch.object(MODULE.subprocess, "run", side_effect=fake_run),
            patch.dict(MODULE.os.environ, {}, clear=True),
        ):
            token = MODULE.get_cloud_run_identity_token("https://service-abc-uc.a.run.app")

        self.assertEqual(token, "plain-user-token")

    def test_with_cloud_run_auth_skips_non_cloud_run_urls(self) -> None:
        headers = with_cloud_run_auth(
            "https://example.com/health",
            {"Authorization": "Bearer app-token"},
        )

        self.assertEqual(headers, {"Authorization": "Bearer app-token"})


class CesRuntimeSmokeSuiteTests(unittest.TestCase):
    def create_suite(self, payload: dict) -> Path:
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False)
        with handle:
            json.dump(payload, handle)
        self.addCleanup(lambda: Path(handle.name).unlink(missing_ok=True))
        return Path(handle.name)

    def create_artifacts_dir(self) -> Path:
        path = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: path.exists() and __import__("shutil").rmtree(path))
        return path

    def create_temp_dir(self) -> Path:
        path = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: path.exists() and __import__("shutil").rmtree(path))
        return path

    def write_json_file(self, path: Path, payload: dict) -> Path:
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def run_suite_captured(self, suite_path: Path, fail_fast: bool, artifacts_dir: Path) -> tuple[int, str]:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            result = run_suite(suite_path, fail_fast=fail_fast, artifacts_dir=artifacts_dir)
        return result, stdout.getvalue()

    def test_run_suite_passes_execute_tool_and_toolset_cases(self) -> None:
        suite_path = self.create_suite(
            {
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "tests": [
                    {
                        "name": "tool-exists",
                        "type": "resource_exists",
                        "kind": "tools",
                        "display_name": "get_customer_details_wrapper",
                    },
                    {
                        "name": "tool-health",
                        "type": "execute_tool",
                        "tool_display_name": "get_customer_details_wrapper",
                        "args": {"partner_id": "1234567890"},
                        "expect": {"equals": {"response.result.success": True}},
                    },
                    {
                        "name": "toolset-step",
                        "type": "execute_toolset_tool",
                        "toolset_display_name": "customer_details_openapi",
                        "tool_id": "getEidpToken",
                        "args": {"grant_type": "client_credentials"},
                        "expect": {"exists": ["response.access_token"]},
                    },
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()

        with (
            patch.object(MODULE, "get_access_token", return_value="token"),
            patch.object(
                MODULE,
                "resolve_resource_by_display_name",
                side_effect=[
                    {"name": "projects/p/locations/us/apps/a/tools/tool-1"},
                    {"name": "projects/p/locations/us/apps/a/tools/tool-1"},
                    {"name": "projects/p/locations/us/apps/a/toolsets/toolset-1"},
                ],
            ),
            patch.object(MODULE, "execute_tool", return_value={"response": {"result": {"success": True}}}),
            patch.object(MODULE, "execute_toolset_tool", return_value={"response": {"access_token": "abc"}}),
        ):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 0)
            self.assertIn(f"Running smoke suite: {suite_path.name}", output)
            self.assertIn(f"tool-health [{suite_path.name}]", output)
            self.assertIn(f"SUITE PASSED: {suite_path.name} - 3 test(s) passed.", output)
            self.assertTrue((artifacts_dir / "summary.json").exists())

    def test_run_suite_fail_fast_stops_on_first_failure(self) -> None:
        suite_path = self.create_suite(
            {
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "tests": [
                    {
                        "name": "first",
                        "type": "execute_tool",
                        "tool_display_name": "get_customer_details_wrapper",
                        "args": {"partner_id": "123"},
                        "expect": {"equals": {"response.result.success": True}},
                    },
                    {
                        "name": "second",
                        "type": "execute_tool",
                        "tool_display_name": "get_customer_details_wrapper",
                        "args": {"partner_id": "456"},
                        "expect": {"equals": {"response.result.success": True}},
                    },
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()

        with (
            patch.object(MODULE, "get_access_token", return_value="token"),
            patch.object(
                MODULE,
                "resolve_resource_by_display_name",
                return_value={"name": "projects/p/locations/us/apps/a/tools/tool-1"},
            ),
            patch.object(MODULE, "execute_tool", return_value={"response": {"result": {"success": False}}}) as execute_tool_mock,
        ):
            result, output = self.run_suite_captured(suite_path, fail_fast=True, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 1)
            self.assertEqual(execute_tool_mock.call_count, 1)
            self.assertIn(f"[1/2] first [{suite_path.name}] (execute_tool)", output)
            self.assertNotIn(f"[2/2] second [{suite_path.name}] (execute_tool)", output)
            self.assertIn("Artifact:", output)

    def test_run_suite_rejects_unsupported_test_types(self) -> None:
        suite_path = self.create_suite(
            {
                "project": "voice-banking-poc",
                "location": "us",
                "app_id": "acme-voice-us",
                "tests": [
                    {"name": "unsupported", "type": "unknown"},
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()

        with patch.object(MODULE, "get_access_token", return_value="token"):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 1)
            self.assertIn("Unsupported test type: unknown", output)
            self.assertIn(f"unsupported [{suite_path.name}]", output)

    def test_run_suite_auto_loads_root_env_for_placeholders(self) -> None:
        suite_path = self.create_suite(
            {
                "project": "${GCP_PROJECT_ID}",
                "location": "${GCP_LOCATION}",
                "app_id": "${CES_APP_ID}",
                "tests": [
                    {
                        "name": "tool-health",
                        "type": "execute_tool",
                        "tool_display_name": "get_customer_details_wrapper",
                        "args": {"partner_id": "1234567890"},
                        "expect": {"equals": {"response.result.success": True}},
                    }
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()
        root_env = self.create_temp_dir() / ".env"
        state_env = self.create_temp_dir() / "discovery-plan.env"
        root_env.write_text(
            "\n".join(
                [
                    'export GCP_PROJECT_ID="voice-banking-poc-from-env"',
                    "GCP_LOCATION=us",
                    "CES_APP_ID=acme-voice-us-from-env",
                ]
            ),
            encoding="utf-8",
        )
        state_env.write_text("", encoding="utf-8")

        with (
            patch.object(MODULE, "AUTO_ENV_LOADED", False),
            patch.object(MODULE, "default_root_env_path", return_value=root_env),
            patch.object(MODULE, "default_state_env_path", return_value=state_env),
            patch.dict(os.environ, {}, clear=True),
            patch.object(MODULE, "get_access_token", return_value="token"),
            patch.object(
                MODULE,
                "resolve_resource_by_display_name",
                return_value={"name": "projects/p/locations/us/apps/a/tools/tool-1"},
            ) as resolve_mock,
            patch.object(
                MODULE,
                "execute_tool",
                return_value={"response": {"result": {"success": True}}},
            ),
        ):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)

        self.assertEqual(result, 0)
        self.assertIn(f"SUITE PASSED: {suite_path.name} - 1 test(s) passed.", output)
        resolve_mock.assert_called_once_with(
            "https://ces.us.rep.googleapis.com",
            "token",
            "voice-banking-poc-from-env",
            "us",
            "acme-voice-us-from-env",
            "tools",
            "get_customer_details_wrapper",
        )

    def test_run_suite_uses_wrapper_defaults_when_env_values_are_missing(self) -> None:
        suite_path = self.create_suite(
            {
                "project": "${GCP_PROJECT_ID}",
                "location": "${GCP_LOCATION}",
                "app_id": "${CES_APP_ID}",
                "tests": [
                    {
                        "name": "tool-health",
                        "type": "execute_tool",
                        "tool_display_name": "get_customer_details_wrapper",
                        "args": {"partner_id": "1234567890"},
                        "expect": {"equals": {"response.result.success": True}},
                    }
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()
        root_env = self.create_temp_dir() / ".env"
        state_env = self.create_temp_dir() / "discovery-plan.env"
        root_env.write_text("", encoding="utf-8")
        state_env.write_text("", encoding="utf-8")

        with (
            patch.object(MODULE, "AUTO_ENV_LOADED", False),
            patch.object(MODULE, "default_root_env_path", return_value=root_env),
            patch.object(MODULE, "default_state_env_path", return_value=state_env),
            patch.dict(os.environ, {}, clear=True),
            patch.object(MODULE, "get_access_token", return_value="token"),
            patch.object(
                MODULE,
                "resolve_resource_by_display_name",
                return_value={"name": "projects/p/locations/us/apps/a/tools/tool-1"},
            ) as resolve_mock,
            patch.object(
                MODULE,
                "execute_tool",
                return_value={"response": {"result": {"success": True}}},
            ),
        ):
            result, _ = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)

        self.assertEqual(result, 0)
        resolve_mock.assert_called_once_with(
            "https://ces.us.rep.googleapis.com",
            "token",
            "voice-banking-poc",
            "us",
            "acme-voice-us",
            "tools",
            "get_customer_details_wrapper",
        )

    def test_run_suite_supports_http_request_tests(self) -> None:
        suite_path = self.create_suite(
            {
                "tests": [
                    {
                        "name": "gateway-health",
                        "type": "http_request",
                        "method": "GET",
                        "url": "https://example.com/health",
                        "expected_status": 200,
                        "expect": {
                            "contains": {"body": "UP"},
                            "equals": {"json.status": "UP"},
                        },
                    }
                ],
            }
        )
        artifacts_dir = self.create_artifacts_dir()

        with patch.object(
            MODULE,
            "http_request_raw",
            return_value={
                "method": "GET",
                "url": "https://example.com/health",
                "status": 200,
                "headers": {"Content-Type": "application/json"},
                "body": "{\"status\":\"UP\"}",
                "json": {"status": "UP"},
            },
        ):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 0)
            self.assertIn(f"gateway-health [{suite_path.name}]", output)
            self.assertIn(f"SUITE PASSED: {suite_path.name} - 1 test(s) passed.", output)

    def test_run_suite_supports_openapi_operation_tests(self) -> None:
        temp_dir = self.create_temp_dir()
        spec_path = self.write_json_file(
            temp_dir / "branch-finder.json",
            {
                "openapi": "3.0.1",
                "info": {"title": "Branch Finder Adapter API"},
                "paths": {
                    "/health": {
                        "get": {
                            "operationId": "health",
                            "summary": "Adapter health",
                        }
                    }
                },
            },
        )
        services_path = self.write_json_file(
            temp_dir / "services.json",
            {
                "services": {
                    "branch_finder_adapter": {
                        "display_name": "Branch Finder Adapter",
                        "base_url_env": "BFA_ADAPTER_BRANCH_FINDER_URL",
                        "openapi_spec": f"./{spec_path.name}",
                    }
                }
            },
        )
        suite_path = self.write_json_file(
            temp_dir / "suite.json",
            {
                "services_file": f"./{services_path.name}",
                "tests": [
                    {
                        "name": "branch_finder_adapter.health.contract",
                        "type": "openapi_operation",
                        "service": "branch_finder_adapter",
                        "operation_id": "health",
                        "expected_status": 200,
                    }
                ],
            },
        )
        artifacts_dir = self.create_artifacts_dir()

        with (
            patch.dict(os.environ, {"BFA_ADAPTER_BRANCH_FINDER_URL": "https://branch.example.com"}, clear=False),
            patch.object(
                MODULE,
                "http_request_raw",
                return_value={
                    "method": "GET",
                    "url": "https://branch.example.com/health",
                    "status": 200,
                    "headers": {},
                    "body": "{\"status\":\"UP\"}",
                    "json": {"status": "UP"},
                },
            ) as http_request_mock,
        ):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 0)
            self.assertIn(
                f"branch_finder_adapter.health.contract [{suite_path.name}]",
                output,
            )
            self.assertIn(
                "branch_finder_adapter::health -> https://branch.example.com/health",
                output,
            )
            http_request_mock.assert_called_once()
            self.assertEqual(http_request_mock.call_args.args[0], "GET")
            self.assertEqual(http_request_mock.call_args.args[1], "https://branch.example.com/health")

    def test_run_suite_reports_missing_openapi_service_env(self) -> None:
        temp_dir = self.create_temp_dir()
        spec_path = self.write_json_file(
            temp_dir / "mock-server.json",
            {
                "openapi": "3.0.1",
                "info": {"title": "Mock Server API"},
                "paths": {
                    "/advisory-appointments/taxonomy": {
                        "get": {
                            "operationId": "getAppointmentTaxonomyUpstream",
                            "summary": "Get taxonomy",
                        }
                    }
                },
            },
        )
        services_path = self.write_json_file(
            temp_dir / "services.json",
            {
                "services": {
                    "mock_server_upstream": {
                        "display_name": "Mock Server Upstream",
                        "base_url_env": "MOCK_SERVER_URL",
                        "required_env": ["MOCK_REQUIRED_TOKEN"],
                        "openapi_spec": f"./{spec_path.name}",
                        "default_headers": {
                            "Authorization": "Bearer ${MOCK_REQUIRED_TOKEN}",
                        },
                    }
                }
            },
        )
        suite_path = self.write_json_file(
            temp_dir / "suite.json",
            {
                "services_file": f"./{services_path.name}",
                "tests": [
                    {
                        "name": "mock_server_upstream.getAppointmentTaxonomyUpstream.contract",
                        "type": "openapi_operation",
                        "service": "mock_server_upstream",
                        "operation_id": "getAppointmentTaxonomyUpstream",
                        "expected_status": 200,
                    }
                ],
            },
        )
        artifacts_dir = self.create_artifacts_dir()

        with patch.dict(os.environ, {"MOCK_SERVER_URL": "https://mock.example.com"}, clear=True):
            result, output = self.run_suite_captured(suite_path, fail_fast=False, artifacts_dir=artifacts_dir)
            self.assertEqual(result, 1)
            self.assertIn(
                "Service 'mock_server_upstream' requires environment variable "
                "'MOCK_REQUIRED_TOKEN' but it is not set.",
                output,
            )

    def test_merge_expectations_adds_expected_status(self) -> None:
        self.assertEqual(
            merge_expectations({"expected_status": 200}),
            {"equals": {"status": 200}},
        )


if __name__ == "__main__":
    unittest.main()
