# ADR-0112: mTLS Everywhere + PKI / Key Rotation Strategy

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, Vendor Risk  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

mTLS everywhere is required. The architecture spans CES→BFA→(Apigee|Glue)→on‑prem.
We must manage certificates, rotation, and audit trails without compromising availability.

---

## Decision

Adopt centralized PKI strategy (enterprise PKI or Private CA integration) for issuing service certificates.
Enforce mTLS on all controllable hops:
- BFA↔CIAM
- BFA↔AuthZ PDP
- BFA↔Apigee (private)
- BFA↔Glue (via Interconnect)
- Apigee↔on‑prem targets
Automate rotation and implement expiry alerting; use CMEK where applicable.

---

## Alternatives Considered

### Option A: TLS only (server-auth), no mutual auth
- Pros:
  - Simpler operations
- Cons:
  - Does not meet mTLS everywhere requirement

### Option B: mTLS everywhere with centralized PKI + automation (chosen)
- Pros:
  - Meets requirement; strong service identity
  - Audit-ready
- Cons:
  - Operational complexity; rotation must be automated

---

## Consequences

### Positive
- Strong service identity; reduced MITM risk
- Meets security posture and audit expectations

### Negative / Trade-offs
- Certificate lifecycle management complexity

### Risks & Mitigations
- Risk: Expired certs or rotation outage
  - Mitigation: Automated rotation; expiry alerting; staged rollout; runbooks

---

## Follow-ups

- [ ] Decide PKI source and automate issuance/rotation
- [ ] Define mTLS matrix per hop and conformance tests
- [ ] Add expiry monitoring and incident runbooks

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

