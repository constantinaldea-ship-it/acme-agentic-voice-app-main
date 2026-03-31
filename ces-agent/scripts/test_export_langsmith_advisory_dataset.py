import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
MODULE_PATH = SCRIPT_DIR / "export_langsmith_advisory_dataset.py"
SPEC = importlib.util.spec_from_file_location("export_langsmith_advisory_dataset", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Could not load exporter module from {MODULE_PATH}")
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules["export_langsmith_advisory_dataset"] = MODULE
SPEC.loader.exec_module(MODULE)

build_dataset_seed = MODULE.build_dataset_seed
build_example = MODULE.build_example
infer_journey_type = MODULE.infer_journey_type
infer_language = MODULE.infer_language


class ExportLangSmithAdvisoryDatasetTests(unittest.TestCase):
    def create_temp_dir(self) -> Path:
        path = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: path.exists() and __import__("shutil").rmtree(path))
        return path

    def write_evaluation(self, root: Path, folder_name: str, payload: dict) -> Path:
        folder = root / folder_name
        folder.mkdir(parents=True, exist_ok=True)
        path = folder / f"{folder_name}.json"
        path.write_text(json.dumps(payload), encoding="utf-8")
        return path

    def test_infer_helpers(self) -> None:
        self.assertEqual(infer_journey_type("appointment_routing_german"), "routing")
        self.assertEqual(infer_journey_type("appointment_cancel_reschedule"), "lifecycle")
        self.assertEqual(infer_journey_type("appointment_no_slots_recovery"), "recovery")
        self.assertEqual(infer_journey_type("appointment_video_consultation"), "booking")
        self.assertEqual(infer_language("appointment_routing_german"), "de")
        self.assertEqual(infer_language("appointment_routing_english"), "en")

    def test_build_example_maps_ces_scenario_to_langsmith_shape(self) -> None:
        temp_dir = self.create_temp_dir()
        package_root = temp_dir / "ces-agent"
        evaluations_root = package_root / "acme_voice_agent" / "evaluations"
        path = self.write_evaluation(
            evaluations_root,
            "appointment_booking_branch_flow",
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

        example = build_example(path, package_root)
        self.assertEqual(example["name"], "appointment_booking_branch_flow")
        self.assertEqual(example["inputs"]["task"], "Book an appointment in Berlin")
        self.assertEqual(example["inputs"]["max_turns"], 10)
        self.assertEqual(
            example["outputs"]["reference_expectations"],
            [
                "Routes to advisory_appointment_agent.",
                "Asks for confirmation before booking.",
            ],
        )
        self.assertEqual(example["metadata"]["journey_type"], "booking")
        self.assertEqual(
            example["metadata"]["source_path"],
            "acme_voice_agent/evaluations/appointment_booking_branch_flow/appointment_booking_branch_flow.json",
        )

    def test_build_dataset_seed_collects_all_appointment_evals(self) -> None:
        temp_dir = self.create_temp_dir()
        evaluations_root = temp_dir / "acme_voice_agent" / "evaluations"
        self.write_evaluation(
            evaluations_root,
            "appointment_routing_english",
            {
                "displayName": "appointment_routing_english",
                "scenario": {
                    "task": "I want an appointment",
                    "scenarioExpectations": [{"expectedResult": "Routes correctly."}],
                    "maxTurns": 2,
                },
            },
        )
        self.write_evaluation(
            evaluations_root,
            "appointment_cancel_reschedule",
            {
                "displayName": "appointment_cancel_reschedule",
                "scenario": {
                    "task": "Cancel my appointment",
                    "scenarioExpectations": [{"expectedResult": "Asks for confirmation."}],
                    "maxTurns": 8,
                },
            },
        )

        dataset = build_dataset_seed(evaluations_root, "advisory-dataset")
        self.assertEqual(dataset["dataset_name"], "advisory-dataset")
        self.assertEqual(len(dataset["examples"]), 2)
        self.assertEqual(
            [example["name"] for example in dataset["examples"]],
            ["appointment_cancel_reschedule", "appointment_routing_english"],
        )


if __name__ == "__main__":
    unittest.main()
