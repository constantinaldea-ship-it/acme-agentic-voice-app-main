# Voice Banking Assistant - Java Architecture

## Table of Contents

1. [Overview](#overview)
2. [Technology Stack](#technology-stack)
3. [Architecture Diagram](#architecture-diagram)
4. [Component Details](#component-details)
5. [Data Flow](#data-flow)
6. [Profile System](#profile-system)
7. [Security](#security)
8. [Testing Strategy](#testing-strategy)

---

## Overview

The Java implementation is a Spring Boot 3.3.x application providing a RESTful API for voice-driven banking inquiries. It uses Google Cloud services (Speech-to-Text, Vertex AI Gemini) for production deployments and stub adapters for local development.

**Key Design Principles:**
- **Profile-based configuration** - `local` (stubs) vs `cloud` (GCP)
- **Provider pattern** - Adapters for STT/LLM with multiple implementations
- **Policy-driven safety** - Tool allowlist, blocked intents, consent gates
- **Immutable domain** - Java records for Account, Balance, Transaction
- **Validation layers** - Bean Validation (simple) + Yavi (complex cross-field)

---

## Technology Stack

| Layer | Technology | Version | Purpose |
|-------|------------|---------|---------|
| **Runtime** | Java LTS | 21.0.9 | Language runtime |
| **Framework** | Spring Boot | 3.3.0 | Web framework, DI |
| **Build** | Maven | 3.9.12 | Dependency management |
| **Web** | Spring WebMVC | 6.1.8 | REST endpoints |
| **Validation** | Bean Validation | 3.x | Simple constraints |
| **Validation** | Yavi | 0.14.1 | Cross-field rules |
| **STT** | Google Cloud Speech | 4.x | Chirp2 model |
| **LLM** | Vertex AI Gemini | 1.x | Tool calling |
| **Testing** | JUnit 5 | 5.10.2 | Unit tests |
| **Testing** | AssertJ | 3.25.3 | Fluent assertions |
| **Testing** | Testcontainers | 1.19.7 | Integration tests |
| **Coverage** | JaCoCo | 0.8.11 | Code coverage |

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         Client                              │
│                  (React Frontend / curl)                    │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP POST /api/orchestrate
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                  OrchestratorController                     │
│              (@RestController, @CrossOrigin)                │
└────────────────────────────┬────────────────────────────────┘
                             │ validate(@Valid)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                   OrchestratorService                       │
│  ┌────────────┬──────────────┬──────────────┬─────────────┐│
│  │ 1. STT     │ 2. LLM       │ 3. Policy    │ 4. Tool     ││
│  │ (optional) │ (intent)     │ (allow/block)│ (execute)   ││
│  └────┬───────┴──────┬───────┴──────┬───────┴──────┬──────┘│
└───────┼──────────────┼──────────────┼──────────────┼───────┘
        │              │              │              │
        ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌────────────────────────┐
│ SttProvider  │ │ LlmProvider  │ │  PolicyGateService     │
├──────────────┤ ├──────────────┤ │  ToolRegistryService   │
│ - Stub       │ │ - Stub       │ │  MockBankingService    │
│ - Chirp2     │ │ - Gemini     │ └────────────────────────┘
└──────────────┘ └──────────────┘
```

---

## Component Details

### 1. Domain Layer

**Location:** `com.voicebanking.domain`

#### Account (Record)
```java
public record Account(
    @NotBlank String id,
    @NotNull AccountType type,
    @NotBlank String name,
    @Size(min = 3, max = 3) String currency,
    @Size(min = 4, max = 4) String lastFour
) {}
```

- Immutable
- Bean Validation constraints
- AccountType enum: CHECKING, SAVINGS, CARD

#### Balance (Record)
```java
public record Balance(
    String accountId,
    BigDecimal available,
    BigDecimal current,
    String currency,
    Instant asOf
) {}
```

#### Transaction (Record)
```java
public record Transaction(
    String id,
    String accountId,
    LocalDate date,
    String description,
    BigDecimal amount,
    String currency,
    TransactionType type
) {}
```

- TransactionType enum: DEBIT, CREDIT

### 2. DTO Layer

**Location:** `com.voicebanking.domain.dto`

#### OrchestratorRequest
```java
public record OrchestratorRequest(
    String audio,
    String text,
    String sessionId,
    Boolean consentAccepted
) {
    public static final Validator<OrchestratorRequest> VALIDATOR = 
        // Yavi: (audio XOR text) AND sessionId != null
}
```

**Validation:**
- Exactly one of `audio` or `text` required (Yavi cross-field)
- `sessionId` required

#### OrchestratorResponse
```java
public record OrchestratorResponse(
    String transcript,
    String intent,
    String toolCalled,
    Object toolResult,
    String responseText,
    String refusalReason
) {
    public static OrchestratorResponse success(...) { }
    public static OrchestratorResponse refusal(...) { }
}
```

**Factory methods:**
- `success()` - Normal flow
- `refusal()` - Blocked/rejected requests

### 3. Adapter Layer

**Location:** `com.voicebanking.adapter`

#### SttProvider Interface
```java
public interface SttProvider {
    TranscriptResponse transcribe(byte[] audio);
}
```

**Implementations:**
- `SttStubAdapter` - `@Profile("local")` - Deterministic stub
- `SttChirp2Adapter` - `@Profile("cloud")` - Google Cloud Speech

#### LlmProvider Interface
```java
public interface LlmProvider {
    LlmResponse process(String transcript, Map<String, Object> context);
}
```

**Implementations:**
- `LlmStubAdapter` - `@Profile("local")` - Pattern matching
- `LlmGeminiAdapter` - `@Profile("cloud")` - Vertex AI Gemini

**LlmResponse:**
```java
public enum ResponseType {
    TOOL_CALL, CLARIFICATION, REFUSAL, DIRECT_RESPONSE
}
```

### 4. Service Layer

**Location:** `com.voicebanking.service`

#### MockBankingService
- **Purpose:** In-memory banking data (accounts, balances, transactions)
- **Data:** 3 accounts, 3 balances, 15+ transactions
- **Methods:**
  - `getAllAccounts()`
  - `getAccountBalance(String accountId)`
  - `queryTransactions(String accountId, int limit, String filter)`

#### PolicyGateService
- **Purpose:** Safety/compliance enforcement
- **Rules:**
  - Allowlist: 4 tools (getBalance, listAccounts, queryTransactions, summarizeTransactions)
  - Blocked intents: transfer, payment, send_money, delete_account
- **Methods:**
  - `evaluate(String toolName, String intent, boolean consent)`

#### ToolRegistryService
- **Purpose:** Tool execution dispatcher
- **Tools:** Maps tool names → banking service methods
- **Methods:**
  - `executeTool(String toolName, Map<String, Object> input)`
  - Default accountId: `acc-checking-001`

#### OrchestratorService
- **Purpose:** Main request orchestration
- **Flow:**
  1. Validate request (Yavi)
  2. STT (if audio provided)
  3. LLM intent detection
  4. Policy gate check
  5. Tool execution
  6. Response assembly
  7. (Phase 2) Session history tracking via AdkSessionManager

#### AdkSessionManager (Phase 2)
- **Purpose:** Session/context management for multi-turn conversations
- **Features:**
  - In-memory session storage with TTL (30 minutes)
  - Conversation history (turns with transcript/intent/result)
  - User preferences (default accountId, language, etc.)
  - Thread-safe concurrent access
- **Replaces:** TypeScript packages/agent-memory module (see ADR-J003)
- **Methods:**
  - `createSession(sessionId)` - Create new session
  - `getOrCreateSession(sessionId)` - Retrieve or create
  - `addTurn(sessionId, turn)` - Add conversation turn
  - `getSessionHistory(sessionId)` - Retrieve history
  - `setPreference(sessionId, key, value)` - Set user preference
  - `cleanupExpiredSessions()` - Remove stale sessions

### 5. Controller Layer

**Location:** `com.voicebanking.controller`

#### OrchestratorController
```java
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OrchestratorController {
    @PostMapping("/orchestrate")
    public ResponseEntity<OrchestratorResponse> orchestrate(
        @Valid @RequestBody OrchestratorRequest request
    ) { }
}
```

#### AccountController
- `GET /api/accounts` - List all accounts
- `GET /api/accounts/{id}/balance` - Get account balance
- `GET /api/accounts/{id}/transactions` - Get transactions

#### HealthController
- `GET /api/health` - Health check (status, profile, timestamp)

---

## Data Flow

### Success Flow (Balance Inquiry)

```
1. POST /api/orchestrate
   {
     "text": "What is my balance?",
     "sessionId": "test-123",
     "consentAccepted": true
   }

2. OrchestratorService.process()
   ├─ Validate: audio XOR text ✓
   ├─ STT: skipped (text input)
   ├─ LLM: process("What is my balance?", {})
   │  └─> LlmResponse.toolCall("getBalance", {})
   ├─ PolicyGate: evaluate("getBalance", true)
   │  └─> PolicyResult.allowed()
   ├─ ToolRegistry: executeTool("getBalance", {})
   │  └─> MockBanking.getAccountBalance("acc-checking-001")
   │     └─> Balance(available=1250.50, ...)
   └─ Response: OrchestratorResponse.success(...)

3. HTTP 200 OK
   {
     "transcript": "What is my balance?",
     "intent": "I'll check your balance for you.",
     "toolCalled": "getBalance",
     "toolResult": { "accountId": "acc-checking-001", ... },
     "responseText": "Here is your balance information."
   }
```

### Refusal Flow (Transfer Request)

```
1. POST /api/orchestrate
   { "text": "Transfer $100 to savings", ... }

2. OrchestratorService.process()
   ├─ LLM: process("Transfer $100 to savings", {})
   │  └─> LlmResponse.refusal("Read-only demo")
   └─ Response: OrchestratorResponse.refusal(...)

3. HTTP 200 OK
   {
     "transcript": "Transfer $100 to savings",
     "refusalReason": "I can only provide read-only information..."
   }
```

---

## Profile System

### Local Profile (`application-local.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: local

voice-banking:
  stub:
    stt-latency-ms: 50
    llm-latency-ms: 100
    llm-confidence: 0.95
```

**Activated Beans:**
- `SttStubAdapter`
- `LlmStubAdapter`

**Use Case:** Local development, unit tests, CI/CD

### Cloud Profile (`application-cloud.yml`)

```yaml
spring:
  config:
    activate:
      on-profile: cloud

google:
  cloud:
    project-id: ${GOOGLE_CLOUD_PROJECT}
    location: us-central1

voice-banking:
  stt:
    model: chirp_2
    language-code: en-US
  llm:
    model: gemini-1.5-flash-002
    temperature: 0.7
```

**Activated Beans:**
- `SttChirp2Adapter`
- `LlmGeminiAdapter`

**Use Case:** Production, staging, cloud integration tests

**Environment Variables:**
- `GOOGLE_APPLICATION_CREDENTIALS` (required)
- `GOOGLE_CLOUD_PROJECT` (required)

---

## Security

### 1. Consent Gate
- All operations require `consentAccepted: true`
- Enforced in `OrchestratorService`

### 2. Policy Enforcement
- Tool allowlist: Only 4 read-only tools
- Blocked intents: transfer, payment, etc.
- Logged with `WARN` level

### 3. PII Redaction (Future)
- Placeholder for production logging
- Should redact account IDs, balances in logs

### 4. CORS
- Configured via `@CrossOrigin(origins = "*")` (PoC only)
- Production: Restrict to frontend domain

---

## Testing Strategy

### Unit Tests (48 tests, 0 failures)

| Test Class | Tests | Coverage |
|------------|-------|----------|
| MockBankingServiceTest | 6 | 100% |
| PolicyGateServiceTest | 7 | 100% |
| ToolRegistryServiceTest | 7 | 95% |
| OrchestratorServiceTest | 9 | 85% |
| SttStubAdapterTest | 2 | 100% |
| LlmStubAdapterTest | 7 | 100% |
| VoiceBankingApplicationTest | 1 | N/A |

### Integration Tests (Controller Layer)

| Test Class | Tests | Coverage |
|------------|-------|----------|
| AccountControllerTest | 4 | 80% |
| OrchestratorControllerTest | 4 | 75% |
| HealthControllerTest | 1 | 100% |

### Golden Utterance Tests

```java
@ParameterizedTest
@CsvSource({
    "What is my balance?, getBalance",
    "Show me my accounts, listAccounts",
    "What are my recent transactions?, queryTransactions",
    "How much did I spend at Starbucks?, queryTransactions"
})
void shouldProcessGoldenUtterances(String utterance, String expectedTool) {
    // Test stub LLM correctly maps utterances to tools
}
```

### Coverage Requirements

- **Services:** ≥70% line coverage
- **Controllers:** ≥60% line coverage
- **Domain:** ≥90% line coverage
- **Overall:** ≥70% line coverage

**Verification:**
```bash
mvn verify
open target/site/jacoco/index.html
```

---

## Future Enhancements

1. **Google ADK Integration**
   - Replace manual tool calling with ADK framework
   - Session management via ADK
   - Memory/context via ADK

2. **Persistent Storage**
   - Replace in-memory data with PostgreSQL
   - Spring Data JPA repositories
   - Flyway migrations

3. **Authentication**
   - OAuth2/OIDC integration
   - JWT token validation
   - User-scoped banking data

4. **Observability**
   - Micrometer metrics
   - Distributed tracing (Zipkin)
   - Structured logging (JSON)

5. **Production Readiness**
   - Rate limiting
   - Circuit breakers (Resilience4j)
   - API documentation (OpenAPI 3.0)
   - Docker image + Kubernetes manifests

---

## References

- [Master Migration Plan](./JAVA-MIGRATION-MASTER-PLAN.md)
- [ADR Index](./adr/README.md)
- [Development Guide](./DEVELOPMENT.md)
- [API Documentation](./API.md)
- [TypeScript Implementation](../../app/backend/)

---

**Last Updated:** 2026-01-16  
**Version:** 0.1.0-SNAPSHOT  
**Status:** Phase 1 Complete, Phase 2 Complete (Session Management + Cloud Adapters)
