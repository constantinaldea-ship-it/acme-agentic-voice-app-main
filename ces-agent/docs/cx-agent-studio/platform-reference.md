# CX Agent Studio Platform Reference

Author: Codex
Date: 2026-02-07
Status: Reference
Modified by Codex on 2026-02-07 to add deployment, security, and operations coverage.
Modified by  on 2026-02-07 to add GTP telephony deep-dive, API access details, and audit logging API catalog.

## 1) Artifact Model
A CX Agent Studio app export is a package of declarative artifacts:
- `app.json`: app-level runtime configuration.
- `environment.json`: environment overrides for endpoints and logging sinks.
- `global_instruction.txt`: global prompt context.
- `agents/*/*.json` and `agents/*/instruction.txt`: agent contracts and behavior directives.
- `tools/*/*.json` + Python handlers.
- `toolsets/*/*.json` + OpenAPI schema.
- `guardrails/*/*.json`: safety and policy definitions.
- `evaluations/*/*.json`: scenario/golden test assets.

## 2) App Configuration (`app.json`)
Sample reference: `sample/Sample_app_2026-02-07-162736/app.json`

Key fields:
| Field | Type | Purpose | Sample value |
|---|---|---|---|
| `displayName` | string | App name | `Sample app 2026-02-07-162736` |
| `rootAgent` | string | Entry agent | `cymbal_retail_agent` |
| `audioProcessingConfig` | object | TTS, inactivity timeout, ambient audio | `inactivityTimeout: 2s` |
| `loggingSettings` | object | Audio recording, BigQuery export, Cloud Logging | `bigqueryExportSettings.enabled: true` |
| `guardrails` | string[] | App-wide enabled guardrails | safety/prompt/content/policy guardrails |
| `modelSettings` | object | Default model | `gemini-3.0-flash-001` |
| `variableDeclarations` | object[] | Typed state declarations | `customer_profile`, `manager_approved` |
| `globalInstruction` | path | Global prompt text file | `global_instruction.txt` |
| `languageSettings` | object | default/supported language and multilingual mode | `en-US`, `fr-CA`, `es-ES` |
| `evaluationMetricsThresholds` | object | Golden evaluation thresholds | semantic/tool correctness thresholds |
| `toolExecutionMode` | enum | Tool concurrency mode | `PARALLEL` |

## 3) Agent Configuration (`agents/*/*.json`)
Sample references:
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/cymbal_retail_agent.json`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_upsell_agent/cymbal_upsell_agent.json`
- `sample/Sample_app_2026-02-07-162736/agents/out_of_scope_handling/out_of_scope_handling.json`

Common agent fields:
| Field | Required | Purpose |
|---|---|---|
| `displayName` | yes | Agent identifier |
| `description` | yes | Human-readable scope |
| `instruction` | yes | Path to instruction text |
| `modelSettings.model` | optional | Agent-level model override |
| `tools` | optional | Directly attached tools |
| `toolsets` | optional | Toolset + selected operations |
| `childAgents` | optional | Transfer targets |
| `beforeAgentCallbacks` / `beforeModelCallbacks` / `beforeToolCallbacks` | optional | Pre-execution interception |
| `afterAgentCallbacks` / `afterModelCallbacks` / `afterToolCallbacks` | optional | Post-execution interception |

## 4) Instruction Syntax
Instruction files use an XML-like structured style. Core sections observed in sample:
- `<role>`
- `<persona>`
- `<constraints>`
- `<taskflow>` with `<subtask>`, `<step>`, `<trigger>`, `<action>`
- `<examples>`

Transfer and tool invocation syntax used in the sample:
- Agent transfer: `{@AGENT: cymbal_upsell_agent}`
- Tool invocation: `{@TOOL: update_cart}`

Sample references:
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/instruction.txt`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_upsell_agent/instruction.txt`
- `sample/Sample_app_2026-02-07-162736/agents/out_of_scope_handling/instruction.txt`

## 5) Tool Integration
CX Agent Studio supports multiple tool forms. In this sample:
- Python function tool (`pythonFunction`): local deterministic action.
- OpenAPI toolset (`openApiToolset`): operations from schema become callable actions.
- Google search tool (`googleSearchTool`): constrained web lookup with context URLs.
- MCP tool (`mcpTool`): connects to MCP servers for dynamic tool discovery.

Tool JSON examples:
- Python: `sample/Sample_app_2026-02-07-162736/tools/update_cart/update_cart.json`
- Search: `sample/Sample_app_2026-02-07-162736/tools/lookup_plant_details/lookup_plant_details.json`
- OpenAPI toolset: `sample/Sample_app_2026-02-07-162736/toolsets/crm_service/crm_service.json`

OpenAPI schema example:
- `sample/Sample_app_2026-02-07-162736/toolsets/crm_service/open_api_toolset/open_api_schema.yaml`

Official behavior to account for:
- OpenAPI tool definitions map one callable tool per OpenAPI path operation.
- MCP authentication follows the same model used for OpenAPI tools.
- MCP transport requirement (updated 2026-02-12): CES supports only Streamable HTTP transport for MCP servers; SSE transport servers are not supported.
- OpenAPI tools support `x-ces-session-context` annotations to inject session context (project ID, session ID, custom variables) into API calls without model prediction. See `design-patterns.md` section 5.1 for full reference.

Deterministic chaining pattern from sample tools:
- `greeting` calls `tools.crm_service_get_cart_information`.
- `get_product_recommendations` calls both `lookup_plant_details` and `crm_service_get_product_recommendations` in fixed order.

## 6) Variables and State
Declarations are centralized in `app.json` under `variableDeclarations` with type schema and default values.

Runtime access patterns in sample:
- Prompt interpolation: `CURRENT CUSTOMER PROFILE: {customer_profile}` in `global_instruction.txt`.
- Python callback/tool access: `callback_context.variables[...]`, `context.variables[...]`, and `get_variable("telephony-caller-id")`.

Practical variable scopes observed:
- Global/session business context (`customer_profile`).
- Runtime control flags (`request_image_tool_called`, `no_input_counter`).
- Workflow flags (`manager_approved`).
- Channel metadata (`telephony-caller-id`, `uui-headers`).

## 7) Guardrails
Guardrails in sample demonstrate four policy families:
- Model safety (`modelSafety`): category thresholds.
- Prompt security (`llmPromptSecurity`): prompt-injection handling.
- Content filter (`contentFilter`): banned words with match strategy.
- LLM policy (`llmPolicy`): policy prompt evaluating agent response context.

Action modes observed:
- `respondImmediately`: static pre-authored reply.
- `generativeAnswer`: generated safe fallback text.

Sample references:
- `sample/Sample_app_2026-02-07-162736/guardrails/Safety_Guardrail_1757021079744/Safety_Guardrail_1757021079744.json`
- `sample/Sample_app_2026-02-07-162736/guardrails/Prompt_Guardrail_1757021081696/Prompt_Guardrail_1757021081696.json`
- `sample/Sample_app_2026-02-07-162736/guardrails/bad_words/bad_words.json`
- `sample/Sample_app_2026-02-07-162736/guardrails/French_Fries_Policy/French_Fries_Policy.json`

## 8) Callback Lifecycle and Contracts
Official callback lifecycle includes before/after hooks for agent, model, and tool phases.

Observed signatures from sample Python files:
- `before_model_callback(callback_context, llm_request) -> Optional[LlmResponse]`
- `after_model_callback(callback_context, llm_response) -> Optional[LlmResponse]`
- `before_tool_callback(tool, input, callback_context) -> Optional[dict]`
- `after_tool_callback(tool, input, callback_context, tool_response) -> Optional[dict]`
- `after_agent_callback(callback_context) -> Optional[Content]`

Behavioral semantics:
- Return `None` to allow normal pipeline execution.
- Return a crafted response or tool payload to override or short-circuit behavior.
- Use callbacks for policy checks, deterministic correction, counters, and state mutation.

Sample callback references:
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/before_model_callbacks/before_model_callbacks_01/python_code.py`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/after_model_callbacks/after_model_callbacks_01/python_code.py`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/after_tool_callbacks/after_tool_callbacks_01/python_code.py`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/after_agent_callbacks/after_agent_callbacks_01/python_code.py`
- `sample/Sample_app_2026-02-07-162736/agents/cymbal_upsell_agent/before_tool_callbacks/before_tool_callbacks_01/python_code.py`

## 9) Agent-to-Agent Handoffs
Handoffs are explicit and deterministic using `{@AGENT: <name>}` syntax.

Documented behavior from official handoff rules:
- Parent can transfer to child.
- Child can transfer back to parent.
- Sibling transfers route through parent.
- No implicit auto-handoffs.

In sample:
- Retail agent transfers to upsell or out-of-scope agents.
- Out-of-scope agent returns control to retail agent.

## 10) Evaluation Model
Two evaluation structures are used in sample:
- Golden transcript tests (`golden.turns[].steps[]`) with expectations for responses, tool calls, tool responses, and transfers.
- Scenario tests (`scenario.task`, `scenarioExpectations`, `mockToolResponse`, `maxTurns`).

Sample evaluation references:
- `sample/Sample_app_2026-02-07-162736/evaluations/Prompt_Injection_-_Social_Engineering_Method/Prompt_Injection_-_Social_Engineering_Method.json`
- `sample/Sample_app_2026-02-07-162736/evaluations/Update_Cart_-_Rose_Bushes/Update_Cart_-_Rose_Bushes.json`
- `sample/Sample_app_2026-02-07-162736/evaluations/Product_Recommendation_-_Fiddle_Leaf_Fig/Product_Recommendation_-_Fiddle_Leaf_Fig.json`

## 11) Deployment Channels and Runtime Access
Deployment flow:
- Deploy from a selected app `version`.
- Deployment configuration varies by channel and integration endpoint.

Supported channel surfaces documented in CX Agent Studio:
| Channel | Primary usage | Key requirement |
|---|---|---|
| API access | Backend/server mediated chat or voice orchestration | Session lifecycle via API and token flow (`generateChatToken` + `runSession`) |
| Web widget | Browser-based chat embed | Configure authorized JS origins, auth mode, and optional custom domain |
| Google telephony platform | Google-first telephony stack | DID + termination URI on provider side |
| Twilio | External telephony bridge | Twilio Media Streams adapter integration and webhook configuration |
| Five9 | Contact center integration | Bring your own SIP trunk and SIP domain configuration |
| Google Cloud CCAAS | Google contact center routing | Inbound app endpoint setup and complete channel integration flow |

### 11.1 Google Telephony Platform (GTP) Deep Dive

GTP provides an out-of-the-box phone number for voice agent applications. This is **critical for voice banking**.

#### Phone Number Setup
1. Open the Gemini Enterprise for CX console and select your agent application.
2. Click Deploy → Connect to a platform.
3. Enter a channel name, select Google Telephony Platform (GTP), select a version.
4. (Optional) Configure channel-specific behavior: response length, DTMF input, barge-in.
5. Click Create channel. A phone number and deployment ID are provided.
6. Call the number to test.

#### Call Termination
- Add the `end_session` system tool to the agent (and all sub-agents).
- Add instruction: `End the call by executing the end_session tool with arguments reason="customer_query_ended".`

#### Human Agent Transfer

**Direct phone transfer:**
- Add `end_session` system tool to all agents.
- Create variable `ESCALATION_MESSAGE` (type: Text) — optional pre-transfer message.
- Create variable `PHONE_GATEWAY_TRANSFER` (type: Custom schema):
  ```json
  {
    "phone_number": "+PHONE_NUMBER"
  }
  ```
- Instruction: `Escalate the call to a human agent by executing the end_session tool with session_escalated=true`

**Partner-supported transfer (SIP REFER/INVITE):**
- Create variable `LIVE_AGENT_HANDOFF` (type: Custom schema):
  ```json
  {
    "PARTNER_NAME": {
      "type": "action",
      "action": "deflection",
      "deflection_type": "sip",
      "sip_uri": "SIP_URI",
      "sip_refer": true,
      "sip_parameters": {
        "x-header": "value"
      }
    }
  }
  ```

#### SIP Signaling and Data Passing

**Telephony variables** (created automatically when available):
| Variable | Type | Source |
|---|---|---|
| `telephony-caller-id` | Text | Caller's phone number (ANI) |
| `uui-headers` | List | UUI SIP header data (hex-decoded key=value pairs) |
| `x-headers` | Custom schema | SIP x-headers (prefix removed) |
| `ESCALATION_MESSAGE` | Text | Pre-transfer spoken message |
| `PHONE_GATEWAY_TRANSFER` | Custom schema | Transfer target phone number and trunk config |
| `LIVE_AGENT_HANDOFF` | Custom schema | Partner handoff payload |

**UUI SIP Header encoding:**
- Encode key-value pairs as hex: `key1=value1;key2=value2` → `6B6579313D76616C7565313B6B6579323D76616C756532`
- Send as: `User-to-User: <hex>;encoding=hex;purpose=Goog-Session-Param`

**SIP operations:**
| Operation | Trigger | Use case |
|---|---|---|
| SIP BYE | `end_session` with `LIVE_AGENT_HANDOFF` | End call and pass headers |
| SIP REFER | `LIVE_AGENT_HANDOFF` with `"sip-refer": true` + `PHONE_GATEWAY_TRANSFER` | Transfer call, agent drops off |
| SIP INVITE | `PHONE_GATEWAY_TRANSFER` only (no sip-refer) | Transfer call, agent stays (for Agent Assist) |

**Example instruction for SIP REFER with headers:**
```
If the user says that they would like to speak to an agent, escalate the
call to the agent by executing the end_session tool with
session_escalated=true and params={"ESCALATION_MESSAGE": "I am
transferring you to an agent", "PHONE_GATEWAY_TRANSFER":
{"phone_number": "+19496855555", "use_originating_trunk": true},
"LIVE_AGENT_HANDOFF":{"sip-refer": true, "x-headers": {x-headers},
"uui-headers": [{uui-headers[0]}] }}.
```

### 11.2 API Access Deep Dive

#### Channel Setup
1. Click Deploy → New channel → Set up API access.
2. Provide channel name, select app version.
3. Optionally override agent application settings for this channel.
4. Click Create channel. A deployment ID and sample `curl` command are provided.

#### Required IDs
```
projects/PROJECT_ID/locations/REGION_ID/apps/APPLICATION_ID/deployments/DEPLOYMENT_ID
```
- `PROJECT_ID`: your GCP project ID
- `REGION_ID`: your region (e.g., `us`, `eu`)
- `APPLICATION_ID`: your app ID
- `DEPLOYMENT_ID`: your deployment channel ID
- `SESSION_ID`: dynamic, generated per conversation (regex: `[a-zA-Z0-9][a-zA-Z0-9-_]{4,62}`)

#### `runSession` (Single Turn)

Request:
```json
{
  "config": {
    "session": "projects/PROJECT_ID/locations/REGION_ID/apps/APPLICATION_ID/sessions/SESSION_ID",
    "deployment": "projects/PROJECT_ID/locations/REGION_ID/apps/APPLICATION_ID/deployments/DEPLOYMENT_ID"
  },
  "inputs": [
    {
      "text": "hi"
    }
  ]
}
```

Response:
```json
{
  "outputs": [
    {
      "text": "Hello there!",
      "turnCompleted": true,
      "turnIndex": 1,
      "diagnosticInfo": {...}
    }
  ]
}
```

#### `BidiRunSession` (Bidirectional Streaming)

For real-time audio streaming:
- WebSocket connection to `wss://ces.googleapis.com/ws/google.cloud.ces.v1.SessionService/BidiRunSession/locations/<LOCATION>`
- Config message specifies `inputAudioConfig` and `outputAudioConfig` (encoding, sample rate).
- Client sends `realtimeInput.audio` (base64 LINEAR16 chunks).
- Server sends `SessionOutput` (text + audio), `RecognitionResult`, `InterruptionSignal`, `EndSession`.
- Supported audio: LINEAR16 at 8000/16000/24000 Hz.

#### Regional Endpoints
- Global: `ces.googleapis.com` (requires `x-goog-request-params: location=locations/us` header)
- US regional: `ces.us.rep.googleapis.com` (no extra header needed)
- EU regional: `ces.eu.rep.googleapis.com` (no extra header needed)

#### Authentication
- Development: `gcloud auth print-access-token`
- Production: service account credentials. See [Authenticate to CX Agent Studio](https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/authentication).

## 12) Security and Compliance Controls

### Audit Logging
- Service name: `ces.googleapis.com`
- Filter: `protoPayload.serviceName="ces.googleapis.com"`

**Permission types and audit log categories:**
| Permission Type | Audit Log Type | Methods |
|---|---|---|
| `ADMIN_WRITE` | Admin Activity | `CreateApp` (LRO), `DeleteApp` (LRO), `ImportApp` (LRO), `UpdateApp` |
| `ADMIN_READ` | Data Access | `ExportApp` (LRO), `GetApp`, `ListApps` |
| `DATA_WRITE` | Data Access | `CreateAgent`, `CreateAppVersion`, `CreateDeployment`, `CreateExample`, `CreateGuardrail`, `CreateTool`, `CreateToolset`, `DeleteAgent`, `DeleteAppVersion`, `DeleteConversation`, `DeleteDeployment`, `DeleteExample`, `DeleteGuardrail`, `DeleteTool`, `DeleteToolset`, `RestoreAppVersion` (LRO), `UpdateAgent`, `UpdateDeployment`, `UpdateExample`, `UpdateGuardrail`, `UpdateTool`, `UpdateToolset` |
| `DATA_READ` | Data Access | `GetAgent`, `GetAppVersion`, `GetChangelog`, `GetConversation`, `GetDeployment`, `GetExample`, `GetGuardrail`, `GetTool`, `GetToolset`, `ListAgents`, `ListAppVersions`, `ListChangelogs`, `ListConversations`, `ListDeployments`, `ListExamples`, `ListGuardrails`, `ListTools`, `ListToolsets` |

**API service interfaces:**
| Service Interface | Key Methods |
|---|---|
| `google.cloud.ces.v1.AgentService` | All CRUD operations for apps, agents, tools, toolsets, guardrails, examples, deployments, versions, changelogs, conversations |
| `google.cloud.ces.v1.SessionService` | `RunSession` (DATA_WRITE), `BidiRunSession` (DATA_WRITE, streaming) |
| `google.cloud.ces.v1.ToolService` | `ExecuteTool` (DATA_WRITE), `RetrieveToolSchema` (DATA_READ), `RetrieveTools` (DATA_READ) |

**Example audit log filters:**
```
# All admin activity
protoPayload.serviceName="ces.googleapis.com"
protoPayload.methodName=~"CreateApp|DeleteApp|UpdateApp|ImportApp"

# All session activity (runtime calls)
protoPayload.methodName=~"RunSession|BidiRunSession"

# Tool executions
protoPayload.methodName="google.cloud.ces.v1.ToolService.ExecuteTool"
```

**Notes:**
- Methods marked (LRO) generate two audit log entries: one at start, one at completion.
- `BidiRunSession` is a streaming RPC and generates streaming audit logs.
- Data Access audit logs must be explicitly enabled (Admin Activity logs are always on).

### CMEK (Customer-Managed Encryption Keys)
- CX Agent Studio supports CMEK for data at rest via Cloud KMS.
- Key ring location must match app data location.
- One key per project/location is supported.
- Organization policy can enforce CMEK usage.
- Key disable/revoke can block runtime access to encrypted resources.

**CMEK limitations:**
- Key rotation is supported but data re-encryption is not.
- Existing resources in non-CMEK projects cannot be retroactively CMEK-integrated (must export and restore in a new project).
- Key management uses the Conversational Insights API (`ccai.googleapis.com`).

### VPC Service Controls
- CX Agent Studio supports VPC Service Controls for network-level access restrictions.
- Configure service perimeters to restrict access to `ces.googleapis.com`.

## 13) Operations and Lifecycle
Conversation history:
- Search and inspect sessions by date range and session ID.
- Inspect transcript events and tool calls for debugging and QA.

Versioning and change history:
- Deploy flow and REST resources expose versioned artifacts and change logs.
- Use these resources as release evidence when alias docs pages are unstable.

Flow mode:
- Flow-based agents are available as a complementary design mode and are not equivalent to pure instruction/callback orchestration.

## 14) Export Format Notes
Export action emits a package containing app, agents, tools, toolsets, guardrails, evaluations, and environment assets. The sample folder is a representative export layout.

Representative structure:
- `app.json`
- `environment.json`
- `global_instruction.txt`
- `agents/`
- `tools/`
- `toolsets/`
- `guardrails/`
- `evaluations/`

## 15) References
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/variable
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/guardrail
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/handoff
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation-batch-upload
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/export
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/api-access
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/web-widget
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/google-telephony-platform
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/twilio
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/five9
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/ccaas
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/cmek
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/audit-logging
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/conversation-history
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/flow
