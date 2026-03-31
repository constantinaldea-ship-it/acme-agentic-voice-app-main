# ADR-CES-004: Backend Implementation Language Choice for CES Data Connectivity

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed
**Date:** 2026-02-10
**Owner:** Architecture Guild / CES Integration Team
**Decision Drivers:** Compliance, auditability, operational supportability, performance, CES platform fit, delivery risk
**Related:**
- [ADR-CES-001: REST API (BFA) vs MCP Server](./ADR-CES-001-rest-api-vs-mcp-server.md)
- [ADR-CES-002: MCP vs OpenAPI Toolsets](./ADR-CES-002-mcp-server-topology.md)
- [ADR-CES-003: MCP Location Services Implementation](./ADR-CES-003-mcp-location-services-implementation.md)
- [CES Tool Selection Guide](../tool-selection-guide.md)
- [CX Agent Studio Platform Reference](../cx-agent-studio/platform-reference.md)
- [bfa-service-resource (Java)](../../../java/bfa-service-resource/README.md)
- [bfa-mcp-spike (Java)](../../../java/bfa-mcp-spike/README.md)
- [Java implementation overview](../../../java/README.md)

---

## Context

The CES agent `acme_voice_agent` needs a stable, secure, and auditable backend for tool connectivity and data retrieval in an enterprise banking context.

Current repository reality:
- Production-oriented backend services are Java Spring Boot:
  - `java/bfa-service-resource`
  - `java/voice-banking-app`
- MCP spike for CES compatibility is also Java:
  - `java/bfa-mcp-spike`
- TypeScript backend exists as archived MVP reference:
  - `ai-account-balance-ts` is explicitly non-primary and preserved for reference patterns.
- Official CES baseline: Python tools are a supported integration mechanism for custom logic, proprietary APIs, proprietary databases, tool chaining, and external network requests ([Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python), [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)).
- Repository architecture recommendation: even though Python tools are officially supported for API/database integration, this repo continues to prefer Java-backed services plus reviewed OpenAPI or server-hosted interfaces for regulated banking connectivity.

Banking architecture constraints:
- Strong audit trails and correlation requirements
- Strict consent and legitimation controls
- Data minimization and privacy controls aligned to regulated environments
- Low operational risk and clear incident response ownership
- Support for CES tool integration patterns and evaluation workflows

---

## Decision

Adopt **Java (Spring Boot) as the primary backend implementation language** for providing data and connectivity to CES `acme_voice_agent`.

Implementation policy:
1. **Core banking data plane remains Java-first** (`bfa-service-resource`, `voice-banking-app`).
2. **TypeScript remains reference-only** unless a new explicit migration ADR is approved.
3. **This repository does not choose Python as the primary connectivity architecture for regulated banking flows.** This is a repository architecture decision, not a statement that CES Python tools are unsupported for API or database integration.
4. **Hybrid is allowed:** Java for regulated banking APIs and transport endpoints, with optional Python for CES-local helper logic, thin wrapper tools, or other approved integrations — including documented API/database access and HTTP calls via Python tools where that is the clearest solution.
5. For MCP, prefer Java-based MCP exposure when needed, aligned with ADR-CES-002 and ADR-CES-003, while preserving REST/OpenAPI as default for sensitive banking operations.

---

## Weighted Decision Matrix (Enterprise Banking)

Scoring scale: 1 (poor) to 5 (excellent).  
Weighted score = weight × score.  
Total possible = 500.

| Criterion | Weight | Java | TypeScript | Python | Hybrid (Java Core + Python CES Helpers) |
|-----------|--------|------|------------|--------|------------------------------------------|
| Security and compliance posture | 25 | 5 (125) | 3 (75) | 2 (50) | 4 (100) |
| Audit and regulatory traceability | 20 | 5 (100) | 3 (60) | 2 (40) | 4 (80) |
| SRE operational supportability | 20 | 5 (100) | 3 (60) | 2 (40) | 3 (60) |
| Performance and latency predictability | 15 | 4 (60) | 4 (60) | 3 (45) | 4 (60) |
| CES integration fit and constraints alignment | 10 | 4 (40) | 4 (40) | 3 (30) | 4 (40) |
| Delivery risk and team familiarity | 10 | 4 (40) | 3 (30) | 3 (30) | 3 (30) |
| **Total** | **100** | **465 / 500** | **325 / 500** | **235 / 500** | **370 / 500** |

Decision outcome from matrix: **Java-first architecture is the highest-confidence option** for enterprise banking reliability, compliance, and operational ownership.

---

## Alternatives Considered

### Option A: Java as Primary Backend (Chosen)

Pros:
- Aligns with current production-oriented services and team operating model.
- Strong fit for enterprise audit, consent, legitimation, and security controls.
- Existing Spring Boot and Cloud Run patterns are already established.
- Supports both REST/OpenAPI and Java-based MCP pathways.
- Minimizes migration and operational risk.

Cons:
- Slightly slower prototyping speed versus lightweight scripting in some scenarios.
- Requires discipline to keep CES-specific tool wrappers thin and not duplicate business logic.

### Option B: TypeScript as Primary Backend

Pros:
- Strong developer productivity for rapid iteration.
- Good ecosystem for API and edge tooling.
- Familiar to teams with Node-focused toolchains.

Cons:
- Current repo explicitly treats TypeScript backend as archived reference, not primary target.
- Would require renewed architecture, governance, and SRE support model.
- Introduces avoidable migration risk from established Java services.

### Option C: Python as Primary Backend

Pros:
- Fast experimentation and rich AI ecosystem libraries.
- Useful for quick prototyping and analysis workflows.

Cons:
- Misaligned with current enterprise backend and SRE operating baseline in this repo.
- Although CES Python tools are officially valid for API/database integrations, this repository would still take on more manual auth, contract, and context handling in Python than in the existing Java plus OpenAPI approach.
- Higher risk for consistency, governance, and long-term production support in this project context.

### Option D: Hybrid (Java Core Banking + Python CES Tooling)

Pros:
- Preserves Java for regulated data plane and core controls.
- Allows selective Python usage for deterministic CES-local helper logic or thin wrapper tools.
- Can accelerate niche experimentation without changing core architecture.

Cons:
- Increases polyglot complexity, ownership boundaries, and support burden.
- Requires strict guardrails to prevent sensitive logic drift into less-controlled layers.
- Testing and operational accountability become less straightforward across language boundaries.

---

## Consequences

### Positive
- Clear language standard for regulated backend functionality.
- Strong alignment with security, compliance, and audit obligations.
- Lower operational risk and clearer SRE ownership.
- Reuse of existing Java architecture and deployment pipelines.

### Negative / Trade-offs
- Reduced flexibility for teams preferring TypeScript/Python-first delivery.
- Need to enforce strict boundaries for any hybrid additions.
- Potential slower experimentation unless explicit sandbox/prototype lanes are maintained.

### Risks & Mitigations
- Risk: Unauthorized growth of non-Java services handling sensitive banking logic.
  - Mitigation: Enforce architecture review and security sign-off for any non-Java production data-plane component.
- Risk: Hybrid approach introduces operational ambiguity.
  - Mitigation: Define explicit ownership, runbooks, and observability standards per component.
- Risk: CES platform evolves quickly and changes optimal language/transport fit.
  - Mitigation: Reassess through ADR update when CES capabilities materially change.
- Risk: Teams overuse CES direct Python tools for networked integration use cases.
  - Mitigation: Treat Python HTTP support as valid for targeted helper/wrapper use cases, but default regulated banking connectivity to OpenAPI or server-hosted integrations where auth, contracts, and context propagation are explicit and reviewable.

---

## Follow-ups

- [ ] Add an architecture guardrail note to CES docs: Java is the default for production banking connectivity.
- [ ] Define a lightweight exception process for approved hybrid Python helper use cases.
- [ ] Add an SRE readiness checklist for any new non-Java runtime proposal.
- [ ] Re-evaluate this ADR when CES transport and context-injection capabilities change.

---

## Notes (Optional)

This decision is language-governance focused and does not replace transport decisions from ADR-CES-001/002/003. Transport decisions remain:
- REST/OpenAPI as production default for sensitive flows.
- MCP (Streamable HTTP) only for scoped use cases where constraints and controls are acceptable.

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-10 | Codex | Initial ADR-CES-004 draft for backend language choice (Java vs TypeScript vs Python, including hybrid option). |
| 2026-02-12 | Copilot | Updated "MCP SSE" reference to "MCP (Streamable HTTP)" — CES now requires Streamable HTTP transport ([CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp), updated 2026-02-12). |
| 2026-03-13 | Copilot | Corrected the unsupported “Python has no outbound network egress” claim. Updated hybrid guidance and CES fit scoring to reflect official Python tool/runtime docs, while keeping Java/OpenAPI as the preferred production path for regulated banking integrations. |
