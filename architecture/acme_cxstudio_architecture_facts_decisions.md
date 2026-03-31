# Acme Banking — CX Agent Studio Architecture Facts & Decisions Record

**Status:** Draft (Design)  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Scope:** CX Agent Studio tool execution in a **private GCP landing zone**, with **agents consuming data from both Apigee and Glue**, plus **ForgeRock CIAM** + **AuthZ PDP** enforcement.
**Updated by:** Codex on 2026-02-28 for P0 architecture alignment.

---

## 1) Facts and constraints

### 1.1 Non-negotiable facts (confirmed)

1. **Apigee and Glue do not communicate with each other.**  
   - Agents need data from **either or both side** depending on agent and data requirements.  
   - Any “composition” must happen **above** them (e.g., in BFA), not by expecting Apigee↔Glue integration.

2. **Apigee is GCP-based** and is treated as a cloud internal/external API platform.  
   - App is classified as **confidential** → prefer **Apigee Internal** connectivity posture (no internet exposure for internal clients).

3. **Glue is on‑prem WSO2 API Manager**, authoritative for many internal APIs some of which are core banking systems of record.

4. **Hybrid connectivity** exists: assume **Cloud Interconnect** is available; include **Cloud VPN fallback**.

5. **Identity**
   - Authentication is backed by **ForgeRock CIAM**.
   - Authorization decisions must be performed by an **AuthZ backend service (PDP)** and enforced consistently (PEP).
   - Token strategy is **not finalized**:
     - “Agentic token” minted by CIAM (new token type)
     - RFC8693 token exchange (baseline assumption)
     - Technical account acting on behalf of user (allowed but strictly governed)
   - **mTLS everywhere** is required.

6. **VPC Service Controls posture**
   - When VPC‑SC is enabled for CX Agent Studio, **tools/callbacks cannot call arbitrary HTTP endpoints**; use **Service Directory for private network access**.

7. **Tool secrets**
   - CX Agent Studio OpenAPI tools can reference **Secret Manager secret versions** for API keys/OAuth secrets.
   - Assumption stance: **don’t embed long-lived secrets in tool configs**; prefer service identity and keep secrets in backend + Secret Manager.

---

## 2) Architecture principles

1. **Separation of concerns**
   - CX Agent Studio = orchestration + intent routing + tool invocation
   - BFA = single controlled enterprise tool surface + validation + policy enforcement + routing
   - Apigee = product governance for subset of APIs productized there
   - Glue = on‑prem gateway for legacy/internal APIs

2. **No hidden coupling between Apigee and Glue**
   - Any “join/composition” happens in **BFA** (or SoR), never by assuming Apigee proxies to Glue (or vice versa).

3. **Confidential posture by default**
   - Private connectivity end-to-end; VPC‑SC perimeter; no public ingress to tool endpoints.

4. **Fail-closed security**
   - If token validation, step-up, PDP decision, or mTLS fails → deny and log.

---

## 3) Cross-document alignment anchors

- Canonical agent names/IDs/scope/source map:
  - `architecture/agent-registry.md`
- Omnichannel runtime topology and channel coverage:
  - `architecture/addendum-omnichannel-runtime-topology.md`
- Security and auth hardening phases:
  - `architecture/addendum-security-auth-phases.md`
- Canonical naming and phase terminology:
  - `architecture/terminology-glossary.md`

---

## 4) Target reference architecture

### 4.1 Component model (what runs where)

#### A) Google-managed
- **CX Agent Studio**
  - Root agent routes to domain agents.
  - Domain agents invoke tools via OpenAPI toolsets.

#### B) GCP landing zone (private)
- **Service Directory + Private Network Access**
  - Private reachability for tool endpoints under VPC‑SC.
- **Internal HTTP(S) Load Balancer**
  - Fronts the BFA Gateway privately (ILB VIP registered in Service Directory).
- **BFA Gateway (Cloud Run, internal ingress)**
  - Single entry point for tool calls.
  - Validates requests, applies guardrails, calls AuthZ PDP, routes to Apigee or Glue.
- **AuthZ PDP service (Cloud Run or GKE)**
  - Central policy evaluation.
- **ForgeRock CIAM** used by AuthZ PDP service for token introspection/exchange and other domain adapters as needed. **GCP cloud-hosted in a separate landing zone** (not on-prem). Reachable from BFA via cloud-to-cloud connectivity (mechanism TBD: PSC, VPC peering, or other).
- **CIDP (Cloud Identity Provider)** provides service-to-service tokens for Apigee-bound API calls. **GCP cloud-hosted in a separate landing zone** (not on-prem). Reachable from BFA via cloud-to-cloud connectivity.
- **EIDP (Enterprise Identity Provider)** provides service-to-service tokens for Glue-bound API calls. **On-prem** alongside Glue. Reachability from BFA depends on Interconnect/VPN (UNVERIFIED).
- **(Optional) Domain Adapters**
  - Separate Cloud Run services behind BFA for blast-radius isolation:
    - Banking adapter
    - Fees adapter
    - Appointments adapter
    - KB adapter
    - Cards adapter
- **Observability**
  - Cloud Logging + audit sinks (inside perimeter), tracing, dashboards, SIEM export as allowed.

#### C) GCP-based Apigee (internal preferred)
- **Apigee Internal**
  - Used for regulated/productized APIs.
  - BFA connects privately (no internet path).

#### D) On‑prem
- **Glue (WSO2 API Manager)**
- **EIDP (Enterprise Identity Provider)** — service-to-service tokens for Glue-bound calls
- **Core banking systems of record**

#### E) GCP Cloud (separate landing zones — NOT on-prem)
- **ForgeRock CIAM** — customer authn/authz, RFC 8693 token exchange
- **CIDP (Cloud Identity Provider)** — service-to-service tokens for Apigee-bound calls

---

### 4.2 Network & trust boundaries (ASCII)

```
User (Voice)
  → CX Agent Studio (Google-managed)

CX Agent Studio (inside VPC‑SC posture)
  → Service Directory Private Network Access
  → Internal LB VIP
  → BFA Gateway (Cloud Run internal)

BFA Gateway (PEP boundary)
  → AuthZ PDP (mTLS)
  → ForgeRock CIAM (mTLS, cloud-to-cloud) for token exchange/introspection
  → CIDP (mTLS, cloud-to-cloud) for service-to-service tokens (Apigee-bound)
  → EIDP (mTLS, via Interconnect to on-prem) for service-to-service tokens (Glue-bound)
  → Apigee Internal/External (private connectivity)
  → Glue (via Interconnect to on‑prem, mTLS)

Note: CIAM and CIDP are GCP cloud-hosted (separate landing zones) — no Interconnect dependency.
Only EIDP, Glue, and Core Banking are on-prem and require Interconnect/VPN.

No Apigee ↔ Glue communication path is assumed or required.
```

---

## 5) Data source routing model (Apigee and Glue are both first-class)

### 5.1 Source-of-truth rules

- Canonical source of truth for agent names, IDs, scope, phase, and source routing:
  - `architecture/agent-registry.md`
- Each **agent/domain** has one or more declared **primary data sources**:
  - Apigee-backed APIs
  - Glue-backed APIs
  - Curated internal datasets/read models where live calling is not ideal
- BFA supports **two independent upstream connectors**:
  - Upstream A: Apigee
  - Upstream B: Glue
- Routing is decided by:
  1) agent/domain
  2) data classification (regulated/sensitive vs informational)
  3) latency/SLO
  4) availability and fallback policy

### 5.2 Agent -> data source matrix (initial)

| Arch ID | CX Subagent | Typical data | Primary source | Secondary / fallback | Notes |
|---|---|---|---|---|---|
| AG-001 | ~~policy_guardrails_agent~~ | policy rules | BFA internal policy services | N/A | **Removed from topology diagram** — guardrails absorbed into BFA Gateway Edge PEP + Response PEP |
| AG-002 | human_handover_agent | call-center routing | BFA internal handover services | N/A | context packaging + audit |
| AG-003 | location_services_agent | branches/ATMs | BFA location read model (curated `branches.json`) | Glue location API | non-sensitive; deterministic read model first |
| AG-004 | appointment_context_agent | appointments | Apigee or Glue | other side | decide per system ownership |
| AG-005 | fee_schedule_agent | fee tables | BFA fees read model (curated) | Glue and/or BQ snapshot | deterministic answers with effective date |
| AG-006 | knowledge_compiler_agent | FAQs/procedures | curated KB index | Glue (read-only) | grounded answers only |
| AG-007 | banking_operations_agent | balances/transactions | Apigee | Glue (if partial) | sensitive -> AuthZ + step-up |
| AG-008 | credit_card_context_agent | cards/limits | Apigee | Glue (rare) | step-up almost always |
| AG-009 | product_information_agent | product catalog | Apigee | KB snapshot | non-sensitive; no hallucinations |
| AG-010 | mobile_app_assistance_agent | how-to | curated KB | N/A | mostly static |
| AG-011 | non_banking_services_agent | partner offers | Apigee | curated KB | classify carefully |
| AG-012 | personal_finance_agent | spend analysis | Apigee + analytics read model | analytics snapshot | separate policy set |

**Decision note:** “secondary/fallback” means “call the other system if it has data”, not “route through the first system”; all CES ingress remains CES -> BFA Gateway.

---

## 6) Identity and authorization model (ForgeRock CIAM + AuthZ PDP)

### 6.1 Definitions

- **Service identity**: what CX Agent Studio uses to call BFA tools (machine identity).
- **End-user identity**: the customer’s identity + authentication assurance.
- **ACR**: Authentication Context Class Reference — the *assurance level* of the authentication event (used for step-up decisions).

### 6.2 Chosen baseline approach (decision summary)

- **Default consumer flow**: RFC8693-style **token exchange** performed by BFA to mint a short-lived **agentic token** bound to:
  - audience (BFA/Apigee/Glue)
  - scopes
  - auth assurance (`acr`)
  - short TTL
- **Employee-assisted / contact-center**: technical account on-behalf-of allowed only with explicit mode + stronger audit.

---

## 7) Tool connectivity & secrets posture (CX Agent Studio)

### 7.1 Private tool connectivity
- Under VPC‑SC, tool/callback egress is constrained → use **Service Directory private network access**.
- Therefore, BFA must be reachable via:
  - Internal LB VIP registered in Service Directory
  - Strong IAM/service identity checks at Cloud Run

### 7.2 Secrets
- Prefer **service identity** auth from CES → BFA.
- If a tool must call an external system directly (rare), use **Secret Manager secret-version references** and tight RBAC + audit on tool config changes.

---

## 8) Phase scope alignment (resolved)

| Topic | Decision | Phase/date tagging |
|---|---|---|
| NPS capability timing | NPS is not in the baseline Phase 1 external MVP execution scope; enabled in Phase 2 rollout. | Phase 2 (August 1, 2026 to December 31, 2026) |
| Appointment management | Appointment context services activate in controlled rollout after Phase 1 read-first baseline. | Phase 2 (August 1, 2026 to December 31, 2026) |
| Write operation boundary | General write execution remains gated behind step-up and stronger auth controls. | Phase 2+ with ACR/step-up enforcement |
| Phase 1 MVP boundary | Phase 1 emphasizes read-first assistance, routing, and guarded handover. | January 1, 2026 to July 31, 2026 |

---

## 9) Decisions register (decided vs open)

### D1 — Apigee/Glue relationship
- **Decision:** Treat Apigee and Glue as independent upstreams; no integration assumed.
- **Consequence:** BFA must implement both connectors and per-domain routing.

### D2 — Tool endpoint surface
- **Decision:** Single BFA Gateway tool surface for CX Agent Studio.

### D3 — Runtime
- **Decision:** Cloud Run internal ingress for BFA + adapters; GKE only where Kubernetes is required.

### D4 — Apigee connectivity for confidential app
- **Decision:** Prefer internal/private Apigee access (no internet path).

### D5 — Token strategy
- **Decision:** CIAM token exchange to mint agentic token (baseline); technical on-behalf-of reserved for employee-assisted.

### D6 — Step-up policy (ACR)
- **Decision:** Cards/transactions/identity-proof intents require higher ACR; PDP returns “step_up_required” obligation.

### D7 — mTLS
- **Decision:** mTLS required on all controllable hops.

### D8 — Omnichannel topology
- **Decision:** All channels terminate at CX Agent Studio and use a single private ingress path to BFA Gateway.

### D9 — Security hardening phases
- **Decision:** Use phased auth hardening from MVP baseline to strict production token validation, with mandatory audit/CMEK controls.

### D10 — Naming standard
- **Decision:** Canonical runtime names use snake_case and are governed by the architecture glossary + registry.

---

## 10) Risk register (top items)

1) Policy divergence between Apigee enforcement and BFA/AuthZ PDP  
   - Mitigation: layered but aligned policies; shared attribute dictionary; regression tests.

2) Routing mistakes (agent calls wrong upstream)  
   - Mitigation: explicit agent→source matrix; contract tests; deny-by-default.

3) Secret/config drift in tools  
   - Mitigation: service identity first; tool change control; Secret Manager references only; audit alerts.

4) mTLS operational complexity  
   - Mitigation: managed PKI/private CA, automated rotation, strict expiry alerting.

---

## 11) Implementation checklist

### Networking
- [ ] Confirm Interconnect attachments, regions, routing model (BGP), DNS split-horizon plan
- [ ] Define Service Directory namespaces + service naming conventions
- [ ] Implement ILB + Service Directory registration for BFA ingress (private)
- [ ] Define firewall policy for BFA egress to Apigee and to on‑prem

### Apigee (confidential posture)
- [ ] Confirm Apigee internal/private connectivity pattern for the confidential app
- [ ] Define which agent domains use Apigee (regulated APIs) vs Glue (legacy/internal)

### Glue (on-prem)
- [ ] Inventory Glue API products used by each agent and classify sensitivity

### Identity (ForgeRock CIAM)
- [ ] Confirm CIAM capability for RFC8693 token exchange (or equivalent)
- [ ] Define ACR levels and which intents require step-up
- [ ] Define employee-assisted mode and technical on-behalf-of guardrails

### Authorization (AuthZ PDP)
- [ ] Define PDP API contract (subject/action/resource/context + obligations)
- [ ] Define fail-closed behaviors and user-facing refusal language

### mTLS & keys
- [ ] Define CA approach and rotation automation
- [ ] Define mutual auth requirements per hop; prove with connectivity tests

### Tools & secrets
- [ ] Decide which tools rely purely on service identity vs Secret Manager references
- [ ] Put tool configuration under change control with audit alerts

### Observability & audit
- [ ] Standardize correlation headers (request_id/session_id/agent_id/tool_id)
- [ ] Ensure logs/datasets remain inside VPC‑SC perimeter where required
- [ ] Enable required CES Data Access audit logging and retention workflows
- [ ] Validate CMEK region alignment and key lifecycle controls for regulated environments

---

## References (URLs provided in code blocks for portability)

```text
Customer Engagement Suite - VPC Service Controls (tools/callbacks constraints + Service Directory PNA):
https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/vpc-service-controls

CX Agent Studio OpenAPI tool authentication + Secret Manager references:
https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api

Apigee: Shared VPCs:
https://docs.cloud.google.com/apigee/docs/api-platform/system-administration/shared-vpcs

Apigee: Northbound networking via Private Service Connect:
https://docs.cloud.google.com/apigee/docs/api-platform/system-administration/northbound-networking-psc
```
