#!/bin/bash

# Deploy POC Event Store (voice-banking-app with poc profile) to Cloud Run
# Reuses patterns from deploy.sh (root-level BFA deployment script)
# Created: 2026-03-10

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_ROOT="${SCRIPT_DIR}/.."
# shellcheck source=../../scripts/lib/load-env.sh
source "${JAVA_ROOT}/../scripts/lib/load-env.sh"
load_root_env

if [[ -n "${1:-}" ]]; then
    set_gcp_env_override GCP_PROJECT_ID "${1}"
fi

require_gcp_run_env || exit 1

PROJECT_ID="${GCP_PROJECT_ID}"
SERVICE_NAME="${SERVICE_NAME:-voice-banking-poc-eventstore}"
REGION="${GCP_REGION}"
IMAGE_URI="${GCP_CONTAINER_REGISTRY_HOST}/${PROJECT_ID}/${SERVICE_NAME}"

for cmd in gcloud docker mvn; do
    if ! command -v "${cmd}" >/dev/null 2>&1; then
        echo "ERROR: Missing required command: ${cmd}"
        exit 1
    fi
done

echo "=========================================="
echo "POC Event Store — Cloud Run Deployment"
echo "=========================================="
echo "Project ID : ${PROJECT_ID}"
echo "Service    : ${SERVICE_NAME}"
echo "Region     : ${REGION}"
echo "Image      : ${IMAGE_URI}"
echo "Profiles   : local,poc"
echo ""

# -----------------------------------------------
# 1. Build the voice-banking-app JAR
# -----------------------------------------------
echo "▸ Building voice-banking-app JAR..."
(
    cd "${JAVA_ROOT}"
    mvn -pl voice-banking-app -am clean package -DskipTests
)

# -----------------------------------------------
# 2. Build Docker image (reuses existing Dockerfile)
# -----------------------------------------------
echo "▸ Building Docker image..."
docker build --platform linux/amd64 \
    -t "${IMAGE_URI}" \
    "${JAVA_ROOT}"

# -----------------------------------------------
# 3. Push image to GCR
# -----------------------------------------------
echo "▸ Pushing image to Container Registry..."
docker push "${IMAGE_URI}"

# -----------------------------------------------
# 4. Deploy to Cloud Run with poc profile
# -----------------------------------------------
echo "▸ Deploying to Cloud Run..."
gcloud run deploy "${SERVICE_NAME}" \
    --project "${PROJECT_ID}" \
    --image "${IMAGE_URI}" \
    --platform managed \
    --region "${REGION}" \
    --allow-unauthenticated \
    --port 8080 \
    --memory 512Mi \
    --cpu 1 \
    --timeout 300 \
    --max-instances 3 \
    --no-cpu-throttling \
    --execution-environment gen2 \
    --set-env-vars "SPRING_PROFILES_ACTIVE=local,poc"

# -----------------------------------------------
# 5. Print service URL and run smoke test
# -----------------------------------------------
SERVICE_URL=$(gcloud run services describe "${SERVICE_NAME}" \
    --project="${PROJECT_ID}" \
    --region="${REGION}" \
    --format="value(status.url)")

echo ""
echo "=========================================="
echo "✓ Deployment complete"
echo "=========================================="
echo "Service URL : ${SERVICE_URL}"
echo "H2 Console  : ${SERVICE_URL}/h2-console"
echo "Events API  : ${SERVICE_URL}/api/poc/events"
echo ""

echo "▸ Running smoke test..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${SERVICE_URL}/actuator/health")
if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✓ Health check passed (HTTP 200)"
else
    echo "✗ Health check failed (HTTP ${HTTP_CODE})"
    exit 1
fi

echo ""
echo "To run full integration tests:"
echo "  ./test-poc-eventstore.sh ${SERVICE_URL}"
