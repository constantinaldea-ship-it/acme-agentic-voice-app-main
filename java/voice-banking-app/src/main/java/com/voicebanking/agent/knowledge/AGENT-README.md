# KnowledgeCompilerAgent

> **Agent ID:** `knowledge-compiler`  
> **Package:** `com.voicebanking.agent.knowledge`  
> **Status:** ✅ Implemented  
> **Category:** Category 2 — Voice-Enabled Context-Aware Banking (Read)  
> **Priority:** 🔴 P1 Required  
> **Implementation Plan:** [AGENT-003-knowledge-compiler.md](../../../../../docs/implementation-plan/AGENT-003-knowledge-compiler.md)

---

## Agent Description

The **KnowledgeCompilerAgent** is the primary knowledge retrieval engine for the AI Banking Voice system. It compiles and retrieves structured knowledge from the Knowledge Base, FAQs, and bank documentation sources. This agent handles the **highest volume intents** identified in the business discovery — general information queries about the bank, products, and services.

### Role in System

- **Primary Use:** FAQ lookups, bank information, app guidance
- **Interface:** I-19 Knowledge Base API
- **User Intents:** "What is the BIC code?", "How do I transfer money in the app?", "What are your opening hours?"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `getKnowledge` | General knowledge search | `query`, `category`, `maxResults`, `format` | `found`, `response`, `resultCount`, `topResult` |
| `getBankInfo` | Bank-specific info (BIC, hours, contact) | `infoType`, `value`, `format` | `found`, `response`, type-specific data |
| `searchFAQ` | Search frequently asked questions | `question`, `maxResults`, `format` | `found`, `response`, `faqCount`, `topics` |
| `getAppGuidance` | Mobile app usage guidance | `task`, `platform`, `format` | `found`, `response`, `guidance`, `platform` |

### Tool Usage Examples

```
getKnowledge { query: "account fees", category: "PRODUCTS", maxResults: 3 }
→ { found: true, response: "Account fees vary by account type...", resultCount: 3 }

getBankInfo { infoType: "bic", value: "ACMEDEXX" }
→ { found: true, bicCode: "ACMEDEXX", valid: true, response: "The BIC code ACMEDEXX is valid..." }

searchFAQ { question: "how to reset my PIN" }
→ { found: true, faqCount: 2, topics: ["PIN reset", "Card security"], topAnswer: {...} }

getAppGuidance { task: "check balance", platform: "ios" }
→ { found: true, platform: "ios", guidance: "To check your balance on iOS..." }
```

---

## Problem Statement

### Business Problem
Customers frequently ask general information questions:
- Bank contact details and BIC codes
- Product fees and features
- How to use mobile app features
- Branch opening hours

These are **high-volume, low-complexity queries** that don't require account access but do require accurate, up-to-date knowledge.

### Technical Problem
Need to:
- Store and index knowledge articles
- Perform semantic search for query matching
- Format responses for voice output
- Handle partial matches and suggest related topics
- Support multiple output formats (voice, text, detailed)

### FR Coverage
- **FR-001:** General Public Information ✅
- **FR-008:** Mobile App Usage Guidance (partial, via getAppGuidance) ✅

---

## Solution Approach

### Architecture Pattern
```
User Query
    │
    ▼
KnowledgeCompilerAgent
    │
    ├── SemanticSearchService
    │       ├── Keyword matching
    │       ├── Category filtering
    │       └── Score ranking
    │
    └── KnowledgeFormatter
            ├── Voice format (concise)
            ├── Text format (detailed)
            └── Summary extraction
```

### Key Design Decisions

1. **Knowledge Categories:**
   - `GENERAL_INFO` — Bank facts, contact info
   - `PRODUCTS` — Account and card products
   - `FEES` — Pricing information
   - `BRANCH_INFO` — Branch details
   - `APP_GUIDANCE` — Mobile app how-to
   - `FAQ` — Frequently asked questions

2. **Search Strategy:**
   - Semantic search with confidence scoring
   - Fallback to broader search if no results
   - Minimum threshold (0.3) for relevance

3. **Output Formats:**
   - `VOICE` — Concise, speakable format
   - `TEXT` — Full text for screen
   - `DETAILED` — Complete article

4. **BIC Code Special Handling:** Direct lookup for BIC validation with known codes (ACMEDEXX, ACMEDEFF).

---

## Dependencies

### Internal
- `SemanticSearchService` — Search and ranking
- `KnowledgeFormatter` — Output formatting
- `KnowledgeArticle`, `SearchResult` — Domain models

### External
- Knowledge Base (mocked with static articles)

### Package Structure
```
knowledge/
├── KnowledgeCompilerAgent.java       # Main agent (339 lines)
├── client/
│   └── KnowledgeBaseClient.java      # External KB client
├── domain/
│   ├── FormattedKnowledge.java       # Formatted output
│   ├── KnowledgeArticle.java         # Article model
│   ├── KnowledgeCategory.java        # Category enum
│   ├── KnowledgeQuery.java           # Query parameters
│   └── SearchResult.java             # Search result
└── service/
    ├── KnowledgeFormatter.java       # Formatting logic
    └── SemanticSearchService.java    # Search logic
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real Knowledge Base** | Uses mock articles | High for production | P0 |
| **No Vector Embeddings** | Uses keyword matching, not true semantic search | Medium | P2 |
| **No Knowledge Updates** | Static content, no CMS integration | Medium | P2 |
| **Missing `getProductInfo` Tool** | Plan mentioned but not implemented | Medium | P2 |
| **No Caching** | Every query searches from scratch | Low | P3 |
| **No Analytics** | Cannot track popular queries | Low | P3 |
| **Limited Article Corpus** | Small test dataset | High for production | P0 |

### Comparison to Implementation Plan

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `getKnowledge` | ✅ Implemented | General search |
| `getProductInfo` | ⚠️ Partial | Merged into getBankInfo/getKnowledge |
| `getBankInfo` | ✅ Implemented | BIC, hours, contact |
| `searchFAQ` | ✅ Implemented | FAQ search with fallback |
| `getAppGuidance` | ✅ Implemented | Mobile app guidance |

**4 of 5 tools implemented.** `getProductInfo` functionality available but not as dedicated tool.

---

## Alternative Approaches

### Current: Agent with Search Service
```
KnowledgeCompilerAgent
    └── SemanticSearchService
            └── In-memory article index
```

### Alternative 1: RAG Pattern (Retrieval-Augmented Generation)
Use LLM with retrieval:
```
User Query
    → Embed query
    → Vector search in Knowledge DB
    → Retrieve top-k documents
    → LLM synthesizes answer from documents
```

**Pros:**
- Better semantic understanding
- Can synthesize from multiple sources
- Handles novel questions better

**Cons:**
- Requires vector database infrastructure
- LLM latency for voice responses
- Cost per query

### Alternative 2: Specialized Sub-Agents
Split into domain-specific agents:
```
FAQAgent          → searchFAQ
ProductInfoAgent  → getProductInfo
BankInfoAgent     → getBankInfo
AppGuidanceAgent  → getAppGuidance
```

**Pros:**
- Smaller, focused agents
- Easier to update independently

**Cons:**
- More agents to manage
- Duplicated search infrastructure
- Harder orchestration

### Alternative 3: Skills Pattern
```
KnowledgeSearchSkill.search(query, category) → Results
FAQMatchingSkill.match(question) → FAQs
BankInfoSkill.lookup(infoType) → Info
AppGuidanceSkill.getGuide(task, platform) → Guide
FormattingSkill.format(results, format) → FormattedOutput
```

**Pros:**
- Skills reusable across agents
- Fine-grained testing
- Composable

**Cons:**
- Knowledge domain is cohesive
- Skills would share data source anyway
- Extra abstraction layer

### Recommendation

1. **Short-term:** Keep current Agent pattern, add `getProductInfo` tool
2. **Medium-term:** Migrate to RAG pattern when:
   - Knowledge corpus grows (>1000 articles)
   - Query accuracy becomes critical
   - Vector DB infrastructure available

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | RAG Pattern | Skills Pattern |
|-----------|------------------------|-------------|----------------|
| Accuracy | 🟡 Keyword matching | ✅ Semantic | 🟡 Depends on impl |
| Latency | ✅ Fast | 🟡 LLM delay | ✅ Fast |
| Scalability | 🟡 In-memory limits | ✅ Vector DB | 🟡 Same as agent |
| Complexity | ✅ Simple | ⚠️ Infrastructure | 🟡 More classes |
| Maintenance | ✅ Easy | 🟡 Embedding updates | 🟡 Skill management |

### Granularity Assessment

**Current State:** **Appropriate granularity**
- 4 tools covering distinct knowledge types
- High cohesion (all about knowledge retrieval)
- Single data source (Knowledge Base)

**Observation:** Could benefit from `getProductInfo` as dedicated tool for:
- Credit card products
- Account types
- Fee schedules

---

## Testing

### Unit Tests
- `KnowledgeCompilerAgentTest.java`
- `SemanticSearchServiceTest.java`

### Test Categories
```
1. General knowledge queries
2. BIC code validation (valid/invalid)
3. FAQ search with results
4. FAQ search with fallback
5. App guidance by platform
6. No results handling
7. Output format variations
```

### Golden Test Cases
```
1. "What is Acme Bank's BIC code?" → ACMEDEXX/ACMEDEFF
2. "How do I transfer money in the app?" → App guidance
3. "What are account fees?" → Product/fee knowledge
4. "Opening hours" → Bank info
5. "Gibberish query" → No results with helpful message
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AGENT-003 Implementation Plan](../../../../../docs/implementation-plan/AGENT-003-knowledge-compiler.md)
- [FR-001 General Public Information](../../../../../docs/functional-requirements/fr-001-general-public-information.md)
- [FR-008 Mobile App Usage Guidance](../../../../../docs/functional-requirements/fr-008-mobile-app-usage-guidance.md)
- [ProductInformationAgent](../../../../../docs/implementation-plan/AGENT-004-product-information.md) — Related, handles product details

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
