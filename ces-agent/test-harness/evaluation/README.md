# CES Evaluation Tests

`ces-agent/test-harness/evaluation/` is the canonical home for reproducible CES
quality evaluation runs against deployed agent apps.

This framework is intentionally separate from `ces-agent/test-harness/smoke/`:

- `test-harness/smoke/` validates deployed tools, toolsets, and remote HTTP paths
- `test-harness/evaluation/` validates conversational quality through CES evaluations

It also now includes a LangSmith bridge for externally managed experiments:

- `langsmith-live-experiments.py` executes live CES suites and converts the
  resulting artifacts into LangSmith `/datasets/upload-experiment` payloads
- `run-langsmith-experiments.sh` runs a few focused advisory appointment
  experiments with one command

For a stakeholder-oriented explanation of why this matters for a banking agent,
see
[langsmith-banking-agent-quality.md](/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/docs/langsmith-banking-agent-quality.md).

## What The Framework Gives You

- JSON suite files checked locally before any remote CES call
- stable per-evaluation pass-rate assertions for agent and subagent coverage
- remote execution through `projects.locations.apps.runEvaluation`
- polling and artifact capture for the resulting `evaluationRuns` and `evaluations.results`
- unit tests for the framework itself so the harness is reproducible

## Folder Layout

```text
ces-agent/test-harness/evaluation/
├── ces-evaluation-runner.py
├── langsmith-live-experiments.py
├── README.md
├── suites/
│   ├── agent-quality-golden-suite.json
│   ├── agent-quality-scenario-suite.json
│   ├── langsmith-advisory-booking-suite.json
│   ├── langsmith-advisory-recovery-suite.json
│   └── langsmith-advisory-routing-suite.json
└── tests/
    ├── test_ces_evaluation_runner.py
    └── test_langsmith_live_experiments.py
```

The shared top-level runners live one level up:

- `ces-agent/test-harness/run-evaluation-tests.sh`
- `ces-agent/test-harness/run-langsmith-experiments.sh`

## Quick Start

Before remote runs, the runner checks the repository root `.env` and then
`.tmp/cloud-run/discovery-plan.env`. If `GCP_LOCATION` or `CES_APP_ID` are still
unset, it falls back to `us` and `acme-voice-us` respectively. `GCP_PROJECT_ID`
must still be provided explicitly or via one of those files.

Validate a suite locally:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation
python3 ces-evaluation-runner.py validate-suite \
  --suite ./suites/agent-quality-golden-suite.json
```

List remote evaluation resources:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation
python3 ces-evaluation-runner.py list-evaluations \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID"
```

Run the golden regression suite:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/evaluation
python3 ces-evaluation-runner.py run-suite \
  --suite ./suites/agent-quality-golden-suite.json
```

Run both example suites through the wrapper:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-evaluation-tests.sh all
```

Pass a different stack state file when needed:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-evaluation-tests.sh golden /absolute/path/to/discovery-plan.env
```

Validate the embedded LangSmith suite config:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-langsmith-experiments.sh validate
```

Run the advisory routing live experiment and stage only the LangSmith upload
payload:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-langsmith-experiments.sh routing --upload-mode never --app-id acme-voice-us
```

Run all advisory live experiments and upload them to LangSmith when
`LANGSMITH_API_KEY` is configured:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-langsmith-experiments.sh all --upload-mode auto --app-id acme-voice-us
```

If your LangSmith API key is scoped to more than one workspace, also set
`LANGSMITH_WORKSPACE_ID` or pass `--langsmith-workspace-id <workspace-id>`.

Run only the framework unit tests:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-evaluation-tests.sh unit
```

## Suite DSL

Each suite uses one JSON format for both local validation and remote execution.

Example:

```json
{
  "package_root": "../../acme_voice_agent",
  "project": "${GCP_PROJECT_ID}",
  "location": "${GCP_LOCATION}",
  "app_id": "${CES_APP_ID}",
  "display_name_prefix": "agent-quality-scenario",
  "require_latency_report": true,
  "run_request": {
    "runCount": 5,
    "generateLatencyReport": true
  },
  "default_expected": {
    "min_pass_rate": 0.8,
    "min_completed_results": 5,
    "max_error_count": 0
  },
  "evaluations": [
    {
      "name": "branch_search_munich",
      "owner_agent": "location_services_agent",
      "labels": ["subagent", "location", "critical"]
    }
  ]
}
```

### Top-Level Fields

- `package_root`
  relative path to the CES package containing `evaluations/*/*.json`
- `project`, `location`, `app_id`
  remote CES target, with env placeholders allowed
- `display_name_prefix`
  prefix used to generate a unique run display name
- `require_latency_report`
  when `true`, the suite fails if the completed `EvaluationRun` has no `latencyReport`
- `run_request`
  optional subset of the CES `runEvaluation` request:
  `appVersion`, `config`, `generateLatencyReport`, `goldenRunMethod`,
  `optimizationConfig`, `personaRunConfigs`, `runCount`, `scheduledEvaluationRun`
- `default_expected`
  default assertion thresholds applied to each evaluation unless overridden
- `evaluations`
  explicit list of evaluation entries

### Per-Evaluation Fields

- `name`
  local folder name and CES `displayName` of the evaluation
- `owner_agent`
  optional metadata for reporting
- `labels`
  optional metadata for grouping and review
- `expected`
  optional assertion override:
  `min_pass_rate`, `min_completed_results`, `max_error_count`

## Current Example Suites

### `agent-quality-golden-suite.json`

Covers deterministic structural regressions across the root agent and specialist routing:

- `agent_handover_roundtrip`
- `appointment_routing_english`
- `appointment_routing_german`
- `fee_lookup_basic`
- `fee_routing_german`
- `off_topic_redirect`
- `session_end_live_agent`

### `agent-quality-scenario-suite.json`

Covers more variable behavioural quality for specialist agents:

- `appointment_booking_branch_flow`
- `appointment_human_handoff`
- `appointment_no_slots_recovery`
- `branch_search_munich`
- `fee_schedule_multi_turn`
- `german_branch_search`
- `prompt_injection_attempt`

### `langsmith-advisory-routing-suite.json`

Low-friction live routing experiment for the advisory appointment handoff:

- `appointment_routing_english`
- `appointment_routing_german`

### `langsmith-advisory-booking-suite.json`

Live booking experiment focused on the main advisory scheduling journeys:

- `appointment_booking_branch_flow`
- `appointment_phone_consultation`
- `appointment_service_request_booking`
- `appointment_video_consultation`

### `langsmith-advisory-recovery-suite.json`

Live recovery and lifecycle experiment focused on safety and repair behavior:

- `appointment_cancel_reschedule`
- `appointment_human_handoff`
- `appointment_invalid_contact_repair`
- `appointment_no_slots_recovery`
- `appointment_reschedule_window_closed`

## LangSmith Bridge

`langsmith-live-experiments.py` builds on the existing CES runner instead of
creating a second evaluation framework. The execution flow is:

1. execute one or more live CES suites
2. read the per-suite `summary.json` and `03-evaluation-run.json` artifacts
3. convert each CES evaluation into one LangSmith external-experiment row
4. write the request payload to disk
5. upload to LangSmith when `LANGSMITH_API_KEY` is configured

The suite JSONs embed a small `langsmith` block:

```json
{
  "langsmith": {
    "dataset_name": "ces-advisory-booking-live",
    "dataset_description": "Externally managed advisory booking experiments.",
    "experiment_description": "Live CES booking-flow experiment.",
    "experiment_metadata": {
      "agent": "advisory_appointment_agent",
      "journey_type": "booking"
    }
  }
}
```

This keeps the CES suite as the single source of truth for:

- which evaluations run
- what their thresholds are
- which LangSmith dataset the uploaded experiment belongs to

The bridge supports three upload modes:

- `auto`
  upload when `LANGSMITH_API_KEY` is present, otherwise only write the payload
- `always`
  require `LANGSMITH_API_KEY` and fail if upload is not possible
- `never`
  never call LangSmith; useful for validating the payload contract first

Routing suites also attach a first-pass instruction-quality layer from the
detailed CES result artifacts. Current independent evaluators are:

- `same_language_as_user`
- `no_greeting_on_first_turn`
- `single_question_first_turn`
- `confirmation_before_lifecycle_action`
- `no_ungrounded_slot_or_status_claims`
- `first_turn_stays_in_scope`

These are intentionally independent of CES pass-rate thresholds. A routing run
can therefore reveal prompt bugs such as multilingual drift or overly dense
first-turn clarification even when the CES golden expectation is limited to
handoff correctness. The lifecycle and grounding checks use a tri-state result
(`pass`, `fail`, `not_observed`) so the framework does not claim certainty when
the CES trace never exercised the relevant behavior.

Endpoint notes:

- default LangSmith API root:
  `https://api.smith.langchain.com/api/v1`
- EU/self-hosted endpoints can be provided with `--langsmith-endpoint` or
  `LANGSMITH_ENDPOINT`
- multi-workspace or organization-scoped keys may also require
  `LANGSMITH_WORKSPACE_ID`, which is sent as `X-Tenant-Id`

## Artifacts

Each `run-suite` invocation writes artifacts under:

- `ces-agent/test-harness/evaluation/.artifacts/<timestamp>/`

Artifacts include:

- initial operation payload
- completed long-running operation
- resolved `EvaluationRun`
- one JSON file per `EvaluationResult`
- final `summary.json`

That summary is the CI-friendly contract. It records run state, progress, each
evaluation threshold, actual counts, and whether the suite passed.

Each LangSmith live run writes artifacts under:

- `ces-agent/test-harness/evaluation/.artifacts/langsmith/<timestamp>/<suite-name>/`

Additional LangSmith-specific artifacts include:

- `20-langsmith-upload-request.json`
- `21-langsmith-upload-response.json` or `21-langsmith-upload-skipped.json`
- `99-langsmith-live-error.json` on failures
- aggregate `langsmith-live-summary.json` at the run root
