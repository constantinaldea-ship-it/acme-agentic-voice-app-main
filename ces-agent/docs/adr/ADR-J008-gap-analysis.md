# ADR-J008 Gap Analysis: Backend For Agents Pattern vs CES Integration Requirements

**Date:** 2026-02-10  
**Author:** Architecture Review (Copilot)  
**Subject:** [ADR-J008: Backend For Agents (BFA) Pattern Analysis](../../../docs/adr/ADR-J008-backend-for-agents-analysis.md)  
**Status:** ✅ All Gaps Closed (ADR-J008 v1.1, 2026-02-10)  
**Cross-References:**
- [ADR-CES-001: REST API vs MCP Server](ADR-CES-001-rest-api-vs-mcp-server.md)
- [ADR-CES-002: MCP vs OpenAPI Toolsets](ADR-CES-002-mcp-server-topology.md)
- [ADR-CES-003: MCP Location Services Implementation](ADR-CES-003-mcp-location-services-implementation.md)
- [ADR-CES-004 Connectivity Pattern Selection](ADR-CES-004-connectivity-pattern-selection.md)
- [ADR-CES-004 Backend Language Choice](ADR-CES-004-backend-language-choice.md)
- [CX Agent Studio Platform Reference](../cx-agent-studio/platform-reference.md)
- [CES Tool Selection Guide](../tool-selection-guide.md)
- [Banking Use Case Mapping](../cx-agent-studio/banking-use-case-mapping.md)
- [Connectivity Pattern Analysis Prompt](../../../agents/sse_vs_streamable_http.md)

---

## Executive Summary

ADR-J008 is a thorough, well-structured analysis that **correctly identifies the Hybrid Selective BFA (Option D)** as the optimal pattern for cross-NARS boundary enforcement in an enterprise banking context. Its six-perspective evaluation framework (Developer, Security, Performance, Operations, Cost, Governance) is methodologically sound, and the recommendation to centralize AcmeLegi legitimation, consent verification, and audit correlation at the NARS boundary is architecturally defensible.

ADR-J008 was originally authored **before the CES-specific ADR series (CES-001 through CES-004)** and the CES platform documentation were finalized. This gap analysis (2026-02-10) identified 5 P0/P1 critical gaps, 8 P2 medium gaps, 12 recommendations, and 4 cross-ADR consistency issues. **ADR-J008 was subsequently revised to v1.1 (2026-02-10, by Codex)**, which incorporated all findings from this analysis. All gaps are now closed.

**Overall Assessment (Post-Revision):**
- **Section 1 (Alignment):** ✅ 100% — ADR-J008 v1.1 fully aligned with CES topology
- **Section 2 (Critical Gaps):** ✅ 5/5 P0/P1 gaps **CLOSED** in ADR-J008 v1.1
- **Section 3 (Medium Gaps):** ✅ 8/8 P2 gaps **CLOSED** in ADR-J008 v1.1
- **Section 4 (Recommendations):** ✅ 12/12 recommendations **INCORPORATED** in ADR-J008 v1.1
- **Section 5 (Cross-ADR Conflicts):** ✅ 4/4 consistency issues **RESOLVED** in ADR-J008 v1.1

---

## Section 1: Summary of Alignment (What ADR-J008 Got Right)

### 1.1 Correct Architectural Decisions

| Decision | Validation | Supporting Evidence |
|----------|-----------|---------------------|
| **Hybrid Selective BFA (Option D)** | ✅ Correct choice | ADR-CES-001 confirms REST+OpenAPI as primary CES integration path; ADR-CES-004 Connectivity confirms REST for sensitive banking flows. BFA-at-boundary is consistent with both. |
| **Central AcmeLegi at NARS boundary** | ✅ Critical and well-justified | banking-use-case-mapping.md § 7 independently arrives at the same pattern (OpenAPI toolset + `before_tool_callback` gating). |
| **Direct calls for internal services** | ✅ Appropriate separation | ADR-CES-001 retains REST for all consumers; LocationServices correctly identified as "no BFA needed" — consistent with ADR-CES-002 allowing MCP Streamable HTTP for public data. |
| **Session-level legitimation caching** | ✅ Sound performance optimization | ADR-J008 §3.2 proposes reducing AcmeLegi calls by 80%+ via caching. Consistent with 3-second voice banking latency budget. |
| **Two-stage PII redaction** | ✅ Well-designed | Request logging redaction + full data pass-through to agent. Consistent with GDPR data minimization principles. |
| **Fail-closed authorization** | ✅ Correct for banking | §7.3 implementation guideline "If BFA cannot verify authorization, deny the request" aligns with regulatory expectations. |
| **Custom Spring Service for BFA** | ✅ Consistent with language choice | ADR-CES-004 (Backend Language Choice) confirms Java-first; Appendix A's recommendation of Custom Spring Service over Gateway/Envoy aligns. |

### 1.2 Strong Analytical Framework

- **Six-perspective analysis** (Developer, Security, Performance, Operations, Cost, Governance) provides comprehensive coverage that exceeds most ADR analytical depth.
- **Latency budget breakdown** with component-level targets (§2.3) is specific and measurable.
- **Decision matrix** with weighted scoring (§4) provides auditable rationale.
- **Code comparison** (Appendix B) makes the behavioral difference between current and BFA patterns tangible.

### 1.3 Alignment with AGENTS.md Requirements

| AGENTS.md Requirement | ADR-J008 Coverage |
|----------------------|-------------------|
| §8: Voice latency <3s | ✅ Addressed (§2.3 latency budget: 2475-2535ms) |
| §9: Account balance, transfers, payments | ✅ Covered via BankingOperationsAgent (§5.3) |
| §10: Consent, legitimation, audit | ✅ Core focus of BFA design (§3.1-3.4) |
| §11: Beads issue tracking | ⚠️ Not mentioned — no Beads epic/tasks created |

---

## Section 2: Critical Gaps (P0/P1) — ✅ ALL CLOSED in ADR-J008 v1.1

> **All 5 critical gaps below were resolved by ADR-J008 Revision 1.1 (2026-02-10).** Retained for traceability.

### GAP-P0-1: No CES Integration Architecture Acknowledged ✅ CLOSED

**Severity:** P0 — Fundamental architectural blind spot  
**Resolved in:** ADR-J008 v1.1 §1.3 (CES Deployment Topology) + §1.4 (Scope Clarification)  
**Description:** ADR-J008 describes the BFA pattern exclusively in terms of internal Spring Boot `OrchestratorService → AgentRegistry → Agent → Service` communication (§1.1). The actual production deployment uses **CES (Dialogflow CX)** as the orchestration layer, with agents running inside the CES platform and tools invoked via **OpenAPI toolsets** (not direct Spring DI injection).

The BFA layer, as described in ADR-J008, would sit between an agent and a backend service. In the CES topology, the equivalent is:

```
CES Agent → [OpenAPI Toolset] → [bfa-service-resource (Cloud Run)] → [GLUE] → SOR
```

ADR-J008's `BfaAdapter` interface (§5.2) assumes in-process Java injection (`bfaAdapter.callSor(...)`) which is not available inside CES. The BFA behavior must be implemented either:
- **Inside `bfa-service-resource`** as interceptors/filters on the REST endpoints consumed by CES OpenAPI toolsets, OR
- **As CES `before_tool_callback` / `after_tool_callback` Python hooks** calling the BFA service via a secondary OpenAPI toolset

Neither pattern is discussed in ADR-J008.

**Evidence:**
- ADR-CES-001 §Current State: "CES agents reference endpoints via OpenAPI toolsets"
- platform-reference.md §5: Tool types are OpenAPI, Python, MCP — no in-process Java
- banking-use-case-mapping.md §6-7: Consent/legitimation via CES `before_tool_callback`

**Required Action:** Add a "CES Integration Architecture" section that maps the BFA pattern to the CES deployment topology. Define whether BFA logic is in the backend service (preferred, per ADR-CES-004 Language Choice) or in CES callback hooks, or a combination.

---

### GAP-P0-2: Missing `x-ces-session-context` Integration Design ✅ CLOSED

**Severity:** P0 — Security and compliance gap  
**Resolved in:** ADR-J008 v1.1 §3.2 (`x-ces-session-context` OpenAPI Pattern with YAML example)  
**Description:** ADR-J008's BFA relies on `sessionId` and `userId` being available in the `BfaRequest` (§5.2). In the CES integration path, these values are propagated via the **`x-ces-session-context`** annotation mechanism on OpenAPI parameters — a CES-specific feature that injects session variables into API calls without model prediction and invisible to the LLM.

ADR-J008 never mentions `x-ces-session-context`. This is critical because:
1. **Session identity correlation** (§3.2 AcmeLegi, §3.3 Consent, §8 Audit) depends on the session/user ID being available in the BFA layer.
2. Without `x-ces-session-context`, the model would need to predict session IDs — creating **PII exposure in model context** and **hallucination risk** for audit-critical identifiers.
3. ADR-CES-001 and ADR-CES-002 both identify this as the **decisive differentiator** between OpenAPI and MCP for banking operations.

**Evidence:**
- ADR-CES-002 §View 3: Detailed security analysis of `x-ces-session-context`
- design-patterns.md §5.1: YAML annotation pattern for context injection
- tool-selection-guide.md §4.5: Session context injection documentation

**Required Action:** Add a section documenting how `bfa-service-resource` REST endpoints use `x-ces-session-context` annotations to receive `session_id`, `customer_id`, and `correlation_id` from CES. Show the OpenAPI YAML annotation pattern and explain how these map to `BfaRequest` fields.

---

### GAP-P0-3: CES Evaluation Limitations Not Addressed in BFA Testing Strategy ✅ CLOSED

**Severity:** P1 — Testing strategy invalidation  
**Resolved in:** ADR-J008 v1.1 §7 (Testing and Evaluation Strategy) — includes split-layer verification for L-01/L-11 + 6 test layer definitions  
**Description:** ADR-J008 §5.4 proposes a phased implementation roadmap but includes only a `TODO` placeholder for testing strategy. The CES platform imposes critical evaluation limitations that fundamentally affect how BFA-mediated operations can be tested:

- **L-01:** OpenAPI toolset operations **cannot** be referenced in golden eval `toolCall` assertions (tool-selection-guide.md §4.1). BFA operations exposed via OpenAPI are therefore not directly assertable in CES golden evaluations.
- **L-11:** OpenAPI operations **cannot** be mocked in scenario evaluations (tool-selection-guide.md §4.2). BFA-mediated banking operations will always hit live backend in scenario evals.
- **Callback testing:** CES `before_tool_callback` for consent/legitimation gating can short-circuit tool calls, but this behavior interacts with evaluation expectations in undocumented ways.

This means the BFA's core value propositions (legitimation, consent, audit) **cannot be verified through CES evaluations alone** and require a dedicated backend integration testing strategy.

**Evidence:**
- tool-selection-guide.md §4.1 (L-01), §4.2 (L-11), §5.1 Testing Strategy Matrix
- ADR-CES-004 Gap Analysis §Section 2, T-4

**Required Action:** Add a "Testing & Evaluation Strategy" section that:
1. Defines CES-level testing (limited to `agentResponse` behavioral verification)
2. Defines backend integration testing for BFA (legitimation flows, consent gating, audit trail validation, error handling)
3. Specifies mock strategies for AcmeLegi, GLUE, and SOR in integration tests
4. References the evaluation limitations and their workarounds

---

### GAP-P1-4: Header Propagation Pattern for Agent-to-Backend Correlation ✅ CLOSED

**Severity:** P1 — Audit correlation gap  
**Resolved in:** ADR-J008 v1.1 §3.3 (Header Propagation Contract — 6 headers defined with source/propagation/purpose)  
**Description:** ADR-J008 §3.2 proposes "audit correlation" as a BFA responsibility and §8 states "All BFA calls logged with correlation ID, user, account, action." However, the ADR never specifies the **header propagation pattern** for maintaining correlation across the CES → `bfa-service-resource` → GLUE → SOR chain.

In enterprise observability for CES, the following headers should be defined:
- `X-Correlation-Id` / `X-Request-Id`: End-to-end trace identifier
- `X-Agent-Id`: Which CES agent initiated the tool call
- `X-Tool-Id`: Which tool/operation was invoked
- `X-Session-Id`: CES session identifier (via `x-ces-session-context`)

CES OpenAPI toolsets support `x-ces-session-context` for automatic header injection, but ADR-J008 doesn't define which headers should exist or how they propagate through the BFA to downstream systems.

**Evidence:**
- platform-reference.md §8: Callback lifecycle includes `tool` and `input` parameters
- ADR-CES-002 §View 1: Observability comparison (HTTP spans vs JSON-RPC extraction)
- agents/sse_vs_streamable_http.md: Explicitly requests "agent-to-backend header propagation" analysis

**Required Action:** Define a header propagation contract specifying:
1. Required headers on all BFA-exposed endpoints
2. Which headers are injected by CES vs populated by the backend
3. How correlation IDs map to downstream GLUE/SOR calls
4. Structured log format including all correlation dimensions

---

### GAP-P1-5: CES `before_tool_callback` vs BFA-Internal Gating — Dual Enforcement Risk ✅ CLOSED

**Severity:** P1 — Architectural ambiguity creating compliance risk  
**Resolved in:** ADR-J008 v1.1 §3.4 (Dual Enforcement Model with authoritative gate table and interaction rules)  
**Description:** ADR-J008 proposes that the BFA performs consent verification (§3.3) and AcmeLegi legitimation (§3.2) as in-process Java logic. Separately, banking-use-case-mapping.md §6-7 documents consent and legitimation enforcement via CES `before_tool_callback` Python hooks.

This creates **dual enforcement ambiguity**:
- Should consent/legitimation be checked in CES callbacks (before the HTTP call), in the BFA backend (during the HTTP call), or both?
- If both, what happens when they disagree? Does CES callback denial take precedence?
- If only in BFA, the CES callback becomes a pass-through — wasting the CES hook capability.
- If only in CES, the BFA loses its core value proposition of centralized enforcement.

Platform-reference.md §8 confirms `before_tool_callback` applies to all tool types, making CES-side gating technically feasible. But the Python sandbox's network restrictions (tool-selection-guide.md §2.2) mean the callback cannot directly call AcmeLegi — it would need to call a separate BFA "legitimation check" OpenAPI toolset.

**Evidence:**
- banking-use-case-mapping.md §6 (consent via callback), §7 (AcmeLegi via callback)
- platform-reference.md §8 (callback contracts)
- tool-selection-guide.md §2.2 (Python sandbox network restriction)

**Required Action:** Define a clear enforcement architecture:
1. **Recommended pattern:** CES `before_tool_callback` performs lightweight session-state validation (consent token exists, not expired). The BFA backend performs authoritative consent/legitimation verification against the SOR. This provides defense-in-depth without full duplication.
2. Document the interaction between CES callbacks and BFA backend gates.
3. Define error responses that allow CES to present meaningful user-facing messages when BFA denial occurs.

---

## Section 3: Medium-Priority Gaps (P2) — ✅ ALL CLOSED in ADR-J008 v1.1

> **All 8 medium-priority gaps below were resolved by ADR-J008 Revision 1.1 (2026-02-10).** Retained for traceability.

### GAP-P2-1: No Connectivity Pattern Awareness ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §1.6 (Transport/Connectivity Policy)  
**Description:** ADR-J008 implicitly assumes HTTP REST as the only transport between agents and the BFA. ADR-CES-001 through CES-004 document three connectivity patterns (REST+OpenAPI, MCP Streamable HTTP, Streamable HTTP) with specific suitability criteria. The BFA design should acknowledge that:
- BFA endpoints exposed to CES **must** use REST+OpenAPI for banking operations (per ADR-CES-004 Connectivity)
- An MCP exposure of BFA for sensitive operations is explicitly out of scope for sensitive operations (lacks `x-ces-session-context`)
- Future MCP exposure might be considered only when CES adds context injection parity

**Required Action:** Add a "Transport and Connectivity" section referencing ADR-CES-004 decisions.

---

### GAP-P2-2: Missing OpenAPI Schema Design for BFA Endpoints ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §5.3 (CES Toolset Binding Pattern with JSON example)  
**Description:** ADR-J008 §5.2 defines `BfaAdapter` as a Java interface. For CES consumption, the BFA's public API must be defined as an **OpenAPI 3.0 schema** that CES can import as a toolset. The schema must include:
- `x-ces-session-context` annotations on session/correlation parameters
- `operationId` naming aligned with CES tool naming conventions
- Error response schemas that CES can interpret for user-facing error messages
- Request/response DTOs suitable for LLM tool-call reasoning

**Required Action:** Add an OpenAPI schema design section or reference a separate specification document.

---

### GAP-P2-3: Rollback/Failure Strategy Left as TODO ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §6.4 (Rollback / Failure Strategy with 4 failure modes, strategies, and user experience)  
**Description:** ADR-J008 §6.3 contains a placeholder `<!-- TODO: Document rollback strategy -->` with no content. For a production banking BFA, the failure strategy is critical:
- **BFA unavailable:** Fail-closed (block all cross-NARS calls) vs graceful degradation (bypass BFA for read-only queries)
- **AcmeLegi unavailable:** Block all legitimation-requiring operations vs serve from cache with degraded trust
- **Circuit breaker configuration:** Thresholds, half-open strategies, fallback endpoints
- **CES-perspective failure:** What error does the CES agent see when BFA returns 5xx? How does the agent respond to the user?

**Required Action:** Complete the rollback/failure strategy section with production-grade patterns.

---

### GAP-P2-4: Missing Observability Requirements (Left as TODO) ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §6.5 (Observability Requirements with 8 metrics, targets, and alert thresholds)  
**Description:** ADR-J008 §8 contains a `<!-- TODO: Add observability requirements -->` placeholder. For a BFA in production banking:
- **Latency histograms:** p50, p95, p99 for BFA calls (target: p95 < 50ms per ADR-J008 §9)
- **Error rate dashboards:** HTTP 4xx/5xx from BFA, AcmeLegi deny rate, consent failures
- **Cache metrics:** AcmeLegi cache hit rate (expected 80%+), cache TTL violations
- **Alert thresholds:** BFA latency p95 > 50ms, error rate > 1%, AcmeLegi unavailability > 30s
- **Cloud Run-specific:** Instance count, cold start frequency, request queue depth

ADR-CES-002 §View 1 provides an observability comparison for REST vs MCP that should inform BFA monitoring design.

**Required Action:** Complete the observability requirements section.

---

### GAP-P2-5: Session Cache Policy Undefined (Left as TODO) ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §6.6 (Session Cache Policy with TTL, scope, eviction, max entries, audit semantics, storage)  
**Description:** ADR-J008 §8 contains a `<!-- TODO: Define session cache policy -->` placeholder. The cache is a core performance optimization (§2.3: "reduce AcmeLegi calls by 80%+") but without a defined policy, it becomes a compliance risk:
- **TTL:** How long is a cached legitimation result valid? (Recommended: 5-10 minutes, configurable)
- **Eviction:** What triggers cache invalidation? (consent withdrawal, session end, explicit revocation)
- **Max size:** Per-session and global limits
- **Cache scope:** Is the cache per-account, per-operation, or per-session?
- **Audit implications:** Must the audit log distinguish "cached allow" from "fresh allow"?

**Required Action:** Define session cache policy with TTL, eviction, and audit differentiation.

---

### GAP-P2-6: Multi-Agent Handoff Context Not Addressed ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §3.5 (Cross-Agent State Management with session variable table and transfer rules)  
**Description:** ADR-J008 §3.5 covers human handover but doesn't address **multi-agent handoff** within the CES topology. CES uses explicit `{@AGENT: agent_name}` transfers (platform-reference.md §9) with before/after agent callbacks. When an agent hands off to another agent, the BFA context (legitimation status, consent scope, cached authorization) must be preserved or re-validated.

**Key questions unaddressed:**
- Does legitimation cache survive agent transfers within the same session?
- Does the `before_tool_callback` on the target agent have access to the source agent's BFA context?
- CES session variables (`variableDeclarations` in app.json) can persist across transfers — should BFA state be stored there?

**Evidence:** platform-reference.md §9 (agent-to-agent handoffs), banking-use-case-mapping.md §3 (multi-agent topology)

**Required Action:** Add a section on BFA state management across CES agent transfers.

---

### GAP-P2-7: No DORA/Resilience Requirements Specifics ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §3.1 (Regulatory Control Mapping with DORA RTO/RPO targets and ICT controls)  
**Description:** ADR-J008 §3.1 mentions DORA compliance and §2.2 marks it as ✅ for Hybrid BFA. However, DORA (Digital Operational Resilience Act) requires specific ICT risk management controls:
- **Recovery Time Objective (RTO)** for the BFA service
- **Recovery Point Objective (RPO)** for audit data
- **ICT incident classification** — how BFA failures are classified and reported
- **Third-party risk management** — BFA depends on Google Cloud Run, CES, and GLUE (WSO2)
- **Resilience testing** — Chaos engineering scenarios for BFA failure modes

**Required Action:** Add DORA-specific requirements or reference a separate resilience document.

---

### GAP-P2-8: EU AI Act Transparency Requirements Underspecified ✅ CLOSED

**Resolved in:** ADR-J008 v1.1 §3.1 (Regulatory Control Mapping with EU AI Act traceability and conformity controls)  
**Description:** ADR-J008 §2.2 marks EU AI Act transparency as ✅ "Orchestrator level" for Hybrid BFA but provides no implementation specifics. The EU AI Act requires:
- Users must be informed they are interacting with AI (already handled by CES voice prompt)
- AI system decisions must be explainable — BFA tool calls and legitimation decisions must be traceable to a specific AI reasoning step
- High-risk AI systems require conformity assessments — banking AI may qualify

**Required Action:** Add or reference EU AI Act transparency implementation details.

---

## Section 4: Recommendations — ✅ ALL INCORPORATED in ADR-J008 v1.1

> **All 12 recommendations below were incorporated into ADR-J008 Revision 1.1 (2026-02-10).** Retained for traceability.

### R-01: Add "CES Deployment Topology" Section (Critical — addresses GAP-P0-1)

Insert after §1.3 (Current Integration Architecture). Show the CES-specific deployment path:

```
┌────────────────────┐    OpenAPI Toolset    ┌──────────────────────────────────┐
│  CES Agent         │───[HTTP/REST]────────►│  bfa-service-resource             │
│  (acme_voice_agent)│                       │  (Spring Boot, Cloud Run)         │
│                    │  x-ces-session-context │                                  │
│  before_tool_cb:   │  → session_id         │  Servlet Filters:                │
│  • consent check   │  → customer_id        │  • BFA Adapter (AcmeLegi, consent)│
│  • session state   │  → correlation_id     │  • Audit Interceptor              │
│                    │                       │  • PII Redaction Filter           │
│  OpenAPI toolsets: │                       │                                  │
│  • banking         │                       │  → GLUE → Core Banking SOR       │
│  • location        │                       │  → AcmeLegi Legitimation         │
└────────────────────┘                       └──────────────────────────────────┘
```

### R-02: Add `x-ces-session-context` OpenAPI Pattern (Critical — addresses GAP-P0-2)

Add example OpenAPI annotation for BFA endpoints:

```yaml
paths:
  /api/v1/accounts/{accountId}/balance:
    get:
      operationId: getAccountBalance
      parameters:
        - name: X-Session-Id
          in: header
          x-ces-session-context: session_id        # Injected by CES, invisible to model
          schema: { type: string }
        - name: X-Customer-Id
          in: header
          x-ces-session-context: $context.variables.customer_id
          schema: { type: string }
        - name: X-Correlation-Id
          in: header
          x-ces-session-context: $context.variables.correlation_id
          schema: { type: string }
        - name: accountId
          in: path
          required: true
          schema: { type: string }
```

### R-03: Add Testing & Evaluation Strategy (Critical — addresses GAP-P0-3)

| Test Layer | Scope | Tool | Coverage |
|-----------|-------|------|----------|
| **CES golden eval** | Agent routing, response quality | CES evaluation DSL | `agentResponse` only (L-01 limitation) |
| **CES scenario eval** | Multi-turn conversations | CES scenario DSL | Live backend calls (L-11 limitation) |
| **Backend integration test** | BFA logic: legitimation, consent, audit | JUnit 5 + MockMvc + WireMock | Full: request/response, error handling, auth |
| **Contract test** | OpenAPI schema compliance | `OpenApiSpecExportTest` | Schema drift detection |
| **Audit trail test** | Log completeness, PII redaction, retention | Custom audit verifier | 10-year compliance patterns |
| **Performance test** | BFA latency p95 < 50ms | Gatling / k6 | Latency budget compliance |

### R-04: Define Header Propagation Contract (P1 — addresses GAP-P1-4)

| Header | Source | Propagation | Purpose |
|--------|--------|-------------|---------|
| `X-Session-Id` | CES via `x-ces-session-context` | BFA → GLUE → SOR | Session correlation |
| `X-Customer-Id` | CES via `x-ces-session-context` | BFA → AcmeLegi | Legitimation identity |
| `X-Correlation-Id` | CES via `x-ces-session-context` or BFA-generated | BFA → all downstream | End-to-end tracing |
| `X-Agent-Id` | BFA derived from CES callback metadata | BFA logs only | Agent attribution |
| `X-Tool-Id` | BFA derived from OpenAPI `operationId` | BFA logs only | Tool attribution |
| `Authorization` | CES `serviceAgentIdTokenAuthConfig` | BFA → token exchange → GLUE | Service-to-service auth |

### R-05: Define Dual Enforcement Pattern (P1 — addresses GAP-P1-5)

**Recommended two-layer enforcement:**

| Check | CES Layer (`before_tool_callback`) | BFA Backend Layer |
|-------|-----------------------------------|-------------------|
| Consent existence | ✅ Check `consent_state` session variable | ✅ Authoritative verification against consent SOR |
| Consent freshness | ✅ Check `consent_expires_at` variable | ✅ Re-verify if near expiry |
| AcmeLegi legitimation | ❌ Cannot call AcmeLegi (sandbox restriction) | ✅ Full AcmeLegi check (with cache) |
| PII redaction | ❌ Not applicable (pre-tool) | ✅ Redact in request/response logging |
| Audit logging | ⚠️ Lightweight event logging | ✅ Full structured audit record |

**Interaction rule:** CES callback is a **fast-fail optimization** (catches expired consent without an HTTP roundtrip). BFA backend is the **authoritative gate** (performs actual legitimation). Both denials result in the agent receiving a structured error payload that maps to a user-facing refusal message.

### R-06: Add CES Toolset Configuration for BFA (P2 — addresses GAP-P2-2)

Reference the toolset binding pattern:

```json
{
  "displayName": "banking_operations",
  "openApiToolset": {
    "openApiSchema": "toolsets/banking_operations/open_api_toolset/open_api_schema.yaml",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

### R-07: Complete Rollback/Failure Strategy (P2 — addresses GAP-P2-3)

| Failure Mode | Strategy | User Experience |
|-------------|----------|-----------------|
| BFA service 5xx | **Fail-closed** — deny all cross-NARS operations | "I'm unable to access your account right now. Please try again shortly or speak with an advisor." |
| AcmeLegi timeout | **Serve from cache** for reads, **fail-closed** for writes | Cached read: transparent. Write: "I need to verify your authorization. Please hold." |
| GLUE/SOR unavailable | **Circuit breaker** (Resilience4j) with half-open retry | "Core banking systems are temporarily unavailable." |
| Consent SOR unavailable | **Fail-closed** — require re-consent | "I need to verify your consent. Would you like to proceed?" |

### R-08: Complete Observability Requirements (P2 — addresses GAP-P2-4)

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| BFA call latency p50 | < 20ms | N/A |
| BFA call latency p95 | < 50ms | > 50ms for 5 min |
| BFA call latency p99 | < 100ms | > 100ms for 2 min |
| AcmeLegi cache hit rate | > 80% | < 50% for 15 min |
| BFA error rate (5xx) | < 0.1% | > 1% for 5 min |
| Consent denial rate | Informational | > 10% (anomaly detection) |
| Legitimation denial rate | Informational | > 5% (anomaly detection) |
| Audit log write failures | 0% | > 0 (immediate alert) |

### R-09: Define Session Cache Policy (P2 — addresses GAP-P2-5)

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Cache TTL | 5 minutes (configurable) | Balance between AcmeLegi load reduction and authorization freshness |
| Cache scope | Per-session, per-account, per-operation-category | Legitimation for "VIEW_BALANCE" ≠ "TRANSFER_FUNDS" |
| Eviction triggers | Session end, consent withdrawal, explicit revocation, TTL expiry | Ensure stale authorization doesn't persist |
| Max entries per session | 50 | Prevent memory pressure from long sessions |
| Audit distinction | "cached_allow" vs "verified_allow" audit event types | Compliance auditability of cache-based decisions |
| Cache storage | In-memory (ConcurrentHashMap), Cloud Run instance-scoped | No shared cache needed — session affinity to instance |

### R-10: Add Cross-Agent BFA State Management (P2 — addresses GAP-P2-6)

BFA session state (legitimation cache, consent scope) should be stored in **CES session variables** (`variableDeclarations` in `app.json`) to survive agent-to-agent transfers:

| Variable | Type | Purpose |
|----------|------|---------|
| `bfa_legitimation_token` | Text | Cached legitimation result reference |
| `bfa_legitimation_expires` | Text | TTL timestamp for legitimation cache |
| `bfa_consent_scope` | Text | JSON-encoded consent scope set |
| `bfa_consent_validated_at` | Text | Timestamp of last consent verification |

### R-11: Add Connectivity Pattern Cross-Reference (P2 — addresses GAP-P2-1)

Add a statement in the architecture section:

> **Transport Policy (per ADR-CES-004):** BFA endpoints serving banking operations MUST use REST+OpenAPI toolsets for CES integration. MCP Streamable HTTP MUST NOT be used for BFA-mediated operations because `x-ces-session-context` is unavailable on MCP tools. If CES adds MCP context injection parity in the future, this constraint should be reassessed per ADR-CES-004 decision triggers.

### R-12: Add API Versioning Strategy

ADR-J008 §5.4 contains a `<!-- TODO: Add API versioning strategy -->` placeholder. For BFA:
- Use URI path versioning (`/api/v1/`, `/api/v2/`) consistent with existing `bfa-service-resource`
- OpenAPI schema version must match CES toolset deployment version
- Breaking changes require new CES toolset version and agent re-deployment
- Maintain backward compatibility for at least one major version

---

## Section 5: Cross-ADR Consistency Issues — ✅ ALL RESOLVED in ADR-J008 v1.1

> **All 4 cross-ADR consistency issues below were resolved by ADR-J008 Revision 1.1 (2026-02-10).** Retained for traceability.

### XADR-1: ADR-J008 vs ADR-CES-001 — BFA Scope Ambiguity ✅ RESOLVED

**Resolved in:** ADR-J008 v1.1 §1.4 (Scope Clarification — BFA embedded in `bfa-service-resource`, not a separate service)

**Issue:** ADR-J008 defines BFA as a boundary component between agents and SOR systems, with the BFA as a **new service** (§5.1). ADR-CES-001 retains `bfa-service-resource` as the existing REST API consumed by CES. These could be talking about the same component or different ones.

**Interpretation ambiguity:**
- Is the "BFA" in ADR-J008 the same as `bfa-service-resource` in ADR-CES-001?
- Or is ADR-J008 proposing a *new* BFA service *in front of* `bfa-service-resource`?

**Resolution needed:** Clarify that the BFA adapter logic should be **embedded within `bfa-service-resource`** as servlet filters/interceptors (not a separate service), maintaining the single-service deployment model that ADR-CES-001 relies on. This avoids adding a network hop between CES and the backend.

---

### XADR-2: ADR-J008 Agent List vs CES Agent Topology ✅ RESOLVED

**Resolved in:** ADR-J008 v1.1 §1.5 (Java-to-CES Agent Mapping table)

**Issue:** ADR-J008 §1.2 lists 10 agents with 47 tools as "Implemented Agents (as of 2026-01-25)." The CES topology in banking-use-case-mapping.md §3 shows a different agent structure (`voice_banking_root_agent → policy_guardrails_agent → balance_inquiry_agent → ...`). These appear to be different architecture representations — the Java `voice-banking-app` agent registry vs the CES `acme_voice_agent` multi-agent hierarchy.

**Consistency concern:** §5.3 ("Which Calls Go Through BFA?") references the Java agent names (`BankingOperationsAgent`, `LocationServicesAgent`). In CES, these would be different agents with different names and different tool bindings.

**Resolution needed:** Add a mapping table between Java agents (voice-banking-app) and CES agents (acme_voice_agent), or clarify that the BFA decision applies to both deployment models.

---

### XADR-3: ADR-J008 Latency Budget vs CES Platform Overhead ✅ RESOLVED

**Resolved in:** ADR-J008 v1.1 §2.3B (CES-specific latency budget)

**Issue:** ADR-J008 §2.3 provides a latency budget totaling 2475-2535ms against a 3-second target. This budget includes STT (500ms), LLM (1000ms), TTS (300ms) — all of which, in the CES deployment, are **managed by the CES platform** (Gemini model, Google Cloud Speech), not by `voice-banking-app`.

The CES-added overhead (session management, tool routing, callback execution, `x-ces-session-context` injection) is **not included** in the latency budget. ADR-CES-003 documents expected tool call latencies: REST 50-200ms, MCP Streamable HTTP 80-250ms. These are from the CES platform to `bfa-service-resource`, which is only one component of ADR-J008's "Tool Execution" (200ms) budget.

**Resolution needed:** Create a CES-specific latency budget that accounts for:
- CES internal routing and context assembly
- `before_tool_callback` execution time
- OpenAPI toolset HTTP call (including `x-ces-session-context` injection)
- BFA processing (AcmeLegi check, cache lookup, audit logging)
- GLUE gateway transit
- SOR response time
- `after_tool_callback` execution time

---

### XADR-4: Governance Framework References Out of Sync ✅ RESOLVED

**Resolved in:** ADR-J008 v1.1 Related section (now cross-references ADR-CES-001 through CES-004)

**Issue:** ADR-J008 §2.6 references alignment with ADR-J004, ADR-J005, ADR-J006 but does not reference ADR-CES-001 through ADR-CES-004, which were created after ADR-J008. The "Related" section at the top of ADR-J008 should be updated to include CES-series ADRs.

Additionally, ADR-CES-004 (Backend Language Choice) explicitly confirms Java-first for CES backend connectivity. ADR-J008's recommendation of "Custom Spring Service" (Appendix A) is consistent but should explicitly cross-reference ADR-CES-004 for governance reinforcement.

**Resolution needed:** Update ADR-J008 "Related" section and §2.6 governance table to include CES-series ADRs.

---

## Appendix A: Gap Cross-Reference Matrix

| Gap ID | Priority | ADR-J008 Section | CES Evidence | Status | Resolved In |
|--------|----------|------------------|--------------|--------|-------------|
| GAP-P0-1 | P0 | §1 (Architecture) | ADR-CES-001, platform-reference.md §5 | ✅ CLOSED | §1.3, §1.4 |
| GAP-P0-2 | P0 | §5.2 (BfaAdapter) | ADR-CES-002 §View 3, design-patterns.md §5.1 | ✅ CLOSED | §3.2 |
| GAP-P0-3 | P1 | §5.4 (Roadmap TODO) | tool-selection-guide.md §4.1, §4.2 | ✅ CLOSED | §7 |
| GAP-P1-4 | P1 | §3.2, §8 (Audit) | ADR-CES-002 §View 1, agents/sse_vs_streamable_http.md | ✅ CLOSED | §3.3 |
| GAP-P1-5 | P1 | §3.2, §3.3 | banking-use-case-mapping.md §6-7, platform-reference.md §8 | ✅ CLOSED | §3.4 |
| GAP-P2-1 | P2 | Not present | ADR-CES-004 Connectivity | ✅ CLOSED | §1.6 |
| GAP-P2-2 | P2 | §5.2 | ADR-CES-001 §Current State | ✅ CLOSED | §5.3 |
| GAP-P2-3 | P2 | §6.3 (TODO) | Production requirements | ✅ CLOSED | §6.4 |
| GAP-P2-4 | P2 | §8 (TODO) | ADR-CES-002 §View 1 | ✅ CLOSED | §6.5 |
| GAP-P2-5 | P2 | §8 (TODO) | Production requirements | ✅ CLOSED | §6.6 |
| GAP-P2-6 | P2 | §3.5 | platform-reference.md §9, banking-use-case-mapping.md §3 | ✅ CLOSED | §3.5 |
| GAP-P2-7 | P2 | §3.1 | DORA requirements | ✅ CLOSED | §3.1 |
| GAP-P2-8 | P2 | §2.2 | EU AI Act | ✅ CLOSED | §3.1 |
| XADR-1 | P1 | §5.1 | ADR-CES-001 | ✅ RESOLVED | §1.4 |
| XADR-2 | P2 | §1.2, §5.3 | banking-use-case-mapping.md §3 | ✅ RESOLVED | §1.5 |
| XADR-3 | P2 | §2.3 | ADR-CES-003 §View 5 | ✅ RESOLVED | §2.3B |
| XADR-4 | P2 | §2.6, Related | ADR-CES-001-004 | ✅ RESOLVED | Related section |

---

## TODO List — ✅ ALL RESOLVED

> **All 22 action items were addressed by ADR-J008 v1.1 (2026-02-10).** Retained for traceability.

### Critical (Must address before ADR-J008 acceptance) — ✅ DONE

- [x] **TODO-1:** Add CES deployment topology section showing how BFA maps to CES → bfa-service-resource → GLUE chain (GAP-P0-1, R-01) — §1.3
- [x] **TODO-2:** Add `x-ces-session-context` OpenAPI annotation design for BFA endpoints (GAP-P0-2, R-02) — §3.2
- [x] **TODO-3:** Add Testing & Evaluation Strategy accommodating CES L-01/L-11 limitations (GAP-P0-3, R-03) — §7
- [x] **TODO-4:** Define header propagation contract for audit correlation (GAP-P1-4, R-04) — §3.3
- [x] **TODO-5:** Define dual enforcement pattern (CES callback + BFA backend) for consent/legitimation (GAP-P1-5, R-05) — §3.4

### High (Should address before implementation begins) — ✅ DONE

- [x] **TODO-6:** Clarify BFA scope — embedded in `bfa-service-resource` vs separate service (XADR-1) — §1.4
- [x] **TODO-7:** Add Java-to-CES agent mapping table (XADR-2) — §1.5
- [x] **TODO-8:** Create CES-specific latency budget (XADR-3) — §2.3B
- [x] **TODO-9:** Update ADR-J008 "Related" section with CES-series ADR cross-references (XADR-4) — Related section
- [x] **TODO-10:** Add CES toolset configuration example for BFA OpenAPI binding (R-06) — §5.3

### Medium (Should address before production deployment) — ✅ DONE

- [x] **TODO-11:** Complete rollback/failure strategy (GAP-P2-3, R-07) — §6.4
- [x] **TODO-12:** Complete observability requirements with metric targets and alert thresholds (GAP-P2-4, R-08) — §6.5
- [x] **TODO-13:** Define session cache policy with TTL, eviction, and audit differentiation (GAP-P2-5, R-09) — §6.6
- [x] **TODO-14:** Add cross-agent BFA state management via CES session variables (GAP-P2-6, R-10) — §3.5
- [x] **TODO-15:** Add connectivity pattern cross-reference to ADR-CES-004 (GAP-P2-1, R-11) — §1.6
- [x] **TODO-16:** Add API versioning strategy (R-12) — §5.5
- [x] **TODO-17:** Add DORA-specific resilience requirements (GAP-P2-7) — §3.1
- [x] **TODO-18:** Expand EU AI Act transparency implementation details (GAP-P2-8) — §3.1

### Integration (After ADR-J008 is updated) — ⚠️ OPEN (downstream work)

- [ ] **TODO-19:** Create Beads epic tracking ADR-J008 revision work
- [ ] **TODO-20:** Update banking-use-case-mapping.md to reference ADR-J008's dual enforcement pattern
- [ ] **TODO-21:** Update tool-selection-guide.md decision tree to include BFA routing guidance
- [ ] **TODO-22:** Validate that `bfa-service-resource` OpenAPI schema includes `x-ces-session-context` annotations

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-10 | Copilot | Initial gap analysis: 5 critical/P1 gaps, 8 P2 gaps, 12 recommendations, 4 cross-ADR consistency issues, 22 action items. |
| 2026-02-10 | Copilot | Recheck: All P0/P1 critical gaps, P2 medium gaps, and XADR consistency issues marked **CLOSED** — verified against ADR-J008 v1.1 (revised by Codex). 18/22 TODOs resolved; 4 downstream integration TODOs remain open. |
| 2026-02-12 | Codex | CES transport alignment pass: updated residual “MCP SSE” wording to “MCP Streamable HTTP” where referencing ADR-CES transport policy for consistency. |
