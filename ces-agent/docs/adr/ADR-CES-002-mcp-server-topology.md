# ADR-CES-002: MCP vs OpenAPI Toolsets for CES Agent ↔ BFA Integration

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-08  
**Owner:** Architecture Guild / CES Integration Team  
**Decision Drivers:** CES platform capabilities, security (session context injection), Streamable HTTP operational maturity, observability, scaling on Cloud Run, service-layer reuse  
**Related:**
- [ADR-CES-001: REST vs MCP](ADR-CES-001-rest-api-vs-mcp-server.md) — parent decision retaining REST as primary protocol
- [CES MCP tool documentation](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)
- [CES OpenAPI tool documentation](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)
- [bfa-service-resource](../../java/bfa-service-resource/) — current REST API (Cloud Run)
- [CES Agent toolsets](../acme_voice_agent/toolsets/) — current OpenAPI-based tool binding
- [CX Agent Studio Platform Reference](cx-agent-studio/platform-reference.md) — tool types, auth, MCP support (§ 5)
- [CX Agent Studio Design Patterns](cx-agent-studio/design-patterns.md) — `x-ces-session-context` (§ 5.1)
- [CX Agent Studio Banking Use Case Mapping](cx-agent-studio/banking-use-case-mapping.md) — consent/legitimation patterns
- [mcp-vs-openapi analysis](mcp-vs-openapi.md) — transport analysis for Google Cloud

---

## Context

### CES Tool Integration Capabilities

CX Agent Studio (CES) supports **four tool types** as first-class citizens ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)):

| Tool Type | Binding | Use Case |
|-----------|---------|----------|
| `pythonFunction` | Local Python code | Deterministic actions, wrappers, callbacks |
| `openApiToolset` | OpenAPI 3.0 schema + HTTP | External REST API integration (current approach) |
| `googleSearchTool` | Google Search | Grounded web lookup |
| **`mcpTool`** | **MCP server (Streamable HTTP)** | **Dynamic tool discovery from MCP servers** |

CES MCP support includes:
- **Authentication:** Same model as OpenAPI tools (`serviceAgentIdTokenAuthConfig` — Google service account identity tokens) ([platform-reference.md § 5](cx-agent-studio/platform-reference.md))
- **Transport requirement (updated 2026-02-12):** CES requires **Streamable HTTP transport** for MCP servers — SSE transport is not supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp))
- **Session context gap:** OpenAPI tools support [`x-ces-session-context`](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context) annotations for deterministic, model-invisible session context injection. **MCP tools have no equivalent mechanism.** This is the critical differentiator for banking operations.

This means the CES agent (`acme_voice_agent`) **can** consume an MCP server directly as a toolset, making CES itself a concrete MCP consumer.

### The Real Question

Given that CES supports both OpenAPI toolsets and MCP tools, which protocol should `acme_voice_agent` use to connect to `bfa-service-resource` banking operations?

### Current Architecture

```
┌──────────────────┐     OpenAPI toolset      ┌───────────────────────┐
│  CES Agent       │ ──── (HTTP/REST) ──────► │  bfa-service-resource │
│  acme_voice_agent│                           │  (Spring Boot REST)   │
│                  │  location.json            │  Cloud Run            │
│  toolsets/       │  open_api_schema.yaml     │                       │
│  └─ location/    │                           │  /api/v1/branches     │
│                  │  serviceAgentIdToken      │  /api/v1/accounts     │
│                  │  Auth                     │  /api/v1/cards        │
└──────────────────┘                           └───────────────────────┘
```

---

## Diverging Views Analysis

To avoid confirmation bias, this section examines the MCP vs OpenAPI decision from five distinct perspectives, then synthesizes where they converge and diverge.

### View 1: Platform / SRE Engineer — *"Will it page me at 2am?"*

**Streamable HTTP on Cloud Run — operational reality:**
- Cloud Run supports standard HTTP request/response for Streamable HTTP MCP endpoints ([Google Cloud blog](https://cloud.google.com/blog/products/serverless/cloud-run-now-supports-http-grpc-server-streaming))
- But Streamable HTTP follows normal HTTP request/response semantics across load balancers and proxies
- Streamable HTTP avoids long-lived SSE connection buffering and reconnect failure modes ([Google Dev forums](https://discuss.google.dev/t/sse-on-cloud-run-is-it-working-for-anyone/151803))
- No SSE-specific keepalive or reconnect tuning required; standard Cloud Run HTTP settings are sufficient

**OpenAPI (HTTP request/response):**
- Well-understood request lifecycle on Cloud Run
- No long-lived connection concerns
- Standard HTTP monitoring, alerting, and debugging

**Observability comparison:**

| Dimension | OpenAPI (REST) | MCP (Streamable HTTP) |
|-----------|---------------|-----------|
| Request tracing | Standard HTTP spans | Standard HTTP spans; MCP payload-level metrics can be added if needed |
| Error rates | HTTP status codes (4xx/5xx) | JSON-RPC error codes wrapped in 200 OK |
| Latency metrics | Per-request duration | Connection setup + message round-trip (mixed) |
| Cloud Run metrics | Native request count, latency, error rate | Connection count visible, but message-level metrics need custom instrumentation |
| Audit logging | CES `ExecuteTool` audit log entries | Same CES audit logging (tool type agnostic) |

**Verdict:** OpenAPI is operationally simpler. MCP over Streamable HTTP is viable and operationally closer to REST.

---

### View 2: Agent Developer — *"Can I ship this by Friday?"*

**OpenAPI toolset (current):**
- Mature CI/CD: `open_api_schema.yaml` exported by `OpenApiSpecExportTest` every build
- Deploy script (`deploy-agent.sh`) packages toolset alongside agent
- `x-ces-session-context` injects session/customer IDs without model prediction
- Evaluations (`branch_search_munich`, `german_branch_search`) already test the OpenAPI path
- Schema is the single source of truth for both Swagger docs and CES tool generation

**MCP toolset (new):**
- Requires building an MCP server (new artifact)
- CES MCP transport requirement: Streamable HTTP is required; SSE is unsupported (since 2026-02-12)
- Tool definitions via `@McpTool` annotations instead of OpenAPI spec
- No `x-ces-session-context` equivalent — session context propagation is undefined
- Evaluation infrastructure needs adaptation for MCP tool invocations
- Deploy script needs a new toolset binding (`mcpTool` JSON instead of `openApiToolset`)

**DX comparison:**

| Workflow Step | OpenAPI | MCP |
|---------------|---------|-----|
| Define tools | OpenAPI spec (auto-generated from `@Operation`) | `@McpTool` annotations (manual) |
| CES binding | `toolsets/location/location.json` → `open_api_schema.yaml` | New `mcpTool` JSON config pointing to Streamable HTTP endpoint |
| Auth config | `serviceAgentIdTokenAuthConfig` ✅ | `serviceAgentIdTokenAuthConfig` ✅ (same) |
| Session context injection | `x-ces-session-context` ✅ | ❌ **No equivalent** |
| Local testing | `curl` / Swagger UI / Postman | MCP Inspector / custom client |
| CI export | `OpenApiSpecExportTest` auto-exports spec | No equivalent auto-export |

**Verdict:** OpenAPI has a significantly more mature developer experience. MCP tool definitions are straightforward but the supporting toolchain (testing, CI, context injection) is immature.

---

### View 3: Banking Security Architect — *"Can I prove compliance?"*

**The `x-ces-session-context` gap is the critical finding.**

OpenAPI tools in CES support [`x-ces-session-context`](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context) annotations that:

1. **Inject session context** (session ID, project ID, customer ID) into API parameters **without model prediction**
2. Are **invisible to the model** — the schema is not shared, so the model cannot hallucinate these values
3. Enable **deterministic audit correlation** — every API call carries the correct session ID for tracing

This is essential for banking:

```yaml
# OpenAPI — session_id injected by CES runtime, invisible to model
parameters:
  - name: session_id
    in: path
    x-ces-session-context: $context.session_id    # ← CES runtime injects this
  - name: customer_id
    in: query
    x-ces-session-context: $context.variables.customer_id
```

**MCP has no equivalent mechanism.** For an MCP tool call, the model must predict all parameters. If a banking tool needs `session_id` or `customer_id`, the model must include them in the tool call arguments — which means:
- The model can hallucinate these values
- The values are visible in the model's context (token cost + potential PII exposure)
- Audit correlation depends on model accuracy, not runtime guarantee

**Workaround:** The MCP server could extract identity from the bearer token or HTTP headers instead of tool parameters. But this is a backend implementation pattern, not a CES-guaranteed contract. CES MCP auth documentation does not specify header propagation behaviour for session context.

**Auth model comparison:**

| Concern | OpenAPI | MCP |
|---------|---------|-----|
| Service auth (identity tokens) | ✅ `serviceAgentIdTokenAuthConfig` | ✅ Same model |
| Session context injection | ✅ `x-ces-session-context` (runtime, invisible) | ❌ Not available |
| Audit correlation guarantee | ✅ Deterministic (runtime-injected) | ⚠️ Depends on model or backend workaround |
| PII in model context | Minimized (context params invisible) | Higher risk (params in model context) |
| Consent/legitimation gating | ✅ Via interceptors + correlation ID | ✅ Same (if backend extracts from token) |

**Verdict:** For banking operations requiring audit correlation and session context (accounts, cards, transfers), the `x-ces-session-context` gap makes MCP unsuitable today. For public data (branch locations), MCP is acceptable since no session context injection is needed.

---

### View 4: Pragmatist — *"What do we gain that we don't already have?"*

**What MCP adds over OpenAPI:**

| MCP Primitive | What It Does | Value for Voice Banking |
|---------------|-------------|------------------------|
| `tools/list` | Dynamic tool discovery at runtime | Low — our tools are static and known at deploy time |
| Resources | Browsable read-only data | Medium — could expose account schemas, branch directory |
| Prompts | Reusable prompt templates | Low — prompts are managed in CES instruction files |
| Notifications | Server→client change alerts | Low — no real-time data change use case |

**What we lose by switching:**
- `x-ces-session-context` (see View 3)
- Mature CI/CD pipeline and toolset export automation
- Existing evaluation coverage
- Familiar debugging workflow (curl, Swagger UI)

**The location services case:**
Branch search is public data. No session context needed. No legitimation required (`@RequiresConsent(AI_INTERACTION)` is the lightest check, and CES auth headers satisfy it). This makes location services the **lowest-risk experiment  candidate** for MCP — but also the use case that benefits least from MCP's dynamic discovery, since branch search tools are static.

**Verdict:** MCP's distinctive capabilities (dynamic discovery, Resources, Prompts) don't solve problems we currently have. The migration cost is non-trivial, and we'd lose `x-ces-session-context` for banking operations.

---

### View 5: Technology Strategist — *"Where is this going in 12 months?"*

**Signals favouring MCP adoption:**
- Google CES adding `mcpTool` as a first-class type signals directional commitment
- MCP ecosystem growing rapidly (Claude, Copilot, Cursor, ADK, LangChain all support it)
- Java MCP SDK at v0.17.x with active development (3.2k GitHub stars, Spring AI integration)
- Industry consolidating on MCP as the standard AI ↔ tool protocol

**Signals favouring caution:**
- CES MCP support is new — transport changed from SSE to Streamable HTTP (2026-02-12)
- `x-ces-session-context` has no MCP roadmap equivalent — functional gap
- Spring AI MCP Server is pre-1.0
- Our service layer is transport-agnostic → migration cost is bounded and mechanical when needed
- The ChatGPT analysis in [mcp-vs-openapi.md](mcp-vs-openapi.md) recommends: *"Prototype tools as stdio MCP servers (Python) for speed. When a tool becomes 'real', repackage as HTTP/Streamable HTTP MCP server on Cloud Run."*

**The "both protocols" path:**
The service layer (`LocationService`, `AccountService`, etc.) is cleanly separated from controllers. Adding MCP is additive — we don't have to choose one or the other. The question is timing, not direction.

**Verdict:** MCP adoption is likely inevitable. But the CES-specific gaps (`x-ces-session-context`, legacy SSE assumptions) mean the switch should be pull-driven (by a concrete need) rather than push-driven (by protocol enthusiasm).

---

### Synthesis: Where the Views Converge and Diverge

**All five views agree:**
1. CES supports MCP via `mcpTool` with the same auth model as OpenAPI toolsets ([platform-reference.md § 5](cx-agent-studio/platform-reference.md))
2. The `x-ces-session-context` gap is a real blocker for banking operations ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context))
3. Streamable HTTP on Cloud Run uses standard request/response semantics and is operationally closer to REST
4. Location services (public data) is the lowest-risk MCP candidate
5. The service layer is transport-agnostic — migration is mechanical when justified
6. MCP adoption direction is clear; timing is the question

**The views diverge on:**
- **SRE vs Strategist:** SRE focuses on operational simplicity; Strategist focuses on MCP capability development
- **Security vs Developer:** Security sees `x-ces-session-context` as a hard blocker; Developer sees MCP tooling as eventually simpler
- **Pragmatist vs Strategist:** Pragmatist asks "what problem does this solve today?"; Strategist asks "are we ready when the market demands it?"

---

## Decision

**Retain OpenAPI toolsets as the primary CES integration protocol.** MCP is deferred for CES agent toolset binding but approved for a scoped spike (location services only) on a feature branch.

### Rationale

1. **`x-ces-session-context` gap** — Banking operations (accounts, cards, transfers) require deterministic session context injection that MCP cannot provide today. Parameters annotated with `x-ces-session-context` are invisible to the model, preventing hallucination of session IDs ([design-patterns.md § 5.1](cx-agent-studio/design-patterns.md#51-pass-context-variables-to-openapi-tools-x-ces-session-context)). This is a functional blocker, not a preference.

2. ~~**Streamable HTTP operational maturity**~~ **Update (2026-02-12):** CES now requires Streamable HTTP, which uses standard request/response semantics. The original SSE-specific operational concerns (timeouts, buffering, reconnect) no longer apply. This significantly reduces the operational complexity argument against MCP.

3. ~~**CES MCP transport limitation**~~ **Update (2026-02-12):** CES now requires **Streamable HTTP transport** — SSE is no longer supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)). Spring AI's `STREAMABLE` protocol is now the correct configuration for CES.

4. **No functional gap in OpenAPI** — Our current OpenAPI toolset covers all location service operations. MCP's dynamic discovery adds no value for a static tool set deployed alongside the agent.

5. **Migration cost is bounded** — The service layer is transport-agnostic. When CES MCP support matures (especially `x-ces-session-context` equivalent and Streamable HTTP), adding `@McpTool` wrappers around existing services is a <1 day effort per domain.

### Approved Spike (Feature Branch Only)

A **location-services-only MCP spike** is approved on a feature branch to:
- Validate Streamable HTTP transport from CES to Cloud Run end-to-end
- Measure latency overhead vs REST (same `LocationService`, different transport)
- Test CES `mcpTool` binding configuration and auth
- Document CES MCP toolset JSON format and deployment workflow
- Inform future migration timing

**Spike scope:** Location services only (branch search, branch details). No banking operations. Feature branch — do not merge to main.

---

## MCP Server Topology (When Implemented)

### Recommended: In-Process (MCP embedded in `bfa-service-resource`)

When MCP is adopted, the MCP server should be **embedded in `bfa-service-resource`** alongside REST, not deployed as a separate service.

```
┌──────────────────────────────────────────────────────┐
│  bfa-service-resource (single Spring Boot process)   │
│  Cloud Run                                           │
│                                                      │
│  REST endpoints          MCP endpoints (Streamable HTTP)         │
│  /api/v1/branches        /mcp (tools/list,           │
│  /api/v1/accounts         tools/call)                │
│  /api/v1/cards                                       │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │  Service Layer (shared, transport-agnostic)  │    │
│  │  LocationService, AccountService, CardService│    │
│  └──────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────┘
        ↑                          ↑
   CES Agent                  CES Agent (or other MCP client)
   (openApiToolset)           (mcpTool)
```

**Why in-process over sidecar:**

| Dimension | In-Process | Separate Sidecar |
|-----------|-----------|-----------------|
| Service-layer reuse | Direct injection | HTTP delegation or library dependency |
| Deployment | Same artifact, same Cloud Run | New artifact, new service, new health check |
| Auth/TLS | Unified | Must configure separately |
| Monitoring | Single service metrics | Two services to monitor |
| Scaling | Single autoscaler | Coordinated scaling required |
| Rollback | Remove dependency + beans | Delete service + infra |

**CES transport requirement (updated 2026-02-12):** CES requires Streamable HTTP, not SSE. The Spring AI configuration must use:

```properties
# CES-compatible MCP transport (Streamable HTTP, as required since 2026-02-12)
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.type=SYNC
```

> **Note:** The previous SSE-specific Cloud Run hardening (keepalive pings, 300s timeouts, reconnect handling) is no longer required. Streamable HTTP uses standard request/response semantics compatible with default Cloud Run settings.

---

## Alternatives Considered (Topology)

### Option B: Shared-Library Module (`bfa-mcp-server`)

Separate Maven module importing `bfa-service-resource` as a library.

- Pros: Clean isolation from REST; can use stdio for local dev
- Cons: POM surgery (`<classifier>exec</classifier>` on repackage); two deployment artifacts; auto-config leakage; double operational cost

### Option C: HTTP Sidecar

Separate process translating MCP → REST HTTP calls.

- Pros: Zero changes to `bfa-service-resource`
- Cons: Extra network hop; duplicated parameter parsing; two processes required; highest implementation cost

Both alternatives rejected — the in-process approach is simpler, cheaper, and validates the service-layer reuse claim.

---

## Consequences

### Positive
- Banking operations retain `x-ces-session-context` guarantees (deterministic audit correlation, PII minimization)
- No SSE operational complexity added to production
- Existing CI/CD, evaluations, and deploy scripts unchanged
- Clear migration playbook documented for when MCP matures
- Location services spike provides hands-on CES MCP experience without production risk

### Negative / Trade-offs
- Not building full MCP capability now — may slow adoption if CES MCP matures faster than expected
- Location services spike is investment without immediate production value
- Team does not gain deep Streamable HTTP MCP operational experience until spike runs

### Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| CES deprecates OpenAPI toolsets in favour of MCP | Service layer is transport-agnostic; `@McpTool` migration is mechanical (<1 day per domain). Monitor CES deprecation notices. |
| `x-ces-session-context` equivalent added to MCP but with different semantics | Spike validates MCP path; banking operations migrate only after context injection is verified end-to-end. |
| Streamable HTTP on Cloud Run causes unexpected issues | Only expose MCP in spike branch; production retains REST-only. Streamable HTTP uses standard request/response — lower risk than SSE. |
| Spring AI MCP API breaks before 1.0 | Spike is throwaway code on a branch. Production migration waits for stable API. |
| Another team or AI consumer needs MCP access before CES matures | In-process topology supports multiple transports simultaneously — add Streamable HTTP endpoint without removing REST. |

---

## Decision Triggers for Full MCP Adoption

Move from "spike" to "production" when **all** of the following are true:

1. **CES MCP supports session context injection** (equivalent to `x-ces-session-context`)
2. ~~**CES MCP supports Streamable HTTP**~~ ✅ **MET (2026-02-12):** CES now requires Streamable HTTP; SSE is no longer supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp))
3. **Spring AI MCP Server reaches 1.0** (stable API contract)
4. **Spike demonstrates** acceptable latency, reliability, and observability parity with REST

Move to **hybrid** (both OpenAPI and MCP active) when criteria 1, 3, and 4 are met. Criterion 2 is already satisfied. In hybrid mode: banking operations use OpenAPI (session context); location services use MCP.

---

## Relationship to ADR-CES-001

| ADR-CES-001 Statement | Status in This ADR |
|------------------------|-----------------------|
| *"OpenAPI toolsets are the mature, production-tested CES integration path"* | **Confirmed.** This ADR's five-view analysis independently arrives at the same conclusion, with `x-ces-session-context` identified as the decisive differentiator. |
| *"Option C only justified if non-CES AI consumers exist"* | **Refined.** CES itself can consume MCP via `mcpTool` ([platform-reference.md § 5](cx-agent-studio/platform-reference.md)). But the `x-ces-session-context` gap means OpenAPI remains superior for banking operations. |
| *"The REST API serves multiple consumers"* | **Still true.** REST serves CES (OpenAPI toolset), web/mobile, and integration tests. MCP would serve CES (`mcpTool`) and potentially other AI hosts. |
| *"Service layer is transport-agnostic"* | **Confirmed.** `LocationService` can be wrapped with `@Tool` annotations with zero business logic changes. |

---

## Notes

### CES MCP Toolset Binding Format (for spike reference)

Expected `mcpTool` JSON structure (analogous to current `location.json`):

```json
{
  "displayName": "location_mcp",
  "mcpTool": {
    "serverUrl": "https://bfa-service-resource-XXXXX.us-central1.run.app/mcp",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

Compared to current OpenAPI binding:

```json
{
  "displayName": "location",
  "openApiToolset": {
    "openApiSchema": "toolsets/location/open_api_toolset/open_api_schema.yaml",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

### Transport Compatibility Matrix

| Transport | CES Compatible? | Coexists with REST? | Use Case |
|-----------|----------------|-------------------|----------|
| **STDIO** | ❌ (managed runtime) | ❌ | Local dev only |
| **SSE** | ❌ (not supported since 2026-02-12) | ✅ (same Tomcat) | Non-CES MCP clients only |
| **Streamable HTTP** | ✅ (CES `mcpTool`, required) | ✅ (same Tomcat) | CES agent ↔ Cloud Run MCP server |
| **Stateless** | ❌ (CES limitation) | ✅ (same Tomcat) | Cloud-native (future) |

### Spring AI MCP Server Stack (for spike reference)

| Component | Version (as of 2026-02) | Maven Coordinates |
|-----------|------------------------|-------------------|
| Spring AI BOM | 1.0.0-M6 (pre-release) | `org.springframework.ai:spring-ai-bom` |
| MCP Server WebMVC Starter | via BOM | `org.springframework.ai:spring-ai-starter-mcp-server-webmvc` |
| Java MCP SDK | 0.17.2 | `io.modelcontextprotocol.sdk:mcp` |

---

## References

- [CES MCP tool documentation](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)
- [CES OpenAPI tool documentation](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)
- [Host MCP servers on Cloud Run](https://docs.cloud.google.com/run/docs/host-mcp-servers)
- [Cloud Run MCP hosting (HTTP)](https://cloud.google.com/blog/products/serverless/cloud-run-now-supports-http-grpc-server-streaming)
- [Apigee HTTP proxying for MCP endpoints](https://docs.cloud.google.com/apigee/docs/api-platform/develop/server-sent-events)
- [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) (v0.17.2)
- [Spring AI MCP Server Boot Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)
- [CX Agent Studio Platform Reference](cx-agent-studio/platform-reference.md) — tool types, auth model, MCP transport limitations (§ 5)
- [CX Agent Studio Design Patterns](cx-agent-studio/design-patterns.md) — `x-ces-session-context` (§ 5.1), callback patterns
- [CX Agent Studio Banking Use Case Mapping](cx-agent-studio/banking-use-case-mapping.md) — consent/legitimation CES patterns

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-08 |  | Initial proposal — topology analysis, decision to defer |
| 2026-02-08 |  | v2: Diverging views analysis; `x-ces-session-context` gap identified as primary blocker; spike approved |
| 2026-02-09 |  | v3: Removed legacy "corrected premise" framing. CES MCP support is an established fact — the decision now rests on the `x-ces-session-context` functional gap and transport requirement shift to Streamable HTTP. Added cx-agent-studio evidence links throughout. |
| 2026-02-12 | Copilot | **Transport reversal:** CES official docs now state *"CX Agent Studio only supports StreamableHttpTransport-based servers; SSE transport servers are not supported"* ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp), updated 2026-02-12). Reversed all SSE↔Streamable HTTP references. Updated Spring AI config to `STREAMABLE`. Decision trigger #2 marked as MET. Transport compatibility matrix reversed. SSE Cloud Run hardening section replaced with Streamable HTTP note. Spike scope updated. |
| 2026-02-12 | Codex | Consistency pass: removed remaining stale SSE-era wording and synchronized terminology to CES-required Streamable HTTP across views, topology notes, and risk language. |
