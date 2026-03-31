# Acme Banking - Omnichannel Runtime Topology Addendum

**Status:** Active  
**Date:** 2026-02-28  
**Owner:** Principal Cloud Architect (GCP)  
**Created by:** Codex on 2026-02-28

---

## 1) Purpose

This addendum aligns business channel strategy with the runtime architecture and integration boundaries for CX Agent Studio + BFA.

Primary sources:
- `docs/business/voice-banking-deck.md`
- `docs/business/ai-banking-voice-kickoff.md`
- `architecture/acme_cxstudio_architecture_facts_decisions.md`

---

## 2) Channel entry topology

All channels terminate at CX Agent Studio, then route through the same private tool path:

`Channel -> CX Agent Studio -> Service Directory PNA -> Internal LB -> BFA Gateway -> internal adapters -> Apigee/Glue/BFA read models`

### Supported channel surfaces

| Channel surface | Business label | Runtime entry | Notes |
|---|---|---|---|
| Telephone / voice | Phone / Call Center | CX Agent Studio voice channel | Primary H1 2026 MVP channel |
| Web/API | Online Banking (web browser) | CX Agent Studio API access | Server-mediated integration |
| Mobile app | Mobile Banking app | CX Agent Studio API access | Same backend contract as web |
| Email (evaluated) | E-Mail | Channel adapter -> CX Agent Studio | Marked as under evaluation in H1 2026 |
| Contact center handover | Handover to human on demand | BFA handover services | Deterministic escalation path |
| CRM / sales handoff | CRM / Smart Sales Engine | BFA integration adapter | Enabled by phase and policy |

---

## 3) Human handover and CRM path

### Human handover path
1. Domain agent triggers escalation criteria.
2. `human_handover_agent` composes context package.
3. BFA handover services route to queue/target destination.
4. Correlation IDs and policy decision IDs are preserved for audit.

### CRM integration path
1. Agent identifies eligible sales/service continuation action.
2. BFA integration adapter forwards structured payload to CRM/sales systems.
3. CRM update outcome is returned as a bounded response object to the agent.

---

## 4) Phase-aligned channel rollout

| Phase window | Channel scope | Capability boundary |
|---|---|---|
| Phase 1 (January 1, 2026 to July 31, 2026) | Telephone, online banking web, mobile app, email under evaluation | Read-first operations, guarded handover, no general write execution |
| Phase 2 (August 1, 2026 to December 31, 2026) | Mobile + digital expansion, selected service execution | Controlled write execution with step-up and stronger auth enforcement |
| 2027+ | Branch, messenger, public website, broader channel expansion | Wider product and advisory capability expansion |

---

## 5) Alignment to business architecture IDs

| Business component ID | Architecture realization |
|---|---|
| A (Channels) | Channel surfaces terminating at CX Agent Studio |
| B (Interaction Mode) | Voice/text/avatar experiences governed by channel profile |
| C (Contact Center Platform) | Handover and contact-center integration via BFA services |
| D (AI Orchestrator) | CX Agent Studio orchestration + root/domain agent routing |
| E (AI Functional Agents) | Canonical agent registry (`architecture/agent-registry.md`) |
| F (Integration / Service Layer) | BFA adapters and upstream connectors |
| G (Business Applications) | CRM and enterprise application integration via BFA |
| H (Data) | Apigee/Glue/BFA curated models/KB sources |
| I (Infrastructure) | GCP private landing zone with VPC-SC posture |

