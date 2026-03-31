# Architecture Workshop: CES-to-Systems-of-Record Connectivity

**Status:** Prepared  
**Date:** 2026-03-02  
**Owner:** Principal Cloud Architect (GCP)  
**Duration:** 4 hours (half-day)  
**Format:** Structured decision workshop with time-boxed discussions  

---

## 1) Workshop Objectives

### Primary Objective

Converge on a **ratified architecture decision** for how CX Agent Studio (CES) connects to banking Systems of Record (SoR) through Apigee and Glue, ensuring alignment with the governing principle:

> *"Agents never access domain systems directly. They interact only through controlled domain capabilities exposed via a policy-enforced boundary."*

### Specific Outcomes Required

| # | Outcome | Deliverable |
|---|---------|-------------|
| O-1 | **Ratify or amend ADR-0104** topology decision (Option 0 / A / B / C) | Signed ADR-0104 with status → `Accepted` or `Amended` |
| O-2 | **Decide Token Broker architecture** — centralized vs per-adapter vs hybrid | ADR-0106 amendment or new ADR for Token Broker Service |
| O-3 | **Define exception lane governance** — when (if ever) CES may bypass the chosen mediation boundary | Exception governance policy document (or decision to prohibit exceptions) |
| O-4 | **Ratify architecture principles** as evaluation criteria for all future ADRs | Architecture Principles Charter (companion document) |
| O-5 | **Assign ownership** for the selected mediation components (Gateway, federated adapters, hybrid) | RACI matrix for platform components |
| O-6 | **Identify blocking gaps** with owners and deadlines | Action item register with DRIs |

---

## 2) Required Participants

| Role | Responsibility in Workshop | Required / Advisory |
|------|---------------------------|---------------------|
| **Principal Cloud Architect (GCP)** | Facilitator. Presents options, manages time, drives decisions. | **Required** |
| **Domain Architects** (Banking, Cards, Appointments) | Validate adapter model fits their domain; raise domain-specific constraints | **Required** |
| **Security Architect** | Validate token strategy, PDP integration, PII handling, mTLS posture | **Required** |
| **Enterprise Identity Architect** (CIAM/EIDP/CIDP owner) | Clarify EIDP/CIDP integration points, token exchange capabilities, trust boundaries | **Required** |
| **Apigee Platform Lead** | Confirm Apigee Internal connectivity, rate limiting, API key vs token auth options | **Required** |
| **Glue / WSO2 Platform Lead** | Confirm Glue connectivity status, mTLS requirements, API inventory | **Required** |
| **CES Platform Lead** | Confirm CES platform constraints (sandbox, callbacks, Secret Manager, VPC-SC) | **Required** |
| **Infrastructure / Networking Lead** | Confirm Interconnect/VPN status, VPC-SC posture, Service Directory readiness | **Required** |
| **Compliance / Risk Representative** | Validate GDPR, PCI-DSS, SOC 2 alignment of chosen option | **Required** |
| **Engineering Team Leads** (2-3 agent teams) | Represent developer experience, team autonomy needs, delivery velocity concerns | **Required** |
| **Product Owner / Program Manager** | Phase scope, priority, and delivery timeline context | Advisory |
| **Scribe / Decision Recorder** | Capture decisions, action items, dissenting opinions | **Required** |

**Recommended size:** 10–14 participants. Larger groups impede decision velocity.

---

## 3) Pre-Workshop Preparation

### Required Reading (distributed 5 business days before)

| Document | Purpose | Location |
|----------|---------|----------|
| ADR-0104 (full) | Understand the topology options and current recommendation | `architecture/adrs/ADR-0104-*.md` |
| Round 2 Topology Review | Understand EIDP/CIDP findings, CES constraints, gap analysis | `architecture/reviews/ADR-0104-round-2-topology-review-2026-03-01.md` |
| Architecture Facts & Decisions | Non-negotiable constraints and confirmed decisions | `architecture/acme_cxstudio_architecture_facts_decisions.md` |
| Agent Registry | Canonical agent list, sources, and phases | `architecture/agent-registry.md` |
| Design Principles Chat | Governing principle rationale and principle categories | `architecture/diagrams/design_principles_chat.md` |
| Architecture Principles Charter | Evaluation criteria for the workshop | `architecture/architecture-principles-charter.md` (companion document) |
| Exception Lane Decision Tree | Decision framework for direct-path exceptions | `architecture/reviews/ADR-0104-exception-lane-decision-tree.md` |

### Pre-Workshop Assignments

| Participant | Assignment | Deliver by |
|-------------|-----------|------------|
| **Enterprise Identity Architect** | Answer Q-EIDP-1 through Q-EIDP-8 (from Round 2 review § 4.3) | Workshop day minus 3 |
| **Infrastructure Lead** | Provide Interconnect/VPN status and timeline for Glue reachability | Workshop day minus 3 |
| **CES Platform Lead** | Confirm or deny CES Python sandbox constraints (Q-CES-1) | Workshop day minus 3 |
| **Apigee Platform Lead** | Provide list of APIs currently exposed via Apigee Internal, auth requirements per API | Workshop day minus 3 |
| **Glue Platform Lead** | Provide inventory of Glue API products per agent domain, mTLS readiness | Workshop day minus 3 |
| **Each Domain Architect** | Prepare 1-slide "domain adapter needs" summary (upstream calls, data sensitivity, latency needs) | Workshop day minus 2 |

### Pre-Workshop Question Collection

Distribute a form 1 week before to collect:
- Concerns about the proposed topology
- Unresolved constraints or blockers each participant knows about
- Preferences and non-negotiables from each team

Compile responses and share anonymized summary the day before the workshop.

---

## 4) Agenda

### Overview

| Block | Duration | Topic |
|-------|----------|-------|
| **Block 1** | 30 min | Context Setting & Constraint Alignment |
| **Block 2** | 60 min | Topology Decision: Options 0 / A / B / C |
| **Break** | 15 min | — |
| **Block 3** | 45 min | Token Strategy & Identity Provider Integration |
| **Block 4** | 30 min | Exception Lane Governance |
| **Break** | 10 min | — |
| **Block 5** | 30 min | Adapter Packaging, Ownership & Delivery Model |
| **Block 6** | 20 min | Decisions, Action Items & Next Steps |
| **Total** | **4 hours** | |

---

### Block 1 — Context Setting & Constraint Alignment (30 min)

**Facilitator:** Principal Cloud Architect

**Purpose:** Ensure all participants share the same factual foundation before debating options.

| Time | Activity |
|------|----------|
| 0:00–0:10 | **Governing principle review**: Present the architecture charter principles. Confirm consensus that principles are accepted as evaluation criteria. Quick "fist of five" vote. |
| 0:10–0:20 | **Constraint walk-through**: Present confirmed non-negotiable facts (from § 1 of the Facts document). Highlight: (1) Apigee and Glue are independent, (2) VPC-SC posture, (3) CES sandbox constraints (no custom imports, no secrets, no DLP), (4) Glue connectivity status. Ask each platform lead to confirm or update constraints. |
| 0:20–0:30 | **EIDP/CIDP status update**: Enterprise Identity Architect presents answers to Q-EIDP-1 through Q-EIDP-8. Capture any remaining unknowns on the parking lot. |

**Decision gate:** Participants confirm shared understanding of constraints. Any factual disagreement is resolved before proceeding.

---

### Block 2 — Topology Decision: Options 0 / A / B / C (60 min)

**Facilitator:** Principal Cloud Architect  
**Decision method:** Structured debate → Dot voting → Consensus check

| Time | Activity |
|------|----------|
| 0:30–0:35 | **Baseline (Option 0)** (5 min): Briefly present "Direct to Apigee/SoR" without a mediation boundary, highlighting how it aligns to or fails the ratified Security & Compliance principles. |
| 0:35–0:45 | **Option presentations** (10 min): Principal Architect presents Options A (Strict Single Gateway), B (Federated Adapters), and C (Gateway + Controlled Exceptions) side-by-side using the comparative evaluation matrix. Each option is measured against the architecture principles. |
| 0:45–1:00 | **Stakeholder perspectives** (15 min): Each stakeholder group gets 3 minutes to state their position. Order: (1) Security Architect, (2) Domain Architects, (3) Engineering Team Leads, (4) Infrastructure Lead, (5) Compliance Representative. |
| 1:00–1:15 | **Structured debate** (15 min): Open floor with rules — each speaker states which principle their argument supports or weakens. Facilitator tracks on a visible "Principle Alignment Matrix". |
| 1:15–1:20 | **Red team challenge** (5 min): Security Architect + Compliance Rep challenge the leading option. Must survive: (1) PII leak, (2) token compromise, (3) routing failure. |
| 1:20–1:30 | **Decision vote** (10 min): Dot voting (each participant distributes 3 dots). >60% of dots → adopt. If contested → Principal Architect makes final call with documented reason. |

**Key architectural questions to answer in this block:**

1. **Where does the domain model live, and who owns the business capability boundary?**  
   *(From design principles chat — "The Real Strategic Question")*

2. **Is the Mediation Layer allowed to contain domain orchestration logic, or is it strictly a thin policy boundary?**
   *(Model A: Thin Router vs. Model B: Policy Boundary + Domain Adapter vs. Model C: Domain Services)*

3. **Can an agent ever bypass the chosen mediation layer to call Apigee/Glue directly?**
   *(ADR-0104 says no — is there consensus?)*

4. **Who owns capability contracts — agent team or domain team?**  
   *(Affects adapter design, release coupling, and team autonomy)*

5. **Can a domain adapter call multiple upstreams (both Apigee and Glue)?**  
   *(Composition rule from the facts document: composition happens above upstreams)*

6. **Where is response normalization/redaction applied?**  
   *(Centralized at the Mediation Layer Edge PEP, or distributed in adapters?)*

7. **What is the blast radius we accept per failure mode?**  
   *(Per-adapter? Per-domain? Entire platform?)*

**Expected output:** Ratified topology choice with documented principle alignment.

---

### Break (15 min)

---

### Block 3 — Token Strategy & Identity Provider Integration (45 min)

**Facilitator:** Security Architect + Enterprise Identity Architect

**Purpose:** Decide the concrete token exchange architecture that makes the ratified topology operational.

| Time | Activity |
|------|----------|
| 1:45–1:55 | **Token lifecycle walk-through** (10 min): Present the token exchange chain: CES identity → mediation-layer workload identity → Token Broker (centralized or federated) → upstream-scoped token (CIAM/CIDP/EIDP). Show the sequence diagram for a balance inquiry (AG-007) end-to-end. |
| 1:55–2:10 | **EIDP/CIDP integration decision** (15 min): Based on pre-workshop answers to Q-EIDP-1 through Q-EIDP-8, decide: (a) Which identity provider issues tokens for which upstream? (b) What protocol does the mediation layer/Token Broker use? (c) Is EIDP reachable from the selected mediation boundary in Phase 1? |
| 2:10–2:20 | **Token Broker Service architecture** (10 min): Present two sub-options from Round 2 review: (i) Embedded in the mediation gateway, (ii) Separate Cloud Run service. Evaluate against: blast radius, latency (token caching), team ownership, operational complexity. |
| 2:20–2:30 | **Decision and recording** (10 min): Vote on Token Broker placement. Document EIDP/CIDP/CIAM role assignments. Identify any ADR amendments needed (ADR-0106, ADR-0107). |

**Key questions to answer:**

8. **Is CIAM the only customer-facing IdP, while EIDP/CIDP handle service-to-service only?**  
   *(Q-EIDP-6)*

9. **Who mints the subject_token for RFC 8693 exchange?**  
   *(The bootstrapping question: where does the initial service identity token come from?)*

10. **What is the token TTL and caching strategy?**  
    *(Short TTL = more IdP round-trips = higher latency. Long TTL = larger compromise blast radius.)*

11. **Is step-up enforced centrally at the mediation layer or per domain?**

**Expected output:** Token Broker architecture decision. EIDP/CIDP/CIAM role matrix.

---

### Block 4 — Exception Lane Governance (30 min)

**Facilitator:** Security Architect + Principal Cloud Architect

**Purpose:** Decide whether controlled exceptions to the single-ingress model are allowed, and if so, under what governance.

| Time | Activity |
|------|----------|
| 2:30–2:40 | **Present the exception lane decision tree** (10 min): Walk through the 5-decision flowchart. Show Tier 1 (API key) and Tier 2 (Token Broker pre-call) paths. Present anti-patterns. |
| 2:40–2:50 | **Risk assessment** (10 min): Security Architect presents the governance cost of exceptions — exception registry, 90-day reviews, ≤3 cap. Compare: "What does the exception save?" (reduced latency for 1 read-only, non-PII call) vs. "What does it cost?" (shadow architecture risk, audit scope expansion). |
| 2:50–3:00 | **Decision** (10 min): Binary vote: (a) No exceptions — Core mediation path for everything, period. (b) Exceptions allowed under the decision tree governance. Record rationale. If exceptions allowed, assign Exception Registry owner. |

**Key questions to answer:**

12. **Is the latency saving from bypassing the mediation layer (~20-50ms) significant enough to justify governance overhead?**  
    *(Within a 3-second voice SLA budget)*

13. **What happens when an exception-lane endpoint evolves to require customer context?**  
    *(Migration cost + sprint commitment needed)*

14. **Who has authority to approve exceptions?**  
    *(Security team? Architecture board? Individual team leads?)*

**Expected output:** Exception governance policy (adopted or rejected). If adopted, decision tree is ratified and Exception Registry owner is assigned.

---

### Break (10 min)

---

### Block 5 — Adapter Packaging, Ownership & Delivery Model (30 min)

**Facilitator:** Principal Cloud Architect + Engineering Team Leads

**Purpose:** Define how domain adapters are deployed, who owns them, and how teams deliver independently behind the single ingress.

| Time | Activity |
|------|----------|
| 3:10–3:20 | **Packaging model review** (10 min): Present the adapter packaging comparison from ADR-0104 (separate Cloud Run vs. in-process vs. monolith). Show the degradation risk: "If adapters are in-process permanently, Option C collapses to Option A." Present the phased delivery timeline (Phase 1a → 1b → 1c → 1d → 2). |
| 3:20–3:30 | **Team operating model** (10 min): Each Engineering Team Lead states their team's needs: language stack, release cadence, on-call expectations. Identify shared vs. team-owned components. |
| 3:30–3:40 | **RACI assignment** (10 min): Fill in the ownership matrix collaboratively. |

**RACI Template:**

| Component | Responsible | Accountable | Consulted | Informed |
|-----------|-----------|-------------|-----------|----------|
| Central Mediation Gateway / Edge PEP | | Principal Architect | Security, Domain Archs | All teams |
| Token Broker Service | | | Identity Architect | All teams |
| Domain Adapter (per domain) | | Domain Team Lead | Mediation Platform Team | Principal Architect |
| Shared middleware / SDK | | | All teams | |
| Exception Registry | | | Security Architect | |
| Agent Registry maintenance | | Principal Architect | Domain Archs | |

**Key questions to answer:**

15. **When must in-process adapters be extracted to separate Cloud Run services?**  
    *(ADR-0104 recommends "before Phase 2" — is this feasible? What CI gate enforces it?)*

16. **What conformance testing is required for adapter contracts?**  
    *(Schema validation, error format, correlation header propagation)*

17. **Who owns the shared adapter middleware/template?**

**Expected output:** Adapter packaging decision by category. RACI matrix. Phase 2 extraction deadline confirmed.

---

### Block 6 — Decisions, Action Items & Next Steps (20 min)

**Facilitator:** Principal Cloud Architect + Scribe

| Time | Activity |
|------|----------|
| 3:40–3:50 | **Decision recap** (10 min): Scribe reads back all decisions made. Each decision requires: (a) What was decided, (b) Which principles it satisfies, (c) Dissenting views (if any), (d) Owner for follow-up. |
| 3:50–4:00 | **Action items and deadlines** (10 min): Capture remaining gaps, ADR amendments, diagram updates, and prototype tasks with DRIs and deadlines. Schedule follow-up review if needed. |

---

## 5) Decision-Making Framework

### Evaluation Matrix

For each topology option, score against principles using this matrix. Display on a shared screen/whiteboard during Block 2.

| Principle | Weight | Option 0 (Baseline/Direct) | Option A (Strict Gateway) | Option B (Federated) | Option C (Gateway + Exceptions) |
|-----------|--------|----------------------------|---------------------------|----------------------|--------------------------------|
| Confidential-by-default | High | | | | |
| Policy before data | High | | | | |
| Capability-oriented access | High | | | | |
| No agent-to-upstream coupling | High | | | | |
| Reduced blast radius | High | | | | |
| Least privilege everywhere | Medium | | | | |
| Secrets never in orchestration | High | | | | |
| Graceful degradation | Medium | | | | |
| Auditability first-class | High | | | | |
| Stable contracts over internal freedom | Medium | | | | |
| Data minimization | High | | | | |
| Everything as code | Medium | | | | |
| Observability by design | Medium | | | | |
| **Total weighted score** | | | | | |

**Scoring:** ✅ Fully satisfies (3), ⚠️ Partially satisfies (1), ❌ Violates or does not satisfy (0). Weighted total determines recommendation.

### Voting Mechanisms

| Situation | Method | Threshold |
|-----------|--------|-----------|
| Principle ratification | "Fist of five" (1=strongly disagree, 5=strongly agree) | Average ≥ 4 → adopted. Average < 3 → requires rework. |
| Topology choice | Dot voting (3 dots per person) | >60% of total dots → adopted |
| Binary decisions (exception lane, packaging) | Roman vote (thumbs up/sideways/down) | Majority up → adopted. If contested, Principal Architect decides with documented rationale |
| Dissenting opinions | Named dissent recording | All dissents are recorded in the ADR changelog with the dissenter's reasoning |

### Parking Lot Protocol

Items that cannot be resolved in the workshop are recorded with:
- Description
- Why it's blocking (or non-blocking)
- Assigned DRI
- Deadline for resolution

---

## 6) Expected Outputs

| # | Output | Format | Owner | Deadline |
|---|--------|--------|-------|----------|
| 1 | ADR-0104 updated to `Accepted` (or `Amended`) | Markdown (existing file) | Principal Architect | Workshop + 2 days |
| 2 | Token Broker ADR (new or amendment to ADR-0106) | Markdown | Security Architect + Identity Architect | Workshop + 5 days |
| 3 | Exception Governance Policy (adopted or rejected) | Markdown | Security Architect | Workshop + 3 days |
| 4 | Architecture Principles Charter ratified | Markdown (companion file) | Principal Architect | Workshop day (if voted) |
| 5 | Updated topology diagrams (Mermaid) reflecting decisions | `.mmd` files | Principal Architect | Workshop + 5 days |
| 6 | RACI matrix for platform components | Markdown or spreadsheet | Principal Architect | Workshop + 2 days |
| 7 | Action item register with DRIs and deadlines | Markdown | Scribe | Workshop + 1 day |
| 8 | Parking lot items with assigned owners | Markdown | Scribe | Workshop + 1 day |
| 9 | Workshop decision log (all votes, dissents, rationale) | Markdown | Scribe | Workshop + 1 day |

---

## 7) Room Setup & Materials

### Physical / Virtual Setup

- Shared screen for principle alignment matrix (live editing)
- Whiteboard / Miro board for architecture diagrams
- Sticky notes or digital equivalent for dot voting
- Timer visible to all participants

### Materials to Prepare

- [ ] Printed / shared copies of the Architecture Principles Charter
- [ ] Pre-filled principle alignment matrix (empty scores, principles and options filled)
- [ ] Topology option comparison table (from ADR-0104 alternatives)
- [ ] Exception lane decision tree (printed / shared)
- [ ] Agent registry summary (which agents use which upstreams)
- [ ] Sequence diagrams: balance inquiry (AG-007), branch lookup (AG-003), appointment booking (AG-004)
- [ ] EIDP/CIDP pre-workshop answers compiled
- [ ] Pre-workshop concern survey results (anonymized summary)

---

## 8) Risk Mitigation for the Workshop Itself

| Risk | Mitigation |
|------|-----------|
| Key participant absent | Require pre-workshop written position from all required participants; proxy must have decision authority |
| Debate loops without decision | Time-boxed blocks + facilitator authority to call vote when discussion repeats |
| Missing pre-workshop data (EIDP answers, Glue connectivity) | Identify gap explicitly in Block 1; make decisions conditional where necessary; schedule follow-up for data-dependent items |
| Scope creep into detailed implementation | Facilitator redirects to parking lot; workshop decides "what" not "how" |
| No consensus reached | Principal Architect has final decision authority per architecture governance; dissenting views are recorded |

---

## References

- [ADR-0104: Backend Topology](../adrs/ADR-0104-backend-topology-single-bfa-gateway-tool-surface-internal-domain-adapters.md)
- [Round 2 Topology Review](../reviews/ADR-0104-round-2-topology-review-2026-03-01.md)
- [Architecture Facts & Decisions](../acme_cxstudio_architecture_facts_decisions.md)
- [Agent Registry](../agent-registry.md)
- [Exception Lane Decision Tree](../reviews/ADR-0104-exception-lane-decision-tree.md)
- [Design Principles Discussion](../diagrams/design_principles_chat.md)
- [Architecture Principles Charter](../architecture-principles-charter.md)
