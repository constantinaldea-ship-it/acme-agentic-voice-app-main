# ADR-0105: Runtime Selection: Cloud Run (internal) for BFA/adapters; Kubernetes only where required

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** DX, Cost, Latency, Operability, Security  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

We need a private, confidential runtime for tool backends with voice-friendly latency.
mTLS everywhere is required, influencing sidecar/proxy patterns.
Kubernetes should be used only when required by specific components or enterprise constraints.

---

## Decision

Run BFA Gateway and most domain adapters on Cloud Run with internal ingress behind an ILB.
Use Kubernetes (GKE) only when required (e.g., dedicated mTLS proxy layer, specialized networking controls, or mandated runtime constraints).
Configure Cloud Run min instances for latency-sensitive tool paths.

---

## Alternatives Considered

### Option A: Everything on GKE
- Pros:
  - Maximum control (networking, sidecars, mTLS)
  - Uniform ops model
- Cons:
  - Higher operational overhead and slower delivery

### Option B: Everything on Cloud Run
- Pros:
  - Lowest ops burden
  - Fast iteration; autoscaling
- Cons:
  - Some enterprise mTLS patterns may require additional proxy design

### Option C: Cloud Run for BFA + selective GKE use (chosen)
- Pros:
  - Balances speed and control
  - Keeps confidential posture with private ingress
- Cons:
  - Two runtimes to operate when GKE is introduced

---

## Consequences

### Positive
- Fast delivery for most services
- Private ingress via ILB supports confidentiality

### Negative / Trade-offs
- mTLS everywhere may require additional proxy layer depending on strictness

### Risks & Mitigations
- Risk: Cold starts impact voice UX
  - Mitigation: Min instances for critical endpoints; tune concurrency/timeouts
- Risk: mTLS complexity on serverless
  - Mitigation: Centralize mTLS at dedicated proxy/gateway where necessary; enforce private networking

---

## Follow-ups

- [ ] Set Cloud Run performance guardrails (min instances, concurrency, timeouts) per tool class
- [ ] Decide if a dedicated mTLS proxy layer is required (and where it runs)
- [ ] Define SLOs and capacity tests for peak call volumes

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

