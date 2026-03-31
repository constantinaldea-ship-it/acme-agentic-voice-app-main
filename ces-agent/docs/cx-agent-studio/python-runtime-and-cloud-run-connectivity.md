# CX Agent Studio Python Runtime and Cloud Run Connectivity Analysis

Author: Codex
Date: 2026-03-27
Status: Design analysis
Verified against official documentation on 2026-03-27

## Purpose

This document packages the current repository findings about CX Agent Studio Python tools, OpenAPI toolsets, and Cloud Run connectivity into one decision-oriented reference.

It answers four practical questions:

1. Which Python language features and libraries are documented as usable in CX Agent Studio?
2. Which items are only implied by examples, inferred by repository notes, or currently unverified?
3. Why does the current `ces-agent` package produce errors such as "Any type is not known", "OpenAPI bridge unavailable", or Python tools failing to reach Cloud Run?
4. Which experiments should be run next to separate true platform limits from packaging or runtime wiring problems?

## Executive Summary

- Official baseline: Google documents Python tools as a supported way to implement custom logic, connect to proprietary APIs or databases, call other tools, and make external network requests from CX Agent Studio.
- Official baseline: Google also documents OpenAPI toolsets as a first-class way to call backend services with schema-governed contracts, managed authentication options, and `x-ces-session-context` injection.
- Repository recommendation: for this repository's Cloud Run banking backends, OpenAPI remains the default production path when authentication, explicit contracts, Cloud Run invocation identity, or hidden session-context injection matter.
- Repository recommendation: Python tools remain useful for deterministic helper logic, response shaping, tool chaining, and explicitly validated direct HTTP experiments using the documented CES runtime helpers.
- The current repository already uses the recommended hybrid pattern for customer details: a direct Python tool wrapping attached OpenAPI operations. This pattern only works when the tool executes inside CES runtime and the relevant toolset is attached to the agent.
- The error around `Any` is not supported by the official examples. Google’s Python tool documentation uses `typing.Any` in sample code, so this should be treated as a Studio editor or import-parser symptom until runtime testing proves otherwise.
- For direct HTTP from Python tools, the documented runtime primitive is `ces_requests`. Older ad hoc networking experiments using `urllib` should not be treated as authoritative evidence of current platform behavior.

## Source Basis for the Main Recommendation

The statement below is a synthesis, not a verbatim rule from one single Google page:

> Python tools are valid and useful in CX Agent Studio, but they should be treated as a constrained runtime for deterministic helper logic, response shaping, tool chaining, and carefully scoped outbound HTTP experiments.

This repository now treats that statement as a derived recommendation based on the following source mapping:

| Recommendation fragment | Basis | Source type |
|---|---|---|
| `Python tools are valid and useful in CX Agent Studio` | Google documents that Python code can extend agent capabilities, accept inputs, return results, and connect to proprietary APIs or databases. | Official |
| `constrained runtime` | Google documents a specific runtime surface built around CES globals such as `context`, `tools`, `async_tools`, and `ces_requests`, rather than a general unrestricted Python environment. | Official |
| `deterministic helper logic` | Google explicitly says Python tools can ensure deterministic outcomes for tasks requiring precision, and documents deterministic tool-to-tool invocation. | Official |
| `response shaping` | Google documents that Python tools accept inputs and return results; this repository interprets that as the correct place for thin output filtering and normalization. This is also reinforced by the local CES developer guide’s wrapper pattern. | Official + repository inference |
| `tool chaining` | Google explicitly documents that Python tools can call other tools in the same app using `tools.<tool_name>_<endpoint_name>(...)`. | Official |
| `carefully scoped outbound HTTP experiments` | Google explicitly documents external network requests from Python tools. This repository adds the word `carefully scoped` because OpenAPI tools provide stronger service-integration controls such as session-context injection and service-account based auth patterns. | Official + repository inference |

### Precise references

#### 1. Python tools are valid and useful

Google says Python code tools let you:

- extend agent capabilities
- accept inputs
- return results used by the agent
- implement custom logic
- connect to proprietary APIs or databases
- ensure deterministic outcomes for precise tasks

Reference:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
  Verified during documentation review on 2026-03-27.

#### 2. Why this document calls the runtime "constrained"

This wording is an inference from the runtime reference, which documents a specific CES-provided runtime surface rather than an unrestricted Python environment. In particular, Google documents:

- `context`
- `tools`
- `async_tools`
- `ces_requests`
- variable helpers such as `get_variable`

Reference:

- [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)
  Verified during documentation review on 2026-03-27.

#### 3. Deterministic helper logic

Google directly supports this framing in two ways:

- Python tools can "ensure deterministic outcomes for tasks that require precision"
- Python tools can deterministically call other tools

Reference:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
  Verified during documentation review on 2026-03-27.

#### 4. Response shaping

Google does not use the exact phrase "response shaping" on the Python tools page. This part is an engineering recommendation derived from the documented tool model:

- Python tools take agent inputs and return structured results
- Python tools can wrap and simplify what the agent sees

For this repository, that advice is additionally grounded in the local CES guide, which recommends wrapping APIs with Python tools so the agent sees only the relevant fields.

References:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- Local guide: [developer-guide.md](./developer-guide.md)

#### 5. Tool chaining

Google explicitly documents tool-to-tool calling from Python code, including OpenAPI-backed tool calls via the `tools.<tool_name>_<endpoint_name>(...)` pattern.

Reference:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
  Verified during documentation review on 2026-03-27.

#### 6. Carefully scoped outbound HTTP

Google explicitly documents that Python tools can make external network requests.

This document adds `carefully scoped` as repository guidance, not as a Google quote, because Google’s OpenAPI documentation also provides stronger integration controls for service-backed calls:

- `x-ces-session-context` injection
- service account execution identity
- Cloud Run invocation guidance

References:

- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
  Verified during documentation review on 2026-03-27.
- [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)
  Verified during documentation review on 2026-03-27.

## What Google Officially Documents

### Python tools can execute custom logic and call external systems

Google documents that Python code tools can accept agent inputs, return structured results, and connect to proprietary APIs or databases through tool code.

Reference:
- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)

### Python tools can call other tools in the same agent application

Google documents that Python tools can deterministically call other tools from the same agent application using `tools.<tool_name>_<endpoint_name>(...)`.

This is the foundation of the repository’s hybrid wrapper pattern.

Reference:
- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- Local explanation: [tool-selection-guide.md](../tool-selection-guide.md)

### The Python runtime exposes CES-specific globals

Google documents the following runtime primitives:

- `context`
- `tools`
- `async_tools`
- `ces_requests`
- helper functions such as `get_variable`, `set_variable`, and `remove_variable`

Google also documents that `context.state` and `context.variables` are interchangeable, with `variables` preferred for new code.

Reference:
- [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)

### Python tools can make outbound HTTP requests

Google documents outbound HTTP support through `ces_requests`, described as a requests-like helper for HTTP calls.

Reference:
- [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)
- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)

### OpenAPI tools support hidden session-context injection

Google documents `x-ces-session-context` on OpenAPI parameters. This allows values such as session ID or session variables to be injected into requests at runtime without the model having to predict them.

For banking and other regulated integrations, this is one of the most important platform capabilities because it improves audit correlation and reduces model-visible sensitive identifiers.

Reference:
- [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)
- Local guidance: [design-patterns.md](./design-patterns.md)
- Local decision record: [ADR-CES-004-connectivity-pattern-selection.md](../adr/ADR-CES-004-connectivity-pattern-selection.md)

### OpenAPI tool execution uses the CX Agent Studio service account

Google documents that OpenAPI tool actions are executed with the permissions of the CX Agent Studio service account. This matters for Cloud Run access and transitive access risk.

Reference:
- [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)

### MCP support is limited to Streamable HTTP

Google documents that CX Agent Studio supports only Streamable HTTP transport for MCP servers. SSE is not supported.

Reference:
- [MCP tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)

## Python Library and Language Support Matrix

This section separates documented support from example-level evidence and unverified assumptions.

| Item | Current evidence level | Status | Recommendation |
|---|---|---|---|
| Python tool functions with type hints | Officially documented | Supported | Safe to use. Follow Google’s documented tool style. |
| `typing.Optional` | Official example | Supported | Safe to use. |
| `typing.Dict` | Official example | Supported | Safe to use. |
| `typing.Any` | Official example | Supported enough for current design use | Treat Studio complaints about `Any` as tooling or parsing issues until a CES runtime experiment disproves this. |
| `pydantic.BaseModel` and model validation | Official example | Supported enough for current design use | Safe to use for structured state and validation. |
| `context` | Official runtime reference | Supported | Use for session variables and execution context. |
| `tools` | Official runtime reference | Supported | Use for deterministic tool chaining inside CES runtime only. |
| `async_tools` | Official runtime reference | Supported | Use only when asynchronous tool polling is needed. |
| `ces_requests` | Official runtime reference | Supported | This is the preferred Python HTTP path for experiments and lightweight non-OpenAPI integrations. |
| Plain `requests` library | Mentioned indirectly by official text, but sample code still uses `ces_requests` | Ambiguous | Do not rely on `requests` as a production CES runtime dependency until it passes a sandbox experiment. Prefer `ces_requests`. |
| Python standard library imports such as `typing` | Official examples use them | Supported in practice | Safe for normal language features and lightweight helpers. |
| `urllib` for network access | Technically likely importable as stdlib, but not the documented HTTP path | Low confidence for CES networking | Avoid for backend connectivity tests. Prefer `ces_requests`. |
| `numpy` | Repository documentation claims support, but this was not directly revalidated in the official pages reviewed on 2026-03-27 | Unverified in this analysis pass | Do not adopt unless a sandbox import test passes. |
| `protobuf` | Repository documentation claims support, but this was not directly revalidated in the official pages reviewed on 2026-03-27 | Unverified in this analysis pass | Do not adopt unless a sandbox import test passes. |
| `httpx` | Not documented in the reviewed official pages | Unsupported until proven otherwise | Avoid. |
| `google.auth` | Not documented in the reviewed official pages | Unsupported until proven otherwise | Avoid inside Python tools. Prefer OpenAPI-managed auth. |
| `google.cloud.*` client libraries | Not documented in the reviewed official pages | Unsupported until proven otherwise | Avoid inside Python tools. |
| Arbitrary `pip install` third-party libraries | Not documented in the reviewed official pages | Unsupported until proven otherwise | Avoid. Keep Python tools dependency-light. |

## Important Documentation Nuance: `requests` vs `ces_requests`

The official Python tools page currently says that the sample uses the standard `requests` library available in the environment for external network requests. However, the concrete sample code on the same page uses `ces_requests`, and the Python runtime reference documents `ces_requests` explicitly as the available request helper.

Because of that mismatch, the safest interpretation is:

- `ces_requests` is the documented runtime contract.
- plain `requests` may or may not be available in the sandbox, but should not be treated as a guaranteed platform feature until experimentally confirmed.

This nuance is important because the repository still contains local reference code that imports `requests`, but that code is not the CES runtime contract.

Relevant local file:
- [get_customer_details.py](../../acme_voice_agent/tools/get_customer_details/get_customer_details.py)

Relevant official references:
- [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)

## Current Repository Findings

### 1. The repository already uses the correct hybrid pattern for one banking flow

The customer-details flow uses a direct Python wrapper tool that internally calls attached OpenAPI operations. This is aligned with the hybrid design documented in the repository.

Relevant local files:
- [python_code.py](../../acme_voice_agent/tools/get_customer_details/python_function/python_code.py)
- [get_customer_details.json](../../acme_voice_agent/tools/get_customer_details/get_customer_details.json)
- [customer_details_agent.json](../../acme_voice_agent/agents/customer_details_agent/customer_details_agent.json)
- [tool-selection-guide.md](../tool-selection-guide.md)

### 2. The OpenAPI bridge is runtime-bound, not globally available

The Python wrapper in `get_customer_details_wrapper` defines a fallback `_MissingToolsBridge` that raises an error outside CES runtime. That means the OpenAPI bridge is expected to be unavailable during standalone local import or incomplete package import.

This is not necessarily a platform failure. It is expected behavior when:

- the Python tool is imported outside CES runtime
- the tool is tested without the agent that owns the toolset
- the toolset is not attached to the executing agent
- the tool is imported in isolation rather than as part of the full app package

Relevant local files:
- [python_code.py](../../acme_voice_agent/tools/get_customer_details/python_function/python_code.py)
- [customer_details_agent.json](../../acme_voice_agent/agents/customer_details_agent/customer_details_agent.json)

### 3. The runtime layout now matches the documented skeleton

The repository documentation uses the canonical tool layout `tools/<tool_name>/python_function/python_code.py`, and the `get_customer_details_wrapper` tool now follows that shape. This removes one source of packaging ambiguity when comparing repository structure to Google documentation and sample layouts.

Relevant local files:
- [developer-guide.md](./developer-guide.md)
- [get_customer_details.json](../../acme_voice_agent/tools/get_customer_details/get_customer_details.json)

### 4. The current deployment path assumes full app import, not tool-only import

The packaging and ZIP validation flow in the repository is built around the whole `acme_voice_agent` application package. That matters because the Python wrapper depends on agent-level toolset attachment.

Relevant local files:
- [ces-deploy-manager.py](../../scripts/deploy/ces-deploy-manager.py)
- [deploy-agent.sh](../../scripts/deploy/deploy-agent.sh)
- [validate-package.py](../../scripts/deploy/validate-package.py)

### 5. Older repo notes about Python DNS failures are no longer sufficient evidence

The repository correctly notes that older experiments saw DNS failures with ad hoc networking code such as `urllib`. However, the current official documentation explicitly documents outbound HTTP through `ces_requests`.

That means the correct next step is not to assume that Python tools cannot reach Cloud Run. The correct next step is to revalidate with the documented request helper.

Relevant local files:
- [tool-selection-guide.md](../tool-selection-guide.md)
- [test-tool-call.py](../../scripts/test-tool-call.py)

## Why the Current Errors Are Happening

### Error class A: "`Any` type is not known"

Most likely explanation:

- This is a Studio-side parser, schema extraction, or editor-intellisense issue rather than a hard CES runtime limit.

Why:

- Google’s Python tool sample uses `Any` in tool examples.
- The repository wrapper uses ordinary `typing.Any`, not a non-standard type.

Current conclusion:

- Do not treat `Any` as unsupported by the platform.
- Treat it as a candidate tooling/import issue to verify experimentally.

### Error class B: "OpenAPI bridge is unavailable"

Most likely explanation:

- The Python wrapper is being executed or validated without the runtime-attached `customer_details_openapi` toolset.

Why:

- The wrapper explicitly raises that error when the CES `tools` bridge is missing.
- The bridge only becomes meaningful when the agent owns both the direct Python tool and the referenced toolset operations.

Current conclusion:

- This symptom points first to packaging, import scope, or agent-toolset wiring.
- It should not yet be classified as a platform limitation.

### Error class C: Python tool cannot "understand" Cloud Run DNS

Most likely explanation:

- Earlier tests used the wrong HTTP mechanism for CES Python runtime, or mixed local-library assumptions with the CES sandbox.

Why:

- The official runtime documents `ces_requests` for outbound HTTP.
- The repository’s negative DNS evidence came from ad hoc networking approaches, not the now-documented helper.

Current conclusion:

- The correct hypothesis is not "Python tools have no network."
- The correct hypothesis is "ad hoc HTTP code and documented CES runtime HTTP are not equivalent and must be tested separately."

## Repository Connectivity Pattern Guidance for Cloud Run

This section is repository guidance layered on top of the official Google baseline. It is not a claim that Python tools are unsupported for Cloud Run access.

| Pattern | Recommendation | Why |
|---|---|---|
| OpenAPI toolset calling Cloud Run | Preferred production default | Best fit for auth, schema contracts, Cloud Run invocation identity, and `x-ces-session-context`. |
| Python tool calling OpenAPI operations through `tools.<toolset>_<operation>` | Preferred hybrid pattern | Keeps backend call in OpenAPI while allowing Python-side formatting and deterministic orchestration. |
| Python tool calling Cloud Run directly with `ces_requests` | Good experiment path and acceptable for low-risk helper cases | Officially documented HTTP path, but auth and session wiring remain manual. |
| Python tool calling Cloud Run with `requests` or `urllib` | Avoid for primary decision-making | Not the clearest documented runtime path. |
| MCP server on Cloud Run | Separate path, not a fix for Python tool HTTP issues | Useful for dynamic tool registries; limited by Streamable HTTP and weaker session-context story for banking. |

## Recommended Experiment List

These experiments should be run in a CES sandbox before making platform-wide conclusions.

### E-01: Python import matrix

Question:

- Which imports succeed in the actual CES Python runtime?

Test set:

- `typing.Any`
- `pydantic`
- `requests`
- `numpy`
- `protobuf`
- `httpx`
- `google.auth`

Success criteria:

- Each import is tested by a minimal Python tool that returns a structured success or failure result.

Decision value:

- Converts library support from assumption into evidence.

### E-02: Public Cloud Run connectivity via documented Python HTTP

Question:

- Can a CES Python tool reach a public Cloud Run endpoint when it uses `ces_requests`?

Method:

- Create a trivial public `/health` endpoint on Cloud Run.
- Call it from a Python tool using `ces_requests`.
- Compare with the same endpoint reached through an OpenAPI toolset.

Success criteria:

- Both paths return consistent status and body.

Decision value:

- Separates DNS or egress failure from backend application failure.

### E-03: IAM-protected Cloud Run access comparison

Question:

- Is the barrier for Python-to-Cloud-Run access connectivity, or authentication?

Method:

- Keep one endpoint public and one endpoint protected by Cloud Run IAM.
- Test OpenAPI with service-agent auth.
- Test Python with `ces_requests` only if a manual auth story is intentionally being evaluated.

Success criteria:

- OpenAPI path succeeds against protected endpoint.
- Python path result is explicitly classified as either unsupported, unsupported-without-manual-auth, or workable.

Decision value:

- Prevents conflating auth failure with network failure.

### E-04: OpenAPI bridge availability test

Question:

- Under which import/deployment conditions is `tools.<toolset>_<operation>` available?

Method:

- Test the wrapper tool in three states:
  - standalone tool import
  - full app import without toolset attached to the executing agent
  - full app import with toolset attached

Success criteria:

- Document exactly when bridge calls succeed and fail.

Decision value:

- Determines whether the current failure is packaging scope rather than platform limitation.

### E-05: Packaging shape comparison

Question:

- If import issues persist after layout alignment, does CX Studio still behave differently from the documented full-package skeleton?

Method:

- Re-run the same CES import checks after the wrapper has been aligned to `python_function/python_code.py`.
- If problems remain, compare the current package against a minimal fresh package that matches the documented skeleton exactly.

Success criteria:

- Import and runtime behavior are compared apples-to-apples.

Decision value:

- Clarifies whether part of the failure is repository-specific packaging drift.

## Recommended Working Policy for This Repository

### Production default

- Use OpenAPI toolsets for Cloud Run-backed banking services.

### Allowed Python use

- Deterministic formatting
- response shaping
- wrapper tools
- tool chaining
- low-risk helper integrations
- explicitly approved experiments using `ces_requests`

### Avoid by default

- direct use of non-documented HTTP libraries inside CES Python tools
- sensitive banking connectivity implemented primarily in Python tool code
- assuming that local `requests`-based scripts prove CES runtime compatibility
- assuming that older `urllib` DNS failures still describe the current platform

## Cross-References in This Repository

- [tool-selection-guide.md](../tool-selection-guide.md)
- [developer-guide.md](./developer-guide.md)
- [design-patterns.md](./design-patterns.md)
- [platform-reference.md](./platform-reference.md)
- [ADR-CES-004-backend-language-choice.md](../adr/ADR-CES-004-backend-language-choice.md)
- [ADR-CES-004-connectivity-pattern-selection.md](../adr/ADR-CES-004-connectivity-pattern-selection.md)
- [python_code.py](../../acme_voice_agent/tools/get_customer_details/python_function/python_code.py)
- [get_customer_details.json](../../acme_voice_agent/tools/get_customer_details/get_customer_details.json)
- [customer_details_agent.json](../../acme_voice_agent/agents/customer_details_agent/customer_details_agent.json)
- [environment.json](../../acme_voice_agent/environment.json)
- [ces-deploy-manager.py](../../scripts/deploy/ces-deploy-manager.py)
- [deploy-agent.sh](../../scripts/deploy/deploy-agent.sh)
- [validate-package.py](../../scripts/deploy/validate-package.py)

## External References

- Google Cloud, CX Agent Studio, [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python)
- Google Cloud, CX Agent Studio, [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python)
- Google Cloud, CX Agent Studio, [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api)
- Google Cloud, CX Agent Studio, [MCP tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp)

## Final Conclusion

The repository should not frame the current problem as a generic "CX Studio Python limitation" without qualification.

The evidence reviewed on 2026-03-27 supports a more precise position:

- Python tools are supported and useful in CX Agent Studio.
- The documented runtime contract is centered on `context`, `tools`, and `ces_requests`.
- For this repository, OpenAPI remains the preferred integration path for Cloud Run banking backends.
- The current failure pattern is most likely a mix of runtime-bridge assumptions, packaging shape, import scope, and unvalidated library expectations.
- The next decisions should be driven by a small set of targeted CES sandbox experiments, not by local-script behavior alone.
