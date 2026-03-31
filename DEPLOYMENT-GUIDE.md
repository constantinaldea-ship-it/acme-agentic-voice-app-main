# Deployment Guide

_Last updated: 2026-03-27_

This guide documents the consolidated Cloud Run deployment workflow for the discovery-plan backend stack.

## Scope

The shared automation in `scripts/cloud/` manages these deployable services as one unit:

- `mock-server`
- `bfa-adapter-branch-finder`
- `bfa-gateway`
- `bfa-service-resource`

It also documents the related CES packaging/import workflow in `ces-agent/scripts/deploy/deploy-agent.sh`.

## Deployment automation audit

### Active automation

- `scripts/cloud/deploy.sh` — deploy one service or the full stack
- `scripts/cloud/undeploy.sh` — undeploy one service or the full stack
- `scripts/cloud/smoke-test.sh` — verify health and cross-service integration

During deploy, the shared suite refreshes each service's canonical OpenAPI export into its module-local `openapi-specs/` directory before packaging.

| Service | Canonical OpenAPI artifact |
|---|---|
| `mock-server` | `java/mock-server/openapi-specs/mock-server.json` |
| `bfa-adapter-branch-finder` | `java/bfa-adapter-branch-finder/openapi-specs/branch-finder-adapter.json` |
| `bfa-gateway` | `java/bfa-gateway/openapi-specs/bfa-gateway.json` |
| `bfa-service-resource` | `java/bfa-service-resource/openapi-specs/location-services.json` |
| `bfa-service-resource` | `java/bfa-service-resource/openapi-specs/advisory-appointment.json` |
| `bfa-service-resource` | `java/bfa-service-resource/openapi-specs/bfa-resource-all.json` |

### Legacy wrappers retained for compatibility

- `deploy.sh` → defaults to `scripts/cloud/deploy.sh stack discovery-plan`, while still supporting passthrough `service ...` / `stack ...` usage and shorthand service names such as `./deploy.sh mock-server`
- `undeploy.sh` → defaults to `scripts/cloud/undeploy.sh stack discovery-plan`, while still supporting passthrough `service ...` / `stack ...` usage
- `ces-agent/deploy.sh` → mirrors the root deploy wrapper behavior for the shared deploy suite
- `java/mock-server/deploy.sh` / `java/mock-server/undeploy.sh` → forward to the shared deploy suite

These wrappers no longer contain deployment logic; the shared suite is the single implementation path.

### Non-operational assets reviewed

The repository also contains `cloud_deploy` and `cloud_deployment_manager` icon assets under `agents/gcp-cx-agent-hybrid-enterprise/architecture/icons/`. These are diagram resources, not deployment automation, and were intentionally left in place.

## Service dependency map

| Service | Purpose | Depends on | Dependency wiring |
|---|---|---|---|
| `mock-server` | WireMock upstream for appointment and customer mock APIs | none | none |
| `bfa-adapter-branch-finder` | AG-003 branch finder adapter | none | none |
| `bfa-gateway` | Single-ingress gateway for adapter topology | `bfa-adapter-branch-finder` | `ADAPTER_BRANCH_FINDER_URL` |
| `bfa-service-resource` | Resource-oriented BFA API | `mock-server` | `BFA_APPOINTMENT_UPSTREAM_BASE_URL` |
| CES package import | Agent Studio package/import flow | stable backend URLs | manual config / CES import workflow |

## Prerequisites

```bash
brew install --cask google-cloud-sdk || true
brew install maven || true

gcloud auth login
cp .env.example .env
# then edit .env and set at least GCP_PROJECT_ID, GCP_REGION, and GCP_LOCATION

gcloud config set project "$GCP_PROJECT_ID"
```

Tools required by the shared suite:

- `gcloud`
- `docker`
- `mvn`
- `curl` for smoke tests

## Quick runbook: deploy all / undeploy all

Use these commands from the repository root when you want to bring up or tear down the entire backend stack managed by `scripts/cloud/`.

### Deploy all services

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance

export MOCK_API_KEY="replace-me-if-needed"
./scripts/cloud/deploy.sh stack discovery-plan
```

This deploys the full `discovery-plan` stack:

1. `mock-server`
2. `bfa-adapter-branch-finder`
3. `bfa-gateway`
4. `bfa-service-resource`

The deploy script also writes the resolved service URLs to:

```bash
.tmp/cloud-run/discovery-plan.env
```

### Smoke-test all services

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance
./scripts/cloud/smoke-test.sh stack discovery-plan
```

### Undeploy all services

Safe runtime teardown:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

Deep cleanup including Cloud Run service images:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance
./scripts/cloud/undeploy.sh stack discovery-plan --force --delete-images
```

Verify teardown:

```bash
gcloud run services list --project "$GCP_PROJECT_ID" --region "$GCP_REGION"
gcloud container images list --repository "gcr.io/$GCP_PROJECT_ID"
```

> Note: the CES app/package import flow is separate from the Cloud Run stack. Undeploying `discovery-plan` removes the backend services, but does not remove an imported CES app by itself.

## Shared command surface

From the repository root:

```bash
./scripts/cloud/deploy.sh service <service-name>
./scripts/cloud/deploy.sh stack discovery-plan
./scripts/cloud/smoke-test.sh service <service-name> <base-url>
./scripts/cloud/smoke-test.sh stack discovery-plan
./scripts/cloud/undeploy.sh service <service-name> --force
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

`scripts/cloud/*` and the Java deploy helpers load the repository root `.env` automatically.
You can still override the project per command by passing `[project-id]` as the final positional argument.

Optional destructive cleanup of container images:

```bash
./scripts/cloud/undeploy.sh stack discovery-plan --force --delete-images
```

## Individual service deployment

### 1. `mock-server`

**Dependencies:** none  
**Optional env vars:** `MOCK_API_KEY`

Deploy:

```bash
export MOCK_API_KEY="replace-me-if-needed"
./scripts/cloud/deploy.sh service mock-server
```

Undeploy:

```bash
./scripts/cloud/undeploy.sh service mock-server --force
```

Smoke test:

```bash
./scripts/cloud/smoke-test.sh service mock-server https://mock-server-<hash>-uc.a.run.app
```

Operational notes:

- If `MOCK_API_KEY` is unset, the service still deploys, but without API-key protection.
- After redeploying, update `java/mock-server/bruno/environments/CloudRun.bru` if you want Bruno to point at the latest URL.

### 2. `bfa-adapter-branch-finder`

**Dependencies:** none

Deploy:

```bash
./scripts/cloud/deploy.sh service bfa-adapter-branch-finder
```

Undeploy:

```bash
./scripts/cloud/undeploy.sh service bfa-adapter-branch-finder --force
```

Smoke test:

```bash
./scripts/cloud/smoke-test.sh service bfa-adapter-branch-finder https://bfa-adapter-branch-finder-<hash>-uc.a.run.app
```

### 3. `bfa-gateway`

**Dependencies:** `bfa-adapter-branch-finder`  
**Required env vars if dependency is not already deployed:** `ADAPTER_BRANCH_FINDER_URL`

Deploy with dependency auto-discovery:

```bash
./scripts/cloud/deploy.sh service bfa-gateway
```

Deploy with explicit dependency URL override:

```bash
export ADAPTER_BRANCH_FINDER_URL="https://bfa-adapter-branch-finder-<hash>-uc.a.run.app"
./scripts/cloud/deploy.sh service bfa-gateway
```

Undeploy:

```bash
./scripts/cloud/undeploy.sh service bfa-gateway --force
```

Smoke test:

```bash
./scripts/cloud/smoke-test.sh service bfa-gateway https://bfa-gateway-<hash>-uc.a.run.app
```

### 4. `bfa-service-resource`

**Dependencies:** `mock-server`  
**Required env vars if dependency is not already deployed:** `BFA_APPOINTMENT_UPSTREAM_BASE_URL`  
**Static env vars set by the suite:** `BFA_SECURITY_ENABLED=false`

Deploy with dependency auto-discovery:

```bash
./scripts/cloud/deploy.sh service bfa-service-resource
```

Deploy with explicit upstream override:

```bash
export BFA_APPOINTMENT_UPSTREAM_BASE_URL="https://mock-server-<hash>-uc.a.run.app"
./scripts/cloud/deploy.sh service bfa-service-resource
```

Undeploy:

```bash
./scripts/cloud/undeploy.sh service bfa-service-resource --force
```

Smoke test:

```bash
./scripts/cloud/smoke-test.sh service bfa-service-resource https://bfa-service-resource-<hash>-uc.a.run.app
```

## Full-stack orchestration

### Deploy the full discovery-plan stack

```bash
export MOCK_API_KEY="replace-me-if-needed"
./scripts/cloud/deploy.sh stack discovery-plan
```

The stack deploy order is:

1. `mock-server`
2. `bfa-adapter-branch-finder`
3. `bfa-gateway`
4. `bfa-service-resource`

The script writes resolved service URLs to:

```bash
.tmp/cloud-run/discovery-plan.env
```

### Smoke-test the full stack

```bash
./scripts/cloud/smoke-test.sh stack discovery-plan
```

### Undeploy the full stack

Safe runtime teardown:

```bash
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

Deep cleanup including service images:

```bash
./scripts/cloud/undeploy.sh stack discovery-plan --force --delete-images
```

## CES agent packaging/import

The CES package/import flow remains separate from Cloud Run deployment.

**Dependencies:** backend URLs should already be stable before packaging/importing for a demo or exercise.

Validate/package:

```bash
cd ces-agent/scripts
./deploy-agent.sh
```

Package and import directly:

```bash
cd ces-agent/scripts
./deploy-agent.sh --import
```

## Cost control guidance

The shared deploy suite applies bounded Cloud Run defaults:

- `--min-instances 0`
- bounded `--max-instances`
- explicit region selection
- explicit per-service ports

Recommended cleanup sequence after an exercise:

1. `./scripts/cloud/undeploy.sh stack discovery-plan --force`
2. If the environment is ephemeral, add `--delete-images`
3. Remove or rotate any temporary secrets such as `MOCK_API_KEY`
4. Verify Cloud Run is empty for the stack:

```bash
gcloud run services list --project "$GCP_PROJECT_ID" --region "$GCP_REGION"
```

## Troubleshooting

### Dependency URL could not be resolved

If a dependent service is not yet deployed, export the required URL explicitly:

```bash
export ADAPTER_BRANCH_FINDER_URL="https://..."
export BFA_APPOINTMENT_UPSTREAM_BASE_URL="https://..."
```

### Smoke test fails after deployment

Re-run smoke tests with the exact service URL:

```bash
./scripts/cloud/smoke-test.sh service bfa-gateway https://...
./scripts/cloud/smoke-test.sh service bfa-service-resource https://...
```

### Want the old entrypoints?

Legacy wrappers still work, but they now just forward into the shared suite. Prefer the `scripts/cloud/*` commands for all new documentation and operations.

