# CX Agent Studio Caveats and Gotchas

Author: Codex
Date: 2026-02-07
Status: Risk guide
Modified by Codex on 2026-02-07 to add deployment/compliance caveats.

## 1) Common Pitfalls
### Ambiguous instruction design
Symptoms:
- Inconsistent transfer/tool decisions.
- Verbose or off-task responses.

Mitigation:
- Use strict trigger/action taskflow steps.
- Keep out-of-scope logic explicit and deterministic.

### Missing deterministic controls
Symptoms:
- Model mentions an action but does not call the required tool.

Mitigation:
- Add after-model callback safeguards.
- Track state flags (`request_image_tool_called` style) to avoid loops.

### Callback side effects and recursion
Symptoms:
- Infinite loops from forced tool calls.
- Repeated overrides.

Mitigation:
- Gate forced callbacks with explicit idempotency flags.
- Return `None` unless override is strictly necessary.

## 2) Guardrail Anti-Patterns
- One broad policy prompt trying to solve all safety and policy requirements.
- Static canned responses for all violations regardless severity.
- No evaluation coverage for adversarial prompts.

Recommended:
- Layer safety + prompt security + content filters + policy guardrails.
- Tune each guardrail for one purpose.
- Add adversarial eval cases for each guardrail path.

## 3) Performance Considerations
- Parallel tool execution can increase throughput, but shared-state mutations must be carefully coordinated.
- Toolset network latency can dominate total turn time.
- Heavy callback logic can become hidden hot paths.

Recommendations:
- Keep callbacks lightweight and deterministic.
- Isolate long-running work in tools/services, not callbacks.
- Use evaluation thresholds and telemetry to watch regression.

## 4) Deployment Channel Pitfalls
Common issues:
- API deployments that do not enforce server-side token/session brokering.
- Web widget deployments with incomplete origin/auth configuration.
- Telephony/contact-center integrations started before SIP/adapter prerequisites are complete.

Mitigation:
- Treat channel onboarding as a separate readiness track with explicit checks.
- Validate channel-specific deployment artifacts before traffic cutover.
- Run channel smoke tests after every version deployment.

## 5) Security and Compliance Gaps To Watch
- Sensitive data may be available in prompt context unless aggressively minimized.
- Callback and tool logging can leak PII if not redacted.
- Prompt-injection coverage is never complete; keep multi-layer controls.

Required controls for regulated domains:
- Data minimization and strict redaction policies.
- Explicit allowlists for tool invocations.
- Immutable audit logs for transfers and tool executions.

## 6) CMEK and Audit Logging Gotchas
- CMEK setup is location-sensitive; mismatched key/app regions can block rollout.
- One-key-per-project/location constraints can force up-front key governance decisions.
- Disabled/revoked keys can break runtime access unexpectedly.
- Audit logging enabled without searchable retention workflows is usually insufficient for regulated investigations.

## 7) Migration Challenges
### From Dialogflow CX
- Dialogflow flow/page mental model does not map 1:1 to agent/taskflow + callback design.
- Teams need to rewrite deterministic flow logic into instruction + callback patterns.

### From ADK or other code-first stacks
- Declarative artifact workflow can feel restrictive for heavily custom runtimes.
- Callback lifecycle semantics and tool contracts need adaptation.

### From LangGraph/CrewAI
- Graph/task abstractions are more code-native; CX Studio requires declarative packaging and explicit transfer syntax.

## 8) Known Operational Gotchas
- Documentation path names for new CX Studio pages can change quickly (for example sample/instruction/evaluation/export reference aliases).
- Export packages can include environment placeholders that must be resolved before deployment.
- Inconsistent variable initialization can break callback assumptions.
- Simulator/version/change-history aliases can be unstable in crawler/doc mirrors; rely on docs navigation tree and API resources where needed.

Workarounds:
- Treat export package as source of truth and validate every referenced path.
- Add startup validation for required variables and toolset environment values.
- Maintain a compatibility map for documentation URL aliases.

## 9) Recommended Pre-Production Checklist
- Handoff matrix fully covered by tests.
- High-risk tool calls protected by before-tool callbacks.
- Prompt-injection and content abuse eval cases pass.
- Environment secrets and endpoint URLs resolved and validated.
- Logging redaction validated with sample sensitive payloads.
- Channel-specific readiness (API/web/telephony/contact center) validated in staging.
- CMEK key ownership, rotation, and recovery runbooks approved.

## References
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/guardrail
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/api-access
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/web-widget
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/google-telephony-platform
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/twilio
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/five9
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/ccaas
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/cmek
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/audit-logging
