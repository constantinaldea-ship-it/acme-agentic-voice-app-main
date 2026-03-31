# ADR-FEE-001: Fee Schedule Data Preparation and RAG Strategy

> **Status:** Accepted  
> **Date:** 2026-02-21  
> **Owner:** Voice Banking Architecture Team  
> **Decision Drivers:** Fee accuracy, hallucination prevention, CES integration constraints, data freshness  
> **Related ADRs:**
> - [ADR-CES-001: REST API vs MCP Server](ADR-CES-001-rest-api-vs-mcp-server.md)
> - [ADR-CES-002: MCP Server Topology](ADR-CES-002-mcp-server-topology.md)
> - [ADR-CES-003: MCP Location Services Implementation](ADR-CES-003-mcp-location-services-implementation.md)

---

## Context

### Why Fee Schedule Knowledge for CES?

The Acme Bank Premium Banking voice concierge currently handles branch/ATM location queries
via the `location_services_agent`. Customers frequently ask about fees and pricing — a natural
complement to location queries and a high-value customer service interaction. Without structured
fee knowledge, the agent either:

1. Fabricates fee amounts from general training data (hallucination risk — high severity)
2. Deflects all fee questions to a live agent (poor CX)

Both outcomes are unacceptable for a production banking assistant.

### The Source Document

**Deutsche Bank "List of Prices and Services" (Preis- und Leistungsverzeichnis)**
- URL: https://www.deutsche-bank.de/dam/deutschebank/de/shared/pdf/kontakt-und-service/list-of-prices-and-Services-deutsche-bank-ag.pdf
- Format: PDF (~100+ pages)
- Contents: All account fees, transaction charges, card fees, service costs, waiver conditions, footnotes, pricing tiers for retail and private banking customers
- Update frequency: Periodic (typically aligned with regulatory or commercial changes)
- Language: German (primary) with some English sections

### Key Risks

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Fee hallucination (wrong amounts) | 🔴 Critical | Tool-grounded responses only; strict constraint in agent instruction |
| Stale data (outdated fees) | 🟡 Medium | Version-tracked data store; mention effective date in responses |
| Incomplete extraction (missed footnotes) | 🟡 Medium | Track 2 (structured extraction) captures footnotes explicitly |
| PDF parsing errors | 🟡 Medium | Manual review of ingested content during Track 1 setup |

---

## Decision: Two-Track Approach

### Track 1 (Primary — CES Native): Google Cloud Data Store via Vertex AI Search

**Status:** PoC-01 — Active  
**Timeline:** Immediate

The first and primary implementation uses CES's native `googleSearchTool` capability with a
Vertex AI Search data store. This approach:

1. Requires zero custom backend code
2. Uses CES's built-in tool integration
3. Handles PDF ingestion natively via Vertex AI Search
4. Supports contextual search over document content

#### Architecture

```
Customer Voice Input
       ↓
  voice_banking_agent (root)
       ↓ (fee/pricing trigger)
  fee_schedule_agent
       ↓ (tool call)
  fee_schedule_lookup (googleSearchTool)
       ↓
  Vertex AI Search Data Store
       ↓
  Deutsche Bank List of Prices and Services PDF
       ↑
  GCS Bucket (source document storage)
```

#### Setup Steps

1. **Upload PDF to GCS:**
   ```
   gsutil cp list-of-prices-and-Services-deutsche-bank-ag.pdf \
     gs://voice-banking-poc-fee-schedule/
   ```

2. **Create Vertex AI Search Data Store:**
   - Navigate to: https://console.cloud.google.com/gen-app-builder/engines/create?project=voice-banking-poc
   - Create a new Search data store
   - Source: Cloud Storage → point to the GCS bucket
   - Type: Unstructured documents (PDF)
   - Note the `DATA_STORE_ID` for the next step

3. **Configure the tool:**
   The current `fee_schedule_lookup.json` uses `contextUrls` pointing to the public PDF URL.
   Once the Vertex AI data store is configured, update to use the data store reference:
   ```json
   {
     "googleSearchTool": {
       "name": "fee_schedule_lookup",
       "dataStoreId": "projects/voice-banking-poc/locations/global/collections/default_collection/dataStores/DATA_STORE_ID"
     },
     "displayName": "fee_schedule_lookup"
   }
   ```

#### CES Integration Pattern

The `fee_schedule_lookup` tool uses the `googleSearchTool` format (same as `lookup_plant_details`
in the sample app), declared in `tools/fee_schedule_lookup/fee_schedule_lookup.json` and
referenced in the `fee_schedule_agent`'s `tools[]` array.

**Important CES Constraint (L-01):** `toolCall` expectations in golden evaluations cannot
reference `googleSearchTool` operations. Use `agentResponse` expectations instead.

### Track 2 (Deferred — Custom RAG): OpenAPI Toolset to Retrieval Service

**Status:** Deferred — not yet scheduled  
**Rationale:** Track 1 provides sufficient accuracy for initial PoC validation. Track 2 is
warranted if Track 1 fails to meet numeric accuracy requirements (see Success Metrics below).

#### Architecture

```
fee_schedule_agent
       ↓ (OpenAPI toolset call)
  fee-retrieval-service (Spring Boot)
       ↓
  Document AI extraction pipeline
       ↓
  BigQuery structured fee table
       ↑
  Deutsche Bank PDF (processed)
```

#### Implementation Plan (Deferred)

1. **Document AI extraction:** Use Google Document AI to extract fee tables from the PDF
   into structured JSON (fee name, amount, currency, conditions, footnotes, effective date)
2. **BigQuery table:** Store extracted fee data in a structured BQ table with versioning
3. **Fee retrieval API:** Spring Boot REST endpoint with OpenAPI spec, returning fee records
   by service type, account type, or keyword
4. **CES toolset:** OpenAPI toolset following the `location` toolset pattern, referencing
   the fee retrieval API
5. **Migration:** Replace `googleSearchTool` with the OpenAPI toolset in `fee_schedule_agent.json`

#### Why Deferred?

- Track 2 requires significant extraction engineering (Document AI pipeline, BQ schema design)
- Track 1 can be validated in hours vs. weeks for Track 2
- Vertex AI Search handles multi-language (DE/EN) natively
- Track 1 allows earlier customer feedback to inform Track 2 requirements

---

## CES Integration Patterns Used

| Pattern | Reference | Usage |
|---------|-----------|-------|
| Sub-agent architecture | `location_services_agent` | `fee_schedule_agent` follows the same agent JSON structure |
| Root agent routing | `voice_banking_agent/instruction.txt` | Added fee routing subtask following location routing pattern |
| Direct tool declaration | `tools[]` in agent JSON | `fee_schedule_lookup` declared as direct tool |
| `googleSearchTool` | `sample/Sample_app_2026-02-07-162736/tools/lookup_plant_details/` | `fee_schedule_lookup.json` follows this exact format |
| Evaluation patterns | `evaluations-howto.md` | Goldens use `agentTransfer` + `agentResponse`; scenario uses `scenarioExpectations: []` per L-01, G-02 |

---

## Alternatives Considered

### A1: Inline Knowledge in Agent Instruction

Embed fee data directly in the `fee_schedule_agent` instruction as few-shot examples or a
static table.

**Rejected because:**
- Fees change periodically; updating instruction requires redeployment
- Token limits constrain how much fee data can be embedded
- Highest hallucination risk (model may interpolate between embedded values)

### A2: Python Function Tool with Static JSON

A Python function tool in `tools/` returning hardcoded fee data from a JSON file.

**Rejected because:**
- Same staleness problem as A1
- More complex than `googleSearchTool` for initial PoC

### A3: Vertex AI Agent Builder Native Data Store (Without GCS)

Upload PDF directly in Vertex AI Agent Builder UI without GCS intermediate step.

**Viable alternative — not selected** because GCS provides an auditable, version-controlled
source of truth for the PDF. GCS → Vertex AI data store is the recommended production pattern.

---

## Success Metrics

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Fee amount accuracy | ≥95% correct EUR amounts vs. source PDF | Manual audit of 20 sampled fee queries |
| Citation correctness | 100% of fee responses cite "List of Prices and Services" | Inspection of `agentResponse` in CES simulator |
| Hallucination rate | 0% fabricated fees (tool not called) | Scenario evaluation with hallucination metric enabled |
| German language correctness | ≥90% DE queries answered in German | German routing evaluation (`fee_routing_german`) |
| Response latency (voice) | ≤3 seconds end-to-end | CES conversation logging |

---

## Evaluation Strategy

Per CES constraints documented in `evaluations-howto.md`:

### Golden Evaluations (Structure Tests)
- `fee_lookup_basic`: Verify routing to `fee_schedule_agent` + `agentResponse` with fee amount
- `fee_routing_german`: Verify German query routes correctly + German response

**CES Constraint L-01:** Golden `toolCall` expectations cannot reference `googleSearchTool`
operations. All `fee_schedule_lookup` verification uses `agentResponse` expectations.

### Scenario Evaluations (Behaviour Tests)
- `fee_schedule_multi_turn`: Multi-turn fee query conversation (3 turns, 6 maxTurns limit)
  - `scenarioExpectations: []` per G-02 (cannot mock search tool responses)
  - Verification criteria in `task` string: citation, no fabrication, German language, conditions

---

## Reprioritized PoC Backlog

| Priority | Item | Track | Status |
|----------|------|-------|--------|
| PoC-01 | GCS upload + Vertex AI data store setup | Track 1 | ✅ Done — PDF in `gs://voice-banking-poc-fee-schedule/`, data store `fee-schedule-lookup_1771833548805` |
| PoC-02 | Deploy `fee_schedule_agent` via ZIP import | Track 1 | ✅ Code ready — agent, tool, instruction, evaluations committed. Deploy when data store is live and update `fee_schedule_lookup.json` with `dataStoreId`. |
| PoC-03 | Validate fee accuracy (manual audit, 20 queries) | Track 1 | 🟡 Pending (post-deploy) |
| PoC-04 | Enable hallucination metric in `app.json` | Track 1 | ✅ Done — `evaluationMetricsThresholds` with `hallucinationMetricBehavior: ENABLED` added |
| PoC-05 | Document AI extraction pipeline | Track 2 | ⏸️ Deferred |
| PoC-06 | BigQuery structured fee table | Track 2 | ⏸️ Deferred |
| PoC-07 | Fee retrieval Spring Boot service | Track 2 | ⏸️ Deferred |
| PoC-08 | OpenAPI toolset migration | Track 2 | ⏸️ Deferred |

---

## Consequences

### Positive
- Grounded fee responses — zero hallucination for covered fee topics
- No custom backend code required for Track 1
- Bilingual (EN/DE) handled natively by Vertex AI Search
- Agent architecture follows established patterns (reuses location_services_agent pattern)

### Negative / Trade-offs
- Track 1 search quality depends on Vertex AI Search's PDF parsing accuracy — footnotes and
  complex table structures may not be captured perfectly
- No deterministic test coverage for fee amounts in CES evaluations (L-01 constraint)
- Track 2 requires a multi-week engineering effort to deliver higher numeric precision
- Data store must be manually updated when Deutsche Bank publishes a new price list version

### Neutral
- A `README.md` in `tools/fee_schedule_lookup/` documents the Track 1 → Track 2 migration path
