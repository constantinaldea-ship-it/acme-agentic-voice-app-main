# fee_schedule_lookup Tool

> **Track:** Track 1 (CES Native — `googleSearchTool`)  
> **ADR:** [ADR-FEE-001](../../docs/adr/ADR-FEE-001-fee-schedule-data-prep-and-rag.md)  
> **Status:** PoC-01 Active

## Overview

This tool provides the `fee_schedule_agent` with access to Acme Bank's official fee schedule
(Deutsche Bank "List of Prices and Services" / Preis- und Leistungsverzeichnis).

### Current Implementation (Track 1)

Uses CES's native `googleSearchTool` with `contextUrls` pointing to the public PDF URL.
This requires **zero custom backend code**.

```
fee_schedule_agent
       ↓ (tool call)
  fee_schedule_lookup (googleSearchTool)
       ↓
  Vertex AI Search → Deutsche Bank PDF
```

### Tool JSON

```json
{
  "googleSearchTool": {
    "name": "fee_schedule_lookup",
    "description": "Searches the official Deutsche Bank List of Prices and Services...",
    "contextUrls": ["https://www.deutsche-bank.de/dam/...pdf"]
  },
  "displayName": "fee_schedule_lookup"
}
```

## Migration to Vertex AI Data Store (PoC-01 → PoC-02)

Once the GCS bucket and Vertex AI Search data store are provisioned, update
`fee_schedule_lookup.json` to reference the data store instead of `contextUrls`:

### Step 1: Upload PDF to GCS

```bash
gsutil cp list-of-prices-and-Services-deutsche-bank-ag.pdf \
  gs://voice-banking-poc-fee-schedule/
```

### Step 2: Create Vertex AI Search Data Store

1. Navigate to: https://console.cloud.google.com/gen-app-builder/engines/create?project=voice-banking-poc
2. Create a Search data store → Source: Cloud Storage → `gs://voice-banking-poc-fee-schedule/`
3. Type: Unstructured documents (PDF)

**Completed:** Data store ID is `fee-schedule-lookup_1771833548805` (created 2026-02-23).

### Step 3: Update Tool JSON

✅ **Done.** Tool JSON now uses the data store reference:

```json
{
  "googleSearchTool": {
    "name": "fee_schedule_lookup",
    "description": "Searches the official Deutsche Bank List of Prices and Services...",
    "dataStoreId": "projects/voice-banking-poc/locations/global/collections/default_collection/dataStores/fee-schedule-lookup_1771833548805"
  },
  "displayName": "fee_schedule_lookup"
}
```

## Migration to Track 2 (Custom RAG — Deferred)

Track 2 replaces the `googleSearchTool` with an OpenAPI toolset backed by a Spring Boot
fee-retrieval service and BigQuery structured fee table. See ADR-FEE-001 for details.

When Track 2 is implemented:

1. Remove this tool file (`fee_schedule_lookup.json`)
2. Add a new toolset entry in `fee_schedule_agent.json` following the `location` toolset pattern
3. Update the agent instruction to reference the new tool names

## CES Constraints

- **L-01:** `toolCall` expectations in golden evaluations cannot reference `googleSearchTool`
  operations. All evaluations use `agentResponse` expectations instead.
- **G-02:** Scenario evaluations use `scenarioExpectations: []` because search tool responses
  cannot be mocked in scenarios.

## Evaluation Coverage

| Evaluation | Type | Validates |
|------------|------|-----------|
| `fee_lookup_basic` | Golden | EN routing + citation in response |
| `fee_routing_german` | Golden | DE routing + German citation |
| `fee_schedule_multi_turn` | Scenario | 3-turn conversation, no fabrication, citation |
