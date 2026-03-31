# CreditCardContextAgent

> **Agent ID:** `credit-card-context`  
> **Package:** `com.voicebanking.agent.creditcard`  
> **Status:** ✅ Implemented  
> **Category:** Category 4 — Context-Aware Banking with Extended Services (Read)  
> **Priority:** 🟠 P2 Important  
> **Implementation Plan:** [AGENT-006-credit-card-context.md](../../../../../docs/implementation-plan/AGENT-006-credit-card-context.md)

---

## Agent Description

The **CreditCardContextAgent** provides detailed credit card activity information, spending insights, and rewards tracking. It integrates with the Card Management API (I-05) to retrieve statements, track rewards, and provide contextual spending analysis. This agent supports **higher-tier conversational banking** use cases.

### Role in System

- **Primary Use:** Credit card balance, transactions, statements, limits, and rewards queries
- **Interface:** I-05 Card Management API (mocked for development)
- **User Intents:** "What's my credit card balance?", "Show my rewards", "How much did I spend?"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `getCreditCardBalance` | Get current balance and available credit | `cardId` | `balance`, `card`, `voiceResponse` |
| `getCreditCardTransactions` | Get transactions with filtering | `cardId`, `startDate?`, `endDate?`, `category?`, `limit?` | `transactions`, `summary`, `voiceResponse` |
| `getCreditCardStatement` | Get statement for a period | `cardId`, `period?` (YYYY-MM) | `statement`, `voiceResponse` |
| `getCreditCardLimit` | Get credit limit and utilization | `cardId` | `limit`, `utilizationPercentage`, `voiceResponse` |
| `getCreditCardRewards` | Get rewards balance and info | `cardId` | `rewards`, `redemptionOptions`, `voiceResponse` |

### Tool Usage Examples

```
getCreditCardBalance { cardId: "card-gold-4821" }
→ { success: true, balance: { currentBalance: 1245.50, availableCredit: 8754.50 }, 
    voiceResponse: "Your Gold MasterCard ending in 4821 has a current balance of €1,246..." }

getCreditCardTransactions { cardId: "card-gold-4821", category: "RESTAURANTS", limit: 5 }
→ { success: true, transactions: [...], summary: { totalTransactions: 5, purchaseTotal: 145.50 } }

getCreditCardRewards { cardId: "card-gold-4821" }
→ { success: true, rewards: { programName: "Miles & More", currentBalance: 12450, earnedThisMonth: 845 } }
```

---

## Problem Statement

### Business Problem
Credit card customers need:
- Quick access to balance and available credit
- Transaction history with spending insights
- Rewards tracking and expiration awareness
- Credit limit utilization visibility

Voice banking enables hands-free access to this information while driving, cooking, or multitasking.

### Technical Problem
Need to:
- Integrate with Card Management System (I-05)
- Mask card numbers for security (show only last 4 digits)
- Format responses for natural voice delivery
- Support filtering and pagination for transaction queries
- Calculate spending insights from transaction data

### FR Coverage
- **FR-009:** Credit Card Activity Inquiries ✅

---

## Solution Approach

### Architecture Pattern
```
User Query ("What's my credit card balance?")
         │
         ▼
CreditCardContextAgent
    │
    ├── CardManagementClient.getBalance()
    │       └── Retrieve balance from Card Management API
    │
    ├── CreditCardService.formatBalanceForVoice()
    │       └── Format response for natural speech
    │
    └── Return structured response with voiceResponse
```

### Key Design Decisions

1. **Card Number Masking:** All card numbers displayed as `****-****-****-XXXX` format
2. **Voice Response Formatting:** Amounts rounded to nearest euro, dates in natural language
3. **Category Classification:** Merchant categorization using name patterns (ML-based in production)
4. **Pagination:** Max 50 transactions per request to prevent overload

---

## Dependencies

### Internal
- `CreditCardService` — Business logic and formatting
- `CardManagementClient` — Integration with Card Management API

### External
- Card Management API I-05 (mocked via `MockCardManagementClient`)

### Package Structure
```
creditcard/
├── CreditCardContextAgent.java           # Main agent
├── domain/
│   ├── CreditCard.java                   # Card model with masking
│   ├── CreditCardBalance.java            # Balance model
│   ├── CreditCardTransaction.java        # Transaction model
│   ├── CreditCardStatement.java          # Statement model
│   ├── CreditCardLimit.java              # Limit model
│   ├── CreditCardRewards.java            # Rewards model
│   └── SpendingCategory.java             # Category enum
├── integration/
│   ├── CardManagementClient.java         # Interface
│   └── MockCardManagementClient.java     # Mock implementation
└── service/
    └── CreditCardService.java            # Business logic
```

---

## Security Considerations

### Card Number Masking
- Full card numbers are NEVER stored or transmitted in responses
- All card numbers display as `****-****-****-XXXX`
- `CreditCard.maskCardNumber()` utility for any number masking needs

### Logging
- Card IDs are masked in logs using `maskCardId()` method
- Transaction amounts are NOT logged to prevent financial data exposure
- Only metadata (counts, date ranges) are logged

### PII Protection
- Cardholder names are included only in card details, never logged
- Customer IDs are used for validation but not exposed in voice responses

---

## Mock Data

The `MockCardManagementClient` provides realistic test data:

### Sample Cards
| Card ID | Type | Network | Status | Rewards |
|---------|------|---------|--------|---------|
| `card-gold-4821` | Gold | MasterCard | Active | Miles & More |
| `card-platinum-9456` | Platinum | Visa | Active | Cashback Plus |
| `card-standard-7123` | Standard | Visa | Active | Basic Rewards |

### Sample Transactions
- 10 transactions for Gold card (various categories)
- 5 transactions for Platinum card
- 3 transactions for Standard card

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real API Integration** | Uses mock client | High for production | P0 |
| **No Card Actions** | Cannot block/unblock cards | Medium | P1 |
| **No Spending Insights** | Advanced analytics pending | Low | P2 |
| **No Statement PDF** | Cannot generate PDF statements | Low | P3 |

---

## Testing

### Unit Tests
All tests in `CreditCardContextAgentTest.java`:
- Agent identity tests (ID, description, tools)
- Balance retrieval tests (success, errors, masking)
- Transaction tests (filtering, pagination, categories)
- Statement tests (period handling, totals)
- Limit tests (utilization, eligibility)
- Rewards tests (balance, expiration)
- Error handling tests
- Card number masking tests

### Running Tests
```bash
cd java/voice-banking-app
mvn test -Dtest=CreditCardContextAgentTest
```

---

## Usage

### From AgentRegistry
```java
@Autowired AgentRegistry registry;

Agent agent = registry.getAgent("credit-card-context").orElseThrow();
Map<String, Object> result = agent.executeTool("getCreditCardBalance", 
    Map.of("cardId", "card-gold-4821"));
```

### Direct Tool Execution
```java
Map<String, Object> result = registry.executeTool("getCreditCardBalance",
    Map.of("cardId", "card-gold-4821"));
```

---

## Changelog

See [CHANGELOG.md](./CHANGELOG.md) for version history.

---

## Related Documents

| Document | Purpose |
|----------|---------|
| [AGENT-006 Implementation Plan](../../../../../docs/implementation-plan/AGENT-006-credit-card-context.md) | Detailed implementation specification |
| [Voice Banking Interfaces](../../../../../docs/architecture/voice-banking-interfaces-2026.md) | API interface definitions |
| [Architecture](../../../../../docs/architecture/architecture.md) | System architecture overview |
