# Option 0 Analysis: Direct CES → Apigee/SoR (No Mediation Boundary)

**Status:** Discussion input for workshop  
**Date:** 2026-03-02  
**Scope:** Baseline/Direct pattern where CES tools call Apigee or SoR directly without a mediation boundary.

---

## 1) Latency claim evaluation

### Claim
Option 0 is often justified as “fewer hops, therefore faster.”

### What is true
- Removing a mediation hop can reduce network path length.
- For narrow, read-only, non-sensitive calls, this may produce small median-latency gains.

### What is incomplete
- **Hop count is not the only latency driver.** Policy, auth, retries, and downstream behavior dominate p95/p99 in real incidents.
- **Token work does not disappear** in Option 0; it shifts into CES tooling or direct path integrations (or gets under-implemented).
- **Tail latency can worsen** when governance/security checks are duplicated inconsistently across multiple direct paths.

### Option-to-option latency shape
- **Option 0 (Direct):** fewer network hops, but policy/auth work is fragmented; higher risk of non-uniform tail behavior.
- **Mediation path (CES → Mediation → Apigee):** one additional hop, but centralized policy/auth/redaction produces more predictable latency and easier tuning.

### Conclusion on latency
**Real gain is typically modest and situational; perceived gain is often overstated.** For regulated flows, predictable and auditable latency is usually more valuable than minimal hop count.

---

## 2) Pros/Cons matrix against workshop principles

| Principle | Option 0 Assessment | Notes |
|---|---|---|
| Confidential-by-default | ⚠️/❌ | Harder to maintain one private, uniformly controlled boundary across many direct endpoints. |
| Policy-before-data | ❌ | Central pre-data PEP/PDP chain is bypassed unless reimplemented per path. |
| No agent-to-upstream coupling | ❌ | CES becomes aware of upstream-specific endpoints/contracts. |
| Data minimization | ❌ | No guaranteed centralized response redaction layer before CES-visible responses. |
| Auditability first-class | ⚠️/❌ | Audit evidence fragments across direct paths; harder end-to-end reconstruction. |

**Net:** Option 0 is misaligned with the core workshop principles unless constrained to highly exceptional, low-risk cases.

---

## 3) Security and compliance implications

### Token exchange (ADR-0106 context)
- ADR-0106 defaults to token exchange in backend mediation for strong control and auditability.
- In Option 0, token exchange must be moved/duplicated outside the mediation boundary or simplified unsafely.
- Result: higher risk of scope drift, secret sprawl, and weaker consistency in auth handling.

### `x-ces-session-context` handling
- `x-ces-session-context` can improve deterministic context propagation, but direct forwarding increases exposure risk if not strictly allowlisted/redacted.
- Without mediation-layer stripping/redaction, sensitive session variables can leak into upstreams/logs.

### Step-up authentication ownership
- With no mediation boundary, ownership of step-up control becomes ambiguous (tool-level logic, upstream policy, or both).
- This weakens deterministic enforcement and increases audit challenges.

### Audit completeness
- Option 0 tends toward multiple partial audit streams.
- A compliance-grade “single trace” view is harder than in centralized mediation patterns.

---

## 4) Operational considerations

### Blast radius
- Failures in direct paths can propagate unevenly, with inconsistent fallback behavior.
- No single control plane for degradation strategy unless rebuilt separately.

### Secret management complexity
- Higher risk of credential/config spread across tool definitions and team-owned integrations.
- Rotation and incident containment become multi-team operations.

### Governance and exception handling
- Option 0 as default creates governance debt quickly.
- If used at all, it must be managed as an exception lane with strict approval, expiry, and migration-back obligations.

---

## 5) Recommendation

### Primary recommendation
**Do not use Option 0 as a default architecture.**

### Limited exception posture (if ever allowed)
Allow only as a time-bounded exception when all conditions hold:
1. Read-only, low-risk, non-PII capability
2. No customer-context token exchange required
3. Explicit security/compliance approval
4. Registered owner, expiry date, and 90-day review
5. Documented migration-back trigger to mediation path

This aligns with the direction in ADR-0104 exception-lane materials while preserving ADR-0106 token governance and ADR-CES-001/004 control expectations.

---

## Consistency references

- `architecture/adrs/ADR-0104-backend-topology-internal-domain-adapters.md`
- `architecture/reviews/ADR-0104-exception-lane-decision-tree.md`
- `architecture/reviews/ADR-0104-option-C-controlled-apigee-exceptions.md`
- `architecture/adrs/second-priority/ADR-0106-ciam-token-strategy-agentic-token-via-rfc8693-token-exchange-default-;-technical-on-behalf-of-for-employee-assisted-only.md`
- `ces-agent/docs/ADR-CES-001-rest-api-vs-mcp-server.md`
- `ces-agent/docs/adr/ADR-CES-004-connectivity-pattern-selection.md`
