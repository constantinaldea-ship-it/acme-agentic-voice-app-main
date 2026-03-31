# Feature Request: Direct Audio Fixture Support in CES Evaluations

**Audience:** Google Cloud CX Agent Studio / CES product team  
**Prepared by:** repository maintainers of `ces-agent/voice-testing/`  
**Date:** 2026-03-29

## Executive summary

We are building automated voice regression tests for a CES agent that runs on a
multimodal model. Today, CES runtime session APIs support audio input, but the
evaluation surfaces that are practical for CI/CD automation remain effectively
text-first for externally supplied test cases.

We need a first-class way to run CES **golden** and/or **scenario** evaluations
from **real prerecorded audio fixtures**, including **multi-turn** conversations.

At the moment, our best workaround is to:

1. start from local audio fixtures,
2. transcribe them outside CES or before CES evaluation execution,
3. validate the transcript against expected text, and then
4. optionally replay the linked CES evaluation by name.

That workaround is useful, but it does **not** test the CES multimodal runtime
with the original audio turns.

## What we believe the platform supports today

### Supported today

CES runtime APIs appear to support audio input:

- `SessionInput.audio` accepts base64-encoded audio bytes.
- `runSession` is documented as the single-turn session API surface.
- `BidiRunSession` supports streaming audio via `realtimeInput.audio` and
  audio config.

This means CES can process audio during a live session.

During a live repository validation on **2026-03-29**, our prototype attempt to
send prerecorded audio through single-turn `runSession` returned:

`HTTP 400 ... Input audio config is not supported for RunSession.`

That runtime result reinforces that the correct CES audio surface is the
streaming `BidiRunSession` API, not single-turn `runSession` with audio config.

### Missing or not exposed for automated evaluation workflows

The current evaluation automation surfaces do **not** appear to expose a clean,
documented way to inject arbitrary prerecorded audio files into evaluation runs:

- `projects.locations.apps.runEvaluation` accepts evaluation resources,
  datasets, personas, and run settings, but no obvious per-turn audio payloads
  or audio-fixture overrides.
- evaluation creation and batch-upload flows are documented around text/image
  turn inputs and expectations, not uploaded WAV/AIFF turn inputs.
- evaluation personas expose synthetic voice configuration, but that is not the
  same thing as testing with our own real fixture audio.

## Problem statement

Teams building voice agents need to validate more than transcript correctness.
They need to validate the full multimodal execution path against realistic audio
inputs, including:

- accent and pronunciation differences,
- hesitation and natural speech rhythm,
- truncation and endpointing behavior,
- background noise sensitivity,
- wording that transcribes correctly but still behaves differently when the
  model receives audio rather than text.

Today, CES evaluations are excellent for conversation quality, routing, tool
use, and agent behavior, but they do not appear to give us a programmatic way
to run those same evaluation workflows directly from recorded voice turns.

## Current workaround and why it is insufficient

In this repository, the `ces-agent/voice-testing/` sleeve currently uses the
following pattern:

1. load a local audio fixture,
2. transcribe it using a sidecar transcript or external STT,
3. compare the transcript to an expected utterance,
4. optionally compare it to the linked CES evaluation text contract,
5. optionally call CES `runEvaluation` for the linked evaluation.

This gives us:

- good fixture-level validation,
- confidence that audio still maps to the expected wording,
- optional observation of how the linked CES evaluation behaves remotely.

However, it still falls short because:

- the original audio is **not** what powers the CES evaluation run,
- transcript replay is not the same as multimodal runtime execution,
- the session APIs require custom orchestration that duplicates evaluation
  harness responsibilities,
- pass/fail logic, artifacts, multi-turn coordination, and regression reporting
  must be rebuilt outside the native evaluation system.

In short: we can test audio today, and we can test CES evaluations today, but
we cannot cleanly do both together using the evaluation layer that is best
suited for automation.

## Requested feature

We request a first-class CES capability to run evaluations with **direct audio
fixture inputs**.

### Requested outcomes

1. **Audio-backed golden evaluations**
   - allow end-user turns to be supplied as audio, not only text.

2. **Audio-backed scenario evaluations**
   - allow scenario tasks or generated conversations to execute from audio turn
     fixtures or audio datasets.

3. **Multi-turn audio session support**
   - support evaluation runs where turn 1, turn 2, turn 3, etc. are each driven
     by recorded audio while preserving CES session state across turns.

4. **Programmatic automation support**
   - expose this through documented APIs suitable for CI/CD and batch tooling,
     not only through a console-only workflow.

## Suggested product/API shapes

Any of the following would materially help.

### Option A: Extend evaluation turn input types

Add a supported audio input type to evaluation assets and batch upload.

Example direction:

- new action type such as `INPUT_AUDIO`
- associated fields such as:
  - `audio_mime_type`
  - `audio_encoding`
  - `sample_rate_hertz`
  - `audio_content` (base64)
  - or `audio_uri` (for example GCS-backed fixtures)

### Option B: Extend evaluation JSON/export format

Allow a golden turn's user input to accept either text or audio.

Example direction:

- `userInput.text`
- `userInput.audio`
- `userInput.audioConfig`

### Option C: Extend `runEvaluation`

Allow evaluation execution to reference external audio fixture packs or an audio
evaluation dataset.

Example direction:

- `evaluationInputSource`
- `audioDataset`
- `evaluationOverrides`

### Option D: Voice-session-to-golden capture

Allow teams to record or upload an audio-driven runtime session and save it as a
golden conversation that preserves:

- turn audio references,
- recognized transcript,
- agent outputs,
- tool behavior,
- and replay metadata.

## Acceptance criteria

We would consider this feature successful if all of the following are true:

1. We can run an evaluation from **our own prerecorded audio fixtures**.
2. The capability works for **multi-turn** conversations, not only one-turn
   examples.
3. The workflow is available through a **documented API** suitable for
   automation.
4. Results preserve the same quality-bar value as CES evaluations today:
   - turn-level artifacts,
   - pass/fail status,
   - expectation outcomes,
   - tool-call correctness,
   - hallucination/quality metrics where applicable.
5. Results also include voice-specific diagnostics such as:
   - recognized transcript,
   - recognition metadata/confidence when available,
   - audio format metadata,
   - optional audio-turn artifact references.
6. Tool fakes, personas, and replay modes remain compatible where conceptually
   supported.

## Why this matters

This feature would let teams keep **one** evaluation system for:

- text-first conversation regressions,
- multimodal voice regressions,
- routing/tool correctness,
- and automated release gates.

Without this capability, voice teams must split their testing into two layers:

- native CES evaluations for conversation quality,
- separate custom session harnesses for real audio execution.

That split adds operational cost, reduces comparability, and makes it harder to
ship reliable multimodal voice experiences with confidence.

## Concrete use case from our repository

Our repository contains multi-turn advisory appointment booking fixtures in
English and German. We can already:

- store WAV fixtures,
- validate them via deterministic or live transcription,
- compare them against linked CES evaluation text,
- and replay the linked CES evaluations.

What we cannot do cleanly today is this:

> Run the CES evaluation itself from those exact prerecorded audio turns,
> preserving multi-turn session state and native evaluation reporting.

That is the gap this request is intended to close.

## References consulted

Official CES documentation reviewed on 2026-03-29:

- Evaluation overview:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation`
- Evaluation batch upload:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation-batch-upload`
- Runtime API access:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/api-access`
- `runSession` REST method:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/rest/v1/projects.locations.apps.sessions/runSession`
- `SessionInput` reference:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/rest/v1/SessionInput`
- `runEvaluation` REST method:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/rest/v1beta/projects.locations.apps/runEvaluation`
- `testPersonaVoice` REST method:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/rest/v1beta/projects.locations.apps/testPersonaVoice`
- `EvaluationPersona` reference:  
  `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/rest/v1beta/EvaluationPersona`

## Closing note

We are not asking for CES to replace runtime audio session APIs. Those are
valuable and already useful. We are asking for **evaluation-grade automation**
that lets teams use their own real audio fixtures as first-class inputs when
testing multimodal CES agents.

That would remove a major gap between “voice works at runtime” and “voice is
testable in an automated, regression-friendly way.”