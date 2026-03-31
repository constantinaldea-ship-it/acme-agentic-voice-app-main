# CES Tool Selection Guide — OpenAPI vs Direct Tools vs MCP

> **Status:** Active  
> **Last Updated:** 2026-03-13  
> **Purpose:** Decision framework for choosing between tool types in CX Agent Studio (CES).  
> **Audience:** Agent developers packaging tools for CES import.  
> **Cross-references:**  
> - [evaluations-howto.md](./evaluations-howto.md) — Evaluation DSL and gotchas G-01, G-02  
> - [platform-reference.md](../../docs/architecture/cx-agent-studio/platform-reference.md) § 5 — Tool Integration  
> - [developer-guide.md](../../docs/architecture/cx-agent-studio/developer-guide.md) § 5–7 — Tool best practices  
> - [CHANGELOG.md](../CHANGELOG.md) — Learnings L-01, L-11  
> - [IMPROVEMENT-BACKLOG.md](../IMPROVEMENT-BACKLOG.md) — Lessons learned  

---

## TL;DR — Quick Decision

**Official baseline:** Google documents Python tools as a supported way to implement custom logic, connect to proprietary APIs or databases, call other tools, and make external network requests. OpenAPI tools are also a first-class supported integration path. This guide’s recommendations below are repository guidance layered on top of that documented baseline, not a claim that Python tools are only for helper logic.

```
Need to call an external REST API?
  └─ Yes → Official docs support both Python tools and OpenAPI tools.
              └─ If you want CES-managed auth, schema contract, or `x-ces-session-context`
                 → OpenAPI toolset  (repository default for these cases)
              └─ If you want code-first logic, tool chaining, or direct API/database access
                 → Direct Python function tool can work
  └─ No  → Is it deterministic logic, data transform, or session management?
              └─ Yes → Direct Python function tool
              └─ No  → Do you need dynamic tool discovery from an external server?
                          └─ Yes → MCP tool
                          └─ No  → Direct Python function tool
```

---

## 1. Tool Types at a Glance

CES supports three primary tool integration patterns. Each has fundamentally different execution models.

### 1.0 Documentation Baseline vs Repository Guidance

Google’s documented baseline is:

- Python tools are supported for custom logic
- Python tools can connect to proprietary APIs or databases
- Python tools can call other tools
- Python tools can make external HTTP requests
- OpenAPI tools are also supported and provide schema-governed HTTP integration

This guide adds repository recommendations on top of that baseline. Whenever you see words such as `default`, `prefer`, or `recommended`, read them as repository guidance rather than as a Google platform restriction.

| | **OpenAPI Toolset** | **Direct Tool (Python)** | **MCP Tool** |
|---|---|---|---|
| **Config key** | `openApiToolset` | `pythonFunction` | `mcpTool` |
| **Execution environment** | CES platform (server-side HTTP) | CES Python sandbox | External MCP server |
| **Network access** | ✅ Full (DNS, HTTPS, mTLS) | ✅ External HTTP via `ces_requests` (documented) | ✅ Via MCP protocol |
| **Auth model** | `serviceAgentIdTokenAuthConfig`, OAuth, API key | ⚠️ Manual in code / request headers | Same as OpenAPI tools |
| **Session context injection** | ✅ `x-ces-session-context` | ⚠️ Access via `context` / `variables`; manual mapping into requests | Unknown |
| **`toolCall` in golden evals** | ❌ Not supported (L-01) | ✅ Fully supported | Unknown (untested) |
| **Mock in scenario evals** | ❌ Not supported (L-11) | ✅ `mockToolResponse` works | Unknown |
| **CES transport** | HTTP request/response | N/A | Streamable HTTP (required since 2026-02-12) |
| **Callback hooks** | ✅ `before_tool` / `after_tool` | ✅ `before_tool` / `after_tool` | ✅ `before_tool` / `after_tool` |
| **Maintenance** | Low (schema + server) | Medium (Python code in sandbox) | High (external server lifecycle) |

---

## 2. Detailed Comparison

### 2.1 OpenAPI Toolsets (`openApiToolset`)

**How it works:** You provide an OpenAPI schema (YAML/JSON). CES maps each `operationId` to a callable tool. The platform makes the HTTP call — your agent never touches the network.

**Best for:**
- Calling existing REST/HTTP backends (your own or third-party)
- Services requiring authentication (ID tokens, OAuth, API keys)
- APIs where session context should be injected automatically via `x-ces-session-context` headers
- Scenarios where you want CES to handle retries, timeouts, and error formatting

**Limitations:**
- Operations are **not** direct Tool resources — cannot be referenced in `toolCall` evaluation expectations (L-01)
- Cannot be mocked in scenario evaluations (L-11)
- Schema must be valid OpenAPI 3.0+ (CES parser is strict)
- Server URL can use `$env_var` substitution from `environment.json`

**Config structure:**

```
toolsets/
  location/
    location.json                              ← toolset config
    open_api_toolset/
      open_api_schema.yaml                     ← OpenAPI 3.0 schema
```

**Toolset config** (`location.json`):
```json
{
  "displayName": "location",
  "openApiToolset": {
    "openApiSchema": "toolsets/location/open_api_toolset/open_api_schema.yaml",
    "apiAuthentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

**Agent reference** — agent JSON uses `toolsets[]` array with selected `toolIds`:
```json
{
  "displayName": "location_services_agent",
  "tools": ["end_session"],
  "toolsets": [{
    "toolset": "location",
    "toolIds": ["getBranch", "searchBranches"]
  }]
}
```

**`x-ces-session-context` injection** (annotate OpenAPI parameters):
```yaml
parameters:
  - name: x-project-id
    in: header
    x-ces-session-context: project_id
    schema:
      type: string
```

CES injects the value at call time — the model never sees or predicts this parameter.

---

### 2.2 Direct Python Function Tools (`pythonFunction`)

**How it works:** You write a Python function. CES executes it in a sandboxed environment. The function receives the parameters the model predicted and returns a structured result. Per the current CES docs, Python tools can also make external HTTP requests using the built-in `ces_requests` helper.

**Best for:**
- Calling proprietary APIs or databases, as explicitly documented by Google
- Deterministic logic (calculations, formatting, data transforms)
- Session management actions (`end_session` is a platform-provided direct tool)
- Lightweight HTTP calls where maintaining an OpenAPI contract is not worth the overhead
- Wrapping multiple API calls into a single tool (tool chaining — see § 4.3)
- Cases where you need full `toolCall` assertion support in evaluations

**Limitations:**
- HTTP is supported, but request construction, headers, auth, retries, and response shaping are your responsibility in code
- No first-class OpenAPI auth configuration or `x-ces-session-context` injection
- The runtime is documented through CES-specific primitives such as `context`, `tools`, `async_tools`, and `ces_requests`; if you plan to rely on additional libraries, validate them in a CES sandbox first
- `None` defaults rejected — use `str = ""` for optional parameters
- Only `str` parameter types work reliably (no `float`, `int`, `bool` defaults as `None`)
- Stay within the documented runtime surface unless you have explicit validation evidence

**Config structure:**

```
tools/
  search_branches/
    search_branches.json                       ← tool config
    python_function/
      python_code.py                           ← implementation
```

**Tool config** (`search_branches.json`):
```json
{
  "pythonFunction": {
    "name": "search_branches",
    "pythonCode": "tools/search_branches/python_function/python_code.py",
    "description": "Search for bank branches by city, address, or coordinates.\n\nArgs:\n    city: City name (German, e.g. 'München')\n    limit: Max results (numeric string, e.g. '10')\n\nReturns:\n    Dict with 'success', 'data' containing 'branches' array."
  },
  "executionType": "SYNCHRONOUS",
  "displayName": "search_branches"
}
```

**Agent reference** — agent JSON lists tools directly in `tools[]` array:
```json
{
  "displayName": "location_services_agent",
  "tools": ["search_branches", "get_branch", "end_session"]
}
```

**Implementation** (`python_code.py`) — use the documented `ces_requests` helper for outbound HTTP:
```python
BFA_BASE_URL = "https://your-service.run.app"

def search_branches(
    city: str = "",
    limit: str = "",
) -> dict:
    """Search for bank branches by city."""
    params = {}
    if city:
        params["city"] = city
    if limit:
        params["limit"] = limit

    try:
      res = ces_requests.get(
        url=f"{BFA_BASE_URL}/api/v1/branches",
        params=params,
        headers={},
      )
      res.raise_for_status()
      return res.json()
    except Exception as e:
        return {"success": False, "error": str(e)}
```

  > ✅ **Current CES docs explicitly support external network requests from Python tools** via
  > `ces_requests`, and the Python tools page also documents proprietary API/database integration.
  > This guide still recommends OpenAPI toolsets in cases where you specifically want
  > platform-managed auth, schema-driven contracts, or `x-ces-session-context`.

---

### 2.3 MCP Tools (`mcpTool`)

**How it works:** CES connects to an external MCP (Model Context Protocol) server. The server advertises available tools dynamically. CES discovers and invokes them at runtime.

**Best for:**
- Dynamic tool registries where available tools change at runtime
- Stateful server-side interactions that maintain context across calls
- Reusing an existing MCP server across multiple agents or platforms
- Organizations standardizing on MCP for tool interoperability

**Current limitations:**
- **CES requires Streamable HTTP transport** for MCP servers — SSE transport is not supported (as of 2026-02-12, per [CES MCP docs](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp))
- Authentication follows the same model as OpenAPI tools (`serviceAgentIdTokenAuthConfig`, OAuth, API key)
- No examples exist in this project — MCP tools are documented but untested
- Evaluation support (golden `toolCall`, scenario mocking) is **unknown/untested**

**Conceptual config** (based on CES documentation — not validated in this project):
```json
{
  "displayName": "dynamic_tools",
  "mcpTool": {
    "serverUri": "https://your-mcp-server.run.app",
    "authentication": {
      "serviceAgentIdTokenAuthConfig": {}
    }
  }
}
```

**When NOT to use MCP:**
- If your tools are static and well-defined → use OpenAPI toolset
- If you need evaluation support (golden `toolCall` assertions) → use direct Python tools
- If you don't have an MCP server already → the overhead isn't justified

---

## 3. Decision Framework

### 3.1 Primary Decision: Where Does the Logic Live?

| Logic location | Tool type | Why |
|---------------|-----------|-----|
| External REST API (yours or third-party) | **OpenAPI toolset** or **Direct Python tool** | Both are officially supported. This repository defaults to OpenAPI when auth, contract control, and `x-ces-session-context` matter. |
| Proprietary API or database access | **Direct Python tool** | Explicitly documented on the Python tools page. Useful when a code-first tool is clearer than an OpenAPI contract. |
| Lightweight external HTTP call without an OpenAPI contract | **Direct Python tool** | Supported via `ces_requests`; good for quick adapters when manual request handling is acceptable. |
| Local computation (math, formatting, validation) | **Direct Python tool** | Runs in sandbox. No network needed. Fully testable in evals. |
| External server with dynamic tool registry | **MCP tool** | Dynamic discovery. But high operational overhead. |
| External API **and** you need `toolCall` assertions | **Hybrid** (see § 4.3) | OpenAPI for HTTP + Python wrapper for eval assertability. |

### 3.2 Secondary Decision: How Will You Test It?

| Testing need | Compatible tool types |
|-------------|----------------------|
| Golden eval `toolCall` assertion | ✅ Direct Python tools only |
| Golden eval `agentResponse` check | ✅ All tool types |
| Scenario eval `mockToolResponse` | ✅ Direct Python tools only |
| Scenario eval with live backend | ✅ OpenAPI toolsets, Direct Python tools, MCP tools |
| Deterministic unit testing | ✅ Java/backend tests (all types) |

**If evaluation assertability is critical**, prefer direct Python tools — even if it means wrapping an OpenAPI call (see hybrid pattern below).

### 3.3 Decision Matrix — "Use X When Y"

| Scenario | Recommended | Rationale |
|----------|-------------|-----------|
| Calling your own Cloud Run backend | **OpenAPI toolset** or **Direct Python tool** | Both are supported. This guide recommends OpenAPI when you want platform-managed auth and `x-ces-session-context`; Python remains valid for code-first integrations and experiments. |
| Calling a third-party API with OAuth | **OpenAPI toolset** | CES handles OAuth token flow natively. |
| Calling a simple HTTP endpoint with no useful OpenAPI contract | **Direct Python tool** using `ces_requests` | Lowest friction path when manual headers/auth are acceptable. |
| Calling a proprietary API or database from tool code | **Direct Python tool** | Matches the official Python tools baseline directly. |
| Formatting a response for voice output | **Direct Python tool** | Pure computation. No network needed. |
| Ending a session / handing off to human | **Direct Python tool** | `end_session` is the canonical example. |
| Chaining multiple API calls into one action | **Direct Python tool** via `tools.<toolset>_<operationId>()` | Single tool call from model's perspective. See § 4.3. |
| Need `toolCall` assertions in golden evals | **Direct Python tool** | Only direct tools are Tool resources in CES. |
| Dynamic tool catalog that changes at runtime | **MCP tool** | Dynamic discovery is MCP's core value prop. |
| Need to inject session context into API calls | **OpenAPI toolset** | `x-ces-session-context` annotation. Automatic injection. |
| Simple data lookup with no auth | **Direct Python tool** | If data is small, embed it. If large, use OpenAPI toolset. |

---

## 4. Key Findings from This Project

### 4.1 L-01: `toolCall` Cannot Reference OpenAPI Operations

**Discovery:** CES import failed with:
> `Reference 'location.searchBranches' of type 'ces.googleapis.com/Tool' not found.`

**Root cause:** OpenAPI toolset operations (`searchBranches`, `getBranch`) are not Tool resources in CES. They are operations managed by a Toolset resource. Golden evaluation `toolCall` expectations can only reference direct Tool resources.

**Impact:** You cannot write golden eval assertions like:
```json
// ❌ FAILS — OpenAPI operation is not a Tool resource
{ "expectation": { "toolCall": { "tool": "searchBranches" } } }
{ "expectation": { "toolCall": { "tool": "location.searchBranches" } } }
```

**Workaround:** Use `agentResponse` expectations to verify the *result* of an OpenAPI call:
```json
// ✅ WORKS — verify the response content instead
{
  "expectation": {
    "agentResponse": {
      "role": "location_services_agent",
      "chunks": [{ "text": "I found branches in Frankfurt" }]
    }
  }
}
```

See [evaluations-howto.md § G-01](./evaluations-howto.md#g-01-toolcall-cannot-reference-openapi-operations) for full details.

### 4.2 L-11: OpenAPI Operations Cannot Be Mocked in Scenario Evaluations

**Discovery:** `scenarioExpectations[].toolExpectation` with OpenAPI operations silently fails or is rejected with the same "Tool not found" error.

**Impact:** You cannot provide mock responses for OpenAPI tool calls in scenario evaluations. The scenario will always hit the live backend.

**Workaround:** Leave `scenarioExpectations: []` and put all verification criteria in the `task` text. Accept that scenario evals for OpenAPI-backed features are non-deterministic integration tests.

See [evaluations-howto.md § G-02](./evaluations-howto.md#g-02-scenario-tool-mocking-only-works-for-direct-tools) for full details.

### 4.3 Hybrid Pattern: Python Wrapper over OpenAPI Toolset

When you need **both** OpenAPI's platform capabilities **and** evaluation assertability (direct tool), use CES's built-in tool chaining capability.

**How it works:** Inside a Python function tool, you can call OpenAPI toolset operations using the `tools.<toolset>_<operationId>()` syntax. CES provides this bridge automatically. Even though Python tools can now make HTTP requests directly, this pattern remains the preferred hybrid when you want Python-side formatting/orchestration without giving up OpenAPI auth and context features.

```python
def search_and_format_branches(city: str = "") -> dict:
    """Search branches and format for voice output.
    
    Uses the OpenAPI toolset 'location' for the actual API call,
    then formats the result for the voice channel.
    """
    # CES provides tools.location_searchBranches() automatically
    # when the agent has the 'location' toolset attached
    raw = tools.location_searchBranches(city=city)
    
    # Format for voice output (deterministic logic)
    branches = raw.get("data", {}).get("branches", [])
    if not branches:
        return {"message": f"No branches found in {city}."}
    
    top3 = branches[:3]
    lines = [f"{b['name']} at {b['address']}" for b in top3]
    return {
        "message": f"Found {len(branches)} branches. Top 3: {'; '.join(lines)}",
        "count": len(branches)
    }
```

**Benefits:**
- Network call goes through OpenAPI toolset (platform handles auth, DNS, TLS)
- The wrapper is a direct tool → fully assertable in golden `toolCall` expectations
- Single tool call from the model's perspective (no multi-step tool chaining overhead)

**Trade-offs:**
- More moving parts (Python code + OpenAPI schema + toolset config)
- Python code must be maintained alongside the OpenAPI schema
- Debugging failures requires checking both layers

> ⚠️ **Note:** The `tools.<toolset>_<operationId>()` syntax is documented in the
> [developer-guide.md](../../docs/architecture/cx-agent-studio/developer-guide.md) § 7.1
> but has not been validated in this project. Test in a CES sandbox before relying on it.

### 4.4 Python Runtime Notes (Current Docs + Project Findings)

The current CES docs explicitly document proprietary API/database integration and external HTTP support for Python tools, including use of `ces_requests`. Earlier project experiments observed DNS failures with ad hoc networking code, so older assumptions about “no egress” should be treated as stale unless revalidated in the current runtime.

| Observation | Current status | Recommendation |
|------------|---------------|----------------|
| External HTTP from Python | ✅ Officially supported via `ces_requests` | Official baseline supports this directly; this guide prefers OpenAPI only when you need stronger platform-managed request controls |
| `None` parameter defaults | Project observed CES rejects `None` defaults | Use `str = ""` for all optional params until revalidated |
| `float`/`int`/`bool` params with `None` default | Project observed same validation failure | Declare params as `str`, parse in function body |
| Import surface | Official docs clearly describe CES runtime primitives; broader library availability should be sandbox-validated | Prefer documented CES primitives first; treat extra library usage as something to verify rather than assume |
| Direct ad hoc networking libraries | Earlier project test hit DNS error with `urllib` | Prefer the documented `ces_requests` helper over custom networking code |
| Package root layout | Project packaging constraint | Keep only CES package artifacts in the root |

### 4.5 Session Context Injection via `x-ces-session-context`

OpenAPI toolsets support automatic injection of session variables into API calls. This is a significant advantage over direct Python tools, which can read session context via `context` / `variables` but do not provide equivalent schema-driven request injection.

**Pattern:** Annotate OpenAPI parameters with `x-ces-session-context`:

```yaml
paths:
  /api/v1/branches:
    get:
      parameters:
        - name: x-session-id
          in: header
          x-ces-session-context: session_id
          schema:
            type: string
        - name: x-project-id  
          in: header
          x-ces-session-context: project_id
          schema:
            type: string
```

CES injects these values automatically — the model never predicts them, reducing hallucination risk for sensitive identifiers.

**Available context variables** (per [platform-reference.md](../../docs/architecture/cx-agent-studio/platform-reference.md) § 5.1):
- `session_id` — Current conversation session ID
- `project_id` — GCP project ID
- Custom session parameters defined in agent config

---

## 5. Testing Implications by Tool Type

### 5.1 Testing Strategy Matrix

| Tool Type | Golden `toolCall` | Golden `agentResponse` | Scenario Mock | Scenario Live | Java Unit Test |
|-----------|:-:|:-:|:-:|:-:|:-:|
| **OpenAPI Toolset** | ❌ | ✅ | ❌ | ✅ | ✅ |
| **Direct Python Tool** | ✅ | ✅ | ✅ | ✅ | N/A |
| **MCP Tool** | ❓ | ✅ | ❓ | ✅ | ✅ |

Legend: ✅ Supported | ❌ Not supported | ❓ Untested

### 5.2 Recommended Testing Approach by Tool Type

**OpenAPI Toolsets:**
1. **Structure testing** → Golden eval with `agentTransfer` + `agentResponse` expectations
2. **API correctness** → Java unit/integration tests with mocked services
3. **Language quality** → Scenario eval with criteria in `task` (hits live backend)

```json
// Golden eval: verify agent responds with branch data (not the tool call itself)
{
  "steps": [
    { "userInput": { "text": "Filialen in München" } },
    { "expectation": { "agentResponse": {
        "role": "location_services_agent",
        "chunks": [{ "text": "I found branches in München" }]
    }}}
  ]
}
```

**Direct Python Tools:**
1. **Tool invocation** → Golden eval with `toolCall` expectation (exact tool + params)
2. **Response quality** → Golden eval with `agentResponse`
3. **Mock responses** → Scenario eval with `mockToolResponse`

```json
// Golden eval: verify end_session is called with correct params
{
  "steps": [
    { "userInput": { "text": "Connect me to a human" } },
    { "expectation": { "agentResponse": {
        "role": "voice_banking_agent",
        "chunks": [{ "text": "I'll connect you with a live agent" }]
    }}},
    { "expectation": { "toolCall": {
        "tool": "end_session",
        "params": {}
    }}}
  ]
}
```

**MCP Tools:**
1. **Response quality** → Golden eval with `agentResponse` (safest)
2. **Live integration** → Scenario eval with criteria in `task`
3. **Tool correctness** → Test MCP server independently (outside CES)

### 5.3 When to Fall Back to Java Integration Tests

Use Java/backend tests instead of CES evaluations when:

| Situation | Why CES evals aren't enough |
|-----------|---------------------------|
| Need to verify exact API request parameters | CES evals can only check the response, not the request |
| Need to test error handling (4xx, 5xx, timeouts) | Scenario evals hit live backend — can't force errors |
| Need deterministic OpenAPI tool testing | OpenAPI ops can't be mocked in CES evals |
| Need to test pagination, rate limiting, edge cases | Requires controlled backend state |
| Testing auth token propagation | CES handles auth invisibly — can't assert token content |

---

## 6. Anti-Patterns

### ❌ Don't: Treat Python HTTP as a Full Replacement for OpenAPI Toolsets

```python
# ⚠️ This can work, but it gives up OpenAPI's auth/session/context benefits
resp = ces_requests.get("https://api.example.com/data")
```

Python tools can make HTTP requests and can be used for API/database integrations per the official docs. This guide still recommends OpenAPI toolsets for repository cases where first-class authentication options, schema-governed contracts, and `x-ces-session-context` injection are the deciding factors.

### ❌ Don't: Write `toolCall` Expectations for OpenAPI Operations

```json
// ❌ CES import will fail
{ "expectation": { "toolCall": { "tool": "searchBranches" } } }
```

Use `agentResponse` expectations to verify OpenAPI-backed behavior.

### ❌ Don't: Use `None` as Default for Python Tool Parameters

```python
# ❌ CES rejects None defaults
def search(city: str = None, limit: int = None) -> dict:
```

Use `str = ""` for all optional parameters:
```python
# ✅ Works in CES
def search(city: str = "", limit: str = "") -> dict:
```

### ❌ Don't: Chain Multiple Tool Calls When One Wrapper Will Do

```
# ❌ Bad: Model makes 3 sequential tool calls
User: "Find branches near me"
→ Model calls get_location()
→ Model calls search_branches(lat=..., lon=...)
→ Model calls format_results(branches=...)
```

```python
# ✅ Good: Single wrapper tool
def find_nearby_branches(latitude: str = "", longitude: str = "") -> dict:
    """Find and format nearby branches in one call."""
    # ... all logic in one function
```

See [developer-guide.md](../../docs/architecture/cx-agent-studio/developer-guide.md) § 7.2 for the official tool chaining guidance.

---

## 7. Summary: When to Use Each Tool Type

| | OpenAPI Toolset | Direct Python Tool | MCP Tool |
|---|---|---|---|
| **Choose when** | Contract-first or sensitive REST APIs | Local logic, eval assertability, or lightweight HTTP via `ces_requests` | Dynamic tool registries |
| **Avoid when** | Need `toolCall` eval assertions | Need OpenAPI-style auth/context injection or strong schema contracts | SSE transport required |
| **Auth** | Platform-managed | Manual in code | Same as OpenAPI |
| **Testing** | `agentResponse` + Java tests | Full CES eval support | `agentResponse` + server tests |
| **Complexity** | Low (schema + config) | Medium (Python code) | High (server lifecycle) |
| **This project uses** | ✅ `location` toolset | ✅ `end_session`, experimental `search_branches`/`get_branch` | ❌ Not used |
