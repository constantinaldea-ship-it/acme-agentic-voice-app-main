# ADR-0104 Review: Backend Topology and Diverging Consumption Options

**Status:** Draft for architecture alignment  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Created by:** Codex on 2026-02-28

---

## 1) Purpose

This document reviews `ADR-0104` against current delivery realities and captures diverging architecture options for agent data consumption from Apigee and Glue.

Scope:
- Topology fit to independent agent teams
- Glue network feasibility under unverified connectivity
- Token/authz governance in line with ADR-0106, ADR-0107, ADR-0108
- Voice-latency implications (target: p95 under 3 seconds)

Related decisions:
- `architecture/adrs/ADR-0101-cx-agent-studio-private-tool-connectivity-vpc-sc-service-directory-pna-internal-lb.md`
- `architecture/adrs/ADR-0103-apigee-and-glue-boundary-independent-upstreams;-per-domain-routing-in-bfa.md`
- `architecture/adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md`
- `architecture/adrs/ADR-0105-runtime-selection-cloud-run-internal-for-bfa-adapters;-kubernetes-only-where-required.md`
- `architecture/adrs/second-priority/ADR-0106-ciam-token-strategy-agentic-token-via-rfc8693-token-exchange-default-;-technical-on-behalf-of-for-employee-assisted-only.md`
- `architecture/adrs/second-priority/ADR-0107-authz-pdp-integration-&-enforcement-layered-pep-bfa-primary;-apigee-secondary-controls.md`
- `architecture/adrs/ADR-0108-tool-authentication-&-secrets-service-identity-first;-secret-manager-references-only-when-unavoidable.md`

---

## 2) Current ADR-0104 stance (summary)

ADR-0104 establishes:
1. Single externally visible tool surface: BFA Gateway.
2. Domain adapters remain internal for isolation and team ownership.
3. CES does not call adapters directly.
4. Internal adapters may call Apigee, Glue, or BFA-curated read models via agent-registry routing.

This is directly aligned with:
- ADR-0101 private ingress via Service Directory PNA + ILB.
- ADR-0103 independent Apigee and Glue upstream model.
- ADR-0107 layered enforcement with BFA as primary PEP.
- ADR-0108 service-identity-first and backend secret custody.

---

## 3) Confirmed/Provided constraints and context

| Topic | Current input |
|---|---|
| A) Connectivity to Glue | Unverified for all points (treat as unknown, no assumptions). |
| B) SLA | Voice hotline requires low friction; target p95 under 3 seconds. |
| B2/B3 | Sensitive-flow budget split and timeout behavior still open. |
| C) Caching | Caching acceptable for most domains except balances; balances may need hot preload pattern if justified. |
| D) Token lifecycle | No implementation owner yet; conceptual direction is CIAM minting via CIAM adapter/auth server pattern. |
| E) Team skills | Mixed stacks: Java, Python, Node.js. |
| E2/E3/E4 | Ownership model and on-call are TBD; SDLC + Terraform Enterprise are available. |
| F) Existing pilots | No precedent for direct CES->Apigee, no BFA->Glue pilot, no latency baseline, no token-config findings yet. |

---

## 4) Gap and ambiguity analysis (ADR-0104 vs team reality)

### Gap G1: Connectivity certainty gap
ADR-0104 assumes adapters can call Glue, but current network status is unverified. This is a deployment blocker, not a conceptual blocker.

### Gap G2: Token custody ambiguity
Some teams expect CES-level token generation/direct upstream usage, which conflicts with ADR-0108 and weakens ADR-0107 consistency.

### Gap G3: Caching policy incompleteness
ADR-0104 references read models but does not define TTL, invalidation, consistency classes, or balance-specific hot data strategy.

### Gap G4: Team operating model
ADR-0104 keeps domain ownership, but does not define release contracts, conformance tests, or ownership split for BFA shared edge vs domain adapters.

### Gap G5: Exception governance
No explicit rulebook for when direct upstream access is allowed (if ever), causing interpretation drift across teams.

---

## 5) Clarifying questions log (grouped)

These were requested to avoid hidden assumptions. Current response status is tracked.

### A) Network connectivity (GCP to Glue)
1. Is Cloud Interconnect active for BFA runtime VPC? `Status: OPEN (unverified)`
2. Is VPN fallback configured and tested? `Status: OPEN (unverified)`
3. Is private DNS resolution to Glue available from runtime? `Status: OPEN (unverified)`
4. Are firewall and routing paths validated bidirectionally? `Status: OPEN (unverified)`
5. Is mTLS trust distribution/rotation operational for Glue? `Status: OPEN (unverified)`

### B) Latency and timeout budget
1. What are p50/p95/p99 tool-call targets? `Status: PARTIAL (p95 under 3 seconds provided; p50/p99 open)`
2. Are sensitive operations given separate budgets? `Status: OPEN`
3. Fallback behavior on upstream latency/failure? `Status: OPEN`

### C) Caching requirements
1. Which domains can serve stale data? `Status: PARTIAL (all except balances)`
2. TTL by domain? `Status: OPEN`
3. Invalidation strategy (time, event, mixed)? `Status: OPEN`
4. Consistency model per domain? `Status: OPEN`
5. Cache ownership (gateway, adapter, dual-tier)? `Status: OPEN`

### D) Token lifecycle
1. Who mints downstream tokens? `Status: PARTIAL (conceptually CIAM via adapter/auth-server)`
2. Who validates per hop? `Status: OPEN`
3. TTL/rotation/replay controls? `Status: OPEN`
4. Storage policy (ephemeral vs persisted)? `Status: OPEN`
5. CES config token handling allowed? `Status: OPEN (decision needed; ADR-0108 suggests avoid)`

### E) Team operating model
1. Team language stacks? `Status: ANSWERED (Java/Python/Node.js)`
2. Shared BFA platform ownership? `Status: OPEN`
3. IaC ownership maturity? `Status: PARTIAL (SDLC + Terraform Enterprise in place)`
4. On-call model? `Status: OPEN`

### F) Precedent and pilots
1. Direct CES->Apigee pilot exists? `Status: ANSWERED (No)`
2. BFA->Glue pilot exists? `Status: ANSWERED (No)`
3. Measured latency baseline exists? `Status: ANSWERED (No)`
4. Security findings on CES token configs? `Status: ANSWERED (Not yet)`

---

## 6) Connectivity view (unverified-by-default)

Diagram: `architecture/diagrams/adr-0104-connectivity-unverified.mmd`

This view explicitly marks GCP-to-on-prem dependencies as unverified until validated:
- Interconnect
- VPN fallback
- DNS split-horizon
- Firewall route policy
- mTLS trust chain

```mermaid
flowchart LR
  subgraph User[End User]
    U((Voice Caller))
  end

  subgraph GoogleManaged[Google-managed]
    CES[CX Agent Studio]
  end

  subgraph GCP[GCP Landing Zone]
    SD[Service Directory PNA]
    ILB[Internal HTTP(S) Load Balancer]
    BFA[BFA Gateway]
    ADP[Domain Adapters]
    PDP[AuthZ PDP]
  end

  subgraph OnPrem[On-Premises]
    GLUE[Glue WSO2 API Manager]
    CIAM[ForgeRock CIAM]
  end

  subgraph ApigeeProj[Apigee Project]
    AP[Apigee Internal]
  end

  subgraph Hybrid[Hybrid Network Controls]
    IC[Cloud Interconnect]
    VPN[Cloud VPN Fallback]
    DNS[Private DNS Split-Horizon]
    FW[Firewall + Routing Policy]
    MTLS[mTLS Trust Chain]
  end

  U --> CES
  CES --> SD --> ILB --> BFA --> ADP
  BFA --> PDP
  ADP --> AP
  ADP -.->|UNVERIFIED| GLUE
  BFA -.->|UNVERIFIED| CIAM
  ADP -.->|depends on| IC
  ADP -.->|fallback| VPN
  ADP -.->|depends on| DNS
  ADP -.->|depends on| FW
  ADP -.->|depends on| MTLS
```

---

## 7) Diverging architecture options (overview)

| Option | Core idea | Fit summary |
|---|---|---|
| Option A | Strict single BFA gateway; all CES traffic through BFA and internal adapters | Highest governance/security consistency; moderate DX constraints |
| Option B | Single ingress retained; federated internal domain adapters with domain cache planes | Strong balance of autonomy + policy control; higher internal platform complexity |
| Option C | Controlled direct Apigee exception lane; BFA remains default path | Lower hop for selected reads but highest governance/security drift risk |

Deep dives:
- Option A: `architecture/reviews/ADR-0104-option-A-strict-single-bfa.md`
- Option B: `architecture/reviews/ADR-0104-option-B-federated-adapters.md`
- Option C: `architecture/reviews/ADR-0104-option-C-controlled-apigee-exceptions.md`

---

## 8) Comparative analysis

| Criterion | Option A | Option B | Option C |
|---|---|---|---|
| Security (ADR-0106/0107/0108 + PII/DLP) | Strongest central token/PEP governance | Strong if BFA remains primary PEP and adapter conformance is enforced | Weakest; increases token/config and policy split risk |
| Network feasibility (ADR-0101/0103) | Good once BFA->Glue is proven | Best flexibility for hybrid path tuning behind BFA | Does not solve Glue access; only optimizes some Apigee paths |
| Latency/SLA | Good with min instances + read models + cache policy | Best potential with domain-tuned caches and specialized adapters | Best hop count for narrow exceptions, but fragmented behavior |
| Governance and audit traceability (ADR-0107) | Highest uniformity, easiest audit | High with strict standards and shared controls | Lowest consistency, highest exception burden |
| Developer experience | Moderate (central contracts) | High (domain autonomy with shared edge) | High short-term, low long-term due policy drift/operations debt |

---

## 9) Recommendation

Recommended direction:
1. Keep ADR-0104 as canonical ingress: CES -> BFA only.
2. Adopt Option B execution model behind that ingress:
   - Team-owned internal adapters
   - Domain-specific cache/read-model strategy
   - Shared conformance suite for authz headers, redaction, and fail-closed behavior
3. Keep Option C as explicit exception-only path with formal security/governance approval and ADR addendum.

Rationale:
- Preserves ADR-0101/0103/0107/0108 control intent.
- Supports mixed team stacks and independent delivery cycles.
- Improves p95 latency posture through domain-level optimization without exposing CES to token/secrets complexity.

---

## 10) Follow-up ADRs and artifacts needed

1. `ADR-01xx`: Canonical data-consumption pattern and exception governance (default-deny direct CES->upstream).
2. `ADR-01xx`: Voice-SLA caching and freshness model (TTL, invalidation, stale-response policy by domain).
3. `ADR-01xx`: Token lifecycle and custody contract (mint, validate, propagate, expire, audit).
4. `ADR-0104` addendum: Adapter operating model (ownership, API compatibility, release and rollback standards).
5. Network readiness checklist artifact tied to ADR-0101/0102 before any Glue-dependent go-live.

---

## 11) Changelog

| Date | Author | Change |
|---|---|---|
| 2026-02-28 | Codex | Initial review with diverging options, user-provided clarifications, and recommendation |
