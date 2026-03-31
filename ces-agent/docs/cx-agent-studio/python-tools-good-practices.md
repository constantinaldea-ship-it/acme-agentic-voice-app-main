# CX Agent Studio Python Tools Good Practices

Author: Codex
Date: 2026-03-27
Status: Practical guidance
Verified against official documentation and repository analysis on 2026-03-27

## Purpose

This document defines good practices for Python tools in CX Agent Studio for this repository.

It separates:

- the official Google-documented baseline for Python tools
- the stricter repository guidance used for the current POC

It is intentionally opinionated for the current POC phase:

- keep Python tools simple
- keep Python tools deterministic
- keep Python tools thin
- keep Python tools inside the documented CES runtime surface

This is not a general Python style guide. It is a CES Python tools operating guide for `ces-agent`.

## POC Rule of Thumb

For this repository, a good Python tool should usually satisfy all of the following:

- one clear purpose
- one model-visible tool call
- minimal parameters
- minimal branching
- minimal response shaping
- no hidden business workflow spread across multiple model turns
- no dependency on undocumented libraries

If a Python tool starts looking like a small application, it is already too complex for the current POC.

## Primary Design Principle

**Official baseline:** Google documents Python tools as valid for custom logic, proprietary API access, proprietary database access, tool chaining, and external network requests. See [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python) and the [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python).

**Repository recommendation:** For this repository’s current POC, keep Python tools simple and thin even though the platform officially supports broader usage.

When this guide says `default`, `prefer`, or `recommended`, read that as repository guidance rather than as a Google platform restriction.

Use Python tools for local deterministic glue, and use broader Python tool integrations only when the reason is explicit.

For this repository's POC, that means:

- use OpenAPI toolsets as the default backend integration path
- use Python tools to simplify what the model sees
- use Python tools to wrap multiple backend calls into one deterministic action
- use Python tools to format, filter, and normalize responses for voice use

This follows the documented CES patterns and the repository’s current architecture direction.

Related documents:

- [tool-selection-guide.md](../tool-selection-guide.md)
- [python-runtime-and-cloud-run-connectivity.md](./python-runtime-and-cloud-run-connectivity.md)
- [ADR-CES-004-connectivity-pattern-selection.md](../adr/ADR-CES-004-connectivity-pattern-selection.md)

## When to Use a Python Tool

Use a Python tool when one of these is true:

- you need deterministic formatting or response shaping
- you need one wrapper tool that hides multiple internal calls from the model
- you need a direct CES tool that can be asserted in evaluations
- you need a lightweight helper with a small input and a small output

Do not start with a Python tool when one of these is true:

- the primary problem is calling a backend REST API with managed auth requirements
- the backend needs hidden session-context injection
- the backend contract is already stable and deserves a first-class OpenAPI schema
- the tool needs many parameters, many branches, or complex retries

In those cases, this repository usually starts with OpenAPI and adds a thin Python wrapper only if it improves the agent surface. That is a repository recommendation, not a claim that Python tools are unsupported there.

## Default Pattern for This Repository

Repository-preferred order:

1. OpenAPI toolset for backend connectivity
2. thin Python wrapper for deterministic orchestration or output shaping
3. direct Python HTTP only for explicitly approved low-risk experiments

This is the simplest stable pattern for the current CES POC. It is stricter than the official baseline on purpose.

## Keep the Tool Contract Small

A good Python tool for this repo should keep the model contract narrow.

Good practices:

- expose only the parameters the model truly needs
- keep parameter names explicit and predictable
- flatten parameter shapes where possible
- return only the fields the agent needs to answer
- avoid leaking raw backend payloads into the model context

Bad signs:

- nested request bodies with many optional fields
- tool outputs that mirror the full backend schema
- tools that expect the model to carry intermediate tokens, IDs, or internal headers

## Keep the Runtime Surface Small

For this repository, the default allowed runtime surface for CES Python tools should be:

- Python language basics and standard library features used for simple logic
- `typing`
- `context`
- `tools`
- `async_tools` only if truly needed
- `ces_requests` for explicitly approved direct HTTP experiments
- `pydantic` only when structured validation clearly helps

Default avoid list:

- `requests`
- `httpx`
- `urllib` as the primary backend connectivity mechanism
- `google.auth`
- `google.cloud.*`
- arbitrary third-party libraries
- any dependency that requires guessing whether it exists in CES runtime

Reason:

- the official runtime contract is much clearer around CES-provided primitives than around arbitrary Python libraries
- the POC should reduce uncertainty, not add more

Reference:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)
- [python-runtime-and-cloud-run-connectivity.md](./python-runtime-and-cloud-run-connectivity.md)

## Good Python Tool Characteristics

### 1. Deterministic

The tool should do the same thing every time for the same input.

Good:

- validate input
- call a known helper or backend
- normalize the result
- return a stable structure

Avoid:

- hidden randomness
- broad exception swallowing without clear output
- logic that depends on untracked global state

### 2. Thin

The tool should not own business architecture that belongs in backend services.

Good:

- selecting a few backend fields for voice output
- chaining two or three known operations into one wrapper
- converting low-level responses into a compact tool result

Avoid:

- implementing full banking rules in tool code
- reproducing backend authorization logic
- accumulating many special-case branches inside the tool

### 3. Predictable

The tool should have one obvious reason to exist.

Good:

- `get_customer_details_wrapper`
- `search_and_format_branches`
- `get_verified_balance`

Avoid:

- general-purpose mega-tools
- tools that mix unrelated domains
- names that are vague or overlap heavily with other tools

### 4. Small Output

The output should help the model answer, not force the model to sift through noise.

Good:

- `success`
- `summary`
- a compact domain object
- a short error object with stage and message

Avoid:

- full backend payload dumps
- raw auth payloads
- internal request/response tracing in normal outputs

## Recommended Structure for a POC Python Tool

For this repo, a good POC Python tool usually has this structure:

1. validate inputs
2. normalize inputs
3. perform one deterministic operation or wrapper chain
4. normalize outputs
5. return one stable result shape

That is enough for most POC tools.

If the tool needs much more than that, stop and ask whether it should instead become:

- an OpenAPI toolset-backed backend endpoint
- a thin wrapper over an existing toolset
- a backend service change rather than a Python tool change

## Error Handling Guidance

Good CES Python tool error handling should be simple and structured.

Prefer:

- one consistent failure shape
- a small number of error stages
- clear user-safe messages

Avoid:

- returning raw stack traces
- mixing many unrelated error schemas
- making the model infer whether a failure is retryable

A tool should tell the agent enough to react safely, but not dump internal implementation detail.

## Tool Chaining Guidance

If multiple operations must happen in one user turn, prefer one wrapper tool over multiple model-predicted tool calls.

Why:

- fewer model calls
- lower token use
- less parameter hallucination risk
- simpler evaluation surface

In this repository, wrapper tools over OpenAPI operations are a strong fit when:

- the backend contract should stay in OpenAPI
- the model should still see one simple tool
- the final result needs voice-friendly shaping

Reference:

- [developer-guide.md](./developer-guide.md)
- [tool-selection-guide.md](../tool-selection-guide.md)

## Direct HTTP Guidance

Official baseline:

- direct Python HTTP is supported in CES via `ces_requests`

For the current POC in this repository:

- direct Python HTTP is not the default integration path
- if direct Python HTTP is tested, use `ces_requests`
- do not use ad hoc HTTP libraries as the primary runtime assumption

Direct Python HTTP is acceptable only when all of these are true:

- the use case is low risk
- the auth story is simple and explicit
- there is a clear reason not to use OpenAPI
- the experiment is documented as an experiment

For this repository's Cloud Run and sensitive banking backends, prefer OpenAPI first.

## Simplicity Rules for This POC

Use these rules during review:

- if the tool needs more than a few parameters, it is probably too broad
- if the tool returns more than the agent needs to speak, it is probably too noisy
- if the tool needs undocumented libraries, it is too risky
- if the tool contains policy logic, it probably belongs elsewhere
- if the tool is difficult to explain in one sentence, it is probably too complex

## Testing Guidance

Minimum expectation for a POC Python tool:

- local import sanity check
- one happy-path test
- one validation-failure test
- one dependency-failure or backend-failure test

For CES-level validation:

- prefer a simple golden evaluation for direct tool invocation behavior
- prefer backend integration tests for request-level correctness
- avoid building the testing strategy around OpenAPI operation-level assertions that CES does not support well

## Review Checklist

Before accepting a new or changed Python tool, verify:

- the tool has one clear job
- the tool is smaller than the equivalent model-driven multi-step flow
- the parameters are minimal and well named
- the output is small and stable
- the runtime dependencies are documented and allowed
- direct HTTP, if any, uses the documented CES path
- sensitive backend work follows this repository's OpenAPI-first guidance
- the tool can be explained in one short paragraph

## Recommended Language for Team Reviews

When discussing Python tools in this repository, use this language.

These are repository phrases, not official Google terminology:

- Python tools are acceptable for deterministic CES-local glue
- Python tools are not the default regulated banking integration layer
- OpenAPI remains the default for backend connectivity
- simplicity is a requirement, not a preference, for the POC

This keeps architecture discussions aligned and avoids drifting into an accidental Python-heavy backend strategy.

## Cross-References

- [python-runtime-and-cloud-run-connectivity.md](./python-runtime-and-cloud-run-connectivity.md)
- [developer-guide.md](./developer-guide.md)
- [design-patterns.md](./design-patterns.md)
- [platform-reference.md](./platform-reference.md)
- [tool-selection-guide.md](../tool-selection-guide.md)
- [ADR-CES-004-backend-language-choice.md](../adr/ADR-CES-004-backend-language-choice.md)
- [ADR-CES-004-connectivity-pattern-selection.md](../adr/ADR-CES-004-connectivity-pattern-selection.md)

## External References

- Google Cloud, CX Agent Studio, [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- Google Cloud, CX Agent Studio, [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)
- Google Cloud, CX Agent Studio, [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)

## Final Recommendation

For this repository’s POC, the best Python tool is usually a very small wrapper:

- small input
- small deterministic body
- small normalized output
- no undocumented libraries
- OpenAPI-first whenever backend integration matters

That should be treated as the repository default standard unless there is a clear reason to do something more complex. The official Google baseline is broader than this repository standard.
