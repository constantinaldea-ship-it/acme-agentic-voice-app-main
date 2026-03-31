# ADR-0108: Tool Authentication & Secrets: Service Identity First; Secret Manager references only when unavoidable

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, DX  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`

---

## Context

CES tools should avoid long-lived static secrets in tool configuration.
Service-to-service auth is preferred for internal endpoints (Cloud Run IAM).
Some external integrations might require secrets; these must be centrally stored and governed.

---

## Decision

Use service identity (ID token/IAM invoker) from CX Agent Studio to BFA.
Keep secrets in Secret Manager and reference secret versions only when a tool must authenticate to a non-IAM endpoint directly.
Apply strict change control and separation of duties for tool and secret administration.

---

## Alternatives Considered

### Option A: Embed API keys/client secrets directly in tool configs
- Pros:
  - Simple setup
- Cons:
  - High leakage risk; hard rotation/audit; violates confidential posture

### Option B: Service identity to BFA; backend holds secrets (chosen)
- Pros:
  - Minimizes secrets in CES
  - Central rotation/audit
  - Better compliance posture
- Cons:
  - BFA must proxy more integrations

### Option C: Tool uses Secret Manager secret-version references
- Pros:
  - Rotation without code changes
  - Better than embedding
- Cons:
  - Expands sensitive admin surface (tool config admins)

---

## Consequences

### Positive
- Reduced secret exposure risk
- Clear governance boundaries

### Negative / Trade-offs
- Requires role separation and audit alerts

### Risks & Mitigations
- Risk: Unauthorized tool config changes alter endpoints/secrets
  - Mitigation: Change control, audit alerts, least privilege, drift detection

---

## Follow-ups

- [ ] Define CES tool admin vs Secret Manager admin roles (SoD)
- [ ] Implement audit alerting for toolset changes
- [ ] Standardize service identity audiences and IAM bindings

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |

