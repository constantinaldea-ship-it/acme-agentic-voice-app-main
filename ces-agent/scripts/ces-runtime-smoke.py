#!/usr/bin/env python3
"""Compatibility shim for the relocated CES runtime smoke runner.

Canonical smoke-test assets now live in `ces-agent/test-harness/smoke/`.
This file remains as a thin backward-compatible entrypoint for older commands,
imports, and references.
"""

from __future__ import annotations

import runpy
import sys
from pathlib import Path

LEGACY_ROOT = Path(__file__).resolve().parents[1] / "smoke-tests"
NEW_SCRIPT = Path(__file__).resolve().parents[1] / "test-harness" / "smoke" / "ces-runtime-smoke.py"


def _rewrite_cli_args(argv: list[str]) -> list[str]:
    rewritten = list(argv)
    for index, arg in enumerate(rewritten[:-1]):
        if arg != "--suite":
            continue
        candidate = Path(rewritten[index + 1])
        if candidate.is_absolute():
            continue
        legacy_path = (LEGACY_ROOT / candidate).resolve()
        if legacy_path.exists():
            rewritten[index + 1] = str(legacy_path)
            continue
        new_path = (NEW_SCRIPT.parent / candidate).resolve()
        if new_path.exists():
            rewritten[index + 1] = str(new_path)
    return rewritten


LOADED = runpy.run_path(str(NEW_SCRIPT))
for key, value in LOADED.items():
    if key in {"__name__", "__file__", "__package__", "__cached__", "__spec__"}:
        continue
    globals()[key] = value

# Extract SmokeError if available for clean reporting
SmokeError = LOADED.get("SmokeError", type("SmokeError", (RuntimeError,), {}))


if __name__ == "__main__":
    sys.argv = _rewrite_cli_args(sys.argv)
    try:
        raise SystemExit(main())
    except SmokeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
