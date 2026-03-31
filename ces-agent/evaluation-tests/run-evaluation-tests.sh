#!/usr/bin/env bash
# Compatibility wrapper for the relocated evaluation harness.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../test-harness/run-evaluation-tests.sh" "$@"
