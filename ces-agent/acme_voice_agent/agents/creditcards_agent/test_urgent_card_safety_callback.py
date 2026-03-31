#!/usr/bin/env python3
"""Unit tests for the creditcards_agent callback example."""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "before_model_callbacks" / "urgent_card_safety" / "python_code.py"
SPEC = importlib.util.spec_from_file_location("creditcards_urgent_card_safety_callback", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CreditcardsUrgentCardSafetyCallbackTests(unittest.TestCase):
    def test_detects_english_urgent_card_safety_request(self) -> None:
        self.assertTrue(MODULE.is_urgent_card_safety_request("Please block my card, I think it was stolen."))

    def test_detects_german_urgent_card_safety_request(self) -> None:
        self.assertTrue(MODULE.is_urgent_card_safety_request("Meine Karte wurde gestohlen, bitte Karte sperren."))

    def test_ignores_non_urgent_card_question(self) -> None:
        self.assertFalse(MODULE.is_urgent_card_safety_request("How does contactless payment work abroad?"))

    def test_builds_german_safety_message_for_german_input(self) -> None:
        message = MODULE.build_urgent_card_safety_message("Bitte Karte sperren, ich vermute Betrug.")
        self.assertIn("Kartensperr", message)

    def test_builds_english_safety_message_for_english_input(self) -> None:
        message = MODULE.build_urgent_card_safety_message("I need to block my card because of fraud.")
        self.assertIn("official card-blocking", message)


if __name__ == "__main__":
    unittest.main()
