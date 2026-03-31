# ADR-0109: Observability & Audit Correlation Standard (headers, tracing, redaction)

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Compliance, Security, Operability  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

Regulated banking assistant requires end-to-end auditability:
CES → BFA → (Apigee or Glue) → on-prem systems.
Because Apigee and Glue are independent, correlation must be consistent across both paths.

---

## Decision

Adopt mandatory correlation headers across all tool calls and upstream requests:
request_id, session_id, agent_id, tool_id, customer_id_hash, auth_level/ACR, policy_decision_id.
Implement OpenTelemetry tracing from BFA onward and keep logs within the perimeter with redaction and retention controls.

---

## Alternatives Considered

### Option A: Best-effort logging without strict correlation
- Pros:
  - Low effort
- Cons:
  - Fails audit requirements; hard incident response

### Option B: Strict correlation headers + tracing (chosen)
- Pros:
  - Audit-grade traceability
  - Faster debugging and compliance evidence
- Cons:
  - Requires discipline and conformance tests

---

## Consequences

### Positive
- End-to-end traceability across both upstream paths
- Clear evidence for investigations

### Negative / Trade-offs
- Implementation overhead (propagation, dashboards, tests)

### Risks & Mitigations
- Risk: PII leakage in logs/traces
  - Mitigation: Redaction at BFA boundary; least-privileged access; periodic audits

---

## Follow-ups

- [ ] Define and publish header/tracing contract
- [ ] Add conformance tests and CI gates
- [ ] Configure immutable audit sinks and retention policies

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

