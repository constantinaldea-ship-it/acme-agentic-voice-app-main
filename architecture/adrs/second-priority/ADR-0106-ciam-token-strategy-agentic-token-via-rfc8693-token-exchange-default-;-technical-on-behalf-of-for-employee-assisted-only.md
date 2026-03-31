# ADR-0106: CIAM Token Strategy: Agentic Token via RFC8693 Token Exchange (default); Technical On-Behalf-Of for employee-assisted only

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, Auditability, Non-repudiation  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

We must represent end-user identity and authentication assurance during tool calls.
CES calls tools using service identity; user identity claims must be cryptographically proven.
Token options: direct agentic token, token exchange, or technical on-behalf-of.
Step-up is required for customer-facing sensitive journeys (cards, transactions, identity-bound flows).

---

## Decision

Adopt RFC8693-style token exchange in BFA to mint a short-lived, audience-restricted 'agentic access token' from ForgeRock CIAM.
Include authentication assurance (ACR/auth-level) for step-up enforcement.
Allow technical on-behalf-of only for explicit employee-assisted flows with separate audit labeling and least privilege.

---

## Alternatives Considered

### Option A: CIAM mints direct agentic token to CES tools (no exchange)
- Pros:
  - Fewer hops
- Cons:
  - Expands secrets/auth complexity into CES tool configs
  - Harder governance

### Option B: BFA performs RFC8693 token exchange (chosen)
- Pros:
  - Centralized control/audit
  - Keeps secrets in backend
  - Supports step-up via ACR
- Cons:
  - Requires CIAM integration work; adds hop

### Option C: Technical on-behalf-of as default
- Pros:
  - Simpler plumbing short-term
- Cons:
  - Weak non-repudiation for consumers
  - Higher fraud/compliance risk

---

## Consequences

### Positive
- Strong auditable token for downstream calls
- Strict scopes/audience reduce blast radius

### Negative / Trade-offs
- Requires CIAM capability alignment and careful session lifecycle handling

### Risks & Mitigations
- Risk: CIAM does not support RFC8693 as-is
  - Mitigation: Use closest supported mechanism (JWT bearer grant or signed session assertions) preserving security properties
- Risk: Over-broad scopes/audience
  - Mitigation: Enforce least-privilege scopes, strict audience, short TTL, centralized enforcement

---

## Follow-ups

- [ ] Confirm CIAM capability for token exchange (RFC8693 or equivalent)
- [ ] Define token claims contract (aud, scope, acr, session-id, customer-id-hash)
- [ ] Define step-up triggers and required assurance levels per intent

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

