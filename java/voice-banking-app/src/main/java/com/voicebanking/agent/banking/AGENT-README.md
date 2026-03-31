# BankingOperationsAgent

> **Agent ID:** `banking-operations`  
> **Package:** `com.voicebanking.agent.banking`  
> **Status:** ✅ Implemented  
> **Category:** Category 2 — Voice-Enabled Context-Aware Banking (Read)  
> **Priority:** Foundation (Pre-existing)

---

## Agent Description

The **BankingOperationsAgent** is the core banking domain agent responsible for customer account data retrieval. It provides read-only access to account balances, account listings, and transaction history. This agent serves as the foundation for all customer-facing banking queries.

### Role in System

- **Primary Use:** Customer balance inquiries and transaction lookups
- **Interface:** I-06 Account Balance API, I-07 Account Statements API
- **User Intents:** "What's my balance?", "Show my transactions", "List my accounts"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `getBalance` | Retrieve current balance for an account | `accountId` (optional, defaults to checking) | `accountId`, `available`, `current`, `currency`, `asOf` |
| `listAccounts` | List all accounts for authenticated customer | (none) | List of `Account` objects |
| `queryTransactions` | Search transaction history with filters | `accountId`, `dateFrom`, `dateTo`, `textQuery`, `limit` | List of `Transaction` objects |

### Tool Usage Examples

```
getBalance { accountId: "acc-checking-001" }
→ { accountId: "acc-checking-001", available: 2500.00, current: 2500.00, currency: "EUR" }

queryTransactions { accountId: "acc-checking-001", textQuery: "REWE", limit: 5 }
→ { transactions: [...filtered transactions matching "REWE"] }
```

---

## Problem Statement

### Business Problem
Customers frequently want to check their account balances and review recent transactions. These are **high-volume, low-complexity queries** that are ideal for voice automation.

### Technical Problem
Need a consistent interface for account data access that:
- Handles multiple account types (checking, savings, credit)
- Supports flexible transaction filtering (date, text, amount)
- Provides real-time or near-real-time balance information
- Integrates with Core Banking System (CBS)

### FR Coverage
- **FR-004:** Account Balance Inquiry ✅
- **FR-005:** Account Statements (partial) ✅

---

## Solution Approach

### Architecture Pattern
The agent follows a **Service-Delegation Pattern**:

```
BankingOperationsAgent
         │
         ▼
    BankingService (Business Logic)
         │
         ▼
    Repository/Client (Data Access)
         │
         ▼
    Mock Data / Core Banking API
```

### Key Design Decisions

1. **Default Account Fallback:** If no `accountId` is provided, defaults to `acc-checking-001` (primary checking). This simplifies voice interactions ("What's my balance?" without specifying account).

2. **Map-Based Tool I/O:** All tools accept `Map<String, Object>` for flexibility and use pattern matching for dispatch.

3. **Instant-Based Dates:** Transaction filtering uses `java.time.Instant` for timezone-safe date handling.

---

## Dependencies

### Internal
- `BankingService` — Business logic for account operations
- `Balance`, `Account`, `Transaction` — Domain models

### External
- Core Banking System (mocked in current implementation)

### Package Structure
```
banking/
├── BankingOperationsAgent.java    # Main agent
├── domain/
│   ├── Account.java               # Account record
│   ├── Balance.java               # Balance record
│   └── Transaction.java           # Transaction record
├── service/
│   └── BankingService.java        # Business logic
└── tool/
    └── (Tool-specific helpers)
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real CBS Integration** | Uses mock data, not real banking APIs | High for production | P0 |
| **No Account Type Filtering** | Cannot filter by account type in listAccounts | Low | P3 |
| **No Pagination** | queryTransactions uses limit but no offset/cursor | Medium | P2 |
| **No Transaction Categories** | Transactions lack category classification | Medium | P2 |
| **No Currency Conversion** | All amounts in native currency | Low | P3 |
| **No Account Nicknames** | Cannot reference accounts by user-defined names | Low | P3 |

### Missing from Implementation Plan (N/A - Pre-existing Agent)
This agent predates the implementation plans and was created as part of the initial MVP.

---

## Alternative Approaches

### Current: Agent Pattern
```
BankingOperationsAgent implements Agent
    → executeTool("getBalance", input)
    → executeTool("queryTransactions", input)
```

### Alternative 1: Skills Pattern
Instead of a monolithic agent, decompose into **reusable skills**:

```
BalanceSkill.execute(accountId) → Balance
TransactionQuerySkill.execute(query) → List<Transaction>
AccountListSkill.execute() → List<Account>
```

**Pros:**
- Skills can be composed by multiple agents
- Easier testing at skill level
- Better single responsibility

**Cons:**
- More classes to manage
- Skills need a registry too
- Current Agent interface is well-established

### Alternative 2: Direct Service Exposure
Expose `BankingService` directly to Orchestrator without Agent wrapper.

**Pros:**
- Simpler call path
- Less indirection

**Cons:**
- Loses tool-based introspection
- Inconsistent with other agents
- No policy enforcement hook

### Recommendation
**Keep Agent pattern** for now. The current implementation is appropriate for the problem domain. Consider Skills pattern only if:
- Multiple agents need identical capabilities
- Tool composition becomes a bottleneck
- Testing reveals need for finer granularity

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | Skills Pattern |
|-----------|------------------------|----------------|
| Discoverability | ✅ AgentRegistry auto-discovers | 🟡 Need SkillRegistry |
| Composability | 🟡 Tools coupled to agent | ✅ Skills can be mixed |
| Testing | ✅ Easy to mock agent | ✅ Easy to test skills |
| Complexity | ✅ Simple, one class | 🟡 Multiple skill classes |
| Granularity | 🟡 Agent-level granularity | ✅ Fine-grained |

### Granularity Assessment
The **BankingOperationsAgent** has **appropriate granularity**:
- 3 tools is a reasonable scope
- All tools relate to the same domain (accounts/transactions)
- Cohesion is high (single responsibility: banking data access)

**Not too coarse:** Each tool has distinct purpose
**Not too fine:** Don't need separate BalanceAgent, TransactionAgent

---

## Testing

### Unit Tests
- `BankingOperationsAgentTest.java` — Agent tool execution tests

### Integration Tests
- Test with mock BankingService
- Verify tool parameter validation

### Golden Test Cases
```
1. Balance inquiry with explicit account ID
2. Balance inquiry with default account
3. Transaction query with date range
4. Transaction query with text search
5. List all accounts
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AgentRegistry](../AgentRegistry.java)
- [FR-004 Account Balance Inquiry](../../../../../docs/functional-requirements/fr-004-account-balance-inquiry.md)
- [FR-005 Account Statements](../../../../../docs/functional-requirements/fr-005-account-statements.md)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
