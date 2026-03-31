# LangSmith Advisory Instruction Evaluation

Author: Codex  
Date: 2026-03-27  
Status: Draft proposal

## Purpose

This document defines how LangSmith can complement the existing CES prompt and
evaluation setup for
`acme_voice_agent/agents/advisory_appointment_agent/instruction.txt`.

For a broader multi-audience explanation of business value, prompt-engineering
value, and engineering value, see
[langsmith-banking-agent-quality.md](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/docs/langsmith-banking-agent-quality.md).

The main point is to separate two concerns:

- deterministic validation of the instruction file itself
- behavioral evaluation of the LLM outputs and multi-turn appointment flow

LangSmith is a strong fit for the second concern. The first concern should stay
in deterministic local tests.

## Baseline

The repository already contains the right lower-level building blocks:

- deterministic prompt parsing and contract validation in
  `ces-agent/scripts/prompt_contracts.py`
- package-level instruction validation in
  `ces-agent/scripts/deploy/validate-package.py`
- CES-native advisory scenarios under
  `ces-agent/acme_voice_agent/evaluations/appointment_*`

The advisory instruction itself defines clear behavioral rules that are good
evaluation targets:

- no greeting
- keep the same language as the caller
- one question at a time
- tool-ground all slots and confirmations
- do not ask for a slot before path, channel, and eligible location are known
- summarize and ask for explicit confirmation before lifecycle or creation calls

Source:

- [advisory instruction](../acme_voice_agent/agents/advisory_appointment_agent/instruction.txt)

## What LangSmith Adds

LangSmith is useful here because it supports:

- offline evaluations against curated datasets
- online evaluations against production runs and threads
- code evaluators
- LLM-as-judge evaluators
- pairwise comparisons
- human review through annotation queues

That maps well to appointment orchestration, where correctness is not only
structural but also behavioral across turns.

Official references:

- [Evaluation concepts](https://docs.langchain.com/langsmith/evaluation-concepts)
- [Example data format](https://docs.langchain.com/langsmith/example-data-format)
- [Annotation queues](https://docs.langchain.com/langsmith/annotation-queues)
- [Online code evaluators](https://docs.langchain.com/langsmith/online-evaluations-code)

## Recommended Split

### 1. Deterministic local checks

Keep these in repo-local tests:

- required sections exist: `role`, `persona`, `constraints`, `taskflow`
- allowed tool references are valid
- critical guardrail phrases remain present
- handoff scope does not drift

Why:

- these are exact contract assertions
- they do not require an LLM
- they should fail fast in CI

Recommended source of truth:

- `ces-agent/scripts/prompt_contracts.py`
- `ces-agent/scripts/test_prompt_contracts.py`

### 2. LangSmith offline evaluations

Use LangSmith datasets and experiments to score the behavior caused by the
instruction.

For `advisory_appointment_agent`, the best offline targets are:

- routing correctness
- booking-flow ordering
- explicit confirmation behavior
- lifecycle behavior
- invalid-contact repair
- no-slots recovery
- human-handoff behavior
- multilingual consistency

### 3. LangSmith online evaluations

Only add online evaluators once the CES runtime or wrapper can emit traces into
LangSmith or once external experiment results are uploaded consistently.

This layer is useful for:

- real production threads
- regression detection on live traffic
- triage queues for failed or suspicious runs

## Dataset Design

LangSmith examples are built around:

- `inputs`
- `outputs`
- `metadata`

For this repo, each advisory CES scenario can become one LangSmith dataset
example.

Recommended example shape:

```json
{
  "name": "appointment_booking_branch_flow",
  "inputs": {
    "task": "Test a full branch appointment booking conversation...",
    "max_turns": 10
  },
  "outputs": {
    "reference_expectations": [
      "The agent routes to advisory_appointment_agent when the user requests an appointment.",
      "The agent asks for explicit confirmation before creating the appointment."
    ],
    "target_agent": "advisory_appointment_agent",
    "source_evaluation_type": "ces_scenario"
  },
  "metadata": {
    "journey_type": "booking",
    "language": "en",
    "source_path": "acme_voice_agent/evaluations/appointment_booking_branch_flow/appointment_booking_branch_flow.json"
  }
}
```

LangSmith reference:

- [Example data format](https://docs.langchain.com/langsmith/example-data-format)

## Mapping From CES Evaluations

The existing CES advisory scenario files are already a good offline seed set.

Suggested mapping:

| CES evaluation | LangSmith metadata `journey_type` | Main rubric focus |
| --- | --- | --- |
| `appointment_routing_english` | `routing` | handoff correctness, English continuity |
| `appointment_routing_german` | `routing` | handoff correctness, German continuity |
| `appointment_booking_branch_flow` | `booking` | flow order, slot grounding, confirmation gate |
| `appointment_service_request_booking` | `booking` | correct service-request path, repair behavior |
| `appointment_phone_consultation` | `booking` | correct PHONE path, no city over-collection |
| `appointment_video_consultation` | `booking` | correct VIDEO path, concise option presentation |
| `appointment_cancel_reschedule` | `lifecycle` | retrieve-first, confirmation-before-action |
| `appointment_reschedule_window_closed` | `lifecycle` | refusal quality, no false success |
| `appointment_invalid_contact_repair` | `recovery` | one-field-at-a-time repair, no premature create |
| `appointment_no_slots_recovery` | `recovery` | fallback quality, no invented availability |
| `appointment_human_handoff` | `recovery` | escalation quality, no fabricated recovery |

## Evaluator Set

Use three evaluator layers.

### A. Code evaluators

Use deterministic evaluators for rules that can be checked without a judge:

- `no_greeting`
- `same_language_as_user`
- `single_question_turn`
- `has_confirmation_before_create`
- `has_confirmation_before_cancel`
- `has_confirmation_before_reschedule`
- `response_not_empty`

These are the easiest to trust and should run on every experiment.

LangSmith reference:

- [Evaluation concepts: code evaluators](https://docs.langchain.com/langsmith/evaluation-concepts)

### B. LLM-as-judge evaluators

Use judge prompts for richer semantic behavior:

- `appointment_scope_adherence`
- `voice_conciseness`
- `correct_next_step`
- `recovery_quality`
- `lifecycle_safety`
- `no_fabricated_slots_or_confirmations`

These should be few-shot where possible, especially for:

- confirmation gating
- no-slot recovery
- lifecycle refusal quality

LangSmith reference:

- [Evaluation concepts: LLM-as-judge](https://docs.langchain.com/langsmith/evaluation-concepts)

### C. Pairwise evaluators

Use pairwise evaluation when changing the instruction text itself.

Best use cases:

- comparing two prompt revisions
- comparing shorter vs more explicit constraints
- choosing between two taskflow orderings

LangSmith reference:

- [Evaluation concepts: pairwise](https://docs.langchain.com/langsmith/evaluation-concepts)

## Recommended Rubric

### Booking rubric

- `routing_correct`: transferred into appointment scope when appropriate
- `flow_order_correct`: path or channel before slots, slots before booking
- `grounded_with_tools`: no invented availability, branch, or appointment state
- `confirmation_gate_respected`: explicit confirmation before create
- `voice_suitable`: concise, one question at a time

### Lifecycle rubric

- `retrieve_before_mutation`: existing appointment checked before cancel or reschedule
- `confirmation_gate_respected`: explicit confirmation before destructive action
- `status_communication_clear`: result explained clearly
- `blocked_action_honesty`: no false success when action is not allowed

### Recovery rubric

- `repair_quality`: asks for one missing or invalid field at a time
- `fallback_quality`: offers another day, location, channel, or handoff
- `handoff_quality`: escalates cleanly when asked or when flow is blocked

## Execution Model

There are two realistic ways to use LangSmith with this repo.

### Option 1. Wrap CES as the evaluation target

Build a small harness that:

- sends the scenario task into CES
- captures the returned text plus any observable tool trajectory
- logs the run into LangSmith

Use this when you want LangSmith to own the experiment lifecycle directly.

### Option 2. Run outside LangSmith, upload results later

Keep CES execution where it already lives, then upload experiment results or use
LangSmith datasets for comparison after the fact.

Use this when:

- CES remains the real execution environment
- you do not want to re-platform the runtime
- you want LangSmith mainly for evaluation, comparison, and annotation

Official reference:

- [Upload experiments run outside of LangSmith](https://docs.langchain.com/langsmith/upload-existing-experiments)

## Repo Starter Artifact

The repository now includes:

- `ces-agent/scripts/export_langsmith_advisory_dataset.py`

This script converts `appointment_*` CES evaluations into a LangSmith
dataset seed JSON. It supports both:

- CES `scenario` evaluations
- CES `golden` evaluations used for routing checks

The exported rows use:

- `inputs.task`
- `inputs.max_turns`
- `outputs.reference_expectations`
- `metadata.journey_type`
- `metadata.language`
- `metadata.source_path`

Run it with:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/scripts
python3 export_langsmith_advisory_dataset.py --output -
```

It writes a default seed file to:

- `ces-agent/docs/langsmith-advisory-appointment-dataset-seed.json`

## Recommendation

Use LangSmith for behavioral scoring, not as a replacement for deterministic
instruction contract tests.

The most practical rollout is:

1. keep `prompt_contracts.py` for instruction structure and hard guardrails
2. export advisory CES scenarios into a LangSmith offline dataset
3. add code evaluators for strict behavioral rules
4. add LLM-as-judge evaluators for conversation quality and scope adherence
5. use pairwise evaluations for prompt revisions
6. add online thread evaluators only after tracing or result-upload workflow is stable
