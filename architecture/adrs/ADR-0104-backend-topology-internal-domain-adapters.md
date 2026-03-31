# ADR-0104: Backend Topology: Single BFA Gateway Tool Surface + Internal Domain Adapters

> PHASE: DESIGN_ONLY
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Status:** Proposed  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Decision Drivers:** Security, Compliance, Operability, Team Autonomy, Latency  
**Updated by:** Codex on 2026-02-28 for ADR hardening and topology-option alignment.  
**Related:**  
- Master facts & decisions: `acme_cxstudio_architecture_facts_decisions.md`
- Canonical agent registry: `../agent-registry.md`
- Topology review and diverging options: `../reviews/ADR-0104-topology-review-diverging-options.md`
- Round 2 topology review (EIDP/CIDP, CES constraints, Token Broker): `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md`
- Terminology glossary: `../terminology-glossary.md`
- ADR-0101 (private ingress): `ADR-0101-cx-agent-studio-private-tool-connectivity-vpc-sc-service-directory-pna-internal-lb.md`
- ADR-0103 (Apigee/Glue independent upstreams): `ADR-0103-apigee-and-glue-boundary-independent-upstreams;-per-domain-routing-in-bfa.md`
- ADR-0105 (runtime): `ADR-0105-runtime-selection-cloud-run-internal-for-bfa-adapters;-kubernetes-only-where-required.md`
- ADR-0106 (token strategy): `second-priority/ADR-0106-ciam-token-strategy-agentic-token-via-rfc8693-token-exchange-default-;-technical-on-behalf-of-for-employee-assisted-only.md`
- ADR-0107 (AuthZ enforcement): `second-priority/ADR-0107-authz-pdp-integration-&-enforcement-layered-pep-bfa-primary;-apigee-secondary-controls.md`
- ADR-0108 (tool auth and secrets): `ADR-0108-tool-authentication-&-secrets-service-identity-first;-secret-manager-references-only-when-unavoidable.md`
- ADR-CES-001 (REST vs MCP): `../../ces-agent/docs/adr/ADR-CES-001-rest-api-vs-mcp-server.md`
- ADR-J008 Gap Analysis (CES integration gaps): `../../ces-agent/docs/adr/ADR-J008-gap-analysis.md`

---

## Context

CX Agent Studio will run multiple subagents with distinct domain responsibilities and independent team ownership.
Each domain may require one of three source types:
1. Apigee Internal APIs (productized/regulated domains).
2. Glue (WSO2) APIs (legacy/internal domains).
3. BFA-curated read models (deterministic low-latency reads).

Key constraints:
1. Apigee and Glue are independent upstreams; no chaining assumptions.
2. VPC-SC private posture is required for CES tool connectivity.
3. Token and secret handling in CES tool configuration should be minimized (service-identity-first).
4. Voice latency pressure requires predictable tool-call paths and explicit caching strategy by domain.
5. Team autonomy is required, but external governance and audit controls must remain consistent.

Problem statement:
If each domain exposes its own CES-facing tool endpoint, operational and governance sprawl increases:
- More IAM bindings and Service Directory registrations.
- More tool configuration and secret-management surface.
- Higher policy drift risk across teams.
- Harder audit correlation and uniform fail-closed behavior.

---

## Decision

Adopt a single externally visible tool ingress for CES:
1. CES calls only the BFA Gateway.
2. Domain adapters are internal to BFA topology and not CES-addressable.
3. BFA is the mandatory policy boundary for:
   - request validation and correlation
   - identity and token processing (per ADR-0106/0108)
   - authorization obligations enforcement (per ADR-0107)
   - domain routing
4. Internal adapters may call Apigee, Glue, CIAM, EIDP or curated read models according to `../agent-registry.md`.

Decision scope:
- This ADR defines the external topology contract (`single ingress`).
- This ADR does not prescribe a single internal packaging model, but **strongly recommends separately deployable services** (Cloud Run internal, per ADR-0105) for all write-path and upstream-dependent adapters. In-process modules are acceptable only for stateless read-model adapters during initial bootstrap (Phase 1b). See [Internal Adapter Packaging Model](#internal-adapter-packaging-model) below.

Explicit non-goals:
1. No direct CES-to-adapter topology.
2. No default direct CES-to-upstream topology.
3. No assumption that Glue is reachable from GCP until validated by networking controls.

### What is an internal domain adapter?

A domain adapter is a **BFA-internal service** — not a backend system of record, not a CES-visible component. It sits between the BFA Gateway and the upstream backends (via Glue or Apigee). Each adapter is owned by a domain team and is responsible for:

| Responsibility | Description |
|---|---|
| **Protocol translation** | Maps the BFA Gateway's canonical tool-call schema into the domain's upstream REST API contracts (specific endpoints, headers, payloads) |
| **Multi-step orchestration** | Sequences multiple upstream calls when a single tool invocation requires several backend round-trips (e.g., partner lookup → advisor search → slot selection → booking) |
| **Token acquisition** | Calls the Token Broker Service to obtain the correctly-scoped tokens (EIDP, AuthZ, CIAM, CIDP) required by the domain's upstream — adapters never interact with identity providers directly |
| **Response normalization** | Transforms upstream-specific response formats into the BFA Gateway's canonical response schema (with correlation IDs, status codes, cacheable metadata) |
| **Error mapping** | Converts upstream error codes (409 conflict, 404 not found, 503 unavailable) into voice-friendly error responses suitable for the conversational layer |
| **Domain caching** | Optionally caches upstream data within a session or short TTL to reduce repeated upstream round-trips (e.g., advisor lists, time slots) |

What an adapter does **NOT** do:
- Does NOT apply PII redaction — that is the BFA Gateway's Response PEP
- Does NOT authorize requests — that is the BFA Gateway's Edge PEP via PDP
- Does NOT own the data — the upstream backend system of record does
- Does NOT expose any endpoint to CES — only BFA Gateway is CES-addressable

**Example — Appointments Adapter (AG-004):**
```
CES (Appointments Sub-Agent)
  → BFA Gateway (Edge PEP: validate, correlate, authorize)
    → Appointments Adapter (AG-004, Cloud Run internal):
        1. Call Token Broker → acquire EIDP + AuthZ tokens
        2. Call Glue → POST /pd-partners/search (partner identification)
        3. Return partner list to user for confirmation
        4. Call Glue → GET /advisors?partnerId=... (advisor discovery)
        5. Return advisor list to user for selection
        6. Call Glue → GET /appointments/slots?advisorId=... (timetable)
        7. Return time slots to user for selection
        8. Call Glue → POST /appointments (booking creation)
        9. Return confirmation to BFA Gateway
  ← BFA Gateway (Response PEP: redact PII, attach correlation IDs)
← CES → User: voice confirmation
```

### Internal Adapter Packaging Model

> **⚠️ CRITICAL: Packaging choice determines whether Option C delivers its promised benefits or silently degrades into Option A.**

Option C's per-domain blast radius isolation, independent releases, and team autonomy are **only realized when adapters are separately deployable services**. If all adapters are packaged in-process within the BFA Gateway, the architecture collapses to Option A (monolith) in practice:

| Dimension | Option C with separate services | Option C in-process (undisciplined) | Option A (monolith) |
|---|---|---|---|
| Code boundaries | Enforced by network | Erode over time (cross-imports) | None |
| Release coupling | Per-adapter (independent pipelines) | 100% (same binary, same release train) | 100% |
| Blast radius | Per-adapter (Appointments crash ≠ Banking down) | 100% (shared JVM/process) | 100% |
| Team autonomy | Real (own repo, own pipeline, own schedule) | Nominal (code ownership only) | None |
| Cold-start impact | Per-adapter (only the affected service restarts) | Grows with N adapters (+200-400ms each) | Grows with N adapters |

**Recommendation by adapter category:**

| Category | Examples | Recommended packaging | Rationale |
|---|---|---|---|
| **Write-path / upstream-dependent** | AG-004 (Appointments), AG-007 (Banking), AG-008 (Credit Card) | **Separate Cloud Run service** (mandatory) | Multi-step orchestration, Glue/Apigee round-trips, blast radius isolation needed |
| **Read-model / stateless** | AG-003 (Location), AG-005 (Fees), AG-006 (Knowledge), AG-010 (Mobile) | Separate Cloud Run service (recommended) or in-process module (acceptable during Phase 1b only) | No upstream calls, read from BFA-hosted curated data, low blast radius |
| **Platform services** | Token Broker, AuthZ PDP client | **Separate Cloud Run service** (mandatory) | Shared dependency across all adapters, must scale independently |

**Anti-pattern: in-process adapters as permanent architecture.** If in-process modules are used during Phase 1b bootstrap, they MUST be extracted to separate services before Phase 2. Failure to extract results in:
- Option A's blast radius (100%) with Option C's governance overhead
- Module boundary erosion as teams take shortcuts (adapter A imports adapter B's internals)
- Single release train despite nominal "team ownership"
- Growing cold-start times as adapters accumulate

---

## Alternatives Considered

> **Reading guide:** Each option below is evaluated from four stakeholder perspectives — **delivery velocity**, **security & compliance**, **operational risk**, and **developer experience**. Quantitative estimates are provided where available. For full deep-dive analysis per option, see the linked review documents.

### Option A: Single monolith backend (all domains in one runtime)

All domain logic, routing, token exchange, and caching in a single BFA process.

- Pros:
  - Simplest deployment unit (1 Cloud Run service, 1 CI pipeline)
  - Fastest initial setup — zero inter-service coordination overhead
- Cons:
  - Large blast radius (one bad adapter deployment takes down all domains)
  - Coupled release cadence across teams (every change = full regression)
  - Harder per-domain compliance and policy regression testing

| Perspective | Assessment |
|---|---|
| **Delivery velocity** | Fast for Week 1. Degrades quickly as teams grow. All N teams share one release train, meaning any team's regression blocks all others. Estimated: 1 deployment pipeline, but merge conflicts scale as O(N²) with team count. |
| **Security & compliance** | Strong — all enforcement in one process, single audit stream. But a security vulnerability in any adapter compromises the entire runtime. |
| **Operational risk** | Blast radius = 100%. A memory leak in the Location adapter takes down Banking and Payments. MTTR is high because the failure domain is the entire service. Cold-start time grows with adapter count (~200-400ms per additional adapter initialization). |
| **Developer experience** | Low cognitive load initially (one codebase), but increases rapidly. Teams cannot use different languages/frameworks. Shared dependency management causes version conflicts. Local dev requires running the entire monolith. |

### Option B: Many CES-exposed domain backends (federated endpoints)

Each team registers its own tool endpoint directly with CES. No BFA gateway.

- Pros:
  - Strongest isolation and independent scaling
  - Teams fully own their deployment lifecycle
- Cons:
  - High operational sprawl
  - Larger IAM/tool configuration surface
  - Higher risk of auth/token/policy inconsistency across domains

| Perspective | Assessment |
|---|---|
| **Delivery velocity** | Highest individual team velocity — each team deploys independently. But: **N direct endpoints = N × (IAM bindings + Service Directory registrations + tool configs + Secret Manager entries + audit policies + secret rotation schedules)**. For 7 agents, that is 7× governance overhead vs 1× for single ingress. |
| **Security & compliance** | Weakest option. Each team must independently implement token validation, PII redaction, PDP calls, correlation header injection, and fail-closed behavior. Policy drift is near-certain. Audit requires correlating N independent log streams. Violates ADR-0108 (service-identity-first) because each CES tool config needs its own auth setup. |
| **Operational risk** | Per-service blast radius is small (good), but governance blast radius is large. Inconsistent error handling across teams creates unpredictable failure modes. Incident response requires understanding N different auth/routing patterns. Estimated: 3× IAM surface area, 3× the number of Service Directory entries to manage. |
| **Developer experience** | Highest autonomy per team, but no shared platform benefits. Each team builds their own PEP, token handling, PII filtering, and observability. Estimated cognitive overhead: each team must understand CIAM + CIDP + EIDP + PDP + PII redaction mechanics independently. |

### Option C: Single BFA gateway + internal domain adapters (chosen)

CES calls only BFA. BFA enforces identity/token/PDP/obligations. Domain adapters are separately deployable internal services, each owned by a domain team. Adapters handle protocol translation, multi-step orchestration, and Token Broker integration — but never identity, authorization, or PII redaction (those stay in BFA Gateway). See the [adapter definition](#what-is-an-internal-domain-adapter) and [packaging model](#internal-adapter-packaging-model) in the Decision section.

- Pros:
  - Minimal CES tool surface (1 tool endpoint, 1 IAM binding, 1 Service Directory entry)
  - Strong centralized policy, audit, and correlation controls
  - Preserves internal modularity and team ownership
  - Per-adapter blast radius, independent release cycles, and team-owned deployment pipelines
  - Adapters consume Token Broker via internal API — no direct identity provider knowledge required
- Cons:
  - BFA is a critical shared dependency
  - Requires strict routing and contract discipline
  - Adapter extraction requires upfront investment (Cloud Run setup, internal networking, health checks per adapter)

| Perspective | Assessment |
|---|---|
| **Delivery velocity** | BFA must be minimally operational before any agent works end-to-end (Phase 1 dependency). After BFA skeleton exists, teams deploy adapters independently behind stable gateway contracts. Read-model adapters (no upstream calls) can ship in weeks 2-4 without waiting for Token Broker. **Net effect:** slower start (weeks 1-2), then comparable velocity to Option B with dramatically lower governance overhead. |
| **Security & compliance** | Strongest single-boundary enforcement. One PEP, one PDP integration, one PII redaction point, one audit stream. Policy changes are applied once and take effect for all domains. CIAM/CIDP/EIDP token exchange is centralized in Token Broker Service — adapters never touch identity providers directly. Enables compliance evidence from one system, not N. |
| **Operational risk** | BFA is a single point of failure — mitigated by HA deployment, canary releases, and SLO-based autoscaling. Blast radius for adapter failures is per-domain only (Banking adapter crash does not affect Location). Estimated latency overhead: +20-50ms per call for policy enforcement (PEP + PDP round-trip), acceptable within 3-second voice SLA budget. |
| **Developer experience** | Teams own their adapter end-to-end (code, cache, release cycle). Shared middleware package handles correlation headers, error normalization, and Token Broker client. Adapter templates and CI conformance gates reduce boilerplate. Trade-off: teams depend on BFA platform team for gateway changes and shared library updates. |

> **Why Option C wins:** It is the only option that satisfies all three audiences simultaneously — developers get team autonomy behind stable contracts, business stakeholders get manageable governance overhead (1× not N×), and compliance teams get a single enforceable policy boundary with complete audit coverage.

Detailed diverging analysis and deep dives:
- Main review: `../reviews/ADR-0104-topology-review-diverging-options.md`
- Option A deep dive: `../reviews/ADR-0104-option-A-strict-single-bfa.md`
- Option B deep dive: `../reviews/ADR-0104-option-B-federated-adapters.md`
- Option C deep dive: `../reviews/ADR-0104-option-C-controlled-apigee-exceptions.md`
- Round 2 review (with Token Broker recommendation): `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md`

---

## Consequences

### Positive
- One external ingress from CES simplifies IAM/tool governance.
- BFA centralizes authorization and obligation enforcement.
- Better audit traceability with consistent correlation propagation.
- Team autonomy remains possible behind stable gateway contracts.
- Supports independent upstream routing choices while keeping external topology stable.

### Impact on Delivery Velocity

> **For architects and business stakeholders concerned about speed-to-market.**

Single ingress does NOT mean single team or single release cycle. Option C (chosen) combined with the federated adapter model (see `../reviews/ADR-0104-option-B-federated-adapters.md`) delivers team autonomy as follows:

| Phase | What ships | Team dependency on BFA | Velocity impact |
|---|---|---|---|
| Phase 1a (weeks 1-2) | BFA Gateway skeleton + Edge PEP (validation, correlation, PII redaction) | BFA platform team only | Foundation — unblocks all downstream work |
| Phase 1b (weeks 2-4) | Read-model adapters (location, fees, KB) — no upstream calls needed | None — adapters use BFA-hosted curated read models | **Immediate team-level velocity**. AG-003, AG-005, AG-006, AG-010 operational |
| Phase 1c (weeks 4-8) | Token Broker Service v1 (CIDP for Apigee, CIAM exchange) | Token Broker team | Unblocks AG-009 (products), AG-007 (banking) via Apigee |
| Phase 1d (weeks 6-10) | Interconnect/VPN + EIDP integration in Token Broker | Infrastructure + Token Broker teams | Glue-dependent fallback paths operational |
| Phase 2 | Full write-capable adapters with step-up enforcement | Per-domain teams independently | Full operational capability |

**Key insight:** Centralized policy enforcement **accelerates** delivery by eliminating per-team compliance work. Without BFA, every team must independently implement PII redaction, token exchange, PDP integration, correlation headers, and fail-closed behavior — multiplying effort by N teams. With BFA, this is done once and shared.

**Governance arithmetic:**
- Without BFA: N agents × (IAM binding + SD registration + tool config + secret rotation + audit policy + PDP integration + PII filter) = **7N governance items**
- With BFA: 1 × (IAM binding + SD registration + tool config) + N × (adapter contract conformance) = **3 + N governance items**
- Net savings at N=7: **~46 governance items reduced to ~10**

### Regulatory Compliance Impact

> **For security, compliance, and risk teams.**

| Regulatory framework | Requirement | How single ingress addresses it |
|---|---|---|
| **GDPR Article 32** | Appropriate technical measures for data protection | Single PII redaction boundary at BFA ensures customer data is minimized before reaching CES prompt context. One enforcement point to audit, not N. |
| **PCI-DSS 3.2.1 / 4.0** | Protect cardholder data; restrict access on a need-to-know basis | BFA enforces field-level redaction (mask account numbers, card details) before data enters CES. Adapters behind BFA never expose raw cardholder data to the conversational layer. |
| **SOC 2 CC6.1** | Logical access security controls | Single PEP at BFA with PDP integration provides one auditable access control boundary. All tool invocations pass through BFA's access decision chain before reaching banking backends. |
| **EBA Guidelines on ICT Risk** | Operational resilience and access control for critical systems | Centralized gateway simplifies operational resilience monitoring. BFA outage is one failure mode to plan for, not N independent failure modes with N different recovery procedures. |

**Why distributed PEP (Option B direct) creates audit gaps:**
With multiple CES-exposed endpoints, each team's PEP implementation becomes an independent audit target. A compliance officer must verify N separate policy enforcement implementations, N separate PII handling behaviors, and N separate audit log formats. A single PEP at BFA reduces this to one verification scope. ADR-0107 requires BFA as the primary PEP with Apigee as secondary — this is architecturally enforceable only with single ingress.

### Incident Response Impact

| Dimension | Single ingress (Option C) | Multiple endpoints (Option B direct) |
|---|---|---|
| **Blast radius per adapter failure** | Isolated to the failing domain. BFA returns a graceful degradation response for that domain while other adapters continue serving. | Same per-service isolation, but no central circuit breaker or unified degradation strategy. |
| **BFA failure blast radius** | 100% — all agents affected. **Mitigated by:** HA deployment (min 2 instances), canary releases, health checks, SLO-based autoscaling, and pre-deployed read-model fallback data. | N/A — no single gateway dependency. But: no unified fallback either. |
| **MTTR (Mean Time To Recover)** | One system to diagnose. Correlation headers enable end-to-end trace reconstruction from one log source. | N systems to diagnose with potentially inconsistent logging formats. Correlation requires stitching N independent log streams. |
| **Security incident containment** | Revoke one service identity or rotate one token to isolate BFA from all upstreams. | Must identify and contain each independently configured endpoint, token, and IAM binding. Containment time scales with N. |

### Negative / Trade-offs
- BFA becomes a shared platform dependency.
- Poor boundary discipline could collapse into a logic-heavy gateway.
- Internal service sprawl can still occur if adapter boundaries are not governed.
- **If adapters are deployed in-process rather than as separate services, Option C silently degrades into Option A** — sharing blast radius, release coupling, and cold-start penalties. See [Internal Adapter Packaging Model](#internal-adapter-packaging-model) for mandatory extraction timeline.

### Risks & Mitigations
- Risk: Gateway becomes a business-logic monolith
  - Mitigation: Strict layering: gateway for auth/routing only; domain logic in adapters
- Risk: Single point of failure
  - Mitigation: HA deployment, canary releases, SLO-based scaling and monitoring
- Risk: Token/auth policy drift if teams bypass gateway conventions
  - Mitigation: Explicit default-deny for direct CES-upstream patterns; exception governance ADR
- Risk: Glue path not operational
  - Mitigation: Keep Glue connectivity marked unverified until ADR-0101/0102 readiness checks pass
- Risk: In-process adapter packaging becomes permanent, collapsing Option C into Option A
  - Mitigation: Phase 1b in-process adapters must be extracted to separate Cloud Run services before Phase 2 (August 2026). CI conformance gate blocks Phase 2 deployment if adapters are still in-process. See [Internal Adapter Packaging Model](#internal-adapter-packaging-model).

---

## Why NOT Direct CES Python API Calls

> **For Python developers who want to embed API calls directly in CES tool `pythonFunction` callbacks, bypassing BFA.**

This section explains why direct API calls from CES callbacks are a **technical impossibility for banking use cases**, not merely a policy preference.

### CES platform constraints (hard blockers)

The following constraints are documented in the CES platform reference (`ces-agent/docs/cx-agent-studio/platform-reference.md`), caveats guide (`ces-agent/docs/cx-agent-studio/caveats.md`), and validated in the Round 2 review (`../reviews/ADR-0104-round-2-topology-review-2026-03-01.md` § 5):

| # | Constraint | Source | Why it blocks direct API calls |
|---|---|---|---|
| C1 | **Python callbacks must be "lightweight and deterministic"** | caveats.md § 3 | HTTP calls to banking APIs, token exchanges, and retry logic are neither lightweight nor deterministic. Callbacks are hot paths in every agent turn — heavy logic creates visible latency spikes. |
| C2 | **No custom library imports in sandboxed Python** | platform-reference.md § 5; Round 2 review Q-CES-1 | Cannot `import requests`, `import google.auth`, `import cryptography`, or any OAuth/HTTP client library. Standard library `urllib` is insufficient for mTLS, OAuth 2.0, or RFC 8693 token exchange. |
| C3 | **No secret management in callbacks** | caveats.md § 5; ADR-0108 | Callbacks cannot access Secret Manager, environment variables with credentials, or any form of credential store. API keys, client secrets, or OAuth credentials cannot be securely obtained. |
| C4 | **No DLP/PII filtering capability** | Round 2 review § 5.3 | CES has no native PII redaction. API responses containing account numbers, balances, or customer names would flow unfiltered into CES prompt context, violating GDPR data minimization. |
| C5 | **VPC-SC constrains tool/callback egress** | facts document § 6 | When VPC-SC is enabled, tools and callbacks cannot call arbitrary HTTP endpoints. Only endpoints registered in Service Directory via private network access are reachable. Direct Apigee or Glue calls would be blocked by the VPC-SC perimeter. |
| C6 | **No complex token exchange flows** | Round 2 review § 5.1 | OpenAPI toolsets reference endpoints and optionally Secret Manager entries. They cannot orchestrate multi-step flows like: obtain CIDP service token → call CIAM for RFC 8693 exchange → attach agentic token → call Apigee. This flow requires a backend orchestrator (BFA). |

### Concrete failure scenarios

**Scenario 1: Customer PII leaked into CES logs**
> Developer adds `pythonFunction` callback that calls Apigee's account balance API directly.
> Response contains `{ "accountNumber": "DE89370400440532013000", "balance": 12450.00, "holderName": "Maria Schmidt" }`.
> This raw response enters CES prompt context. CES BigQuery export logs the full model context including PII.
> **Result:** GDPR Article 32 violation. Full account number + customer name + balance in an unredacted log system. BFA's PII filter was bypassed entirely.

**Scenario 2: Secrets hardcoded in tool configs**
> Developer needs an OAuth client_secret to call CIDP for a service token.
> CES callbacks cannot access Secret Manager. Developer hardcodes the secret in the Python callback code.
> **Result:** Secret is visible in CES export packages, version control, and evaluation logs. Rotation requires redeploying the entire CES agent. ADR-0108 (service-identity-first) violated. Audit trail shows credential embedded in tool configuration.

**Scenario 3: Token exchange attempt fails silently**
> Developer tries RFC 8693 token exchange in a `before_tool_callback`.
> Callback cannot import `google.auth` or `requests`. Falls back to `urllib.request`.
> mTLS is required for CIDP — `urllib` cannot present client certificates.
> **Result:** Token exchange fails. Callback either crashes (500 error to user) or silently skips auth (unauthenticated API call rejected by Apigee). No graceful degradation path exists.

**Scenario 4: VPC-SC blocks direct upstream call**
> Developer configures an OpenAPI toolset pointing directly at Apigee Internal.
> VPC-SC perimeter blocks the egress because Apigee Internal is not registered in Service Directory for CES.
> **Result:** Tool call fails at the network level. The only registered Service Directory entry is BFA's internal LB — the single ingress this ADR mandates.

### The architectural principle being violated

ADR-0108 establishes **service-identity-first**: CES authenticates to BFA using its GCP service account identity token. BFA (the backend) holds all downstream credentials and performs token exchange. This is violated by any architecture that puts token acquisition, secret handling, or upstream authentication logic inside CES.

> **Bottom line for developers:** The constraint is the *platform*, not the policy. Even if this ADR were rescinded, CES's sandboxed Python environment physically cannot perform the token exchange, PII filtering, and authenticated API calls that banking operations require. BFA exists because CES *cannot do these things*.

Cross-references:
- ADR-CES-001: `ces-agent/docs/adr/ADR-CES-001-rest-api-vs-mcp-server.md` — REST API as primary CES integration
- ADR-J008 Gap Analysis: `ces-agent/docs/adr/ADR-J008-gap-analysis.md` — GAP-P0-1 through GAP-P0-3 (CES integration gaps)
- ADR-0108: `ADR-0108-tool-authentication-&-secrets-service-identity-first;-secret-manager-references-only-when-unavoidable.md`

---

## Business Case for Single Ingress

> **For architects and business stakeholders prioritizing speed-to-market and delivery velocity.**

### The false economy of "skip the gateway"

Teams naturally want to ship features fast. The fastest path *appears* to be: configure CES tools to call Apigee directly, skip the BFA layer entirely. This is an observed real-world behavior (see Gap G2 in `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md`).

The Round 2 review escalated this as an active risk: **teams are already attempting direct Apigee connections and embedding auth logic in CES tool configurations**, violating ADR-0104, ADR-0107, and ADR-0108.

**Why this is a false economy:**

| "Faster" shortcut | Hidden cost (per team) | Multiplied cost (7 agent teams) |
|---|---|---|
| Direct CES→Apigee tool config | Team must implement their own token validation, PDP call, PII redaction, correlation headers, error normalization, and fail-closed behavior | 7× independent implementations of the same security stack |
| Embed OAuth credentials in tool config | Per-team secret rotation, per-team audit policy, per-team IAM binding, per-team incident response runbook | 7× secret management overhead; 7× audit scope for compliance |
| Skip PII filtering | Per-team GDPR review, per-team DLP implementation, per-team data-leak remediation risk | 7× compliance review cycles; any single failure is a bank-wide incident |

### How Option C preserves team autonomy

The chosen architecture (single ingress + federated adapters) is explicitly designed to maximize delivery velocity *within* governance guardrails:

1. **Adapters are team-owned.** Each team fully controls their adapter's code, cache strategy, testing, and release cycle. BFA does not dictate adapter internals.
2. **BFA is a thin policy boundary, not a logic gateway.** BFA handles auth/routing/PII *only*. Domain logic stays in adapters. This is enforced by code review and CI conformance checks.
3. **Token Broker Service abstracts identity complexity.** Individual adapter teams do NOT need to understand CIAM + CIDP + EIDP token exchange mechanics. They call one internal API (`Token Broker`) and receive the appropriately-scoped token for their upstream. See `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md` § 8, Option B for the Token Broker design.
4. **Read-model adapters ship immediately.** Location, fees, and knowledge-base adapters operate against BFA-hosted curated read models with zero upstream dependency. These can ship in weeks 2-4 without waiting for Token Broker or Interconnect.
5. **Shared middleware eliminates boilerplate.** Correlation header injection, error normalization, and Token Broker client are distributed as a shared library/template. Teams add domain logic, not platform plumbing.

### Quantified governance savings

| Governance dimension | Without BFA (N direct paths) | With BFA (single ingress) |
|---|---|---|
| IAM bindings to manage | N (one per CES tool endpoint) | 1 |
| Service Directory registrations | N | 1 |
| Tool configurations in CES | N (each with own auth/endpoint) | 1 (uniform BFA config) |
| Secret Manager entries | Up to 2N (client ID + secret per team) | Centralized in BFA/Token Broker |
| Audit log streams to correlate | N independent streams | 1 unified stream with correlation IDs |
| PII redaction implementations | N (each team rolls their own) | 1 (BFA edge PEP) |
| PDP integration points | N | 1 |
| Compliance review scope | N separate systems | 1 system boundary |

At N=7 agent teams, this is the difference between ~50 governance items and ~10.

---

## Regulatory and Audit Requirements

> **For security, compliance, and risk teams requiring audit trails, policy enforcement, and regulatory controls.**

### Compliance framework mapping

| Framework | Specific control | Architecture requirement | How ADR-0104 satisfies it |
|---|---|---|---|
| **GDPR Art. 32** | Pseudonymization and data minimization | PII must be minimized before entering conversational AI context | BFA's edge PEP applies field-level redaction (account number masking, name truncation) before responses reach CES. Without BFA, raw PII flows into CES prompt context and BigQuery export logs. |
| **GDPR Art. 35** | Data Protection Impact Assessment | High-risk processing must be assessable | Single BFA boundary = one DPIA scope. N direct paths = N DPIAs. |
| **PCI-DSS 4.0 Req. 3** | Protect stored account data | Primary Account Numbers (PANs) must be rendered unreadable | BFA masks PANs in all responses to CES. CES never sees unmasked card numbers. Adapters may handle full PANs internally for processing but never return them through BFA. |
| **PCI-DSS 4.0 Req. 7** | Restrict access by business need-to-know | System components must enforce least-privilege access | PDP at BFA evaluates (subject, action, resource, context) and returns permit/deny + obligations. Adapters receive only permitted data. |
| **SOC 2 CC6.1** | Logical access controls | Access decisions must be centralized, auditable, and enforceable | BFA PEP logs every access decision (permit/deny, subject, resource, obligations applied). Single audit stream enables SOC 2 evidence collection from one system. |
| **SOC 2 CC7.2** | Monitoring for anomalies | Anomalous access patterns must be detectable | BFA-level observability enables rate analysis, pattern detection, and anomaly alerting across all domains from one monitoring point. N separate endpoints require N monitoring configurations. |
| **EBA ICT Risk Mgmt** | Operational resilience controls | Critical system access must have defined recovery procedures | One BFA gateway = one failure mode with one recovery runbook. N endpoints = N recovery runbooks, each potentially different. |

### Why distributed PEP enforcement creates audit gaps

Option B (many CES-exposed endpoints) distributes policy enforcement across N independent implementations:

1. **No guarantee of consistent PDP integration.** Team A might call the AuthZ PDP. Team B might implement a local RBAC check. Team C might skip authorization for "read-only" operations. A centralized compliance audit cannot verify consistency without examining N codebases.
2. **No uniform PII handling.** Without a single redaction boundary, each team defines their own "PII" scope. One team masks account numbers; another returns full IBANs. The compliance officer cannot Assert uniform data minimization.
3. **Split audit trails.** N separate log formats, N separate correlation strategies, N separate retention policies. Constructing an end-to-end audit trail for a single customer interaction requires correlating across N systems.
4. **Policy update lag.** A new compliance rule (e.g., "mask all IBANs in conversational responses") must be implemented and deployed by N teams independently. With BFA, it is one change in the edge PEP, deployed once.

ADR-0107 establishes the layered PEP model: **BFA is primary PEP** (all enforcement happens here), **Apigee is secondary** (defense-in-depth for Apigee-routed calls). This layering is architecturally enforceable only when all traffic flows through BFA first.

---

## Evidence from Round 2 Review

> **Findings from `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md` that validate and strengthen this ADR.**

### Gap G2: Team direct-connect behavior (proof of the problem this ADR solves)

The Round 2 review (2026-03-01) escalated a critical observation:

> *"Some agent teams are actively attempting to: (1) Connect CES tools directly to Apigee, bypassing BFA. (2) Generate auth/authz tokens inside CES tool configurations."*

This is exactly the architecture anti-pattern ADR-0104 prohibits. The root cause: **BFA was not yet operational**, creating a vacuum that teams filled with shortcuts. These shortcuts violate:
- **ADR-0104**: No direct CES-to-upstream topology
- **ADR-0108**: Service-identity-first; no long-lived secrets in CES tool configs
- **ADR-0107**: BFA is primary PEP; direct connections bypass PDP enforcement

**Resolution:** Accelerate BFA to provide a working path (Phase 1a). Do not legitimize direct connections as a permanent pattern. Exception lanes require formal governance approval (see Option C analysis in the Round 2 review).

### CES platform constraints (§ 5.3) — why CES cannot handle tokens/secrets

The Round 2 review's CES constraint analysis independently validates ADR-0104 and ADR-0108:

| CES constraint | Architectural implication |
|---|---|
| CES cannot do complex token exchange | Token exchange MUST happen at BFA, not in CES tool configs |
| CES callbacks cannot safely manage secrets | BFA as single ingress protects CES from secret sprawl |
| CES has no native DLP/PII filtering | BFA must enforce PII minimization on responses returned to CES |
| `x-ces-session-context` leaks data into API calls | BFA must strip or redact session context before forwarding to upstreams |

### Recommendation: Option B with Token Broker Service

The Round 2 review recommends **Option B (Federated Adapters + Token Broker Service + BFA Edge PEP)** as the balanced solution that satisfies all stakeholders:

1. **Solves identity provider complexity:** Token Broker Service abstracts CIAM + EIDP + CIDP behind one internal API. Adapters never interact with identity providers directly.
2. **Preserves single ingress:** CES still sees only BFA. No ADR violations.
3. **Best latency potential:** Domain caches + token caching reduce redundant upstream and identity calls.
4. **Supports mixed stacks:** Token Broker is consumed via standard HTTP API, not language-specific SDKs.
5. **Best fit for team structure:** Independent delivery cycles map to domain-owned adapters.

See the full comparative evaluation matrix in `../reviews/ADR-0104-round-2-topology-review-2026-03-01.md` § 9.

---

## Architectural Concepts and Definitions

Use canonical definitions in `../terminology-glossary.md`. Core terms used by this ADR:
1. `Single ingress topology`
2. `BFA Gateway`
3. `Internal domain adapter`
4. `Federated adapter model`
5. `Exception lane`
6. `PEP` and `PDP`
7. `Curated read model`
8. `Primary source` and `Secondary/fallback`
9. `Fail-closed`

---

## Compliance and Alignment

This ADR is intended to be read with:
1. ADR-0101 for private connectivity requirements and CES ingress path.
2. ADR-0103 for independent Apigee/Glue routing principles.
3. ADR-0105 for runtime placement guidance (Cloud Run internal first).
4. ADR-0106 for token-minting and assurance strategy.
5. ADR-0107 for layered authorization enforcement model.
6. ADR-0108 for service-identity-first and secret-handling boundaries.

---

## Implementation and Governance Follow-ups

- [ ] Define stable public OpenAPI surface for BFA (paths per domain)
- [ ] Define internal adapter interface and error normalization contract
- [ ] Establish domain ownership and release process behind the gateway
- [ ] Keep diagrams aligned to CES -> BFA Gateway external ingress only
- [ ] Publish explicit direct-path exception policy and approval workflow
- [ ] Define cache/freshness policy by domain (especially balance handling)
- [ ] Confirm network readiness gates for Glue-dependent adapters (Interconnect/VPN/DNS/firewall/mTLS)

### Phase tags
- Phase 1 (January 1, 2026 to July 31, 2026):
  - single gateway ingress and read-first domain baseline
- Phase 2+ (August 1, 2026 onward):
  - controlled expansion of write-capable adapters with step-up enforcement

---

## Notes (Optional)

N/A

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-02-28 | Principal Cloud Architect (GCP) | Initial draft |
| 2026-02-28 | Codex | Expanded into full ADR with explicit constraints, scope, alternative linkage, and glossary alignment |
| 2026-03-01 | GitHub Copilot (Claude Opus 4.6) | Comprehensive enhancement: expanded Alternatives with multi-perspective trade-off analysis; added "Why NOT Direct CES Python API Calls" (with CES platform blockers and failure scenarios); added "Business Case for Single Ingress" (quantified governance savings, Token Broker, phased delivery); added "Regulatory and Audit Requirements" (GDPR, PCI-DSS, SOC 2, EBA mapping); added "Evidence from Round 2 Review" (Gap G2, CES constraints, Option B recommendation); expanded Consequences with delivery velocity, compliance, and incident response impact |
| 2026-03-01 | GitHub Copilot (Claude Opus 4.6) | Clarified adapter definition and responsibilities ("What is an internal domain adapter?" section with responsibility table and Appointments AG-004 example). Added "Internal Adapter Packaging Model" section: mandatory separate Cloud Run services for write-path adapters, packaging comparison table (separate vs in-process vs monolith), per-category recommendation, and anti-pattern warning for permanent in-process packaging. Strengthened Option C description, Negative/Trade-offs, and Risks with explicit degradation-to-Option-A warning. |
