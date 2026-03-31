#!/bin/bash
# Deploy bfa-mcp-spike (MCP location services) to Cloud Run.
# ADR-CES-003 spike — feature branch only.
#
# Usage:
#   ./deploy.sh [PROJECT_ID]
#
# Environment variables:
#   GCP_PROJECT_ID — Cloud Run project ID (required via repo root .env unless passed as arg)
#   SERVICE_NAME  — Cloud Run service name (default: bfa-mcp-spike)
#   GCP_REGION    — Cloud Run region (required via repo root .env)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=../../scripts/lib/load-env.sh
source "${REPO_ROOT}/../scripts/lib/load-env.sh"
load_root_env

if [[ -n "${1:-}" ]]; then
  set_gcp_env_override GCP_PROJECT_ID "${1}"
fi

require_gcp_run_env || exit 1

PROJECT_ID="${GCP_PROJECT_ID}"
SERVICE_NAME="${SERVICE_NAME:-bfa-mcp-spike}"
REGION="${GCP_REGION}"
IMAGE_URI="${GCP_CONTAINER_REGISTRY_HOST}/${PROJECT_ID}/${SERVICE_NAME}"

for cmd in gcloud docker mvn; do
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "ERROR: Missing required command: ${cmd}"
    exit 1
  fi
done

echo "=== BFA MCP Spike Deployment ==="
echo "Project ID : ${PROJECT_ID}"
echo "Service    : ${SERVICE_NAME}"
echo "Region     : ${REGION}"
echo "Image      : ${IMAGE_URI}"
echo ""

# Step 1: Build JAR
echo ">>> Building bfa-mcp-spike JAR..."
(
  cd "${REPO_ROOT}"
  mvn -pl bfa-mcp-spike -am clean package -DskipTests
)

# Step 2: Build Docker image
echo ">>> Building Docker image..."
docker build --platform linux/amd64 -t "${IMAGE_URI}" "${SCRIPT_DIR}"

# Step 3: Push image
echo ">>> Pushing image to GCR..."
docker push "${IMAGE_URI}"

# Step 4: Deploy to Cloud Run
# Key: --port 8081 matches application.properties server.port
echo ">>> Deploying to Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
  --project "${PROJECT_ID}" \
  --image "${IMAGE_URI}" \
  --platform managed \
  --region "${REGION}" \
  --allow-unauthenticated \
  --port 8081 \
  --memory 512Mi \
  --cpu 1 \
  --timeout 60 \
  --max-instances 5 \
  --min-instances 0 \
  --execution-environment gen2 \
  --set-env-vars "PORT=8081"

# Step 5: Show service URL
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" --project="${PROJECT_ID}" --region="${REGION}" --format="value(status.url)")
echo ""
echo "=== Deployment complete ==="
echo "Service URL : ${SERVICE_URL}"
echo "MCP         : ${SERVICE_URL}/mcp"
echo ""
echo "Test with MCP Inspector:"
echo "  npx @modelcontextprotocol/inspector"
echo "  Enter MCP URL: ${SERVICE_URL}/mcp"
