# Fee Schedule Agent — Use Case Specification

> **Status:** Active  
> **Last Updated:** 2026-02-21  
> **Agent:** `fee_schedule_agent`  
> **Related ADR:** [ADR-FEE-001: Fee Schedule Data Preparation and RAG Strategy](../adr/ADR-FEE-001-fee-schedule-data-prep-and-rag.md)  
> **Source Document:** Deutsche Bank [List of Prices and Services](https://www.deutsche-bank.de/dam/deutschebank/de/shared/pdf/kontakt-und-service/list-of-prices-and-Services-deutsche-bank-ag.pdf)

---

## Use Case Description

The `fee_schedule_agent` is a CES sub-agent specialised in answering customer questions about
Deutsche Bank / Acme Bank fees, pricing tiers, conditions, waivers, and service charges. It is
triggered by fee-related keywords in any language (EN/DE) and responds with grounded,
tool-retrieved information from the official "List of Prices and Services."

**Scope:**
- ✅ Fee amounts (EUR) for specific services
- ✅ Monthly account maintenance fees by account type/tier
- ✅ Transaction fees (wire transfers, card payments, withdrawals)
- ✅ Waiver conditions and minimum balance requirements
- ✅ Fee comparisons across account tiers
- ✅ Bilingual responses (English and German)
- ❌ Branch/ATM locations (→ `location_services_agent`)
- ❌ Account balances or transaction history (→ `voice_banking_agent`)

---

## Agent Architecture

```
                    ┌────────────────────────┐
                    │   voice_banking_agent   │  (root agent)
                    │   (routing only)        │
                    └──────────┬─────────────┘
                               │
              ┌────────────────┴────────────────────┐
              │ Fee/pricing trigger                  │
              │ (fees, costs, Gebühren, Kosten...)   │
              ↓
    ┌─────────────────────┐
    │  fee_schedule_agent  │
    │  (fee specialist)    │
    └──────────┬──────────┘
               │
               │ tool call
               ↓
    ┌───────────────────────────────────────────────┐
    │  fee_schedule_lookup (googleSearchTool)        │
    │  → Vertex AI Search Data Store                │
    │  → Deutsche Bank List of Prices and Services  │
    └───────────────────────────────────────────────┘
```

---

## Conversation Flow Diagram

```
User: "What's the fee for a wire transfer?"
              │
              ▼
  voice_banking_agent detects fee keyword
              │
              ▼ [silent transfer]
  fee_schedule_agent receives query
              │
              ▼ [fee_schedule_lookup tool call]
  Vertex AI Search returns relevant passages
              │
              ▼
  fee_schedule_agent composes response
  ─────────────────────────────────────────
  "According to the current List of Prices
   and Services, a SEPA wire transfer via
   online banking costs 0.00 EUR. Branch-
   initiated transfers may incur a fee."
              │
              ▼ (if user says goodbye)
  end_session tool call
```

---

## Result Formatting Rules

| Query Type | Response Format |
|-----------|----------------|
| Single fee | Amount + currency (EUR) + key condition (1-2 sentences) |
| Tier comparison | Structured list: "Basic: X EUR / Premium: Y EUR / Private: Z EUR" |
| Waiver conditions | State the trigger condition: "Waived if monthly credit turnover ≥ X EUR" |
| No results found | Honest acknowledgment + offer to connect with advisor |
| Ambiguous query | Clarifying question: specify account type or service |

**Voice brevity rule:** All responses must fit within 2-3 spoken sentences for voice interaction.

**Citation rule:** ALWAYS prefix fee data with:
- EN: "According to the current List of Prices and Services..."
- DE: "Laut dem aktuellen Preis- und Leistungsverzeichnis..."

---

## Error Handling Strategies

| Scenario | Agent Behaviour |
|----------|----------------|
| Tool returns no results | "I wasn't able to find that specific fee in the current price list. Would you like me to connect you with an advisor?" |
| Ambiguous fee question (no account type) | Ask clarifying question before calling tool |
| Tool returns partial/incomplete data | State what was found, flag that additional conditions may apply |
| User asks about changes vs previous version | Acknowledge limitation; provide current data; suggest Deutsche Bank website |
| User asks non-fee question | Transfer back to `voice_banking_agent` without answering |
| User requests live agent | Call `end_session` with `session_escalated=true` |

---

## Data Store Configuration

### Track 1: Vertex AI Search (Current)

**Step 1: Upload PDF to GCS**

```bash
# Create bucket (if not exists)
gsutil mb -p voice-banking-poc gs://voice-banking-poc-fee-schedule

# Upload the fee schedule PDF
gsutil cp list-of-prices-and-Services-deutsche-bank-ag.pdf \
  gs://voice-banking-poc-fee-schedule/

# Verify
gsutil ls gs://voice-banking-poc-fee-schedule/
```

**Step 2: Create Vertex AI Search Data Store**

1. Go to: https://console.cloud.google.com/gen-app-builder/engines/create?project=voice-banking-poc
2. Select **Search** app type
3. Configure data store:
   - Name: `fee-schedule-datastore`
   - Data type: **Unstructured documents**
   - Source: **Cloud Storage** → `gs://voice-banking-poc-fee-schedule/*`
   - Enable **Document parsing** for PDF
4. Note the `DATA_STORE_ID` from the data store details page

**Step 3: Update Tool Configuration**

Update `ces-agent/acme_voice_agent/tools/fee_schedule_lookup/fee_schedule_lookup.json`:

```json
{
  "googleSearchTool": {
    "name": "fee_schedule_lookup",
    "description": "Searches the official Deutsche Bank List of Prices and Services...",
    "dataStoreId": "projects/voice-banking-poc/locations/global/collections/default_collection/dataStores/DATA_STORE_ID"
  },
  "displayName": "fee_schedule_lookup"
}
```

**Step 4: Deploy to CES**

Follow the preferred incremental deploy procedure in
`ces-agent/scripts/deploy/ces-deploy-manager.py`.
Use the ZIP import path only when a full-app reimport is required.

### Keeping Data Fresh

When Deutsche Bank publishes an updated price list:
1. Upload the new PDF to the same GCS bucket (overwrite or version the filename)
2. Trigger a re-index of the Vertex AI data store
3. Update the effective date reference in agent instruction if needed

---

## Track 2: Migration to Custom RAG with OpenAPI Toolset (Future)

When higher numeric precision is required (e.g., fee amounts failing accuracy targets), migrate
from `googleSearchTool` to a custom RAG backend:

1. **Document AI extraction:** Extract PDF fee tables into structured JSON
2. **BigQuery table:** Store fee records with versioning
3. **Fee retrieval API:** Spring Boot REST service with OpenAPI spec
4. **CES toolset:** Replace `tools/fee_schedule_lookup/fee_schedule_lookup.json` with an
   OpenAPI toolset referencing the new service, following the `toolsets/location/` pattern
5. **Agent update:** Add toolset reference in `fee_schedule_agent.json` and remove `tools[]` entry

See [ADR-FEE-001](../adr/ADR-FEE-001-fee-schedule-data-prep-and-rag.md) for full details.

---

## Testing and Evaluations

| Evaluation | Type | Tests |
|-----------|------|-------|
| `fee_lookup_basic` | Golden | Routing to `fee_schedule_agent` + fee response in English |
| `fee_routing_german` | Golden | German query routing + German response |
| `fee_schedule_multi_turn` | Scenario | 3-turn fee conversation: lookup → conditions → tier comparison |

**Known CES Constraint (L-01):** Golden `toolCall` expectations cannot reference
`googleSearchTool` operations. All fee tool verification uses `agentResponse` expectations.
See `evaluations-howto.md` for details.

---

## Related Files

| File | Purpose |
|------|---------|
| `agents/fee_schedule_agent/fee_schedule_agent.json` | Agent manifest |
| `agents/fee_schedule_agent/instruction.txt` | Agent instructions (role, constraints, taskflow, examples) |
| `tools/fee_schedule_lookup/fee_schedule_lookup.json` | googleSearchTool configuration |
| `agents/voice_banking_agent/voice_banking_agent.json` | Root agent (childAgents updated) |
| `agents/voice_banking_agent/instruction.txt` | Root agent routing (fee subtask added) |
| `global_instruction.txt` | Global constraint #10 (fee data accuracy) |
| `evaluations/fee_lookup_basic/` | Golden evaluation — basic fee lookup |
| `evaluations/fee_routing_german/` | Golden evaluation — German routing |
| `evaluations/fee_schedule_multi_turn/` | Scenario evaluation — multi-turn |
