# Voice Testing Flow Comparison

This note compares the **previous text-first voice-testing path** with the
**new target direct CES audio runtime path**.

## Side-by-side flow

```mermaid
flowchart LR
  subgraph Legacy["Previous text-first voice testing flow"]
    A1["Audio fixture<br/>.wav or .aiff"] --> A2["Local transcription<br/>sidecar or OpenAI"]
    A2 --> A3["Assert transcript matches<br/>expected_transcript"]
    A3 --> A4["Assert first turn matches<br/>linked CES evaluation text"]
    A4 --> A5["Optional CES runEvaluation replay<br/>using linked evaluation name"]
    A5 --> A6["Artifacts<br/>transcripts plus replay metadata"]
  end

  subgraph New["New target direct CES audio runtime flow"]
    B1["Audio fixture<br/>PCM WAV"] --> B2["Same local transcript validation<br/>for regression safety"]
    B2 --> B3["Streaming CES audio path<br/>BidiRunSession(audio)"]
    B3 --> B4["Shared CES session<br/>one session per scenario"]
    B4 --> B5["CES multimodal runtime<br/>consumes audio directly"]
    B5 --> B6["Artifacts<br/>transcripts plus per-turn session outputs"]
  end
```

> **Live validation note (2026-03-29):** the first `--ces-session` prototype
> attempted single-turn `runSession` and CES returned
> `HTTP 400 ... Input audio config is not supported for RunSession.` The runner
> now uses **`BidiRunSession`**, which is the correct CES runtime audio path.

## Why this matters

The previous harness path is still useful because it protects the text
contract:

- audio fixture quality
- transcript stability
- alignment with linked CES evaluation text

The new target direct-session path adds a different assurance layer:

- the deployed CES runtime receives the real audio
- the scenario stays inside one CES session across multiple turns
- artifacts capture actual session outputs turn by turn

## Practical interpretation

- Use the **previous path** when you care most about regression safety and
  transcript drift.
- Use the **new target path** when you need confidence that the CES multimodal
  runtime itself can process the prerecorded audio fixtures through
  `BidiRunSession`.