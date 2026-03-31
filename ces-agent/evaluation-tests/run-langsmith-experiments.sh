#!/usr/bin/env bash
# Compatibility wrapper for the relocated LangSmith experiment runner.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../test-harness/run-langsmith-experiments.sh" "$@"
