# HumanHandoverAgent

> **Agent ID:** `human-handover`  
> **Package:** `com.voicebanking.agent.handover`  
> **Status:** ✅ Implemented  
> **Category:** Category 0 — Overarching AI Banking Voice Ecosystem  
> **Priority:** 🔴 P0 Essential  
> **Implementation Plan:** [AGENT-002-human-handover.md](../../../../../docs/implementation-plan/AGENT-002-human-handover.md)

---

## Agent Description

The **HumanHandoverAgent** manages seamless transfer of customer conversations from the AI assistant to human agents. It ensures context is preserved during handover, reducing customer frustration and agent handle time. This is a **critical safety mechanism** ensuring customers always have access to human support.

### Role in System

- **Primary Use:** Escalation to human agents when AI cannot help
- **Interface:** I-10 Human Handover API (Call Center Integration)
- **User Intents:** "I want to speak to a person", "Connect me to an agent", "This isn't working"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `initiateHandover` | Start handover process with full context | `sessionId`, `reason`, `queueId` | `success`, `status`, `context`, `ticketId` |
| `buildContextPayload` | Build context without initiating handover | `sessionId`, `reason` | `success`, `context` (conversation summary, tools called) |
| `checkAgentAvailability` | Check if human agents are available | `queueId` | `available`, `withinBusinessHours`, `agentCount`, `queueDepth` |
| `getQueueWaitTime` | Get estimated wait time for queue | `queueId` | `estimatedWaitMinutes`, `message`, `available` |
| `routeToQueue` | Route call to specific queue | `sessionId`, `queueId`, `reason` | `success`, `ticketId`, `estimatedWait` |
| `sendHandoverNotification` | Send context to agent desktop | `ticketId`, `sessionId`, `reason` | `success`, `message` |

### Tool Usage Examples

```
checkAgentAvailability { queueId: "general" }
→ { available: true, withinBusinessHours: true, agentCount: 5, queueDepth: 3 }

initiateHandover { sessionId: "sess-123", reason: "USER_REQUEST", queueId: "premium" }
→ { success: true, status: "QUEUED", ticketId: "TKT-456", estimatedWait: 2 }

getQueueWaitTime { queueId: "general" }
→ { estimatedWaitMinutes: 5, message: "Estimated wait time is approximately 5 minutes.", available: true }
```

---

## Problem Statement

### Business Problem
Customers may need human assistance when:
- AI cannot understand their request
- Request requires human judgment (complaints, exceptions)
- User explicitly requests human agent
- Sensitive situations (fraud, security concerns)
- Complex multi-step transactions

A poor handover experience leads to customer frustration and repeated explanations.

### Technical Problem
Need to:
- Build comprehensive context payload for receiving agent
- Integrate with call center routing systems
- Handle after-hours and queue status scenarios
- Track handover success/failure for analytics
- Maintain session continuity across AI→human transition

### FR Coverage
- **FR-006:** Human Handover ✅

---

## Solution Approach

### Architecture Pattern
```
User Request: "I want to speak to a person"
         │
         ▼
HumanHandoverAgent
    │
    ├── ContextBuilderService.buildContext()
    │       └── Summarize conversation, extract entities, list tools called
    │
    ├── QueueStatusService.checkAvailability()
    │       └── Check business hours, agent availability, queue depth
    │
    └── CallCenterClient.routeHandover()
            └── Send to call center routing system
```

### Key Design Decisions

1. **Handover Reasons:** Enum-based classification
   - `USER_REQUEST` — User asked for human
   - `AI_LIMITATION` — AI cannot handle request
   - `POLICY_ESCALATION` — Policy agent triggered
   - `ERROR_RECOVERY` — System error occurred
   - `SECURITY_CONCERN` — Potential fraud/security

2. **Business Hours Handling:** Returns friendly message when outside hours (09:00-18:00 CET, Monday-Friday)

3. **Queue Priority:** Different queues for general vs premium customers

4. **Context Payload:** Includes conversation summary, detected intent, entities, tools called, reason for handover

---

## Dependencies

### Internal
- `ContextBuilderService` — Builds handover context
- `QueueStatusService` — Queue and agent status
- `HandoverLogService` — Audit logging
- `CallCenterClient` — Integration with call center

### External
- Call Center Platform (mocked via `MockCallCenterClient`)

### Package Structure
```
handover/
├── HumanHandoverAgent.java           # Main agent
├── domain/
│   ├── HandoverContext.java          # Context payload
│   ├── HandoverReason.java           # Reason enum
│   ├── HandoverResult.java           # Result of handover
│   └── QueueStatus.java              # Queue information
├── integration/
│   ├── CallCenterClient.java         # Interface
│   └── MockCallCenterClient.java     # Mock implementation
└── service/
    ├── ContextBuilderService.java    # Context building
    ├── HandoverLogService.java       # Logging
    └── QueueStatusService.java       # Queue status
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real Call Center Integration** | Uses mock client | High for production | P0 |
| **No Callback Scheduling** | Cannot schedule callbacks if queue is full | Medium | P2 |
| **No Priority Routing** | All customers routed equally | Medium | P2 |
| **No Sentiment Detection** | Cannot detect frustrated customers | Medium | P2 |
| **No Agent Skill Matching** | Cannot route to agent with specific skills | Low | P3 |
| **No Handover Completion Tracking** | Cannot track if handover was successful | Medium | P1 |
| **Limited Conversation Summary** | Basic summarization, not ML-based | Medium | P2 |

### Comparison to Implementation Plan

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `initiateHandover` | ✅ Implemented | Full context payload |
| `buildContextPayload` | ✅ Implemented | Working |
| `checkAgentAvailability` | ✅ Implemented | With business hours |
| `getQueueWaitTime` | ✅ Implemented | Friendly messages |
| `routeToQueue` | ✅ Implemented | With logging |
| `sendHandoverNotification` | ✅ Implemented | Context to agent desktop |

**All 6 planned tools implemented.** ✅

---

## Alternative Approaches

### Current: Agent Pattern
```
HumanHandoverAgent implements Agent
    → Tools for handover orchestration
    → Called by Orchestrator when escalation needed
```

### Alternative 1: Event-Driven Pattern
Handover as an event that triggers workflow:
```
HandoverRequestedEvent
    → HandoverSaga
        → CheckAvailability
        → BuildContext
        → RouteToQueue
        → NotifyAgent
        → TrackCompletion
```

**Pros:**
- Decoupled components
- Better for async operations
- Easier retry/compensation

**Cons:**
- More complex infrastructure
- Harder to trace flow
- Overkill for current scope

### Alternative 2: Service Pattern (No Agent)
Direct service without agent wrapper:
```
HandoverService.initiateHandover(sessionId, reason)
```

**Pros:**
- Simpler for single-purpose
- Direct invocation

**Cons:**
- Loses tool discoverability
- Inconsistent with other agents
- No AgentRegistry integration

### Alternative 3: Skills Pattern
```
ContextBuildingSkill.buildContext(sessionId) → Context
QueueCheckSkill.checkAvailability(queueId) → Status
RoutingSkill.route(context, queueId) → Result
NotificationSkill.notify(ticketId, context) → Success
```

**Pros:**
- Skills reusable by other flows
- Finer granularity testing

**Cons:**
- Handover is a cohesive workflow
- Skills would be tightly coupled anyway
- Extra abstraction without benefit

### Recommendation
**Keep Agent pattern**. Handover is a distinct domain with:
- Clear boundary (AI → Human transition)
- Specific integration requirements
- Need for tool discoverability

Consider **Event-Driven pattern** in future if:
- Handover becomes async (callback scheduling)
- Multiple systems need to react to handover
- Saga-style compensation needed

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | Skills Pattern | Event-Driven |
|-----------|------------------------|----------------|--------------|
| Cohesion | ✅ High (single domain) | 🟡 Fragmented | ✅ Saga groups |
| Discoverability | ✅ AgentRegistry | 🟡 SkillRegistry | 🟡 Event catalog |
| Testing | ✅ Agent-level mocking | ✅ Skill isolation | 🟡 Event replay |
| Async Support | 🟡 Synchronous | 🟡 Synchronous | ✅ Native async |
| Complexity | ✅ Simple | 🟡 More classes | ⚠️ Infrastructure |

### Granularity Assessment

**Current State:** **Appropriate granularity**
- 6 tools covering distinct handover steps
- High cohesion (all about human escalation)
- Clear workflow: check → build → route → notify

**Not too coarse:** Each tool serves distinct purpose in flow
**Not too fine:** All tools relate to handover domain

---

## Testing

### Unit Tests
- `HumanHandoverAgentTest.java` — Comprehensive test suite

### Test Categories
```
1. Successful handover initiation
2. After-hours handling
3. Queue availability checks
4. Wait time calculations
5. Context building accuracy
6. Notification delivery
7. Error handling (service unavailable)
```

### Golden Test Cases
```
1. User requests handover during business hours → QUEUED
2. User requests handover after hours → AFTER_HOURS message
3. No agents available → Helpful wait time message
4. Context payload includes conversation summary
5. Premium customer routes to premium queue
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AGENT-002 Implementation Plan](../../../../../docs/implementation-plan/AGENT-002-human-handover.md)
- [FR-006 Human Handover](../../../../../docs/functional-requirements/fr-006-human-handover.md)
- [PolicyGuardrailsAgent](../policy/AGENT-README.md) — May trigger escalation

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
