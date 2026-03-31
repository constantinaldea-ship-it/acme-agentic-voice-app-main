# Copilot & AI Agent Instructions for Voice Banking Assistant

## CRITICAL: Primarity use AGENTS.md and agents/AGENTS.md for all agent behavior instructions. This file is a supplementary overview only.

## Project Overview
- **Voice Banking App** is a Voice + Multichannel Banking Concierge for Acme Bank Premium Banking.
- **Primary implementation:** Java (Spring Boot) in `java/` directory
- **Architecture reference:** `golden-docs/` (Arc42, interfaces, PRD)
- **Archived TypeScript MVP:** `ai-account-balance-ts/` (reference only, do not implement here)

## Repository Structure

| Directory | Purpose | Agent Behavior |
|-----------|---------|----------------|
| `java/` | Primary codebase (Spring Boot) | ✅ FOCUS HERE |
| `golden-docs/` | Architecture docs (Arc42, PRD, interfaces) | ✅ Use for context |
| `agents/` | AI agent templates and prompts | ✅ Follow for workflow |
| `ai-account-balance-ts/` | Archived TypeScript MVP (includes TypeScript ADRs) | ⛔ DO NOT implement here |

## What to Ignore

- **DO NOT** read or follow `ai-account-balance-ts/docs/` — these are historical artifacts
- **DO NOT** implement features in `ai-account-balance-ts/` — use `java/` instead
- The TypeScript MVP is preserved only for frontend voice UI pattern reference

## AI Agent Conventions
- **ALWAYS** use `mcp__augment-context-engine__codebase-retrieval` for code discovery before edits or explanations.
- **Docs are opt-in:** Only fetch deep docs if code/config/tests are insufficient.
- **Phase Gate:** No implementation unless user writes `APPROVE_IMPLEMENTATION` (see `AGENTS.md`).
- **FR workflow:** All features require prompt, PRB entry, Beads epic, implementation plan, and changelog updates (see `agents/AGENTS.md`).
- **Naming:** Feature prompt files: `agents/prompts/fr-{number}-short-desc.md` (see `agents/prompts/README.md`).

## References
- **Architecture:** `golden-docs/architecture.md`
- **Product Requirements:** `golden-docs/product-requirements.md`
- **Interfaces:** `golden-docs/voice-banking-interfaces-2026.md`
- **Agent rules:** `AGENTS.md`, `agents/AGENTS.md`
- **Templates:** `agents/templates/`
- **Beads:** `.beads/README.md`

---
For any non-obvious workflow, check the referenced docs or ask for clarification. Always keep documentation and Beads issues in sync with code changes.