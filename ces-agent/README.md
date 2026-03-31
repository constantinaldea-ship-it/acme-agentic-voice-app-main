# CES Agent - Multi-Agent Architecture

> **Status:** ✅ Implemented  
> **Last Updated:** 2026-02-02  
> **Architecture Pattern:** Multi-Agent with Root Orchestrator  

---

## Overview

This directory contains the Google Customer Engagement Suite (CES) / Dialogflow CX configuration for the Voice Banking Assistant. The implementation follows a **multi-agent architecture** where:

1. **Root Orchestrator Agent** - Handles intent classification, session management, and routing
2. **PersonalFinance Specialized Agent** - Handles all personal finance analysis operations

This mirrors the Java Spring Boot implementation pattern where `OrchestratorService` routes to specialized agents like `PersonalFinanceManagementAgent`.

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         CES MULTI-AGENT ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│   User Voice Input                                                               │
│        │                                                                         │
│        ▼                                                                         │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │              ROOT ORCHESTRATOR AGENT                                     │   │
│   │              (voice-banking-orchestrator)                                │   │
│   │                                                                          │   │
│   │   • Session initialization & authentication context                      │   │
│   │   • Consent collection & verification                                    │   │
│   │   • Intent classification                                                │   │
│   │   • Route to specialized agents or local flows                           │   │
│   │                                                                          │   │
│   │   LOCAL FLOWS:                    AGENT ROUTING:                         │   │
│   │   ├─ Banking Flow                 ├─► PersonalFinance Agent              │   │
│   │   │  (balance, transactions)      │   (spending, budget, trends)         │   │
│   │   ├─ Knowledge Flow               └─► [Future: More Agents]              │   │
│   │   │  (FAQ, products, branches)                                           │   │
│   │   └─ Handover Flow                                                       │   │
│   │      (human agent transfer)                                              │   │
│   │                                                                          │   │
│   └──────────────────────────┬──────────────────────────────────────────────┘   │
│                              │                                                   │
│              ┌───────────────┴───────────────┐                                   │
│              │  Agent Transfer (Handoff)     │                                   │
│              │  Context: userId, token,      │                                   │
│              │  consent, legitimation cache  │                                   │
│              └───────────────┬───────────────┘                                   │
│                              │                                                   │
│                              ▼                                                   │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │              PERSONAL FINANCE AGENT                                      │   │
│   │              (personal-finance-agent)                                    │   │
│   │                                                                          │   │
│   │   CAPABILITIES:                                                          │   │
│   │   ├─ Spending Analysis & Breakdown                                       │   │
│   │   ├─ Monthly Trend Analysis                                              │   │
│   │   ├─ Income/Expense Summary                                              │   │
│   │   ├─ Budget Status Tracking                                              │   │
│   │   ├─ Unusual Activity Detection                                          │   │
│   │   ├─ Recurring Transaction Analysis                                      │   │
│   │   └─ Top Merchants Analysis                                              │   │
│   │                                                                          │   │
│   │   POLICY ENFORCEMENT:                                                    │   │
│   │   • No financial advice (analysis only)                                  │   │
│   │   • Voice-first bilingual responses (DE/EN)                              │   │
│   │   • Disclaimer injection when needed                                     │   │
│   │                                                                          │   │
│   └──────────────────────────┬──────────────────────────────────────────────┘   │
│                              │                                                   │
│              ┌───────────────┴───────────────┐                                   │
│              │  Return to Root Agent         │                                   │
│              │  Context: analysis results,   │                                   │
│              │  returnReason, targetIntent   │                                   │
│              └───────────────┬───────────────┘                                   │
│                              │                                                   │
│                              ▼                                                   │
│   ┌─────────────────────────────────────────────────────────────────────────┐   │
│   │                    BFA WEBHOOK LAYER                                     │   │
│   │                    (Backend For Agents)                                  │   │
│   │                                                                          │   │
│   │   All fulfillment goes through single webhook:                           │   │
│   │   POST /webhook/fulfill                                                  │   │
│   │                                                                          │   │
│   │   Processing Pipeline:                                                   │   │
│   │   1. Parse WebhookRequest                                                │   │
│   │   2. Session Resolution (extract userId, load cache)                     │   │
│   │   3. Consent Gate (GDPR/TTDSG)                                          │   │
│   │   4. Legitimation Gate (AcmeLegi)                                       │   │
│   │   5. Intent Router → Handler dispatch                                    │   │
│   │   6. Backend Call (Core Banking, PFM)                                   │   │
│   │   7. Response Formatting (voice-first)                                   │   │
│   │   8. Audit Logging (PII redacted)                                       │   │
│   │   9. Return WebhookResponse                                              │   │
│   │                                                                          │   │
│   └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## Directory Structure

```
ces-agent/
├── agents/                         # Agent configurations
│   ├── root-orchestrator.yaml      # Root agent (main entry)
│   └── personal-finance-agent.yaml # PersonalFinance specialized agent
│
├── flows/                          # Dialogflow CX flows
│   ├── orchestrator-main.yaml      # Root agent main flow
│   ├── banking.yaml                # Core banking operations
│   ├── personal-finance.yaml       # Personal finance flow (legacy)
│   ├── handover.yaml               # Human agent handover
│   └── knowledge.yaml              # FAQ and product info
│
├── intents/                        # Intent definitions
│   └── personal-finance-intents.yaml  # PF agent intents
│
├── entities/                       # Entity types
│   ├── account-type.yaml           # Account types (checking, savings)
│   ├── time-period.yaml            # Time periods (today, last month)
│   ├── spending-category.yaml      # Spending categories
│   ├── analysis-type.yaml          # Analysis types
│   └── budget-period.yaml          # Budget periods
│
├── webhooks/                       # Webhook configurations
│   └── bfa-fulfillment.yaml        # BFA webhook with all handlers
│
├── pages/                          # (Empty - pages defined in flows)
├── agent.yaml                      # Legacy single-agent config
└── README.md                       # This file
```

---

## Agent Configurations

### Root Orchestrator Agent

**File:** `agents/root-orchestrator.yaml`

The root agent is responsible for:
- Session initialization and authentication context
- Consent collection (GDPR/TTDSG compliance)
- Intent classification and routing
- Multi-agent handoff coordination
- Post-agent return handling

**Local Intents (handled directly):**
- `intent.balance` → Banking Flow
- `intent.transactions` → Banking Flow
- `intent.accounts` → Banking Flow
- `intent.faq` → Knowledge Flow
- `intent.human_agent` → Handover Flow

**Routed Intents (to PersonalFinance agent):**
- `intent.spending_analysis`
- `intent.monthly_trend`
- `intent.income_expense`
- `intent.budget_status`
- `intent.unusual_activity`
- `intent.recurring_transactions`
- `intent.top_merchants`

### PersonalFinance Specialized Agent

**File:** `agents/personal-finance-agent.yaml`

The PersonalFinance agent provides:
- Spending analysis and category breakdown
- Monthly trend analysis
- Income/expense summaries
- Budget status tracking
- Unusual activity detection
- Recurring transaction analysis
- Top merchant analysis

**Key Features:**
- Voice-first bilingual responses (German/English)
- Policy enforcement (no financial advice)
- Automatic disclaimer injection
- Return triggers for banking operations or handover

---

## Handoff Protocol

### Root → PersonalFinance Handoff

```yaml
handoff_context:
  - userId
  - correlationId
  - channel
  - consentStatus
  - userToken
  - legitimationCache
  - triggerIntent
  - accountId
```

### PersonalFinance → Root Return

```yaml
return_context:
  - returnReason: "BANKING_OPERATION" | "HANDOVER_REQUESTED" | "USER_DONE"
  - targetIntent: "intent.balance" | "intent.transactions" | null
  - handoverContext: "unusual_activity" | "budget_setup" | null
  - lastAnalysis: "getSpendingBreakdown" | "getBudgetStatus" | etc.
```

---

## BFA Webhook Integration

All fulfillment is routed through a single BFA webhook endpoint:

```
POST ${WEBHOOK_BASE_URL}/webhook/fulfill
```

### Personal Finance Fulfillment Tags

| Tag | Description | Java Tool Mapping |
|-----|-------------|-------------------|
| `pf_get_spending_breakdown` | Spending by category | `getSpendingBreakdown` |
| `pf_get_monthly_trend` | Month-over-month trends | `getMonthlyTrend` |
| `pf_get_income_expense_summary` | Cash flow summary | `getIncomeExpenseSummary` |
| `pf_get_budget_status` | Budget vs actual | `getBudgetStatus` |
| `pf_get_unusual_activity` | Anomaly detection | `getUnusualActivity` |
| `pf_get_recurring_transactions` | Recurring payments | `getRecurringTransactions` |
| `pf_get_top_merchants` | Top spending merchants | `getTopMerchants` |

---

## Deployment

### Prerequisites

1. Google Cloud project with CX Agent Studio enabled
2. BFA backend deployed and accessible
3. Credentials configured for the CES import flow
4. The full `acme_voice_agent/` package available locally

### Preferred Deploy Path

For this repository, the preferred day-to-day deployment path is the
state-aware incremental deploy manager:

- `ces-agent/scripts/deploy/ces-deploy-manager.py`

It validates the package, prints a deployment plan, applies only changed
toolsets, tools, and agents, and writes a per-run JSON artifact.

### Full-App Import Path

For full-app reset/import behavior, use:

- `ces-agent/scripts/deploy/deploy-agent.sh`

Do not import `ces-agent/acme_voice_agent/tools/` by itself. Some tools depend on
agent-level toolset attachment and package-level environment files.

For a brand-new CES app, bootstrap with `ces-agent/scripts/deploy/deploy-agent.sh --import`
first. After the app exists, use `ces-agent/scripts/deploy/ces-deploy-manager.py`
for incremental updates; it can create missing agents inside that existing app in
dependency order and preserves built-in CES tools such as `end_session`.

### Deploy Commands

```bash
# Preferred daily deploy path
cd ces-agent/scripts
python3 ./deploy/ces-deploy-manager.py --validate-only
python3 ./deploy/ces-deploy-manager.py

# Full-app ZIP import path
./deploy/deploy-agent.sh --import --location us

# Or package and follow the printed manual-import instructions
./deploy/deploy-agent.sh
```

Additional references:

- `scripts/deploy/README.md`
- `scripts/README.md`
- `test-harness/README.md`
- `test-harness/evaluation/README.md`
- `test-harness/smoke/README.md`
- `docs/smoke-tests/runtime-api-reference.md`
- `docs/smoke-tests/extending-smoke-suites.md`
- `docs/prompt-testing-strategy.md`
- `docs/cx-agent-studio/python-runtime-and-cloud-run-connectivity.md`

### Quality Evaluation Tests

Use the dedicated evaluation-test folder for reproducible CES quality runs over
root-agent and specialist-agent behavior:

```bash
cd ces-agent/test-harness/evaluation

python3 ces-evaluation-runner.py validate-suite \
  --suite ./suites/agent-quality-golden-suite.json

python3 ces-evaluation-runner.py run-suite \
  --suite ./suites/agent-quality-golden-suite.json

cd ..
./run-evaluation-tests.sh unit
```

The evaluation suites validate higher-level conversational quality through
`projects.locations.apps.runEvaluation`, capture run/result artifacts, and
enforce per-evaluation pass-rate thresholds in a repo-local JSON contract.
The runner auto-loads the repo `.env` plus `.tmp/cloud-run/discovery-plan.env`,
and defaults `GCP_LOCATION=us` / `CES_APP_ID=acme-voice-us` when they are not
already set.

### Runtime Smoke Tests

Use the dedicated smoke-test folder for post-import CES runtime validation:

```bash
cd ces-agent/test-harness/smoke

export GCP_PROJECT_ID=voice-banking-poc
export GCP_LOCATION=us
export CES_APP_ID=acme-voice-us
export CUSTOMER_DETAILS_API_KEY='YGCVjdxtq_FjDc1vKqnSpOZji6CTWd8BECVpdNyegGQ'

python3 ces-runtime-smoke.py run-suite \
  --suite ./suites/ces/customer-details-smoke-suite.json

cd ..
./run-smoke-tests.sh unit
```

The smoke suites validate deployed resource presence, direct tool health,
OpenAPI toolset contract integrity, and low-level CES runtime execution through
`projects.locations.apps.executeTool`.

---

## Related Documentation

- [ADR-J009: BFA-CES Architecture](../docs/adr/ADR-J009-bfa-ces-architecture.md)
- [PersonalFinanceManagementAgent (Java)](../java/voice-banking-app/src/main/java/com/voicebanking/agent/personalfinance/AGENT-README.md)
- [OrchestratorService (Java)](../java/voice-banking-app/src/main/java/com/voicebanking/orchestrator/)

### CES Agent Guides

- [Tool Selection Guide — OpenAPI vs Direct Tools vs MCP](docs/tool-selection-guide.md)
- [Evaluations How-To & DSL Reference](docs/evaluations-howto.md)
- [Resource Inventory](docs/resources.md)

---

## Changelog

### 2026-02-02

- Created multi-agent architecture with root orchestrator and PersonalFinance agent
- Implemented all personal finance intents (spending, budget, trends, unusual activity)
- Added entity definitions for spending categories, analysis types, budget periods
- Updated BFA webhook with personal finance fulfillment tags
- Added handoff protocol for agent-to-agent communication

---

*Created by  on 2026-02-02*
