# Java Migration Master Plan: Voice Banking Assistant

> **Status:** Draft  
> **Created:** 2026-01-15  
> **Target:** Migrate TypeScript/Node.js PoC to Java + Google Agent Development Kit (ADK)  
> **Branch:** `feature/java_re-write`

---

## Executive Summary

This document defines the multi-phase plan for rewriting the Voice Banking Assistant PoC from TypeScript/Node.js to Java using Google Agent Development Kit (ADK). The migration preserves all current functionality while leveraging Java ecosystem benefits and ADK's native Gemini/Vertex AI integration.

### Key Decisions Summary

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Frontend | Keep React (no migration) | Fastest path; React UI unchanged |
| Backend Framework | Spring Boot 3.x | Industry standard, excellent ADK integration |
| AI Framework | Google ADK for Java | Native Gemini support, tool calling, agentic patterns |
| Build System | Maven | Enterprise standard, predictable builds |
| Java Version | 21 LTS | Virtual threads, modern features, long-term support |
| Validation | Bean Validation + Yavi | See ADR-J001 |
| Testing | JUnit 5 + AssertJ + Testcontainers | See ADR-J002 |
| Memory Module | Deprecate (use ADK) | See ADR-J003 |

---

## Table of Contents

1. [Current Architecture Analysis](#1-current-architecture-analysis)
2. [Technology Mapping](#2-technology-mapping)
3. [Phase Overview](#3-phase-overview)
4. [Phase 0: Project Setup](#4-phase-0-project-setup)
5. [Phase 1: Core Backend](#5-phase-1-core-backend)
6. [Phase 2: Google ADK Integration](#6-phase-2-google-adk-integration)
7. [Phase 3: Testing & Validation](#7-phase-3-testing--validation)
8. [Phase 4: Documentation & Cleanup](#8-phase-4-documentation--cleanup)
9. [Risk Register](#9-risk-register)
10. [Rollback Strategy](#10-rollback-strategy)
11. [Success Criteria](#11-success-criteria)
12. [Appendices](#12-appendices)

---

## 1. Current Architecture Analysis

### 1.1 TypeScript Components Inventory

#### Backend (`app/backend/`)

| Component | File(s) | Purpose | Lines |
|-----------|---------|---------|-------|
| **Express Server** | `src/index.ts` | HTTP server, route mounting | ~50 |
| **Orchestrator** | `src/services/orchestrator.ts` | Main flow: consent→STT→LLM→tool→response | ~200 |
| **Mock Banking** | `src/services/mockBanking.ts` | In-memory accounts, balances, transactions | ~150 |
| **Policy Gates** | `src/services/policyGates.ts` | Tool allowlist, refusal logic | ~100 |
| **Tool Registry** | `src/services/toolRegistry.ts` | Tool definitions, execution | ~150 |
| **Session Context** | `src/services/sessionContext.ts` | Session state management | ~80 |
| **Context Assembler** | `src/services/contextAssembler.ts` | Build context packets for LLM | ~120 |
| **Short-Term Memory** | `src/services/shortTermMemory.ts` | Dialogue history | ~100 |
| **Fact Memory** | `src/services/factMemory.ts` | Persistent user facts | ~80 |
| **STT Stub Adapter** | `src/adapters/sttStub.ts` | Deterministic STT for dev/test | ~60 |
| **STT Chirp2 Adapter** | `src/adapters/sttChirp2.ts` | Google Cloud STT integration | ~120 |
| **LLM Stub Adapter** | `src/adapters/llmStub.ts` | Deterministic LLM for dev/test | ~100 |
| **LLM Gemini Adapter** | `src/adapters/llmGemini.ts` | Vertex AI Gemini integration | ~180 |
| **Profile Config** | `src/config/profiles.ts` | Runtime profile selection | ~60 |
| **Redaction Utils** | `src/utils/redaction.ts` | PII masking | ~80 |
| **Logger** | `src/utils/logger.ts` | Structured logging | ~40 |
| **Routes** | `src/routes/*.ts` | API endpoints | ~100 |

**Estimated Backend Total:** ~1,850 lines of TypeScript

#### Shared Package (`packages/shared/`)

| Schema File | Purpose | Schemas |
|-------------|---------|---------|
| `banking.ts` | Account, Balance, Transaction | 3 |
| `adapters.ts` | TranscriptResponse, ToolCall, LlmResponse | 4 |
| `tools.ts` | Tool input/result schemas | 12 |
| `memory.ts` | Context packets, entities | 6 |

**Estimated Shared Total:** ~400 lines

#### Frontend (`app/frontend/`)

| Component | Purpose |
|-----------|---------|
| `App.tsx` | Main app, state management |
| `VoiceConsentModal.tsx` | Consent UI |
| `VoiceControls.tsx` | Push-to-talk, text input |
| `TranscriptPanel.tsx` | Display transcript |
| `AgentDebugPanel.tsx` | Debug info |
| `ResponsePanel.tsx` | Agent response + TTS |
| `api.ts` | API service |

**Frontend Status:** NO MIGRATION REQUIRED (kept as-is, pointing to Java backend)

### 1.2 API Contract

The React frontend expects these endpoints:

```
POST /api/orchestrate
  Request: { text?: string, audio?: string, sessionId: string, consentAccepted: boolean }
  Response: { transcript, intent, toolCalled, toolResult, responseText, refusalReason }

GET /api/health
  Response: { status: "ok", timestamp: string }

GET /api/accounts
  Response: Account[]

GET /api/accounts/:id/balance
  Response: Balance

GET /api/accounts/:id/transactions
  Response: Transaction[]
```

**Requirement:** Java backend MUST implement identical endpoints and response shapes.

### 1.3 Runtime Profiles

| Profile | STT Provider | LLM Provider | Use Case |
|---------|--------------|--------------|----------|
| `local-only` | Stub (deterministic) | Stub (deterministic) | Dev, CI, tests |
| `cloud-concierge` | Google Chirp 2 | Vertex AI Gemini | Demo, production |

**Requirement:** Java must support both profiles via Spring Profiles.

---

## 2. Technology Mapping

### 2.1 TypeScript → Java Mapping

| TypeScript | Java Equivalent | Notes |
|------------|-----------------|-------|
| Express | Spring Boot 3.x | WebMVC or WebFlux |
| Zod schemas | Bean Validation + Yavi | Runtime validation |
| `@google-cloud/speech` | `google-cloud-speech` Java SDK | STT |
| `@google-cloud/vertexai` | Google ADK / Vertex AI Java SDK | LLM |
| Vitest | JUnit 5 + AssertJ | Testing |
| TypeScript interfaces | Java records / interfaces | Type safety |
| `tsx watch` | Spring DevTools + LiveReload | Hot reload |
| pnpm workspaces | Maven multi-module | Monorepo |

### 2.2 Google ADK Evaluation

**Google Agent Development Kit (ADK)** is Google's official Java framework for building AI agents, released in early 2025.

#### Why ADK is the Best Option

| Criteria | ADK | Raw Vertex AI SDK | LangChain4j |
|----------|-----|-------------------|-------------|
| Native Gemini support | ✅ First-class | ✅ Yes | ⚠️ Adapter |
| Tool/Function calling | ✅ Built-in | ⚠️ Manual | ✅ Yes |
| Conversation memory | ✅ Built-in | ❌ DIY | ✅ Yes |
| Multi-agent orchestration | ✅ Native | ❌ DIY | ⚠️ Limited |
| Google Cloud integration | ✅ Native | ✅ Yes | ⚠️ Adapter |
| Enterprise support | ✅ Google | ✅ Google | ❌ Community |
| Spring Boot integration | ✅ Starter | ⚠️ Manual | ✅ Starter |

**Recommendation:** Use Google ADK because:
1. Native Gemini integration reduces boilerplate
2. Built-in tool calling matches our current architecture
3. Built-in conversation state replaces our custom memory modules
4. Google enterprise support for banking use case
5. Active development with Spring Boot starter

**Trade-offs:**
- ADK is newer (less community resources)
- Ties implementation to Google ecosystem
- May require adaptation for non-Gemini LLMs in future

See **ADR-J004** for full evaluation.

---

## 3. Phase Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JAVA MIGRATION TIMELINE                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Phase 0          Phase 1           Phase 2          Phase 3        │
│  ────────         ────────          ────────         ────────       │
│  Project          Core              ADK              Testing &      │
│  Setup            Backend           Integration      Validation     │
│                                                                     │
│  [2-3 days]       [5-7 days]        [4-5 days]       [3-4 days]    │
│                                                                     │
│  • Maven setup    • Mock Banking    • STT Adapters   • Golden set   │
│  • Dependencies   • Tool Registry   • LLM Adapters   • Integration  │
│  • ADRs           • Policy Gates    • ADK Agent      • Comparison   │
│  • Project        • REST APIs       • Memory         • Performance  │
│    structure      • Profiles        • Context        • CI/CD        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│  Total Estimated: 14-19 days                                        │
└─────────────────────────────────────────────────────────────────────┘
```

### Phase Dependencies

```
Phase 0 ──► Phase 1 ──► Phase 2 ──► Phase 3
   │            │           │           │
   │            │           │           └── Depends on working ADK integration
   │            │           └── Depends on core services
   │            └── Depends on project setup
   └── No dependencies
```

---

## 4. Phase 0: Project Setup

**Duration:** 2-3 days  
**Goal:** Establish Java project structure, dependencies, and foundational ADRs

### 4.0.1 Create Maven Project Structure

```
java/
├── pom.xml                           # Parent POM
├── docs/
│   ├── JAVA-MIGRATION-MASTER-PLAN.md # This file
│   └── adr/
│       ├── ADR-J001-validation-framework.md
│       ├── ADR-J002-testing-framework.md
│       ├── ADR-J003-memory-module-deprecation.md
│       └── ADR-J004-google-adk-selection.md
├── voice-banking-app/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/voicebanking/
│       │   │       ├── VoiceBankingApplication.java
│       │   │       ├── config/
│       │   │       │   ├── ProfileConfiguration.java
│       │   │       │   └── AdkConfiguration.java
│       │   │       ├── controller/
│       │   │       │   ├── OrchestratorController.java
│       │   │       │   ├── AccountController.java
│       │   │       │   └── HealthController.java
│       │   │       ├── service/
│       │   │       │   ├── OrchestratorService.java
│       │   │       │   ├── MockBankingService.java
│       │   │       │   ├── PolicyGateService.java
│       │   │       │   └── ToolRegistryService.java
│       │   │       ├── adapter/
│       │   │       │   ├── stt/
│       │   │       │   │   ├── SttProvider.java
│       │   │       │   │   ├── SttStubAdapter.java
│       │   │       │   │   └── SttChirp2Adapter.java
│       │   │       │   └── llm/
│       │   │       │       ├── LlmProvider.java
│       │   │       │       ├── LlmStubAdapter.java
│       │   │       │       └── LlmGeminiAdapter.java
│       │   │       ├── domain/
│       │   │       │   ├── Account.java
│       │   │       │   ├── Balance.java
│       │   │       │   ├── Transaction.java
│       │   │       │   └── dto/
│       │   │       │       ├── OrchestratorRequest.java
│       │   │       │       └── OrchestratorResponse.java
│       │   │       ├── tool/
│       │   │       │   ├── GetBalanceTool.java
│       │   │       │   ├── ListAccountsTool.java
│       │   │       │   └── QueryTransactionsTool.java
│       │   │       └── util/
│       │   │           ├── RedactionUtil.java
│       │   │           └── LoggingUtil.java
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-local.yml
│       │       └── application-cloud.yml
│       └── test/
│           └── java/
│               └── com/voicebanking/
│                   ├── OrchestratorServiceTest.java
│                   ├── MockBankingServiceTest.java
│                   └── integration/
│                       └── GoldenUtteranceIT.java
└── voice-banking-shared/             # Optional: shared DTOs if needed
    ├── pom.xml
    └── src/main/java/...
```

### 4.0.2 Dependencies (Parent POM)

```xml
<!-- Key dependencies -->
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.0</spring-boot.version>
    <google-adk.version>0.1.0</google-adk.version>
    <google-cloud-speech.version>4.x.x</google-cloud-speech.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Google ADK -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-adk-spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Google Cloud STT -->
    <dependency>
        <groupId>com.google.cloud</groupId>
        <artifactId>google-cloud-speech</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>am.ik.yavi</groupId>
        <artifactId>yavi</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 4.0.3 Deliverables

- [ ] Parent `pom.xml` with dependency management
- [ ] `voice-banking-app/pom.xml` with module config
- [ ] `application.yml` with profile configuration
- [ ] ADR-J001: Validation Framework
- [ ] ADR-J002: Testing Framework
- [ ] ADR-J003: Memory Module Deprecation
- [ ] ADR-J004: Google ADK Selection
- [ ] `.gitignore` for Java/Maven
- [ ] README.md for java/ folder

### 4.0.4 Verification

```bash
# Build compiles
cd java && mvn clean compile

# Empty test suite passes
mvn test

# Application starts (will fail on missing beans, that's OK)
mvn spring-boot:run -pl voice-banking-app
```

---

## 5. Phase 1: Core Backend

**Duration:** 5-7 days  
**Goal:** Implement core services WITHOUT cloud integrations (stub adapters only)

### 5.1.1 Domain Models

Port from TypeScript `packages/shared/`:

| TypeScript | Java | Validation |
|------------|------|------------|
| `AccountSchema` | `Account.java` record | Bean Validation |
| `BalanceSchema` | `Balance.java` record | Bean Validation |
| `TransactionSchema` | `Transaction.java` record | Bean Validation |
| `OrchestratorRequest` | `OrchestratorRequest.java` | Yavi |
| `OrchestratorResponse` | `OrchestratorResponse.java` | Yavi |

**Example:**

```java
// Account.java
public record Account(
    @NotBlank String id,
    @NotNull AccountType type,
    @NotBlank String name,
    @NotBlank String currency,
    @Size(min = 4, max = 4) String lastFour
) {
    public enum AccountType { CHECKING, SAVINGS, CARD }
}
```

### 5.1.2 Mock Banking Service

Port `mockBanking.ts` → `MockBankingService.java`:

- Same seeded data (3 accounts, balances, ~20 transactions)
- Same methods: `getAccounts()`, `getBalance(accountId)`, `getTransactions(accountId, filters)`
- In-memory storage using `ConcurrentHashMap` or `Map.of()`

### 5.1.3 Tool Registry

Port `toolRegistry.ts` → `ToolRegistryService.java`:

- Tool definitions with input/output schemas
- Tool execution dispatch
- Integration with ADK tool interfaces (prepare for Phase 2)

### 5.1.4 Policy Gates

Port `policyGates.ts` → `PolicyGateService.java`:

- Tool allowlist (read-only tools only)
- Refusal logic for transactional intents
- Consent verification

### 5.1.5 STT/LLM Stub Adapters

Port stub adapters for local-only profile:

- `SttStubAdapter.java`: Deterministic transcription (echo input or mapped responses)
- `LlmStubAdapter.java`: Deterministic intent classification for golden utterances

### 5.1.6 Orchestrator Service

Port `orchestrator.ts` → `OrchestratorService.java`:

Flow: `request → consent check → STT → LLM → policy gate → tool execution → response`

### 5.1.7 REST Controllers

- `OrchestratorController.java`: `POST /api/orchestrate`
- `AccountController.java`: `GET /api/accounts`, `GET /api/accounts/{id}/balance`, etc.
- `HealthController.java`: `GET /api/health`

### 5.1.8 Deliverables

- [ ] Domain models (records with validation)
- [ ] `MockBankingService.java` with seeded data
- [ ] `ToolRegistryService.java` with tool definitions
- [ ] `PolicyGateService.java` with allowlist
- [ ] `SttStubAdapter.java`
- [ ] `LlmStubAdapter.java`
- [ ] `OrchestratorService.java`
- [ ] REST controllers (3)
- [ ] Unit tests for all services (≥70% coverage)
- [ ] Profile configuration (`local` profile working)

### 5.1.9 Verification

```bash
# All tests pass
mvn test

# Application starts with local profile
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=local

# API responds correctly
curl http://localhost:8080/api/health
curl http://localhost:8080/api/accounts
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"What is my balance?","sessionId":"test","consentAccepted":true}'
```

---

## 6. Phase 2: Google ADK Integration

**Duration:** 4-5 days  
**Goal:** Integrate Google ADK for cloud profile with real STT and Gemini

### 6.2.1 ADK Agent Configuration

```java
@Configuration
@Profile("cloud")
public class AdkConfiguration {
    
    @Bean
    public Agent voiceBankingAgent(
            List<Tool> tools,
            LlmProvider llmProvider) {
        return Agent.builder()
            .name("voice-banking-assistant")
            .description("Voice-enabled banking assistant for balance inquiries and transaction queries")
            .model(llmProvider.getModel())
            .tools(tools)
            .systemPrompt(loadSystemPrompt())
            .build();
    }
}
```

### 6.2.2 ADK Tools

Convert tool registry to ADK Tool interface:

```java
@Component
public class GetBalanceTool implements Tool {
    
    @Override
    public String getName() { return "getBalance"; }
    
    @Override
    public String getDescription() { 
        return "Get the current balance of a bank account"; 
    }
    
    @Override
    public Schema getInputSchema() {
        return Schema.builder()
            .property("accountId", Schema.Type.STRING, "Optional account ID")
            .build();
    }
    
    @Override
    public ToolResult execute(ToolInput input) {
        // Delegate to MockBankingService
    }
}
```

### 6.2.3 STT Chirp2 Adapter

```java
@Component
@Profile("cloud")
public class SttChirp2Adapter implements SttProvider {
    
    private final SpeechClient speechClient;
    
    @Override
    public TranscriptResponse transcribe(byte[] audio) {
        RecognizeRequest request = RecognizeRequest.newBuilder()
            .setConfig(RecognitionConfig.newBuilder()
                .setEncoding(AudioEncoding.LINEAR16)
                .setLanguageCode("en-US")
                .setModel("chirp_2")
                .build())
            .setAudio(RecognitionAudio.newBuilder()
                .setContent(ByteString.copyFrom(audio))
                .build())
            .build();
        
        RecognizeResponse response = speechClient.recognize(request);
        // Map to TranscriptResponse
    }
}
```

### 6.2.4 LLM Gemini Adapter (via ADK)

```java
@Component
@Profile("cloud")
public class LlmGeminiAdapter implements LlmProvider {
    
    private final Agent agent;
    
    @Override
    public LlmResponse process(String transcript, Context context) {
        AgentResponse response = agent.run(
            UserMessage.of(transcript),
            context.toAgentContext()
        );
        return mapToLlmResponse(response);
    }
}
```

### 6.2.5 Session/Memory via ADK

ADK provides built-in conversation state management:

```java
@Component
public class AdkSessionManager {
    
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    
    public AgentSession getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, 
            id -> AgentSession.builder()
                .id(id)
                .maxTurns(10)
                .build());
    }
}
```

This replaces:
- `sessionContext.ts`
- `shortTermMemory.ts`
- `contextAssembler.ts`
- `@agent-toolkit/memory`

### 6.2.6 Deliverables

- [ ] `AdkConfiguration.java` with Agent bean
- [ ] ADK Tool implementations (3+)
- [ ] `SttChirp2Adapter.java`
- [ ] `LlmGeminiAdapter.java`
- [ ] `AdkSessionManager.java`
- [ ] Profile switch (`cloud` profile working)
- [ ] Integration tests with GCP credentials

### 6.2.7 Verification

```bash
# Cloud profile starts (requires GCP credentials)
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=cloud

# Test with real STT/LLM
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"What is my checking account balance?","sessionId":"test","consentAccepted":true}'
```

---

## 7. Phase 3: Testing & Validation

**Duration:** 3-4 days  
**Goal:** Validate Java implementation produces identical results to TypeScript

### 7.3.1 Golden Utterance Test Set

From existing TypeScript tests, port the 7+ golden utterances:

| # | Utterance | Expected Intent | Expected Tool |
|---|-----------|-----------------|---------------|
| 1 | "What is my balance?" | balance_inquiry | getBalance |
| 2 | "Show me my accounts" | list_accounts | listAccounts |
| 3 | "What are my recent transactions?" | transaction_query | queryTransactions |
| 4 | "Transfer $100 to savings" | transfer (blocked) | REFUSAL |
| 5 | "How much did I spend at Starbucks?" | transaction_query | queryTransactions |
| 6 | "Show my checking balance" | balance_inquiry | getBalance |
| 7 | "What is the weather?" | out_of_scope | REFUSAL |

### 7.3.2 Comparison Test Strategy

```java
@SpringBootTest
class GoldenUtteranceComparisonIT {
    
    @ParameterizedTest
    @CsvFileSource(resources = "/golden-utterances.csv")
    void shouldMatchTypeScriptBehavior(
            String utterance, 
            String expectedIntent, 
            String expectedTool,
            String expectedRefusal) {
        
        OrchestratorResponse response = orchestratorService.process(
            new OrchestratorRequest(utterance, "test-session", true)
        );
        
        assertThat(response.intent()).isEqualTo(expectedIntent);
        if (expectedTool != null) {
            assertThat(response.toolCalled()).isEqualTo(expectedTool);
        }
        if (expectedRefusal != null) {
            assertThat(response.refusalReason()).contains(expectedRefusal);
        }
    }
}
```

### 7.3.3 Performance Benchmarks

| Metric | TypeScript Baseline | Java Target |
|--------|---------------------|-------------|
| Stub orchestration latency | < 50ms | < 50ms |
| Cloud STT latency | < 2s | < 2s |
| Cloud LLM latency | < 3s | < 3s |
| Memory usage (idle) | ~150MB | < 200MB |
| Startup time | < 3s | < 5s |

### 7.3.4 Test Coverage Requirements

| Module | Minimum Coverage |
|--------|------------------|
| Services | ≥70% line coverage |
| Controllers | ≥60% line coverage |
| Domain | ≥90% line coverage |
| Integration | All golden utterances |

### 7.3.5 CI/CD Pipeline

**GitHub Actions** (`java/.github/workflows/java-ci.yml`):

```yaml
name: Java CI

on:
  push:
    paths: ['java/**']
  pull_request:
    paths: ['java/**']

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Build
        run: cd java && mvn clean verify
      - name: Coverage Report
        run: cd java && mvn jacoco:report
```

**Cloud Build** (`java/cloudbuild.yaml`):

```yaml
steps:
  - name: 'maven:3.9-eclipse-temurin-21'
    entrypoint: 'mvn'
    args: ['clean', 'verify', '-f', 'java/pom.xml']
  - name: 'gcr.io/cloud-builders/docker'
    args: ['build', '-t', 'gcr.io/$PROJECT_ID/voice-banking-java', 'java/']
```

### 7.3.6 Deliverables

- [ ] Golden utterance test suite
- [ ] Comparison tests (Java vs TypeScript behavior)
- [ ] Performance benchmarks
- [ ] JaCoCo coverage configuration
- [ ] GitHub Actions workflow
- [ ] Cloud Build configuration
- [ ] Test documentation

### 7.3.7 Verification

```bash
# Full test suite with coverage
mvn verify -pl voice-banking-app

# Coverage report
open target/site/jacoco/index.html

# Golden utterance tests
mvn test -Dtest=GoldenUtteranceIT
```

---

## 8. Phase 4: Documentation & Cleanup

**Duration:** 1-2 days  
**Goal:** Complete documentation and prepare for handoff

### 8.4.1 Documentation Deliverables

- [ ] `java/README.md` - Quick start guide
- [ ] `java/docs/ARCHITECTURE.md` - Java architecture overview
- [ ] `java/docs/DEVELOPMENT.md` - Dev setup guide
- [ ] `java/docs/API.md` - API documentation (OpenAPI spec)
- [ ] `java/docs/COMPARISON.md` - TypeScript vs Java comparison matrix
- [ ] Update root `README.md` with Java section

### 8.4.2 Comparison Matrix

| Aspect | TypeScript | Java |
|--------|------------|------|
| Language | TypeScript 5.x | Java 21 LTS |
| Runtime | Node.js 20 LTS | JVM 21 |
| Framework | Express 4.x | Spring Boot 3.3.x |
| Validation | Zod | Bean Validation + Yavi |
| AI Framework | Custom adapters | Google ADK |
| Testing | Vitest | JUnit 5 + AssertJ |
| Build | pnpm | Maven |
| LOC (backend) | ~1,850 | ~2,200 (est.) |

### 8.4.3 Migration Completion Checklist

- [ ] All golden utterances pass
- [ ] API contract identical to TypeScript
- [ ] Both profiles working (local, cloud)
- [ ] Test coverage ≥70%
- [ ] CI/CD pipelines green
- [ ] Documentation complete
- [ ] ADRs approved
- [ ] Beads issues closed

---

## 9. Risk Register

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| ADK API changes | High | Medium | Pin version, monitor releases |
| Chirp2 Java SDK issues | Medium | Low | Fallback to REST API |
| Performance regression | Medium | Low | Benchmark early (Phase 1) |
| Memory model mismatch | Medium | Medium | Careful context packet mapping |
| Spring Boot version conflicts | Low | Medium | Use BOM, test upgrades |

---

## 10. Rollback Strategy

### 10.1 Full Rollback

If Java migration fails:
1. TypeScript implementation remains in `app/` unchanged
2. Delete `java/` folder
3. Revert any shared changes

### 10.2 Partial Rollback

If specific phase fails:
1. Roll back to previous phase's stable state
2. Document issues in Beads
3. Re-plan affected phase

### 10.3 Parallel Running

During development, both implementations can run:
- TypeScript: `pnpm --filter @voice-banking/backend dev` (port 3000)
- Java: `mvn spring-boot:run` (port 8080)

Frontend can be pointed to either via environment variable.

---

## 11. Success Criteria

### 11.1 Phase Gate Criteria

| Phase | Gate Criteria |
|-------|---------------|
| Phase 0 | Maven builds, ADRs approved |
| Phase 1 | Local profile passes all golden utterances |
| Phase 2 | Cloud profile passes all golden utterances |
| Phase 3 | CI green, coverage ≥70%, benchmarks met |
| Phase 4 | Documentation complete, stakeholder sign-off |

### 11.2 Overall Success

Migration is successful when:
1. ✅ All 7+ golden utterances produce identical behavior
2. ✅ Both runtime profiles work (local, cloud)
3. ✅ Test coverage ≥70% for services
4. ✅ Performance within 20% of TypeScript baseline
5. ✅ API contract 100% compatible (React frontend works unchanged)
6. ✅ CI/CD pipelines operational
7. ✅ Documentation complete and reviewed

---

## 12. Appendices

### 12.1 Related Documents

- [ADR-J001: Validation Framework](./adr/ADR-J001-validation-framework.md)
- [ADR-J002: Testing Framework](./adr/ADR-J002-testing-framework.md)
- [ADR-J003: Memory Module Deprecation](./adr/ADR-J003-memory-module-deprecation.md)
- [ADR-J004: Google ADK Selection](./adr/ADR-J004-google-adk-selection.md)

### 12.2 Reference Links

- [Google ADK Documentation](https://cloud.google.com/agent-development-kit)
- [Spring Boot 3.3 Reference](https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/)
- [Google Cloud Speech Java SDK](https://cloud.google.com/speech-to-text/docs/libraries#client-libraries-install-java)
- [Vertex AI Java SDK](https://cloud.google.com/vertex-ai/docs/start/client-libraries)

### 12.3 Glossary

| Term | Definition |
|------|------------|
| ADK | Google Agent Development Kit |
| STT | Speech-to-Text |
| LLM | Large Language Model |
| Chirp 2 | Google's latest speech recognition model |
| Golden Utterance | Reference test case with known expected behavior |

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-01-15 | AI Agent | Initial draft of Java migration master plan |
