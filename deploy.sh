#!/usr/bin/env bash
# Modified by Copilot on 2026-03-29
# Root wrapper for Cloud Run deployment. Defaults to the full discovery-plan
# stack, but also supports explicit passthrough (`service ...` / `stack ...`)
# plus shorthand service names such as `./deploy.sh mock-server`.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

case "${1:-}" in
	service|stack|-h|--help|help)
		exec "${SCRIPT_DIR}/scripts/cloud/deploy.sh" "$@"
		;;
	mock-server|bfa-adapter-branch-finder|bfa-gateway|bfa-service-resource)
		exec "${SCRIPT_DIR}/scripts/cloud/deploy.sh" service "$@"
		;;
	discovery-plan)
		shift
		exec "${SCRIPT_DIR}/scripts/cloud/deploy.sh" stack discovery-plan "$@"
		;;
	*)
		exec "${SCRIPT_DIR}/scripts/cloud/deploy.sh" stack discovery-plan "$@"
		;;
esac
