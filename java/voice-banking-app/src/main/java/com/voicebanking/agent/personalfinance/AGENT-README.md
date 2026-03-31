# PersonalFinanceManagementAgent

> **Agent ID:** `personal-finance`  
> **Package:** `com.voicebanking.agent.personalfinance`  
> **Status:** ✅ Implemented  
> **Category:** Category 5 — Agentic Conversational Banking (Analyze)  
> **Priority:** 🟡 P3 Enhancer  
> **Implementation Plan:** [AGENT-009-personal-finance.md](../../../../../docs/implementation-plan/AGENT-009-personal-finance.md)

---

## Agent Description

The **PersonalFinanceManagementAgent** provides factual insights into spending patterns, budget status, and cash flow based on customer transactions. It is **analysis-only** and does **not** provide financial advice or recommendations. All responses are voice-first and bilingual (English/German).

### Role in System

- Reads transaction data from **BankingOperationsAgent** and **CreditCardContextAgent**
- Aggregates spending by category and period
- Produces cash flow summaries and recurring payment insights
- Flags unusual spending activity using statistical thresholds
- Enforces **no-advice** policy with a disclaimer when needed

---

## Tools

| Tool ID | Description | Example Input |
|---------|-------------|---------------|
| `getSpendingBreakdown` | Spending by category for a period | `{ "month": "2026-01", "lang": "en" }` |
| `getMonthlyTrend` | Month-over-month spending trend | `{ "months": 3, "lang": "de" }` |
| `getIncomeExpenseSummary` | Cash flow summary for a month | `{ "month": "2026-01" }` |
| `getBudgetStatus` | Budget vs. actual status | `{ "month": "2026-01" }` |
| `getRecurringTransactions` | Detect recurring payments | `{ "lang": "en" }` |
| `getTopMerchants` | Top spending merchants | `{ "limit": 5 }` |
| `getUnusualActivity` | Unusual spending detection | `{ "month": "2026-01" }` |

---

## Architecture

```
PersonalFinanceManagementAgent
├── TransactionAggregator            # Integrates Banking + Credit Card data
├── SpendingAnalysisService          # Breakdown, trend, top merchants
├── BudgetTrackingService            # Budget status
├── CashFlowService                  # Income vs expenses
├── RecurringDetectionService        # Recurring payments
├── UnusualActivityService           # Outlier detection
├── VoiceResponseFormatter           # Bilingual, voice-first formatting
└── PolicyEnforcer                   # No-advice enforcement
```

---

## Usage Examples

### From AgentRegistry

```java
@Autowired AgentRegistry registry;

Agent agent = registry.getAgent("personal-finance").orElseThrow();
Map<String, Object> result = agent.executeTool("getSpendingBreakdown", 
    Map.of("month", "2026-01", "lang", "en"));
```

### Example Voice Response (EN)

> "Your spending for the period January 1, 2026 to January 31, 2026: Housing €1,200, Food €485, Transport €320."

### Example Voice Response (DE)

> "Ihre Ausgaben im Zeitraum 1. Januar 2026 bis 31. Januar 2026: Wohnen 1.200 Euro, Lebensmittel 485 Euro, Transport 320 Euro."

---

## Compliance

- **No advice**: factual analysis only
- **Disclaimer** added when advisory language is detected
- **Logging** for policy violations and execution errors

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [Architecture Master Plan](../../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md) | Agent architecture and dependencies |
| [Voice Banking Interfaces](../../../../../docs/architecture/voice-banking-interfaces-2026.md) | Interface contracts |
| [Implementation Plan](../../../../../docs/implementation-plan/AGENT-009-personal-finance.md) | Detailed scope and steps |

---

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for version history.
