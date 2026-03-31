# CES Prompt Testing Strategy

Author: Codex
Date: 2026-03-27
Status: Active

## Purpose

This document defines a practical way to test the prompt and instruction surface of
`ces-agent/acme_voice_agent/`, especially the multiple `instruction.txt` files used
by the root and specialist agents.

Short answer: yes, prompt testing is achievable by current 2026 standards, but not
as a single deterministic "unit test" in the traditional software sense. The
reliable pattern today is a layered evaluation pyramid:

- fast static and fixture-based prompt checks
- CES-native integration evaluations
- deployed runtime smoke tests

The count of `instruction.txt` files is not the main problem by itself. The real
risk is overlap, drift, and untested routing or safety rules across those files.
Five focused instruction files are manageable if each file owns a narrow scope and
has explicit regression coverage.

## Current Prompt Surface in This Repo

The live CES package currently spreads behavior across:

- `acme_voice_agent/global_instruction.txt`
- `acme_voice_agent/agents/voice_banking_agent/instruction.txt`
- `acme_voice_agent/agents/location_services_agent/instruction.txt`
- `acme_voice_agent/agents/fee_schedule_agent/instruction.txt`
- `acme_voice_agent/agents/customer_details_agent/instruction.txt`
- `acme_voice_agent/agents/advisory_appointment_agent/instruction.txt`

The package already contains the three building blocks needed for a modern prompt
test strategy:

- Static package validation via `ces-agent/scripts/deploy/validate-package.py`
- Behavioral evaluations via `ces-agent/acme_voice_agent/evaluations/*`
- Live runtime smoke coverage via `ces-agent/test-harness/smoke/`

## Recommended Test Pyramid

### 1. Prompt Unit Tests

Goal: catch prompt regressions without deploying and without relying on model
judgment.

These tests should treat instruction files as contracts, not as prose blobs.
Recommended assertions:

- Every `{@AGENT: ...}` reference resolves to a real child agent or valid agent.
- Every `{@TOOL: ...}` reference resolves to an attached direct tool.
- Every agent declares the expected sections: role, persona, constraints, taskflow.
- Every specialist prompt still contains its non-fabrication rule.
- The root agent still contains the public-service exception for location, fees,
  and appointments.
- Language-matching rules remain present in the global and per-agent prompts.
- Root-agent routing rules do not drift into specialist responsibilities.
- Tool-grounding rules remain present for location, fee, customer, and appointment
  specialists.

What belongs here:

- text-structure checks
- XML-like section parsing checks
- exact phrase or policy-presence checks for critical guardrails
- prompt inventory checks that map each instruction file to its owner, tools,
  child agents, and required eval suites

What does not belong here:

- natural-language quality scoring
- semantic correctness of open-ended answers
- real tool execution

This repo already has the beginnings of this layer. `validate-package.py` validates
cross-file references, tool inventories, child agents, and evaluation integrity.
The missing piece is prompt-specific fixture tests for instruction invariants.

### 2. Prompt Integration Tests

Goal: verify that prompts, tools, handoffs, and guardrails work together through
the CES evaluation system.

Use two CES-native evaluation modes:

- Golden evaluations for deterministic routing and step structure
- Scenario evaluations for behavioral quality, ambiguity handling, prompt
  injection, and multi-turn recovery

For this repo, the practical split is:

- Golden: root-agent routing, agent transfers, `end_session`, direct tool calls
- Scenario: branch search quality, fee dialogue quality, appointment booking
  flow, multilingual behavior, adversarial prompt-injection behavior

Important current limitation:

- CES golden eval `toolCall` expectations only work for direct tools, not OpenAPI
  toolset operations. This repo already encodes that learning in
  `validate-package.py`. Toolset-operation correctness should therefore be checked
  through scenario evaluations and runtime smoke tests, not golden `toolCall`
  assertions.

Recommended integration coverage by agent:

| Agent | Must-have evals |
| --- | --- |
| `voice_banking_agent` | greeting, public-service exception, ID gate, fee routing, location routing, appointment routing, live-agent handoff |
| `location_services_agent` | English city mapping, German city names, zero-results handling, branch detail grounding, off-topic redirect |
| `fee_schedule_agent` | direct fee lookup, ambiguity clarification, waiver/condition handling, no-results honesty |
| `customer_details_agent` | partner ID required, wrapper-tool only, privacy summarization, not-found handling |
| `advisory_appointment_agent` | booking path selection, slot narrowing, confirmation gate before create/cancel/reschedule |

Recommended scenario packs:

- happy-path pack
- multilingual pack
- ambiguity-repair pack
- adversarial/prompt-injection pack
- handoff-return pack

## 3. Smoke Tests

Goal: prove the deployed app is wired correctly and its runtime contracts still
work in a real CES environment.

This repo already has a solid smoke framework:

- `ces-agent/test-harness/smoke/ces-runtime-smoke.py`
- `ces-agent/test-harness/smoke/suites/*.json`
- `ces-agent/test-harness/smoke/tests/test_ces_runtime_smoke.py`

Smoke tests should stay narrow and stable. They should verify:

- the expected CES agents, tools, and toolsets are deployed
- direct tools can be resolved and executed
- selected OpenAPI toolset operations can be executed through CES
- backend HTTP dependencies are reachable and return expected contract fields

Smoke tests are the right place for:

- deployment wiring
- auth and endpoint drift
- OpenAPI schema drift
- tool/runtime health

Smoke tests are not enough for:

- tone
- policy compliance nuance
- hallucination resistance
- conversation quality

## What Is Achievable Today

### Achievable Now

By current platform standards, the following is realistic and production-grade:

- Static prompt contract testing in CI
- Deterministic validation of prompt references and package structure
- CES-managed golden and scenario evaluation suites
- Regression packs for routing, tool use, and prompt-injection resistance
- Scheduled evaluation runs and batch-uploaded evaluation assets in CES
- Deployed runtime smoke checks against live CES resources
- Audio-oriented evaluation support in CES for voice-specific testing workflows

### Only Partially Achievable

These areas are possible, but not perfectly deterministic:

- exact wording stability across model versions
- exact tool-argument behavior for open-ended multi-turn flows
- fully automated verification of conversational quality
- full jailbreak resistance guarantees
- full voice-quality validation without human review

### Not Realistically Achievable

By today’s standards, do not promise:

- mathematically deterministic prompt correctness for all user phrasing
- zero false positives or false negatives in adversarial prompt testing
- full replacement of human review for critical voice and policy changes

## Recommended Release Gate

For any change touching `global_instruction.txt` or any `agents/*/instruction.txt`
file, use this gate:

1. Run static package validation.
2. Run prompt unit tests for instruction invariants.
3. Run the impacted CES eval subset.
4. Run the full adversarial eval subset for security-sensitive prompt changes.
5. Run deployed smoke tests if the change also affects tool references, toolsets,
   guardrails, or package deployment.
6. For voice-critical releases, run audio/persona validation plus a short human
   review pass.

Suggested cadence:

- PR gate: static checks + impacted eval subset
- nightly: full eval pack
- release gate: full eval pack + deployed smoke + manual voice review

## Repo-Specific Recommendations

### Keep the Multi-File Prompt Layout

Do not collapse everything into one giant instruction file. The current split is
architecturally reasonable:

- root agent owns routing and identification
- specialists own grounded domain behavior
- global instruction owns cross-cutting language, privacy, and tone constraints

The better fix is stronger tests and clearer ownership, not prompt centralization.

### Reduce Drift Risk

Maintain a simple ownership table for each prompt artifact:

| Artifact | Primary responsibility | Required tests |
| --- | --- | --- |
| `global_instruction.txt` | cross-cutting language, privacy, tool-grounding policy | multilingual, privacy, prompt-injection |
| `voice_banking_agent/instruction.txt` | routing and ID gate | golden routing, handoff, live-agent |
| `location_services_agent/instruction.txt` | branch search behavior | scenario branch search, zero-results, multilingual |
| `fee_schedule_agent/instruction.txt` | fee grounding and fee dialogue | scenario fee lookup, ambiguity, no-results |
| `customer_details_agent/instruction.txt` | verified customer lookup | partner ID, privacy, wrapper-tool-only |
| `advisory_appointment_agent/instruction.txt` | appointment orchestration | slot flow, confirmation gate, lifecycle |

### Prefer Stable Assertions

For this repo, stable assertions are:

- correct agent transfer
- correct direct-tool usage
- correct presence of grounding and privacy rules
- correct schema and runtime response fields

Fragile assertions are:

- exact natural-language wording
- exact ordering of non-essential phrasing
- overly long semantic expectations that combine many behaviors at once

## Concrete Next Step

The repo already supports most of this strategy. The highest-leverage follow-up is
to add a dedicated prompt-contract unit test module that parses the instruction
files and asserts the critical invariants listed above. That would complete the
missing bottom layer of the test pyramid without changing the deployment model.

## External References

- Google Cloud CX Agent Studio evaluation reference:
  https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation
- Google Cloud CX Agent Studio evaluation batch upload:
  https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation-batch-upload
- Google Cloud CX Agent Studio MCP tooling limits:
  https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp
- LangSmith evaluation concepts:
  https://docs.langchain.com/langsmith/evaluation-concepts
- Promptfoo getting started:
  https://www.promptfoo.dev/docs/getting-started/
