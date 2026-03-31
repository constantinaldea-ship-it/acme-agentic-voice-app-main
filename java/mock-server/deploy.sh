#!/usr/bin/env bash
# Modified by Copilot on 2026-03-27
# Compatibility wrapper for the shared Cloud Run deployment suite.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../../scripts/cloud/deploy.sh" service mock-server "$@"
