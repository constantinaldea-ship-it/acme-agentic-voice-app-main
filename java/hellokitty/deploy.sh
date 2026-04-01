#!/bin/bash
set -e

# Hello Kitty Cloud Run Deployment Script
# Usage: ./deploy.sh [PROJECT_ID] [REGION]

PROJECT_ID=${1:-$PROJECT_ID}
REGION=${2:-us-central1}
SERVICE_NAME="hellokitty"
IMAGE_NAME="gcr.io/${PROJECT_ID}/${SERVICE_NAME}"

if [ -z "$PROJECT_ID" ]; then
    echo "Error: PROJECT_ID is not set. Provide it as the first argument or set the PROJECT_ID environment variable."
    exit 1
fi

echo "--- Deploying ${SERVICE_NAME} to project ${PROJECT_ID} in region ${REGION} ---"

# 1. Build Maven project
echo "Step 1: Building Maven project..."
mvn clean package -DskipTests

# 2. Build Docker image
echo "Step 2: Building Docker image..."
docker build --platform linux/amd64 -t ${IMAGE_NAME} .

# 3. Push to GCR
echo "Step 3: Pushing image to Google Container Registry..."
docker push ${IMAGE_NAME}

# 4. Deploy to Cloud Run
echo "Step 4: Deploying to Google Cloud Run..."
gcloud run deploy ${SERVICE_NAME} \
    --image ${IMAGE_NAME} \
    --platform managed \
    --region ${REGION} \
    --project ${PROJECT_ID} \
    --port 8080 \
    --memory 512Mi \
    --cpu 1 \
    --timeout 60s \
    --max-instances 3 \
    --min-instances 0 \
    --execution-environment gen2 \
    --allow-unauthenticated

echo "--- Deployment Complete ---"
gcloud run services describe ${SERVICE_NAME} --platform managed --region ${REGION} --project ${PROJECT_ID} --format 'value(status.url)'
