#!/usr/bin/env python3
"""Tests for the incremental CES deploy manager."""

from __future__ import annotations

import importlib.util
import io
import json
import sys
import tempfile
import textwrap
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


MODULE_PATH = Path(__file__).resolve().parent.parent / "ces-deploy-manager.py"
SPEC = importlib.util.spec_from_file_location("ces_deploy_manager", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class CesDeployManagerTests(unittest.TestCase):
    def create_app(self) -> Path:
        temp_dir = Path(tempfile.mkdtemp(prefix="ces-deploy-manager-"))
        write_text(
            temp_dir / "app.json",
            '{"displayName":"demo","rootAgent":"voice_banking_agent","globalInstruction":"global_instruction.txt"}\n',
        )
        write_text(temp_dir / "global_instruction.txt", "<persona>Global</persona>\n")

        write_text(
            temp_dir / "toolsets/customer_details/customer_details.json",
            textwrap.dedent(
                """
                {
                  "displayName": "customer_details_openapi",
                  "openApiToolset": {
                    "openApiSchema": "toolsets/customer_details/open_api_toolset/open_api_schema.yaml"
                  }
                }
                """
            ).strip()
            + "\n",
        )
        write_text(
            temp_dir / "toolsets/customer_details/open_api_toolset/open_api_schema.yaml",
            "openapi: '3.0.1'\ninfo:\n  title: Demo\n  version: '1.0.0'\npaths: {}\n",
        )

        write_text(
            temp_dir / "tools/get_customer_details_python/get_customer_details_python.json",
            textwrap.dedent(
                """
                {
                  "displayName": "get_customer_details_python",
                  "executionType": "SYNCHRONOUS",
                  "pythonFunction": {
                    "name": "get_customer_details_python",
                    "pythonCode": "tools/get_customer_details_python/python_function/python_code.py"
                  }
                }
                """
            ).strip()
            + "\n",
        )
        write_text(
            temp_dir / "tools/get_customer_details_python/python_function/python_code.py",
            "def get_customer_details_python(partner_id=''):\n    return {'success': True}\n",
        )

        write_text(
            temp_dir / "agents/customer_details_agent/customer_details_agent.json",
            textwrap.dedent(
                """
                {
                  "displayName": "customer_details_agent",
                  "instruction": "agents/customer_details_agent/instruction.txt",
                  "tools": ["get_customer_details_python", "end_session"],
                  "toolsets": [
                    {
                      "toolset": "customer_details",
                      "toolIds": ["getEidpToken"]
                    }
                  ]
                }
                """
            ).strip()
            + "\n",
        )
        write_text(
            temp_dir / "agents/customer_details_agent/instruction.txt",
            "<role>Customer agent</role>\n<persona>Helpful</persona>\n<constraints>1. Be concise.</constraints>\n<taskflow><subtask name='main'><step name='lookup'><action>Lookup customer.</action></step></subtask></taskflow>\n",
        )
        return temp_dir

    def test_discover_components_returns_dependency_order(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        self.assertEqual(
            [(component.kind, component.resource_id) for component in components],
            [
                ("toolset", "customer_details"),
                ("tool", "get_customer_details_python"),
                ("agent", "customer_details_agent"),
            ],
        )

    def test_classify_components_marks_added_modified_and_noop(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        state = {"components": {}}
        plan = MODULE.classify_components(components, state["components"])
        self.assertEqual(len(plan["added"]), 3)
        self.assertEqual(len(plan["modified"]), 0)
        self.assertEqual(len(plan["noop"]), 0)

        state["components"] = {
            component.key: {
                "combined_sha256": component.combined_hash,
            }
            for component in components
        }
        plan = MODULE.classify_components(components, state["components"])
        self.assertEqual(len(plan["added"]), 0)
        self.assertEqual(len(plan["modified"]), 0)
        self.assertEqual(len(plan["noop"]), 3)

        instruction_file = app_root / "agents/customer_details_agent/instruction.txt"
        write_text(instruction_file, instruction_file.read_text(encoding="utf-8") + "\nChanged.\n")
        changed_components = MODULE.discover_components(app_root)
        plan = MODULE.classify_components(changed_components, state["components"])
        self.assertEqual([component.key for component in plan["modified"]], ["agent:customer_details_agent"])

    def test_environment_json_change_marks_env_backed_toolset_modified(self) -> None:
        app_root = self.create_app()
        write_text(
            app_root / "environment.json",
            '{"toolsets":{"customer_details":{"openApiToolset":{"url":"https://mock-one.example"}}}}\n',
        )
        schema_path = app_root / "toolsets/customer_details/open_api_toolset/open_api_schema.yaml"
        write_text(
            schema_path,
            'openapi: "3.0.1"\nservers:\n  - url: "$env_var"\npaths: {}\n',
        )

        components = MODULE.discover_components(app_root)
        toolset_component = next(component for component in components if component.kind == "toolset")
        tracked_relative = {
            path.resolve().relative_to(app_root.resolve()).as_posix()
            for path in toolset_component.tracked_files
        }
        self.assertIn("environment.json", tracked_relative)

        state_components = {
            component.key: {"combined_sha256": component.combined_hash}
            for component in components
        }
        write_text(
            app_root / "environment.json",
            '{"toolsets":{"customer_details":{"openApiToolset":{"url":"https://mock-two.example"}}}}\n',
        )

        changed_components = MODULE.discover_components(app_root)
        plan = MODULE.classify_components(changed_components, state_components)
        self.assertEqual([component.key for component in plan["modified"]], ["toolset:customer_details"])

    def test_find_removed_components_returns_state_only_keys(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        removed = MODULE.find_removed_components(
            components,
            {
                "tool:legacy_tool": {"combined_sha256": "abc"},
                components[0].key: {"combined_sha256": components[0].combined_hash},
            },
        )
        self.assertEqual(removed, ["tool:legacy_tool"])

    def test_build_run_artifact_contains_plan_and_hashes(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        state_components = {
            components[0].key: {
                "combined_sha256": components[0].combined_hash,
                "resource_name": "projects/demo/locations/us/apps/demo-app/toolsets/uuid-1",
                "remote_update_time": "2026-03-28T11:20:22Z",
                "remote_create_time": "2026-03-28T11:20:00Z",
                "kind": components[0].kind,
                "resource_id": components[0].resource_id,
                "display_name": components[0].display_name,
                "source_path": str(components[0].source_path),
                "tracked_files": [{"path": "toolsets/customer_details/customer_details.json"}],
            }
        }
        plan = MODULE.classify_components(components, state_components)
        artifact = MODULE.build_run_artifact(
            run_id="run-123",
            app_root=app_root,
            state_file=app_root / ".ces-deployment-state.json",
            artifacts_dir=app_root / ".artifacts",
            project="voice-banking-poc",
            location="us",
            app_id="acme-voice-us",
            endpoint=None,
            validate_only=True,
            auto_confirm=False,
            git_commit="abc123",
            local_components=components,
            state_components=state_components,
            plan=plan,
            removed=["tool:legacy_tool"],
        )

        self.assertEqual(artifact["run_id"], "run-123")
        self.assertEqual(artifact["git"]["commit_sha"], "abc123")
        self.assertEqual(artifact["plan"]["summary"]["noop"], 1)
        self.assertEqual(artifact["plan"]["summary"]["added"], 2)
        self.assertEqual(artifact["plan"]["summary"]["removed"], 1)

        customer_tool_entry = next(
            entry for entry in artifact["plan"]["components"] if entry["key"] == "tool:get_customer_details_python"
        )
        self.assertEqual(customer_tool_entry["plan_status"], "added")
        self.assertIsNone(customer_tool_entry["before_combined_sha256"])
        self.assertEqual(customer_tool_entry["after_combined_sha256"], components[1].combined_hash)
        self.assertEqual(customer_tool_entry["execution_status"], "pending")

        noop_entry = next(
            entry for entry in artifact["plan"]["components"] if entry["key"] == components[0].key
        )
        self.assertEqual(noop_entry["plan_status"], "noop")
        self.assertEqual(noop_entry["before_combined_sha256"], components[0].combined_hash)
        self.assertEqual(noop_entry["after_combined_sha256"], components[0].combined_hash)
        self.assertEqual(
            noop_entry["after_remote_update_time"],
            "2026-03-28T11:20:22Z",
        )
        self.assertEqual(noop_entry["execution_status"], "skipped")

    def test_load_env_file_preserves_existing_environment(self) -> None:
        env_file = Path(tempfile.mkdtemp(prefix="ces-deploy-env-")) / ".env"
        write_text(env_file, "GCP_PROJECT_ID=file-value\nNEW_KEY=from-file\n")

        with mock.patch.dict(
            "os.environ",
            {"GCP_PROJECT_ID": "shell-value"},
            clear=True,
        ):
            MODULE.load_env_file(env_file)
            self.assertEqual(MODULE.os.environ["GCP_PROJECT_ID"], "shell-value")
            self.assertEqual(MODULE.os.environ["NEW_KEY"], "from-file")

    def test_resolved_resource_maps_filters_entries_for_current_target(self) -> None:
        agent_names, tool_names, toolset_names = MODULE.resolved_resource_maps(
            {
                "agent:customer_details_agent": {
                    "kind": "agent",
                    "resource_id": "customer_details_agent",
                    "display_name": "customer_details_agent",
                    "resource_name": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "agents/uuid-agent"
                    ),
                },
                "tool:get_customer_details_python": {
                    "kind": "tool",
                    "resource_id": "get_customer_details_python",
                    "display_name": "get_customer_details_python",
                    "resource_name": (
                        "projects/other-project/locations/eu/apps/other-app/tools/uuid-tool"
                    ),
                },
                "toolset:customer_details": {
                    "kind": "toolset",
                    "resource_id": "customer_details",
                    "display_name": "customer_details",
                    "resource_name": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        "toolsets/uuid-toolset"
                    ),
                },
            },
            project="voice-banking-poc",
            location="us",
            app_id="acme-voice-us",
        )

        self.assertEqual(
            agent_names,
            {
                "customer_details_agent": (
                    "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                    "agents/uuid-agent"
                )
            },
        )
        self.assertEqual(tool_names, {})
        self.assertEqual(
            toolset_names,
            {
                "customer_details": (
                    "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                    "toolsets/uuid-toolset"
                )
            },
        )

    def test_preferred_resource_name_for_ignores_mismatched_target(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)

        preferred = MODULE.preferred_resource_name_for(
            components[0],
            {
                components[0].key: {
                    "resource_name": (
                        "projects/other-project/locations/eu/apps/other-app/toolsets/uuid-1"
                    )
                }
            },
            project="voice-banking-poc",
            location="us",
            app_id="acme-voice-us",
        )

        self.assertIsNone(preferred)

    def test_reconcile_noop_components_with_remote_reclassifies_missing_resources(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        state_components = {
            component.key: {
                "combined_sha256": component.combined_hash,
                "resource_name": (
                    "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                    f"{component.kind}s/{component.resource_id}"
                ),
                "kind": component.kind,
                "resource_id": component.resource_id,
                "display_name": component.display_name,
                "source_path": str(component.source_path),
            }
            for component in components
        }
        plan = MODULE.classify_components(components, state_components)

        with mock.patch.object(
            MODULE,
            "find_existing_resource_name",
            side_effect=["projects/.../toolsets/uuid-toolset", None, "projects/.../agents/uuid-agent"],
        ):
            refreshed_plan, stale_components = MODULE.reconcile_noop_components_with_remote(
                plan,
                state_components,
                project="voice-banking-poc",
                location="us",
                app_id="acme-voice-us",
                endpoint=None,
                token="token",
            )

        self.assertEqual([component.key for component in stale_components], ["tool:get_customer_details_python"])
        self.assertEqual(
            [component.key for component in refreshed_plan["added"]],
            ["tool:get_customer_details_python"],
        )
        self.assertEqual(
            [component.key for component in refreshed_plan["noop"]],
            ["toolset:customer_details", "agent:customer_details_agent"],
        )
        self.assertEqual(
            state_components["toolset:customer_details"]["resource_name"],
            "projects/.../toolsets/uuid-toolset",
        )
        self.assertEqual(
            state_components["agent:customer_details_agent"]["resource_name"],
            "projects/.../agents/uuid-agent",
        )

    def test_main_redeploys_when_noop_state_is_stale(self) -> None:
        app_root = self.create_app()
        state_file = app_root / ".ces-deployment-state.json"
        artifacts_dir = app_root / ".artifacts"
        components = MODULE.discover_components(app_root)
        state = {
            "schema_version": MODULE.SCHEMA_VERSION,
            "components": {
                component.key: {
                    "combined_sha256": component.combined_hash,
                    "resource_name": (
                        "projects/voice-banking-poc/locations/us/apps/acme-voice-us/"
                        f"{component.kind}s/{component.resource_id}"
                    ),
                    "kind": component.kind,
                    "resource_id": component.resource_id,
                    "display_name": component.display_name,
                    "source_path": str(component.source_path),
                }
                for component in components
            },
        }
        state_file.write_text(json.dumps(state), encoding="utf-8")
        args = MODULE.argparse.Namespace(
            app_root=str(app_root),
            state_file=str(state_file),
            artifacts_dir=str(artifacts_dir),
            project="voice-banking-poc",
            location="us",
            app_id="acme-voice-us",
            endpoint=None,
            yes=True,
            validate_only=False,
            status=False,
            status_json=False,
        )

        with mock.patch.object(MODULE, "ensure_env_loaded"), mock.patch.object(
            MODULE, "parse_args", return_value=args
        ), mock.patch.object(MODULE, "run_validator"), mock.patch.object(
            MODULE, "get_access_token", return_value="token"
        ), mock.patch.object(
            MODULE,
            "print_execution_target",
            return_value=MODULE.ExecutionTargetStatus(
                project="voice-banking-poc",
                location="us",
                app_id="acme-voice-us",
                endpoint="https://ces.us.rep.googleapis.com",
                app_exists=True,
                deployments=(),
                configured_deployment_id="",
                configured_deployment_matches=None,
            ),
        ), mock.patch.object(
            MODULE,
            "reconcile_noop_components_with_remote",
            return_value=(
                {
                    "added": [components[2]],
                    "modified": [],
                    "noop": [components[0], components[1]],
                },
                [components[2]],
            ),
        ), mock.patch.object(
            MODULE, "confirm_plan", return_value=True
        ), mock.patch.object(
            MODULE, "execute_plan", return_value=0
        ) as execute_plan:
            result = MODULE.main()

        self.assertEqual(result, 0)
        execute_plan.assert_called_once()
        called_components = execute_plan.call_args.args[0]
        self.assertEqual([component.key for component in called_components], [components[2].key])
        self.assertEqual(execute_plan.call_args.kwargs["token"], "token")

    def test_main_allows_validate_only_without_remote_target_config(self) -> None:
        app_root = self.create_app()
        state_file = app_root / ".ces-deployment-state.json"
        artifacts_dir = app_root / ".artifacts"
        args = MODULE.argparse.Namespace(
            app_root=str(app_root),
            state_file=str(state_file),
            artifacts_dir=str(artifacts_dir),
            project="",
            location="",
            app_id="",
            endpoint=None,
            yes=False,
            validate_only=True,
            status=False,
            status_json=False,
        )

        with mock.patch.object(MODULE, "ensure_env_loaded"), mock.patch.object(
            MODULE, "parse_args", return_value=args
        ), mock.patch.object(MODULE, "run_validator"), mock.patch.object(
            MODULE, "confirm_plan", side_effect=AssertionError("should not prompt")
        ), mock.patch.object(
            MODULE, "print_execution_target", side_effect=AssertionError("should not inspect remote CES deployments")
        ), mock.patch.object(
            MODULE, "require_value", side_effect=AssertionError("should not require CES target")
        ):
            result = MODULE.main()

        self.assertEqual(result, 0)
        artifact_files = sorted(artifacts_dir.glob("*.json"))
        self.assertEqual(len(artifact_files), 1)
        artifact = json.loads(artifact_files[0].read_text(encoding="utf-8"))
        self.assertEqual(artifact["status"], "validate_only")
        self.assertEqual(
            artifact["outcome"]["message"],
            "Validation-only mode; no remote changes executed.",
        )

    def test_main_aborts_cleanly_when_bootstrap_import_is_declined(self) -> None:
        app_root = self.create_app()
        state_file = app_root / ".ces-deployment-state.json"
        artifacts_dir = app_root / ".artifacts"
        args = MODULE.argparse.Namespace(
            app_root=str(app_root),
            state_file=str(state_file),
            artifacts_dir=str(artifacts_dir),
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint=None,
            yes=True,
            validate_only=False,
            status=False,
            status_json=False,
        )

        missing_status = MODULE.ExecutionTargetStatus(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            app_exists=False,
            deployments=(),
            configured_deployment_id="",
            configured_deployment_matches=None,
        )
        with mock.patch.object(MODULE, "ensure_env_loaded"), mock.patch.object(
            MODULE, "parse_args", return_value=args
        ), mock.patch.object(MODULE, "run_validator"), mock.patch.object(
            MODULE, "get_access_token", return_value="token"
        ), mock.patch.object(
            MODULE, "print_execution_target", return_value=missing_status
        ), mock.patch.object(
            MODULE, "confirm_bootstrap_import", return_value=False
        ), mock.patch.object(
            MODULE, "confirm_plan", side_effect=AssertionError("should not prompt")
        ), mock.patch.object(
            MODULE, "run_bootstrap_import", side_effect=AssertionError("should not bootstrap")
        ), mock.patch.object(
            MODULE, "execute_plan", side_effect=AssertionError("should not execute")
        ):
            result = MODULE.main()

        self.assertEqual(result, 0)
        artifact_files = sorted(artifacts_dir.glob("*.json"))
        self.assertEqual(len(artifact_files), 1)
        artifact = json.loads(artifact_files[0].read_text(encoding="utf-8"))
        self.assertEqual(artifact["status"], "cancelled")
        self.assertEqual(artifact["outcome"]["message"], "Bootstrap import aborted by user.")

    def test_main_runs_bootstrap_import_then_initializes_state(self) -> None:
        app_root = self.create_app()
        state_file = app_root / ".ces-deployment-state.json"
        artifacts_dir = app_root / ".artifacts"
        components = MODULE.discover_components(app_root)
        args = MODULE.argparse.Namespace(
            app_root=str(app_root),
            state_file=str(state_file),
            artifacts_dir=str(artifacts_dir),
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint=None,
            yes=False,
            validate_only=False,
            status=False,
            status_json=False,
        )
        missing_status = MODULE.ExecutionTargetStatus(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            app_exists=False,
            deployments=(),
            configured_deployment_id="",
            configured_deployment_matches=None,
        )
        live_status = MODULE.ExecutionTargetStatus(
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
            endpoint="https://ces.eu.rep.googleapis.com",
            app_exists=True,
            deployments=(
                {
                    "name": "projects/voice-banking-poc/locations/eu/apps/acme-voice-eu/deployments/api-access-1",
                    "displayName": "Primary API Access",
                },
            ),
            configured_deployment_id="",
            configured_deployment_matches=None,
        )
        post_bootstrap_plan = {"added": [], "modified": [], "noop": list(components)}

        with mock.patch.object(MODULE, "ensure_env_loaded"), mock.patch.object(
            MODULE, "parse_args", return_value=args
        ), mock.patch.object(MODULE, "run_validator"), mock.patch.object(
            MODULE, "get_access_token", return_value="token"
        ), mock.patch.object(
            MODULE, "print_execution_target", side_effect=[missing_status, live_status]
        ), mock.patch.object(
            MODULE, "confirm_bootstrap_import", return_value=True
        ), mock.patch.object(
            MODULE, "run_bootstrap_import"
        ) as run_bootstrap_import, mock.patch.object(
            MODULE,
            "reconcile_added_components_with_remote",
            return_value=(post_bootstrap_plan, list(components)),
        ) as reconcile_added, mock.patch.object(
            MODULE,
            "reconcile_noop_components_with_remote",
            return_value=(post_bootstrap_plan, []),
        ), mock.patch.object(
            MODULE, "confirm_plan", side_effect=AssertionError("should not prompt once everything is synced")
        ), mock.patch.object(
            MODULE, "execute_plan", side_effect=AssertionError("should not execute once bootstrap import already synced state")
        ):
            result = MODULE.main()

        self.assertEqual(result, 0)
        run_bootstrap_import.assert_called_once_with(
            app_root=app_root.resolve(),
            project="voice-banking-poc",
            location="eu",
            app_id="acme-voice-eu",
        )
        reconcile_added.assert_called_once()
        artifact_files = sorted(artifacts_dir.glob("*.json"))
        artifact = json.loads(artifact_files[0].read_text(encoding="utf-8"))
        self.assertEqual(artifact["status"], "noop")
        self.assertEqual(artifact["outcome"]["message"], "Everything is up to date.")

    def test_write_run_artifact_persists_json(self) -> None:
        target_dir = Path(tempfile.mkdtemp(prefix="ces-deploy-artifact-"))
        artifact_path = target_dir / "run-123.json"
        artifact = {
            "run_id": "run-123",
            "plan": {"components": []},
            "outcome": {"summary": {}},
        }

        MODULE.write_run_artifact(artifact_path, artifact)

        persisted = json.loads(artifact_path.read_text(encoding="utf-8"))
        self.assertEqual(persisted["run_id"], "run-123")

    def test_print_plan_uses_color_codes_when_forced(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        plan = {
            "added": [components[1]],
            "modified": [components[2]],
            "noop": [components[0]],
        }
        stdout = io.StringIO()
        with mock.patch.dict("os.environ", {"CLICOLOR_FORCE": "1"}, clear=True):
            with redirect_stdout(stdout):
                MODULE.print_plan(plan, ["tool:legacy_tool"])
        rendered = stdout.getvalue()
        self.assertIn("\033[32mAdded (1):\033[0m", rendered)
        self.assertIn("\033[33mUpdated (1):\033[0m", rendered)
        self.assertIn("\033[90mNo-op (1):\033[0m", rendered)
        self.assertIn("\033[31mRemoved (1):\033[0m", rendered)

    def test_latest_artifact_path_returns_newest_json(self) -> None:
        artifacts_dir = Path(tempfile.mkdtemp(prefix="ces-deploy-artifacts-"))
        write_text(artifacts_dir / "20260328T100000000000Z-a.json", "{}\n")
        write_text(artifacts_dir / "20260328T100500000000Z-b.json", "{}\n")
        write_text(artifacts_dir / "notes.txt", "ignore\n")
        self.assertEqual(
            MODULE.latest_artifact_path(artifacts_dir),
            artifacts_dir / "20260328T100500000000Z-b.json",
        )

    def test_print_status_report_includes_latest_run_and_components(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        state = {
            "app_root": str(app_root),
            "project": "voice-banking-poc",
            "location": "us",
            "app_id": "acme-voice-us",
            "components": {
                components[0].key: {
                    "kind": components[0].kind,
                    "resource_id": components[0].resource_id,
                    "display_name": components[0].display_name,
                    "deployed_at": "2026-03-28T11:20:22Z",
                    "remote_update_time": "2026-03-28T11:20:21Z",
                    "resource_name": "projects/demo/toolsets/uuid-1",
                },
                components[2].key: {
                    "kind": components[2].kind,
                    "resource_id": components[2].resource_id,
                    "display_name": components[2].display_name,
                    "deployed_at": "2026-03-28T11:22:00Z",
                    "remote_update_time": "2026-03-28T11:21:59Z",
                    "resource_name": "projects/demo/agents/uuid-2",
                },
            },
        }
        artifacts_dir = Path(tempfile.mkdtemp(prefix="ces-deploy-status-"))
        artifact = {
            "run_id": "run-123",
            "status": "success",
            "started_at": "2026-03-28T11:23:00Z",
            "completed_at": "2026-03-28T11:23:10Z",
            "git": {"commit_sha": "abc123"},
            "outcome": {"message": "Applied 2 resource change(s) successfully."},
            "target": {"artifacts_dir": str(artifacts_dir)},
        }
        artifact_path = artifacts_dir / "20260328T112300000000Z-run.json"
        MODULE.write_run_artifact(artifact_path, artifact)
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            MODULE.print_status_report(state, artifact, as_json=False)
        rendered = stdout.getvalue()
        self.assertIn("Deployment Status", rendered)
        self.assertIn("Latest run: run-123 status=success", rendered)
        self.assertIn("Latest git SHA: abc123", rendered)
        self.assertIn("toolset customer_details", rendered)
        self.assertIn("agent   customer_details_agent", rendered)

    def test_print_execution_target_shows_discovered_deployment_id_and_export_hint(self) -> None:
        stdout = io.StringIO()
        with mock.patch.object(
            MODULE,
            "inspect_execution_target",
            return_value=MODULE.ExecutionTargetStatus(
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
                endpoint="https://ces.eu.rep.googleapis.com",
                app_exists=True,
                deployments=(
                    {
                        "name": (
                            "projects/voice-banking-poc/locations/eu/apps/acme-voice-eu/"
                            "deployments/api-access-1"
                        ),
                        "displayName": "Primary API Access",
                    },
                ),
                configured_deployment_id="",
                configured_deployment_matches=None,
            ),
        ), mock.patch.dict("os.environ", {}, clear=True), redirect_stdout(stdout):
            MODULE.print_execution_target(
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
                endpoint=None,
                token="token",
            )

        rendered = stdout.getvalue()
        self.assertIn("CES Execution Target", rendered)
        self.assertIn("CES_APP_ID         : acme-voice-eu", rendered)
        self.assertIn("CES app status     : exists", rendered)
        self.assertIn("api-access-1", rendered)
        self.assertIn("Suggested export    : CES_DEPLOYMENT_ID=api-access-1", rendered)

    def test_print_execution_target_reports_bootstrap_mode_when_app_is_missing(self) -> None:
        stdout = io.StringIO()
        with mock.patch.object(
            MODULE,
            "inspect_execution_target",
            return_value=MODULE.ExecutionTargetStatus(
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
                endpoint="https://ces.eu.rep.googleapis.com",
                app_exists=False,
                deployments=(),
                configured_deployment_id="api-access-1",
                configured_deployment_matches=None,
            ),
        ), redirect_stdout(stdout):
            status = MODULE.print_execution_target(
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
                endpoint=None,
                token="token",
            )

        rendered = stdout.getvalue()
        self.assertFalse(status.app_exists)
        self.assertIn("CES Execution Target", rendered)
        self.assertIn("CES app status     : missing", rendered)
        self.assertIn("Mode preview       : bootstrap import required", rendered)
        self.assertIn("Deployment IDs     : unavailable until the CES app exists", rendered)
        self.assertIn("Configured CES_DEPLOYMENT_ID status: cannot verify because the CES app is missing", rendered)

    def test_confirm_bootstrap_import_accepts_yes(self) -> None:
        with mock.patch("builtins.input", return_value="yes"):
            approved = MODULE.confirm_bootstrap_import(
                False,
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
            )

        self.assertTrue(approved)

    def test_run_bootstrap_import_dispatches_deploy_agent_import(self) -> None:
        app_root = MODULE.ces_agent_dir() / "acme_voice_agent"
        with mock.patch.object(MODULE.subprocess, "run", return_value=mock.Mock(returncode=0)) as run_subprocess:
            MODULE.run_bootstrap_import(
                app_root=app_root,
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
            )

        run_subprocess.assert_called_once()
        command = run_subprocess.call_args.args[0]
        self.assertEqual(
            command,
            [
                "bash",
                str(MODULE.deploy_agent_script()),
                "--source",
                "acme_voice_agent",
                "--import",
                "--project",
                "voice-banking-poc",
                "--location",
                "eu",
                "--app-id",
                "acme-voice-eu",
            ],
        )

    def test_reconcile_added_components_with_remote_initializes_state_entries(self) -> None:
        app_root = self.create_app()
        components = MODULE.discover_components(app_root)
        plan = {"added": list(components), "modified": [], "noop": []}
        state_components: dict[str, dict[str, str]] = {}

        with mock.patch.object(
            MODULE,
            "find_existing_resource_name",
            side_effect=[
                "projects/.../toolsets/uuid-toolset",
                "projects/.../tools/uuid-tool",
                "projects/.../agents/uuid-agent",
            ],
        ):
            refreshed_plan, discovered_existing = MODULE.reconcile_added_components_with_remote(
                plan,
                state_components,
                project="voice-banking-poc",
                location="eu",
                app_id="acme-voice-eu",
                endpoint=None,
                token="token",
            )

        self.assertEqual([component.key for component in refreshed_plan["added"]], [])
        self.assertEqual(
            [component.key for component in refreshed_plan["noop"]],
            [component.key for component in components],
        )
        self.assertEqual(
            [component.key for component in discovered_existing],
            [component.key for component in components],
        )
        self.assertEqual(
            state_components["tool:get_customer_details_python"]["resource_name"],
            "projects/.../tools/uuid-tool",
        )


if __name__ == "__main__":
    unittest.main()
