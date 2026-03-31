#!/usr/bin/env python3
"""Regression tests for validate-package.py."""

from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
VALIDATOR = SCRIPT_DIR / "deploy" / "validate-package.py"


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


class ValidatePackageScriptTests(unittest.TestCase):
    def full_global_instruction(self) -> str:
        return textwrap.dedent(
            """
            <persona>
            Global banking persona.
            </persona>
            <constraints>
            1. Be concise.
            </constraints>
            """
        ).strip() + "\n"

    def full_agent_instruction(self) -> str:
        return textwrap.dedent(
            """
            <role>
            Root agent.
            </role>
            <persona>
            Helpful and concise.
            </persona>
            <constraints>
            1. Stay in scope.
            </constraints>
            <taskflow>
            <subtask name="Main">
            <step name="Help">
            <action>Help the caller.</action>
            </step>
            </subtask>
            </taskflow>
            """
        ).strip() + "\n"

    def build_package(self, root: Path, threshold_literal: str | None) -> None:
        threshold_block = ""
        if threshold_literal is not None:
            threshold_block = textwrap.dedent(
                f"""
                ,
                  "evaluationMetricsThresholds": {{
                    "goldenEvaluationMetricsThresholds": {{
                      "turnLevelMetricsThresholds": {{
                        "semanticSimilaritySuccessThreshold": {threshold_literal}
                      }}
                    }}
                  }}
                """
            ).rstrip()

        write_text(
            root / "app.json",
            textwrap.dedent(
                f"""
                {{
                  "displayName": "sample_agent",
                  "rootAgent": "voice_banking_agent",
                  "globalInstruction": "global_instruction.txt",
                  "guardrails": []{threshold_block}
                }}
                """
            ).strip()
            + "\n",
        )
        write_text(root / "global_instruction.txt", self.full_global_instruction())
        write_text(
            root / "agents/voice_banking_agent/voice_banking_agent.json",
            textwrap.dedent(
                """
                {
                  "displayName": "voice_banking_agent",
                  "instruction": "agents/voice_banking_agent/instruction.txt",
                  "tools": ["end_session"]
                }
                """
            ).strip()
            + "\n",
        )
        write_text(root / "agents/voice_banking_agent/instruction.txt", self.full_agent_instruction())

    def run_validator(self, threshold_literal: str | None) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory(prefix="validate-package-") as temp_dir:
            package_root = Path(temp_dir)
            self.build_package(package_root, threshold_literal)
            return subprocess.run(
                [sys.executable, str(VALIDATOR), str(package_root)],
                capture_output=True,
                text=True,
                check=False,
            )

    def test_accepts_integer_int32_threshold(self) -> None:
        result = self.run_validator("3")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("semanticSimilaritySuccessThreshold = 3 -> PASS", result.stdout)

    def test_rejects_decimal_threshold(self) -> None:
        result = self.run_validator("2.5")
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("semanticSimilaritySuccessThreshold", result.stdout)
        self.assertIn("integer/int32 literal", result.stdout)

    def test_rejects_instruction_missing_required_sections_from_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="validate-package-") as temp_dir:
            package_root = Path(temp_dir)
            self.build_package(package_root, None)
            write_text(
                package_root / "agents/voice_banking_agent/instruction.txt",
                "<role>Root agent only</role>\n",
            )
            result = subprocess.run(
                [sys.executable, str(VALIDATOR), str(package_root)],
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("CES_INSTRUCTION_MISSING_SECTION", result.stdout)

    def test_rejects_instruction_with_wrong_section_order_from_contract(self) -> None:
        with tempfile.TemporaryDirectory(prefix="validate-package-") as temp_dir:
            package_root = Path(temp_dir)
            self.build_package(package_root, None)
            write_text(
                package_root / "agents/voice_banking_agent/instruction.txt",
                textwrap.dedent(
                    """
                    <persona>
                    Helpful and concise.
                    </persona>
                    <role>
                    Root agent.
                    </role>
                    <constraints>
                    1. Stay in scope.
                    </constraints>
                    <taskflow>
                    <subtask name="Main">
                    <step name="Help">
                    <action>Help the caller.</action>
                    </step>
                    </subtask>
                    </taskflow>
                    """
                ).strip() + "\n",
            )
            result = subprocess.run(
                [sys.executable, str(VALIDATOR), str(package_root)],
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("CES_INSTRUCTION_SECTION_ORDER_INVALID", result.stdout)

    def test_accepts_global_instruction_contract_shape(self) -> None:
        result = self.run_validator(None)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("global_instruction.txt: PASS", result.stdout)


if __name__ == "__main__":
    unittest.main()
