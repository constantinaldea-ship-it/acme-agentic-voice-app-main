# ADR-0107: AuthZ PDP Integration & Enforcement: Layered PEP (BFA primary; Apigee secondary controls)

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, Consistency  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

Authorization decisions must be performed by an AuthZ PDP service.
Enforcement must be consistent across Apigee and Glue routes; Apigee and Glue are independent upstreams.
Step-up obligations must be enforced consistently for sensitive actions.

---

## Decision

Implement primary Policy Enforcement Point (PEP) in BFA:
- Validate CIAM token + assurance
- Call AuthZ PDP for decision and obligations
- Enforce deny/permit + apply obligations (redaction, step-up)
Use Apigee as secondary controls for routed APIs (quotas/threat protection; optional claim checks).
Fail-closed on validation/PDP failures.

---

## Alternatives Considered

### Option A: PDP enforced only in Apigee
- Pros:
  - Central API gateway enforcement
- Cons:
  - Does not cover Glue-only routes consistently
  - Hard to apply agent-context obligations before upstream

### Option B: PDP enforced only in BFA
- Pros:
  - Consistent across both upstreams
  - Full context available
- Cons:
  - Other clients hitting Apigee could bypass unless governed

### Option C: Layered: BFA PEP + Apigee secondary (chosen)
- Pros:
  - Consistent control + defense-in-depth where Apigee is used
  - Clean place for step-up/obligations
- Cons:
  - Requires policy alignment/testing across layers

---

## Consequences

### Positive
- Unified authorization decisions regardless of upstream
- Clear fail-closed behavior and auditability

### Negative / Trade-offs
- Policy drift risk between BFA and Apigee if not governed

### Risks & Mitigations
- Risk: Policy drift
  - Mitigation: Centralize decisions in PDP; keep Apigee checks minimal and standard
- Risk: PDP outage blocks tools
  - Mitigation: HA PDP deployment; monitoring; user-safe denial messaging

---

## Follow-ups

- [ ] Define PDP contract and obligations vocabulary
- [ ] Implement deny-by-default for sensitive routes
- [ ] Add integration tests for step-up and obligations enforcement

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

