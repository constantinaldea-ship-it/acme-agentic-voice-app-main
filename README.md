# Voice Banking App

> **Primary implementation:** Java / Spring Boot  
> **Workspace status:** consolidated multi-module Java workspace  
> **Last updated:** 2026-03-27

This repository contains the backend services, mock upstreams, and CES packaging assets for the voice banking discovery plan.

## Workspace layout

- `java/` — Maven reactor for backend services and the moved `mock-server`
- `scripts/cloud/` — shared deploy / undeploy / smoke-test automation for Cloud Run
- `ces-agent/` — CES agent package plus validation/import tooling
- `architecture/`, `docs/`, `agents/` — reference architecture, development WoW, and workflow guidance

## Quick start

### Build the unified Java workspace

```bash
cd java
mvn clean verify -DskipTests
```

### Build one module

```bash
cd java
mvn -pl bfa-service-resource -am clean package -DskipTests
```

## Deployment

Use the shared Cloud Run automation from the repository root:

```bash
cp .env.example .env
# edit .env and set at least GCP_PROJECT_ID, GCP_REGION, and GCP_LOCATION

./scripts/cloud/deploy.sh stack discovery-plan
./scripts/cloud/smoke-test.sh stack discovery-plan
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

See `DEPLOYMENT-GUIDE.md` for the detailed service-by-service deployment plan, dependency map, and teardown procedures.

## License

UNLICENSED - Internal PoC only
