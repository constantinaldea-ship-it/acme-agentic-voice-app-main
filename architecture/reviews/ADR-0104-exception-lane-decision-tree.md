# ADR-0104: Exception Lane Decision Tree

> **Purpose:** Determine whether a CES tool should use the BFA default path or request an exception for direct Apigee access.
>
> **Context:** ADR-0104 Round 2 Review, Option C — Controlled Exception Lane with EIDP/CIDP Governance.
>
> **Created:** 2026-03-01 | **Author:** GitHub Copilot

---

## How to Use This Decision Tree

Start at **Decision 1** and follow the path. Each terminal node gives a definitive routing decision. If _any_ gate evaluates to "Yes" on the BFA-mandatory side, the exception lane is **not viable** for that tool — no further evaluation is needed.

---

## Decision Tree

### Decision 1 — Does the endpoint require customer-context authorization?

> _Customer-context authorization = user-delegated credentials, RFC 8693 token exchange, customer-scoped access tokens (e.g., "show **my** balance")._

- **Yes** → 🔴 **STOP. Use BFA default path.**
  Customer-context tokens require CIAM RFC 8693 exchange, which only the BFA Gateway's Token Broker can perform. Neither API keys nor service tokens carry customer identity. CES's Python sandbox cannot execute OAuth flows.
  _Examples: account balance, transaction history, fund transfers, card management._

- **No** → proceed to **Decision 2**.

---

### Decision 2 — Does the endpoint return PII or sensitive data?

> _PII = any personally identifiable information (names, account numbers, addresses, transaction details). Sensitive = classified above "public" in Acme Bank's data classification policy._

- **Yes** → 🔴 **STOP. Use BFA default path.**
  The exception lane bypasses BFA's Response PEP (data redaction/masking). Any PII in the Apigee response enters CES conversation context unredacted and persists in CES logs. DLP controls are not enforceable on the direct path.

- **No** → proceed to **Decision 3**.

---

### Decision 3 — Is the upstream backed by Glue (WSO2)?

> _Glue-backed APIs are accessed via Hybrid Interconnect from on-premises data centers. CES has no direct network path to Glue._

- **Yes** → 🔴 **STOP. Use BFA default path.**
  Glue access requires BFA → Interconnect → Glue (WSO2) → Core Banking. There is no alternative network path from CES or Apigee to Glue. The exception lane does not solve Glue connectivity.

- **No** → proceed to **Decision 4**.

---

### Decision 4 — What authentication does the Apigee endpoint require?

> _Determine the minimum credential the Apigee proxy validates._

#### Path A — No auth or API-key-only

> _The endpoint validates an Apigee API key (application identity) but does not require a bearer token with service or user claims._

- Proceed to **Decision 5A (Tier 1)**.

#### Path B — Service-level authentication (CIDP/EIDP token)

> _The endpoint requires a CIDP- or EIDP-issued service token (JWT with service identity claims) but does NOT require customer-context scopes._

- Proceed to **Decision 5B (Tier 2)**.

#### Path C — Customer-scoped bearer token

> _The endpoint requires a customer-context bearer token (user-delegated scopes, CIAM-issued)._

- 🔴 **STOP. Use BFA default path.**
  This is equivalent to Decision 1 = Yes. CES cannot obtain customer-scoped tokens.

---

### Decision 5A — Tier 1 Viability Gate (API Key)

All conditions below must be satisfied. If **any** condition fails → 🔴 **Use BFA default path.**

| # | Condition | Rationale |
|---|-----------|-----------|
| 1 | Endpoint is **read-only** (GET, no side effects) | Exception lane is approved for reads only; mutations require full PEP/PDP chain |
| 2 | Endpoint returns **zero PII** (re-confirm Decision 2) | No Response PEP on direct path; PII cannot be redacted |
| 3 | API key can be stored in **Secret Manager** and referenced from CES tool config | CES Python sandbox cannot execute dynamic credential retrieval; Secret Manager reference is the only viable mechanism |
| 4 | Apigee proxy enforces **rate limiting, threat protection, and audit logging** independently of BFA | Apigee must be a standalone security boundary for exception-lane calls |
| 5 | **≤ 3 active exceptions** exist across all CES tools | Governance cap per security policy |
| 6 | Security team grants **time-bound approval** (max 90 days, renewable with review) | Mandatory governance gate |

- **All conditions met** → 🟢 **Tier 1 Exception Lane MAY be approved.**
  - Token source: Apigee API key via Secret Manager (BYOK).
  - CES tool config: API key reference, no token flow, no callback logic.
  - Hop count: CES → Apigee (~1 hop, zero token-exchange overhead).
  - Governance: register in Exception Registry, set 90-day expiry, schedule review.

- **Any condition fails** → 🔴 **Use BFA default path.**

---

### Decision 5B — Tier 2 Viability Gate (Token Broker)

All conditions below must be satisfied. If **any** condition fails → 🔴 **Use BFA default path.**

| # | Condition | Rationale |
|---|-----------|-----------|
| 1 | Endpoint is **read-only** (GET, no side effects) | Same as Tier 1 |
| 2 | Endpoint returns **low or zero PII** (re-confirm Decision 2) | No Response PEP; tolerable only if PII risk is minimal and documented |
| 3 | Token Broker Service is **operational** in the BFA Landing Zone | Tier 2 depends on Token Broker availability; if not yet deployed, fall back to BFA default path or Tier 1 |
| 4 | CES tool can make a **lightweight HTTP pre-call** to Token Broker before the Apigee call | CES Python sandbox must support a simple HTTP request to a Landing Zone endpoint |
| 5 | The Token Broker can mint a **short-lived CIDP or EIDP service token** for the target Apigee proxy | Token Broker must have the credential material and trust relationship for the target endpoint's IdP |
| 6 | Apigee proxy enforces **rate limiting, threat protection, claim validation, and audit logging** independently | Apigee validates the Token-Broker-issued token and enforces its own security controls |
| 7 | **≤ 3 active exceptions** exist across all CES tools | Governance cap (shared with Tier 1) |
| 8 | Security team grants **time-bound approval** (max 90 days, renewable with review) | Mandatory governance gate |
| 9 | **Documented escalation path** exists: if the endpoint later requires customer-context auth, the tool migrates back to BFA within one sprint | Prevents governance debt accumulation |

- **All conditions met** → 🟡 **Tier 2 Exception Lane MAY be approved.**
  - Token source: CIDP/EIDP service token via Token Broker pre-call.
  - CES tool flow: CES → Token Broker → CES → Apigee (~2 hops).
  - Latency: faster than full BFA path (~3-4 hops) because PEP/PDP/adapter routing is bypassed.
  - Governance: register in Exception Registry, set 90-day expiry, document Token Broker dependency.
  - ⚠️ **Note:** Tier 2 reintroduces a BFA Landing Zone dependency. If Token Broker is unavailable, the exception-lane call fails.

- **Any condition fails** → 🔴 **Use BFA default path.**

---

## Decision Flow Summary

```
START: "Should this tool use BFA default path or request an exception?"
  │
  ├─ D1: Customer-context auth needed?
  │   ├─ Yes → 🔴 BFA (RFC 8693 exchange required)
  │   └─ No ↓
  │
  ├─ D2: Returns PII or sensitive data?
  │   ├─ Yes → 🔴 BFA (Response PEP required for redaction)
  │   └─ No ↓
  │
  ├─ D3: Upstream backed by Glue?
  │   ├─ Yes → 🔴 BFA (Interconnect path only via BFA)
  │   └─ No ↓
  │
  ├─ D4: Apigee endpoint auth requirement?
  │   ├─ API-key-only → D5A (Tier 1 gate)
  │   ├─ CIDP/EIDP service token → D5B (Tier 2 gate)
  │   └─ Customer-scoped token → 🔴 BFA
  │
  ├─ D5A: Tier 1 viability (6 conditions)
  │   ├─ All met → 🟢 Tier 1 Exception (API key via Secret Manager)
  │   └─ Any fails → 🔴 BFA
  │
  └─ D5B: Tier 2 viability (9 conditions)
      ├─ All met → 🟡 Tier 2 Exception (Token Broker pre-mint)
      └─ Any fails → 🔴 BFA
```

---

## Terminal Node Reference

| Terminal | Routing | Token Source | Hop Count | Governance |
|----------|---------|-------------|-----------|------------|
| 🔴 BFA default path | CES → SD → ILB → BFA → Adapters → Apigee/Glue | Full token model (CIAM, CIDP, EIDP via Token Broker) | ~3-4 hops | Standard — no exception needed |
| 🟢 Tier 1 exception | CES → Apigee (direct) | API key via Secret Manager | ~1 hop | Exception Registry, 90-day expiry, security sign-off |
| 🟡 Tier 2 exception | CES → Token Broker → CES → Apigee | CIDP/EIDP service token from Token Broker | ~2 hops | Exception Registry, 90-day expiry, security sign-off, escalation path documented |

---

## Anti-Patterns

| Anti-Pattern | Why It Fails |
|---|---|
| Embedding OAuth client-credentials flow in CES Python callback | CES sandbox does not guarantee arbitrary library access; complex token logic violates "lightweight and deterministic" callback contract |
| Passing customer tokens via `x-ces-session-context` header to Apigee | PII leakage risk — session context persists in CES logs; Apigee cannot validate customer-scoped tokens from CES identity |
| Using exception lane for write operations (POST/PUT/DELETE) | Exception governance requires read-only; mutations need AuthZ PDP validation in BFA |
| Approving > 3 simultaneous exceptions | Governance cap exists to prevent exception-lane becoming a shadow architecture |
| Exception without documented escalation path | If endpoint auth requirements change, there must be a one-sprint migration plan back to BFA; without it, governance debt accumulates |

---

## References

- [ADR-0104 Round 2 Review — Option C](ADR-0104-round-2-topology-review-2026-03-01.md)
- [Exception Lane Architecture Diagram](../diagrams/adr-0104-exception-lane-ces-to-apigee-direct.mmd)
