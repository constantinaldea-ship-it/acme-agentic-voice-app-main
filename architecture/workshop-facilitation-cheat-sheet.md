# CES-to-SoR Connectivity Workshop — Facilitation Cheat Sheet (1 Page)

**Date:** 2026-03-02  
**Purpose:** Drive an objective decision on connectivity topology and controls for CES ↔ SoR.

---

## 1) Decision frame (use at kickoff)

**Governing principle:**
> Agents never access domain systems directly; they call controlled capabilities through a policy-enforced mediation boundary.

**Options to score:**
- **Option 0:** Baseline/Direct CES → Apigee/SoR
- **Option A:** Strict single gateway mediation
- **Option B:** Federated adapters behind mediation policy boundary
- **Option C:** Gateway + tightly governed exceptions

**One-line statement per option:**
- **Option 0:** CES calls upstreams directly, maximizing speed of wiring but minimizing centralized policy control.
- **Option A:** CES calls one strict mediation gateway that enforces policy centrally before any domain access.
- **Option B:** CES calls a mediation boundary for policy, then domain-owned federated adapters execute per-domain logic.
- **Option C:** Same as Option A by default, with explicitly approved, time-bounded exception paths.

**Policy (definition):** Identity validation, authorization (PDP/PEP), obligation enforcement (e.g., step-up), data minimization/redaction, and auditable controls applied before any upstream data access.

**Option 0 reference:** [Option 0 Analysis: Direct CES → Apigee/SoR](reviews/option-0-direct-ces-to-apigee-analysis.md)

**Scoring scale:** ✅ = 3, ⚠️ = 1, ❌ = 0

---

## 2) Non-negotiables (fail-fast checks)

Reject or heavily penalize any option that cannot satisfy:
1. **Confidential-by-default** (private network paths, no public default exposure)
2. **Policy-before-data** (authN/authZ + obligations before upstream data access)
3. **No agent-to-upstream coupling** (CES never owns upstream routing)
4. **Data minimization** (PII redaction before CES-visible responses)
5. **Deterministic security controls** (step-up/PDP enforced in deterministic runtime)

---

## 3) Block 2 prompts (topology)

Use these exact prompts to avoid bias:
- Where does the **domain model** live, and who owns the business capability boundary?
- Should the mediation layer be **thin policy boundary** or include orchestration?
- Can adapters compose Apigee + Glue while still keeping CES abstracted from upstreams?
- Where are response normalization and redaction enforced (central vs adapter)?
- What blast radius is acceptable for a single adapter, domain, or platform failure?

**Tie-breaker rule:** if scores are close, choose the option with lower compliance/audit ambiguity and lower blast radius.

---

## 4) Block 3 prompts (identity/token)

Mandatory decisions to close:
- CIAM vs EIDP/CIDP role boundaries (customer-facing vs service-to-service)
- Who mints `subject_token` for RFC 8693 exchange?
- Token TTL + cache strategy (latency vs compromise window)
- Step-up enforcement location (central mediation vs per domain)
- Token Broker placement (embedded vs separate service)

**Done means:** a single token flow diagram exists with owners for each trust hop.

---

## 5) Exception lane guardrails (if Option C considered)

Allow exceptions only when all are true:
- read-only capability
- no restricted/PII payload to CES context
- explicit owner + expiry date
- central exception registry entry
- review cadence (e.g., 90 days)
- migration-back trigger defined

If any condition is missing → decision defaults to **No Exception**.

---

## 6) `x-ces-session-context` policy checkpoint

Decide explicitly (don’t defer):
- Which fields are allowlisted for injection?
- Which fields are stripped/redacted before upstream forwarding?
- Who owns schema/versioning for session context?
- How is context usage audited per request?

**Principle mapping:**
- Policy-before-data
- Confidential-by-default
- Data minimization

---

## 7) Red flags during discussion

- "We can fix governance later" (usually means never)
- "Just one direct path" without expiry/owner/migration-back
- "Model can enforce this" for deterministic security decisions
- Missing owner for token broker, policy engine, or adapter contracts
- Confusing transport choice (REST/OpenAPI vs MCP) with policy ownership model

---

## 8) End-of-workshop checklist (must leave with)

- Topology selected (0/A/B/C) with weighted rationale
- Dissent log captured
- Token architecture decision + trust chain owners
- Exception policy decision (adopt/reject) + registry owner
- RACI agreed for mediation layer, token broker, adapters
- ADR update actions with DRIs and deadlines
