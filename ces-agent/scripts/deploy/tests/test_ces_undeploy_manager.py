import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent.parent / "ces-undeploy-manager.py"
SPEC = importlib.util.spec_from_file_location("ces_undeploy_manager", MODULE_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CesUndeployManagerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)

    def write_manifest(self, relative_path: str, display_name: str) -> Path:
        manifest_path = self.root / relative_path
        manifest_path.parent.mkdir(parents=True, exist_ok=True)
        manifest_path.write_text(
            json.dumps({"displayName": display_name}, indent=2) + "\n",
            encoding="utf-8",
        )
        return manifest_path

    def test_merge_targets_prefers_local_entries_and_orders_reverse_dependencies(self) -> None:
        self.write_manifest("acme_voice_agent/toolsets/accounts/accounts.json", "accounts")
        self.write_manifest("acme_voice_agent/tools/customer_lookup/customer_lookup.json", "customer_lookup")
        self.write_manifest("acme_voice_agent/agents/advisor/advisor.json", "advisor")

        local_targets = MODULE.discover_local_targets(self.root / "acme_voice_agent")
        merged = MODULE.merge_targets(
            local_targets,
            {
                "tool:legacy_tool": {
                    "kind": "tool",
                    "resource_id": "legacy_tool",
                    "display_name": "legacy_tool",
                    "resource_name": "projects/test/locations/us/apps/demo/tools/legacy_tool",
                },
                "agent:advisor": {
                    "kind": "agent",
                    "resource_id": "advisor",
                    "display_name": "stale-advisor",
                    "resource_name": "projects/test/locations/us/apps/demo/agents/advisor",
                },
            },
        )

        self.assertEqual(
            [(target.kind, target.resource_id, target.state_only) for target in merged],
            [
                ("agent", "advisor", False),
                ("tool", "customer_lookup", False),
                ("tool", "legacy_tool", True),
                ("toolset", "accounts", False),
            ],
        )
        advisor = merged[0]
        self.assertEqual(advisor.display_name, "advisor")
        self.assertEqual(
            advisor.resource_name,
            "projects/test/locations/us/apps/demo/agents/advisor",
        )

    def test_execute_plan_ignores_not_found_and_clears_state_entries(self) -> None:
        state_file = self.root / ".ces-deployment-state.json"
        state = {
            "schema_version": 1,
            "components": {
                "agent:advisor": {
                    "kind": "agent",
                    "resource_id": "advisor",
                    "display_name": "advisor",
                    "resource_name": "projects/test/locations/us/apps/demo/agents/advisor",
                },
                "tool:legacy_tool": {
                    "kind": "tool",
                    "resource_id": "legacy_tool",
                    "display_name": "legacy_tool",
                    "resource_name": "projects/test/locations/us/apps/demo/tools/legacy_tool",
                },
            },
        }
        MODULE.write_state(state_file, state)
        targets = MODULE.merge_targets([], state["components"])

        with patch.object(MODULE, "get_access_token", return_value="token"), patch.object(
            MODULE,
            "http_request",
            return_value=(404, '{"error": {"message": "not found"}}'),
        ):
            exit_code = MODULE.execute_plan(
                targets,
                project="test",
                location="us",
                app_id="demo",
                endpoint=None,
                state=state,
                state_file=state_file,
                dry_run=False,
            )

        self.assertEqual(exit_code, 0)
        self.assertEqual(state["components"], {})
        persisted = json.loads(state_file.read_text(encoding="utf-8"))
        self.assertEqual(persisted["components"], {})

    def test_execute_plan_continues_after_non_not_found_failure(self) -> None:
        state = {
            "schema_version": 1,
            "components": {
                "agent:advisor": {
                    "kind": "agent",
                    "resource_id": "advisor",
                    "display_name": "advisor",
                    "resource_name": "projects/test/locations/us/apps/demo/agents/advisor",
                },
                "tool:legacy_tool": {
                    "kind": "tool",
                    "resource_id": "legacy_tool",
                    "display_name": "legacy_tool",
                    "resource_name": "projects/test/locations/us/apps/demo/tools/legacy_tool",
                },
            },
        }
        state_file = self.root / ".ces-deployment-state.json"
        MODULE.write_state(state_file, state)
        targets = [
            MODULE.UndeployTarget(
                key="agent:advisor",
                kind="agent",
                resource_id="advisor",
                display_name="advisor",
                source_path=None,
                resource_name="projects/test/locations/us/apps/demo/agents/advisor",
                state_only=True,
            ),
            MODULE.UndeployTarget(
                key="tool:legacy_tool",
                kind="tool",
                resource_id="legacy_tool",
                display_name="legacy_tool",
                source_path=None,
                resource_name="projects/test/locations/us/apps/demo/tools/legacy_tool",
                state_only=True,
            ),
        ]
        responses = iter(
            [
                (500, '{"error": {"message": "boom"}}'),
                (404, '{"error": {"message": "not found"}}'),
                (404, '{"error": {"message": "not found"}}'),
            ]
        )

        with patch.object(MODULE, "get_access_token", return_value="token"), patch.object(
            MODULE,
            "http_request",
            side_effect=lambda *_args, **_kwargs: next(responses),
        ):
            exit_code = MODULE.execute_plan(
                targets,
                project="test",
                location="us",
                app_id="demo",
                endpoint=None,
                state=state,
                state_file=state_file,
                dry_run=False,
            )

        self.assertEqual(exit_code, 1)
        self.assertIn("agent:advisor", state["components"])
        self.assertNotIn("tool:legacy_tool", state["components"])


if __name__ == "__main__":
    unittest.main()
