# ADR-CES-001: REST API (BFA) vs MCP Server for Agent Tool Integration

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-08  
**Owner:** Architecture Guild / CES Integration Team  
**Decision Drivers:** Agent integration patterns, protocol standardization, CES constraints, developer experience, multi-client support  
**Related:**
- [ADR-J009: BFA-CES Architecture](../../docs/adr/ADR-J009-bfa-ces-architecture.md)
- [ADR-J008: BFA Analysis](../../docs/adr/ADR-J008-backend-for-agents-analysis.md)
- [bfa-service-resource](../../java/bfa-service-resource/) — current REST API implementation
- [CES Agent toolsets](../acme_voice_agent/toolsets/) — current OpenAPI-based tool binding
- [CX Agent Studio Platform Reference](cx-agent-studio/platform-reference.md) — tool types, auth, MCP support (§ 5)
- [CX Agent Studio Design Patterns](cx-agent-studio/design-patterns.md) — `x-ces-session-context` (§ 5.1)

---

## Context

### Current State

The Voice Banking Assistant uses a **two-layer architecture** to connect the CES conversational agent to banking backend services:

```
┌──────────────────┐     OpenAPI toolset      ┌───────────────────────┐
│   CES Agent      │ ──── (HTTP/REST) ──────► │  bfa-service-resource │
│   (Dialogflow CX)│                           │  (Spring Boot REST)   │
│                  │     location.json          │                       │
│  toolsets/       │     open_api_schema.yaml   │  /api/v1/accounts     │
│  └─ location/    │                           │  /api/v1/cards        │
│                  │                           │  /api/v1/transfers    │
│                  │                           │  /api/v1/branches     │
└──────────────────┘                           └───────────────────────┘
```

**How it works today:**
1. `bfa-service-resource` is a standard **Spring Boot REST API** exposing HTTP endpoints with OpenAPI/Swagger documentation.
2. CES agents reference these endpoints via **OpenAPI toolsets** (`toolsets/location/open_api_schema.yaml`), which Dialogflow CX uses to generate tool calls at runtime.
3. Authentication is handled via `serviceAgentIdTokenAuthConfig` (Google service account identity tokens).
4. The backend URL is injected through `app.json` → `variableDeclarations` → `$env_var` substitution.

### The Question

The **Model Context Protocol (MCP)** has emerged as a standardized protocol for AI model ↔ tool communication. Should we replace or supplement the current REST API + OpenAPI toolset pattern with an MCP server?

### What is MCP?

MCP (Model Context Protocol) is a JSON-RPC 2.0 based protocol that standardizes how AI models discover and invoke tools. It defines three primitives:

| Primitive | Purpose | REST Equivalent |
|-----------|---------|-----------------|
| **Tools** | Actions the AI can invoke (with JSON Schema params) | REST endpoints |
| **Resources** | Read-only data the AI can browse | GET endpoints / static data |
| **Prompts** | Reusable prompt templates | No equivalent |

Transports: **stdio** (local subprocess) or **Streamable HTTP** (remote, CES-required). SSE remains a non-CES MCP transport option.

---

## Decision

**Retain the REST API (`bfa-service-resource`) as the primary backend, with OpenAPI toolsets for CES integration.** MCP is not adopted at this time.

### Rationale

a1. **OpenAPI toolsets are the mature, production-tested CES integration path.** CES supports four tool types — `pythonFunction`, `openApiToolset`, `googleSearchTool`, and `mcpTool` — all as first-class citizens ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)). However, OpenAPI toolsets offer a critical capability that MCP tools currently lack: **`x-ces-session-context`** for deterministic session context injection without model prediction ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context)). For banking operations requiring session/customer ID audit correlation, this makes OpenAPI the only viable choice today.

2. **OpenAPI toolsets have the deepest CES ecosystem integration.** The current pattern (`toolsets/location/location.json` → `open_api_schema.yaml`) receives first-class support in the CES console, testing, deployment toolchain, and evaluation workflows. CES MCP support is functional but newer. As of 2026-02-12, CES requires **Streamable HTTP transport** for MCP servers — SSE transport is not supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)).

3. **The REST API serves multiple consumers.** The `bfa-service-resource` is consumed by CES agents, potentially by a web/mobile frontend, and by integration tests. MCP servers are purpose-built for AI model hosts and would not serve non-AI consumers.

4. **The service layer is transport-agnostic.** The business logic (`AccountService`, `CardService`, `TransferService`, etc.) is cleanly separated from controllers. If MCP is needed in the future, a thin MCP adapter can be layered on top of the same services without rewriting business logic.

---

## Alternatives Considered

### Option A: Keep REST API + OpenAPI Toolsets (✅ Chosen)

The current architecture: Spring Boot REST API with CES OpenAPI toolset bindings.

- **Pros:**
  - Native CES integration — no translation layer needed
  - Serves both AI and non-AI consumers (browsers, mobile, integration tests)
  - Mature ecosystem: load balancers, API gateways, observability, caching all work out of the box
  - OpenAPI spec is the single source of truth for both documentation and CES tool generation
  - Google Cloud Run deployment is straightforward (HTTP container)
  - Authentication via Google service account identity tokens is well-understood

- **Cons:**
  - Tool definitions are implicit (derived from OpenAPI) rather than explicitly declared for AI consumption
  - No support for MCP Resources or Prompts primitives
  - AI models outside CES (e.g., Claude, Copilot) cannot natively discover tools without an adapter

### Option B: Replace REST API with MCP Server

Rewrite the transport layer as a pure MCP server (stdio or Streamable HTTP).

- **Pros:**
  - Standardized tool discovery (`tools/list`) and invocation (`tools/call`) across any MCP-compatible AI host
  - First-class support for Resources (browsable data) and Prompts (reusable templates)
  - Potentially simpler auth model for local (stdio) deployments
  - Growing ecosystem adoption (Claude, Copilot, Cursor, etc.)

- **Cons:**
  - **CES MCP tooling lacks `x-ces-session-context`** — banking operations require deterministic session context injection, available only via OpenAPI toolsets ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context)). CES MCP requires Streamable HTTP transport (SSE is not supported as of 2026-02-12).
  - Loses HTTP ecosystem benefits (load balancers, API gateways, standard monitoring)
  - Cannot serve non-AI consumers (web UI, mobile app, curl-based testing)
  - Java MCP SDK is early-stage compared to the mature Spring Boot web stack
  - Deployment model changes: stdio requires co-location with the AI host; Streamable HTTP requires dedicated MCP endpoint infrastructure
  - Team would need to learn a new protocol and toolchain

### Option C: Hybrid — REST API + MCP Sidecar

Keep `bfa-service-resource` as-is. Add a separate MCP server that delegates to the same service layer (or calls the REST API).

- **Pros:**
  - Best of both worlds: CES uses OpenAPI toolsets, non-CES AI hosts use MCP
  - Service logic stays in one place
  - Incremental adoption — MCP layer can be added without touching existing code

- **Cons:**
  - Two integration surfaces to maintain and test
  - Risk of drift between OpenAPI spec and MCP tool definitions
  - Additional deployment artifact and infrastructure
  - Only justified if there are concrete non-CES AI consumers

---

## Comparison Matrix

| Dimension | REST + OpenAPI (Current) | Pure MCP Server | Hybrid (REST + MCP) |
|-----------|--------------------------|-----------------|----------------------|
| **CES Compatibility** | ✅ Native (OpenAPI toolset) | ⚠️ Supported (`mcpTool`) but no `x-ces-session-context` | ✅ Native (REST side) + MCP side |
| **Non-AI Consumers** | ✅ Full support | ❌ Not supported | ✅ Full support |
| **MCP-Native AI Hosts** | ❌ Not supported | ✅ Native | ✅ Via MCP sidecar |
| **Deployment Complexity** | Low (single HTTP container) | Medium (new transport) | High (two surfaces) |
| **Tool Discovery** | Implicit (OpenAPI) | Explicit (`tools/list`) | Both |
| **Auth Model** | Google IAM + service tokens | OAuth 2.1 or implicit (stdio) | Both |
| **Observability** | Mature (HTTP metrics, traces) | Immature | Mixed |
| **Team Familiarity** | High | Low | Medium |
| **Resources/Prompts** | ❌ | ✅ | ✅ (MCP side only) |

---

## Layer-by-Layer: What Would Change (REST → MCP)

For teams evaluating a future migration, this is the concrete mapping:

| Layer | REST API (Current) | MCP Server (Hypothetical) | Change Required |
|-------|-------------------|---------------------------|-----------------|
| **Services** (`AccountService`, etc.) | Business logic | Same business logic | ❌ None |
| **DTOs** (`AccountDto`, `BalanceDto`) | JSON serialization | Internal; format for AI output | 🔄 Add AI-friendly formatting |
| **Controllers** (6 controllers, ~20 endpoints) | `@RestController`, HTTP verbs, paths | `@Tool` handlers with name + description | ✅ Replace entirely |
| **Filters/Interceptors** (auth, consent, audit) | Servlet filters, `@RequiresConsent` | Auth at MCP transport; consent at service layer | ✅ Restructure |
| **OpenAPI/SpringDoc** | Auto-generated spec | MCP tool manifests (JSON Schema) | ✅ Replace |
| **`spring-boot-starter-web`** | Embedded Tomcat | MCP SDK (stdio or Streamable HTTP transport) | ✅ Swap dependency |
| **CES toolset binding** | `open_api_schema.yaml` → direct HTTP | Custom bridge (MCP → webhook) | ✅ New component needed |
| **Deployment** | Cloud Run (HTTP container) | Cloud Run (Streamable HTTP) or co-located (stdio) | ✅ New infra |

---

## Consequences

### Positive
- No disruption to the working CES integration pipeline
- Single deployment artifact serves all consumers
- Team continues using well-understood Spring Boot patterns
- Clear migration path to Hybrid (Option C) if MCP demand materializes

### Negative / Trade-offs
- AI hosts outside CES (Claude Desktop, Copilot, Cursor) cannot natively discover `bfa-service-resource` tools
- No support for MCP Resources (browsable account data) or Prompts (banking prompt templates)
- If the industry converges on MCP as the standard, we may need to retrofit later

### Risks & Mitigations
- **Risk:** CES matures MCP tooling to full parity with OpenAPI toolsets (including `x-ces-session-context`), making OpenAPI toolsets redundant  
  - **Mitigation:** Monitor Google CES roadmap; the service layer is transport-agnostic so migration cost is bounded to the controller/transport layer. See [ADR-CES-002](ADR-CES-002-mcp-server-topology.md) for migration triggers.
- **Risk:** Non-CES AI consumers require tool access (e.g., internal Copilot agents)  
  - **Mitigation:** Adopt Option C (Hybrid) incrementally — add an MCP sidecar that calls the existing REST API
- **Risk:** MCP ecosystem matures and becomes a compliance/interoperability requirement  
  - **Mitigation:** Track MCP spec evolution; the `@Tool`-handler migration from `@RestController` is mechanical and can be automated

---

## Decision Triggers for Reassessment

Re-evaluate this ADR if any of the following occur:

1. **CES MCP tooling adds `x-ces-session-context` equivalent** (session context injection for MCP tool calls). ~~Streamable HTTP transport~~ ✅ **MET (2026-02-12):** CES now requires Streamable HTTP; SSE is no longer supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp))
2. **A concrete non-CES AI consumer** needs programmatic access to banking tools
3. **Spring AI MCP Server** reaches GA with production-grade Streamable HTTP transport
4. **Regulatory or enterprise standard** mandates MCP for AI-tool integration
5. **The team builds an internal AI agent** (e.g., for ops or compliance) that would benefit from MCP tool discovery

---

## Follow-ups

- [ ] Monitor Google CES 2026 roadmap for MCP/tool protocol announcements
- [ ] Track [Spring AI MCP Server](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot.html) maturity
- [ ] Evaluate Option C (Hybrid) if a non-CES AI consumer is identified
- [ ] Document the mechanical steps for `@RestController` → `@Tool` handler migration for future reference

---

## Notes

### MCP Protocol Reference
- Specification: https://modelcontextprotocol.io/specification
- Java SDK: https://modelcontextprotocol.io/sdk/java
- Spring AI integration: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot.html

### Current CES Toolset Binding Pattern
The CES agent discovers tools via:
1. `toolsets/location/location.json` — declares the toolset type (`openApiToolset`) and auth config
2. `toolsets/location/open_api_toolset/open_api_schema.yaml` — full OpenAPI 3.0 spec with paths, parameters, and schemas
3. `app.json` → `variableDeclarations` → `backend` — injects the runtime base URL via `$env_var`

This is a **compile-time** binding (deployed with the agent package), unlike MCP's **runtime** discovery (`tools/list` at connection time).

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-08 |  | Initial proposal — REST vs MCP analysis |
| 2026-02-09 |  | Corrected CES MCP support status: CES supports `mcpTool` natively ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)). Updated rationale to reflect `x-ces-session-context` gap as the primary blocker, not platform support. Added cx-agent-studio evidence links. |
| 2026-02-12 | Copilot | **Transport reversal:** CES official docs now state *"CX Agent Studio only supports StreamableHttpTransport-based servers; SSE transport servers are not supported"* ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp), updated 2026-02-12). Updated all SSE transport references to Streamable HTTP. Decision trigger #1 partially met (Streamable HTTP now required; `x-ces-session-context` still absent). |
| 2026-02-12 | Codex | Consistency pass: aligned residual wording to Streamable HTTP requirement and removed stale SSE-oriented phrasing in alternatives, migration mapping, and reassessment triggers. |
