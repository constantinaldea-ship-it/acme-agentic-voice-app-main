# MCP vs OpenAPI in CX Agent Studio

This note is specific to **CX Agent Studio / Customer Engagement Suite (CES)**.

The practical CES choice is usually **OpenAPI vs MCP**, not “stdio vs SSE”. Under the current official CES docs, MCP support is narrower than generic MCP ecosystem discussions suggest.

Official baseline:

- Google supports OpenAPI tools, Python tools, and MCP tools in CES
- Google limits MCP to Streamable HTTP transport in CES

Repository recommendations below are labeled as recommendations for this repo, not as platform restrictions.

## Current CES reality

- **OpenAPI tools** let CES call one OpenAPI operation per tool and support runtime context injection via `x-ces-session-context`. See the official [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api) docs.
- **MCP tools** let CES connect to a remote MCP server, but **only StreamableHttpTransport-based servers are supported**. The official docs explicitly state that **SSE transport servers are not supported**. See [MCP tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp).
- **Python tools** are a third CES option. Google documents them for custom logic, proprietary API access, proprietary database access, tool chaining, and external HTTP requests. This repository still tends to use them as thin wrappers or code-first integration helpers when that is simpler than OpenAPI. See [Python code tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/python) and the [Python runtime reference](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python).

## What OpenAPI gives you

OpenAPI is usually the better CES fit when you need:

- **Deterministic session/context injection** using `x-ces-session-context` for values like `$context.session_id` and `$context.variables.*`.
- **Managed authentication options** such as service agent ID token, service account auth, OAuth, or API key.
- **Explicit request/response contracts** that are easy to review in architecture, security, and compliance processes.
- **Sensitive or regulated integrations** where the tool surface should be narrowly defined and auditable.

These capabilities are documented on the official [OpenAPI tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api) page.

## What MCP gives you

MCP is the better CES fit when you already have, or intentionally want, an MCP server:

- **Existing MCP ecosystem investment** that you do not want to remodel as one-operation-per-tool OpenAPI definitions.
- **Server-side tool aggregation/discovery** where the MCP server owns the tool surface.
- **Remote hosted integrations** that can expose a CES-compatible Streamable HTTP endpoint.

Official CES guidance for MCP:

- Only **Streamable HTTP** is supported.
- MCP tools use the **same authentication options as OpenAPI tools**.
- Google recommends testing the MCP server independently before attaching it to CES, and notes that StreamableHttpTransport servers typically end in `/mcp` or `/mcp/`.

Source: [MCP tools](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp).

## Where stdio and SSE fit

Generic MCP discussions often compare **stdio**, **SSE**, and **Streamable HTTP**. That broader comparison is useful for MCP in general, but it is **not** the operative CES constraint.

For **CX Agent Studio specifically**:

- **stdio** is not a CES tool transport option.
- **SSE-based MCP servers** are not supported by CES.
- **Streamable HTTP** is the CES-compatible MCP transport.

So if you are making a CES architecture decision, treat “stdio vs SSE” as out-of-scope background context, not as the current CES decision point.

## Where Python tools fit

Official CES best practices explicitly recommend two Python-tool patterns that matter here:

- **Wrap APIs with Python tools** when you want to hide irrelevant schema fields or control what the model sees.
- **Chain multiple tool calls inside a single wrapper tool** rather than relying on the model to orchestrate multiple deterministic steps.

Source: [Best practices and patterns](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices).

That means OpenAPI should **not** be justified with the claim that “Python tools cannot do HTTP”. They can. The better justification for OpenAPI is its auth, contract, and context-injection model.

## Recommendation for this repo

For the voice-banking use cases in this repository:

1. **Default to OpenAPI** for regulated banking operations and any flow that depends on deterministic context injection or tightly reviewed auth/configuration.
2. **Use MCP** when there is a strong reason to expose an existing or intentionally designed remote MCP server, and only via **Streamable HTTP**.
3. **Use Python tools** when a code-first CES integration is the clearest fit, including proprietary API/database access, thin wrappers, context shaping, or deterministic orchestration.

This aligns with the local guidance in:

- `ces-agent/docs/tool-selection-guide.md`
- `ces-agent/docs/adr/ADR-CES-001-rest-api-vs-mcp-server.md`
- `ces-agent/docs/adr/ADR-CES-002-mcp-server-topology.md`
- `ces-agent/docs/adr/ADR-CES-004-backend-language-choice.md`
