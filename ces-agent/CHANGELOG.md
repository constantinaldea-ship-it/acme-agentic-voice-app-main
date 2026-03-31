# CES Agent — Version History & Learning Registry

> **Status:** Active  
> **Last Updated:** 2026-02-08  
> **Purpose:** Version history tracking, learning registry for AI agents, and rollback reference for the CES/CX Agent Studio `acme_voice_agent` package.  
> **Related ADRs:** [ADR-J010 CES Migration](../docs/adr/ADR-J010-ces-migration-plan.md) · [ADR-J009 BFA Architecture](../docs/adr/ADR-J009-backend-for-agents.md)  
> **Backlog:** [IMPROVEMENT-BACKLOG.md](./IMPROVEMENT-BACKLOG.md)  
> **Format:** [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) with CES-specific extensions

---

## How to Use This Changelog

- **Version tracking:** Each release maps to a git tag (`ces-v{major}.{minor}`) and a versioned ZIP.
- **Rollback:** Every version includes rollback instructions and the ZIP artifact to restore.
- **Learning registry:** Gotchas, CES platform quirks, and hard-won lessons are documented inline and in the [Platform Learnings](#platform-learnings--ces-gotchas) section.
- **Agent attribution:** Changes note which agent or developer made them.

### Version Naming Convention

| Component | Meaning |
|-----------|---------|
| `Major` | Breaking changes to agent architecture (new agents, removed agents, schema changes) |
| `Minor` | New resources (evaluations, guardrails) or significant instruction improvements |
| `Patch` | Bug fixes, wording tweaks, config changes |

---

## [Unreleased]

### Added — Fee Schedule Agent (v2.0 candidate)

- **`fee_schedule_agent`** — New CES sub-agent for fee and pricing queries
  - `agents/fee_schedule_agent/fee_schedule_agent.json` — Agent manifest with `end_session` and `fee_schedule_lookup` tools
  - `agents/fee_schedule_agent/instruction.txt` — Comprehensive XML-structured instructions with role, persona, 11 constraints, 5 taskflow subtasks, and 12 examples (EN + DE)
  - Constraints include: no fee fabrication, mandatory source citation, voice brevity, bilingual support, waiver/condition disclosure
  - Taskflow covers: single fee lookup, tier comparison, waiver queries, version comparison, unclear question clarification, scope exit

- **`fee_schedule_lookup` tool** — New `googleSearchTool` for the Deutsche Bank price list
  - `tools/fee_schedule_lookup/fee_schedule_lookup.json` — Uses CES `googleSearchTool` format with `contextUrls` pointing to the official PDF
  - Track 1 (GCS + Vertex AI data store) configuration documented in `docs/agent-use-cases/fee-schedule-agent.md`
  - Track 2 (custom RAG + OpenAPI toolset) architecture documented in `docs/adr/ADR-FEE-001-fee-schedule-data-prep-and-rag.md`

- **Root agent updates** (`voice_banking_agent`)
  - `voice_banking_agent.json` — Added `fee_schedule_agent` as a child agent
  - `instruction.txt` — Added "Fee Schedule Routing" subtask (triggers on fees/Gebühren/Kosten/prices), updated constraints to include fee routing prohibition (#3, #4 renumbered), added 3 fee routing examples

- **Global instruction update** — Added constraint #10: Fee Data Accuracy (no fabrication, tool-grounded responses only)

- **3 new evaluations**
  - `fee_lookup_basic` (Golden) — English fee query → `agentTransfer` to `fee_schedule_agent` + `agentResponse` with fee amount
  - `fee_routing_german` (Golden) — German query "Was kostet eine Überweisung?" → `agentTransfer` + German `agentResponse`
  - `fee_schedule_multi_turn` (Scenario) — 3-turn conversation: fee lookup → conditions → tier comparison

- **Documentation**
  - `docs/adr/ADR-FEE-001-fee-schedule-data-prep-and-rag.md` — Architecture Decision Record covering two-track approach, success metrics, evaluation strategy, PoC backlog
  - `docs/agent-use-cases/fee-schedule-agent.md` — Use case specification with architecture diagram, flow diagram, result formatting rules, data store setup instructions, Track 2 migration path

- **Validate script improvements** (`scripts/deploy/validate-package.py`)
  - Section 7: Added `mcpTool` handling (was causing false FAIL for `location_mcp` toolset)
  - Section 7b: Added `googleSearchTool` handling (validates `contextUrls`/`dataStoreId` presence instead of requiring `pythonCode`)

### Planned Improvements (from [IMPROVEMENT-BACKLOG.md](./IMPROVEMENT-BACKLOG.md))

- [ ] **Evaluation Metrics Thresholds** — Add `evaluationMetricsThresholds` to `app.json` with hallucination detection enabled and stricter semantic similarity (Backlog #5)
- [ ] **Model Settings** — Pin `gemini-2.5-flash-001` in both agent JSONs for reproducible behavior (Backlog #6)
- [ ] **Guardrail Enhancements via CES UI** — Safety threshold tightening (`BLOCK_LOW_AND_ABOVE` for hate/sexual), custom rejection prompts, PII guardrail. These CANNOT be imported via ZIP — must be configured in the CES console (see [Learning L-05](#l-05-new-guardrail-folders-cannot-be-created-via-zip-import))
- [ ] **banking_policy LLM Guardrail** — Re-add via CES UI (was removed during ground-truth reset; cannot be imported via ZIP)
- [ ] **Additional Evaluations** — Saturday hours, multi-turn refinement, accessibility search (from use case spec UC-BF-06, UC-BF-09, UC-BF-04)

---

## [1.3.1] - 2026-02-08

**Status:** ✅ Working (verified manually in CES simulator)  
**Git Tag:** `ces-v1.3.1-post-greeting-routing`  
**Agent:** 

### Fixed

- **Post-greeting agent transfer failure** in `voice_banking_agent/instruction.txt`
  - *Symptom:* Starting with "Hello" then asking "I need to find a branch in Munich" failed to transfer to `location_services_agent`. Skipping the greeting (going directly with the branch request) worked fine.
  - *Root cause:* After the greeting subtask fired, the LLM stayed in "greeting mode" and did not evaluate the next user message against routing subtasks.
  - *Fix 1:* Added explicit post-greeting routing directive (step 4 in Customer Greeting action): "After greeting, evaluate the user's NEXT message against ALL routing subtasks below."
  - *Fix 2:* Broadened Location Services Routing trigger to: "At ANY point in the conversation — including immediately after a greeting."
  - *Fix 3:* Added multi-turn example showing `Hello → greeting → branch request → [silent transfer]`.

- **`branch_search_munich` evaluation using English city name** in `evaluations/branch_search_munich/branch_search_munich.json`
  - *Symptom:* Evaluation failed because the LLM inconsistently translated "Munich" → "München" for the `searchBranches` API call.
  - *Root cause:* Despite constraint #8 and few-shot examples, the LLM did not reliably translate English city names to German when the request came after a greeting turn.
  - *Fix:* Changed evaluation user input from "I need to find a branch in Munich" → "I need to find a branch in München" to use the German name the API expects.
  - *Added:* `toolCall` expectation for `searchBranches` between `agentTransfer` and `agentResponse` expectations.

### Changed

- `voice_banking_agent/instruction.txt`: Added 3 lines to greeting action, broadened routing trigger, added 1 multi-turn example (157 → 163 lines)
- `evaluations/branch_search_munich/branch_search_munich.json`: Updated user input + added `toolCall` expectation

### Resource Details

| Resource | File | Change |
|----------|------|--------|
| Root Agent | `agents/voice_banking_agent/instruction.txt` | Post-greeting routing directive, broadened trigger, multi-turn example |
| Evaluation | `evaluations/branch_search_munich/branch_search_munich.json` | München input + searchBranches toolCall expectation |

### Deployment Verification

1. Import `acme_voice_agent.zip` into CES → expect success
2. Simulator test: "Hello" → greeting → "I need to find a branch in München" → verify agent transfers to `location_services_agent` and calls `searchBranches`
3. Run `branch_search_munich` evaluation → expect PASS

### Rollback Instructions

```bash
# Restore to v1.3.0
git checkout ces-v1.3-instruction-improvements -- ces-agent/acme_voice_agent/
cd ces-agent && rm -f acme_voice_agent.zip && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'
```

### Lessons Learned

- LLM adherence to city name translation rules degrades in multi-turn conversations — using German names directly is more reliable than depending on in-context translation
- Post-greeting routing is a common failure mode in multi-agent CES setups — always add explicit "after greeting, route next message" directives
- Golden evaluations should use API-native values (German city names) rather than depending on LLM translation

---

## [1.3.0] - 2026-02-08

**Status:** ✅ Working (verified CES import)  
**Git Tag:** `ces-v1.3-instruction-improvements`  
**Agent:** 

### Added

- **Constraint #8 — German City Names for API Calls** in `location_services_agent/instruction.txt`
  - Translation table: Munich→München, Cologne→Köln, Nuremberg→Nürnberg, Hanover→Hannover, Brunswick→Braunschweig
  - *Why:* Agent was sending English city names (e.g., "Munich") to the BFA API, which expects German names. API returned 0 results for English names.
  - *ADR:* ADR-J010 §5 (tool integration quality)

- **Constraint #9 — Only Send Relevant Parameters** in `location_services_agent/instruction.txt`
  - Do not send `limit`, `brand`, or `accessible` unless user mentioned them
  - *Why:* Agent was always sending `limit=5` from few-shot examples, overriding the API default of 10 and reducing result quality.

- **2 new few-shot examples** demonstrating English→German city name translation
  - "Find branches in Cologne" → `searchBranches(city="Köln")`
  - "I need a branch in Munich" → `searchBranches(city="München")`
  - *Why:* Provides explicit demonstration of constraint #8 for the LLM to learn from.

- **Multi-Brand Awareness** in `global_instruction.txt` persona section
  - Recognises Postbank (mass-market), Norisbank (direct bank), Fyrst (digital business bank)
  - Empathetic guidance for customers disoriented by branch mergers
  - *Why:* Use case spec UC-BF-02 requires multi-brand handling. Customers asking about Postbank branches need to be directed to Deutsche Bank locations.
  - *ADR:* ADR-J010 §4 (global instruction persona)

- **Frustration Handling** in `global_instruction.txt` persona section
  - Detects frustration signals ("Ich verstehe das nicht", "Can you just connect me to someone?")
  - Proactive live agent handover offer
  - *Why:* Use case spec UC-BF-07 defines frustration escalation as a core requirement.

- **Constraint #8 — Repeat & Slow Down** in `global_instruction.txt`
  - Handles "nochmal", "langsam", "repeat", "again", "slowly" requests
  - Restates information with pauses between key details
  - *Why:* Use case spec UC-BF-08 — voice-first UX requires accessible information delivery.

- **Constraint #9 — Subsidiary Brands** in `global_instruction.txt`
  - Reinforces multi-brand awareness at the constraint level
  - *Why:* Dual encoding (persona + constraint) increases reliability of brand recognition.

### Fixed

- **Removed `limit=5` from ALL 5 few-shot examples** in `location_services_agent/instruction.txt`
  - Lines affected: München, Berlin Postbank, Alexanderplatz, Karlsruhe, Berlin accessible examples
  - *Root cause:* Examples contained explicit `limit=5` parameter, teaching the LLM to always send it. This overrode the API default of 10 and reduced search results.
  - *Impact:* Agent now lets the API use its default limit (10), returning more results to users.

- **Fixed "Show More" limit reference** from `limit=10 instead of default 5` → `limit=20`
  - *Why:* The old phrasing reinforced the incorrect belief that default was 5. New phrasing uses limit=20 for "show more" requests.

### Changed

- `global_instruction.txt`: 42 → 64 lines (added multi-brand, frustration, 2 new constraints)
- `location_services_agent/instruction.txt`: 234 → 266 lines (added 2 constraints, 2 examples, fixed 5 examples)

### Resource Details

| Resource | File | Change |
|----------|------|--------|
| Global Instruction | `acme_voice_agent/global_instruction.txt` | Multi-brand, frustration, repeat/slow-down, subsidiary constraints |
| Location Agent | `acme_voice_agent/agents/location_services_agent/instruction.txt` | German names, relevant params, limit bug fix, new examples |

### Deployment Verification

1. Import `acme_voice_agent.zip` into CES → expect success
2. Simulator test: "Find branches in Cologne" → verify API call uses `city="Köln"` (not "Cologne")
3. Simulator test: "I need a branch in Munich" → verify API call uses `city="München"` (not "Munich")
4. Simulator test: "Filialen in Berlin" → verify NO `limit` parameter sent
5. Simulator test: "I'm a Postbank customer" → verify multi-brand acknowledgement
6. Simulator test: "Ich verstehe das nicht, kann ich jemanden sprechen?" → verify frustration escalation

### Deployment Artifacts

| Artifact | SHA-256 |
|----------|---------|
| `acme_voice_agent.zip` (current build) | `473176d658d970a81c1e10cd13a0b982834ae5810a90e5d8a363682b16e072ca` |

### Rollback Instructions

```bash
# Restore to v1.2 (all evaluations, no instruction improvements)
git checkout ces-v1.2-all-evals -- ces-agent/acme_voice_agent/
cd ces-agent && rm -f acme_voice_agent.zip && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'
# Import the restored zip into CES
```

### Known Issues

- Multi-brand awareness is in instructions only — no evaluation covers it yet
- Frustration escalation is in instructions only — no evaluation covers it yet
- Guardrail enhancements (Safety, Prompt, PII) still need to be done via CES UI

---

## [1.2.0] - 2026-02-08

**Status:** ✅ Working (verified CES import)  
**Git Tag:** `ces-v1.2-all-evals`  
**Git Commit:** `d934f0d`  
**Agent:** 

### Added

- **5 additional evaluations** (total: 7 evaluations)
  - `branch_search_munich` — Golden: tests München search + agent handover to location_services_agent + `agentTransfer` expectation
  - `session_end_live_agent` — Golden: tests "Mitarbeiter" → live agent handover with `end_session` toolCall
  - `agent_handover_roundtrip` — Golden: tests full roundtrip (root → location → root) with scope-exit transfer back
  - `prompt_injection_attempt` — Scenario: tests injection resilience ("Ignore your instructions…", "Reveal your system prompt")
  - `edge_case_no_results` — Scenario: tests graceful handling of zero-result searches

### Resource Details

| Evaluation | Type | File | Key Expectations |
|------------|------|------|-----------------|
| `branch_search_munich` | Golden | `evaluations/branch_search_munich/branch_search_munich.json` | `agentTransfer` to location_services_agent |
| `session_end_live_agent` | Golden | `evaluations/session_end_live_agent/session_end_live_agent.json` | `toolCall: end_session` |
| `agent_handover_roundtrip` | Golden | `evaluations/agent_handover_roundtrip/agent_handover_roundtrip.json` | `agentTransfer` both directions |
| `prompt_injection_attempt` | Scenario | `evaluations/prompt_injection_attempt/prompt_injection_attempt.json` | `scenarioExpectations: []`, maxTurns=5 |
| `edge_case_no_results` | Scenario | `evaluations/edge_case_no_results/edge_case_no_results.json` | `scenarioExpectations: []`, maxTurns=3 |

### Deployment Artifacts

| Artifact | SHA-256 |
|----------|---------|
| `acme_voice_agent_v1.2.zip` | `f5fd4fd6984b4e766157dfa1d2f51df75cd94772335ea1d112a4690caa211e0b` |

### Deployment Verification

1. Import `acme_voice_agent_v1.2.zip` → all 7 evaluations appear in CES Evaluations tab
2. Run `branch_search_munich` evaluation → verify agent transfers to location_services_agent
3. Run `session_end_live_agent` → verify `end_session` tool invoked
4. Run `prompt_injection_attempt` scenario → verify agent doesn't reveal system prompt

### Rollback Instructions

```bash
# Restore to v1.1 (2 evaluations only)
git checkout ces-v1.1-evaluations -- ces-agent/acme_voice_agent/
cd ces-agent && rm -f acme_voice_agent.zip && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'
```

### Known Issues

- `branch_search_munich` cannot use `toolCall` expectation for `searchBranches` because it's an OpenAPI toolset operation (see [Learning L-01](#l-01-toolcall-cannot-reference-openapi-operations))
- `location_services_agent/instruction.txt` still contains `limit=5` in examples (fixed in v1.3.0)

### Lessons Learned

- Scenario evaluations with `scenarioExpectations: []` are the safest format — they avoid `toolCall` reference issues entirely
- Adding 5 evaluations at once worked fine — CES import handles bulk additions well

---

## [1.1.0] - 2026-02-08

**Status:** ✅ Working (verified CES import)  
**Git Tag:** `ces-v1.1-evaluations`  
**Git Commit:** `f0cad6a`  
**Agent:** 

### Added

- **2 golden evaluations** (first evaluations added incrementally after baseline reset)
  - `off_topic_redirect` — Tests weather question redirect + "coming soon" banking feature handling
  - `german_branch_search` — Tests German-language branch search in Frankfurt with bilingual response

### Resource Details

| Evaluation | Type | File | Key Expectations |
|------------|------|------|-----------------|
| `off_topic_redirect` | Golden | `evaluations/off_topic_redirect/off_topic_redirect.json` | `agentResponse` only (no toolCall) |
| `german_branch_search` | Golden | `evaluations/german_branch_search/german_branch_search.json` | `agentTransfer` to location_services_agent |

### Deployment Artifacts

| Artifact | SHA-256 |
|----------|---------|
| `acme_voice_agent_v1.1.zip` | `eb97be29a43d401e61ece9ff64f95a1474d6f31c88a1a28a0e7c07cb0ffe4d2b` |

### Deployment Verification

1. Import `acme_voice_agent_v1.1.zip` → 2 evaluations appear in CES Evaluations tab
2. Run `off_topic_redirect` → agent stays on voice_banking_agent, redirects to banking
3. Run `german_branch_search` → agent transfers to location_services_agent

### Rollback Instructions

```bash
# Restore to v1.0 baseline (no evaluations)
git checkout ces-v1.0-baseline -- ces-agent/acme_voice_agent/
cd ces-agent && rm -f acme_voice_agent.zip && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'
```

### Lessons Learned

- First successful incremental addition after the ground-truth reset
- Validated that evaluations CAN be added via ZIP import (unlike guardrails)
- Established the "add → zip → import → verify → commit → tag" workflow

---

## [1.0.0] - 2026-02-08

**Status:** ✅ Working (verified CES export used as ground truth)  
**Git Tag:** `ces-v1.0-baseline`  
**Git Commit:** `4d969f8`  
**Agent:**  + Developer (manual CES export)

### Added

- **Complete CES agent package** restored from verified CES export (`exported_app_acme_voice_agent (2).zip`)
- **2 agents:**
  - `voice_banking_agent` — Root orchestrator with greeting, routing, session management
  - `location_services_agent` — Branch search specialist with city/postal/brand/accessibility search
- **2 guardrails** (platform-native, from CES export):
  - `Safety_Guardrail_1770494214565` — Content safety filter (4 harm categories, default thresholds)
  - `Prompt_Guardrail_1770494214565` — Prompt injection detection (default settings)
- **1 OpenAPI toolset:**
  - `location` toolset with `open_api_schema.yaml` pointing to BFA service Cloud Run endpoint
  - Operations: `searchBranches`, `getBranch`, `getOpeningHours`
- **Global instruction** (`global_instruction.txt`) with bilingual persona, language matching, 7 constraints
- **App configuration** (`app.json`) with audio processing (de-DE, en-US), logging, BigQuery export, timezone (Europe/Paris)
- **Environment** (`environment.json`) with Cloud Run service account

### Resource Details

| Resource | File | Description |
|----------|------|-------------|
| Root Agent | `agents/voice_banking_agent/voice_banking_agent.json` + `instruction.txt` | Orchestrator with greeting flow, agent routing |
| Location Agent | `agents/location_services_agent/location_services_agent.json` + `instruction.txt` | Branch search with 7 constraints, taskflow, 7 examples |
| Safety Guardrail | `guardrails/Safety_Guardrail_1770494214565/` | Platform content filter |
| Prompt Guardrail | `guardrails/Prompt_Guardrail_1770494214565/` | Prompt injection detector |
| Location Toolset | `toolsets/location/` | OpenAPI spec → BFA Cloud Run |
| Global Instruction | `global_instruction.txt` | Bilingual persona + 7 constraints |
| App Config | `app.json` | Root agent, languages, guardrails, logging |
| Environment | `environment.json` | Service account config |

### Migration Notes

This version was created by **exporting from CES** (not building from scratch) after multiple failed import attempts with hand-built packages. The export was used as the authoritative ground truth for all subsequent versions.

**Why the reset happened:** Earlier in this session, attempts to add new guardrail folders (`banking_policy/`, `pii_protection/`) and modify guardrail JSONs via ZIP import repeatedly failed. The CES import validation is strict about:
1. Directory structure (must wrap in `acme_voice_agent/` root)
2. New guardrail folders (CANNOT be created via import)
3. File references in `app.json` (must match exactly)

### Deployment Verification

1. Import ZIP → success (this is the verified export, so import always works)
2. Simulator: "Hallo" → German greeting from voice_banking_agent
3. Simulator: "Find branches in Berlin" → agent transfers to location_services_agent → results
4. Simulator: "Tell me a joke" → stays on topic (safety guardrail or instruction redirect)

### Rollback Instructions

This IS the baseline. To restore from any version:

```bash
git checkout ces-v1.0-baseline -- ces-agent/acme_voice_agent/
cd ces-agent && rm -f acme_voice_agent.zip && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'
```

### Known Issues

- `location_services_agent/instruction.txt` has `limit=5` in ALL examples (bug — fixed in v1.3.0)
- Agent sends English city names to API (bug — fixed in v1.3.0)
- No evaluations included (added in v1.1.0)
- Safety guardrail uses default thresholds (enhancement deferred — must use CES UI)
- Prompt guardrail has empty rejection prompt (enhancement deferred — must use CES UI)
- `banking_policy` LLM guardrail was manually created in CES but is NOT in the export (platform guardrail; not exportable as folder — see [Learning L-05](#l-05-new-guardrail-folders-cannot-be-created-via-zip-import))

---

## [0.x] - Pre-Baseline History (2026-02-07 to 2026-02-08)

**Status:** ❌ Superseded (do not use — multiple broken imports)  
**Git Commits:** `2e5a793` → `9ed6764` → `1a97dda`

These commits represent the initial development phase before the ground-truth reset. They are preserved in git history but should NOT be used as a base for future work.

### Timeline

| Date | Commit | What Happened |
|------|--------|---------------|
| 2026-02-07 | `2e5a793` | Initial agent package creation (voice_banking_agent + location_services_agent) |
| 2026-02-08 | `9ed6764` | Language handling improvements (German default → language matching) |
| 2026-02-08 | `1a97dda` | Added branch_search eval + banking_policy guardrail — **broke CES import** |

### What Went Wrong

1. **`banking_policy/` guardrail folder** — CES import rejected the new folder. Platform-native guardrails (with numeric IDs) can be imported; custom guardrail folders cannot.
2. **Guardrail JSON modifications** — Editing Safety/Prompt guardrail JSONs to add custom prompts also broke import.
3. **Flat ZIP structure** — Early ZIPs didn't wrap content in `acme_voice_agent/` directory.
4. **Inlined `globalInstruction`** — Attempted to inline instruction text into `app.json` instead of file reference. CES rejected this when used alongside other file references.

### Lessons Learned

These failures led to the ground-truth reset (v1.0.0) and the incremental add-verify-commit workflow used for all subsequent versions.

---

## Platform Learnings & CES Gotchas

> **Purpose:** Persistent knowledge base for AI agents and developers working with CES/CX Agent Studio. Each learning has an ID for cross-referencing.

### L-01: `toolCall` Cannot Reference OpenAPI Operations

**Discovered:** 2026-02-08 (v1.2 evaluation development)  
**Severity:** 🔴 High — causes evaluation creation failures

In CES golden evaluations, the `toolCall` expectation can only reference **direct Tool resources** (e.g., `end_session`). It CANNOT reference operations within an OpenAPI toolset (e.g., `searchBranches` from the `location` toolset).

**Workaround:** Use `agentTransfer` expectations to verify the agent routes to the correct sub-agent, rather than verifying the specific tool call. For scenario evaluations, use `scenarioExpectations: []` to avoid the issue entirely.

### L-02: Language Instruction Sensitivity

**Discovered:** 2026-02-08 (v0.x language fix iterations)  
**Severity:** 🟡 Medium — causes agent to freeze or respond in wrong language

Overly strict language constraints using words like `CRITICAL`, `MUST NEVER`, `ABSOLUTELY` cause the Gemini model to freeze or produce garbled responses. 

**Best practice:** Use soft, positive phrasing: *"Always respond in the same language the user is speaking. English user → English responses. German user → German responses."*

### L-03: CES ZIP Structure Must Use Root Directory Wrapper

**Discovered:** 2026-02-08 (v0.x import failures)  
**Severity:** 🔴 High — flat ZIPs fail silently or with cryptic errors

The ZIP file MUST contain a root directory matching the app's `displayName` (e.g., `acme_voice_agent/`). All resources must be nested inside this directory.

**Correct:**
```
acme_voice_agent.zip
└── acme_voice_agent/
    ├── app.json
    ├── global_instruction.txt
    ├── agents/
    ├── evaluations/
    ├── guardrails/
    └── toolsets/
```

**Incorrect (will fail):**
```
acme_voice_agent.zip
├── app.json          ← flat structure, NO root wrapper
├── global_instruction.txt
└── agents/
```

**Build command:** `cd ces-agent && zip -r acme_voice_agent.zip acme_voice_agent/ -x '*/.*'`

### L-04: Guardrail Naming Must Match Exactly

**Discovered:** 2026-02-08 (v0.x guardrail attempts)  
**Severity:** 🟡 Medium — mismatched names cause silent import failures

CES references guardrails by `displayName` in `app.json`. The guardrail array values must exactly match the folder names (replacing `_` with spaces and stripping file extensions).

**Example:**
- Folder: `Safety_Guardrail_1770494214565/`
- `app.json` entry: `"Safety Guardrail 1770494214565"` (underscores → spaces)

### L-05: New Guardrail Folders Cannot Be Created via ZIP Import

**Discovered:** 2026-02-08 (v0.x banking_policy + pii_protection attempts)  
**Severity:** 🔴 High — fundamentally limits what can be deployed via ZIP

CES import can restore/update **existing** platform-native guardrails (with numeric IDs like `Safety_Guardrail_1770494214565`), but it CANNOT create **new** guardrail folders.

**Implication:** Custom guardrails (`banking_policy`, `pii_protection`) must be created through the CES console UI, not via ZIP import. They will appear in CES exports but cannot be re-imported as new resources to a different agent.

**Workaround:** Create guardrails manually in CES UI, then export to capture their platform-assigned IDs. Future imports can then update these guardrails.

### L-06: `global_instruction.txt` File Reference Works in Import

**Discovered:** 2026-02-08 (v1.0 baseline verification)  
**Severity:** ℹ️ Informational

The `"globalInstruction": "global_instruction.txt"` pattern in `app.json` (file reference, not inline content) works correctly for both import and export. The file must exist at the root of the agent package directory.

### L-07: Evaluations Can Be Added Incrementally via ZIP

**Discovered:** 2026-02-08 (v1.1 → v1.2 evaluation additions)  
**Severity:** ℹ️ Informational — good news

Unlike guardrails, evaluations CAN be created via ZIP import. New evaluation folders added to `evaluations/` are picked up correctly. This was validated by adding evaluations in two batches (2 in v1.1, 5 more in v1.2).

### L-08: Few-Shot Examples Are Treated as Training Data

**Discovered:** 2026-02-08 (v0.x limit=5 bug)  
**Severity:** 🟡 Medium — examples with bad values teach bad behaviour

The Gemini model treats few-shot examples in `<examples>` blocks as strong training signals. If all examples include `limit=5`, the model will ALWAYS send `limit=5`, even when the user doesn't request a specific number.

**Best practice:** Only include parameters in examples that would naturally be mentioned by the user. Omit optional parameters to let the API use its defaults.

### L-09: English City Names Fail API Lookups

**Discovered:** 2026-02-08 (v0.x München vs Munich bug)  
**Severity:** 🟡 Medium — returns 0 results for English city names

The BFA `searchBranches` API expects German city names. "Munich" returns 0 results; "München" returns 8. The agent must translate English city names to German before making API calls.

**Solution:** Added constraint #8 in location_services_agent with an explicit translation table (Munich→München, Cologne→Köln, etc.) and few-shot examples demonstrating the translation.

### L-10: Incremental Workflow Is Essential

**Discovered:** 2026-02-08 (entire session)  
**Severity:** 🔴 High — multiple changes at once lead to undebuggable failures

The CES import process gives minimal error information. When multiple resources change at once and import fails, it's nearly impossible to identify which change caused the failure.

**Mandatory workflow:**
1. Make ONE category of changes (e.g., evaluations only, or instruction changes only)

2. Re-zip: `cd ces-agent/scripts && bash deploy/deploy-agent.sh`
3. Import to CES → verify success
4. Commit + tag: `git add -A && git commit -m "..." && git tag ces-v{X}.{Y}`
5. Save versioned ZIP: `cp acme_voice_agent.zip acme_voice_agent_v{X}.{Y}.zip`
6. Repeat

---

### L-11: OpenAPI Operations Cannot Be Mocked or Asserted in Scenario Evaluations

**Discovered:** 2026-02-08 (german_branch_search evaluation)  
**Severity:** 🔴 High — fundamentally limits deterministic testing of OpenAPI-backed tools  
**CES Error:** `Reference 'searchBranches' of type 'ces.googleapis.com/Tool' not found`

CES `scenarioExpectations[].toolExpectation` can only reference **direct tools** (tools listed in an agent's `tools` array, e.g., `end_session`). OpenAPI operations (`searchBranches`, `getBranch`) that come from `toolsets` are **not** referenceable — CES rejects them with the error above.

This is the scenario-evaluation counterpart of **[L-01](#l-01-toolcall-in-evaluation-json-cannot-reference-openapi-operations)**, which documents the same restriction for golden evaluations. L-01 covers `golden.turns[].steps[].expectation.toolCall`; L-11 extends the rule to `scenario.scenarioExpectations[].toolExpectation.expectedToolCall`.

**Consequence:** For agents that rely on OpenAPI toolsets (like `location_services_agent` with `searchBranches`), there is **no way** to:
- Provide deterministic mock responses in scenario evaluations
- Assert exact tool call parameters (e.g., `city=Berlin`)
- Run evaluations offline/in CI without the live backend

**Testing strategy (workaround):**

| Layer | What It Tests | Deterministic? |
|-------|--------------|----------------|
| **Scenario evaluation** (CES) | Language, hallucination, conversation flow, agent transfer | ❌ No — hits live BFA backend |
| **Golden evaluation** (CES) | Turn-by-turn response shape, `agentTransfer`, `end_session` calls | ✅ Yes — scripted turns, no tool mocking needed |
| **Java unit/integration tests** | `searchBranches` parameters, response schema, edge cases | ✅ Yes — mocked services |

**Rule:** Use `scenarioExpectations: []` for evaluations that test OpenAPI-backed workflows. Put all verification criteria in the `task` field where the AI evaluator reads them. Reserve `toolExpectation` for direct tools only.

---

## Complete Resource Inventory (Current State)

### Agents

| Agent | Role | Instruction Lines | JSON Config |
|-------|------|-------------------|-------------|
| `voice_banking_agent` | Root orchestrator | `agents/voice_banking_agent/instruction.txt` | `voice_banking_agent.json` |
| `location_services_agent` | Branch search specialist | `agents/location_services_agent/instruction.txt` (266 lines) | `location_services_agent.json` |

### Guardrails

| Guardrail | Type | Status | Notes |
|-----------|------|--------|-------|
| `Safety_Guardrail_1770494214565` | Content Filter | ✅ In package | Default thresholds (to be enhanced via CES UI) |
| `Prompt_Guardrail_1770494214565` | Prompt Security | ✅ In package | Default settings (to be enhanced via CES UI) |
| `banking_policy` | LLM Policy | ❌ Not in package | Must be created in CES UI |
| `pii_protection` | LLM Policy | ❌ Not in package | Must be created in CES UI |

### Evaluations

| Evaluation | Type | Key Test |
|------------|------|----------|
| `off_topic_redirect` | Golden | Weather redirect + "coming soon" handling |
| `agent_handover_roundtrip` | Golden | Root → location → root roundtrip |
| `session_end_live_agent` | Golden | "Mitarbeiter" → live agent + `end_session` |
| `german_branch_search` | Scenario | German-language Berlin branch search (live BFA) — see [L-11](#l-11-openapi-operations-cannot-be-mocked-or-asserted-in-scenario-evaluations) |
| `branch_search_munich` | Scenario | München search (live BFA) — multi-turn English |
| `prompt_injection_attempt` | Scenario | Injection resilience (5 turns) |
| `edge_case_no_results` | Scenario | Zero-result graceful handling (3 turns) |

### Toolsets

| Toolset | Type | Backend | Operations |
|---------|------|---------|------------|
| `location` | OpenAPI | `https://bfa-service-resource-1041912723804.us-central1.run.app` | `searchBranches`, `getBranch`, `getOpeningHours` |

### Configuration

| File | Purpose |
|------|---------|
| `app.json` | Root config: agents, guardrails, languages (en-US, de-DE), logging, timezone |
| `global_instruction.txt` | Global persona + 9 constraints (64 lines) |
| `environment.json` | Cloud Run service account |

---

## Deployment Artifacts Archive

> ⚠️ **DO NOT DELETE** versioned ZIP files. They are committed to git and serve as rollback points.

| Version | ZIP File | SHA-256 | Size |
|---------|----------|---------|------|
| v1.2 | `acme_voice_agent_v1.2.zip` | `f5fd4fd6984b4e766157dfa1d2f51df75cd94772335ea1d112a4690caa211e0b` | 21,657 bytes |
| v1.1 | `acme_voice_agent_v1.1.zip` | `eb97be29a43d401e61ece9ff64f95a1474d6f31c88a1a28a0e7c07cb0ffe4d2b` | 17,352 bytes |
| v1.0 | *(baseline from CES export — restore via git tag)* | — | — |
