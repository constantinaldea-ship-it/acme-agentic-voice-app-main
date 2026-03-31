# Acme Banking - Architecture Terminology and Naming Glossary

**Status:** Active  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Created by:** Codex on 2026-02-28

---

## 1) Purpose

This glossary standardizes naming across architecture and documentation to avoid ambiguity.

---

## 2) Product/platform naming

| Canonical term | Allowed aliases | Guidance |
|---|---|---|
| CX Agent Studio | CES, Dialogflow CX (legacy context only) | Use `CX Agent Studio` in current architecture text. Mention legacy names only for migration context. |
| BFA Gateway | BFA edge, tool gateway | Use `BFA Gateway` for the single externally visible tool ingress. |
| Apigee Internal | Apigee private/internal | Use `Apigee Internal` for the Apigee instance used for regulated/productized APIs. Depends on CIDP, EIDP  |
| Glue (WSO2) | Glue gateway, on-prem WSO2 | Use `Glue` plus `(WSO2)` on first reference. Used to access legacy/internal APIs so of which are core banking systems of record. |
| CIAM | Customer Identity and Access Management | Use `CIAM` for identity management references. GCP cloud-hosted in a separate landing zone (NOT on-prem). Provides customer authn/authz. |
| EIDP | Enterprise Identity Provider | Use `EIDP` for enterprise identity provider references, needed to access some of the Apigee Internal and Glue accessible APIs/backends. Provides both authz and authn services. On-prem alongside Glue. |
| CIDP | Cloud Enterprise Identity Provider | Use `CIDP` for a cloud identity provider references, needed to access some of the Apigee Internal and Glue accessible APIs/backends. Both authz and authn services. GCP cloud-hosted in a separate landing zone (NOT on-prem). |
---

## 3) Agent naming convention

1. Canonical runtime names use `snake_case`.
2. Canonical IDs use `AG-xxx` in `architecture/agent-registry.md`.
3. PascalCase requirement labels may appear in business docs but must map to snake_case runtime names.

| Requirement-facing label | Canonical runtime name |
|---|---|
| ~~PolicyGuardrailsAgent~~ | `policy_guardrails_agent` *(removed from topology diagram — guardrails absorbed into BFA Gateway Edge PEP)* |
| HumanHandoverAgent | `human_handover_agent` |
| VoiceBankingRootAgent / voice_banking_root_agent | `voice_banking_agent` |
| LocationServicesAgent | `location_services_agent` |

---

## 4) Scope/phase terminology

| Canonical phase label | Date window |
|---|---|
| Phase 1 (H1 2026) | January 1, 2026 to July 31, 2026 |
| Phase 2 (H2 2026) | August 1, 2026 to December 31, 2026 |
| Phase 3+ | January 1, 2027 onward |

---

## 5) Data-source terminology

| Term | Meaning |
|---|---|
| Primary source | First source chosen by routing policy for a given agent/domain/phase |
| Secondary/fallback | Alternative source if primary is unavailable or non-authoritative for a request |
| Curated read model | BFA-hosted deterministic data model derived from approved source feeds |

---

## 6) Topology and enforcement terminology

| Term | Meaning |
|---|---|
| Single ingress topology | External pattern where CES calls only one tool endpoint (`BFA Gateway`) and never calls domain adapters directly. |
| Internal domain adapter | Domain-owned backend component behind BFA that handles domain-specific normalization, routing, and optional cache/read-model access. |
| Federated adapter model | Internal operating model where teams independently own adapters behind one common BFA ingress and shared policy boundary. |
| Exception lane | Explicitly approved non-default direct path (for example CES direct to Apigee for tightly scoped low-risk reads), with formal governance controls. |
| PEP (Policy Enforcement Point) | Runtime layer that enforces permit/deny and obligations from PDP decisions (primary PEP is BFA in this architecture). |
| PDP (Policy Decision Point) | Centralised authorization decision service that evaluates a request tuple of *(subject, action, resource, context)* and returns a **permit / deny** decision plus zero or more **obligations** (e.g. "redact field X", "log at WARN"). In this architecture the PDP runs inside the VPC-SC perimeter and is called by the BFA Gateway (PEP) on every tool invocation. The PDP is **stateless per-request**: it fetches policy rules from a policy store (OPA bundle, Cedar policy set, or equivalent) and may query external attribute sources (CIAM claims, CIDP scopes, EIDP roles) to resolve dynamic attributes. It never caches authorization decisions across requests. See ADR-0107 for the layered PEP/PDP integration pattern. |
| Fail-closed | Security posture where token, policy, validation, or trust failures result in denial and audit logging, not permissive fallback. |

---

## 7) Security and token terminology

| Term | Meaning |
|---|---|
| Opaque token | A token whose content is **not self-describing** — it is a random or pseudo-random reference string (e.g. a UUID or Base64 blob) that carries no embedded claims. The receiving service **cannot** decode or validate an opaque token locally; it must call the issuing identity provider's **token introspection endpoint** (RFC 7662) to learn the token's validity, scopes, subject, and expiry. **Trade-offs vs. JWTs:** Opaque tokens are smaller on the wire and are instantly revocable (the IdP can invalidate them server-side), but they introduce a runtime dependency on the introspection endpoint — every API call requires a network round-trip to the IdP, adding latency and creating an availability coupling. In this architecture, CIAM and CIDP may issue opaque tokens for customer-facing and service-to-service flows respectively; BFA or Apigee must introspect them before granting access. |
| JWT (JSON Web Token) | A self-contained, signed (JWS) or encrypted (JWE) token that embeds claims (subject, issuer, audience, scopes, expiry) in a Base64url-encoded JSON payload. The receiving service validates the signature locally using the issuer's public key (fetched once from a JWKS endpoint) without calling back to the IdP. **Trade-off vs. opaque tokens:** JWTs enable offline validation (no introspection round-trip) but cannot be revoked before expiry without additional infrastructure (e.g., a token deny-list or short-lived tokens with refresh). In this architecture, EIDP issues JWTs for on-prem Glue API access. |
| Token introspection | The process of calling an IdP's introspection endpoint (RFC 7662) to validate an opaque token at runtime. Returns active/inactive status plus associated claims. Each introspection call adds network latency; caching introspection results introduces a revocation-lag window. |
| RFC 8693 Token Exchange | An OAuth 2.0 extension that allows a service to exchange one security token for another (e.g., swap a CIAM customer token for a downstream service token with narrower scopes). BFA's Token Broker uses this pattern to obtain appropriately-scoped tokens for Apigee and Glue calls on behalf of the authenticated customer. |
| Refresh token | A long-lived credential used to obtain new access tokens without re-authenticating the user. Stored securely server-side; never exposed to CES or the browser. Relevant for maintaining session continuity in voice banking flows. |

---

## 8) Cross-reference map

| Concept | Primary document |
|---|---|
| Single ingress topology | `adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md` |
| Private CES tool connectivity | `adrs/ADR-0101-cx-agent-studio-private-tool-connectivity-vpc-sc-service-directory-pna-internal-lb.md` |
| Apigee/Glue independent upstream model | `adrs/ADR-0103-apigee-and-glue-boundary-independent-upstreams;-per-domain-routing-in-bfa.md` |
| Token strategy and assurance | `adrs/second-priority/ADR-0106-ciam-token-strategy-agentic-token-via-rfc8693-token-exchange-default-;-technical-on-behalf-of-for-employee-assisted-only.md` |
| AuthZ enforcement layering | `adrs/second-priority/ADR-0107-authz-pdp-integration-&-enforcement-layered-pep-bfa-primary;-apigee-secondary-controls.md` |
| Tool auth and secret handling | `adrs/ADR-0108-tool-authentication-&-secrets-service-identity-first;-secret-manager-references-only-when-unavoidable.md` |
| ADR-0104 option deep dives | `reviews/ADR-0104-topology-review-diverging-options.md` |
