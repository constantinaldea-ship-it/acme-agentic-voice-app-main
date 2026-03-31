#!/usr/bin/env python3
"""Tests for CES resource deploy helpers."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent.parent / "lib" / "deploy_resource_lib.py"
SPEC = importlib.util.spec_from_file_location("deploy_resource_lib", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class DeployResourceLibTests(unittest.TestCase):
    def make_request(self) -> object:
        return MODULE.DeploymentRequest(
            kind="toolset",
            source_path=Path("/tmp/toolset.json"),
            resource_id="advisory_appointment",
            collection_url=(
                "https://ces.us.rep.googleapis.com/v1/projects/demo/locations/us/"
                "apps/demo-app/toolsets"
            ),
            resource_url=(
                "https://ces.us.rep.googleapis.com/v1/projects/demo/locations/us/"
                "apps/demo-app/toolsets/advisory_appointment"
            ),
            create_query="toolsetId=advisory_appointment",
            resource_name=(
                "projects/demo/locations/us/apps/demo-app/toolsets/advisory_appointment"
            ),
            display_name="advisory_appointment",
            update_mask="displayName,openApiToolset",
            payload={"displayName": "advisory_appointment"},
        )

    def test_find_existing_resource_name_falls_back_to_display_name_lookup(self) -> None:
        request = self.make_request()
        responses = [
            (404, '{"error":{"code":404}}'),
            (
                200,
                """
                {
                  "toolsets": [
                    {
                      "name": "projects/demo/locations/us/apps/demo-app/toolsets/uuid-123",
                      "displayName": "advisory_appointment"
                    }
                  ]
                }
                """.strip(),
            ),
        ]

        def fake_http_request(method: str, url: str, token: str, body=None):  # type: ignore[no-untyped-def]
            self.assertEqual(method, "GET")
            return responses.pop(0)

        with patch.object(MODULE, "http_request", side_effect=fake_http_request):
            resource_name = MODULE.find_existing_resource_name(request, "token")

        self.assertEqual(
            resource_name,
            "projects/demo/locations/us/apps/demo-app/toolsets/uuid-123",
        )

    def test_find_existing_resource_name_prefers_tracked_resource_name(self) -> None:
        request = self.make_request()
        calls: list[str] = []

        def fake_http_request(method: str, url: str, token: str, body=None):  # type: ignore[no-untyped-def]
            self.assertEqual(method, "GET")
            calls.append(url)
            return 200, (
                '{"name":"projects/demo/locations/us/apps/demo-app/toolsets/uuid-123"}'
            )

        with patch.object(MODULE, "http_request", side_effect=fake_http_request):
            resource_name = MODULE.find_existing_resource_name(
                request,
                "token",
                preferred_resource_name=(
                    "projects/demo/locations/us/apps/demo-app/toolsets/uuid-123"
                ),
            )

        self.assertEqual(
            resource_name,
            "projects/demo/locations/us/apps/demo-app/toolsets/uuid-123",
        )
        self.assertEqual(
            calls,
            [
                "https://ces.us.rep.googleapis.com/v1/projects/demo/locations/us/"
                "apps/demo-app/toolsets/uuid-123"
            ],
        )

    def test_apply_request_patches_existing_uuid_resource(self) -> None:
        request = self.make_request()
        calls: list[tuple[str, str]] = []

        def fake_http_request(method: str, url: str, token: str, body=None):  # type: ignore[no-untyped-def]
            calls.append((method, url))
            return 200, '{"name":"projects/demo/locations/us/apps/demo-app/toolsets/uuid-123"}'

        with patch.object(MODULE, "http_request", side_effect=fake_http_request):
            status, _ = MODULE.apply_request(
                request,
                "token",
                "projects/demo/locations/us/apps/demo-app/toolsets/uuid-123",
            )

        self.assertEqual(status, 200)
        self.assertEqual(
            calls,
            [
                (
                    "PATCH",
                    "https://ces.us.rep.googleapis.com/v1/projects/demo/locations/us/"
                    "apps/demo-app/toolsets/uuid-123?updateMask=displayName,openApiToolset",
                )
            ],
        )

    def test_build_request_for_tool_includes_display_name_in_update_mask(self) -> None:
        app_root = Path(tempfile.mkdtemp(prefix="deploy-resource-lib-"))
        (app_root / "app.json").write_text("{}\n", encoding="utf-8")
        tool_dir = app_root / "tools" / "customer_lookup"
        tool_dir.mkdir(parents=True)
        (tool_dir / "customer_lookup.json").write_text(
            """
            {
              "displayName": "customer_lookup",
              "executionType": "SYNCHRONOUS",
              "pythonFunction": {
                "name": "customer_lookup",
                "pythonCode": "tools/customer_lookup/python_function/python_code.py"
              }
            }
            """.strip()
            + "\n",
            encoding="utf-8",
        )
        python_code = tool_dir / "python_function" / "python_code.py"
        python_code.parent.mkdir(parents=True, exist_ok=True)
        python_code.write_text("def customer_lookup():\n    return {}\n", encoding="utf-8")

        request = MODULE.build_request(
            kind="tool",
            source_path=tool_dir / "customer_lookup.json",
            project="voice-banking-poc",
            location="us",
            app_id="acme-voice-us",
        )

        self.assertEqual(
            request.update_mask,
            "displayName,executionType,pythonFunction",
        )

    def test_build_request_for_toolset_resolves_env_var_schema_url(self) -> None:
        app_root = Path(tempfile.mkdtemp(prefix="deploy-resource-lib-toolset-"))
        (app_root / "app.json").write_text("{}\n", encoding="utf-8")
        (app_root / "environment.json").write_text(
            json.dumps(
                {
                    "toolsets": {
                        "customer_details_openapi": {
                            "openApiToolset": {"url": "https://mock-server.example"}
                        }
                    }
                }
            )
            + "\n",
            encoding="utf-8",
        )
        toolset_dir = app_root / "toolsets" / "customer_details_openapi"
        (toolset_dir / "open_api_toolset").mkdir(parents=True, exist_ok=True)
        (toolset_dir / "customer_details_openapi.json").write_text(
            '{"displayName":"customer_details_openapi","openApiToolset":{"openApiSchema":"toolsets/customer_details_openapi/open_api_toolset/open_api_schema.yaml"}}\n',
            encoding="utf-8",
        )
        (toolset_dir / "open_api_toolset" / "open_api_schema.yaml").write_text(
            'openapi: "3.0.1"\nservers:\n  - url: "$env_var"\npaths: {}\n',
            encoding="utf-8",
        )

        request = MODULE.build_request(
            kind="toolset",
            source_path=toolset_dir / "customer_details_openapi.json",
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
        )

        schema = request.payload["openApiToolset"]["openApiSchema"]
        self.assertIn('url: "https://mock-server.example"', schema)
        self.assertNotIn("$env_var", schema)

    def test_build_request_for_toolset_requires_environment_value_for_env_var(self) -> None:
        app_root = Path(tempfile.mkdtemp(prefix="deploy-resource-lib-toolset-missing-env-"))
        (app_root / "app.json").write_text("{}\n", encoding="utf-8")
        (app_root / "environment.json").write_text('{"toolsets":{}}\n', encoding="utf-8")
        toolset_dir = app_root / "toolsets" / "customer_details_openapi"
        (toolset_dir / "open_api_toolset").mkdir(parents=True, exist_ok=True)
        (toolset_dir / "customer_details_openapi.json").write_text(
            '{"displayName":"customer_details_openapi","openApiToolset":{"openApiSchema":"toolsets/customer_details_openapi/open_api_toolset/open_api_schema.yaml"}}\n',
            encoding="utf-8",
        )
        (toolset_dir / "open_api_toolset" / "open_api_schema.yaml").write_text(
            'openapi: "3.0.1"\nservers:\n  - url: "$env_var"\npaths: {}\n',
            encoding="utf-8",
        )

        with self.assertRaises(MODULE.DeployResourceError) as ctx:
            MODULE.build_request(
                kind="toolset",
                source_path=toolset_dir / "customer_details_openapi.json",
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
            )

        self.assertIn("toolsets.customer_details_openapi.openApiToolset.url", str(ctx.exception))

    def test_http_request_wraps_transport_failures(self) -> None:
        with patch.object(
            MODULE.urllib.request,
            "urlopen",
            side_effect=MODULE.urllib.error.URLError("network down"),
        ):
            with self.assertRaises(MODULE.DeployResourceError) as ctx:
                MODULE.http_request(
                    "GET",
                    "https://ces.us.rep.googleapis.com/v1/projects/demo/locations/us/apps/demo-app/tools",
                    "token",
                )

        self.assertIn("Request to CES API failed", str(ctx.exception))

    def test_convert_agent_manifest_resolves_builtin_end_session_tool_name(self) -> None:
        app_root = Path("/tmp/demo-app")
        manifest = {
            "displayName": "advisory_appointment_agent",
            "instruction": "agents/advisory_appointment_agent/instruction.txt",
            "tools": ["end_session"],
        }

        with patch.object(
            MODULE,
            "read_text",
            return_value="<role>Advisory appointment specialist</role>",
        ):
            payload = MODULE.convert_agent_manifest(
                manifest,
                app_root,
                "voice-banking-poc",
                "us",
                "acme-voice-us",
            )

        self.assertEqual(
            payload["tools"],
            ["projects/voice-banking-poc/locations/us/apps/acme-voice-us/tools/end_session"],
        )

    def test_convert_agent_manifest_uses_resolved_state_names_for_tools_and_toolsets(self) -> None:
        app_root = Path("/tmp/demo-app")
        manifest = {
            "displayName": "customer_details_agent",
            "instruction": "agents/customer_details_agent/instruction.txt",
            "tools": ["get_customer_details_wrapper", "end_session"],
            "toolsets": [
                {
                    "toolset": "customer_details_openapi",
                    "toolIds": ["getEidpToken"],
                }
            ],
        }

        with patch.object(
            MODULE,
            "read_text",
            return_value="<role>Customer details specialist</role>",
        ):
            payload = MODULE.convert_agent_manifest(
                manifest,
                app_root,
                "voice-banking-poc",
                "us",
                "acme-voice-us",
                tool_names={
                    "get_customer_details_wrapper": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "tools/09e5c1fa-1374-46ce-ad9e-bc6c10d9c03c"
                    )
                },
                toolset_names={
                    "customer_details_openapi": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "toolsets/17fcf5dc-9c4f-4dee-bfbb-88db737076d4"
                    )
                },
            )

        self.assertEqual(
            payload["tools"],
            [
                "projects/voice-banking-poc/locations/us/apps/acme-voice-us/tools/09e5c1fa-1374-46ce-ad9e-bc6c10d9c03c",
                "projects/voice-banking-poc/locations/us/apps/acme-voice-us/tools/end_session",
            ],
        )
        self.assertEqual(
            payload["toolsets"],
            [
                {
                    "toolset": "projects/voice-banking-poc/locations/us/apps/acme-voice-us/toolsets/17fcf5dc-9c4f-4dee-bfbb-88db737076d4",
                    "toolIds": ["getEidpToken"],
                }
            ],
        )

    def test_convert_agent_manifest_prefers_state_mapping_for_builtin_tool_when_present(self) -> None:
        app_root = Path("/tmp/demo-app")
        manifest = {
            "displayName": "voice_banking_agent",
            "instruction": "agents/voice_banking_agent/instruction.txt",
            "tools": ["end_session"],
        }

        with patch.object(
            MODULE,
            "read_text",
            return_value="<role>Root agent</role>",
        ):
            payload = MODULE.convert_agent_manifest(
                manifest,
                app_root,
                "voice-banking-poc",
                "us",
                "acme-voice-us",
                tool_names={
                    "end_session": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "tools/end_session"
                    )
                },
            )

        self.assertEqual(
            payload["tools"],
            ["projects/voice-banking-poc/locations/us/apps/acme-voice-us/tools/end_session"],
        )

    def test_convert_agent_manifest_uses_resolved_child_agent_names(self) -> None:
        app_root = Path("/tmp/demo-app")
        manifest = {
            "displayName": "voice_banking_agent",
            "instruction": "agents/voice_banking_agent/instruction.txt",
            "childAgents": ["location_services_agent"],
        }

        with patch.object(
            MODULE,
            "read_text",
            return_value="<role>Root agent</role>",
        ):
            payload = MODULE.convert_agent_manifest(
                manifest,
                app_root,
                "voice-banking-poc",
                "us",
                "acme-voice-us",
                agent_names={
                    "location_services_agent": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "agents/24ad93cd-1111-2222-3333-444444444444"
                    )
                },
            )

        self.assertEqual(
            payload["childAgents"],
            [
                "projects/voice-banking-poc/locations/us/apps/acme-voice-us/agents/24ad93cd-1111-2222-3333-444444444444"
            ],
        )


if __name__ == "__main__":
    unittest.main()
