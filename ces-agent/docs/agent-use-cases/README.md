# Agent Use Case Specifications

> **Voice Banking App (AI Banking Voice) — Persona-Driven Use Case Analysis**  
> **Last Updated:** 2026-02-07

---

## Purpose

This directory contains **persona-driven use case specifications** for each AI Functional Agent (Component E) in the Voice Banking App. Each document captures:

- **Realistic voice interaction scenarios** grounded in customer personas
- **Data and service dependencies** required to fulfil the use cases
- **Intent impact analysis** for both ADK and CES/Dialogflow CX implementations
- **Conversational agent design foundations** (tools, flows, entities, webhooks)
- **Accessibility and inclusion recommendations**

These specs bridge the gap between Functional Requirements (FRDs) and implementation by ensuring the agent designs are validated against real human needs — not just technical capabilities.

---

## Methodology

Each spec follows a **divergent-thinking persona analysis**:

1. **Review** the existing agent implementation and data sources
2. **Adopt** a representative customer persona (demographics, digital literacy, emotional state)
3. **Generate** 8–10 realistic voice interaction scenarios
4. **Identify** gaps between current implementation and persona needs
5. **Produce** structured deliverables (use cases, dependencies, intents, design guidance, accessibility)

---

## Document Index

| Agent ID | Agent Name | Persona | Status | Document |
|----------|------------|---------|--------|----------|
| `location-services` | LocationServicesAgent | Brigitte (60, low digital literacy, ex-Postbank) | ✅ Complete | [location-services-agent.md](./location-services-agent.md) |
| `advisory-appointment` | AdvisoryAppointmentAgent | Sabine (43, advisory booking, fallback-heavy) | 🟡 Draft baseline | [advisory-appointment-agent.md](./advisory-appointment-agent.md) |

---

## Relationship to Other Documents

```
docs/functional-requirements/FR-XXX.md      ← What the agent must do
    ↓
docs/agent-use-cases/<agent-id>.md          ← How real users will interact (THIS DIR)
    ↓
docs/implementation-plan/AGENT-XXX-*.md     ← How to build it
    ↓
java/voice-banking-app/.../agent/<domain>/  ← The code
```

---

## Personas Library

Personas used across agent use case specs:

| Persona | Age | Profile | Used In |
|---------|-----|---------|---------|
| **Brigitte** | 60 | Ex-Postbank customer, low digital literacy, prefers in-person banking, mobility concerns | `location-services` |
| **Sabine** | 43 | Existing or prospective customer needing advisory booking with clear confirmation and fallback | `advisory-appointment` |

> **Note:** Future specs should reuse existing personas where applicable and introduce new ones only when the agent serves a materially different user segment.

---

*Document Control: Created 2026-02-07 by *
