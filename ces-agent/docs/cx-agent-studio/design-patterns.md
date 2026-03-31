# CX Agent Studio Design Patterns

Author: 
Date: 2026-02-07
Status: Reference — Curated pattern catalog with code examples
Primary sources:
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python

## Purpose

This document catalogs **current CX Agent Studio design patterns** from Google's live documentation, with concrete Python callback code examples. These patterns are production-tested building blocks for agent applications.

During verification on 2026-03-13, the previously cited `/ps/design-pattern` URL returned HTTP 404. The material below is therefore mapped from the live `best-practices`, `callback`, and Python runtime/tool documentation rather than from a dedicated design-pattern page.

Each pattern includes:
- The problem it solves.
- The callback hook or mechanism used.
- Complete Python code ready to adapt.
- Banking use case applicability.

---

## 1) Session Handling Patterns

### 1.1 Deterministic Greeting on Session Connection

**Problem:** You want a consistent greeting without model variation, saving tokens and reducing latency.

**Hook:** `before_model_callback`

**Pattern:** Intercept the session-start event and return a static response.

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in callback_context.get_last_user_input():
    # Or other events or texts
    if part.text == "<event>session start</event>":
      return LlmResponse.from_parts(parts=[
          Part.from_text(text="Hello how can I help you today?")
      ])
  return None
```

**Banking adaptation:**
```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in callback_context.get_last_user_input():
    if part.text == "<event>session start</event>":
      return LlmResponse.from_parts(parts=[
          Part.from_text(
              text="Welcome to Acme Bank Premium Banking. "
                   "How can I assist you today?"
          )
      ])
  return None
```

**Key insight:** This avoids a model call entirely on the first turn. Use this for brand-consistent IVR greetings.

---

### 1.2 Verify and Enforce Mandatory Content

**Problem:** The model should include specific mandatory content (like a legal disclaimer) but may forget it. You want model-first with deterministic fallback.

**Hook:** `after_model_callback`

**Required variable:** `first_turn` (boolean, default `True`)

```python
DISCLAIMER = "THIS CONVERSATION MAY BE RECORDED FOR LEGAL PURPOSES."

def after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse
) -> Optional[LlmResponse]:
  if callback_context.variables.get("first_turn"):
    callback_context.variables["first_turn"] = False

    # Check if the agent's response already contains the disclaimer.
    # The agent might have produced it based on instructions.
    for part in callback_context.get_last_agent_output():
      if part.text and DISCLAIMER in part.text:
        return None

    # If the agent failed to produce the disclaimer, force it.
    return LlmResponse.from_parts(parts=[
        Part.from_text(DISCLAIMER),
        *llm_response.content.parts
    ])

  return None
```

**Banking adaptation:** Use for regulatory disclaimers, consent notices, or recording notifications required by banking compliance.

**Key insight:** The callback checks model output first (returns `None` if content is already present), then injects it only if missing. This is a "trust but verify" pattern.

---

### 1.3 Call Custom Tool on Session End

**Problem:** You need cleanup actions (API calls, logging, metadata export) before the session terminates.

**Hook:** `after_model_callback`

**Pattern:** Intercept the `end_session` tool call and insert a cleanup tool call before it.

```python
def after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse
) -> Optional[LlmResponse]:
  for index, part in enumerate(llm_response.content.parts):
    if part.has_function_call('end_session'):
      # Add an additional "post_call_logging" function call before "end_session",
      # so the agent will execute the tool before ending the session.
      tool_call = Part.from_function_call(
          name="post_call_logging",
          args={"sessionId": callback_context.session_id}
      )
      return LlmResponse.from_parts(
          parts=llm_response.content.parts[:index]
                + [tool_call]
                + llm_response.content.parts[index:]
      )
  return None
```

**Banking adaptation:** Use for audit trail finalization, session summary export, or compliance event logging before call termination.

**Key insight:** The tool call is inserted *before* `end_session` in the parts list, so it executes first. The original `end_session` call is preserved.

---

## 2) Client-Side Integration Patterns

### 2.1 Return Custom Payload for Client-Side Rendering

**Problem:** You want to send structured data (chip lists, card layouts, action buttons) to the client alongside the text response.

**Hook:** `after_model_callback`

```python
import json

def after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse
) -> Optional[LlmResponse]:
  prefix = 'Available options are:'
  payload = {}
  for part in llm_response.content.parts:
    if part.text is not None and part.text.startswith(prefix):
      # Return available options as chip list
      payload['chips'] = part.text[len(prefix):].split(',')
      break

  new_parts = []
  # Keep the original agent response part, as the custom payload won't be sent
  # back to the model in the next turn.
  new_parts.extend(llm_response.content.parts)
  new_parts.append(Part.from_json(data=json.dumps(payload)))
  return LlmResponse.from_parts(parts=new_parts)
```

**Banking adaptation:** Return account selection chips, transaction confirmation cards, or balance summary widgets alongside spoken responses.

**Key insight:** Custom JSON payloads added via `Part.from_json()` are delivered to the client but **not** fed back into the model's conversation history.

---

### 2.2 Displaying Markdown and HTML

**Problem:** Your chat interface supports rich content and you want the agent to render formatted responses.

**Pattern:** Use structured instructions with a dedicated tool for content generation. This is an instruction-driven pattern, not a callback pattern.

**Instruction structure:**
```xml
<role>
    You are a "Markdown Display Assistant," an AI agent designed to demonstrate
    various rich content formatting options.
</role>
<constraints>
    1. Scope Limitation: Only handle requests related to displaying markdown content.
    2. Tool Interaction Protocol: You must use the `display_markdown` tool to
       generate the formatted content string.
    3. Direct Output: Your final response must be the raw markdown string
       returned by the tool. Do not add conversational text around it.
</constraints>
<taskflow>
    <subtask name="Generate and Display Markdown">
        <step name="Parse Request and Call Tool">
            <trigger>User requests rich content.</trigger>
            <action>Identify content types and call `display_markdown` tool.</action>
        </step>
        <step name="Output Tool Response">
            <trigger>Tool returns successful response.</trigger>
            <action>Extract markdown_string and present directly.</action>
        </step>
    </subtask>
</taskflow>
```

**Banking adaptation:** Use for rendering account statements, transaction tables, or payment confirmation receipts in web widget deployments.

---

## 3) Voice and Audio Channel Controls

> **Critical for Voice Banking:** These patterns directly apply to telephony and voice-first deployments.

### Audio Encoding Support
- Linear16, mulaw, and alaw audio encodings are supported.
- If using a Cloud Storage bucket from a different project, the CES Service Account (`service-<PROJECT-NUMBER>@gcp-sa-ces.iam.gserviceaccount.com`) must have `storage.objects.get` permission on the bucket.

### 3.1 Play Brand-Specific Pre-Recorded Audio

**Problem:** Play a branded audio file (jingle, welcome message) at session start before any model interaction.

**Hook:** `before_model_callback`

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in callback_context.get_last_user_input():
    if part.text == "<event>session start</event>":
      return LlmResponse.from_parts(parts=[
          Part.from_json(
              data='{"audioUri": "gs://path/to/audio/file", "interruptable": false}'
          )
      ])
  return None
```

**Parameters:**
- `audioUri`: GCS path to the audio file.
- `interruptable`: Whether the user can interrupt the playback. Set to `false` for mandatory brand greetings or legal disclaimers.

**Banking adaptation:** Play the Acme Bank brand jingle or "Your call may be recorded" audio before agent interaction begins.

---

### 3.2 Play Hold Music During Slow Tool Execution (Blocking)

**Problem:** A long-running tool (account validation, fraud check) blocks the conversation. You want to play music while the user waits, and the music stops automatically when the tool completes. The user **cannot** interact during playback.

**Hook:** `after_model_callback`

```python
def after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse
) -> Optional[LlmResponse]:
  for index, part in enumerate(llm_response.content.parts):
    if part.has_function_call("slow_tool"):
      play_music = Part.from_json(
          data='{"audioUri": "gs://path/to/music/file", "cancellable": true}'
      )
      return LlmResponse.from_parts(
          parts=llm_response.content.parts[:index] +
          [play_music] + llm_response.content.parts[index:]
      )
  return None
```

**Parameters:**
- `cancellable`: Music stops when a new agent response is generated (tool completes).

**Banking adaptation:** Play hold music during AcmeLegi legitimation verification or account balance aggregation across multiple accounts.

---

### 3.3 Play Hold Music During Asynchronous Tool Execution (Non-Blocking)

**Problem:** An async tool runs in the background. Music plays but the user **can** interrupt it at any time to continue engaging with the agent.

**Hook:** `before_model_callback`

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in llm_request.contents[-1].parts:
    if part.has_function_response("async_tool"):
      text = Part.from_text(
          text="I'm submitting your order, it may take a while."
      )
      music = Part.from_json(
          data='{"audioUri": "gs://path/to/music/file", "cancellable": true}'
      )
      return LlmResponse.from_parts(parts=[text, music])
  return None
```

**Banking adaptation:** "I'm processing your transfer, this may take a moment." + hold music while fund transfer API processes.

---

### 3.4 Disallow User Barge-In for Important Responses

**Problem:** The agent reads critical information (legal disclaimers, transaction confirmations) that must not be interrupted.

**Hook:** `before_model_callback` (deterministic) or instructions (agent-driven via `customize_response` system tool).

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in callback_context.get_last_user_input():
    if part.text == "<event>session start</event>":
      return LlmResponse.from_parts(parts=[
          Part.from_customized_response(
              content=(
                  "Hello, I'm your Acme Bank assistant. Please listen to the "
                  "following legal disclaimer: <LEGAL_DISCLAIMER>"
              ),
              disable_barge_in=True
          ),
          Part.from_text("How can I help you today?")
      ])
  return None
```

**Key API:** `Part.from_customized_response(content=..., disable_barge_in=True)`

**Banking adaptation:** Force the user to listen to the full recording disclaimer, consent notice, or transaction confirmation amount before continuing.

---

### 3.5 Custom Response for No-Input (Silence Timeout)

**Problem:** The agent times out waiting for user input. Instead of a generative response, you want a deterministic prompt.

**Hook:** `before_model_callback`

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in callback_context.get_last_user_input():
    if part.text:
      if "no user activity detected" in part.text:
        return LlmResponse.from_parts(parts=[
            Part.from_text(text="Hi, are you still there?")
        ])

  return None
```

**Banking adaptation:** Combine with the inactivity counter pattern from the sample app to implement progressive timeout behavior:
1. First timeout: "Are you still there?"
2. Second timeout: "I'll wait a bit longer."
3. Third timeout: End session with `end_session` tool.

---

## 4) Error Handling Patterns

### 4.1 Transfer to Another Agent on Tool Failure

**Problem:** A critical tool (authentication, account lookup) fails. You want to deterministically hand off to an escalation agent rather than letting the model attempt recovery.

**Hook:** `before_model_callback`

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in llm_request.contents[-1].parts:
    if (part.has_function_response('authentication') and
        'error' in part.function_response.response['result']):
      return LlmResponse.from_parts(parts=[
          Part.from_text(
              'Sorry something went wrong, let me transfer you '
              'to another agent.'
          ),
          Part.from_agent_transfer(agent='escalation agent')
      ])
  return None
```

**Key API:** `Part.from_agent_transfer(agent='...')` — programmatic agent handoff from a callback.

**Banking adaptation:** Transfer to `human_handover_agent` when AcmeLegi authentication fails or account service returns errors.

---

### 4.2 Terminate Session on Tool Failure

**Problem:** An unrecoverable tool failure requires graceful session termination.

**Hook:** `before_model_callback`

```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  for part in llm_request.contents[-1].parts:
    if (part.has_function_response('authentication') and
        'error' in part.function_response.response['result']):
      return LlmResponse.from_parts(parts=[
          Part.from_text(
              'Sorry something went wrong, please call back later.'
          ),
          Part.from_end_session(
              reason='Failure during user authentication.'
          )
      ])
  return None
```

**Key APIs:**
- `Part.from_end_session(reason='...')` — programmatic session termination from a callback.
- `Part.from_agent_transfer(agent='...')` — programmatic agent handoff from a callback.

**Banking adaptation:** Use when critical banking services are unavailable and no fallback is possible.

---

## 5) Context and Variables Patterns

### 5.1 Pass Context Variables to OpenAPI Tools (`x-ces-session-context`)

**Problem:** Your OpenAPI tool needs session context (session ID, project ID, custom variables) without the model having to predict these values.

**Mechanism:** `x-ces-session-context` annotation in OpenAPI schema.

**Available context values:**

| Expression | Value |
|---|---|
| `$context.project_id` | The Google Cloud project ID |
| `$context.project_number` | The Google Cloud project number |
| `$context.location` | The location (region) of the agent |
| `$context.app_id` | The agent application ID |
| `$context.session_id` | The unique identifier for the session |
| `$context.variables` | All context variable values as an object |
| `$context.variables.variable_name` | The value of a specific context variable |

**OpenAPI schema example:**
```yaml
openapi: 3.0.0
info:
  title: Banking API
  description: Voice Banking backend API
  version: 1.0.0
paths:
  /api/accounts/{session_id}/balance:
    get:
      operationId: getAccountBalance
      parameters:
      - name: session_id
        in: path
        description: The session ID for audit correlation.
        required: true
        schema:
          type: string
        x-ces-session-context: $context.session_id
      - name: customer_id
        in: query
        description: Customer identifier from session variables.
        required: true
        schema:
          type: string
        x-ces-session-context: $context.variables.customer_id
      responses:
        '200':
          description: Account balance response
          content:
            application/json:
              schema:
                type: object
                properties:
                  balance:
                    type: number
                  currency:
                    type: string
```

**Passing all variables as request body:**
```yaml
      requestBody:
        description: Session context
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/SessionParams'
components:
  schemas:
    SessionParams:
      type: object
      description: All context variables
      x-ces-session-context: $context.variables
```

**Key insight:** Parameters annotated with `x-ces-session-context` are **invisible to the model** — the schema is not shared with the model, and the values are injected by the runtime. This prevents hallucination of session IDs and ensures correct context propagation.

**Banking adaptation:** Essential for correlating API calls with audit sessions, passing customer IDs without model prediction, and injecting legitimation tokens into backend calls.

---

### 5.2 Dynamic Prompts via Variables and Callbacks

**Problem:** You want to change the agent's behavior dynamically based on user identity, conversation state, or external signals.

**Mechanism:** Variables + `before_model_callback` + instructions with variable interpolation.

**Variables:**

| Name | Default |
|---|---|
| `current_instructions` | You are Gemini and you work for Google. |
| `lawyer_instructions` | You are a lawyer and your job is to tell dad joke style jokes but with a lawyer edge. |
| `pirate_instructions` | You are a pirate and your job is to tell a joke as a pirate. |
| `username` | Unknown |

**Instructions (with variable interpolation):**
```
The current user is: {username}
You can use {@TOOL: update_username} to update the user's name if they provide it.

Follow the current instruction set below exactly.

{current_instructions}
```

**Python tool for state mutation:**
```python
from typing import Optional

def update_username(username: str) -> Optional[str]:
  """Updates the current user's name."""
  set_variable("username", username)
```

**Callback for dynamic instruction switching:**
```python
def before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest
) -> Optional[LlmResponse]:
  username = callback_context.get_variable("username", None)

  if username == "Jenn":
    new_instructions = callback_context.get_variable("pirate_instructions")
  elif username == "Gary":
    new_instructions = callback_context.get_variable("lawyer_instructions")

  callback_context.set_variable("current_instructions", new_instructions)
```

**Banking adaptation:** Switch instruction sets based on customer segment (premium vs standard), legitimation state (authenticated vs anonymous), or channel type (telephony vs web).

---

## 6) Pattern Selection Guide

| Scenario | Pattern | Hook |
|---|---|---|
| Consistent IVR greeting | 1.1 Deterministic Greeting | `before_model_callback` |
| Regulatory disclaimer | 1.2 Mandatory Content | `after_model_callback` |
| Post-call audit logging | 1.3 Session End Cleanup | `after_model_callback` |
| UI chip lists / cards | 2.1 Custom Payload | `after_model_callback` |
| Brand audio jingle | 3.1 Pre-Recorded Audio | `before_model_callback` |
| Wait during slow API | 3.2 Blocking Hold Music | `after_model_callback` |
| Background processing | 3.3 Async Hold Music | `before_model_callback` |
| Non-interruptible disclaimer | 3.4 Disable Barge-In | `before_model_callback` |
| Silence timeout handling | 3.5 No-Input Response | `before_model_callback` |
| Tool failure → escalate | 4.1 Transfer on Error | `before_model_callback` |
| Tool failure → terminate | 4.2 End Session on Error | `before_model_callback` |
| Pass session context to API | 5.1 `x-ces-session-context` | OpenAPI annotation |
| Dynamic persona/instructions | 5.2 Dynamic Prompts | `before_model_callback` + variables |

---

## 7) Banking-Specific Pattern Combinations

### Voice Banking Call Flow (Composite Pattern)

A production voice banking call combines multiple patterns:

```
Session Start
  → 3.1 Brand Audio (Acme Bank jingle, interruptable=false)
  → 3.4 Disable Barge-In (recording disclaimer)
  → 1.2 Mandatory Content (consent notice fallback)

User Request
  → 5.1 x-ces-session-context (inject customer_id, session_id into API)
  → 3.2 Hold Music (during AcmeLegi verification)

Tool Failure
  → 4.1 Transfer on Error (→ human_handover_agent)
  OR
  → 4.2 Terminate on Error (unrecoverable)

Session End
  → 1.3 Session End Cleanup (audit log finalization)
  → 3.5 No-Input progressive timeout (if user goes silent)
```

### Consent and Legitimation Gate (Composite Pattern)

```
Sensitive Request Detected
  → before_tool_callback: check consent_state variable
  → before_tool_callback: check legitimation_token freshness
  → If missing: invoke consent_check / verify_legitimation tools
  → If denied: 4.1 Transfer to human_handover_agent
  → If approved: proceed with banking tool
  → after_tool_callback: log CONSENT_CHECK / LEGITIMATION_CHECK events
```

---

## References

- Best practices and patterns: https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices
- Callback documentation: https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- Python runtime reference: https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/python
- System tools (customize_response, end_session): https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool#system_tools
- Variables: https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/variable
- OpenAPI tools: https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api
