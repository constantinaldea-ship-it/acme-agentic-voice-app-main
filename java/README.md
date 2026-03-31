# Voice Banking Assistant (Java)

Java implementation of the Voice Banking Assistant using Google Agent Development Kit (ADK) and Spring Boot.

## Overview

This is a complete rewrite of the TypeScript/Node.js PoC to Java, preserving all functionality:

- Voice/text input processing via Google Cloud Speech-to-Text
- LLM-powered intent recognition via Vertex AI Gemini  
- Tool calling for banking operations (balance, transactions)
- Policy gates for safety (read-only mode)
- Consent management and PII redaction

## Quick Start

### Prerequisites

- Java 21 LTS
- Maven 3.9+
- (Optional) Google Cloud credentials for cloud profile

### Run with Local Profile (Stub Adapters)

```bash
cd java
mvn clean compile
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=local
```

### Run with Cloud Profile (Real GCP Services)

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json
export GOOGLE_CLOUD_PROJECT=your-project-id

cd java
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=cloud
```

### Run Tests

```bash
cd java
mvn test                    # Unit tests
mvn verify                  # Unit + integration tests
mvn verify -Pcoverage       # With coverage report
```

## Project Structure

```
java/
├── pom.xml                           # Parent POM / Maven reactor
├── mock-server/                      # WireMock upstreams for appointments + banking mocks
├── bfa-service-resource/             # Resource-oriented BFA API
├── bfa-gateway/                      # Single-ingress gateway for adapter topology
├── bfa-adapter-branch-finder/        # Separately deployable AG-003 adapter
├── docs/
│   ├── JAVA-MIGRATION-MASTER-PLAN.md # Migration plan
│   └── adr/                          # Architecture Decision Records
│       ├── ADR-J001-validation-framework.md
│       ├── ADR-J002-testing-framework.md
│       ├── ADR-J003-memory-module-deprecation.md
│       └── ADR-J004-google-adk-selection.md
└── voice-banking-app/
    ├── pom.xml
    └── src/
        ├── main/java/com/voicebanking/
        │   ├── VoiceBankingApplication.java
        │   ├── config/         # Configuration classes
        │   ├── controller/     # REST controllers
        │   ├── service/        # Business services
        │   ├── adapter/        # STT/LLM adapters
        │   ├── domain/         # Domain models
        │   ├── tool/           # ADK tool definitions
        │   └── util/           # Utilities
        ├── main/resources/
        │   ├── application.yml
        │   ├── application-local.yml
        │   └── application-cloud.yml
        └── test/java/com/voicebanking/
            ├── *Test.java      # Unit tests
            └── integration/
                └── *IT.java    # Integration tests
```

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orchestrate` | Process voice/text request |
| GET | `/api/health` | Health check |
| GET | `/api/accounts` | List accounts |
| GET | `/api/accounts/{id}/balance` | Get account balance |
| GET | `/api/accounts/{id}/transactions` | Get transactions |

## Runtime Profiles

| Profile | Description | STT | LLM |
|---------|-------------|-----|-----|
| `local` | Development/CI | Stub | Stub |
| `cloud` | Demo/Production | Chirp 2 | Gemini |

### Cloud Profile Setup

To use the `cloud` profile with real Google Cloud services:

1. **Set up GCP credentials:**
   ```bash
   # Create service account and download JSON key
   gcloud iam service-accounts create voice-banking-sa
   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:voice-banking-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/speech.admin"
   gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
     --member="serviceAccount:voice-banking-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
     --role="roles/aiplatform.user"
   gcloud iam service-accounts keys create credentials.json \
     --iam-account=voice-banking-sa@YOUR_PROJECT_ID.iam.gserviceaccount.com
   ```

2. **Enable required APIs:**
   ```bash
   gcloud services enable speech.googleapis.com
   gcloud services enable aiplatform.googleapis.com
   ```

3. **Configure environment variables:**
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/credentials.json
   export GOOGLE_CLOUD_PROJECT=your-project-id
   ```

4. **Update `application-cloud.yml`:**
   ```yaml
   google:
     cloud:
       project-id: ${GOOGLE_CLOUD_PROJECT}
       location: us-central1  # or your preferred region
   ```

5. **Run with cloud profile:**
   ```bash
   mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=cloud
   ```

**Troubleshooting:**
- If STT fails: Verify `roles/speech.admin` is assigned
- If LLM fails: Verify `roles/aiplatform.user` is assigned and model is available in region
- Check logs for detailed error messages with stack traces

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 LTS |
| Framework | Spring Boot 3.3.x |
| AI Framework | Google ADK |
| STT | Google Cloud Speech-to-Text (Chirp 2) |
| LLM | Vertex AI Gemini |
| Validation | Bean Validation + Yavi |
| Testing | JUnit 5 + AssertJ + Testcontainers |
| Build | Maven |

## Session Management

The application supports session-based conversations via the `sessionId` field in requests:

```json
{
  "text": "What is my balance?",
  "sessionId": "user-abc-123",
  "consentAccepted": true
}
```

**Session Features:**
- **Stateless by default:** Each request is independent (Phase 1)
- **Session context (Phase 2):** `AdkSessionManager` will maintain conversation history
- **Session storage:** In-memory with configurable TTL (default: 30 minutes)
- **Session cleanup:** Automatic expiration of idle sessions

**Session Context (Coming in Phase 2):**
- Previous intents and tool calls
- User preferences (default account, etc.)
- Multi-turn conversation support

**Example Multi-Turn Flow:**
```bash
# Turn 1
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"Show my accounts","sessionId":"s1","consentAccepted":true}'
# Response: Lists 3 accounts

# Turn 2 (references previous context)
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"What about the checking account?","sessionId":"s1","consentAccepted":true}'
# Response: Uses context to know which account (requires Phase 2 AdkSessionManager)
```

## Cloud Run deployment

From the repository root, use the shared deployment suite:

```bash
cp .env.example .env
# edit .env and set at least GCP_PROJECT_ID and GCP_REGION before deploying

./scripts/cloud/deploy.sh service mock-server
./scripts/cloud/deploy.sh stack discovery-plan
./scripts/cloud/smoke-test.sh stack discovery-plan
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

See `../DEPLOYMENT-GUIDE.md` for service dependencies, required environment variables, and teardown guidance.

## Related Documentation

- [Migration Master Plan](./docs/JAVA-MIGRATION-MASTER-PLAN.md)
- [ADR-J001: Validation Framework](./docs/adr/ADR-J001-validation-framework.md)
- [ADR-J002: Testing Framework](./docs/adr/ADR-J002-testing-framework.md)
- [ADR-J003: Memory Module Deprecation](./docs/adr/ADR-J003-memory-module-deprecation.md)
- [ADR-J004: Google ADK Selection](./docs/adr/ADR-J004-google-adk-selection.md)

## Frontend

The React frontend (in `app/frontend/`) is **not migrated**. It continues to work with the Java backend by pointing to:

```
http://localhost:8080/api
```

Configure the frontend's proxy in `vite.config.ts` to target the Java backend port.
