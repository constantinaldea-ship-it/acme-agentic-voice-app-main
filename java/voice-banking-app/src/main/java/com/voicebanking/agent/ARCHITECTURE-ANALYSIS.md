# Multi-Agent Architecture Analysis

> **Location:** `com.voicebanking.agent`  
> **Version:** 1.0  
> **Date:** 2026-01-24  
> **Status:** Living Document

---

## Executive Summary

This document provides a comprehensive architectural analysis of the implemented agent framework, evaluating the current **Agent-Based Architecture** against alternative patterns, particularly the **Skills-Based Architecture**. It consolidates findings from all 6 implemented agents.

### Current State
- **6 of 12 agents implemented** (50%)
- **25 of ~45 tools implemented** (56%)
- **All P0 Essential agents complete** ✅
- **Core infrastructure ready for remaining agents**

---

## 1. Agent Inventory Summary

| Agent | Tools | Category | Granularity | Recommendation |
|-------|-------|----------|-------------|----------------|
| BankingOperationsAgent | 3 | Cat 2 (Banking) | ✅ Appropriate | Keep as-is |
| LocationServicesAgent | 1 | Cat 2 (Banking) | 🟡 Too fine | Expand to 3-5 tools |
| PolicyGuardrailsAgent | 6 | Cat 0 (Core) | ✅ Appropriate | Add middleware wrapper |
| HumanHandoverAgent | 6 | Cat 0 (Core) | ✅ Appropriate | Keep as-is |
| KnowledgeCompilerAgent | 4 | Cat 2 (Knowledge) | ✅ Appropriate | Add getProductInfo tool |
| TextGeneratorAgent | 5 | Cat 0 (Core) | ✅ Appropriate | Keep as-is |

---

## 2. Agent vs Skills Pattern Analysis

### 2.1 Current Architecture: Agent Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                        Orchestrator                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│    ┌─────────────────┐    ┌─────────────────┐                  │
│    │ AgentRegistry   │───▶│ Agent           │                  │
│    │                 │    │ ───────         │                  │
│    │ • getAgent(id)  │    │ • getToolIds()  │                  │
│    │ • findByTool()  │    │ • executeTool() │                  │
│    │ • listAgents()  │    └─────────────────┘                  │
│    └─────────────────┘             │                            │
│                                    ▼                            │
│                         ┌─────────────────┐                    │
│                         │ Service Layer   │                    │
│                         │ (Business Logic)│                    │
│                         └─────────────────┘                    │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Each agent owns a set of related tools
- Tools are discovered via `AgentRegistry`
- Agents encapsulate domain knowledge
- High cohesion within agents

### 2.2 Alternative: Skills Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                        Orchestrator                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│    ┌─────────────────┐                                          │
│    │ SkillRegistry   │                                          │
│    │                 │                                          │
│    │ • getSkill(id)  │                                          │
│    │ • compose()     │                                          │
│    └────────┬────────┘                                          │
│             │                                                    │
│    ┌────────┼────────┬────────────┬────────────┐               │
│    ▼        ▼        ▼            ▼            ▼               │
│ ┌──────┐ ┌──────┐ ┌──────┐   ┌──────┐   ┌──────┐             │
│ │Skill1│ │Skill2│ │Skill3│   │Skill4│   │Skill5│             │
│ └──────┘ └──────┘ └──────┘   └──────┘   └──────┘             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Characteristics:**
- Skills are atomic, reusable capabilities
- Orchestrator composes skills dynamically
- Lower coupling between skills
- Finer granularity than agents

### 2.3 Comparison Matrix

| Criterion | Agent Pattern | Skills Pattern | Verdict |
|-----------|--------------|----------------|---------|
| **Discoverability** | ✅ AgentRegistry auto-discovers | 🟡 Need SkillRegistry | Agent ≈ Skills |
| **Cohesion** | ✅ Domain-grouped | 🟡 Scattered | Agent wins |
| **Composability** | 🟡 Tool-level only | ✅ Fine-grained composition | Skills wins |
| **Testing** | ✅ Agent mocking | ✅ Skill isolation | Tie |
| **Complexity** | ✅ Fewer classes | 🟡 Many small classes | Agent wins |
| **Reusability** | 🟡 Agent-bound | ✅ Cross-agent | Skills wins |
| **Granularity Control** | ✅ Natural grouping | 🟡 Manual grouping | Agent wins |
| **Domain Modeling** | ✅ Clear boundaries | 🟡 Fragmented | Agent wins |

### 2.4 Recommendation

**Keep Agent Pattern** with the following enhancements:

1. **Internal Skill-Like Structure:** Agents internally delegate to services (already the case with formatters, classifiers, etc.)

2. **Shared Skill Layer:** Extract truly cross-cutting capabilities:
   ```
   shared/
   ├── VoiceFormattingSkill     → Used by TextGeneratorAgent, others
   ├── PolicyCheckSkill         → Used by PolicyGuardrailsAgent, Orchestrator
   └── AuditLoggingSkill        → Used by all agents
   ```

3. **Agent Composition:** Allow agents to invoke other agents' tools when needed (already supported via AgentRegistry)

---

## 3. Granularity Analysis

### 3.1 Per-Agent Assessment

| Agent | Current Tools | Ideal Range | Status | Action |
|-------|--------------|-------------|--------|--------|
| BankingOperationsAgent | 3 | 3-5 | ✅ Optimal | None |
| LocationServicesAgent | 1 | 3-5 | ⚠️ Too narrow | Add tools |
| PolicyGuardrailsAgent | 6 | 5-7 | ✅ Optimal | None |
| HumanHandoverAgent | 6 | 5-7 | ✅ Optimal | None |
| KnowledgeCompilerAgent | 4 | 4-6 | ✅ Optimal | Add 1 tool |
| TextGeneratorAgent | 5 | 5-7 | ✅ Optimal | None |

### 3.2 Granularity Guidelines

**Optimal Agent Scope:**
- **3-7 tools** per agent
- **Single domain** per agent
- **High cohesion:** tools should relate to same concept
- **Low coupling:** minimal dependencies on other agents

**Too Coarse (Anti-pattern):**
```
BankingMegaAgent
    ├── getBalance
    ├── transferMoney
    ├── payBill
    ├── getStatement
    ├── openAccount
    ├── closeAccount
    └── ... (20+ tools)
```

**Too Fine (Anti-pattern):**
```
BalanceAgent          → 1 tool
TransactionAgent      → 1 tool
AccountListAgent      → 1 tool
```

**Right-Sized (Current Pattern):**
```
BankingOperationsAgent    → 3 tools (balance, accounts, transactions)
LocationServicesAgent     → 3-5 tools (branches, ATMs, hours)
PolicyGuardrailsAgent     → 6 tools (classify, check, refuse, log, block)
```

---

## 4. Gap Analysis Summary

### 4.1 Cross-Agent Gaps

| Gap | Affected Agents | Priority | Recommendation |
|-----|----------------|----------|----------------|
| No real external integrations | All | P0 | Phase 2 work |
| No Orchestrator integration | Policy, All | P0 | Wire policy as gateway |
| No caching layer | Knowledge, Banking | P2 | Add shared cache |
| No metrics/analytics | All | P2 | Add observability |
| Limited test coverage | Location, Banking | P2 | Expand tests |

### 4.2 Per-Agent Gap Priorities

**High Priority (P0-P1):**
- PolicyGuardrailsAgent: Orchestrator integration
- KnowledgeCompilerAgent: Real knowledge base
- HumanHandoverAgent: Real call center integration
- LocationServicesAgent: Address geocoding

**Medium Priority (P2):**
- TextGeneratorAgent: Template expansion
- KnowledgeCompilerAgent: Vector embeddings
- All agents: Metrics and observability

---

## 5. Architectural Recommendations

### 5.1 Immediate Actions

1. **Wire PolicyGuardrailsAgent as Gateway**
   - Orchestrator should call `checkPolicyViolation` before routing to agents
   - Consider middleware wrapper for automatic enforcement

2. **Expand LocationServicesAgent**
   - Add `geocodeAddress` tool
   - Add `getBranchHours` tool
   - Add `findNearbyATMs` tool

3. **Add Missing Tool to KnowledgeCompilerAgent**
   - Implement `getProductInfo` as dedicated tool

### 5.2 Future Considerations

1. **Shared Skill Layer**
   - Extract common formatting to shared skills
   - Create shared audit logging skill
   - Create shared caching skill

2. **Event-Driven Extensions**
   - Consider events for async operations (handover completion tracking)
   - Consider events for analytics (query logging)

3. **RAG Migration Path**
   - Plan for KnowledgeCompilerAgent migration to RAG when:
     - Knowledge corpus > 1000 articles
     - Vector DB infrastructure available
     - Query accuracy becomes critical

---

## 6. Agent Documentation Index

Each implemented agent has detailed documentation:

| Agent | Documentation |
|-------|--------------|
| BankingOperationsAgent | [banking/AGENT-README.md](banking/AGENT-README.md) |
| LocationServicesAgent | [location/AGENT-README.md](location/AGENT-README.md) |
| PolicyGuardrailsAgent | [policy/AGENT-README.md](policy/AGENT-README.md) |
| HumanHandoverAgent | [handover/AGENT-README.md](handover/AGENT-README.md) |
| KnowledgeCompilerAgent | [knowledge/AGENT-README.md](knowledge/AGENT-README.md) |
| TextGeneratorAgent | [text/AGENT-README.md](text/AGENT-README.md) |

---

## 7. Decision Log

| Decision | Date | Rationale |
|----------|------|-----------|
| Keep Agent Pattern | 2026-01-24 | High cohesion, clear domain boundaries, simpler than skills |
| Internal skill-like structure | 2026-01-24 | Best of both: agent encapsulation + internal reusability |
| 3-7 tools per agent guideline | 2026-01-24 | Balances discoverability with cohesion |
| Recommend middleware for policy | 2026-01-24 | Automatic enforcement, can't be bypassed |
| Defer RAG migration | 2026-01-24 | Current keyword search sufficient for MVP |

---

## Related Documents

- [Agent Interface](Agent.java)
- [AgentRegistry](AgentRegistry.java)
- [AGENT-ARCHITECTURE-MASTER](../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md)
- [package-info.java](package-info.java)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial architecture analysis |
