# ADR-0101: CX Agent Studio → Private Tool Connectivity (VPC-SC + Service Directory PNA + Internal LB)

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, DX, Latency  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

CX Agent Studio must invoke tool endpoints from a confidential, private landing zone. VPC Service Controls (VPC‑SC) is required from day 1.
Under VPC‑SC, tools/callbacks cannot call arbitrary HTTP endpoints; private tool connectivity is achieved via Service Directory Private Network Access (PNA).
Tool endpoints must not be publicly exposed by default.

---

## Decision

Expose a single internal tool surface (BFA Gateway) behind an Internal HTTP(S) Load Balancer (ILB).
Register the ILB forwarding rule into Service Directory and configure CX Agent Studio tools to call the Service Directory endpoint (PNA).
Use IAM/service-identity authentication from CX Agent Studio to BFA (avoid static secrets in tool configs).

---

## Alternatives Considered

### Option A: Public HTTPS endpoint + IP allowlisting
- Pros:
  - Fast initial setup
  - No Service Directory/ILB complexity
- Cons:
  - Violates confidential/private posture
  - Weaker egress control under VPC‑SC
  - Higher exfiltration risk

### Option B: Per-agent private endpoints (many ILBs/Services)
- Pros:
  - Strong isolation per domain
  - Independent release cycles
- Cons:
  - Operational sprawl (SD registrations, IAM bindings, tool configs)
  - Harder governance and audit

### Option C: Single ILB + Service Directory PNA to a BFA Gateway (chosen)
- Pros:
  - Minimal tool surface
  - Aligned with VPC‑SC posture
  - Centralized policy/audit enforcement
- Cons:
  - Gateway is a critical dependency; requires strong SLOs/testing

---

## Consequences

### Positive
- Confidential/private tool connectivity end-to-end
- Simplified IAM: one invoker surface for CX Agent Studio
- Centralized validation, throttling, and audit correlation at BFA

### Negative / Trade-offs
- Requires Service Directory + ILB governance and IaC discipline
- BFA must scale reliably (min instances for voice latency)

### Risks & Mitigations
- Risk: Misconfigured Service Directory endpoint routes to wrong backend
  - Mitigation: Strict naming, IaC-managed registration, synthetic monitoring
- Risk: Gateway outage impacts all tools
  - Mitigation: Multi-zone deployment, canary releases, circuit breakers

---

## Follow-ups

- [ ] Define Service Directory namespace and service naming conventions (env/region/service)
- [ ] Provision ILB + serverless NEG for Cloud Run internal ingress
- [ ] Lock down Cloud Run Invoker to CES service identity only; enforce correlation headers

---

## Notes (Optional)

This ADR assumes VPC‑SC is enabled from day 1. Even if phased, this remains the target production posture.

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

