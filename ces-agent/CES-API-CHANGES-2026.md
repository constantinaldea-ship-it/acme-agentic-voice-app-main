# CES / Dialogflow CX API Changes — Research Summary (2026-01)

> **Purpose:** Document the latest CES API changes that affect how we build and deploy the LocationServicesAgent.  
> **Researched:** 2026-01-17  
> **Sources:** cloud.google.com/dialogflow/cx/docs (agent concept, playbook concept, tools, webhooks, JSON export format)

---

## 1. Playbooks — New Primary Building Block

CES has introduced **Playbooks** as the primary building block for generative AI agents, alongside the existing Flows/Pages/Intents model.

### 1.1 Two Playbook Types

| Type | Purpose | Parameter Model | Best For |
|------|---------|----------------|----------|
| **Task Playbook** | Goal-oriented, parent/child hierarchy | Input/Output parameters (scoped to playbook) | Modular subtasks, tool calling |
| **Routine Playbook** *(NEW)* | Sequential stages, ordered execution | Session parameters (`$session.params`) | Multi-step workflows, sequential conversations |

### 1.2 Playbook Anatomy

Each playbook has:
- **Goal:** Natural language description of what the playbook achieves
- **Instructions:** Step-by-step directives the LLM follows (use `${}` for parameter refs)
- **Examples:** Few-shot examples showing expected input → actions → output
- **Tools:** OpenAPI tools, function tools, or data stores the playbook can invoke
- **Parameters:** Input/output (task) or session-based (routine)

### 1.3 Routine Playbook — Stage Architecture

Routine playbooks define sequential **stages** where each stage:
- Has its own instructions and tool access
- Can transition to other playbooks or flows
- Shares state via `$session.params`
- Supports conditional routing based on session state

### 1.4 Model Support

| Model | Status | Notes |
|-------|--------|-------|
| `gemini-2.5-flash` | GA | Recommended for production |
| `gemini-2.5-flash-lite` | GA | Cost-optimized |
| `gemini-2.0-flash-001` | GA | Previous generation |
| `gemini-2.0-flash-lite-001` | GA | Previous gen lite |

---

## 2. OpenAPI Tools — Direct API Consumption

**Key capability for LocationServicesAgent:** CES playbooks can directly consume an OpenAPI spec as a tool. The LLM uses the operation descriptions to decide when and how to call the API.

### 2.1 How It Works

1. Upload an OpenAPI 3.0 spec (JSON or YAML)
2. CES parses the spec and exposes operations as "tools"
3. The LLM reads operation summaries, descriptions, and parameter schemas
4. At runtime, the LLM constructs API calls based on user intent

### 2.2 Authentication Options for Tools

| Auth Method | Configuration | Notes |
|-------------|---------------|-------|
| Service Agent | Default — uses CX agent's service account | Simplest for GCP-internal APIs |
| Service Account | Explicit SA + bearer token | For cross-project APIs |
| API Key | Header or query parameter | For simple API key auth |
| OAuth 2.0 | Client credentials flow | For production APIs |
| Bearer Token | `$session.params.<token-name>` | **Dynamic per-session auth** |
| mTLS | Mutual TLS with client certificate | High-security environments |

### 2.3 OpenAPI Extensions for CES

| Extension | Purpose | Example |
|-----------|---------|---------|
| `x-agent-input-parameter` | Bind session param to API input | `x-agent-input-parameter: userToken` |
| `@dialogflow/sessionId` | Inject DFCX session ID into API calls | Schema `$ref: "@dialogflow/sessionId"` |

### 2.4 Implications for Our Architecture

Our `openapi-specs/location-services.json` can be registered directly as an OpenAPI Tool:
- **Search branches** → `GET /api/v1/branches` (all query params auto-discovered)
- **Get branch details** → `GET /api/v1/branches/{branchId}` (path param auto-discovered)
- The LLM reads the rich OpenAPI descriptions to construct appropriate API calls
- Bearer auth token can be injected from `$session.params.userToken`

---

## 3. Webhook Changes

### 3.1 Standard vs Flexible Webhooks

| Type | Request Format | Response Format | Use Case |
|------|----------------|-----------------|----------|
| **Standard** | CX-defined JSON (intent, params, etc.) | CX-defined JSON (messages, params, etc.) | Traditional DFCX webhooks |
| **Flexible** | Custom URL + body template with `$session.params` | Custom response field mapping | Modern REST API integration |

### 3.2 Auth Changes (Post Aug 15, 2025)

> **CRITICAL:** After August 15, 2025, exported agents must use **Secret Manager** for all credentials. Environment variables and inline secrets are no longer supported for exported/imported agents.

### 3.3 Environment-Specific Webhooks

CES now supports per-environment webhook URL overrides, which we already model in our `agent.yaml` environments section.

---

## 4. JSON Package Export Format

The official CES directory structure for agent import/export:

```
agent.json                          # Agent root config
entityTypes/
  <EntityType>.json                 # Entity type definition
flows/
  <Flow>.json                       # Flow definition
  <Flow>/
    pages/
      <Page>.json                   # Page definitions
    transitionRouteGroups/
      <RouteGroup>.json             # Shared route groups
intents/
  <Intent>.json                     # Intent definition
  <Intent>/
    trainingPhrases/
      <LanguageCode>.json           # Training phrases per locale
webhooks/
  <Webhook>.json                    # Webhook definitions
```

**Key notes:**
- Files use **display names** as filenames, not resource IDs
- Training phrases are split by language code
- Import/export via CES Console or REST API

---

## 5. Impact on Our Custom YAML DSL

Our `ces-agent/` uses a custom YAML DSL that is deployed via `deploy.sh` → `deploy_flows.py`. This DSL is **not** the official CES JSON format but a developer-friendly abstraction that gets translated during deployment.

### 5.1 What We Keep

- YAML DSL for human readability and version control
- Custom deployment scripts for translation to CES API calls
- Multi-file structure (agents/, flows/, intents/, entities/, webhooks/)

### 5.2 What We Update

- Add **Playbook-aware** configuration in agent YAML files
- Reference **OpenAPI Tools** from our OpenAPI specs directory
- Adopt **Bearer Token auth** pattern for session-based API auth
- Use **Secret Manager** references for production credentials
- Add location-services-specific intent routing

---

## 6. Recommendations for LocationServicesAgent

| Aspect | Recommendation | Rationale |
|--------|---------------|-----------|
| **Agent Type** | Specialized sub-agent (same pattern as personal-finance) | Consistent with existing multi-agent architecture |
| **API Integration** | OpenAPI Tool referencing `location-services.json` | Modern CES pattern, LLM auto-discovers operations |
| **Auth** | Bearer token via `$session.params.userToken` | Dynamic per-session auth consistent with existing pattern |
| **No-auth Operations** | Branch search doesn't require auth in our API | Can work without consent for basic branch info |
| **Model** | `gemini-2.5-flash` | GA, best quality/speed balance |
| **Playbook Note** | Our YAML DSL doesn't directly map to CES Playbooks | Playbook support can be added to deploy scripts later |

---

*Document created: 2026-01-17 by *
