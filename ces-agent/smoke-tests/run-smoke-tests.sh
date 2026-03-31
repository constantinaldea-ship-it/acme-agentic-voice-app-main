#!/usr/bin/env bash
# Compatibility wrapper for the relocated smoke harness.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../test-harness/run-smoke-tests.sh" "$@"
