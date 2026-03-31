# Advisory Appointment — Mock Mode & Cutover Preparation

> Documents the mock-backed API boundaries, provider seam architecture,
> and preparation for future internal advisory API integration.

## 1. Mock-Mode Assumptions

### What Is Mocked

| Component | Mock Strategy | Production Replacement |
|-----------|--------------|----------------------|
| **WireMock stubs** (mock-server) | Static JSON response files with stateful scenario chaining | Real advisory appointment upstream service |
| **BFA upstream proxy** | Points to `localhost:8080` mock-server | Internal advisory API endpoint |
| **Authentication** | `Bearer mock-appointment-service-token` hardcoded | Real IdP token exchange (EIDP → AuthZ → downstream) |
| **Customer identity** | Not validated in mock responses | Authenticated customer context from session |
| **Appointment IDs** | Fixed `APT-BRANCH-0001` with deterministic access token | UUID/sequential IDs from real booking system |
| **Time slots** | Static future dates (2030-06-18) | Dynamic availability from real scheduling engine |
| **Taxonomy tree** | Fixed JSON, 3 categories | Dynamic taxonomy from real advisory catalog |
| **Lifecycle state** | WireMock scenario state machine (in-memory, resets on restart) | Persisted state in real booking backend |

### What Is NOT Mocked

- BFA controller validation logic (real Spring Boot validation)
- BFA audit logging (real structured logging)
- OpenAPI spec generation (real SpringDoc)
- CES agent instruction and routing (real LLM evaluation)
- Bruno/curl test scripts (real HTTP requests)

### Mock Constraints

- **Stateful lifecycle resets on restart**: The WireMock scenario `advisory-appointment-lifecycle-APT-BRANCH-0001` lives in memory. Run `POST /__admin/scenarios/reset` to return to `Started` state.
- **Single appointment ID**: Only `APT-BRANCH-0001` is pre-wired. Creating multiple appointments requires additional WireMock mappings.
- **No concurrency**: The state machine is sequential — only one lifecycle flow can run at a time per scenario name.
- **Fixed dates**: Availability stubs use `2030-06-18` / `2030-06-19`. Changing dates requires new WireMock body files.

## 2. Provider Seam Architecture

### Current Provider Chain

```
CES Agent (LLM)
    ↓ OpenAPI tool calls
BFA Resource API (Spring Boot)
    ↓ HTTP proxy via AppointmentMockServerClient
Mock Server (WireMock)
    ↓ static JSON responses
```

### Future Provider Chain (Target)

```
CES Agent (LLM)
    ↓ OpenAPI tool calls (unchanged)
BFA Resource API (Spring Boot)
    ↓ HTTP client via AppointmentRealApiClient (new)
Internal Advisory API
    ↓ real booking engine
```

### Seam Location

The provider seam is currently split across two layers:

- **Upstream read seam:** `AppointmentMockServerClient` handles taxonomy,
  service-search, eligibility, and slot lookups against the mock-server.
- **Lifecycle execution seam:** `AppointmentService` plus
  `AppointmentRepository` currently own create/get/cancel/reschedule behavior
  through the in-memory runtime overlay.

What this means in practice:
- Replacing the upstream HTTP client is sufficient for read-only discovery
  calls.
- A real internal cutover for lifecycle execution will also require moving
  create/get/cancel/reschedule behavior behind a provider abstraction rather
  than leaving it embedded in the BFA overlay.
- The controller, OpenAPI surface, and CES toolset should still remain stable,
  but the service/repository seam is not yet narrow enough to call cutover a
  pure client substitution.

**Java prototype seam**: `AppointmentBookingClient` interface in `voice-banking-app/` provides a parallel seam. `MockAppointmentClient` implements it today; a real client would replace it.

### What MUST NOT Change on Cutover

| Artifact | Owner | Why |
|----------|-------|-----|
| CES `open_api_schema.yaml` | CES Agent | The LLM prompt contract — any change requires evaluation re-runs |
| BFA `AppointmentController` method signatures | BFA | The API contract surface exposed to CES |
| BFA OpenAPI spec (`advisory-appointment.json`) | BFA | Machine-verifiable contract artifact |
| Evaluation scenarios | CES Agent | Regression coverage for appointment flows |
| Bruno collection | Test Infra | Manual verification scripts |

### What WILL Change on Cutover

| Artifact | Change |
|----------|--------|
| `AppointmentMockServerClient` | Replace HTTP target URL and potentially auth headers for discovery/read APIs |
| `AppointmentService` / `AppointmentRepository` | Move lifecycle execution behind a provider abstraction instead of the in-memory overlay |
| `AppointmentUpstreamProperties` | New base-url, real auth config |
| `application.properties` | Production upstream URL, remove mock fallback token |
| WireMock stubs | Archived but no longer active |

## 3. Mock → Real Endpoint Mapping

| Mock Endpoint | Query Params | Real API Equivalent (Expected) |
|---------------|-------------|-------------------------------|
| `GET /advisory-appointments/taxonomy` | — | `GET /api/v1/advisory/topics` or catalog API |
| `GET /advisory-appointments/service-search` | `query` | Search service on advisory catalog |
| `GET /advisory-appointments/eligibility` | `consultationChannel`, `city` | Branch eligibility check API |
| `GET /advisory-appointments/availability` | `consultationChannel`, `locationId`, `selectedDay` | Scheduling/slots API |
| `POST /advisory-appointments/lifecycle` | Body: booking request | Appointment creation API |
| `GET /advisory-appointments/lifecycle/{id}` | `appointmentAccessToken` | Appointment retrieval API |
| `POST /advisory-appointments/lifecycle/{id}/cancel` | Body: reason | Appointment cancellation API |
| `POST /advisory-appointments/lifecycle/{id}/reschedule` | Body: new slot | Appointment reschedule API |

## 4. Known Gaps

| # | Gap | Impact | Mitigation |
|---|-----|--------|-----------|
| 1 | **Lifecycle seam is not yet provider-isolated** | Real cutover would still touch BFA lifecycle orchestration, not only the HTTP client | Introduce a lifecycle provider abstraction before internal API cutover |
| 2 | **No multi-appointment support** | Only `APT-BRANCH-0001` is wired in mock scenarios | Add parameterized WireMock templates or use WireMock's `transformers` |
| 3 | **No customer context validation** | Mock ignores who is calling | Real API will validate customer identity from auth token |
| 4 | **No concurrent booking protection** | WireMock state machine is sequential | Real API handles concurrency server-side |
| 5 | **Static taxonomy** | Categories don't update dynamically | Real API would serve current catalog |
| 6 | **No advisor assignment** | Mock creates appointments without matching advisors | Real API integrates with advisor scheduling |
| 7 | **No email/SMS confirmation** | Mock doesn't send confirmation messages | Real API triggers notifications |
| 8 | **No recurring appointments** | Not modeled in current API surface | Future feature if needed |
| 9 | **`getMyAppointments` not in BFA** | Java prototype has it, BFA resource API does not | Add to BFA if needed post-cutover |
| 10 | **No idempotency tokens** | Create/cancel/reschedule could duplicate on retry | Real API should support idempotency keys |
| 11 | **Fixed future dates (2030)** | Mock slots use 2030-06-18 | Real API returns actual available dates |

## 5. Pre-Cutover Checklist

When an internal advisory API becomes available:

- [ ] Stand up real advisory API endpoint
- [ ] Create `AppointmentRealApiClient` implementing same interface patterns
- [ ] Extract lifecycle execution behind a provider abstraction used by `AppointmentService`
- [ ] Update `application.properties` with real upstream URL and auth
- [ ] Run all Bruno collection tests against real API
- [ ] Run BFA bash test script against real API
- [ ] Re-run CES evaluation scenarios (routing, booking flow, cancel/reschedule, phone consultation)
- [ ] Verify OpenAPI spec compatibility (no breaking changes to CES contract)
- [ ] Archive WireMock appointment stubs (keep for regression)
- [ ] Update this document with real API details
