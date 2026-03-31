#!/usr/bin/env python3
"""POC tests for deterministic CES instruction contract validation.

Author: Codex
Date: 2026-03-27
"""

from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


SCRIPT_DIR = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIR / "prompt_contracts.py"
SPEC = importlib.util.spec_from_file_location("prompt_contracts", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load prompt contract module from {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules["prompt_contracts"] = MODULE
SPEC.loader.exec_module(MODULE)

AgentManifestContract = MODULE.AgentManifestContract
InstructionContract = MODULE.InstructionContract
default_rule_set_path = MODULE.default_rule_set_path
load_instruction_rule_set = MODULE.load_instruction_rule_set
find_missing_sections = MODULE.find_missing_sections
find_unknown_direct_tools = MODULE.find_unknown_direct_tools
find_unknown_toolset_operations = MODULE.find_unknown_toolset_operations


PACKAGE_DIR = SCRIPT_DIR.parent / "acme_voice_agent"
LOCATION_AGENT_JSON = PACKAGE_DIR / "agents" / "location_services_agent" / "location_services_agent.json"
LOCATION_INSTRUCTION = PACKAGE_DIR / "agents" / "location_services_agent" / "instruction.txt"


class LocationServicesPromptContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.contract = InstructionContract.load(
            LOCATION_INSTRUCTION,
            package_root=PACKAGE_DIR,
            agent_name="location_services_agent",
        )
        cls.manifest = AgentManifestContract.load(LOCATION_AGENT_JSON)
        cls.rule_set = load_instruction_rule_set()
        cls.structure_rule = cls.rule_set.find_rule(cls.contract.relative_path)

    def test_default_rule_set_path_is_local_to_ces_agent_scripts(self) -> None:
        expected = SCRIPT_DIR / "contracts" / "instruction-contract-rules.json"
        self.assertEqual(default_rule_set_path(), expected)
        self.assertTrue(expected.is_file())

    def test_location_instruction_has_all_required_top_level_sections(self) -> None:
        self.assertIsNotNone(self.structure_rule)
        self.assertEqual(find_missing_sections(self.contract, self.structure_rule), [])

    def test_prompt_references_only_declared_direct_tools_and_toolset_operations(self) -> None:
        self.assertEqual(find_unknown_direct_tools(self.contract, self.manifest), [])
        self.assertEqual(find_unknown_toolset_operations(self.contract, self.manifest), [])

    def test_prompt_only_transfers_back_to_root_agent(self) -> None:
        self.assertEqual(self.contract.agent_refs, ("voice_banking_agent",))

    def test_prompt_keeps_critical_location_rules(self) -> None:
        normalized_text = " ".join(self.contract.raw_text.split()).lower()
        required_snippets = [
            "do not re-greet the customer.",
            "use only the `totalmatches` field for the total number of branches.",
            "do not send `limit` unless the user asks for a specific number.",
            "do not make any claims about whether the city or village exists,",
            "never call end_session without a verbal goodbye.",
        ]
        for snippet in required_snippets:
            with self.subTest(snippet=snippet):
                self.assertIn(snippet, normalized_text)

    def test_examples_encode_english_city_name_translation_for_api_calls(self) -> None:
        example_tool_calls = {
            example.user: example.tool_calls
            for example in self.contract.examples
            if example.user and example.tool_calls
        }
        self.assertIn(
            'location.searchBranches(city="München")',
            example_tool_calls["I need a branch in Munich"],
        )
        self.assertIn(
            'location.searchBranches(city="Köln")',
            example_tool_calls["Find branches in Cologne"],
        )

    def test_examples_do_not_reintroduce_legacy_limit_five_bug(self) -> None:
        for example in self.contract.examples:
            for tool_call in example.tool_calls:
                with self.subTest(tool_call=tool_call):
                    self.assertNotIn("limit=5", tool_call)

    def test_examples_preserve_goodbye_before_end_session_pattern(self) -> None:
        goodbye_examples = [
            example
            for example in self.contract.examples
            if example.tool_calls and any(call.startswith("end_session(") for call in example.tool_calls)
        ]
        self.assertGreaterEqual(len(goodbye_examples), 2)
        for example in goodbye_examples:
            with self.subTest(user=example.user):
                self.assertIsNotNone(example.agent)
                self.assertIn("Acme Bank", example.agent)


if __name__ == "__main__":
    unittest.main()
