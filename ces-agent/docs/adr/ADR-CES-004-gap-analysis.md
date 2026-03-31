# ADR-CES-004 Gap Analysis: Connectivity Pattern Selection

**Date:** 2026-02-10
**Author:** Architecture Review (Copilot)
**Subject:** [ADR-CES-004: Connectivity Pattern Selection](ADR-CES-004-connectivity-pattern-selection.md)
**Status:** Findings & Action Items
**Cross-References:**
- [ADR-CES-001: REST API vs MCP Server](ADR-CES-001-rest-api-vs-mcp-server.md)
- [ADR-CES-002: MCP vs OpenAPI Toolsets](ADR-CES-002-mcp-server-topology.md)
- [ADR-CES-003: MCP Location Services Implementation](ADR-CES-003-mcp-location-services-implementation.md)
- [CX Agent Studio Platform Reference](../cx-agent-studio/platform-reference.md)
- [CES Tool Selection Guide](../tool-selection-guide.md)
- [Banking Use Case Mapping](../cx-agent-studio/banking-use-case-mapping.md)
- [Design Patterns](../cx-agent-studio/design-patterns.md)
- Original requirements: [agents/sse_vs_streamable_http.md](../../../agents/sse_vs_streamable_http.md)

---

## Executive Summary

ADR-CES-004 **reaches the correct decision** — REST + OpenAPI for banking operations, scoped MCP Streamable HTTP for non-sensitive domains, Streamable HTTP deferred. The conclusions are fully aligned with ADR-CES-001, 002, and 003 with no contradictions.

However, the ADR **functions as a condensed summary rather than a standalone decision record**. A reader unfamiliar with the 450+ line analyses in ADR-CES-002 and ADR-CES-003 will lack context to understand *why* the decisions are sound. Key technical arguments (session context injection mechanics, PII exposure risks, evaluation limitations) are referenced but not explained.

---

## Section 1: Original Prompt Requirements Insufficiently Addressed

The original prompt in `agents/sse_vs_streamable_http.md` specified several deliverables. Coverage assessment:

| Original Requirement | ADR-CES-004 Coverage | Gap |
|---|---|---|
| **Multi-Perspective Evaluation** (3 viewpoints) | ✅ Three viewpoints present | **Shallow.** Each is a single table-row assessment. ADR-CES-002 provides multi-page deep dives per viewpoint (SRE, Developer, Security Architect, Pragmatist, Strategist). ADR-CES-004 loses this analytical depth. |
| **Comparative Analysis** with Pros/Cons per pattern | ✅ Three patterns analyzed | **Adequate** but lacks the granular comparison tables in ADR-CES-002 (observability dimensions, DX workflow step-by-step, auth model comparison). |
| **CES Compatibility** per pattern | ✅ Addressed for all three | Adequate. |
| **Testing & Evaluation Support** per pattern | ⚠️ Mentioned briefly | **Insufficient.** Missing critical CES evaluation limitations: OpenAPI operations cannot be referenced in `toolCall` golden eval assertions (L-01 from tool-selection-guide.md), and cannot be mocked in scenario evaluations (L-11). MCP evaluation support is "unknown/untested" per the tool selection guide — ADR-CES-004 does not surface this uncertainty. |
| **Context-Specific Recommendation** (enterprise + demo) | ✅ Both environments addressed | Adequate. |
| **Impact of audit, compliance, security, legitimation** | ⚠️ Mentioned in passing | **Insufficient.** Original prompt explicitly asks for legitimation impact. ADR-CES-004 mentions `x-ces-session-context` but never discusses consent verification, legitimation token handling, AcmeLegi checks, or the banking-specific callback patterns documented in banking-use-case-mapping.md § 6. |

---

## Section 2: Missing Technical Considerations

### Critical Severity

| # | Gap | Description | Evidence Source |
|---|---|---|---|
| **T-1** | **`x-ces-session-context` injection specifics** | ADR-CES-004 refers to "deterministic session context injection" 6+ times but never explains *what* gets injected, *how* parameters are annotated, or *what happens* when absent. ADR-CES-002 View 3 shows the YAML annotation example — ADR-CES-004 should include or reference this. Without it, a reader cannot evaluate the security claim. | design-patterns.md § 5.1; ADR-CES-002 View 3 |
| **T-2** | **Consent and legitimation gate patterns per connectivity type** | Banking operations require consent verification and legitimation checks (AcmeLegi) before tool execution. banking-use-case-mapping.md documents `before_tool_callback` gating. ADR-CES-004 never addresses whether these gates function identically across OpenAPI vs MCP tool invocations. CES callbacks apply to *all* tool types per platform-reference.md § 8 — this should be explicitly stated as a mitigating factor. | banking-use-case-mapping.md § 6; platform-reference.md § 8 |
| **T-3** | **PII exposure risk quantification** | ADR-CES-002 View 3 identifies that MCP requires the model to predict session parameters (session_id, customer_id), exposing them in model context — token cost and PII leakage risk. ADR-CES-004 mentions "reduced model-visible sensitive context" for OpenAPI but never articulates the *counter-risk* for MCP. In regulated banking, PII in model context is a GDPR/data-minimization compliance issue, not a preference. | ADR-CES-002 § View 3 |

### High Severity

| # | Gap | Description |
|---|---|---|
| **T-4** | **Evaluation strategy differences** | tool-selection-guide.md documents that OpenAPI tools **cannot** be referenced in `toolCall` golden eval assertions (L-01) and **cannot** be mocked in scenario evals (L-11). MCP evaluation support is "unknown/untested". ADR-CES-004 mentions evaluation support in passing but misses these critical limitations that fundamentally affect QA strategy per pattern. |
| **T-5** | **Error handling and retry strategies** | REST returns standard HTTP status codes. MCP wraps JSON-RPC error codes inside HTTP 200 OK (per ADR-CES-002 observability table). ADR-CES-004 never addresses how error detection, retry logic, or circuit-breaker patterns differ. In production banking, how you detect and retry a failed balance inquiry is a critical design concern. |
| **T-6** | **Session management and Streamable HTTP request/response lifecycle** | MCP Streamable HTTP creates long-lived connections. ADR-CES-004 mentions this but never addresses: connection lifecycle during agent handoffs, behavior during Cloud Run scale-down/restart, reconnect strategies when HTTP request failures mid-conversation. ADR-CES-003 documents concrete Cloud Run hardening settings — ADR-CES-004 should reference them. |
| **T-7** | **Concrete examples of "low-risk, non-sensitive domains"** | The decision permits MCP Streamable HTTP "only for low-risk, non-sensitive domains" but never defines or enumerates what qualifies. ADR-CES-002 identifies location/branch search as the canonical example. ADR-CES-004 should include a positive list (branch search, ATM locator, exchange rates) and a negative list (account balance, transfers, card operations). |
| **T-8** | **Migration path when CES adds Streamable HTTP** | "Decision Triggers" lists CES Streamable HTTP support as a trigger but provides zero migration guidance. ADR-CES-003 includes a transport compatibility matrix and Spring AI configuration for the switch — ADR-CES-004 should reference this or include a migration sketch. |

### Medium Severity

| # | Gap | Description |
|---|---|---|
| **T-9** | **Hybrid architecture guidance** | ADR-CES-001 defines a "Hybrid" Option C (REST + MCP coexistence). ADR-CES-004's decision implicitly creates a hybrid pattern (REST for banking, MCP Streamable HTTP for location) but never uses the term or references the hybrid topology diagram from ADR-CES-002 (both transports in same process). Unclear whether this implies two services or co-hosted. |
| **T-10** | **Performance and latency requirements** | ADR-CES-003 documents expected latencies: REST 50–200ms, MCP Streamable HTTP in-process 80–250ms, standalone 150–400ms. ADR-CES-004 has no latency target or SLO. For voice banking, response latency directly impacts call quality (target <2s end-to-end per AGENTS.md § 8). |
| **T-11** | **Disaster recovery and failover** | If the MCP endpoint becomes unavailable, can the agent fall back to OpenAPI for the same operation? CES toolset binding is compile-time (deployed with agent package) per ADR-CES-001 notes. No documented dynamic fallback. Should be made explicit. |
| **T-12** | **Cost implications** | ADR-CES-003 View 5 documents Cloud Run cost impacts (SSE keeps instances alive longer). ADR-CES-004 never mentions operational cost as a decision factor despite listing it in Decision Drivers implicitly. |

### Low Severity

| # | Gap | Description |
|---|---|---|
| **T-13** | **Observability comparison table** | ADR-CES-002 includes a concrete observability comparison (tracing, error rates, latency metrics, Cloud Run metrics, audit logging). ADR-CES-004 says "standard HTTP metrics" for REST and "message-level telemetry complexity" for MCP but provides no comparison table. |
| **T-14** | **VPC Service Controls interaction** | platform-reference.md § 12 documents VPC Service Controls for CES. SSE connections may interact differently with VPC perimeters than standard HTTP. Not addressed. |

---

## Section 3: Missing Governance / Policy Elements

| # | Gap | Recommended Content |
|---|---|---|
| **G-1** | **Decision checklist / flowchart** | The original prompt asked for "Decision Criteria." tool-selection-guide.md provides a clear quick-decision flowchart. ADR-CES-004 has none. Add a decision tree: *"Does the operation handle customer PII? → REST+OpenAPI. Public data? → MCP Streamable HTTP may be considered."* |
| **G-2** | **Approval workflow for new MCP Streamable HTTP use cases** | ADR-CES-004 states "enforce architecture review gate requiring security and compliance sign-off" but doesn't define: who approves, what documentation is required, what review criteria are, who has veto authority. |
| **G-3** | **Monitoring and alerting requirements per pattern** | No requirements for what dashboards, alerts, or SLIs must be in place before an MCP Streamable HTTP tool goes to production. For REST, existing Cloud Run monitoring suffices. For SSE, custom instrumentation is needed (per ADR-CES-002). |
| **G-4** | **SLA/SLO definitions per connectivity type** | ADR-CES-004 is a *policy* document. It should define minimum SLO expectations: availability target, latency P99, etc. For voice banking, SLOs directly affect call quality. |
| **G-5** | **Data classification mapping** | The "low-risk, non-sensitive" qualifier needs a formal definition tied to the bank's data classification policy (public, internal, confidential, restricted). Without this, teams will self-classify inconsistently. |
| **G-6** | **Periodic review cadence** | Decision Triggers exist for reassessment, but no periodic review schedule (e.g., quarterly). Platform capabilities change without explicit triggers firing. |
| **G-7** | **Compliance validation requirements** | No statement about how to verify that a given tool integration *meets* the stated policy. Should reference penetration testing, security architecture review, and audit trail verification for new MCP deployments. |

---

## Section 4: Recommended Additions to the ADR

### 4.1 Add: `x-ces-session-context` Explanation (Critical — T-1)

After the "Context" section, add a subsection explaining:

1. What `x-ces-session-context` does — injects session context (session ID, project ID, customer variables) into API parameters *without model prediction* and *invisible to the model*.
2. Why it matters for banking — deterministic audit correlation, PII minimization, hallucination prevention.
3. What happens without it (MCP path) — model must predict all parameters; session IDs visible in context; susceptible to hallucination.
4. Include or reference the YAML annotation example from design-patterns.md § 5.1.

### 4.2 Add: Permitted and Prohibited Domain Table (High — T-7, G-5)

Replace "low-risk, non-sensitive domains" with a concrete classification:

| Domain | Connectivity | Rationale |
|--------|-------------|-----------|
| Branch search / ATM locator | MCP Streamable HTTP permitted | Public data, no PII, no session context required |
| Exchange rates / market data | MCP Streamable HTTP permitted | Public data, read-only |
| Account balance inquiry | REST + OpenAPI **only** | Customer PII, requires `x-ces-session-context` |
| Fund transfers | REST + OpenAPI **only** | Financial transaction, full audit trail, legitimation |
| Card operations | REST + OpenAPI **only** | Customer PII, consent required |
| Transaction history | REST + OpenAPI **only** | Customer PII, session correlation required |
| Bill payments | REST + OpenAPI **only** | Financial transaction, payee PII |

### 4.3 Add: Decision Flowchart (High — G-1)

```
Does the tool handle customer PII or financial data?
  ├─ YES → REST + OpenAPI (mandatory)
  └─ NO  → Does it require audit session correlation?
              ├─ YES → REST + OpenAPI (mandatory)
              └─ NO  → Is deterministic session context injection needed?
                          ├─ YES → REST + OpenAPI (mandatory)
                          └─ NO  → MCP Streamable HTTP permitted (with architecture review gate)
```

### 4.4 Add: Evaluation Strategy Comparison (High — T-4)

| Evaluation Dimension | REST + OpenAPI | MCP Streamable HTTP |
|---|---|---|
| Golden eval `toolCall` assertions | ❌ Not supported (L-01) | Unknown/untested |
| Scenario eval `mockToolResponse` | ❌ Not supported (L-11) | Unknown/untested |
| `agentResponse` behavioral eval | ✅ Supported | ✅ Supported |
| Backend integration tests | ✅ HTTP-level | ✅ MCP server-level |
| CES `before_tool` / `after_tool` callbacks | ✅ Supported | ✅ Supported |

Note: Both patterns share the `toolCall`-level eval assertability gap. This is worked around via backend integration tests and `agentResponse`-level evaluations.

### 4.5 Add: Error Handling Comparison (Medium — T-5)

| Dimension | REST + OpenAPI | MCP Streamable HTTP |
|---|---|---|
| Error signaling | HTTP status codes (4xx/5xx) | JSON-RPC error codes inside HTTP 200 |
| CES error handling | Platform-native HTTP error mapping | Requires JSON-RPC error interpretation |
| Retry strategy | Standard HTTP retry (idempotency keys for POST) | No built-in MCP retry; retry handled via standard HTTP retry policy |
| Circuit breaker | Standard HTTP patterns | transport-level; tool-call-level breaking is custom |
| Timeout detection | HTTP request timeout | SSE connection alive but tool call may hang; need application-level timeout |

### 4.6 Add: Architecture Alignment Section (Medium)

Explicit cross-reference to predecessor decisions:

- **ADR-CES-001:** Confirms REST as primary protocol. ADR-CES-004 extends this by codifying *when* MCP Streamable HTTP is permitted.
- **ADR-CES-002:** Confirms `x-ces-session-context` as the decisive differentiator. ADR-CES-004 operationalizes the spike approval.
- **ADR-CES-003:** Defines the implementation framework (Spring AI MCP, in-process topology). ADR-CES-004 does not alter those decisions.
- **Tool Selection Guide:** ADR-CES-004's decision flowchart should be added to the guide's quick-decision tree.

### 4.7 Add: Migration Sketch for Streamable HTTP (Medium — T-8)

When CES adds Streamable HTTP support:

1. Change Spring AI config: `spring.ai.mcp.server.protocol=STREAMABLE`
2. Remove SSE-specific Cloud Run hardening (keepalive, extended timeouts)
3. Update CES `mcpTool` JSON endpoint if URL changes
4. Verify stateless operation (no session affinity required)
5. Run spike evaluation suite against Streamable HTTP endpoint
6. Update ADR-CES-004 and ADR-CES-003

---

## Section 5: Open Questions to Resolve Before Finalizing

| # | Question | Stakeholder | Impact |
|---|---|---|---|
| **Q-1** | Does CES propagate any HTTP headers (e.g., `Authorization`, `X-Request-Id`) to MCP tool calls beyond the bearer token? This determines whether backend audit correlation is feasible via headers rather than tool parameters. | Google CES Product Team | Could change MCP audit capability assessment. |
| **Q-2** | What is the CES MCP roadmap for `x-ces-session-context` equivalent? Planned, under consideration, or out of scope? | Google CES Product Team | Determines how long the REST-only mandate will last. |
| **Q-3** | What happens to an in-flight MCP Streamable HTTP tool call when Cloud Run performs a rolling update or scale-down? Does CES automatically reconnect and retry? | Platform Engineering / SRE | Critical for production readiness of MCP Streamable HTTP. |
| **Q-4** | Has the CES team tested MCP tools with golden evals (`toolCall` assertions) or scenario evals (`mockToolResponse`)? The tool selection guide marks this as "unknown." | CES QA / Testing Team | Affects testing strategy for any MCP-backed functionality. |
| **Q-5** | Should the "architecture review gate" for Streamable HTTP MCP usage be formal CISO/DPO approval, lightweight architecture guild check, or automated policy check? | Security & Compliance | Determines operational overhead for teams wanting MCP Streamable HTTP. |
| **Q-6** | Are there data residency or sovereignty constraints that affect SSE connection routing differently than standard REST (e.g., regional endpoints per platform-reference.md § 11.2)? | Data Protection Officer | EU banking may require data within EU regional endpoints. |
| **Q-7** | For the hybrid model (REST for banking + MCP for location, same Cloud Run service), what is the concurrency impact? SSE connections consume Cloud Run concurrency slots differently than request/response. | Platform Engineering | Capacity planning for production deployment. |

---

## TODO List

### Critical (Must address before ADR finalization)

- [x] **TODO-1:** Add `x-ces-session-context` explanation subsection with YAML annotation example (§ 4.1, gap T-1)
- [x] **TODO-2:** Add consent/legitimation callback analysis — confirm CES `before_tool_callback` applies equally to OpenAPI and MCP tool types (§ 4.1, gap T-2)
- [x] **TODO-3:** Add explicit PII exposure risk statement for MCP path — GDPR/data-minimization argument (gap T-3)

### High (Should address before ADR approval)

- [x] **TODO-4:** Replace "low-risk, non-sensitive domains" with concrete permitted/prohibited domain table (§ 4.2, gaps T-7, G-5)
- [x] **TODO-5:** Add decision flowchart for connectivity pattern selection (§ 4.3, gap G-1)
- [x] **TODO-6:** Add evaluation strategy comparison table noting L-01 and L-11 limitations (§ 4.4, gap T-4)
- [x] **TODO-7:** Define approval workflow for new MCP Streamable HTTP use cases — who approves, criteria, documentation required (gap G-2)
- [x] **TODO-8:** Seek answer to Q-2 — CES roadmap for `x-ces-session-context` MCP equivalent (documented status: no published commitment in referenced docs as of 2026-02-10)

### Medium (Should address before production use)

- [ ] **TODO-9:** Add error handling and retry strategy comparison (§ 4.5, gap T-5)
- [ ] **TODO-10:** Add architecture alignment section cross-referencing ADR-CES-001, 002, 003 (§ 4.6)
- [ ] **TODO-11:** Add migration sketch for Streamable HTTP transition (§ 4.7, gap T-8)
- [ ] **TODO-12:** Add hybrid architecture clarification — confirm in-process co-hosting per ADR-CES-003 (gap T-9)
- [ ] **TODO-13:** Define latency SLOs per connectivity type — align with voice banking <2s target (gaps T-10, G-4)
- [ ] **TODO-14:** Define monitoring/alerting requirements for MCP Streamable HTTP before production (gap G-3)
- [ ] **TODO-15:** Seek answer to Q-1 — CES HTTP header propagation to MCP tools
- [ ] **TODO-16:** Seek answer to Q-3 — Cloud Run rolling update impact on SSE connections
- [ ] **TODO-17:** Seek answer to Q-4 — CES MCP evaluation support status

### Low (Address opportunistically)

- [ ] **TODO-18:** Add observability comparison table (gap T-13)
- [ ] **TODO-19:** Investigate VPC Service Controls interaction with SSE (gap T-14)
- [ ] **TODO-20:** Add periodic review cadence (e.g., quarterly CES MCP maturity check) (gap G-6)
- [ ] **TODO-21:** Define compliance validation checklist for new MCP deployments (gap G-7)
- [ ] **TODO-22:** Add cost analysis section — SSE instance-hour impact on Cloud Run (gap T-12)
- [ ] **TODO-23:** Clarify disaster recovery / fallback posture — no dynamic fallback between toolset types (gap T-11)
- [ ] **TODO-24:** Seek answer to Q-5, Q-6, Q-7

### Integration TODOs (After ADR-CES-004 is updated)

- [ ] **TODO-25:** Update tool-selection-guide.md quick-decision tree to incorporate ADR-CES-004 connectivity policy
- [ ] **TODO-26:** Create Beads epic tracking ADR-CES-004 gap remediation work
- [ ] **TODO-27:** Update banking-use-case-mapping.md to reference ADR-CES-004 for connectivity decisions

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-10 | Copilot | Initial gap analysis with 14 technical gaps, 7 governance gaps, 7 recommended ADR additions, 7 open questions, and 27 action items. |
| 2026-02-12 | Codex | Terminology alignment after CES transport change: updated residual “MCP SSE” references to “MCP Streamable HTTP” in policy examples, risk framing, and migration notes. |
