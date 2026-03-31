# POC: Event Store — H2 File-Based Persistence

**Status:** Proof of Concept (decoupled from production agents/services)  
**Profile:** `poc` — zero impact on production startup unless explicitly enabled  

---

## Quick Start

```bash
cd java

# Run with POC profile enabled (local stubs + H2 event store)
mvn -pl voice-banking-app spring-boot:run \
    -Dspring-boot.run.profiles=local,poc
```

The app starts on `http://localhost:8080`. The H2 database file is persisted to `./data/poc-events.db`.

### H2 Console

Browse persisted data at: [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

- **JDBC URL:** `jdbc:h2:file:./data/poc-events`
- **Username:** `sa`
- **Password:** *(empty)*

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/poc/events` | Write a structured event |
| `GET`  | `/api/poc/events` | Query events (optional filters) |

### Query Parameters (GET)

| Param | Type | Description |
|-------|------|-------------|
| `eventType` | `string` | Filter by event type (e.g., `audit`, `metric`, `log`) |
| `from` | `ISO-8601 instant` | Start of time range (e.g., `2026-03-01T00:00:00Z`) |
| `to` | `ISO-8601 instant` | End of time range |

---

## curl Examples

### 1. Write an audit event

```bash
curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "audit",
    "payload": "{\"action\":\"balance_inquiry\",\"accountId\":\"acc-001\",\"outcome\":\"success\"}",
    "metadata": "{\"source\":\"branch-search-tool\",\"agentId\":\"ces-agent\"}"
  }' | jq .
```

### 2. Write a metric event

```bash
curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "metric",
    "payload": "{\"toolCallLatencyMs\":142,\"tool\":\"searchBranches\"}",
    "metadata": "{\"region\":\"europe-west1\"}"
  }' | jq .
```

### 3. Write a log event

```bash
curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "log",
    "payload": "{\"level\":\"warn\",\"message\":\"Upstream Glue timeout after 3000ms\"}",
    "metadata": null
  }' | jq .
```

### 4. Read all events

```bash
curl -s http://localhost:8080/api/poc/events | jq .
```

### 5. Read events by type

```bash
curl -s "http://localhost:8080/api/poc/events?eventType=audit" | jq .
```

### 6. Read events by time range

```bash
curl -s "http://localhost:8080/api/poc/events?from=2026-03-01T00:00:00Z&to=2026-03-31T23:59:59Z" | jq .
```

### 7. Read events by type + time range

```bash
curl -s "http://localhost:8080/api/poc/events?eventType=metric&from=2026-03-01T00:00:00Z&to=2026-03-31T23:59:59Z" | jq .
```

---

## Verification Checklist

```bash
# 1. Write 3 events
curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"audit","payload":"{\"action\":\"login\"}","metadata":null}' | jq .id

curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"metric","payload":"{\"latencyMs\":95}","metadata":null}' | jq .id

curl -s -X POST http://localhost:8080/api/poc/events \
  -H "Content-Type: application/json" \
  -d '{"eventType":"audit","payload":"{\"action\":\"transfer\"}","metadata":null}' | jq .id

# 2. Verify all 3 exist
curl -s http://localhost:8080/api/poc/events | jq 'length'
# Expected: 3

# 3. Verify type filter returns 2 audit events
curl -s "http://localhost:8080/api/poc/events?eventType=audit" | jq 'length'
# Expected: 2

# 4. Verify metric filter returns 1
curl -s "http://localhost:8080/api/poc/events?eventType=metric" | jq 'length'
# Expected: 1

# 5. Verify data persists across restarts
#    Stop the app (Ctrl+C), restart, and re-run step 2.
```

---

## Running Tests

```bash
cd java

# Unit tests only (no profile needed — pure Mockito)
mvn -pl voice-banking-app test \
    -Dtest="com.voicebanking.poc.eventstore.service.H2EventStoreServiceTest"

# Integration tests (activates poc profile via @ActiveProfiles)
mvn -pl voice-banking-app verify \
    -Dit.test="com.voicebanking.poc.eventstore.controller.EventStoreControllerIT"

# All tests
mvn -pl voice-banking-app verify
```

---

## Project Structure

```
voice-banking-app/src/main/java/com/voicebanking/poc/eventstore/
├── EventStoreAutoConfiguration.java    ← @Profile("poc") gate
├── controller/
│   └── EventStoreController.java       ← REST endpoints
├── dto/
│   ├── EventResponse.java              ← Output record
│   └── WriteEventRequest.java          ← Input record (validated)
├── model/
│   └── Event.java                      ← JPA entity
├── repository/
│   └── EventRepository.java            ← Spring Data JPA
└── service/
    ├── EventStoreService.java          ← Interface
    └── H2EventStoreService.java        ← H2 implementation

voice-banking-app/src/test/java/com/voicebanking/poc/eventstore/
├── controller/
│   └── EventStoreControllerIT.java     ← MockMvc integration test
└── service/
    └── H2EventStoreServiceTest.java    ← Mockito unit test
```

---

## Isolation Guarantees

- **`@Profile("poc")`** — entire module is invisible without the profile
- **DataSource/JPA excluded globally** — re-imported only by `EventStoreAutoConfiguration`
- **No imports** from `com.voicebanking.agent.*`, `com.voicebanking.service.*`, etc.
- **Separate DB file** — `./data/poc-events.db` (gitignored)
- **Test DB** — in-memory H2 (`src/test/resources/application-poc.yml`)
