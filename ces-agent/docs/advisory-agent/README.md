# Advisory Agent Docs

This directory is the canonical documentation home for the advisory appointment subagent.

## Document Map

- `implementation-plan/AGENT-007-appointment-context.md` — implementation baseline and decision freeze
- `implementation-plan/RESEARCH-FINDINGS.md` — Deutsche Bank booking research and field model
- `usecases/advisory-appointment-agent.md` — CES use-case specification
- `architecture/high-level-advisory-subagent-architecture.md` — high-level architecture for the advisory subagent
- `architecture/appointments-preparation-topology-decision-2026-03-15.md` — preparation-phase topology decision
- `architecture/diagrams/bfa-appointments-flow.mmd` — appointment-flow overview for the preparation topology
- `architecture/diagrams/bfa-appointments-flow-01-routing-and-intake.mmd` — root-agent routing and intake capture
- `architecture/diagrams/bfa-appointments-flow-02-location-and-slots.mmd` — booking-eligible location and slot search
- `architecture/diagrams/bfa-appointments-flow-03-booking-confirmation.mmd` — contact capture, confirmation, and create flow
- `architecture/diagrams/bfa-appointments-flow-04-lifecycle-management.mmd` — get, reschedule, and cancel lifecycle flow
- `architecture/reviews/appointments-flow-physical-path-and-latency-analysis.md` — future internal-integration target review, separate from the preparation baseline
- `advisory-promt.md` and `screenshots/` — source material from research

## Scope

These docs cover:

- appointment booking flows
- CES agent behavior and scope
- mock-backed preparation architecture
- contract-first build decisions

The executable CES configuration remains under `ces-agent/acme_voice_agent/...`.
