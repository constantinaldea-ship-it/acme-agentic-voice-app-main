# PolicyGuardrailsAgent

> **Agent ID:** `policy-guardrails`  
> **Package:** `com.voicebanking.agent.policy`  
> **Status:** ✅ Implemented  
> **Category:** Category 0 — Overarching AI Banking Voice Ecosystem  
> **Priority:** 🔴 P0 Essential  
> **Implementation Plan:** [AGENT-001-policy-guardrails.md](../../../../../docs/implementation-plan/AGENT-001-policy-guardrails.md)

---

## Agent Description

The **PolicyGuardrailsAgent** is the foundational safety and compliance layer for the AI Banking Voice system. It enforces policy boundaries, handles out-of-scope request refusals, prevents prohibited actions, and ensures regulatory compliance. This agent acts as a **gateway** that ALL other agents should consult before executing sensitive operations.

### Role in System

- **Primary Use:** Intent classification, policy enforcement, refusal generation
- **Critical For:** Regulatory compliance (BaFin, GDPR, EU AI Act)
- **User Intents Blocked:** "Transfer money", "Buy stocks", "Give me advice", "What's my password?"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `classifyIntent` | Classify intent as in-scope/out-of-scope | `intent`, `rawText` | `category`, `confidence`, `matchedRuleId`, `matchedBy` |
| `checkPolicyViolation` | Evaluate if intent violates policy | `intent`, `rawText` | `violation`, `category`, `decision`, `message`, `alternatives` |
| `getRefusalMessage` | Get appropriate refusal message | `category` | `message`, `alternatives`, `handoverOffered`, `templateId` |
| `getAlternativeChannel` | Get alternative channels for request | `category` | `channels` (list of alternative channels) |
| `logPolicyEvent` | Log policy decision for audit | `intent`, `category`, `decision`, `correlationId` | `logged`, `eventId` |
| `blockProhibitedAction` | Block and log prohibited action | `reason`, `intent`, `correlationId` | `blocked`, `message`, `correlationId` |

### Tool Usage Examples

```
classifyIntent { intent: "transfer_money", rawText: "transfer 500 euros" }
→ { category: "MONEY_MOVEMENT", confidence: 0.95, matchedBy: "KEYWORD" }

checkPolicyViolation { intent: "investment_advice", rawText: "should I buy Apple stock?" }
→ { violation: true, category: "ADVISORY", decision: "DENY", message: "I cannot provide investment advice..." }

getRefusalMessage { category: "MONEY_MOVEMENT" }
→ { message: "I cannot process money transfers...", alternatives: ["mobile app", "branch"], handoverOffered: true }
```

---

## Problem Statement

### Business Problem
AI voice assistants must not:
- Execute financial transactions (regulatory risk)
- Provide investment advice (licensed activity)
- Expose sensitive customer data (GDPR)
- Perform actions outside defined scope (liability)

The system needs a **centralized policy enforcement point** that all agents respect.

### Technical Problem
Need to:
- Classify intents against a policy ruleset
- Generate appropriate, helpful refusal messages
- Log all policy decisions for audit trail
- Provide alternative channels when AI cannot help
- Scale policy rules without code changes

### FR Coverage
- **FR-007:** Out-of-Scope Refusal ✅

---

## Solution Approach

### Architecture Pattern
```
User Request → Orchestrator → PolicyGuardrailsAgent → Check Decision
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
               ALLOWED          ESCALATE          DENIED
                 │                 │                 │
                 ▼                 ▼                 ▼
            Continue to     HumanHandover      Return refusal
            target agent      Agent            + alternatives
```

### Key Design Decisions

1. **Rule-Based Classification:** Uses `PolicyRulesConfig` with keyword matching and pattern recognition for intent classification.

2. **Policy Categories:** 
   - `BANKING_QUERY` (allowed)
   - `GENERAL_INFO` (allowed)
   - `MONEY_MOVEMENT` (denied - requires authenticated channel)
   - `ADVISORY` (denied - licensed activity)
   - `SECURITY_VIOLATION` (blocked - security threat)
   - `HARMFUL` (blocked - dangerous content)
   - `OUT_OF_SCOPE` (denied - outside domain)

3. **Audit Logging:** All policy decisions logged via `PolicyLoggingService` with correlation IDs for traceability.

4. **Alternative Channel Guidance:** Refusals include helpful alternatives (mobile app, branch, call center).

---

## Dependencies

### Internal
- `IntentClassifierService` — Classification logic
- `RefusalMessageService` — Message generation
- `PolicyLoggingService` — Audit logging
- `PolicyRulesConfig` — Rule configuration

### External
- Logging infrastructure (for audit)

### Package Structure
```
policy/
├── PolicyGuardrailsAgent.java      # Main agent
├── config/
│   └── PolicyRulesConfig.java      # Rule definitions
├── domain/
│   ├── IntentClassification.java   # Classification result
│   ├── PolicyCategory.java         # Category enum
│   ├── PolicyDecision.java         # Decision record
│   └── RefusalResult.java          # Refusal message
└── service/
    ├── IntentClassifierService.java
    ├── RefusalMessageService.java
    └── PolicyLoggingService.java
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No ML-Based Classification** | Uses keyword matching, not NLU | Medium - may miss nuanced intents | P2 |
| **No External Rule API** | Rules hardcoded in config | Medium - changes need deployment | P2 |
| **No Real-Time Rule Updates** | Cannot update rules without restart | Low - rules rarely change | P3 |
| **No Confidence Threshold Config** | Hardcoded confidence levels | Low | P3 |
| **No Integration with Orchestrator** | Agent exists but not wired as gateway | High - policy not enforced | P0 |
| **No PII Detection** | Cannot detect PII in user input | Medium - GDPR compliance | P1 |

### Comparison to Implementation Plan

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `classifyIntent` | ✅ Implemented | Working |
| `checkPolicyViolation` | ✅ Implemented | Working |
| `getRefusalMessage` | ✅ Implemented | Working |
| `getAlternativeChannel` | ✅ Implemented | Working |
| `logPolicyEvent` | ✅ Implemented | Working |
| `blockProhibitedAction` | ✅ Implemented | Working |

**All 6 planned tools implemented.** ✅

---

## Alternative Approaches

### Current: Agent Pattern (Policy as Agent)
```
PolicyGuardrailsAgent implements Agent
    → Tools called by Orchestrator
    → Returns allow/deny decisions
```

### Alternative 1: Middleware Pattern
Policy enforcement as a middleware/interceptor:
```
Orchestrator
    │
    ▼
PolicyMiddleware.intercept(request) → Allow/Deny
    │
    ▼
Target Agent
```

**Pros:**
- Automatic enforcement (agents can't bypass)
- Centralized interception point
- Cleaner separation of concerns

**Cons:**
- Less visibility into policy decisions
- Harder to customize per-agent
- May add latency to all requests

### Alternative 2: Annotation-Based Policy
Policy requirements declared on tools:
```
@PolicyCheck(category = "BANKING_QUERY", minConfidence = 0.85)
public Map<String, Object> getBalance(...) { }
```

**Pros:**
- Policy close to code
- Clear requirements per tool
- Compile-time visibility

**Cons:**
- Scattered policy logic
- Harder to change centrally
- Need annotation processor

### Alternative 3: Skills Pattern
Decompose into reusable skills:
```
IntentClassificationSkill.classify(intent) → Classification
PolicyEvaluationSkill.evaluate(classification) → Decision
RefusalGenerationSkill.generate(category) → Message
AuditLoggingSkill.log(decision) → EventId
```

**Pros:**
- Each skill is independently testable
- Skills can be composed differently
- Finer granularity

**Cons:**
- More infrastructure needed
- Risk of inconsistent composition
- Current agent is already well-structured

### Recommendation
**Keep Agent pattern, ADD Middleware wrapper** for enforcement:
1. PolicyGuardrailsAgent provides the tools
2. Orchestrator uses a `PolicyMiddleware` that calls the agent automatically
3. Other agents don't need to explicitly call policy checks

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | Skills Pattern | Middleware Pattern |
|-----------|------------------------|----------------|-------------------|
| Enforcement | 🟡 Opt-in by Orchestrator | 🟡 Opt-in by caller | ✅ Automatic |
| Visibility | ✅ Tools discoverable | ✅ Skills discoverable | 🟡 Hidden layer |
| Flexibility | ✅ Per-tool customization | ✅ Fine-grained composition | 🟡 Coarser |
| Testing | ✅ Easy to mock | ✅ Easy to test skills | 🟡 Integration testing |
| Bypass Risk | ⚠️ Can be bypassed | ⚠️ Can be bypassed | ✅ Cannot bypass |

### Granularity Assessment

**Current State:** **Appropriate granularity**
- 6 tools covering distinct aspects of policy
- High cohesion (all about policy enforcement)
- Clear single responsibility

**Not too coarse:** Each tool has distinct purpose
**Not too fine:** Policy naturally groups these capabilities

---

## Testing

### Unit Tests
- `PolicyGuardrailsAgentTest.java` — 25+ test cases

### Test Categories
```
1. Intent classification tests (various categories)
2. Policy violation detection tests
3. Refusal message generation tests
4. Alternative channel lookup tests
5. Audit logging verification tests
6. Prohibited action blocking tests
```

### Golden Test Cases
```
1. Balance inquiry → ALLOWED (BANKING_QUERY)
2. Transfer request → DENIED (MONEY_MOVEMENT)
3. Investment advice → DENIED (ADVISORY)
4. "What's my password" → BLOCKED (SECURITY_VIOLATION)
5. Unknown intent → OUT_OF_SCOPE with alternatives
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AGENT-001 Implementation Plan](../../../../../docs/implementation-plan/AGENT-001-policy-guardrails.md)
- [FR-007 Out-of-Scope Refusal](../../../../../docs/functional-requirements/fr-007-out-of-scope-refusal.md)
- [Security Architecture](../../../../../docs/architecture/security-architecture.md)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
