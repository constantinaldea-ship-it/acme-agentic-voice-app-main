# ADR-CES-004: Connectivity Pattern Selection for CES Agent Tool Integration

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed
**Date:** 2026-02-10
**Owner:** Architecture Guild / CES Integration Team
**Decision Drivers:** Security, auditability, compliance posture, CES platform constraints, operational maturity, developer experience
**Related:**
- [ADR-CES-001: REST API (BFA) vs MCP Server](ADR-CES-001-rest-api-vs-mcp-server.md)
- [ADR-CES-002: MCP vs OpenAPI Toolsets](ADR-CES-002-mcp-server-topology.md)
- [ADR-CES-003: MCP Location Services Implementation](ADR-CES-003-mcp-location-services-implementation.md)
- [CX Agent Studio Platform Reference](../cx-agent-studio/platform-reference.md)
- [CES Tool Selection Guide](../tool-selection-guide.md)
- [CX Agent Studio Design Patterns](../cx-agent-studio/design-patterns.md)
- [Banking Use Case Mapping](../cx-agent-studio/banking-use-case-mapping.md)

---

## Context

The CES agent `acme_voice_agent` currently integrates backend capabilities primarily through OpenAPI toolsets calling `bfa-service-resource` on Cloud Run. In parallel, the repository contains a scoped MCP Streamable HTTP spike for location services.

The architecture team needs an explicit decision record for when to use:

1. REST API + OpenAPI toolsets
2. MCP over Streamable HTTP

This decision must be grounded in:
- Enterprise banking controls (audit logging, consent, legitimation, authentication, data minimization)
- CES platform limits (including current MCP transport constraints)
- Operational readiness on Cloud Run
- Developer productivity and testability

---

## Security-Critical Mechanism: `x-ces-session-context`

OpenAPI toolsets support runtime context injection using `x-ces-session-context`. This mechanism is central to the banking security argument and is the primary reason REST + OpenAPI remains mandatory for sensitive operations.

What this provides:
- Injects session and project context values at runtime without model prediction.
- Keeps injected parameters invisible to the model, reducing hallucination risk for identifiers.
- Enables deterministic audit correlation for regulated banking traces.

Representative OpenAPI pattern:

```yaml
paths:
  /api/accounts/{session_id}/balance:
    get:
      operationId: getAccountBalance
      parameters:
        - name: session_id
          in: path
          required: true
          schema:
            type: string
          x-ces-session-context: $context.session_id
        - name: customer_id
          in: query
          required: true
          schema:
            type: string
          x-ces-session-context: $context.variables.customer_id
```

MCP impact today:
- CES MCP does not provide an equivalent injection mechanism.
- Session/customer arguments needed by sensitive APIs must therefore be model-supplied or inferred server-side from transport metadata.
- This increases risk of incorrect correlation, model-visible sensitive context, and GDPR/data-minimization concerns for regulated flows.

---

## Consent and Legitimation Control Model Across Tool Types

Consent and legitimation controls are enforced through callback gating patterns (for example `before_tool_callback`) as documented in CES callback lifecycle and banking use-case mapping.

Important clarification:
- Callback lifecycle hooks apply across tool types (OpenAPI and MCP), so pre-tool governance checks can be implemented consistently.
- This callback parity mitigates part of the risk, but does not replace `x-ces-session-context` for deterministic sensitive identifier injection.

| Control Concern | OpenAPI Toolset | MCP Streamable HTTP Tool |
|---|---|---|
| `before_tool_callback` gate | Supported | Supported |
| Consent pre-check enforcement | Supported | Supported |
| Legitimation pre-check enforcement | Supported | Supported |
| Deterministic hidden session context injection | Supported via `x-ces-session-context` | Not available |
| Banking suitability for sensitive operations | Strong | Limited until context-injection parity exists |

---

## Multi-Perspective Evaluation

### View 1: Enterprise Architect (Governance, Security, Compliance)

| Pattern | Assessment |
|---------|------------|
| REST + OpenAPI | Best fit for regulated banking flows. Supports deterministic session context injection via `x-ces-session-context` for strong audit correlation and reduced model-visible sensitive context. |
| MCP over Streamable HTTP | Acceptable for low-risk/public data use cases. Lacks OpenAPI-equivalent deterministic session context injection, which weakens compliance guarantees for sensitive financial operations. |
| ~~Streamable HTTP~~ | ~~Strategically relevant but currently non-viable for CES runtime integration due platform support constraints.~~ **Update (2026-02-12):** CES now requires Streamable HTTP for MCP servers. This row is merged with the MCP row above. |

### View 2: Platform Engineer (Operations, Observability, Scalability)

| Pattern | Assessment |
|---------|------------|
| REST + OpenAPI | Operationally mature. Standard HTTP metrics, tracing, and incident workflows on Cloud Run. Lowest operational complexity. |
| MCP over Streamable HTTP | Operationally close to REST — standard request/response semantics. No long-lived connection concerns. **Update (2026-02-12):** CES requires Streamable HTTP, eliminating SSE-specific operational overhead. |
| ~~Streamable HTTP~~ | ~~Potentially cleaner cloud-native model, but cannot be used for CES production flow today.~~ **Merged** with MCP row above. |

### View 3: Application Developer (Implementation, Debugging, Testing)

| Pattern | Assessment |
|---------|------------|
| REST + OpenAPI | Strongest current DX in this codebase: established schema-driven workflow, known CES packaging path, familiar debugging with HTTP tooling. |
| MCP over Streamable HTTP | Implementable with Spring AI MCP using `STREAMABLE` protocol. Standard HTTP transport simplifies debugging vs SSE. Less mature CES evaluation ergonomics relative to OpenAPI path. |
| ~~Streamable HTTP~~ | ~~Attractive long-term for MCP-native ecosystems, but currently blocked for CES-integrated delivery.~~ **Merged** with MCP row above. |

---

## Decision Criteria Flow

Use this flow for new CES tool connectivity decisions:

```text
Does the operation handle customer PII or financial data?
  ├─ YES → REST + OpenAPI (mandatory)
  └─ NO  → Does it require deterministic audit/session correlation?
              ├─ YES → REST + OpenAPI (mandatory)
              └─ NO  → Is hidden runtime session-context injection required?
                          ├─ YES → REST + OpenAPI (mandatory)
                          └─ NO  → MCP Streamable HTTP permitted (with approval workflow)
```

---

## Permitted and Prohibited Domain Mapping

| Domain | Connectivity Policy | Rationale |
|---|---|---|
| Branch search / ATM locator | MCP Streamable HTTP permitted | Public read-only data; no customer PII required |
| Exchange rates / public market reference data | MCP Streamable HTTP permitted | Public informational data |
| Account balance inquiry | REST + OpenAPI only | Customer data, audit correlation, consent/legitimation relevance |
| Transaction history | REST + OpenAPI only | Customer financial history and PII |
| Fund transfers | REST + OpenAPI only | Regulated money movement with strict controls |
| Card operations | REST + OpenAPI only | Sensitive card and customer data |
| Bill payments | REST + OpenAPI only | Financial action with customer/payee sensitivity |

---

## Comparative Analysis by Connectivity Pattern

### 1) REST API + OpenAPI Toolset

**Pros**
- Strongest current enterprise control alignment for banking operations.
- Mature CES integration model and operational tooling.
- Reuses existing Spring Boot architecture and Cloud Run deployment model.
- Supports `x-ces-session-context` for deterministic runtime context injection.

**Cons**
- Does not provide MCP-native dynamic discovery primitives.
- CES evaluation model has known limitations for OpenAPI operation-level `toolCall` assertions and scenario mocking.

**CES Compatibility**
- Fully supported and currently preferred for production banking use cases.

**Testing and Evaluation Support**
- Golden and scenario evaluations rely mainly on `agentResponse` outcomes for OpenAPI-backed behavior.
- Request-level verification and deterministic failure-path validation should be covered by backend integration tests.

### 2) MCP over Streamable HTTP

> **Update (2026-02-12):** CES now requires Streamable HTTP transport for MCP servers. SSE is no longer supported. The previous "MCP over Stateful SSE" and "MCP Streamable HTTP" sections have been merged.

**Pros**
- Native MCP interoperability.
- Supported by CES `mcpTool` via Streamable HTTP transport.
- Good candidate for public, non-sensitive domains (for example location search).
- Standard request/response semantics — no long-lived connection overhead.

**Cons**
- No OpenAPI-equivalent `x-ces-session-context` mechanism, limiting suitability for sensitive banking operations requiring deterministic identity/session correlation.
- Less proven evaluation ergonomics in CES compared to mature OpenAPI path.

**CES Compatibility**
- Supported. CES requires Streamable HTTP transport for MCP servers (since 2026-02-12).

**Testing and Evaluation Support**
- Favor end-to-end integration tests and MCP server-level tests.
- Use CES evaluations primarily for response-level behavior validation.

### ~~3) MCP Streamable HTTP~~ (Merged with §2 above)

> **Superseded (2026-02-12):** This was a separate section when Streamable HTTP was not yet supported by CES. CES now requires Streamable HTTP as the only MCP transport. See §2 above.

---

## Testing and Evaluation Strategy Comparison

| Evaluation Dimension | REST + OpenAPI | MCP (Streamable HTTP) |
|---|---|---|
| Golden `toolCall` assertions | Not supported for OpenAPI operations (L-01) | Unknown/untested in current project |
| Scenario `mockToolResponse` for tool calls | Not supported for OpenAPI operations (L-11) | Unknown/untested in current project |
| Golden/scenario `agentResponse` validation | Supported | Supported |
| Backend integration testing | HTTP integration tests | MCP server integration tests |
| Callback-based policy gating tests | Supported | Supported |

QA implications:
- For OpenAPI and MCP in this project, behavior validation should prioritize `agentResponse` expectations plus backend integration tests.
- Operation-level determinism for sensitive operations must be proven in backend tests, not CES `toolCall` assertions.

---

## Approval Workflow for New MCP Use Cases

Any new MCP production use case requires explicit approval before implementation.

Required approvers:
1. Architecture Guild lead (solution fit and ADR alignment).
2. Security representative (threat model and control adequacy).
3. Compliance/DPO representative (data classification and minimization).
4. SRE representative (operability, alerting, incident readiness).

Required submission artifacts:
1. Data classification result (public/internal/confidential/restricted).
2. Justification for why REST + OpenAPI is not preferred.
3. Callback control design for consent/legitimation/audit hooks.
4. Observability and alerting plan for MCP transport behavior.
5. Integration test evidence and rollback plan.

Approval criteria:
1. Domain is explicitly permitted by this ADR policy table.
2. No regulated customer-financial action is placed on MCP path.
3. Operational readiness and rollback are demonstrably in place.
4. Security/compliance sign-off is recorded in ADR or linked review artifact.

---

## Decision

Adopt the following connectivity policy:

1. **Enterprise banking production default:** REST API + OpenAPI toolsets.
2. **Scoped MCP usage:** Permit MCP over Streamable HTTP for low-risk, non-sensitive domains where deterministic session context injection is not required.
3. ~~**Streamable HTTP posture:** Defer for CES integration until platform support is available.~~ ✅ **MET (2026-02-12):** CES now requires Streamable HTTP. MCP usage in item 2 above uses Streamable HTTP.

---

## Context-Specific Recommendation

### Enterprise Banking Environment (Production, Regulated)

- Use REST + OpenAPI for sensitive operations (accounts, cards, transfers, payments).
- Require deterministic audit correlation and strict consent/legitimation enforcement.
- Keep MCP out of critical financial action paths until context-injection parity and equivalent control evidence is available.

### Demo Service Context (`bfa-service-resource`)

- Keep demo/stable path on REST + OpenAPI for reliability and easier troubleshooting.
- Allow MCP Streamable HTTP as a bounded experiment for location-like public data use cases.
- Streamable HTTP is now the required CES transport for MCP (since 2026-02-12).

---

## Consequences

### Positive
- Clear governance baseline for transport choice in CES integrations.
- Reduced compliance risk for production banking flows.
- Preserves delivery velocity by using existing mature stack for core operations.
- Allows controlled MCP learning via bounded Streamable HTTP experiments.

### Negative / Trade-offs
- Slower adoption of MCP-native capabilities for sensitive domains.
- Need to maintain clarity on where MCP is allowed versus disallowed.
- Potential future migration work if CES adds context parity features.

### Risks and Mitigations
- Risk: Teams expand MCP into sensitive flows prematurely.
  - Mitigation: Enforce architecture review gate requiring security and compliance sign-off.
- Risk: Platform support evolves and this decision becomes outdated.
  - Mitigation: Reassess when CES adds MCP context injection equivalent to `x-ces-session-context`.
- Risk: Misinterpretation of demo controls as production-grade.
  - Mitigation: Keep explicit environment labeling and production-readiness checklist in release documentation.

---

## CES Roadmap Status: MCP Session-Context Parity

As of 2026-02-10, referenced CES documentation in this repository does not provide a published commitment date for an MCP equivalent to `x-ces-session-context`.

Working assumption for governance:
1. Treat MCP session-context parity as **unknown/uncommitted** until explicitly documented by CES release notes or official docs.
2. Continue enforcing REST + OpenAPI for sensitive banking paths.
3. Reassess this ADR immediately when official CES documentation announces parity support.

---

## Decision Triggers for Reassessment

Re-evaluate this ADR if any of the following occur:

1. ✅ ~~CES adds production support for MCP Streamable HTTP in the relevant runtime path.~~ **MET (2026-02-12):** CES now requires Streamable HTTP ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)).
2. CES MCP tooling adds deterministic session-context injection equivalent to OpenAPI `x-ces-session-context`.
3. Enterprise policy mandates MCP as a primary integration standard.
4. Operational evidence shows Streamable HTTP parity with REST for reliability, observability, and incident response at required scale.

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-10 | Codex | Initial ADR created: connectivity policy for REST vs MCP SSE vs Streamable HTTP with enterprise and demo recommendations. |
| 2026-02-10 | Codex | Added Critical/High gap remediation: `x-ces-session-context` mechanics, consent/legitimation callback model, MCP PII risk statement, domain classification table, decision flow, evaluation strategy comparison, MCP approval workflow, and CES roadmap status note. |
| 2026-02-12 | Copilot | **Transport reversal:** CES official docs now require Streamable HTTP; SSE is no longer supported ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp), updated 2026-02-12). Merged “MCP over SSE” and “MCP Streamable HTTP” sections into single “MCP over Streamable HTTP.” Updated all SSE references, viewpoint tables, decision text, approval workflow, consequences, risks, and decision triggers (#1 MET). |
| 2026-02-12 | Codex | Consistency pass: replaced remaining policy references to “MCP SSE permitted” with “MCP Streamable HTTP permitted” and aligned decision flow/domain mapping with CES transport requirement. |
