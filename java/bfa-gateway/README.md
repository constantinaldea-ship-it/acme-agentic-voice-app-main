# BFA Gateway — ADR-0104 Option C

Single-ingress gateway for the **BFA Gateway + federated domain adapter**
topology defined in [ADR-0104](../../architecture/adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md) Option C.

## Architecture Mapping

| ADR-0104 Concept | Component | Package / Module |
|------------------|-----------|------------------|
| BFA Gateway (single ingress) | `BfaGatewayController` | `controller` |
| Edge PEP | `EdgePepFilter` (@Order 1) | `filter` |
| Response PEP | `ResponsePepFilter` (@Order 2) | `filter` |
| AuthZ PDP | `AuthzPdpService` (stub) | `authz` |
| Adapter Registry | `AdapterRegistry` (HTTP-based) | `adapter` |
| Domain Adapter (AG-003) | `bfa-adapter-branch-finder` | **separate module** (port 8082) |

## Request Flow

```
Client → EdgePepFilter (correlation ID, Bearer check)
       → BfaGatewayController
           → AdapterRegistry.hasAdapter(toolName)
           → AuthzPdpService.evaluate(principal, tool)
           → HTTP POST to remote adapter /invoke endpoint
       → ResponsePepFilter (mask accountNumber, iban, cardNumber)
       → Client
```

## Quick Start

```bash
# 1. Start the adapter (port 8082)
cd java/bfa-adapter-branch-finder
mvn spring-boot:run

# 2. Start the gateway (port 8081) — in a separate terminal
cd java/bfa-gateway
mvn spring-boot:run
# or
java -jar target/bfa-gateway-0.1.0-SNAPSHOT.jar
```

The gateway starts on **port 8081** (configurable via `PORT` env var).

## Try It

```bash
# Health check
curl http://localhost:8081/api/v1/health

# List registered tools
curl -H "Authorization: Bearer demo-user" \
     http://localhost:8081/api/v1/tools

# Invoke branch-finder
curl -X POST http://localhost:8081/api/v1/tools/invoke \
     -H "Authorization: Bearer demo-user" \
     -H "Content-Type: application/json" \
     -d '{"toolName": "branch-finder", "parameters": {"city": "Frankfurt"}}'

# 401 — no token
curl -X POST http://localhost:8081/api/v1/tools/invoke \
     -H "Content-Type: application/json" \
     -d '{"toolName": "branch-finder", "parameters": {"city": "Berlin"}}'

# 404 — unknown tool
curl -X POST http://localhost:8081/api/v1/tools/invoke \
     -H "Authorization: Bearer demo-user" \
     -H "Content-Type: application/json" \
     -d '{"toolName": "unknown-tool", "parameters": {}}'
```

## Response PEP — PII Masking

The `ResponsePepFilter` automatically masks sensitive fields in JSON responses.
Notice that `accountNumber` values are returned as `"****0 00"` instead of
the full IBAN — this happens transparently at the gateway layer.

Masked fields (configurable): `accountNumber`, `iban`, `cardNumber`.

## Running Tests

```bash
# Gateway tests (19 tests — adapter calls are mocked via @MockBean)
cd java/bfa-gateway
mvn clean test

# Adapter tests (15 tests — standalone)
cd java/bfa-adapter-branch-finder
mvn clean test

# Both from the parent
cd java
mvn clean test -pl bfa-gateway,bfa-adapter-branch-finder
```

**Gateway test coverage (19 tests):**
- `BfaGatewayDemoApplicationTest` — context loads
- `AuthzPdpServiceTest` — PERMIT/DENY stub behaviour
- `ResponsePepFilterTest` — masking logic
- `BfaGatewayControllerTest` — integration tests (200, 400, 401, 404, 502, PII masking, discovery)

**Adapter test coverage (15 tests):**
- `BranchFinderAdapterApplicationTest` — context loads
- `BranchFinderServiceTest` — domain logic (city search, case insensitivity, unknown city)
- `BranchFinderControllerTest` — controller tests (invoke, validation, health)

## Swagger UI

- Gateway: http://localhost:8081/swagger-ui.html
- Adapter: http://localhost:8082/swagger-ui.html

## Adding a New Adapter

Per ADR-0104 Option C, each adapter is a **separately deployable Spring Boot service**
with its own module, POM, port, and test suite.

1. Create a new Maven module under `java/` (e.g. `bfa-adapter-my-tool`)
2. Add it to the parent POM modules list
3. Implement a `POST /invoke` endpoint accepting `AdapterRequest` and returning `AdapterResponse`
4. Register the adapter URL in the gateway's `application.properties`:

```properties
bfa.gateway.adapters.my-tool.url=${ADAPTER_MY_TOOL_URL:http://localhost:8083/invoke}
bfa.gateway.adapters.my-tool.description=AG-XXX — My Tool (description)
```

See `bfa-adapter-branch-finder/` for a complete reference implementation.

> **Anti-pattern:** Do NOT add adapters as in-process `@Component` classes inside the
> gateway module. This collapses the deployment boundary and degrades Option C to
> Option A (monolith). See ADR-0104 §Packaging Model for details.

## Project Structure

```
java/bfa-gateway/                         # Gateway module (port 8081)
  src/main/java/com/voicebanking/bfa/gateway/
  ├── BfaGatewayDemoApplication.java      # Entry point
  ├── adapter/
  │   └── AdapterRegistry.java            # Config-driven URL routing + RestClient
  ├── authz/
  │   └── AuthzPdpService.java            # Stub AuthZ PDP
  ├── controller/
  │   └── BfaGatewayController.java       # Single-ingress gateway
  ├── dto/
  │   ├── ErrorResponse.java              # Error envelope
  │   ├── ToolInvokeRequest.java          # Inbound request
  │   └── ToolInvokeResponse.java         # Outbound response
  ├── exception/
  │   └── GlobalExceptionHandler.java     # Centralised error handling
  └── filter/
      ├── EdgePepFilter.java              # Authentication + correlation ID
      └── ResponsePepFilter.java          # PII masking

java/bfa-adapter-branch-finder/           # Adapter module (port 8082)
  src/main/java/com/voicebanking/bfa/adapter/branchfinder/
  ├── BranchFinderAdapterApplication.java # Entry point
  ├── controller/
  │   └── BranchFinderController.java     # POST /invoke + GET /health
  ├── dto/
  │   ├── AdapterRequest.java             # Inbound: correlationId + parameters
  │   └── AdapterResponse.java            # Outbound: success/error envelope
  └── service/
      └── BranchFinderService.java        # Domain logic (branch lookup)
```
