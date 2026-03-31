# BFA MCP Spike (ADR-CES-003)

> **Branch:** `spike/mcp-location-services`
> **Status:** Spike — feature branch only, do not merge to main
> **ADR:** [ADR-CES-003](../../../ces-agent/docs/ADR-CES-003-mcp-location-services-implementation.md)
> **Author:**  | 2026-02-09

---

## Overview

Self-contained MCP server spike that exposes location services (`branch_search`, `branch_details`) via Streamable HTTP transport. Runs independently from `bfa-service-resource` to keep the spike cleanly separated from production code.

### What this validates

1. **MCP tool registration** — `@Tool` annotations auto-registered as MCP tools by Spring AI
2. **Streamable HTTP transport** — CES-required transport on Spring Boot / Tomcat
3. **Service-layer pattern** — `LocationService` → `BranchRepository` (same architecture as the REST API)
4. **Latency measurement** — Nanosecond-precision timing on each MCP tool call

## Architecture

```
┌────────────────────────────────────────────────────┐
│  bfa-mcp-spike (standalone Spring Boot, port 8081) │
│                                                     │
│  MCP endpoints (Streamable HTTP)                    │
│  /mcp → tools/list → branch_search, branch_details  │
│  /mcp → tools/call → LocationService.search()       │
│                                                     │
│  ┌───────────────────────────────────────────┐      │
│  │  LocationService                          │      │
│  │  └─ BranchRepository (in-memory, JSON)    │      │
│  └───────────────────────────────────────────┘      │
│         ↑                                           │
│  LocationMcpTools (@Component + @Tool)              │
└────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# Build
cd java && mvn compile -pl bfa-mcp-spike

# Run locally (Maven)
./bfa-mcp-spike/run-local.sh

# Run locally (Docker)
./bfa-mcp-spike/run-local.sh docker

# Test with MCP Inspector
npx @modelcontextprotocol/inspector
# Enter MCP URL: http://localhost:8081/mcp
# → tools/list should show branch_search and branch_details
# → tools/call branch_search with {"city": "Köln"}
```

## Build & Deploy

### Local

```bash
./run-local.sh          # Maven (default)
./run-local.sh docker   # Docker container
```

### Cloud Run

```bash
# Deploy (builds JAR + Docker image, pushes, deploys)
./deploy.sh [PROJECT_ID]

# Smoke test the deployment
./test-deployed.sh [SERVICE_URL]

# Or let it auto-discover the URL from Cloud Run:
./test-deployed.sh
```

### Cloud Build (CI/CD)

```bash
# Submit from repo root
gcloud builds submit --config java/bfa-mcp-spike/cloudbuild.yaml .
```

## Files

| File | Purpose |
|------|---------|
| `pom.xml` | Module POM — Spring Boot + Spring AI MCP starter |
| `Dockerfile` | Container image (JRE 21 Alpine, port 8081) |
| `deploy.sh` | One-command deploy to Cloud Run |
| `cloudbuild.yaml` | GCP Cloud Build pipeline (build → test → deploy) |
| `run-local.sh` | Local dev runner (Maven or Docker) |
| `test-deployed.sh` | Smoke tests for deployed service |
| `McpSpikeApplication.java` | Minimal Spring Boot entry point |
| `location/Branch.java` | Domain model (copied from bfa-service-resource) |
| `location/BranchDto.java` | Response DTO (without OpenAPI annotations) |
| `location/BranchSearchRequest.java` | Query parameters record |
| `location/BranchSearchResponse.java` | Search response wrapper |
| `location/BranchRepository.java` | In-memory JSON-backed repository |
| `location/LocationService.java` | Search business logic + Haversine |
| `tools/LocationMcpTools.java` | MCP tool definitions (`@Tool`) |
| `resources/data/branches.json` | Symlink to shared branch data |
| `resources/application.properties` | MCP server config |

## MCP Tools

### `branch_search`
Search with combinable filters: city, address, postalCode, lat/lon, brand, accessible, limit.

### `branch_details`
Get full branch info by branchId (from search results).

## CES Integration

Use the binding template at `ces-agent/acme_voice_agent/toolsets/location_mcp/location_mcp.json`:

```json
{
  "displayName": "location_mcp",
  "mcpTool": {
    "serverUrl": "https://<CLOUD_RUN_URL>/mcp",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```
