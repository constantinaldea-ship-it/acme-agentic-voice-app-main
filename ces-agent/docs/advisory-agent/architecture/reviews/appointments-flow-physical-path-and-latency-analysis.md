# Appointment Booking Flow — Future Internal Integration Path and Latency Analysis

**Status:** Workshop discussion note for the future internal integration target  
**Date:** 2026-03-02  
**Scope note:** This note analyzes the later AG-004 / Glue production-target path. It is not the preparation-phase implementation baseline.  
**Related implementation diagrams:** `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow.mmd`, `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-01-routing-and-intake.mmd`, `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-02-location-and-slots.mmd`, `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-03-booking-confirmation.mmd`, `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-04-lifecycle-management.mmd`  
**Audience:** Architecture workshop participants (platform, security, domain, delivery)

---

## 1) Scope and intent

This document explains the **physical request path** for appointment booking via BFA + internal domain adapter and addresses the common criticism that this design is "too latency intensive" or "overly complex".

This is an architecture argument document, not a measured benchmark report. It should be read as the future cutover discussion companion to the preparation-phase implementation diagrams, not as the current implementation flow.

---

## 2) End-to-end physical request path (step by step)

Below is the practical path for a representative request such as `findBranches`, `getAvailableSlots`, or `createAppointment`.

1. **User speaks to the CES voice agent**
   - VoiceBankingAgent resolves intent and prepares tool call payload.

2. **CES sends tool call to private BFA endpoint**
   - Target: `https://bfa-gateway.internal.acme.example/capabilities/...`
   - Transport uses private network controls (VPC-SC posture, private ingress path).

3. **BFA Gateway receives request (Edge PEP)**
   - Applies input validation, rate limiting, request correlation, and policy checks.
   - For this flow, no customer on-behalf-of token is required (public booking), but request governance still applies.
   - **Important:** downstream Glue access still requires technical-user authentication plus authorization tokening.

4. **BFA routes to the internal Appointments Adapter (AG-004)**
   - Domain routing happens behind the mediation boundary.
   - Adapter is an internal service (not CES-addressable).

5. **Adapter acquires technical-user and authorization tokens for Glue**
   - Uses service identity/workload identity to call the token path.
   - Obtains a technical-user token and the corresponding AuthZ token required by Glue-protected services.
   - This step is required irrespective of whether the request is customer-authenticated.

6. **Adapter calls Glue (WSO2), which proxies to on-prem AdvisorAPI**
   - `findBranches` and `getAvailableSlots` call read-like endpoints.
   - `createAppointment` calls booking endpoint.

7. **AdvisorAPI returns domain response via Glue to adapter**
   - Adapter normalizes response shape for the BFA contract.

8. **BFA applies Response PEP controls**
   - Sanitization, log-safe handling, correlation propagation, and response contract shaping.

9. **CES receives response and speaks result to user**
   - User hears branch options, slot options, and final confirmation.

This is the future internal-integration chain discussed in the workshop, with physical boundaries made explicit. The active implementation baseline is documented in the split preparation-phase diagram set rooted at `bfa-appointments-flow.mmd`.

---

## 3) Is this latency intensive? The correct framing

### 3.1 What critics usually say
- "There are too many hops."
- "Direct CES→Apigee would be faster."
- "Gateway + adapter + glue is over-engineered for voice."

### 3.2 What actually drives latency
Hop count matters, but it is **not** the dominant factor in most real-world p95/p99 tails. Major contributors are:
- upstream processing time (on-prem/API manager)
- cold starts / scaling behavior
- cache misses
- retries/timeouts and network variability
- token or policy work done repeatedly without caching

### 3.3 Why BFA + adapter is often acceptable for voice SLAs
- The architecture adds a **small fixed overhead** but gives strong control over larger variable latency sources.
- Centralized policy and routing reduce unpredictable tail behavior from per-team custom implementations.
- Domain adapter caching and response normalization can reduce expensive upstream round-trips.
- Operationally, this improves p95 consistency, which matters more than winning the fastest single p50 call.

---

## 4) Counterarguments and responses (for workshop debate)

| Counterargument | Concern | Response |
|---|---|---|
| "Too many hops" | Added gateway/adapter/glue path increases latency | True in pure hop count. But controlled, cache-aware routing often improves tail predictability and avoids repeated upstream penalties. |
| "Overly complex architecture" | More components = more failure points | Also more isolation points. Domain adapters localize blast radius and enable independent scaling/recovery. |
| "Direct path is simpler" | Faster to wire one direct endpoint | Simpler initially, but governance and security complexity shifts to every team/tool and multiplies over time. |
| "Single gateway is a bottleneck/SPOF" | Central dependency risk | Valid risk; mitigated through HA, min instances, autoscaling, canary rollout, and strict SLO operation. |
| "Policy checks slow everything" | PEP/PDP work adds latency | Keep policy path deterministic/lightweight and cache where safe. Cost is usually smaller than upstream/on-prem variability. |
| "Token handling adds overhead" | Auth chain impacts response time | True. Even in this public flow, Glue requires technical-user + AuthZ tokens. Mitigate with bounded token caching and strict TTL controls. |
| "Debugging is harder across layers" | More services to trace | Only if observability is poor. With correlation IDs + distributed traces, multi-hop diagnosis is operationally tractable. |

---

## 5) Where complexity is justified (and where it is not)

### Justified complexity
- Regulated PII handling and audit controls
- Deterministic policy-before-data enforcement
- Domain team autonomy with internal isolation
- On-prem integration via Glue with controlled security posture

### Unjustified complexity (to avoid)
- Cross-adapter chains for a single user action without clear need
- Business logic bloated into the gateway
- Duplicate policy engines per adapter
- Overly chatty tool design (multiple avoidable round-trips)

---

## 6) Practical latency guardrails for this flow

To keep this architecture fast in practice:

1. Keep BFA thin (validate, route, enforce, shape).
2. Keep appointment adapter domain-focused (orchestration + normalization only).
3. Use bounded timeout budgets per hop.
4. Apply domain cache for branch/slot queries where freshness allows.
5. Use min instances for BFA and AG-004 to avoid cold-start penalties.
6. Propagate end-to-end correlation IDs and trace spans.
7. Define explicit p50/p95/p99 SLOs and alert on tail growth.
8. Prevent cross-adapter fan-out within single request path.

---

## 7) Recommended workshop stance

For appointment booking, argue this position:

- **Yes**, the architecture introduces additional controlled hops.
- **No**, this is not inherently too slow if designed with thin gateway, adapter-local optimizations, and strong observability.
- The added structure is a deliberate trade for governance, consistency, and safer operations in a banking context.
- If a specific endpoint proves latency-critical and low-risk, evaluate it through a strict exception process rather than collapsing the default architecture.

---

## 8) Related references

- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow.mmd`
- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-01-routing-and-intake.mmd`
- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-02-location-and-slots.mmd`
- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-03-booking-confirmation.mmd`
- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow-04-lifecycle-management.mmd`
- `architecture/adrs/ADR-0104-backend-topology-internal-domain-adapters.md`
- `architecture/reviews/ADR-0104-option-B-federated-adapters.md`
- `architecture/reviews/ADR-0104-option-C-controlled-apigee-exceptions.md`
- `architecture/reviews/ADR-0104-exception-lane-decision-tree.md`
- `architecture/reviews/option-0-direct-ces-to-apigee-analysis.md`
