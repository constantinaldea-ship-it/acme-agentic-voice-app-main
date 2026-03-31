#!/usr/bin/env python3
"""Canonical validator entrypoint for the CES app package."""

from pathlib import Path
import runpy
import sys


def main() -> None:
    deploy_dir = Path(__file__).resolve().parent
    scripts_dir = deploy_dir.parent
    implementation = scripts_dir.parent / "acme_voice_agent" / "validate-package.py"

    if not implementation.is_file():
        raise SystemExit(f"Validator implementation not found: {implementation}")

    if str(scripts_dir) not in sys.path:
        sys.path.insert(0, str(scripts_dir))

    sys.argv[0] = str(implementation)
    runpy.run_path(str(implementation), run_name="__main__")


if __name__ == "__main__":
    main()
