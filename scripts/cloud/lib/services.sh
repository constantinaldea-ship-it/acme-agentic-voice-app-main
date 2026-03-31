#!/usr/bin/env bash
# Created by Copilot on 2026-03-27
# Service catalog for the Cloud Run deployment suite.

stack_services() {
  case "$1" in
    discovery-plan)
      printf '%s\n' \
        "mock-server" \
        "bfa-adapter-branch-finder" \
        "bfa-gateway" \
        "bfa-service-resource"
      ;;
    *)
      return 1
      ;;
  esac
}

load_service_config() {
  local service="$1"

  SERVICE_KEY="${service}"
  CLOUD_RUN_PORT="8080"
  MIN_INSTANCES="0"
  EXECUTION_ENVIRONMENT="gen2"
  ALLOW_UNAUTHENTICATED="false"
  STATIC_ENV_VARS=""
  DYNAMIC_ENV_HINTS=""
  HEALTH_PATH=""
  SMOKE_KIND="health"
  DEPENDENCIES=""
  EXPORT_OPENAPI="true"
  OPENAPI_TEST_SELECTOR="OpenApiSpecExportTest"

  case "${service}" in
    mock-server)
      CLOUD_RUN_SERVICE="${MOCK_SERVER_SERVICE_NAME:-mock-server}"
      MAVEN_MODULE="mock-server"
      DOCKERFILE_PATH="java/mock-server/Dockerfile"
      BUILD_CONTEXT="java/mock-server"
      CLOUD_RUN_PORT="8080"
      MEMORY="1Gi"
      CPU="1"
      TIMEOUT="300"
      MAX_INSTANCES="4"
      SMOKE_KIND="mock-server"
      DYNAMIC_ENV_HINTS="MOCK_API_KEY"
      OPENAPI_TEST_SELECTOR="OpenApiSpecExportTest"
      ;;
    bfa-adapter-branch-finder)
      CLOUD_RUN_SERVICE="${BFA_ADAPTER_BRANCH_FINDER_SERVICE_NAME:-bfa-adapter-branch-finder}"
      MAVEN_MODULE="bfa-adapter-branch-finder"
      DOCKERFILE_PATH="java/bfa-adapter-branch-finder/Dockerfile"
      BUILD_CONTEXT="java/bfa-adapter-branch-finder"
      CLOUD_RUN_PORT="8082"
      MEMORY="512Mi"
      CPU="1"
      TIMEOUT="180"
      MAX_INSTANCES="3"
      HEALTH_PATH="/health"
      OPENAPI_TEST_SELECTOR="OpenApiSpecExportTest"
      ;;
    bfa-gateway)
      CLOUD_RUN_SERVICE="${BFA_GATEWAY_SERVICE_NAME:-bfa-gateway}"
      MAVEN_MODULE="bfa-gateway"
      DOCKERFILE_PATH="java/bfa-gateway/Dockerfile"
      BUILD_CONTEXT="java/bfa-gateway"
      CLOUD_RUN_PORT="8081"
      MEMORY="512Mi"
      CPU="1"
      TIMEOUT="180"
      MAX_INSTANCES="3"
      HEALTH_PATH="/api/v1/health"
      DEPENDENCIES="bfa-adapter-branch-finder"
      DYNAMIC_ENV_HINTS="ADAPTER_BRANCH_FINDER_URL"
      OPENAPI_TEST_SELECTOR="OpenApiSpecExportTest"
      ;;
    bfa-service-resource)
      CLOUD_RUN_SERVICE="${BFA_SERVICE_RESOURCE_SERVICE_NAME:-bfa-service-resource}"
      MAVEN_MODULE="bfa-service-resource"
      DOCKERFILE_PATH="java/bfa-service-resource/Dockerfile"
      BUILD_CONTEXT="java/bfa-service-resource"
      CLOUD_RUN_PORT="8080"
      MEMORY="1Gi"
      CPU="1"
      TIMEOUT="300"
      MAX_INSTANCES="4"
      HEALTH_PATH="/api/v1/health"
      DEPENDENCIES="mock-server"
      STATIC_ENV_VARS="BFA_SECURITY_ENABLED=${BFA_SECURITY_ENABLED:-false}"
      DYNAMIC_ENV_HINTS="BFA_APPOINTMENT_UPSTREAM_BASE_URL"
      OPENAPI_TEST_SELECTOR="com.voicebanking.bfa.location.OpenApiSpecExportTest,com.voicebanking.bfa.appointment.OpenApiSpecExportTest"
      ;;
    *)
      return 1
      ;;
  esac

  return 0
}

