# TextGeneratorAgent

> **Agent ID:** `text-generator`  
> **Package:** `com.voicebanking.agent.text`  
> **Status:** ✅ Implemented  
> **Category:** Category 0 — Ecosystem / Core Infrastructure  
> **Priority:** 🟡 P3 Enhancer  
> **Implementation Plan:** [AGENT-010-text-generator.md](../../../../../docs/implementation-plan/AGENT-010-text-generator.md)

---

## Agent Description

The **TextGeneratorAgent** synthesizes natural language responses from structured data returned by other agents. It ensures **consistent tone, voice formatting, and brand alignment** across all customer interactions. This agent transforms raw data into conversational, TTS-optimized responses.

### Role in System

- **Primary Use:** Cross-cutting response formatting for all agents
- **Critical For:** Voice output quality, brand consistency, accessibility
- **Consumers:** All other agents can delegate formatting to TextGeneratorAgent

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `generateResponse` | Generate response from template | `type`, `templateId`, `data`, `language`, `ssmlEnabled` | `FormattedResponse` with text and SSML |
| `formatForVoice` | Optimize text for TTS output | `text`, `language`, `ssmlEnabled` | Voice-optimized string |
| `formatCurrency` | Format amounts for speech | `amount`, `language`, `ssmlEnabled`, `conversationalNumbers` | "fünf Euro und zwanzig Cent" |
| `formatDate` | Format dates for speech | `date`, `language`, `useRelativeDates` | "heute", "gestern", "24. Januar 2026" |
| `formatAccountNumber` | Format account numbers (masked) | `accountNumber`, `language`, `ssmlEnabled` | "XXXX eins zwei drei vier" |

### Tool Usage Examples

```
formatCurrency { amount: 1234.56, language: "de", conversationalNumbers: true }
→ "eintausendzweihundertvierunddreißig Euro und sechsundfünfzig Cent"

formatDate { date: "2026-01-24", language: "de", useRelativeDates: true }
→ "heute"

formatAccountNumber { accountNumber: "DE89370400440532013000", language: "de" }
→ "XXXX drei null null null"

generateResponse { 
  type: "BALANCE", 
  templateId: "balance.single.de",
  data: { accountType: "Girokonto", balance: 2500.00, currency: "EUR" }
}
→ { text: "Ihr Girokonto-Kontostand beträgt zweitausendfünfhundert Euro.", ssml: "..." }
```

---

## Problem Statement

### Business Problem
Voice banking requires responses that are:
- **Speakable:** Optimized for TTS pronunciation
- **Consistent:** Same tone and style across all features
- **Brand-aligned:** Follows Acme Bank voice guidelines
- **Accessible:** Clear and understandable

Raw data from banking APIs is not suitable for direct voice output.

### Technical Problem
Need to:
- Convert structured data to natural language
- Handle German and English localization
- Format numbers, currencies, dates for speech
- Apply SSML markup for pronunciation control
- Enforce brand voice guidelines (prohibited phrases)

### FR Coverage
- **Cross-cutting:** All FRs benefit from consistent response generation ✅

---

## Solution Approach

### Architecture Pattern
```
Structured Data (from any agent)
         │
         ▼
TextGeneratorAgent
    │
    ├── TemplateEngine
    │       ├── Template registry
    │       ├── Variable substitution
    │       └── Conditional rendering
    │
    ├── Formatters
    │       ├── CurrencyFormatter
    │       ├── DateFormatter
    │       ├── AccountFormatter
    │       └── NumberFormatter
    │
    └── BrandEnforcer
            ├── Prohibited phrase detection
            └── Compliance warnings
```

### Key Design Decisions

1. **German-First Localization:**
   - Primary: German (`de`)
   - Secondary: English (`en`)
   - Number words in German: "einundzwanzig", "fünfhundert"

2. **Conversational Numbers:**
   - Below 20: "fünf Euro" (not "5 Euro")
   - Large numbers: "zweitausendfünfhundert" (2500)

3. **Relative Dates:**
   - heute (today), gestern (yesterday), morgen (tomorrow)
   - vorgestern (day before), übermorgen (day after)

4. **Account Number Masking:**
   - Last 4 digits only: "XXXX eins zwei drei vier"
   - Digit-by-digit for voice clarity

5. **Brand Enforcement:**
   - Prohibited: "kostenlos", "gratis", "garantiert", "risikofrei"
   - Returns warnings if brand violations detected

---

## Dependencies

### Internal
- `TemplateEngine` — Template rendering
- `CurrencyFormatter`, `DateFormatter`, `AccountFormatter`, `NumberFormatter` — Data formatters
- `BrandEnforcer` — Brand compliance checking

### External
- None (self-contained)

### Package Structure
```
text/
├── TextGeneratorAgent.java           # Main agent
├── BrandEnforcer.java                # Brand compliance
├── domain/
│   ├── FormattedResponse.java        # Output record
│   ├── ResponseContext.java          # Input context
│   ├── ResponseType.java             # Response type enum
│   └── VoiceOptions.java             # Formatting options
├── formatter/
│   ├── VoiceFormatter.java           # Base interface
│   ├── NumberFormatter.java          # Number → words
│   ├── CurrencyFormatter.java        # Currency formatting
│   ├── DateFormatter.java            # Date formatting
│   └── AccountFormatter.java         # Account formatting
└── template/
    ├── ResponseTemplate.java         # Template record
    └── TemplateEngine.java           # Template registry
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **Limited Template Library** | ~10 templates, need more for all FRs | Medium | P2 |
| **No External Template Source** | Templates hardcoded | Medium | P2 |
| **No SSML Pacing Control** | Fixed speaking rate | Low | P3 |
| **No Pronunciation Lexicon** | Cannot handle unusual words | Medium | P2 |
| **No Template Versioning** | Cannot A/B test templates | Low | P3 |
| **Limited Error Responses** | Need more error message templates | Medium | P2 |
| **No Response Length Control** | Cannot enforce max words | Low | P3 |

### Comparison to Implementation Plan

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `generateResponse` | ✅ Implemented | Template-based |
| `formatForVoice` | ✅ Implemented | Basic optimization |
| `formatCurrency` | ✅ Implemented | German/English, conversational |
| `formatDate` | ✅ Implemented | Relative dates support |
| `formatAccountNumber` | ✅ Implemented | Masked, digit-by-digit |

**All 5 planned tools implemented.** ✅

---

## Alternative Approaches

### Current: Agent Pattern with Formatters
```
TextGeneratorAgent
    ├── formatCurrency(amount, options)
    ├── formatDate(date, options)
    └── generateResponse(template, data)
```

### Alternative 1: LLM-Based Response Generation
Use LLM to generate responses:
```
generateResponse(context, constraints)
    → LLM generates natural language
    → BrandEnforcer validates
    → Return response
```

**Pros:**
- More natural, varied responses
- Handles edge cases better
- Less template maintenance

**Cons:**
- Latency for voice (LLM too slow)
- Cost per response
- Consistency harder to control
- Hallucination risk

### Alternative 2: Pure Skill Pattern
```
CurrencyFormattingSkill.format(amount, locale)
DateFormattingSkill.format(date, options)
NumberToWordsSkill.convert(number, locale)
TemplateRenderingSkill.render(template, data)
BrandCheckingSkill.check(text)
```

**Pros:**
- Fine-grained reusability
- Independent testing
- Composable

**Cons:**
- Many small classes
- TextGeneratorAgent already delegates to formatters
- Current structure is essentially skill-based internally

### Alternative 3: Static Utility Classes
No agent, just utility classes:
```
VoiceFomattingUtils.formatCurrency(amount, locale)
VoiceFomattingUtils.formatDate(date, options)
```

**Pros:**
- Simpler for formatting
- No agent overhead

**Cons:**
- Loses tool discoverability
- Cannot be called via Orchestrator
- Inconsistent with architecture

### Recommendation
**Keep Agent pattern** because:
1. Cross-cutting nature requires discoverability
2. Other agents may call via Orchestrator
3. Template management benefits from centralization
4. Current internal structure (formatters) is already skill-like

---

## Architectural Analysis

### Agent vs Skills Evaluation

| Criterion | Agent Pattern (Current) | Pure Skills | LLM-Based |
|-----------|------------------------|-------------|-----------|
| Latency | ✅ Fast | ✅ Fast | ⚠️ Slow |
| Consistency | ✅ Template-controlled | ✅ Deterministic | 🟡 Variable |
| Flexibility | 🟡 Template-bound | 🟡 Function-bound | ✅ High |
| Cost | ✅ Free | ✅ Free | ⚠️ Per-call |
| Testing | ✅ Deterministic | ✅ Easy | 🟡 Non-deterministic |
| Discoverability | ✅ AgentRegistry | 🟡 Manual | ✅ AgentRegistry |

### Granularity Assessment

**Current State:** **Appropriate granularity**
- 5 tools covering distinct formatting needs
- High cohesion (all about text generation)
- Internal decomposition via formatters is good

**Observation:** This agent is **cross-cutting infrastructure**:
- Other agents produce data
- TextGeneratorAgent makes it voice-ready
- Pattern: Data Agent → TextGeneratorAgent → Voice Output

### Is Agent Pattern Right Here?

**Yes**, because:
1. Other agents may need to invoke formatting dynamically
2. Tool discoverability is valuable for orchestration
3. Centralization ensures consistency

**Internal design is skill-like** (formatters), which is the right balance.

---

## Testing

### Unit Tests (109 tests total)
- `NumberFormatterTest.java` — 18 tests
- `CurrencyFormatterTest.java` — 13 tests
- `DateFormatterTest.java` — 16 tests
- `AccountFormatterTest.java` — 13 tests
- `BrandEnforcerTest.java` — 16 tests
- `TemplateEngineTest.java` — 13 tests
- `TextGeneratorAgentTest.java` — 20 tests

### Test Categories
```
1. German number formatting (1-20, 21-99, 100+, 1000+)
2. English number formatting
3. Currency with/without cents
4. Relative date detection (heute, gestern, etc.)
5. Account number masking
6. Brand violation detection
7. Template rendering with variables
8. SSML output generation
```

### Golden Test Cases
```
1. formatCurrency(5.00, "de") → "fünf Euro"
2. formatCurrency(21.50, "de") → "einundzwanzig Euro und fünfzig Cent"
3. formatDate(today, "de") → "heute"
4. formatAccountNumber("DE891234") → "XXXX eins zwei drei vier"
5. BrandEnforcer("Das ist kostenlos") → violation detected
```

---

## Related Documents

- [Agent Interface](../Agent.java)
- [AGENT-010 Implementation Plan](../../../../../docs/implementation-plan/AGENT-010-text-generator.md)
- [All Functional Requirements](../../../../../docs/functional-requirements/) — Cross-cutting impact
- [AGENT-ARCHITECTURE-MASTER](../../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md)

---

## Document Control

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-01-24 |  | Initial documentation |
