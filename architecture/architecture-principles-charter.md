# Architecture Principles Charter — CX Agent Studio Banking Platform

**Status:** Draft for workshop ratification  
**Date:** 2026-03-02  
**Owner:** Principal Cloud Architect (GCP)  
 **Scope:** CX Agent Studio (CES) + Mediation Layer + Domain Adapters + Apigee/Glue integration  
**Ratification:** Requires workshop consensus (fist-of-five ≥ 4 average)

---

## Governing Principle

> **"Agents never access domain systems directly. They interact only through controlled domain capabilities exposed via a policy-enforced boundary."**

This single sentence governs every other principle below. If an architecture decision violates this, it requires an explicit ADR with risk acceptance.

**Concrete meaning:**

| Agents DO | Agents DO NOT |
|-----------|---------------|
| Call declared capability contracts | Construct upstream URLs |
| Express business intent ("check balance") | Decide Apigee vs. Glue routing |
| Receive normalized, redacted responses | See raw upstream response formats |
| Authenticate with service identity to the mediation boundary | Hold long-lived secrets or OAuth credentials |
| Trust the platform to enforce policy | Implement their own PII filtering or authorization |

**Why this matters for banking agents specifically:**

Agents are probabilistic orchestrators — subject to prompt evolution, tool expansion, and eventual multi-agent autonomy. If agents are allowed to "know" about upstream systems, the organization loses: policy consistency, audit control, routing discipline, and future refactoring flexibility. In a regulated banking environment, this translates directly to compliance risk.

---

## How to Use This Document

### For ADR Authors

Every ADR must state:
1. **Which principles this ADR supports** (with brief justification)
2. **Which principles this ADR weakens** (with explicit risk acceptance and compensating controls)

### For Workshop Participants

Use the evaluation table in § 8 to score topology options. Each principle has a weight (High / Medium). For each option, mark:
- ✅ Fully satisfies (score: 3)
- ⚠️ Partially satisfies (score: 1)
- ❌ Violates or does not satisfy (score: 0)

### For Engineering Teams

Before designing an adapter, integration, or tool configuration, check your design against the principles checklist in § 7. If any principle is violated, escalate to the Principal Architect for an exception review.

---

## Principle Categories

| Category | What it Protects | # of Principles |
|----------|-----------------|-----------------|
| **A. Security & Compliance** | Customer data, regulatory posture, access control | 8 |
| **B. Domain & Integration** | Upstream independence, contract stability, domain isolation | 5 |
| **C. Resilience & Blast Radius** | Availability, fault containment, operational safety | 4 |
| **D. Data & AI Grounding** | Data quality, PII minimization, response trustworthiness | 4 |
| **E. Operability & Delivery** | Deployment safety, observability, team velocity | 4 |

**Total: 25 principles**  
**Compact charter (top 10 for quick reference):** See § 6.

---

## A) Security & Compliance Principles

### A1 — Confidential-by-Default

> All components default to private networking. Public exposure requires an explicit ADR with risk mitigations and security sign-off.

**Rationale:** The banking platform operates under a confidential classification. VPC-SC perimeter, private Service Directory access, and internal-only ingress are baseline, not optional hardening.

**Architectural consequences:**
- CES tool endpoints are reachable only via Service Directory Private Network Access + Internal Load Balancer
- Apigee Internal connectivity (no internet path) for all agent-to-API traffic
- Glue access via Cloud Interconnect / VPN only (no public endpoints)

**Evaluation question:** *"Does this option expose any component to public internet by default?"*

---

### A2 — Policy Before Data

> No data access or action occurs before identity validation, authorization decision, and obligations enforcement.

**Rationale:** In banking, "check first, then serve" is a regulatory requirement (PCI-DSS Req. 7, SOC 2 CC6.1). The alternative — "fetch data, then check if the caller was authorized" — creates a window where unauthorized data exists in memory and logs.

**Architectural consequences:**
- Mediation Layer applies Edge PEP (identity validation + PDP call + obligation enforcement) before routing to any domain adapter
- No adapter fetches upstream data without a valid authorization decision from PDP
- Fail-closed: if PDP is unreachable, deny and log — never serve data optimistically

**Evaluation question:** *"Does this option guarantee that authorization runs before any upstream call?"*

---

### A3 — Least Privilege Everywhere

> IAM bindings, token scopes, network rules, and secrets access are all minimal and per-capability.

**Rationale:** A broad IAM binding or over-scoped token means a compromised component can access unrelated domains. In a multi-agent system with 12+ agents, scope creep is a systemic risk.

**Architectural consequences:**
- CES authenticates to the mediation endpoint with a single-purpose service identity (no cross-project permissions)
- Token Broker issues tokens scoped to specific upstreams and capabilities (audience-bound, short-lived)
- Each domain adapter has its own service account with access only to its upstream
- VPC-SC ingress/egress policies are per-service, not per-project

**Evaluation question:** *"Does this option require broad IAM permissions or over-scoped tokens?"*

---

### A4 — Fail Closed for Security-Critical Dependencies

> If CIAM validation, PDP authorization, mTLS verification, or token exchange fails → deny the request and log. No "best effort allow."

**Rationale:** "Best effort" security in banking is indistinguishable from no security during an incident. A degraded PDP that returns "allow" by default is worse than downtime.

**Architectural consequences:**
- Mediation Layer returns 503 (not 200 with degraded data) when PDP is unreachable
- Token exchange failure → request denied, not forwarded without a token
- mTLS handshake failure → connection refused, not downgraded to plaintext

**Evaluation question:** *"What happens in this option when the PDP is down for 5 minutes?"*

---

### A5 — mTLS for All Controllable Hops

> Every service-to-service hop under our control uses mutual TLS with strong identity verification.

**Rationale:** In a zero-trust model, network position is not identity. mTLS ensures that both sides of every connection cryptographically prove their identity. Required for: mediation layer ↔ adapters, mediation layer ↔ PDP, mediation layer ↔ Token Broker, adapters ↔ Apigee, adapters ↔ Glue.

**Architectural consequences:**
- Managed PKI / private CA with automated certificate rotation
- Certificate expiry alerting and rotation runbooks
- Glue connectivity requires mTLS trust distribution from on-prem CA

**Evaluation question:** *"Does this option introduce any hop that cannot support mTLS?"*

---

### A6 — Secrets Never Live in Orchestration

> CX Agent Studio tools must not hold long-lived secrets. All credential management happens in backend layers (Secret Manager, workload identity, Token Broker).

**Rationale:** CES Python callbacks cannot safely manage secrets (no Secret Manager API access, no custom crypto libraries). Hardcoded secrets in tool configurations are visible in export packages, version history, and evaluation logs. This is not a policy preference — it is a CES platform constraint (see ADR-0108, CES caveats.md § 5).

**Architectural consequences:**
- CES authenticates to the mediation boundary via GCP service identity token (workload identity federation) — no OAuth client secrets
- Token exchange (CIAM, CIDP, EIDP) happens at mediation layer / Token Broker, never in CES callbacks
- If a Secret Manager reference is unavoidable (Tier 1 exception lane API key), it requires security sign-off and 90-day review

**Evaluation question:** *"Does this option require CES to hold, retrieve, or manage any credential?"*

---

### A7 — Separation of Duties

> Tool configuration admins ≠ secret admins ≠ runtime deployment admins. Changes to security-critical configurations require audit trail and approval workflow.

**Rationale:** SOC 2 CC6.1 requires logical access controls with separation of duties. A single team that configures tools, manages secrets, and deploys code has an uncontrolled blast radius.

**Architectural consequences:**
- Tool endpoint registration in Service Directory requires infrastructure team approval
- Secret Manager entries use separate IAM from deployment service accounts
- Adapter deployments go through CI conformance gates, not manual pushes

**Evaluation question:** *"Does this option concentrate more than two of: tool config, secret management, deployment in one role?"*

---

### A8 — Auditability Is a First-Class Feature

> Every sensitive request is traceable end-to-end with immutable logs, correlation IDs, and a unified audit stream.

**Rationale:** In a regulated banking environment, "can we prove what happened?" is not optional. A compliance officer must reconstruct any customer interaction from a single audit stream — not by correlating N independent log formats across N services.

**Architectural consequences:**
- Standard correlation headers propagated on every hop: `x-request-id`, `x-session-id`, `x-agent-id`, `x-tool-id`
- Mediation boundary logs every access decision (permit/deny, subject, resource, obligations applied)
- All logs stay within VPC-SC perimeter; SIEM export follows controlled channels
- Single audit stream from the mediation boundary (not N streams from N direct endpoints)

**Evaluation question:** *"Can a compliance officer reconstruct a full interaction trace from one log source?"*

---

## B) Domain & Integration Principles

### B1 — Capability-Oriented Access

> Agents call capabilities, not systems. They never "know" whether the upstream is Apigee, Glue, a read model, or a combination. The platform routes.

**Rationale:** If an agent is designed to call "the Apigee balance endpoint," it cannot be rerouted to a Glue fallback, a cached read model, or a future consolidated API without changing the agent. Capability abstraction is what makes the architecture evolvable.

**Architectural consequences:**
- Tool contracts are defined as business capabilities: `balance-inquiry`, `fee-lookup`, `branch-search`
- Mediation layer maps capability requests to upstream routing decisions based on: agent-registry, data classification, availability, and policy
- Agent developers never configure upstream URLs — they configure mediation-layer tool endpoints

**Derived rules:**
1. **Domain Isolation Rule** — Agents never construct upstream URLs, decide routing, know schemas, or embed business logic
2. **Capability First, Data Source Second** — APIs are designed around intent (`POST /capabilities/balance-inquiry`), not systems (`GET /glue/accounts/{id}`)

**Evaluation question:** *"Does this option allow agent developers to remain unaware of which upstream serves their data?"*

---

### B2 — No Direct Agent-to-Upstream Coupling

> Agents never call Glue or Apigee directly. They call the mediation-layer tool surface. Period.

**Rationale:** This is the operational expression of the governing principle. Direct coupling means: agents must handle token exchange, PII filtering, error normalization, and policy enforcement independently. At N=7+ agent teams, this multiplies governance overhead by N and creates N independent audit targets.

**CES platform enforcement:** Even if this principle were relaxed, CES physically cannot perform the token exchange, PII filtering, and authenticated API calls that banking operations require (see ADR-0104 § "Why NOT Direct CES Python API Calls" — 6 hard platform blockers).

**Architectural consequences:**
- Single Service Directory entry pointing to the mediation layer Internal Load Balancer
- One IAM binding (CES service identity → mediation layer Cloud Run invoker)
- One tool configuration pattern across all agents

**Evaluation question:** *"Does this option create any CES→upstream path that bypasses the mediation boundary?"*

---

### B3 — Explicit Upstream Independence

> Apigee and Glue are independent upstreams. No design assumes they communicate with each other. Any composition happens above them — in the mediation layer or domain adapters.

**Rationale:** Apigee is GCP-based; Glue is on-prem WSO2. They have different identity models, different API contracts, different availability characteristics. Designs that assume Apigee proxies to Glue (or vice versa) create hidden coupling that is fragile and unauditable.

**Architectural consequences:**
- Mediation layer maintains two independent upstream connectors
- Domain adapters declare their upstream in the agent registry — per agent, per data need
- If a capability requires data from both Apigee and Glue, the domain adapter performs explicit composition (never assumed at the upstream level)

**Evaluation question:** *"Does this option introduce any assumption that Apigee and Glue talk to each other?"*

---

### B4 — Stable Contracts Over Internal Freedom

> Public capability APIs (tool contracts exposed to CES) are stable, versioned, and backward compatible. Internal adapter implementations can change freely without breaking agents.

**Rationale:** Agent prompt engineering and CES tool configuration are expensive to change — they involve testing, evaluation, and prompt tuning. If internal adapter refactoring breaks CES-facing contracts, the blast radius extends to every agent that uses that capability.

**Architectural consequences:**
- Mediation layer exposes a canonical OpenAPI surface to CES; agents depend on this surface only
- Internal adapter changes (upstream migration, caching strategy, error mapping) happen behind stable contracts
- Contract changes go through versioning (path-based or header-based) with deprecation periods

**Evaluation question:** *"Does this option make CES-facing contracts dependent on internal implementation details?"*

---

### B5 — Deterministic Control for Sensitive Operations

> Sensitive banking actions (transfers, card operations, identity-bound queries) require explicit assurance level verification and deterministic authorization checks. The LLM does not decide whether to enforce policy.

**Rationale:** An LLM can be prompt-injected, confused by adversarial input, or exhibit emergent behavior. For operations that move money or expose PII, the security gate must be deterministic code (PDP + PEP), not a probabilistic model.

**Derived rule:** **Policy Before Data (A2)** applies doubly for sensitive operations — the PDP decision includes assurance level (ACR) evaluation and may trigger step-up authentication before any data is returned.

**Architectural consequences:**
- PDP evaluates (subject, action, resource, context) and returns permit/deny + obligations (including `step_up_required`)
- Step-up flow is implemented deterministically in the mediation layer, not delegated to agent logic
- Read-only informational operations (fees, branches, FAQs) may have lighter enforcement

**Evaluation question:** *"Does this option allow any sensitive operation to proceed without deterministic PDP authorization?"*

---

## C) Resilience & Blast Radius Principles

### C1 — Reduced Blast Radius by Domain Isolation

> Failure, compromise, or misconfiguration of any single agent, domain adapter, or upstream integration cannot compromise other domains or the entire platform.

**Rationale:** In a banking platform with 12+ agents and 3+ upstreams, a single failure must not cascade. The Appointments adapter crashing must not affect Banking operations. A Glue timeout must not block knowledge-base responses.

**This principle is operationally meaningful only when:**
- Domain adapters are separately deployable services (not in-process modules sharing a JVM)
- Each adapter has its own service account, health checks, and scaling parameters
- Circuit breakers isolate upstream failures per domain

**Anti-pattern:** If all adapters are packaged in-process within BFA Gateway, blast radius = 100% regardless of the chosen topology option. Option C degrades to Option A in practice.

**Evaluation question:** *"If the Banking adapter crashes, do Fees and Location continue serving?"*

---

### C2 — Graceful Degradation

> Partial upstream failure results in degraded but functional service, not total outage.

**Rationale:** Banking customers on a voice call cannot wait for infrastructure recovery. If Apigee is down, Glue-only agents (location, appointments) must still work. If Glue is down, Apigee-only agents (banking, cards) must still work. If the knowledge base is unavailable, transactional flows must still function.

**Architectural consequences:**
- BFA returns domain-specific degradation responses (not generic 500s) when a specific adapter is unhealthy
- Read-model adapters operate independently of upstream availability
- Circuit breakers with fallback responses per domain

**Evaluation question:** *"What does the user experience when one of the three upstreams (Apigee, Glue, read model) is down?"*

---

### C3 — Backpressure and Rate Limits Are Mandatory

> Every capability enforces quotas, timeouts, retries with backoff, and circuit breakers to protect upstreams and prevent runaway LLM/tool loops.

**Rationale:** LLM-driven agents can enter tool-call loops (the agent keeps calling the same tool expecting different results). Without rate limits, this translates to sustained load on banking backends. Apigee and Glue have their own quotas — exceeding them causes cascading failures.

**Architectural consequences:**
- BFA Gateway enforces per-agent, per-capability rate limits
- Adapters implement circuit breakers with configurable thresholds per upstream
- Timeout budgets are defined per hop (see latency budget in ADR-0104)

**Evaluation question:** *"What prevents an agent from making 1000 balance inquiry calls in 10 seconds?"*

---

### C4 — Idempotency and Replay Safety

> All non-read operations must be idempotent or protected against replay.

**Rationale:** Voice interactions are inherently lossy — users may repeat commands, the speech-to-text engine may fire duplicate transcriptions, and CES may retry failed tool calls. Without idempotency, a transfer or payment could execute twice.

**Architectural consequences:**
- Write operations include idempotency keys (client-generated or BFA-generated)
- Adapters pass idempotency keys to upstreams and handle 409 Conflict responses
- BFA logs idempotency key + outcome for audit reconstruction

**Evaluation question:** *"What happens if CES retries a fund-transfer tool call due to a timeout?"*

---

## D) Data & AI Grounding Principles

### D1 — Grounded Responses for Factual Content

> When answering about policies, fees, products, or procedures, the assistant must cite from approved, curated sources. No ungrounded LLM "knowledge."

**Rationale:** An LLM confidently stating an incorrect fee schedule or wrong product terms is worse than admitting "I don't know." For a bank, hallucinated financial advice has regulatory and reputational consequences.

**Architectural consequences:**
- Informational agents (AG-005 Fees, AG-006 Knowledge, AG-009 Products) consume curated read models, not LLM parametric memory
- BFA-hosted curated datasets are versioned with effective dates and provenance
- Agent prompts instruct models to use tool responses only, never prior training data, for factual claims

**Evaluation question:** *"Can the system prove where every factual claim in a response came from?"*

---

### D2 — Data Minimization

> Only collect, transmit, and store what is necessary. Aggressively redact PII in logs, telemetry, and conversational context.

**Rationale:** GDPR Article 32 and PCI-DSS 4.0 Req. 3 require data minimization by design. Raw PII (account numbers, customer names, balances) must not flow into CES prompt context, BigQuery export logs, or observability pipelines unredacted.

**Architectural consequences:**
- BFA Response PEP applies field-level redaction (account number masking, name truncation) before returning data to CES
- `x-ces-session-context` headers are stripped or redacted by BFA before forwarding to upstreams
- Adapters may handle full PII internally for processing but never return unmasked PII through BFA to CES
- CES BigQuery audit exports contain redacted data only

**Evaluation question:** *"If CES logs are exfiltrated, what customer PII is exposed?"*

---

### D3 — Clear Data Classification Boundaries

> Every capability declares its data classification (public / internal / confidential / restricted) and applies controls accordingly.

**Rationale:** Not all data needs the same protection. Branch locations are public. Fee schedules are internal. Account balances are confidential. Card numbers are restricted. Controls should match classification — over-protecting public data wastes effort; under-protecting restricted data creates compliance violations.

**Architectural consequences:**
- Agent registry declares data classification per agent/capability
- BFA applies classification-appropriate controls: public data may be cached aggressively; restricted data requires step-up + field-level redaction
- Adapter responses include classification metadata for audit

**Evaluation question:** *"Does the agent registry have data classification for every capability?"*

---

### D4 — Read Models for Informational Domains

> Fees, FAQs, product information, and branch data should prefer curated read models and indexed stores over live transactional calls.

**Rationale:** Informational queries (60-70% of expected voice traffic) do not need real-time transactional accuracy. They need deterministic, low-latency, grounded responses. Curated read models provide: predictable latency, no upstream dependency, version-controlled content with effective dates, and zero PII in the data source.

**Architectural consequences:**
- BFA hosts curated read models for AG-003 (Location), AG-005 (Fees), AG-006 (Knowledge), AG-010 (Mobile)
- Read models are refreshed on a controlled cadence (daily / event-driven) — not live upstream calls
- Phase 1 agents (weeks 2-4) can ship using read models without waiting for Token Broker or Interconnect

**Evaluation question:** *"Does this option distinguish between informational queries (read model) and transactional queries (live upstream)?"*

---

## E) Operability & Delivery Principles

### E1 — Everything as Code

> Networking rules, Service Directory registrations, tool endpoint configurations, IAM policies, and deployments are managed via Infrastructure as Code and CI gates.

**Rationale:** Manual configuration in a regulated banking environment creates audit gaps, configuration drift, and irreproducible environments. "I changed the Service Directory entry manually" is a compliance incident.

**Architectural consequences:**
- Terraform manages VPC-SC, Service Directory, IAM, Cloud Run deployments
- CES tool configurations are version-controlled and applied through CI
- Exception Registry entries are code-managed (not spreadsheet-managed)

**Evaluation question:** *"Can the entire environment be torn down and rebuilt from code?"*

---

### E2 — Observability by Design

> Standard correlation headers, distributed traces, metrics dashboards, and alerting are required before any component reaches production. Observability is not a post-launch addition.

**Rationale:** In a voice banking system with a 3-second p95 SLA and multiple hops (CES → BFA → PDP → adapter → upstream), diagnosing latency spikes or failures without distributed tracing is impossible.

**Architectural consequences:**
- Correlation headers (`x-request-id`, `x-session-id`, `x-agent-id`, `x-tool-id`) propagated on every hop
- Cloud Trace integration for end-to-end latency visibility
- Per-adapter health checks, error rate metrics, and latency percentile dashboards
- Alerting on: adapter error rate > threshold, PDP latency > budget, upstream circuit breaker open

**Evaluation question:** *"Can the on-call engineer identify which adapter and which upstream caused a latency spike from one dashboard?"*

---

### E3 — Secure-by-Default Pipelines

> No manual hot-fixes to tool configurations, routing rules, or security policies. All changes go through controlled CI/CD pipelines with audit trails.

**Rationale:** The Round 2 review identified teams attempting to configure CES tools directly (bypassing BFA, embedding secrets). A secure-by-default pipeline prevents this by gating all tool configuration changes through CI conformance checks.

**Architectural consequences:**
- CI pipeline includes: adapter contract conformance test, PII filter validation, correlation header propagation check
- Tool configuration changes require PR approval from Principal Architect or Security Architect
- Emergency changes follow a break-glass procedure with post-hoc audit

**Evaluation question:** *"Can a team deploy a tool configuration change without CI approval?"*

---

### E4 — Progressive Hardening

> PoC → Pilot → Production is a planned progression. Each phase explicitly adds security controls. No "we'll add security later."

**Rationale:** Security and compliance controls are expensive to retrofit. The phased delivery model (Phase 1a–1d → Phase 2) explicitly defines which controls are added at each stage. This prevents two failure modes: (1) shipping without controls, or (2) blocking all delivery on day-1 full-stack security.

**Phases and controls:**

| Phase | Security Controls Added |
|-------|----------------------|
| Phase 1a (weeks 1-2) | BFA Gateway skeleton + Edge PEP (validation, correlation, PII redaction) |
| Phase 1b (weeks 2-4) | Read-model adapters (no upstream calls, no token exchange needed) |
| Phase 1c (weeks 4-8) | Token Broker Service v1 (CIDP for Apigee, CIAM exchange) |
| Phase 1d (weeks 6-10) | Interconnect/VPN + EIDP integration, Glue-dependent adapters |
| Phase 2 (August 2026+) | Full write-capable adapters with step-up enforcement, mTLS everywhere, VPC-SC strict mode |

**Evaluation question:** *"Does the Phase 1 plan include the minimum security controls needed for the data classification of Phase 1 agents?"*

---

## Compact Charter (Top 10)

For quick reference and workshop whiteboarding, these 10 principles cover the critical decisions:

| # | Principle | Category | Weight |
|---|-----------|----------|--------|
| 1 | Confidential-by-default | Security | High |
| 2 | Policy before data | Security | High |
| 3 | Capability-oriented access | Domain | High |
| 4 | No direct agent-to-upstream coupling | Domain | High |
| 5 | Secrets never live in orchestration | Security | High |
| 6 | Reduced blast radius by domain isolation | Resilience | High |
| 7 | Least privilege everywhere | Security | Medium |
| 8 | Auditability is first-class | Security | High |
| 9 | Grounded responses for factual content | Data/AI | Medium |
| 10 | Explicit upstream independence (Apigee ≠ Glue) | Domain | High |

---

## Quality Criteria for Principles

Every principle in this charter must pass these quality checks:

| Quality | Test |
|---------|------|
| **Specific** | Does the principle name a concrete constraint? (Not "be secure" but "fail closed for security-critical dependencies") |
| **Measurable** | Can you write a test or checklist to verify compliance? |
| **Actionable** | Does it tell engineers what to do differently? |
| **Architectural** | Does it influence more than one ADR or design decision? |
| **Non-trivial** | Would a reasonable engineer disagree or argue for an alternative? |

If a principle fails any of these tests, it should be reworded or removed.

---

## Workshop Evaluation Matrix Template

Use this table during the workshop (Block 2) to score topology options against principles.

| # | Principle | Wt | Option 0: Baseline/Direct | Option A: Strict Gateway | Option B: Federated | Option C: Gateway + Exceptions |
|---|-----------|----|--------------------|--------------------|--------------------------|
| A1 | Confidential-by-default | H | | | | |
| A2 | Policy before data | H | | | | |
| A3 | Least privilege everywhere | M | | | | |
| A4 | Fail closed | H | | | | |
| A5 | mTLS all hops | M | | | | |
| A6 | Secrets never in orchestration | H | | | | |
| A7 | Separation of duties | M | | | | |
| A8 | Auditability first-class | H | | | | |
| B1 | Capability-oriented access | H | | | | |
| B2 | No agent-to-upstream coupling | H | | | | |
| B3 | Upstream independence | H | | | | |
| B4 | Stable contracts | M | | | | |
| B5 | Deterministic sensitive ops | H | | | | |
| C1 | Blast radius isolation | H | | | | |
| C2 | Graceful degradation | M | | | | |
| C3 | Backpressure mandatory | M | | | | |
| C4 | Idempotency | M | | | | |
| D1 | Grounded responses | M | | | | |
| D2 | Data minimization | H | | | | |
| D3 | Data classification | M | | | | |
| D4 | Read models for info domains | M | | | | |
| E1 | Everything as code | M | | | | |
| E2 | Observability by design | M | | | | |
| E3 | Secure-by-default pipelines | M | | | | |
| E4 | Progressive hardening | M | | | | |
| | **Weighted total** | | | | | |

**Scoring:** ✅ = 3 (fully satisfies), ⚠️ = 1 (partially), ❌ = 0 (violates).  
**Weights:** High = ×3, Medium = ×1.  
**Maximum possible score:** (14 High × 3 × 3) + (11 Medium × 1 × 3) = 126 + 33 = **159**

---

## Principle-to-ADR Traceability Matrix

| Principle | Primary ADR(s) | Status |
|-----------|---------------|--------|
| A1 Confidential-by-default | ADR-0101 (VPC-SC, SD PNA) | Proposed |
| A2 Policy before data | ADR-0107 (layered PEP) | Proposed |
| A3 Least privilege | ADR-0108 (service-identity-first) | Proposed |
| A4 Fail closed | ADR-0107 (PDP fail-closed) | Proposed |
| A5 mTLS all hops | ADR-0112 (mTLS everywhere) | Referenced |
| A6 Secrets never in orchestration | ADR-0108 | Proposed |
| A7 Separation of duties | (Governance, not ADR-specific) | — |
| A8 Auditability | ADR-0109 (correlation headers, audit) | Referenced |
| B1 Capability-oriented access | ADR-0104 (single BFA ingress) | Proposed |
| B2 No agent-to-upstream coupling | ADR-0104 | Proposed |
| B3 Upstream independence | ADR-0103 (Apigee/Glue independent) | Referenced |
| B4 Stable contracts | ADR-0104 (BFA OpenAPI surface) | Proposed |
| B5 Deterministic sensitive ops | ADR-0106 (token + step-up), ADR-0107 (PDP) | Proposed |
| C1 Blast radius isolation | ADR-0104 (adapter packaging model) | Proposed |
| C2 Graceful degradation | ADR-0104 (per-adapter circuit breakers) | Proposed |
| C3 Backpressure | (Implementation guidance, not ADR-specific) | — |
| C4 Idempotency | (Implementation guidance, not ADR-specific) | — |
| D1 Grounded responses | ADR-0104 (read models), agent-registry.md | Active |
| D2 Data minimization | ADR-0104 (Response PEP), ADR-0107 | Proposed |
| D3 Data classification | agent-registry.md | Active |
| D4 Read models | ADR-0104, agent-registry.md | Active |
| E1 Everything as code | ADR-0105 (Cloud Run, IaC) | Proposed |
| E2 Observability | ADR-0109 (correlation headers) | Referenced |
| E3 Secure-by-default pipelines | ADR-0108 (tool change control) | Proposed |
| E4 Progressive hardening | `addendum-security-auth-phases.md` | Active |

---

## CES-to-SoR Connectivity: Principle Application Guide

For the specific workshop decision on CES-to-SoR connectivity, these principles have **direct bearing**:

| Decision Area | Most Relevant Principles | What They Mandate |
|---------------|-------------------------|-------------------|
| **Should CES call the mediation layer or upstreams directly?** | B1, B2, A2, A6, A8 | The mediation layer is the only CES-addressable endpoint. Upstreams are never CES-visible. |
| **How should tokens be managed?** | A3, A6, B5 | Token Broker Service in the mediation landing zone. CES holds no secrets. Tokens are capability-scoped and short-lived. |
| **What about PII in responses?** | D2, A2, A8 | Mediation Layer Response PEP redacts before data reaches CES. No raw PII in conversational context. |
| **Can exceptions exist?** | B2, A8, C1 | Only if: read-only, no PII, governed, time-bounded, ≤3 active, and with documented escalation path. |
| **How are adapters packaged?** | C1, C2, B4 | Separately deployable Cloud Run services for write-path adapters. In-process only for Phase 1b read models, extracted before Phase 2. |
| **Who owns what?** | A7, E3, B4 | Mediation platform team owns gateway + Token Broker + shared middleware. Domain teams own adapters. Changes go through CI gates. |
| **What about Glue vs Apigee routing?** | B3, B1 | Independent upstreams, per-domain routing in the mediation layer per agent registry. No Apigee↔Glue assumption. |

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-03-02 | GitHub Copilot (Claude Opus 4.6) | Initial charter draft — 25 principles in 5 categories, evaluation matrix, ADR traceability, CES-to-SoR application guide |

---

## References

- [Governing Principle Discussion](diagrams/design_principles_chat.md)
- [ADR-0104: Backend Topology](adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md)
- [ADR-0104 Round 2 Review](reviews/ADR-0104-round-2-topology-review-2026-03-01.md)
- [Architecture Facts & Decisions](acme_cxstudio_architecture_facts_decisions.md)
- [Agent Registry](agent-registry.md)
- [Exception Lane Decision Tree](reviews/ADR-0104-exception-lane-decision-tree.md)
- [Workshop Plan](workshop-ces-to-sor-connectivity-plan.md)
