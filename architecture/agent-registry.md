# Acme Banking - Canonical Agent Registry

**Status:** Active (architecture source of truth)  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Created by:** Codex on 2026-02-28

---

## 1) Purpose

This registry is the canonical source for:
- agent names
- architecture IDs
- scope and responsibility
- activation phase
- primary and fallback data sources
- business capability mapping

All architecture docs, ADRs, and diagrams must align to this registry.

---

## 2) Registry rules

1. **Naming**
   - Canonical runtime names use `snake_case`.
   - Architecture IDs use `AG-xxx`.

2. **Ingress**
   - CX Agent Studio calls only the **BFA Gateway** externally.
   - Domain adapters are internal to BFA.

3. **Data sourcing**
   - Data sources are selected per agent and phase.
   - BFA-hosted curated read models are first-class sources.
   - Apigee and Glue remain independent upstreams.

4. **Change control**
   - Update this file first when adding/renaming/re-scoping agents.
   - Then update facts, ADRs, and diagrams.

---

## 3) Canonical agent registry

| Arch ID | Canonical agent name | Scope / responsibility | Activation phase | Business mapping | Primary source | Secondary / fallback |
|---|---|---|---|---|---|---|
| AG-000 | voice_banking_agent | Root orchestration, intent-to-domain routing, safe response shaping | Phase 1 (H1 2026) | Category 0 (ecosystem), Component D/E | BFA internal orchestration | N/A |
| AG-001 | policy_guardrails_agent | Policy classification, refusal matrix, pre-sensitive gate checks | Phase 1 (H1 2026) | Category 0, Component E | BFA policy services | N/A |
| AG-002 | human_handover_agent | Deterministic escalation and handover payload packaging | Phase 1 (H1 2026) | Category 0, Component E | BFA handover services | N/A |
| AG-003 | location_services_agent | Branch/ATM lookup, opening-hours and location context | Phase 1 (H1 2026) | Category 2, Component E | BFA location read model (curated `branches.json`) | Glue location APIs |
| AG-004 | appointment_context_agent | Appointment context and scheduling support | Phase 2 (H2 2026) | Category 4, Component E |  Glue (owner-dependent) | Other upstream via BFA routing |
| AG-005 | fee_schedule_agent | Deterministic fee answers with effective-date controls | Phase 1 (H1 2026) | Category 2, Component E | BFA fees read model (curated) | Glue and/or BQ snapshot |
| AG-006 | knowledge_compiler_agent | Grounded informational responses from curated banking knowledge | Phase 1 (H1 2026) | Category 2, Component E/H | Curated KB index | Glue read-only KB APIs |
| AG-007 | banking_operations_agent | Sensitive banking operations (balances, transactions, statements) | Phase 2 (H2 2026) | Category 4, Component E/F | Apigee productized APIs | Glue APIs (explicitly authorized cases) |
| AG-008 | credit_card_context_agent | Credit-card context, limits, card-sensitive operations | Phase 2 (H2 2026) | Category 4, Component E/F | Apigee card APIs | Glue card APIs (rare) |
| AG-009 | product_information_agent | Product catalog and public informational product responses | Phase 1 (H1 2026) | Category 2, Component E/F | Apigee product APIs | Curated KB snapshots |
| AG-010 | mobile_app_assistance_agent | Mobile/online app how-to and feature assistance | Phase 1 (H1 2026) | Category 1, Component E/H | Curated KB | N/A |
| AG-011 | non_banking_services_agent | Non-banking partner or auxiliary service information | Phase 2 (H2 2026) | Category 2, Component E/F | Apigee partner APIs | Curated KB |
| AG-012 | personal_finance_agent | Personal finance insights and spend-analysis experiences | Phase 2+ | Category 5, Component E/H | Apigee + analytics read model | Analytics snapshots |

---

## 4) Legacy runtime identifiers

| Runtime artifact | Runtime ID | Status |
|---|---|---|
| Legacy Dialogflow flow-based location agent | `96c77548-182c-4e00-a0f5-f346afdfb553` | Legacy, decommission target per ADR-J010 migration |

