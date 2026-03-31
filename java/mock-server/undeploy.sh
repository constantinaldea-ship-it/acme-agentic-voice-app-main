#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Compatibility wrapper for removing mock-server through the shared Cloud Run suite.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${SCRIPT_DIR}/../../scripts/cloud/undeploy.sh" service mock-server "$@"

