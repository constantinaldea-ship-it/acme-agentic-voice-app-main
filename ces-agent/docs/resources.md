# CES Plugin — Resource Inventory

> **Status:** Active  
> **Last Updated:** 2026-02-08  
> **Purpose:** Comprehensive inventory of all project resources relevant to building a VS Code extension for CES/CX Agent Studio agent development.  
> **Companion:** [initial.md](./initial.md) (feasibility analysis)

---

## How to Use This Document

Resources are organized by **priority tier** — how directly they inform the plugin's design and implementation.

| Tier | Meaning | Action |
|------|---------|--------|
| 🔴 **Critical** | Contains validation rules, schemas, or platform constraints the plugin must enforce | Read before writing any plugin code |
| 🟢 **High** | Architecture context, design patterns, or workflows the plugin should support | Read during design phase |
| 🟡 **Medium** | Supporting context that informs edge cases or future features | Reference as needed |
| ⚪ **Low** | Background information, not directly actionable for the plugin | Skim or skip |

---

## Tier 1 — 🔴 Critical (Must-Read Before Building)

These resources contain concrete validation logic, canonical schemas, or platform constraints that directly translate into plugin features.

### 1.1 Validation Scripts (Port to TypeScript)

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 1 | **Package Validator (Bash)** | `ces-agent/validate-agent.sh` | 595 | Comprehensive structural validation: `app.json`/`app.yaml` parsing, folder depth checks, naming conventions, cross-reference verification (rootAgent, globalInstruction, guardrails, agents, toolsets), unsupported directory detection, OpenAPI existence checks. **This is the primary source of validation rules to port.** |
| 2 | **Cross-Ref Validator (Python)** | `ces-agent/validate-package.py` | 107 | `app.json` ↔ filesystem consistency: verifies rootAgent exists as agent folder, globalInstruction file exists, all guardrails in array have matching folders, all agent folders have instruction.txt + JSON config. **Complements the bash validator with cross-file reference checks.** |

**Plugin mapping:** Every check in these scripts becomes a `vscode.Diagnostic` entry. The bash script's rule categories map to diagnostic severity levels.

### 1.2 CES Migration Plan (Canonical Schema Reference)

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 3 | **ADR-J010 Migration Plan** | `docs/implementation-plan/ADR-J010-migration-plan.md` | 2,411 | The "bible" — 8 deliverables defining every CES file format: directory structure conventions, agent JSON schema, instruction.txt patterns, toolset configuration, guardrail JSON schema, evaluation JSON schema (golden vs scenario), environment.json structure, migration steps. **Contains the canonical JSON schemas the plugin should validate against.** |

**Plugin mapping:** Extract JSON schemas from this document → use as `jsonSchema` validation targets. The directory structure spec becomes the workspace-level structural validator.

### 1.3 CES Platform Documentation

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 4 | **Platform Reference** | `docs/architecture/cx-agent-studio/platform-reference.md` | 436 | CES concepts (agents, tools, guardrails, evaluations), import/export format specification, resource lifecycle, API surface. **Defines the platform model the plugin must understand.** |
| 5 | **Developer Guide** | `docs/architecture/cx-agent-studio/developer-guide.md` | 332 | Local dev workflow, file structure conventions, testing patterns, deployment steps. **Documents the exact developer experience the plugin should improve.** |
| 6 | **Platform Caveats** | `docs/architecture/cx-agent-studio/caveats.md` | 127 | Known CES limitations, import failure modes, undocumented restrictions, workarounds. **Directly maps to warning-level diagnostics the plugin should surface proactively.** |
| 7 | **Cymbal Sample Analysis** | `docs/architecture/cx-agent-studio/sample-analysis.md` | 165 | Reverse-engineering of Google's canonical `cymbal-retail` sample agent package — file format expectations, naming conventions, structure patterns. **Ground-truth reference for "what CES actually accepts."** |

**Plugin mapping:** Caveats → warning diagnostics. Developer guide → feature requirements. Platform reference → hover documentation / IntelliSense descriptions. Cymbal analysis → test fixture patterns.

### 1.4 Live Agent Package (Target Workspace)

The plugin's primary target — the CES agent package it validates and assists with.

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 8 | **Root Manifest** | `ces-agent/acme_voice_agent/app.json` | 47 | Central hub: rootAgent, guardrails array, variable declarations, language settings, logging config, globalInstruction file reference, timezone, toolExecutionMode. **Every cross-reference check starts here.** |
| 9 | **Environment Config** | `ces-agent/acme_voice_agent/environment.json` | 8 | Service account and backend URL configuration. Plugin should validate structure + warn on `localhost` URLs. |
| 10 | **Global Instruction** | `ces-agent/acme_voice_agent/global_instruction.txt` | 64 | Global system prompt — persona, constraints, multi-brand awareness, frustration handling. Plugin should verify this file exists and is referenced from `app.json`. |

**Agent Configs:**

| # | Agent | JSON Config | Lines | Instruction | Lines |
|---|-------|-------------|-------|-------------|-------|
| 11 | `voice_banking_agent` (root) | `agents/voice_banking_agent/voice_banking_agent.json` | 8 | `instruction.txt` | 156 |
| 12 | `location_services_agent` | `agents/location_services_agent/location_services_agent.json` | 9 | `instruction.txt` | 266 |

**Guardrails:**

| # | Guardrail | Path | Lines |
|---|-----------|------|-------|
| 13 | Safety (content filter) | `guardrails/Safety_Guardrail_1770494214565/…json` | 22 |
| 14 | Prompt (injection detector) | `guardrails/Prompt_Guardrail_1770494214565/…json` | 11 |

**Evaluations (7 total):**

| # | Evaluation | Type | Path | Lines |
|---|------------|------|------|-------|
| 15 | `agent_handover_roundtrip` | Golden | `evaluations/agent_handover_roundtrip/…json` | 65 |
| 16 | `branch_search_munich` | Golden | `evaluations/branch_search_munich/…json` | 58 |
| 17 | `off_topic_redirect` | Golden | `evaluations/off_topic_redirect/…json` | 36 |
| 18 | `german_branch_search` | Golden | `evaluations/german_branch_search/…json` | 28 |
| 19 | `session_end_live_agent` | Golden | `evaluations/session_end_live_agent/…json` | 28 |
| 20 | `prompt_injection_attempt` | Scenario | `evaluations/prompt_injection_attempt/…json` | 8 |
| 21 | `edge_case_no_results` | Scenario | `evaluations/edge_case_no_results/…json` | 8 |

**Toolsets:**

| # | Toolset | Config | Lines | OpenAPI Schema | Lines |
|---|---------|--------|-------|----------------|-------|
| 22 | `location` | `toolsets/location/location.json` | 9 | `open_api_toolset/open_api_schema.yaml` | 225 |

**Plugin mapping:** This entire package serves as the **primary test fixture** during development. Every file is a validation target. The agent package totals ~18 files / ~1,038 lines.

### 1.5 Learning Registry (Hard-Won Platform Knowledge)

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 23 | **Changelog + Learnings** | `ces-agent/CHANGELOG.md` | 520 | Version history (v1.0–v1.3) with 10 cross-referenceable platform learnings (L-01 through L-10). These document CES behaviors that are NOT in official docs. |

**Key learnings the plugin must encode:**

| ID | Learning | Plugin Feature |
|----|----------|---------------|
| L-01 | `toolCall` in evals can't reference OpenAPI operations | ⚠️ Warning on `toolCall` referencing OpenAPI toolset ops in evaluation JSONs |
| L-03 | ZIP must use root directory wrapper | ⚠️ Warning in packaging workflow if flat structure detected |
| L-04 | Guardrail naming must match exactly (underscores → spaces) | 🔴 Error when `app.json` guardrail names don't match folder names |
| L-05 | New guardrail folders can't be created via ZIP import | ℹ️ Info diagnostic on non-platform guardrail folders |
| L-06 | `globalInstruction` file reference works in import | ✅ Validate file exists at reference path |
| L-07 | Evaluations CAN be added incrementally via ZIP | ℹ️ No warning needed for new eval folders |
| L-08 | Few-shot examples treated as strong training signals | 🟡 Linting hint if examples contain hardcoded optional params |
| L-09 | English city names fail API lookups | Domain-specific, not general plugin concern |
| L-10 | Incremental workflow essential | Packaging workflow guidance |

### 1.6 Extension Brief

| # | Resource | Path | Lines | What It Contains |
|---|----------|------|-------|------------------|
| 24 | **Feasibility Analysis** | `ces-plugin/initial.md` | 58 | Effort estimates (16–28h prototype, 60–100h production), rule complexity breakdown, architecture recommendation (hybrid schema + custom engine), VS Code API mapping. **The starting brief.** |

---

## Tier 2 — 🟢 High (Architecture & Design Context)

These resources inform the plugin's design decisions and feature scope.

### 2.1 Architecture Decision Records (ADRs)

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 25 | **ADR-J010 CES Migration Strategy** | `docs/adr/ADR-J010-cx-agent-studio-migration-strategy.md` | 526 | The foundational "why" — Flow-based vs Native Generative Agents decision. Explains why the native CES format was chosen, which defines the file formats the plugin validates. |
| 26 | **ADR-J009 BFA in CES Context** | `docs/adr/ADR-J009-bfa-ces-architecture.md` | 983 | BFA becomes essential (not optional) with CES — webhook fulfillment layer design. Informs how `environment.json` and toolset backend URLs should be validated. |
| 27 | **ADR-J008 BFA Analysis** | `docs/adr/ADR-J008-backend-for-agents-analysis.md` | 955 | Comprehensive analysis of Backend-for-Agents pattern. Context for understanding toolset → backend connections. |
| 28 | **ADR-J006 Agent vs Skills** | `docs/adr/ADR-J006-agent-vs-skills-architecture.md` | 367 | Decides on agent-based pattern — affects how CES agents map to the multi-agent hierarchy. |

### 2.2 CES Design Patterns & Mapping

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 29 | **Design Patterns** | `docs/architecture/cx-agent-studio/design-patterns.md` | 635 | Multi-agent orchestration, tool patterns, guardrail patterns, evaluation patterns. **Could power snippets/templates in the plugin.** |
| 30 | **Banking Use Case Mapping** | `docs/architecture/cx-agent-studio/banking-use-case-mapping.md` | 201 | Maps banking use cases to CES agent capabilities — shows how domain requirements translate to CES resources. |
| 31 | **CES API Changes 2026** | `ces-agent/CES-API-CHANGES-2026.md` | 170 | Playbooks (Task/Routine), tool types, webhook contracts, model support matrix. **Defines what CES features the plugin should support in future versions.** |

### 2.3 Agent Use Case Specs

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 32 | **Location Services Agent** | `docs/agent-use-cases/location-services-agent.md` | 660 | Persona "Brigitte", 8+ voice scenarios, intent impact analysis. Shows how use cases drive CES instruction design. |
| 33 | **Use Cases Index** | `docs/agent-use-cases/README.md` | 68 | Methodology, persona library, document relationships. |

### 2.4 Master Agent Plan

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 34 | **Agent Architecture Master** | `docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md` | 598 | Full 12-agent landscape, priority matrix, dependency graph. Informs how many agents the plugin should expect to support. |

### 2.5 Packaging & Deployment Scripts

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 35 | **Deploy Agent Script** | `ces-agent/deploy-agent.sh` | 152 | Versioned ZIP packaging with import instructions. **Plugin could offer a "Package for CES" command that replaces this script.** |
| 36 | **OpenAPI Converter** | `ces-agent/convert-openapi.sh` | 30 | JSON→YAML conversion for toolset schemas. **Plugin could auto-convert on save.** |

### 2.6 Improvement Backlog

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 37 | **Improvement Backlog** | `ces-agent/IMPROVEMENT-BACKLOG.md` | 250 | 6 improvement items with gap analysis. Shows what the plugin's validation should catch proactively (e.g., missing `modelSettings`, missing `evaluationMetricsThresholds`). |

### 2.7 CES Agent README

| # | Resource | Path | Lines | Relevance to Plugin |
|---|----------|------|-------|---------------------|
| 38 | **Architecture Overview** | `ces-agent/README.md` | 291 | Multi-agent architecture diagrams, directory structure, deployment instructions. Good overview for plugin contributors. |

---

## Tier 3 — 🟡 Medium (Supporting Context)

| # | Resource | Path | Lines | Relevance |
|---|----------|------|-------|-----------|
| 39 | ADR-J004 Google ADK Selection | `docs/adr/ADR-J004-google-adk-selection.md` | 375 | Background on why CES was explored as alternative to ADK |
| 40 | ADR-J007 DLP/PII Strategy | `docs/adr/ADR-J007-dlp-pii-protection-strategy.md` | 415 | Informs PII guardrail validation rules |
| 41 | CES vs Alternatives | `docs/architecture/cx-agent-studio/cx-agent-studio-vs-alternatives.md` | 59 | Quick comparison — not actionable for plugin |
| 42 | BFA README | `docs/architecture/bfa/README.md` | 162 | Backend service architecture overview |
| 43 | BFA API Contracts | `docs/architecture/bfa/api/request-response-contracts.md` | 399 | Request/response schemas for backend services |
| 44 | BFA Root Agent Orchestration | `docs/architecture/bfa/adr/ADR-BFA-006-root-agent-orchestration-pattern.md` | 722 | How CES root agent maps to BFA orchestration |
| 45 | BFA Agent Registration | `docs/architecture/bfa/adr/ADR-BFA-005-agent-registration.md` | 512 | Agent registration pattern in BFA |
| 46 | BFA Agentic-Aware Design | `docs/architecture/bfa/adr/ADR-BFA-001-agentic-aware-design.md` | 873 | Agentic-aware BFA design decisions |
| 47 | Security Architecture | `golden-docs/security-architecture.md` | 1,026 | Auth, session mgmt, PII handling — informs security guardrail rules |
| 48 | Legacy Deploy Script | `ces-agent/deploy.sh` | 71 | Older packaging approach (superseded by deploy-agent.sh) |

---

## Tier 4 — ⚪ Low (Background / Not Directly Actionable)

| # | Resource | Path | Lines | Notes |
|---|----------|------|-------|-------|
| 49–54 | BFA ADRs (002–004) | `docs/architecture/bfa/adr/ADR-BFA-00{2,3,4}.md` | ~1,383 | Operation registry, resource-oriented API, PFM migration |
| 55–57 | BFA Diagrams | `docs/architecture/bfa/diagrams/*.md` | ~75 | Component, sequence, deployment diagrams |
| 58–59 | BFA Security | `docs/architecture/bfa/security/*.md` | ~58 | Consent verification, legitimation flow |
| 60 | BFA Migration Plan | `docs/architecture/bfa/MIGRATION-PLAN.md` | 323 | BFA migration roadmap |
| 61–66 | Agent Templates | `agents/templates/*.md` | ~varies | Project workflow templates (ADR, FR, spec, impl plan) — not CES-specific |

---

## Summary Statistics

| Category | Files | Total Lines | Primary Plugin Use |
|----------|-------|-------------|-------------------|
| Validation scripts (bash + python) | 2 | 702 | → TypeScript diagnostics engine |
| CES agent package (live) | 18 | ~1,038 | → Test fixture + validation target |
| CES platform docs | 8 | 2,110 | → Platform model, caveats, patterns |
| CES migration plan | 1 | 2,411 | → Canonical file schemas |
| ADRs (CES-relevant) | 4 | 2,831 | → Architecture decisions |
| Changelog + learnings | 1 | 520 | → Platform gotcha rules |
| Backlog + README | 2 | 541 | → Feature gap analysis |
| Packaging scripts | 3 | 253 | → "Package for CES" command |
| Agent use cases | 2 | 728 | → Instruction design patterns |
| Agent master plan | 1 | 598 | → Agent landscape scope |
| BFA architecture | 9 | 4,507 | → Backend validation context |
| Extension brief | 1 | 58 | → Starting requirements |
| **TOTAL** | **~52** | **~16,297** | |

---

## Recommended Reading Order for Plugin Development

### Phase 1 — Understand the Problem (Day 1)

```
1. ces-plugin/initial.md                          (58 lines)   — Feasibility brief
2. ces-agent/CHANGELOG.md → "Platform Learnings"  (focus L-01–L-10) — Gotchas
3. docs/architecture/cx-agent-studio/caveats.md   (127 lines)  — Platform limitations
4. docs/architecture/cx-agent-studio/developer-guide.md (332 lines) — Current workflow
```

### Phase 2 — Learn the Rules (Day 1–2)

```
5. ces-agent/validate-agent.sh                    (595 lines)  — All validation rules
6. ces-agent/validate-package.py                  (107 lines)  — Cross-ref checks
7. ces-agent/acme_voice_agent/app.json            (47 lines)   — Central manifest
8. docs/architecture/cx-agent-studio/platform-reference.md (436 lines) — CES model
```

### Phase 3 — Understand the Schemas (Day 2–3)

```
9.  docs/implementation-plan/ADR-J010-migration-plan.md  (2,411 lines) — File schemas
10. docs/architecture/cx-agent-studio/sample-analysis.md (165 lines)   — Canonical format
11. ces-agent/acme_voice_agent/ (browse all files)       (~1,038 lines) — Live examples
```

### Phase 4 — Design Features (Day 3+)

```
12. docs/architecture/cx-agent-studio/design-patterns.md (635 lines) — Snippets/templates
13. ces-agent/deploy-agent.sh                            (152 lines) — Packaging workflow
14. ces-agent/IMPROVEMENT-BACKLOG.md                     (250 lines) — Validation gaps
```

---

## Validation Rule Sources → Plugin Feature Map

A cross-reference of where each plugin feature's logic originates:

| Plugin Feature | Primary Source | Secondary Source | Lines to Port |
|----------------|---------------|-----------------|---------------|
| `app.json` schema validation | `validate-agent.sh` §1–2 | Migration plan §D1 | ~80 |
| `rootAgent` existence check | `validate-package.py` L20–35 | `validate-agent.sh` §3 | ~20 |
| `globalInstruction` file ref | `validate-package.py` L36–45 | Learning L-06 | ~15 |
| Guardrail name ↔ folder match | `validate-agent.sh` §5 | Learning L-04 | ~30 |
| Agent folder structure | `validate-agent.sh` §4 | Migration plan §D2 | ~50 |
| Toolset OpenAPI existence | `validate-agent.sh` §6 | Migration plan §D4 | ~25 |
| Evaluation JSON schema | Migration plan §D6 | Learning L-01 | ~40 |
| Nesting depth constraints | `validate-agent.sh` §7 | Caveats doc | ~15 |
| Unsupported directory warning | `validate-agent.sh` §8 | Caveats doc | ~10 |
| `environment.json` validation | `validate-agent.sh` §9 | Learning L-05 | ~15 |
| Localhost URL warning | `validate-package.py` L80+ | — | ~10 |
| ZIP structure check | Learning L-03 | `deploy-agent.sh` | ~20 |
| **Total estimated** | | | **~330 lines TS** |