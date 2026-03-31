#!/usr/bin/env python3
"""Compatibility wrapper for the relocated CES evaluation runner."""

from __future__ import annotations

import runpy
from pathlib import Path
import sys


LEGACY_ROOT = Path(__file__).resolve().parent
NEW_SCRIPT = LEGACY_ROOT.parent / "test-harness" / "evaluation" / "ces-evaluation-runner.py"


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

main = LOADED["main"]


if __name__ == "__main__":
    sys.argv = _rewrite_cli_args(sys.argv)
    raise SystemExit(main())
