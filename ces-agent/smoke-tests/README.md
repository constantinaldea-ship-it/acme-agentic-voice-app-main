# Smoke tests moved

The canonical smoke harness now lives in `ces-agent/test-harness/smoke/`, with
the shared bash runner in `ces-agent/test-harness/run-smoke-tests.sh`.

This folder intentionally stays tiny and only preserves compatibility wrappers:

- `run-smoke-tests.sh`
- `ces-runtime-smoke.py`

Prefer the new home for documentation, suite authoring, and framework changes.
