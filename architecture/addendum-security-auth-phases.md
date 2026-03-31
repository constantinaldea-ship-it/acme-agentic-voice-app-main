# Acme Banking - Security Controls and Auth Hardening Addendum

**Status:** Active  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Created by:** Codex on 2026-02-28

---

## 1) Purpose

This addendum aligns security architecture controls with:
- CES audit requirements
- CMEK requirements
- phased authentication hardening from MVP to production-grade enforcement

Primary sources:
- `architecture/acme_cxstudio_architecture_facts_decisions.md`
- `architecture/adrs/ADR-0108-tool-authentication-&-secrets-service-identity-first;-secret-manager-references-only-when-unavoidable.md`
- `architecture/adrs/ADR-0109-observability-&-audit-correlation-standard-headers-tracing-redaction.md`
- `docs/implementation-plan/ADR-J010-migration-plan.md`
- `docs/architecture/cx-agent-studio/platform-reference.md`

---

## 2) Mandatory control set

1. **Ingress and identity**
   - CES calls BFA through private networking only.
   - Service identity is primary tool authentication model.

2. **Correlation and audit**
   - Mandatory headers on tool paths:
     - `request_id`
     - `session_id`
     - `agent_id`
     - `tool_id`
     - `customer_id_hash`
     - `auth_level` (ACR-aligned)
     - `policy_decision_id`
   - Preserve header continuity across BFA, adapters, and upstreams.

3. **Secrets and key management**
   - Service identity first, Secret Manager only when unavoidable.
   - CMEK region alignment and key lifecycle governance are required for regulated workloads.

4. **Logging and retention**
   - Enable Admin Activity and required Data Access logging for CES/BFA operations.
   - Keep searchable retention workflows for audit investigations.

---

## 3) Authentication hardening phases

### Phase 1 (January 1, 2026 to July 31, 2026) - MVP hardening baseline

- CES toolsets use service identity token flow.
- BFA accepts bearer tokens under a controlled non-production-grade validation path for MVP velocity.
- Sensitive operations remain bounded by phase capability limits and step-up policies.

### Phase 2 (August 1, 2026 to December 31, 2026) - production auth enforcement

- Replace mock/lenient bearer acceptance with strict ID token verification:
  - issuer validation
  - audience validation
  - service principal validation
- Enforce fail-closed behavior for token and claim validation failures.
- Require step-up token assurance (ACR) for write/sensitive operations.

### Phase 3 (2027+) - operational hardening at scale

- Expand continuous compliance testing for authn/authz regression.
- Strengthen key rotation, revocation drills, and audit evidence automation.

---

## 4) CMEK and audit implementation minimums

1. Key ring region must match app data region.
2. Key ownership, rotation schedule, and emergency recovery runbook must be approved before production.
3. Data Access audit logging must be explicitly enabled where required.
4. Audit sinks must support immutable retention and investigation queries.

---

## 5) Open risks and gates

| Risk | Gate | Required evidence |
|---|---|---|
| Incomplete token validation in production | Phase 2 go-live gate | Passing token-validation integration tests + deny-path tests |
| Missing Data Access audit signals | Pre-production gate | Audit log verification queries for runtime and admin APIs |
| CMEK misconfiguration by region | Environment readiness gate | Key-region alignment checklist and test encryption/decryption flow |

