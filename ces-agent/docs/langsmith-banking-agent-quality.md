# LangSmith Quality Strategy For A Banking Agentic App

Author: Codex  
Date: 2026-03-27  
Status: Draft

## Purpose

This document explains why LangSmith evaluation is valuable for a banking
agentic application and how it complements the existing CES evaluation setup in
this repository.

It is written for three audiences:

- business stakeholders who need measurable quality and risk reduction
- prompt engineers who need a fast prompt-improvement loop
- developers and platform engineers who need reproducible evaluation and
  debuggable failures

## Executive Summary

A banking agent can fail in ways that are not visible to normal API tests.

Examples:

- the agent answers in the wrong language
- the agent asks too many questions in one turn
- the agent invents appointment slots
- the agent cancels or reschedules before explicit confirmation
- the agent drifts from appointment handling into unrelated banking topics

Traditional tests are still necessary, but they are not sufficient. Unit tests,
OpenAPI checks, and smoke tests prove that components work. LangSmith helps
prove that the full LLM-driven behavior is safe, grounded, and consistent.

That matters more in banking than in low-risk domains because poor LLM behavior
does not only degrade UX. It can also create trust, compliance, and operational
risk.

## Why This Matters In Banking

For a banking agent, "high quality" means more than a successful tool call.

It means the system:

- stays within scope
- asks for confirmation before sensitive actions
- grounds claims in tool or backend evidence
- behaves consistently across languages
- recovers safely when inputs are incomplete or invalid
- can be measured and reviewed after changes

This repo already has strong CES-native evaluation assets. LangSmith adds a
second layer that makes prompt quality easier to compare, score, and review over
time.

Official LangSmith documentation supports this model directly:

- offline evaluation on curated datasets before release
- online evaluation on live traffic
- code evaluators
- LLM-as-judge evaluators
- pairwise comparisons
- human review through annotation queues

References:

- [LangSmith Evaluation](https://docs.langchain.com/langsmith/evaluation)
- [Evaluation Concepts](https://docs.langchain.com/langsmith/evaluation-concepts)
- [Use Annotation Queues](https://docs.langchain.com/langsmith/annotation-queues)

## Value By Audience

### Business Stakeholders

Business value is mostly about reducing production risk while allowing faster
iteration.

Without this layer, prompt changes often get evaluated informally:

- "it sounded fine in a few test chats"
- "the handoff still worked"
- "the backend call succeeded"

That is weak evidence for a banking assistant.

LangSmith adds:

- measurable quality gates
  example: confirmation-before-cancel pass rate, grounded-slot pass rate,
  language continuity rate
- regression visibility
  prompt changes can be compared against a previous version on the same dataset
- traceable failures
  failed runs are preserved with inputs, outputs, evaluator results, and
  comments
- better release confidence
  releases can be evaluated on representative banking journeys before exposure
  to users
- operational triage
  suspicious or failed runs can be routed into human review queues

In simple terms, LangSmith helps answer:

- Is the agent safer after this change?
- Is it more accurate?
- Did we improve one journey while breaking another?
- Which failures matter enough to stop a release?

### Prompt Engineers

Prompt engineering becomes much more effective when it is treated as an
experiment discipline rather than manual chat testing.

LangSmith helps prompt engineers:

- turn prompt rules into explicit evaluators
- compare prompt version A vs prompt version B on the same scenarios
- identify which instruction rule is failing
- review failures in one place instead of reading raw logs ad hoc
- use human review when semantic quality is hard to automate fully

For this repo, that means prompt engineers can move beyond "does the prompt feel
better?" and instead ask:

- Did the prompt reduce greeting violations?
- Did it improve same-language behavior?
- Did it stop ungrounded slot claims?
- Did it improve confirmation wording before lifecycle actions?
- Did it keep the advisory agent inside appointment scope on the first turn?

That is exactly the kind of loop needed to improve
[instruction.txt](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt).

### Developers And Platform Engineers

Developers still need strong API and integration tests, but agentic systems also
have failure modes above the transport layer.

LangSmith helps engineers:

- separate harness failures from model-behavior failures
- keep evaluation results in a structured experiment format
- attach custom deterministic checks to real traces
- debug failures with preserved artifacts
- compare different prompt, tool, or backend configurations over time

In this repo, that is already visible in the CES-to-LangSmith bridge:

- live CES suites run through
  [langsmith-live-experiments.py](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/langsmith-live-experiments.py)
- suite artifacts are captured under `test-harness/evaluation/.artifacts/langsmith/`
- external experiments are uploaded through LangSmith's
  `upload-experiment` flow

Reference:

- [Upload Existing Experiments](https://docs.langchain.com/langsmith/upload-existing-experiments)

## What LangSmith Adds Beyond Existing Tests

This repository already has several useful layers:

- package validation
- prompt contract checks
- CES golden evaluations
- CES scenario evaluations
- smoke tests for deployed tools and remote services

Those should remain in place.

LangSmith should be viewed as the next quality layer, not a replacement.

### Suggested Quality Pyramid

| Layer | Main question | Best tool |
| --- | --- | --- |
| Static contracts | Is the package structurally valid? | local validators and prompt contracts |
| Backend connectivity | Do tools, OpenAPI specs, and services respond? | smoke tests |
| CES journey behavior | Does the deployed agent pass scripted evaluation runs? | CES evaluations |
| Prompt and trajectory quality | Is the behavior actually good, safe, and consistent? | LangSmith experiments |
| Human review | Are borderline outputs acceptable for banking? | LangSmith annotation queues |

## Banking Defects LangSmith Can Expose

These are the kinds of failures that are costly in a banking assistant and easy
to miss with normal tests.

### 1. Unsafe lifecycle actions

Example:

- the agent cancels or reschedules an appointment before asking the user to
  confirm

Why it matters:

- this is a trust and safety defect
- backend correctness alone does not protect against bad conversational framing

Current repo signal:

- `confirmation_before_lifecycle_action`

### 2. Ungrounded claims

Example:

- the agent says "I have a free slot at 10:30" before calling the slot lookup
  tool
- the agent says "your appointment is confirmed" before any create or
  reschedule action actually happened

Why it matters:

- the user is given false operational information
- this is one of the clearest hallucination patterns in appointment flows

Current repo signal:

- `no_ungrounded_slot_or_status_claims`

### 3. Scope drift

Example:

- the advisory appointment agent starts answering account-balance questions
  instead of staying in appointment scope

Why it matters:

- specialist agents become unreliable
- routing architecture loses value if specialist prompts ignore their scope

Current repo signal:

- `first_turn_stays_in_scope`

### 4. Language drift and tone regressions

Examples:

- user asks in German, agent replies in English
- the prompt says no greeting, but the agent greets anyway
- the agent asks three missing-information questions in a single turn

Why it matters:

- this degrades voice experience immediately
- these regressions often appear after otherwise innocent prompt edits

Current repo signals:

- `same_language_as_user`
- `no_greeting_on_first_turn`
- `single_question_first_turn`

## How This Works In The Current Repo

The current repo already has the right building blocks for a practical LangSmith
workflow.

### Current implementation path

1. CES evaluations run against the deployed app through
  [ces-evaluation-runner.py](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/ces-evaluation-runner.py)
2. LangSmith-focused suites select routing, booking, and recovery journeys:
  - [langsmith-advisory-routing-suite.json](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/suites/langsmith-advisory-routing-suite.json)
  - [langsmith-advisory-booking-suite.json](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/suites/langsmith-advisory-booking-suite.json)
  - [langsmith-advisory-recovery-suite.json](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/suites/langsmith-advisory-recovery-suite.json)
3. The LangSmith bridge converts CES artifacts into external experiment rows in
  [langsmith-live-experiments.py](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/langsmith-live-experiments.py)
4. The wrapper
  [run-langsmith-experiments.sh](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/run-langsmith-experiments.sh)
   uploads those rows to LangSmith

### Current evaluator pattern

The bridge mixes:

- CES-native pass/fail metrics
- deterministic code evaluators on outputs and trace events
- LangSmith experiment storage and review

This is a strong pattern for a banking agent because it preserves both:

- hard operational assertions
- softer but still critical quality judgments

## Recommended Operating Model

### Before a prompt change is merged

- run local prompt contract checks
- run CES golden and scenario suites
- run a LangSmith offline or external experiment on the relevant journeys

### When changing a specialist instruction

- compare the old and new prompt on the same dataset
- inspect which evaluator keys improved or regressed
- route ambiguous failures into human review

### Before release

- confirm high-risk banking journeys are green
- review any failures in lifecycle, grounding, or scope
- verify no key metric regressed versus baseline

### After deployment

- continue using online or uploaded experiments
- feed suspicious runs into annotation queues
- use those reviewed failures to improve future datasets and prompt rules

References:

- [Set Up Online Evaluators](https://docs.langchain.com/langsmith/online-evaluations?ajs_aid=af5b4390-bb02-4030-ac5b-37e312604665)
- [Use Annotation Queues](https://docs.langchain.com/langsmith/annotation-queues)
- [How To Run A Pairwise Evaluation](https://docs.langchain.com/langsmith/evaluate-pairwise)

## What "Good" Looks Like

For a banking agentic app, a useful LangSmith program should eventually provide:

- stable curated datasets for critical banking journeys
- a small set of trusted deterministic evaluators
- a smaller set of carefully designed judge rubrics
- pairwise comparisons for prompt revisions
- human review for ambiguous or high-risk failures
- experiment history that shows whether quality is improving or regressing

The goal is not to maximize the number of evaluators. The goal is to make
prompt and agent changes measurable, explainable, and safer to release.

## Limitations And Correct Framing

LangSmith is not a substitute for:

- backend contract tests
- OpenAPI validation
- smoke tests
- authorization and data security controls
- explicit business rules implemented in code

It should be treated as the quality system for LLM behavior, not as the whole
quality system for the application.

That distinction is especially important in banking, where both deterministic
rules and probabilistic behavior must be controlled.

## Recommended Next Steps For This Repo

1. Keep the current deterministic evaluators for routing and advisory safety.
2. Add pairwise prompt comparison for advisory instruction revisions.
3. Add human-review queues for lifecycle and recovery failures.
4. Expand the dataset from existing CES `appointment_*` evaluations.
5. Track a small banking scorecard over time:
   - confirmation-gate pass rate
   - grounded-slot/status pass rate
   - language continuity pass rate
   - first-turn scope adherence
   - recovery quality

## References

### Official LangSmith Documentation

- [LangSmith Evaluation](https://docs.langchain.com/langsmith/evaluation)
- [Evaluation Concepts](https://docs.langchain.com/langsmith/evaluation-concepts)
- [Upload Existing Experiments](https://docs.langchain.com/langsmith/upload-existing-experiments)
- [Use Annotation Queues](https://docs.langchain.com/langsmith/annotation-queues)
- [Set Up Online Evaluators](https://docs.langchain.com/langsmith/online-evaluations?ajs_aid=af5b4390-bb02-4030-ac5b-37e312604665)
- [How To Run A Pairwise Evaluation](https://docs.langchain.com/langsmith/evaluate-pairwise)

### Repo References

- [instruction.txt](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt)
- [langsmith-advisory-instruction-evaluation.md](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/docs/langsmith-advisory-instruction-evaluation.md)
- [prompt-testing-strategy.md](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/docs/prompt-testing-strategy.md)
- [test-harness/evaluation/README.md](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/README.md)
- [langsmith-live-experiments.py](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation/langsmith-live-experiments.py)
