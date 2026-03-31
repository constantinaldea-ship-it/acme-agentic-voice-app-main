# Voice Banking Assistant - API Documentation

## Base URL

```
http://localhost:8080/api
```

---

## Endpoints

### 1. Orchestrate Request

Process a voice or text banking request through the full pipeline (STT → LLM → Policy → Tool).

**Endpoint:** `POST /api/orchestrate`

**Request Body:**

```json
{
  "text": "What is my balance?",
  "audio": null,
  "sessionId": "user-abc-123",
  "consentAccepted": true
}
```

**Fields:**
- `text` (String, optional): Text input (mutually exclusive with audio)
- `audio` (String, optional): Base64-encoded audio data (mutually exclusive with text)
- `sessionId` (String, required): Unique session identifier for conversation tracking
- `consentAccepted` (Boolean, required): User consent flag (must be true)

**Validation:**
- Exactly one of `text` or `audio` must be provided (enforced by Yavi)
- `sessionId` must not be null
- `consentAccepted` must be true

**Success Response:**

```json
{
  "transcript": "What is my balance?",
  "intent": "I'll check your balance for you.",
  "toolCalled": "getBalance",
  "toolResult": {
    "accountId": "acc-checking-001",
    "available": 1250.50,
    "current": 1250.50,
    "currency": "USD",
    "asOf": "2026-01-16T09:50:00Z"
  },
  "responseText": "Here is your balance information.",
  "refusalReason": null
}
```

**Refusal Response:**

```json
{
  "transcript": "Transfer $100 to savings",
  "intent": null,
  "toolCalled": null,
  "toolResult": null,
  "responseText": null,
  "refusalReason": "I can only provide read-only information about your accounts. Transfers are not supported in this demo."
}
```

**Status Codes:**
- `200 OK`: Request processed successfully (including refusals)
- `400 Bad Request`: Validation failed (missing sessionId, both audio and text provided, etc.)
- `500 Internal Server Error`: Unexpected error during processing

**Example Requests:**

```bash
# Balance inquiry
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "What is my balance?",
    "sessionId": "test-123",
    "consentAccepted": true
  }'

# Account listing
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "Show me my accounts",
    "sessionId": "test-123",
    "consentAccepted": true
  }'

# Transaction query
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "What are my recent transactions?",
    "sessionId": "test-123",
    "consentAccepted": true
  }'

# Merchant-specific query
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "How much did I spend at Starbucks?",
    "sessionId": "test-123",
    "consentAccepted": true
  }'
```

---

### 2. List Accounts

Retrieve all accounts for the user.

**Endpoint:** `GET /api/accounts`

**Response:**

```json
[
  {
    "id": "acc-checking-001",
    "type": "CHECKING",
    "name": "Everyday Checking",
    "currency": "USD",
    "lastFour": "4321"
  },
  {
    "id": "acc-savings-002",
    "type": "SAVINGS",
    "name": "High-Yield Savings",
    "currency": "USD",
    "lastFour": "5678"
  },
  {
    "id": "acc-card-003",
    "type": "CARD",
    "name": "Platinum Credit Card",
    "currency": "USD",
    "lastFour": "9012"
  }
]
```

**Status Codes:**
- `200 OK`: Accounts retrieved successfully

**Example:**

```bash
curl http://localhost:8080/api/accounts
```

---

### 3. Get Account Balance

Retrieve balance for a specific account.

**Endpoint:** `GET /api/accounts/{accountId}/balance`

**Path Parameters:**
- `accountId` (String): Account identifier (e.g., `acc-checking-001`)

**Response:**

```json
{
  "accountId": "acc-checking-001",
  "available": 1250.50,
  "current": 1250.50,
  "currency": "USD",
  "asOf": "2026-01-16T09:50:00Z"
}
```

**Error Response:**

```json
{
  "timestamp": "2026-01-16T09:50:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Account not found: invalid-id",
  "path": "/api/accounts/invalid-id/balance"
}
```

**Status Codes:**
- `200 OK`: Balance retrieved successfully
- `404 Not Found`: Account does not exist

**Example:**

```bash
curl http://localhost:8080/api/accounts/acc-checking-001/balance
```

---

### 4. Get Account Transactions

Retrieve transactions for a specific account.

**Endpoint:** `GET /api/accounts/{accountId}/transactions`

**Path Parameters:**
- `accountId` (String): Account identifier (e.g., `acc-checking-001`)

**Query Parameters:**
- `limit` (Integer, optional): Maximum number of transactions to return (default: 10, max: 100)

**Response:**

```json
[
  {
    "id": "txn-001",
    "accountId": "acc-checking-001",
    "date": "2026-01-15",
    "description": "Starbucks Coffee",
    "amount": -5.75,
    "currency": "USD",
    "type": "DEBIT"
  },
  {
    "id": "txn-002",
    "accountId": "acc-checking-001",
    "date": "2026-01-14",
    "description": "Paycheck Deposit",
    "amount": 2500.00,
    "currency": "USD",
    "type": "CREDIT"
  }
]
```

**Status Codes:**
- `200 OK`: Transactions retrieved successfully
- `404 Not Found`: Account does not exist

**Example:**

```bash
# Get last 5 transactions
curl http://localhost:8080/api/accounts/acc-checking-001/transactions?limit=5

# Get default (10) transactions
curl http://localhost:8080/api/accounts/acc-checking-001/transactions
```

---

### 5. Health Check

Retrieve application health status and active profile.

**Endpoint:** `GET /api/health`

**Response:**

```json
{
  "status": "UP",
  "profile": "local",
  "timestamp": "2026-01-16T09:50:00Z"
}
```

**Fields:**
- `status` (String): Always "UP" if application is running
- `profile` (String): Active Spring profile (`local` or `cloud`)
- `timestamp` (String): ISO 8601 timestamp

**Status Codes:**
- `200 OK`: Application is healthy

**Example:**

```bash
curl http://localhost:8080/api/health
```

---

## Golden Utterances

The following utterances are guaranteed to work correctly in both `local` (stub) and `cloud` (GCP) profiles:

| Utterance | Intent | Tool Called | Expected Result |
|-----------|--------|-------------|-----------------|
| "What is my balance?" | Balance inquiry | `getBalance` | Returns checking account balance |
| "Show me my accounts" | Account listing | `listAccounts` | Returns 3 accounts |
| "What are my recent transactions?" | Transaction query | `queryTransactions` | Returns last 10 transactions |
| "How much did I spend at Starbucks?" | Merchant query | `queryTransactions` | Returns Starbucks transactions |
| "Transfer $100 to savings" | Blocked intent | None | Refusal response (read-only demo) |
| "Send money to John" | Blocked intent | None | Refusal response (blocked by policy) |
| "Pay my credit card" | Blocked intent | None | Refusal response (blocked by policy) |

---

## Error Handling

All endpoints return standard error responses with the following format:

```json
{
  "timestamp": "2026-01-16T09:50:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Must provide either audio or text",
  "path": "/api/orchestrate"
}
```

**Common Error Scenarios:**

| Scenario | Status Code | Message |
|----------|-------------|---------|
| Missing sessionId | 400 | "sessionId is required" |
| Both audio and text provided | 400 | "Must provide either audio or text" |
| Neither audio nor text provided | 400 | "Must provide either audio or text" |
| Account not found | 404 | "Account not found: {accountId}" |
| Internal server error | 500 | "An unexpected error occurred" |

---

## Session Management

All `/api/orchestrate` requests require a `sessionId` for conversation tracking. Sessions:

- Are created automatically on first use
- Store conversation history (transcript, intent, tool call, result)
- Store user preferences (default accountId, language, etc.)
- Expire after 30 minutes of inactivity
- Are thread-safe for concurrent access

**Example Multi-Turn Conversation:**

```bash
# Turn 1: List accounts
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"Show my accounts","sessionId":"user-abc","consentAccepted":true}'

# Turn 2: Check balance (context from Turn 1 may be used in Phase 2)
curl -X POST http://localhost:8080/api/orchestrate \
  -H "Content-Type: application/json" \
  -d '{"text":"What about the checking account?","sessionId":"user-abc","consentAccepted":true}'
```

**Note:** Multi-turn context resolution requires Phase 2 AdkSessionManager integration (completed).

---

## Rate Limiting

**Current:** No rate limiting in PoC

**Production:** Recommended rate limits:
- `/api/orchestrate`: 10 requests/minute per sessionId
- Other endpoints: 100 requests/minute per IP

---

## Authentication

**Current:** No authentication in PoC (demo/test mode)

**Production:** Recommended approach:
- OAuth2/OIDC (Google, Okta, etc.)
- JWT bearer tokens
- Scoped access per user

---

## CORS

**Current:** Open CORS (`@CrossOrigin(origins = "*")`) for PoC

**Production:** Restrict to frontend domain:
```java
@CrossOrigin(origins = "https://banking.example.com")
```

---

## Profiles

### Local Profile (`-Dspring.profiles.active=local`)

- **STT:** Stub adapter (deterministic transcription)
- **LLM:** Stub adapter (pattern matching for intents)
- **Latency:** STT ~50ms, LLM ~100ms (simulated)
- **Use Case:** Local development, CI/CD, unit tests

### Cloud Profile (`-Dspring.profiles.active=cloud`)

- **STT:** Google Cloud Speech-to-Text (Chirp 2 model)
- **LLM:** Vertex AI Gemini (1.5 Flash model with tool calling)
- **Latency:** STT <2s, LLM <3s (real-world)
- **Use Case:** Staging, production, integration tests
- **Requirements:**
  - `GOOGLE_APPLICATION_CREDENTIALS` environment variable
  - `GOOGLE_CLOUD_PROJECT` configuration
  - IAM roles: `roles/speech.admin`, `roles/aiplatform.user`

---

## Performance

**Stub Profile (Local):**
- `/api/orchestrate`: ~150-200ms end-to-end
- `/api/accounts`: <10ms
- `/api/accounts/{id}/balance`: <5ms

**Cloud Profile (GCP):**
- `/api/orchestrate`: ~2-5s end-to-end (depends on LLM + STT latency)
- Direct endpoints: Same as local (<10ms)

---

## References

- [Architecture Documentation](./ARCHITECTURE.md)
- [Development Guide](./DEVELOPMENT.md)
- [Master Migration Plan](./JAVA-MIGRATION-MASTER-PLAN.md)

---

**Last Updated:** 2026-01-16  
**Version:** 0.1.0-SNAPSHOT  
**API Version:** v1
