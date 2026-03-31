# Evaluation tests moved

The canonical evaluation harness now lives in `ces-agent/test-harness/evaluation/`,
with the shared bash runners in `ces-agent/test-harness/`.

This folder intentionally stays tiny and only preserves compatibility wrappers:

- `run-evaluation-tests.sh`
- `run-langsmith-experiments.sh`
- `ces-evaluation-runner.py`
- `langsmith-live-experiments.py`

Prefer the new home for documentation, suite authoring, and framework changes.
