# AGENT-007: Advisory Appointment Agent Preparation and Mock-Backed Implementation Plan

> PHASE: IMPLEMENT_ALLOWED
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**FR Number:** AGENT-007  
**Plan ID:** AGENT-007  
**Title:** Advisory Appointment Agent — Preparation-First Implementation Plan  
**Author:** GitHub Copilot  
**Created:** 2026-03-15  
**Status:** IN_PROGRESS  
**Implementation Status:** Steps 1-6 implemented; Steps 7-8 partially implemented  
**Estimated Effort:** 8–12 implementation days, excluding future internal API integration  
**Estimated Credits:** 15–25k tokens for planning, review, and implementation follow-up  
**Delivery Mode:** Preparation-first, contract-first, mock-backed  

**Related Documents:**
- `ces-agent/docs/advisory-agent/implementation-plan/RESEARCH-FINDINGS.md`
- `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/AGENT-README.md`
- `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow.mmd`
- `ces-agent/docs/advisory-agent/architecture/reviews/appointments-flow-physical-path-and-latency-analysis.md`
- `ces-agent/docs/advisory-agent/architecture/appointments-preparation-topology-decision-2026-03-15.md`
- `ces-agent/docs/advisory-agent/architecture/high-level-advisory-subagent-architecture.md`
- `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md`
- `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml`
- `ces-agent/docs/agent-use-cases/location-services-agent.md`
- `ces-agent/acme_voice_agent/toolsets/location/open_api_toolset/open_api_schema.yaml`

**Primary Constraint:** Internal advisory APIs are not currently accessible. This plan therefore focuses on preparation by establishing a strong resource API, a file-backed mock domain, a CES-ready toolset, and clear seams for future replacement with real integrations.

**Approval status:** `APPROVE_IMPLEMENTATION` was received on 2026-03-15. The decision-freeze sections in this plan are now the pre-build baseline for implementation work in this repository.

---

## Implementation Summary

Status meanings used below:
- `Implemented` = the planned artifact exists in the repository
- `Preapproved` = approved to implement next, but not yet built in the repository
- `Partially implemented` = the baseline artifact exists, but the runtime/build work is still incomplete
- `Not implemented` = the planned runtime artifact does not yet exist in the repository

- **Step 1 — Finalize scope, use cases, and target contract:** `Implemented`
  Documentation baseline now includes the field-level schema appendix, root-agent routing precedence, Step 7 prototype decision, and the WireMock lifecycle state model.
- **Step 2 — Build appointment resource package in `bfa-service-resource`:** `Implemented`
  The `com.voicebanking.bfa.appointment` package now exists with controller, service, repository, slot logic, upstream client, OpenAPI group, and DTOs, and `bfa-service-resource` compiles cleanly.
- **Step 3 — Create file-backed appointment fixtures in `java/mock-server`:** `Implemented`
  Deterministic WireMock mappings, body files, and integration tests now exist for taxonomy, service search, eligibility, availability, lifecycle scenarios, and negative header/auth cases.
- **Step 4 — Implement booking search and lifecycle behavior:** `Implemented`
  `bfa-service-resource` now consumes upstream eligibility and slot fixtures, preserves a deterministic in-memory lifecycle overlay, and exposes structured fallback responses for no-availability journeys.
- **Step 5 — Export OpenAPI and wire CES toolset:** `Implemented`
  OpenApiSpecExportTest generates `openapi-specs/advisory-appointment.json` on every build. CES toolset descriptor and agent-tuned OpenAPI schema are finalized at version 1.0.0.
- **Step 6 — Add CES agent spec, instruction, and evaluation coverage:** `Implemented`
  Agent descriptor, instruction, root agent routing, and the advisory evaluation pack now cover routing, branch, phone, video, service-request, recovery, contact-repair, lifecycle, blocked-reschedule, and human-handoff journeys.
- **Step 7 — Align the Java prototype and integration seams:** `Partially implemented`
  The concept alignment matrix exists, but the Java prototype still exposes the legacy tool and request model and has not yet been refactored onto the BFA resource contract.
- **Step 8 — Hardening and internal-API cutover preparation:** `Partially implemented`
  The mock-mode cutover document, smoke script, and Bruno collection exist, but the provider seam still spans the BFA lifecycle overlay in addition to the upstream client and is not yet a pure client swap.

---

## Summary at a Glance

| Item | Summary |
|------|---------|
| Goal | Prepare a contract-first advisory appointment capability and agent without depending on currently inaccessible internal advisory APIs. |
| Primary Outcome | A stable appointment API in `java/bfa-service-resource`, a dedicated CES appointment agent/toolset, and upstream mock-backed scenarios served from `java/mock-server`. |
| Systems / Repos | `java/mock-server`, `java/bfa-service-resource`, `ces-agent`, and the optional alignment seam in `java/voice-banking-app`. |
| Dependencies | Research findings, existing branch master data, `java/mock-server` auth/header conventions, and future internal advisory APIs for later cutover. |
| Implementation Status | Steps 1-6 implemented; Steps 7-8 partially implemented. |
| Key Decisions | Keep appointment orchestration separate from location services, store fixture truth in `java/mock-server`, and let BFA consume and normalize upstream responses. |
| Main Risks | Later advisory edge cases may still surface, appointment lifecycle/state modeling is more complex than branch lookup, and real internal APIs may diverge from the mock assumptions. |
| Out of Scope | Real internal advisory integration, production-grade token exchange, real email or video-link delivery, and CRM enrichment. |
| Review Focus | Confirm the domain boundary, endpoint surface, fixture ownership, and the preparation-versus-production phase split. |

## Executive Summary

This plan prepares Acme Bank’s appointment capability as a proper advisory-booking domain rather than an extension of branch lookup. The implementation will introduce a new `advisory_appointment_agent` experience anchored in a strong resource-oriented API, backed initially by deterministic WireMock fixture files in the `java/mock-server` module and consumed by a specialized BFA appointment domain instead of inaccessible internal advisory systems.

The core design choice is to separate three concerns that are currently mixed or under-modeled: general branch discovery, advisory appointment orchestration, and backend appointment execution. The result will be a preparation-grade platform that supports realistic voice use cases today, while preserving a clean adapter seam for future internal advisory APIs. The immediate deliverable is not real production scheduling; it is a reviewable, testable, API-first preparation layer that mirrors the discovered Deutsche Bank booking workflow closely enough to validate conversation design, contract shape, CES tool wiring, and mocked scenario coverage.

| Step | Description | Effort | Risk |
|------|-------------|--------|------|
| 1 | Finalize scope, use cases, and target contract | 1 day | LOW |
| 2 | Build appointment resource package in `bfa-service-resource` | 1.5–2 days | MEDIUM |
| 3 | Create file-backed seed data and repositories | 1–1.5 days | MEDIUM |
| 4 | Implement booking search and lifecycle endpoints | 2–3 days | MEDIUM |
| 5 | Export OpenAPI and wire CES toolset | 1–1.5 days | LOW |
| 6 | Add CES agent spec, instruction, and evaluations | 1.5–2 days | MEDIUM |
| 7 | Align Java prototype and integration seams | 1–1.5 days | MEDIUM |
| 8 | Hardening, review, and future API cutover prep | 0.5–1 day | LOW |

---

## Prerequisites

This implementation can start only against the preparation-first baseline defined in this document. The goal is to convert the research into a buildable contract, not to begin speculative production integration.

### Required Inputs Before Build

- `ces-agent/docs/advisory-agent/implementation-plan/RESEARCH-FINDINGS.md` has been reviewed and remains the factual source for appointment-flow discovery.
- `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` exists and is the canonical CES use-case reference for H1 journeys.
- `ces-agent/docs/advisory-agent/architecture/appointments-preparation-topology-decision-2026-03-15.md` and `ces-agent/docs/advisory-agent/architecture/high-level-advisory-subagent-architecture.md` are accepted as the topology baseline.
- `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` remains the concrete contract baseline until the BFA-generated export exists.
- The `java/mock-server` module and the `java/bfa-service-resource` branch dataset are available for implementation and test wiring.

### Scope Boundaries Applied Before Implementation

**In scope**
- new advisory-appointment domain in `java/bfa-service-resource`
- specialized BFA appointment-domain consumption of file-backed mock data served from `java/mock-server`
- CES OpenAPI toolset for advisory appointment actions
- new CES-facing agent identity and instruction model
- realistic voice-oriented booking flows for branch, phone, and video consultations
- simulated lifecycle support for create, view, cancel, and reschedule
- test and evaluation coverage for the mocked domain

**Out of scope for this phase**
- direct integration with internal advisory APIs
- real email delivery or video-link generation
- production-grade token exchange to internal booking backends
- real CRM enrichment or customer master data resolution
- desired-location consultations beyond placeholder design hooks
- partner lookup or secure deep-link cancellation hash flows beyond contract placeholders

---

## Architecture Overview

### Current Architecture

#### What Exists Today

1. A Java prototype `AppointmentContextAgent` exists in `java/voice-banking-app`.
2. That prototype includes lifecycle operations and basic slot booking behavior.
3. The current mock implementation is code-generated and randomish, not file-backed and not contract-first.
4. Generic branch search already exists in `java/bfa-service-resource` and is backed by `branches.json`.
5. CES already consumes location functionality via an OpenAPI toolset.
6. Research findings now document the original Deutsche Bank booking flow in sufficient detail to design a stronger domain.
7. The `java/mock-server` module already behaves like an upstream protected data API with token flow stubs, header matching, and deterministic WireMock priorities.

#### Current Gap

The missing piece is not just data. It is the absence of a preparation-grade appointment resource API that models:
- entry path
- topic selection
- consultation channel
- booking-specific branch eligibility
- date-first then slot selection
- structured contact capture
- spoken summary and confirmation
- fallback behavior when slots are unavailable

The current Java appointment prototype is useful as a backend executor model, but it is not yet a good domain contract for CES or for multi-step appointment orchestration.

---

### Target Architecture

#### End State After This Plan Is Implemented

Acme will have a mock-backed advisory appointment capability with four aligned layers:

1. **Use-case layer**
   - persona-driven use case specification for `advisory_appointment_agent`
   - explicit H1 and P2 journey boundaries

2. **Resource API layer**
   - Spring Boot resource-oriented appointment API in `java/bfa-service-resource`
   - shared response wrapper and standalone OpenAPI group
   - deterministic file-backed data model

3. **CES toolset and agent layer**
   - dedicated appointment OpenAPI toolset
   - dedicated `advisory_appointment_agent`
   - instruction set that treats branch search as one part of booking, not the whole identity

4. **Prototype alignment layer**
   - optional alignment of `java/voice-banking-app` appointment classes to the stronger domain concepts
   - explicit adapter seam for future internal advisory API replacement

#### Architectural Principle

The booking agent must be anchored in **appointment orchestration**, not **generic location discovery**.

Generic location search remains useful for public branch/ATM queries. Appointment booking, however, requires booking-specific eligibility, channel logic, slot logic, validation, and lifecycle behavior. Those capabilities should belong to the appointment domain even if they reuse the same branch master data.

---

### Design Principles

1. **Contract first:** The API contract is the system anchor for CES, tests, and future real integration.
2. **Mock data by files, owned upstream:** Appointment fixtures must live in JSON files inside `java/mock-server`, where the future underlying advisory API is being simulated.
3. **BFA consumes; it does not own fixture truth:** `bfa-service-resource` may translate, validate, and enrich upstream responses, but it must not become the canonical owner of appointment mock data.
4. **Deterministic scenarios over randomness:** Mock behavior should support repeatable journeys and evaluations.
5. **Booking-specific branch lookup:** Appointment branch search must answer booking eligibility questions, not just location questions.
6. **Voice-first flow:** The contract must support date-first slot selection, concise branch options, repair prompts, and explicit confirmation.
7. **Replaceable provider seam:** A future internal advisory API should be swappable without breaking CES or the mock-backed tests.

---

### Decision Freeze

The following decisions close the pre-build ambiguities identified during readiness review. Unless a later ADR supersedes them, implementation should treat these as fixed.

#### Approved Preparation Topology

- **Preparation-phase runtime path:** CES OpenAPI toolset → `java/bfa-service-resource` → `java/mock-server`.
- **Not part of this build:** the `bfa-gateway` → AG-004 adapter → Glue path documented in the architecture review remains the later internal-integration target, not the current implementation topology.
- **Reason:** `bfa-service-resource` is the existing CES-consumed REST surface in this repository; using it directly minimizes speculative infrastructure work while keeping the provider seam behind the appointment domain.

#### Access and Security Model

All BFA appointment endpoints are **transport-authenticated** by CES using the existing service-agent ID token pattern. This is service-to-service authentication, not end-customer authentication.

| Endpoint | CES transport auth | `AI_INTERACTION` consent | Customer legitimation | Appointment access token |
|----------|--------------------|--------------------------|-----------------------|--------------------------|
| `GET /api/v1/appointment-taxonomy` | Required | Not required | Not required | Not required |
| `GET /api/v1/appointment-service-search` | Required | Not required | Not required | Not required |
| `GET /api/v1/appointment-branches` | Required | Not required | Not required | Not required |
| `GET /api/v1/appointment-slots` | Required | Not required | Not required | Not required |
| `POST /api/v1/appointments` | Required | Not required | Not required | Not required |
| `GET /api/v1/appointments/{appointmentId}` | Required | Not required | Not required | Required |
| `POST /api/v1/appointments/{appointmentId}/cancel` | Required | Not required | Not required | Required |
| `POST /api/v1/appointments/{appointmentId}/reschedule` | Required | Not required | Not required | Required |

Additional rules:
- **No customer consent gate** is applied in H1 mock mode because the booking flow must support unauthenticated prospects and walk-ins.
- **No resource legitimation** is applied in H1 mock mode because no customer-owned banking resource is being accessed.
- **Lifecycle protection** is provided by an opaque `appointmentAccessToken` returned by create/retrieve responses and required for retrieve, cancel, and reschedule.
- The future secure deep-link hash model will replace or alias this token; the H1 mock token is the compatibility seam.

#### Mutable State Ownership

- `java/mock-server` owns the **immutable fixture catalog**: taxonomy, service search, eligibility seeds, slot templates, lifecycle templates, and negative cases.
- `bfa-service-resource` owns the **mutable runtime overlay** for booked appointments and reserved slots in mock mode.
- `bfa-service-resource` must not write back to WireMock JSON files and must not duplicate the full upstream fixture catalog.
- Reset strategy:
  - unit and integration tests reset the BFA in-memory overlay before each run
  - `java/mock-server` fixtures remain deterministic and stateless across runs
- WireMock scenarios may still be used for endpoint-level negative-path coverage, but they are **not** the primary reservation engine.

#### Mock-Server Contract Profile

The appointment upstream in `java/mock-server` will use a simplified **service-authenticated** profile that is separate from the customer-data token dance:

| Concern | H1 mock decision |
|---------|------------------|
| Base path | `/advisory-appointments/...` under the existing WireMock root |
| Required auth header | `Authorization: Bearer <service-token>` |
| Required client header | `X-BFA-Client: advisory-appointment-bff` |
| Correlation | `X-Correlation-ID` propagated end-to-end |
| Deterministic scenario selection | optional `X-Mock-Scenario` header for test-only forcing |
| Customer headers such as `DB-ID` / `deuba-client-id` | Not required for the appointment domain in H1 |

This keeps appointment build scope focused on domain behavior while leaving real downstream token exchange explicitly out of scope for this phase.

#### Prototype Role

- `java/voice-banking-app`'s `AppointmentContextAgent` remains a **legacy prototype and compatibility reference**, not the contract source of truth for AGENT-007.
- The canonical H1 contract source is:
  1. this implementation plan
  2. `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md`
  3. `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml`
- Prototype-only capability for now:
  - `getMyAppointments` remains outside the new BFA appointment H1 surface until customer master lookup and authenticated appointment ownership are designed.
- **Resolved Step 7 decision:** the prototype will be **aligned to the new BFA appointment resource model** in Step 7. It will not remain as an independent competing model. Alignment is limited to naming, request shape, lifecycle semantics, and provider seams; it does not pull `getMyAppointments` into the H1 CES contract.

#### Root Agent Routing and Handoff Precedence

- `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` must add `advisory_appointment_agent` as a child agent.
- `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` must define appointment routing **before** generic location routing.
- **Route to `advisory_appointment_agent`** when the utterance contains advisory-booking or lifecycle intent, including booking-qualified branch/location phrasing such as:
  - "book an appointment in Munich"
  - "schedule a mortgage consultation"
  - "I need to talk to an advisor next week"
  - "move my appointment"
  - "cancel my video consultation"
  - equivalent German variants such as "Termin vereinbaren", "Beratung buchen", "Termin verschieben", or "Termin absagen"
- **Route to `location_services_agent`** only when the utterance is limited to public location discovery with **no** booking or lifecycle intent, for example branch address, opening hours, ATM lookup, accessibility, parking, or directions.
- **Routing precedence rule:** if an utterance contains both location cues and booking cues, appointment routing wins. Example: "I need an appointment at the Munich branch" must route to `advisory_appointment_agent`, not `location_services_agent`.
- **Identification rule for H1 mock mode:** public appointment-booking and public location-discovery requests bypass the current mandatory partner-ID collection at the root agent. Identification remains required only for customer-owned data access and account-specific operations.

---

### Proposed Domain Contract

#### Domain Capabilities Required

The appointment API must support the following capability families.

| Capability Family | Purpose | H1 Priority |
|-------------------|---------|-------------|
| Taxonomy and rules | Entry path, topic, channel, validation requirements | Must-have |
| Booking-specific branch search | Eligible branches or advisory centers for a request | Must-have |
| Slot discovery | Day-first and time-slot retrieval | Must-have |
| Appointment lifecycle | Create, retrieve, cancel, reschedule | Must-have |
| Service-request search | Free-text mapping for service appointment path | Should-have |
| Advanced routing | Desired-location, named advisor, deep-link security | Later |

#### Recommended API Surface

The API should be designed around the following resources.

| Endpoint | Purpose | Notes |
|----------|---------|-------|
| `GET /api/v1/appointment-taxonomy` | Return entry paths, topics, channels, advisor modes, validation hints | CES bootstrapping and prompt logic |
| `GET /api/v1/appointment-service-search` | Return service-topic matches for free-text service search | Mock-backed search index |
| `GET /api/v1/appointment-branches` | Return booking-eligible branches or advisory centers | Reuses branch data plus appointment capabilities |
| `GET /api/v1/appointment-slots` | Return available days and slots for branch/channel/topic combination | Date-first selection support |
| `POST /api/v1/appointments` | Create appointment after explicit confirmation | Simulated confirmation outcome |
| `GET /api/v1/appointments/{appointmentId}` | Retrieve appointment details | Used by modify/cancel follow-up |
| `POST /api/v1/appointments/{appointmentId}/cancel` | Cancel appointment | Mock lifecycle update |
| `POST /api/v1/appointments/{appointmentId}/reschedule` | Reschedule appointment to a new slot | Mock lifecycle update |

#### Why This Contract

This surface is intentionally narrower and stronger than mirroring each UI screen. It captures the domain checkpoints required by voice booking without tying the implementation to a specific front-end wizard.

#### Concrete Contract Baseline

The approval baseline for parallel work is the combination of:
- this plan's `Schema Appendix` section
- `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml`

Until the generated BFA spec exists, the schema appendix is the normative Step 1 field baseline and the draft YAML must stay aligned with it for request fields, response fields, and error vocabulary.

#### Contract Summary by Endpoint

| Endpoint | Required request fields | Primary response fields | Notes |
|----------|-------------------------|-------------------------|-------|
| `GET /api/v1/appointment-taxonomy` | none | `entryPaths[]`, `topics[]`, `consultationChannels[]`, `advisorModes[]`, `validationRules[]` | bootstraps CES prompts |
| `GET /api/v1/appointment-service-search` | `query`; optional `entryPath` | `matches[]`, `fallbackGuidance[]` | used for service-request path |
| `GET /api/v1/appointment-branches` | `entryPath`, `consultationChannel`, and either `topicCode` or `serviceCode`; branch flows also require one location hint (`city`, `postalCode`, or `address`) | `locations[]`, `count`, `totalMatches`, `fallbackSuggestions[]` | `locations` may include remote centers for phone/video |
| `GET /api/v1/appointment-slots` | `entryPath`, `consultationChannel`, `locationId`, and either `topicCode` or `serviceCode` | `locationId`, `timezone`, `availableDays[]`, `slots[]`, `fallbackSuggestions[]` | if `selectedDay` is omitted, `slots[]` may be empty and CES should choose a day first |
| `POST /api/v1/appointments` | booking context, contact details, `selectedTimeSlotId`, and `summaryConfirmed=true` | `appointment`, `appointmentAccessToken`, `delivery` | create must be impossible without explicit confirmation |
| `GET /api/v1/appointments/{appointmentId}` | path `appointmentId` and query `appointmentAccessToken` | appointment view fields including `appointmentId`, `status`, `scheduledStart`, `scheduledEnd`, `timeline[]`, and `delivery` | no list endpoint in H1 |
| `POST /api/v1/appointments/{appointmentId}/cancel` | path `appointmentId`, `appointmentAccessToken`, optional `reason`, `summaryConfirmed=true` | appointment view fields including updated `status`, `timeline[]`, and `delivery` | reason is optional in H1 |
| `POST /api/v1/appointments/{appointmentId}/reschedule` | path `appointmentId`, `appointmentAccessToken`, `selectedTimeSlotId`, `summaryConfirmed=true` | appointment view fields including updated `status`, `scheduledStart`, `scheduledEnd`, `timeline[]`, and `delivery` | slot must already exist in slot search output |

#### Error Vocabulary Freeze

The following error codes are approved for H1 mock mode:
- `VALIDATION_FAILED`
- `SERVICE_SEARCH_NO_MATCH`
- `LOCATION_REQUIRED`
- `NO_ELIGIBLE_LOCATIONS`
- `NO_SLOTS_AVAILABLE`
- `APPOINTMENT_NOT_FOUND`
- `APPOINTMENT_ACCESS_DENIED`
- `SUMMARY_CONFIRMATION_REQUIRED`
- `RESCHEDULE_WINDOW_CLOSED`
- `CANCEL_WINDOW_CLOSED`
- `UPSTREAM_UNAVAILABLE`

---

### Data Strategy

#### Canonical Existing Data to Reuse

`java/bfa-service-resource/src/main/resources/data/branches.json` remains the canonical source for branch identity, address, accessibility, and public service attributes.

Appointment-specific mock data, however, should not be added to `bfa-service-resource` as local seed files. It should be owned by the `java/mock-server` module, because that module is the closest stand-in for the eventual underlying advisory data API.

#### Ownership Split

| Layer | Responsibility |
|------|----------------|
| `java/mock-server` | Owns WireMock mappings, body files, upstream auth behavior, and deterministic appointment fixtures |
| `bfa-service-resource` | Owns the specialized appointment domain that consumes the mock-server over HTTP, translates upstream payloads, applies BFA validation, and exposes the stable BFA contract |
| CES / Voice app | Owns conversational orchestration, summarization, confirmation, and tool usage |

#### Proposed Appointment Data Facets in `java/mock-server`

The appointment data should be organized by facet so the underlying mock API stays legible and reviewable.

| Facet | Purpose |
|------|---------|
| Taxonomy | Entry paths, topics, channels, advisor modes |
| Service search | Free-text service lookup and topic mapping |
| Eligibility | Booking-specific branch and advisor eligibility |
| Availability | Available day and slot responses |
| Lifecycle | Retrieve, create, cancel, and reschedule appointment outcomes |
| Validation | Required fields, conditional capture rules, and repair hints |
| Errors and fallback | No slots, unsupported combinations, validation failures, callback suggestions |

#### Proposed `java/mock-server` Mapping Layout

| Path in `java/mock-server` | Purpose |
|------|---------|
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/taxonomy/get-taxonomy.json` | Stub the upstream taxonomy endpoint |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/service-search/get-service-search.json` | Stub free-text service search |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/eligibility/get-eligible-branches.json` | Stub booking-specific branch/advisor eligibility |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/availability/get-slots.json` | Stub slot/day retrieval |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/get-appointment.json` | Stub retrieve-by-id |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/create-appointment.json` | Stub create outcome |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/cancel-appointment.json` | Stub cancel outcome |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/reschedule-appointment.json` | Stub reschedule outcome |
| `src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/errors/*.json` | Negative cases and fallback responses |

#### Proposed `java/mock-server` Body File Layout

Large appointment payloads should prefer `bodyFileName` with files under `__files/`, rather than large inline `jsonBody` mappings.

| File | Purpose |
|------|---------|
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/taxonomy/appointment-taxonomy.json` | Entry paths, topics, channels, advisor modes |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/service-search/service-catalog.json` | Search vocabulary and topic mapping |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/eligibility/branch-capabilities.json` | Booking eligibility by branch/topic/channel |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/eligibility/advisors.json` | Advisor identities, specialties, and supported channels |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/availability/*.json` | Deterministic slot inventory responses keyed by scenario |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/lifecycle/details/*.json` | Existing mock appointments for retrieval flows |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/lifecycle/create/*.json` | Create-success and create-failure outcomes |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/lifecycle/cancel/*.json` | Cancel-success and cancel-blocked outcomes |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/lifecycle/reschedule/*.json` | Reschedule-success and reschedule-blocked outcomes |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/validation/validation-rules.json` | Field rules and conditional capture hints |
| `src/main/resources/wiremock/__files/mobile.api/advisory-appointments/errors/*.json` | No-slots, unsupported path, and repair responses |

#### Runtime State Model

The implementation should not write back to JSON files. Instead:
- `java/mock-server` JSON files remain the canonical fixture source.
- `bfa-service-resource` holds the mutable in-memory overlay for slot reservations and appointment lifecycle changes.
- WireMock scenarios are optional for endpoint-level negative cases, but not the source of truth for reservation state.
- `bfa-service-resource` must not duplicate the full appointment fixture catalog.
- Tests should reset any BFA in-memory orchestration state per run and, where used, reset WireMock scenarios between cases.

This approach keeps fixture ownership where the upstream API is simulated while still allowing the specialized BFA appointment domain to behave like a real consumer.

#### Deterministic Scenario Coverage

Seed data must intentionally support review-friendly scenarios such as:
- successful branch consultation booking
- successful phone consultation booking
- successful video consultation booking
- no slot available at first branch but available at second branch
- invalid email repair
- invalid phone repair
- existing customer with additional identifiers
- service-request path with mandatory comment
- cancellation success
- reschedule success
- reschedule blocked by timing rule

---

### Module and Package Placement

#### Primary Implementation Home

The new appointment resource API should be implemented in:
- `java/bfa-service-resource`

This matches the existing location-service pattern and keeps CES-facing resource contracts in the same resource-oriented backend module.

#### Mock Upstream Home

The appointment fixture files and upstream mock API mappings should be implemented in:
- `java/mock-server`

This keeps appointment mock data close to the potential underlying advisory API boundary rather than embedding it inside the BFA resource service.

#### Proposed Backend Package

`com.voicebanking.bfa.appointment`

#### Proposed Backend Structure

| Path | Role |
|------|------|
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentController.java` | REST endpoints |
| `.../AppointmentService.java` | Domain orchestration and validation |
| `.../AppointmentMockServerClient.java` | Typed outbound client for the `java/mock-server` appointment API |
| `.../AppointmentUpstreamProperties.java` | Base URL, headers, and mock-upstream configuration |
| `.../AppointmentOpenApiConfig.java` | Standalone OpenAPI group |
| `.../AppointmentTaxonomyService.java` | Taxonomy/read-model support |
| `.../AppointmentRepository.java` | Translation/cache layer over upstream responses and any ephemeral orchestration state |
| `.../AppointmentBranchResolver.java` | Booking-specific branch eligibility resolution |
| `.../AppointmentSlotService.java` | Slot search and slot reservation logic |
| `.../dto/...` or same package request/response records | API shapes |
| `.../model/...` or same package records | Domain objects if separate from DTOs |

#### Proposed `mock-server` Structure for Appointment Fixtures

| Path | Role |
|------|------|
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/...` | Upstream endpoint matching rules |
| `java/mock-server/src/main/resources/wiremock/__files/mobile.api/advisory-appointments/...` | JSON payload bodies referenced by mappings |
| `java/mock-server/src/test/java/com/acme/banking/demoaccount/...` | Integration tests for appointment stubs and scenario flows |
| `java/mock-server/bruno/...` | Manual request collection for taxonomy, slots, create, cancel, and reschedule |

#### CES Toolset Placement

| Path | Role |
|------|------|
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/advisory_appointment.json` | Toolset descriptor |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` | CES-importable OpenAPI schema |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/advisory_appointment_agent.json` | Agent descriptor |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt` | Agent instruction |

#### Documentation Placement

| Path | Role |
|------|------|
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Persona-driven use case specification |
| `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md` | This implementation plan |
| `ces-agent/docs/advisory-agent/implementation-plan/RESEARCH-FINDINGS.md` | Research foundation |
| `ces-agent/docs/advisory-agent/architecture/high-level-advisory-subagent-architecture.md` | High-level architecture |

---

## Implementation Steps

Mandatory implementation-plan requirements for this section:
- Start with a short implementation-step summary and status table.
- Keep each step status current as work progresses.
- Keep every step-level success-criteria section as a checkbox checklist.
- Check a success criterion only after verification by tests, manual validation, document review, or other explicit evidence.

These requirements are mandatory for this implementation plan. Step progress must remain reviewable from the summary table, and a checked success criterion must always mean the underlying evidence already exists.

| Step | Short Summary | Status |
|------|---------------|--------|
| Step 1 | Freeze the contract, routing precedence, schema appendix, and lifecycle state model. | Implemented |
| Step 2 | Create the appointment resource package in `bfa-service-resource` with the controller, service, repository, slot logic, DTOs, and OpenAPI group. | Implemented |
| Step 3 | Add deterministic advisory appointment fixtures, mappings, and lifecycle scenarios in `mock-server`. | Implemented |
| Step 4 | Make BFA booking search, slot handling, and lifecycle mutations work against the mock-backed contract with deterministic runtime state. | Implemented |
| Step 5 | Export the appointment OpenAPI contract and wire the CES toolset to the generated spec. | Implemented |
| Step 6 | Finalize the CES appointment agent spec, instruction set, and evaluation coverage. | Implemented |
| Step 7 | Align the Java appointment prototype and integration seams to the approved BFA resource model. | Partially implemented |
| Step 8 | Harden the implementation and prepare the seam for future internal advisory API cutover. | Partially implemented |

## Step 1: Finalize the Reviewable Domain Contract

**Objective:** Convert the research into a stable preparation contract before any runtime behavior is built.

**Effort:** 1 day  
**Risk:** LOW

### Files to Create or Modify

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md` | Finalize and maintain the implementation plan |
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Add new use-case specification for appointment journeys |
| `ces-agent/docs/advisory-agent/implementation-plan/RESEARCH-FINDINGS.md` | Reference only; update only if new evidence is found during review |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` | Freeze root-agent child-agent wiring and advisory-appointment routing precedence |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` | Freeze root-agent utterance triggers so appointment booking is not intercepted by generic location routing |

### Work to Perform

- Confirm the new CES-facing identity is `advisory_appointment_agent`.
- Lock H1 scope to branch, phone, and video appointment journeys.
- Treat desired-location consultations as a later phase.
- Define the final endpoint list and request/response responsibilities.
- Add a field-level schema appendix that freezes request and response DTOs for all eight endpoints.
- Freeze root-agent routing precedence so appointment-booking and appointment-lifecycle requests reach `advisory_appointment_agent` before `location_services_agent`.
- Resolve the Step 7 prototype decision in favor of aligning `AppointmentContextAgent.java` to the BFA resource model.
- Freeze the WireMock lifecycle scenario states that Step 3 must implement.
- Define the seed-data set and naming conventions.
- Map each use case to one or more API calls.

### Success Criteria

- [x] endpoint inventory is approved
- [x] field-level request and response DTO schemas exist for all eight endpoints
- [x] H1 vs later scope is explicit
- [x] root-agent routing precedence between appointment booking and generic location lookup is defined
- [x] seed-data inventory and WireMock lifecycle state model are approved
- [x] the booking agent is clearly separated from `location_services_agent`
- [x] Step 7 explicitly aligns the Java prototype to the BFA resource model

---

## Step 2: Introduce the Appointment Resource Package in `bfa-service-resource`

**Objective:** Create the resource-oriented backend skeleton that mirrors the location-service pattern while consuming the sibling `mock-server` as its upstream appointment data API.

**Effort:** 1.5–2 days  
**Risk:** MEDIUM

### Files to Create or Modify

| File | Change |
|------|--------|
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentController.java` | Add appointment resource endpoints |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentService.java` | Add orchestration logic |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentMockServerClient.java` | Add typed outbound client for the mock upstream |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentUpstreamProperties.java` | Add configuration for base URL and required headers |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentOpenApiConfig.java` | Add standalone OpenAPI group |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentRepository.java` | Add seed/runtime repository |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentBranchResolver.java` | Add booking-specific branch eligibility logic |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentSlotService.java` | Add slot resolution and reservation logic |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/...request and response records` | Add typed DTOs |

### Design Notes

- Reuse `ApiResponse<T>` for consistency.
- Reuse the audit and transport-auth patterns already used by `LocationController`, but do **not** apply `@RequiresConsent` or `@RequiresLegitimation` in H1 mock mode.
- Keep business rules in `AppointmentService` and `AppointmentSlotService`, not in the controller.
- Keep the LLM/CES responsible for natural-language understanding; the API should remain structured and deterministic.
- Mirror the existing `RestClient` style already used elsewhere in the Java codebase for typed upstream consumption.
- Translate BFA auth/context into the frozen mock-upstream header profile (`Authorization`, `X-BFA-Client`, `X-Correlation-ID`, optional `X-Mock-Scenario`) instead of embedding local JSON files in the BFA module.

### Success Criteria

- [x] backend package compiles
- [x] endpoint skeletons exist for all H1 contract actions
- [x] standalone OpenAPI group is registered
- [x] no internal advisory API dependency is introduced yet
- [x] the BFA appointment domain is positioned as a consumer of `mock-server`, not as the owner of appointment fixture files

---

## Step 3: Add File-Backed Appointment Fixtures in `mock-server`

**Objective:** Replace in-code appointment randomness with inspectable, deterministic WireMock fixtures in the `java/mock-server` module.

**Effort:** 1–1.5 days  
**Risk:** MEDIUM

### Files to Create

| File | Change |
|------|--------|
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/taxonomy/get-taxonomy.json` | Add upstream taxonomy mapping |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/service-search/get-service-search.json` | Add service-search mapping |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/eligibility/get-eligible-branches.json` | Add branch/advisor eligibility mapping |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/availability/get-slots.json` | Add deterministic slot lookup mapping |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/*.json` | Add retrieve/create/cancel/reschedule mappings |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/errors/*.json` | Add fallback and negative mappings |
| `java/mock-server/src/main/resources/wiremock/__files/mobile.api/advisory-appointments/...` | Add fixture payload bodies for all appointment data facets |

### Design Notes

- Prefer `bodyFileName` and `__files/` for large appointment payloads instead of oversized inline `jsonBody`.
- Keep WireMock priority ordering explicit, following the existing mock-server style for auth and customer-data stubs.
- Eligibility and slot payloads should reference existing branch IDs from `branches.json` so BFA can enrich upstream results with branch metadata.
- Lifecycle mappings may use WireMock scenarios to simulate deterministic state transitions for create, cancel, and reschedule.
- Fixture coverage must include branch, phone, and video scenarios plus negative and recovery cases.

#### Lifecycle WireMock Scenario State Machine

Step 3 will use one deterministic lifecycle scenario per booked fixture:

- **Scenario name pattern:** `advisory-appointment-lifecycle-{appointmentId}`
- **Primary states:** `STARTED`, `BOOKED`, `RESCHEDULED`, `CANCELLED`
- **State intent:**
  - `STARTED`: no appointment has been created yet for the scenario
  - `BOOKED`: active appointment exists in its original slot
  - `RESCHEDULED`: active appointment exists in its replacement slot
  - `CANCELLED`: terminal state; appointment is no longer active

Required WireMock state transitions:

| Mapping | `scenarioName` | `requiredScenarioState` | `newScenarioState` | Purpose |
|---------|----------------|-------------------------|--------------------|---------|
| `create-appointment.json` | `advisory-appointment-lifecycle-{appointmentId}` | `STARTED` | `BOOKED` | create new appointment |
| `get-appointment-booked.json` | `advisory-appointment-lifecycle-{appointmentId}` | `BOOKED` | unchanged | retrieve active appointment |
| `cancel-appointment-booked.json` | `advisory-appointment-lifecycle-{appointmentId}` | `BOOKED` | `CANCELLED` | cancel active appointment |
| `reschedule-appointment-booked.json` | `advisory-appointment-lifecycle-{appointmentId}` | `BOOKED` | `RESCHEDULED` | move active appointment to replacement slot |
| `get-appointment-rescheduled.json` | `advisory-appointment-lifecycle-{appointmentId}` | `RESCHEDULED` | unchanged | retrieve updated appointment |
| `cancel-appointment-rescheduled.json` | `advisory-appointment-lifecycle-{appointmentId}` | `RESCHEDULED` | `CANCELLED` | cancel a previously rescheduled appointment |
| `get-appointment-cancelled.json` | `advisory-appointment-lifecycle-{appointmentId}` | `CANCELLED` | unchanged | retrieve cancelled appointment status |
| `cancel-appointment-cancelled.json` | `advisory-appointment-lifecycle-{appointmentId}` | `CANCELLED` | unchanged | return `CANCEL_WINDOW_CLOSED` or idempotent cancellation error |
| `reschedule-appointment-cancelled.json` | `advisory-appointment-lifecycle-{appointmentId}` | `CANCELLED` | unchanged | return `RESCHEDULE_WINDOW_CLOSED` |

Additional implementation rule:
- BFA's in-memory overlay remains the primary reservation engine. The WireMock scenario chain above exists for endpoint-level deterministic integration coverage and Bruno/manual demo reproducibility.
- The implemented WireMock mappings use the runtime literal `Started` for the logical `STARTED` state because that is WireMock's built-in initial scenario state value.

### Success Criteria

- [x] mock-server loads all appointment mappings and body files successfully at startup
- [x] branch references are valid against `branches.json`
- [x] fixture scenarios cover the H1 journeys defined in the use-case spec
- [x] lifecycle behavior can be exercised through deterministic mappings without editing JSON on disk

---

## Step 4: Implement Booking Search and Lifecycle Behavior

**Objective:** Make the appointment API useful for realistic booking journeys by translating mock-server responses into the BFA contract and applying booking-domain validation in the specialized BFA layer.

**Effort:** 2–3 days  
**Risk:** MEDIUM

### Functional Areas

| Functional Area | Required Behavior |
|-----------------|-------------------|
| Taxonomy | Return entry paths, topics, channels, and validation hints |
| Service search | Map free-text service intent to service/topic candidates |
| Booking branch search | Return eligible branches or remote advisory centers based on request context |
| Slot search | Return bookable days and slots by branch, topic, and channel |
| Create appointment | Reserve slot, persist runtime state, return booking outcome |
| Get appointment | Return current appointment details |
| Cancel appointment | Update runtime state and release slot when applicable |
| Reschedule appointment | Move appointment to another slot with rule checks |

### Booking Rules to Implement in Mock Form

- day-first then time-slot retrieval
- channel-specific branch or center eligibility
- topic-based branch or advisor filtering
- summary-phase required before final create and lifecycle-changing requests from CES side
- existing-customer optional identifiers
- video-specific follow-up indicator in booking outcome
- no-availability responses with structured fallback guidance
- opaque `appointmentAccessToken` returned on create and required for retrieve/cancel/reschedule
- timing rule for cancellation and rescheduling similar to the current Java prototype

### Success Criteria

- [x] each endpoint returns realistic structured data
- [x] slot reservation and release rules behave deterministically
- [x] no-availability and fallback scenarios are testable
- [x] create, cancel, and reschedule behavior work without internal APIs

---

## Step 5: Generate and Wire the CES OpenAPI Toolset

**Objective:** Make the new contract consumable by CES the same way the location API is today.

**Effort:** 1–1.5 days  
**Risk:** LOW

### Files to Create or Modify

| File | Change |
|------|--------|
| `java/bfa-service-resource/src/test/java/com/voicebanking/bfa/appointment/OpenApiSpecExportTest.java` | Add appointment spec export test |
| `java/bfa-service-resource/openapi-specs/advisory-appointment.json` | Generated artifact |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/advisory_appointment.json` | Add toolset descriptor |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` | Add CES schema tuned for agent import |

### Design Notes

- Mirror the location-service OpenAPI export pattern.
- Ensure the schema is grouped and exportable from the BFA module.
- Keep the CES-facing schema intentionally agent-friendly, with clear summaries and descriptions for the model.
- Preserve authentication structure consistent with the existing toolset conventions, but do not block mock-mode development on unavailable internal token flows.

### Success Criteria

- [x] standalone appointment OpenAPI spec exports during test/build
- [x] CES toolset descriptor references the correct schema path
- [x] tool descriptions map naturally to advisory-booking use cases

---

## Step 6: Add CES Agent Spec, Instruction, and Evaluation Coverage

**Objective:** Make the new domain usable as a real appointment agent, not just an API.

**Effort:** 1.5–2 days  
**Risk:** MEDIUM

### Files to Create

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Create full use-case spec |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/advisory_appointment_agent.json` | Add agent descriptor |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt` | Add booking-focused instruction |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` | Add `advisory_appointment_agent` handoff to the root-agent child list |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` | Implement Step 1 routing precedence so booking-qualified utterances bypass generic location routing |
| `ces-agent/evaluations/...` | Add evaluation scenarios for appointment journeys |

### Instruction Focus Areas

- no re-greeting when transferred from the root agent
- start from appointment intent, not from branch lookup
- ask one question at a time
- never read more than three slot choices per turn
- confirm summary before create action
- offer fallback when slots are unavailable
- hand off to human when requested or when recovery fails

### Evaluation Coverage to Add

| Evaluation Theme | Goal |
|------------------|------|
| Product consultation booking | Verify topic → channel → branch → slot → contact → confirmation flow |
| Service-request booking | Verify free-text service mapping and mandatory comment behavior |
| Phone consultation | Verify remote-center or channel-specific slot selection |
| Video consultation | Verify video outcome text and follow-up instructions |
| No slots available | Verify alternative branch/day/channel recovery |
| Invalid email or phone | Verify repair prompts and retry behavior |
| Cancel and reschedule | Verify lifecycle actions and timing rule handling |
| Human handoff | Verify fallback and escalation |

### Success Criteria

- [x] CES agent identity is fully specified
- [x] evaluation coverage exists for all H1 journeys
- [x] instruction scope clearly excludes generic branch-only requests unrelated to booking

---

## Step 7: Align the Java Prototype and Integration Seams

**Objective:** Reduce divergence between the current Java `AppointmentContextAgent` prototype and the new contract-first resource model.

**Effort:** 1–1.5 days  
**Risk:** MEDIUM

### Files to Consider Modifying

| File | Change |
|------|--------|
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/AppointmentContextAgent.java` | Align tool semantics and input expectations |
| `.../domain/AppointmentType.java` | Reassess role relative to topic/channel split |
| `.../domain/BookingRequest.java` | Expand or replace with richer booking context model |
| `.../integration/AppointmentBookingClient.java` | Add provider seam or contract-aligned abstraction |
| `.../integration/MockAppointmentClient.java` | Either deprecate or realign to new deterministic seeds |

### Strategy

The resource API is the stronger source of truth. Step 7 will align the Java prototype to that model rather than leaving it as-is.

Alignment scope:
- rename or reshape prototype request objects so they map to `entryPath`, `consultationChannel`, `locationId`, `selectedTimeSlotId`, and `appointmentAccessToken`
- align lifecycle semantics with the BFA create, get, cancel, and reschedule contract
- keep `getMyAppointments` as an out-of-scope prototype-only feature until authenticated ownership is designed
- preserve a narrow provider seam so later internal advisory APIs replace the provider layer, not the domain language

The important goal in this phase is not full parity. It is avoiding two incompatible appointment models in the same repository.

### Success Criteria

- [ ] naming and concepts align across CES, BFA resource API, and Java prototype
- [ ] the current Java mock path is no longer the only appointment truth in the repo
- [ ] future internal API integration can target a single dominant booking model

---

## Step 8: Hardening and Internal-API Cutover Preparation

**Objective:** Make the preparation work reusable when internal advisory APIs become available.

**Effort:** 0.5–1 day  
**Risk:** LOW

### Required Preparation Outputs

| Output | Purpose |
|--------|---------|
| provider seam definition | future internal implementation swap |
| mock-mode assumptions documented | prevents accidental production overreach |
| mapping of mock endpoints to expected real capabilities | accelerates later cutover |
| known gaps list | makes transition plan explicit |

### Future Adapter Boundary

A future internal advisory provider must be able to replace only the data-access and execution layer while leaving intact:
- the CES toolset surface
- the resource API path structure
- the seed-data-driven tests as contract baselines
- the use-case specification and evaluation coverage

### Success Criteria

- [ ] the mock-backed API is explicitly documented as preparation, not production truth
- [ ] replacement seam is narrow and clear
- [ ] later internal API work is a provider substitution, not a redesign

---

## Testing Strategy

### Unit Tests

The `bfa-service-resource` module should gain appointment tests mirroring the location pattern.

| Test File | Coverage |
|-----------|----------|
| `AppointmentControllerTest.java` | endpoint behavior, response shape, validation, status codes |
| `AppointmentServiceTest.java` | orchestration rules and fallback behavior |
| `AppointmentMockServerClientTest.java` | upstream HTTP consumption, header mapping, and translation behavior |
| `AppointmentRepositoryTest.java` | translation/cache behavior over upstream fixture responses |
| `AppointmentSlotServiceTest.java` | slot search, reservation interpretation, and reschedule rules |
| `OpenApiSpecExportTest.java` | contract export |

### Integration Tests

Integration coverage should prove the mock-backed runtime seam end to end.

1. **BFA to mock-server contract:** verify header propagation, payload translation, and error mapping across taxonomy, branch, slot, and lifecycle calls.
2. **Runtime overlay behavior:** verify create, cancel, and reschedule state changes without mutating WireMock fixture files.
3. **WireMock fixture coverage:** add `mock-server/...Appointment...IntegrationTest.java` for mapping priorities and deterministic lifecycle scenarios.

### Voice and CES Evaluation

Every H1 use case should have at least one evaluation. Priority should go to:
- product consultation booking
- service-request booking
- branch appointment with branch search
- phone consultation
- video consultation
- no availability recovery
- cancel and reschedule
- escalation and goodbye paths

### Manual Testing

Manual review during implementation should verify:
- the booking flow never starts by asking for a slot before intent or topic context exists
- the agent only asks for location when the selected channel requires it
- slot presentation stays concise for voice
- summary confirmation is explicit before create
- outcome messages clearly distinguish mock confirmations from future real-world integrations if necessary

---

## Rollback Plan

### Emergency Rollback

If implementation begins and the appointment domain introduces instability:
- disable the new CES agent or toolset import
- keep existing `location_services_agent` unchanged
- keep the current Java prototype untouched as fallback reference
- stop exposing appointment endpoints from CES until contract issues are fixed

### Full Rollback

If the preparation branch must be fully withdrawn:
- remove the appointment resource package from `bfa-service-resource`
- remove the appointment toolset from CES
- retain only the research findings and implementation plan as documentation artifacts

The rollback is low risk because the proposed work is additive and should not replace location capabilities.

---

## Deployment Plan

### Pre-Deployment Checklist

- [ ] Documentation, use-case spec, and decision-freeze artifacts are merged and consistent.
- [x] The appointment contract in CES OpenAPI matches the BFA implementation surface.
- [x] Mock-server fixture coverage exists for branch, phone, video, and negative journeys.
- [x] CES evaluation scenarios are ready for non-production validation.

### Deployment Steps

1. Merge documentation and use-case artifacts.
2. Add mock-backed appointment endpoints in `bfa-service-resource`.
3. Export and verify the OpenAPI contract.
4. Add the CES toolset and agent in a non-production environment.
5. Run evaluation coverage and manual journey reviews.
6. Only then consider wider enablement for demos or integration rehearsal.

### Post-Deployment Verification

- Verify the phase is still treated as development and test enablement, not as production scheduling rollout.
- Verify the CES toolset import resolves against the current appointment schema.
- Verify appointment outcomes are clearly mock-backed and do not imply live internal-advisory execution.
- Do not market or expose this phase as real advisory scheduling until internal advisory APIs are available and a production-grade provider replaces the mock-backed layer.

---

## Success Criteria

This checklist is mandatory. Each item must remain unchecked until it has been explicitly verified through tests, manual validation, document review, or other recorded evidence.

- [x] A dedicated `advisory_appointment_agent` design exists and is approved.
- [x] The backend appointment API is implemented in `bfa-service-resource`, not hidden inside a Java-only mock client.
- [x] All mock data is file-backed, reviewable, and owned in `mock-server`.
- [x] Branch eligibility and slot selection are modeled as booking-domain concepts.
- [x] CES can consume the contract through a dedicated OpenAPI toolset.
- [x] H1 voice journeys are covered by evaluations and manual review scenarios.
- [x] The implementation remains preparation-grade and does not pretend to integrate with unavailable internal APIs.
- [x] A future internal-provider seam is documented and preserved.

---

## Known Issues & Limitations

- Internal advisory APIs are unavailable, so all scheduling behavior will be simulated.
- Confirmation emails, video access instructions, and callback routing will be mocked outcomes, not real downstream actions.
- Desired-location consultation is intentionally deferred.
- Service-search quality will depend on seed-catalog quality until a stronger search index or real service mapping is available.
- Generic branch discovery and booking-specific branch eligibility may overlap conceptually; this must be documented carefully to avoid duplicated responsibility.

---

## Future Enhancements

- Replace file-backed provider with internal advisory API adapter.
- Add secure cancellation and reschedule tokens or hashes.
- Add desired-location consultation with address validation.
- Add named-advisor preference and advisor routing rules.
- Add richer customer enrichment when internal customer and consent systems are available.
- Add real confirmation delivery integrations.

---

## Mentor Notes (Design Phase)

### Key Decisions

1. **New agent, not location-agent expansion**
   - Booking is treated as its own domain.
   - Branch search becomes a supporting capability rather than the agent identity.

2. **Resource API in `bfa-service-resource`**
   - This mirrors the location-service implementation pattern already present in the repo.
   - It gives CES a stable contract and keeps backend contract concerns out of the Java-only prototype.

3. **File-backed mock fixtures live in `mock-server`; BFA consumes them**
   - This preserves inspectability at the upstream boundary while keeping BFA honest as a consumer rather than a hidden second source of truth.

4. **Preparation-first delivery**
   - The plan is intentionally honest about the missing internal APIs.
   - The deliverable is readiness, not fake production integration.

### Main Trade-Offs

| Trade-Off | Benefit | Cost |
|-----------|---------|------|
| File-backed mock domain | reviewable, stable, fast to iterate | not a real production backend |
| Dedicated appointment API | strong contract and cleaner CES design | more upfront structure than patching the current prototype |
| Separate booking-specific branch search | correct domain semantics | some overlap with generic location data |
| Additive preparation phase | low risk and reversible | requires later provider replacement work |

### Recommendation

`APPROVE_IMPLEMENTATION` has already been received. The remaining expectation is to use this normalized plan as the execution baseline so build work, test design, and CES integration stay aligned to the same contract and topology decisions.

---

## Schema Appendix

This appendix is the Step 1 contract anchor for downstream implementation. Until the generated BFA OpenAPI export exists, the field definitions below are the normative request and response DTO baseline for all eight H1 endpoints.

### Common Response Envelope

All eight endpoints return the same envelope shape.

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `success` | `boolean` | Yes | `true` on 2xx success responses, `false` on validation or business errors |
| `data` | `object` | Conditional | Required when `success=true`; omitted on error responses |
| `error` | `ErrorInfo` | Conditional | Required when `success=false`; omitted on successful responses |
| `meta` | `ResponseMeta` | Yes | Correlation and version metadata returned on every response |

`ErrorInfo`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `error.code` | `string` | Yes on error | Must use the frozen error vocabulary in this plan |
| `error.message` | `string` | Yes on error | Human-readable explanation for logs and CES handling |
| `error.details` | `object<string, object>` | No | Optional structured validation or upstream details |

`ResponseMeta`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `meta.correlationId` | `string` | Yes | End-to-end correlation identifier |
| `meta.timestamp` | `string (date-time)` | Yes | Response creation timestamp |
| `meta.apiVersion` | `string` | Yes | Contract version emitted by BFA |

### Shared Enums

| Enum | Allowed Values |
|------|----------------|
| `EntryPath` | `SERVICE_REQUEST`, `PRODUCT_CONSULTATION` |
| `ConsultationChannel` | `BRANCH`, `PHONE`, `VIDEO` |
| `AdvisorMode` | `INTERNAL`, `INDEPENDENT`, `PRIVATE_BANKING` |
| `LocationType` | `BRANCH`, `REMOTE_CENTER` |
| `FallbackSuggestion.type` | `TRY_ANOTHER_DAY`, `TRY_ANOTHER_LOCATION`, `TRY_ANOTHER_CHANNEL`, `HANDOFF` |
| `CustomerContact.salutation` | `FRAU`, `HERR` |
| `AppointmentView.status` | `PENDING`, `CONFIRMED`, `CANCELLED`, `RESCHEDULED` |
| `DeliveryInfo.channel` | `NONE`, `EMAIL` |

### Endpoint 1: `GET /api/v1/appointment-taxonomy`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| none | n/a | n/a | No query or body fields |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data.entryPaths` | `array<EntryPathOption>` | Yes | May not be empty in H1 |
| `data.entryPaths[].code` | `EntryPath` | Yes | Entry-path identifier |
| `data.entryPaths[].label` | `string` | Yes | Spoken/display label |
| `data.entryPaths[].description` | `string` | Yes | CES routing and explanation text |
| `data.topics` | `array<TopicOption>` | Yes | May be empty only if the corresponding entry path is unsupported in a future phase |
| `data.topics[].code` | `string` | Yes | Topic identifier, for example `IN` |
| `data.topics[].label` | `string` | Yes | Spoken/display label |
| `data.topics[].entryPath` | `EntryPath` | Yes | Owning entry path |
| `data.topics[].requiresComment` | `boolean` | Yes | Whether booking needs a free-text comment |
| `data.consultationChannels` | `array<ConsultationChannelOption>` | Yes | Supported booking channels |
| `data.consultationChannels[].code` | `ConsultationChannel` | Yes | Channel identifier |
| `data.consultationChannels[].label` | `string` | Yes | Spoken/display label |
| `data.consultationChannels[].description` | `string` | Yes | Used for voice guidance |
| `data.advisorModes` | `array<AdvisorModeOption>` | Yes | Supported advisor modes |
| `data.advisorModes[].code` | `AdvisorMode` | Yes | Advisor-mode identifier |
| `data.advisorModes[].label` | `string` | Yes | Spoken/display label |
| `data.advisorModes[].allowedChannels` | `array<ConsultationChannel>` | Yes | Channels allowed for that mode |
| `data.validationRules` | `array<ValidationRule>` | Yes | May be empty; rules are still structurally present |
| `data.validationRules[].field` | `string` | Yes | Field name the rule applies to |
| `data.validationRules[].required` | `boolean` | Yes | Whether the field is required under the condition |
| `data.validationRules[].message` | `string` | Yes | Repair guidance shown to CES |
| `data.validationRules[].appliesWhen` | `object<string, string>` | No | Conditional context such as `entryPath=SERVICE_REQUEST` |

### Endpoint 2: `GET /api/v1/appointment-service-search`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `query` | `string` | Yes | Free-text service request from the caller |
| `entryPath` | `EntryPath` | No | Defaults to `SERVICE_REQUEST` when omitted |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data.matches` | `array<ServiceSearchMatch>` | Yes | Empty array allowed when no good match exists |
| `data.matches[].serviceCode` | `string` | Yes | Canonical service-request code |
| `data.matches[].label` | `string` | Yes | Spoken/display label |
| `data.matches[].topicCode` | `string` | No | Present when the service maps cleanly to a downstream topic |
| `data.matches[].confidence` | `number` | Yes | Confidence score in the `0.0` to `1.0` range |
| `data.matches[].requiresComment` | `boolean` | Yes | Whether the matched service requires a free-text comment |
| `data.fallbackGuidance` | `array<string>` | Yes | Empty array allowed when matches are strong |

### Endpoint 3: `GET /api/v1/appointment-branches`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `entryPath` | `EntryPath` | Yes | Determines whether topic or service rules apply |
| `consultationChannel` | `ConsultationChannel` | Yes | `BRANCH`, `PHONE`, or `VIDEO` |
| `topicCode` | `string` | Conditional | Required when `entryPath=PRODUCT_CONSULTATION` |
| `serviceCode` | `string` | Conditional | Required when `entryPath=SERVICE_REQUEST` |
| `city` | `string` | Conditional | One of `city`, `postalCode`, or `address` is required when `consultationChannel=BRANCH` |
| `postalCode` | `string` | Conditional | One of `city`, `postalCode`, or `address` is required when `consultationChannel=BRANCH` |
| `address` | `string` | Conditional | One of `city`, `postalCode`, or `address` is required when `consultationChannel=BRANCH` |
| `accessible` | `boolean` | No | Filters to wheelchair-accessible locations |
| `limit` | `integer` | No | Defaults to `5`; max options returned to CES |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data.locations` | `array<AppointmentLocationOption>` | Yes | Non-empty on 200 responses |
| `data.locations[].locationId` | `string` | Yes | Booking location identifier |
| `data.locations[].locationType` | `LocationType` | Yes | `BRANCH` or `REMOTE_CENTER` |
| `data.locations[].branchId` | `string` | Conditional | Required when `locationType=BRANCH` |
| `data.locations[].name` | `string` | Yes | Spoken/display location name |
| `data.locations[].address` | `string` | Conditional | Required for `BRANCH`; optional for `REMOTE_CENTER` |
| `data.locations[].city` | `string` | Conditional | Required for `BRANCH`; optional for `REMOTE_CENTER` |
| `data.locations[].postalCode` | `string` | Conditional | Required for `BRANCH`; optional for `REMOTE_CENTER` |
| `data.locations[].phone` | `string` | No | Contact number if available |
| `data.locations[].wheelchairAccessible` | `boolean` | No | Present for physical branches when known |
| `data.locations[].distanceKm` | `number` | No | Present when computed from a location hint |
| `data.locations[].supportedChannels` | `array<ConsultationChannel>` | Yes | Channels available at this location |
| `data.locations[].supportedAdvisorModes` | `array<AdvisorMode>` | Yes | Supported advisor modes |
| `data.locations[].nextAvailableDay` | `string (date)` | No | Earliest known bookable day |
| `data.locations[].eligibilityReason` | `string` | Yes | Why the location is eligible for the request |
| `data.count` | `integer` | Yes | Number of returned `locations` |
| `data.totalMatches` | `integer` | Yes | Total matches before truncation by `limit` |
| `data.fallbackSuggestions` | `array<FallbackSuggestion>` | Yes | Empty array allowed |
| `data.fallbackSuggestions[].type` | `FallbackSuggestion.type` | Yes | Next-step guidance type |
| `data.fallbackSuggestions[].label` | `string` | Yes | Spoken/display guidance |

### Endpoint 4: `GET /api/v1/appointment-slots`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `entryPath` | `EntryPath` | Yes | Determines whether topic or service rules apply |
| `consultationChannel` | `ConsultationChannel` | Yes | Booking channel |
| `locationId` | `string` | Yes | Location selected from the branch search |
| `topicCode` | `string` | Conditional | Required when `entryPath=PRODUCT_CONSULTATION` |
| `serviceCode` | `string` | Conditional | Required when `entryPath=SERVICE_REQUEST` |
| `selectedDay` | `string (date)` | No | When present, slots for that day are returned |
| `advisorMode` | `AdvisorMode` | No | Optional refinement when multiple advisor modes are available |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data.locationId` | `string` | Yes | Echoes the selected location |
| `data.timezone` | `string` | Yes | Timezone for spoken slot rendering |
| `data.availableDays` | `array<AvailableDay>` | Yes | May not be empty on 200 responses |
| `data.availableDays[].date` | `string (date)` | Yes | Bookable day |
| `data.availableDays[].label` | `string` | Yes | Voice-friendly day label |
| `data.availableDays[].availableSlotCount` | `integer` | Yes | Number of slots on that day |
| `data.availableDays[].earliestTime` | `string` | Conditional | Present when `availableSlotCount > 0` |
| `data.slots` | `array<AppointmentSlotOption>` | Yes | May be empty when `selectedDay` is omitted |
| `data.slots[].slotId` | `string` | Yes | Slot identifier used for create/reschedule |
| `data.slots[].startTime` | `string (date-time)` | Yes | Slot start in local timezone |
| `data.slots[].endTime` | `string (date-time)` | Yes | Slot end in local timezone |
| `data.slots[].advisorId` | `string` | No | Present when the slot is advisor-specific |
| `data.slots[].advisorName` | `string` | No | Present when the slot is advisor-specific |
| `data.slots[].advisorMode` | `AdvisorMode` | Yes | Advisor mode associated with the slot |
| `data.fallbackSuggestions` | `array<FallbackSuggestion>` | Yes | Empty array allowed |
| `data.fallbackSuggestions[].type` | `FallbackSuggestion.type` | Yes | Next-step guidance type |
| `data.fallbackSuggestions[].label` | `string` | Yes | Spoken/display guidance |

### Endpoint 5: `POST /api/v1/appointments`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `entryPath` | `EntryPath` | Yes | Determines whether topic or service rules apply |
| `consultationChannel` | `ConsultationChannel` | Yes | Booking channel |
| `topicCode` | `string` | Conditional | Required when `entryPath=PRODUCT_CONSULTATION` |
| `serviceCode` | `string` | Conditional | Required when `entryPath=SERVICE_REQUEST` |
| `locationId` | `string` | Yes | Selected eligible location |
| `selectedDay` | `string (date)` | No | Optional echo of the chosen day |
| `selectedTimeSlotId` | `string` | Yes | Slot identifier returned by slot search |
| `advisorMode` | `AdvisorMode` | No | Optional selection when the caller chose an advisor mode |
| `comment` | `string` | Conditional | Required when taxonomy or service-search rules mark comment as mandatory |
| `subjectSelections` | `array<string>` | No | Optional selected subject codes |
| `subjectInputs` | `object<string, string>` | No | Optional keyed subject inputs captured from the caller |
| `customer` | `CustomerContact` | Yes | Contact details for the appointment |
| `customer.salutation` | `CustomerContact.salutation` | Yes | Caller salutation |
| `customer.firstName` | `string` | Yes | Caller first name |
| `customer.lastName` | `string` | Yes | Caller last name |
| `customer.email` | `string (email)` | Yes | Confirmation destination in H1 mock mode |
| `customer.phone` | `string` | Yes | Callback/contact number |
| `customer.isExistingCustomer` | `boolean` | Yes | Distinguishes prospect vs existing customer |
| `existingCustomerContext` | `ExistingCustomerContext` | No | Optional unless validation rules require more identifiers |
| `existingCustomerContext.branchNumber` | `string` | No | Optional existing-customer branch identifier |
| `existingCustomerContext.accountNumber` | `string` | No | Optional existing-customer account identifier |
| `summaryConfirmed` | `boolean` | Yes | Must be `true` or the request is rejected |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data.appointment` | `AppointmentView` | Yes | Newly created appointment view |
| `data.appointmentAccessToken` | `string` | Yes | Opaque lifecycle token required for get/cancel/reschedule |
| `data.delivery` | `DeliveryInfo` | Yes | Mocked follow-up delivery outcome |
| `data.delivery.channel` | `DeliveryInfo.channel` | Yes | `NONE` or `EMAIL` |
| `data.delivery.destinationMasked` | `string` | Conditional | Required when `channel=EMAIL` |
| `data.delivery.followUpText` | `string` | Yes | Spoken/display follow-up wording |

### Endpoint 6: `GET /api/v1/appointments/{appointmentId}`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `appointmentId` | `string` | Yes | Path parameter |
| `appointmentAccessToken` | `string` | Yes | Query parameter; lifecycle access token |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data` | `AppointmentView` | Yes | See shared `AppointmentView` contract below |

### Endpoint 7: `POST /api/v1/appointments/{appointmentId}/cancel`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `appointmentId` | `string` | Yes | Path parameter |
| `appointmentAccessToken` | `string` | Yes | Required lifecycle token |
| `reason` | `string` | No | Optional caller cancellation reason |
| `summaryConfirmed` | `boolean` | Yes | Must be `true` or the request is rejected |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data` | `AppointmentView` | Yes | Same schema as get; `status` must be `CANCELLED` |

### Endpoint 8: `POST /api/v1/appointments/{appointmentId}/reschedule`

Request DTO

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `appointmentId` | `string` | Yes | Path parameter |
| `appointmentAccessToken` | `string` | Yes | Required lifecycle token |
| `selectedDay` | `string (date)` | No | Optional echo of the newly chosen day |
| `selectedTimeSlotId` | `string` | Yes | Replacement slot identifier returned by slot search |
| `summaryConfirmed` | `boolean` | Yes | Must be `true` or the request is rejected |

Response DTO (`data`)

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `data` | `AppointmentView` | Yes | Same schema as get; `status` must be `RESCHEDULED` after a successful change |

### Shared Nested DTOs

`AppointmentView`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `appointmentId` | `string` | Yes | Stable appointment identifier |
| `status` | `AppointmentView.status` | Yes | Current lifecycle state |
| `confirmationCode` | `string` | Yes | Spoken/display confirmation code |
| `consultationChannel` | `ConsultationChannel` | Yes | Booked channel |
| `topicCode` | `string` | Conditional | Required when the appointment was created from `PRODUCT_CONSULTATION` |
| `serviceCode` | `string` | Conditional | Required when the appointment was created from `SERVICE_REQUEST` |
| `locationId` | `string` | Yes | Booked location identifier |
| `locationName` | `string` | Yes | Spoken/display location name |
| `scheduledStart` | `string (date-time)` | Yes | Scheduled start time |
| `scheduledEnd` | `string (date-time)` | Yes | Scheduled end time |
| `advisorName` | `string` | No | Present when a specific advisor is assigned |
| `summaryText` | `string` | Yes | Voice-ready appointment summary |
| `canCancelUntil` | `string (date-time)` | No | Omitted when cancellation is no longer possible or not relevant |
| `canRescheduleUntil` | `string (date-time)` | No | Omitted when rescheduling is no longer possible or not relevant |
| `timeline` | `array<string>` | Yes | Chronological lifecycle entries for the current appointment |
| `delivery` | `DeliveryInfo` | Yes | Follow-up delivery state |

`DeliveryInfo`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `channel` | `DeliveryInfo.channel` | Yes | `NONE` or `EMAIL` in H1 |
| `destinationMasked` | `string` | Conditional | Required when `channel=EMAIL` |
| `followUpText` | `string` | Yes | Spoken/display follow-up text |

`CustomerContact`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `salutation` | `CustomerContact.salutation` | Yes | `FRAU` or `HERR` |
| `firstName` | `string` | Yes | Caller first name |
| `lastName` | `string` | Yes | Caller last name |
| `email` | `string (email)` | Yes | Confirmation destination |
| `phone` | `string` | Yes | Contact number |
| `isExistingCustomer` | `boolean` | Yes | Prospect vs existing-customer flag |

`ExistingCustomerContext`

| Field | Type | Required | Notes |
|------|------|----------|-------|
| `branchNumber` | `string` | No | Optional existing-customer identifier |
| `accountNumber` | `string` | No | Optional existing-customer identifier |

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-03-15 | GitHub Copilot | Initial detailed implementation plan created for AGENT-007, aligned to research findings and current repository structure |
| 2026-03-15 | GitHub Copilot | Added a `Summary at a Glance` section and clarified the review focus around mock-server-owned appointment fixtures |
| 2026-03-15 | GitHub Copilot | Centralized advisory-document references under `ces-agent/docs/advisory-agent` and moved appointment architecture review/diagram artifacts into that documentation tree |
| 2026-03-15 | Codex | Added implementation-status summary and normalized the document back onto the standard implementation-plan template structure |
| 2026-03-15 | Codex | Completed Step 1 contract freeze: field-level DTO appendix, root-agent routing precedence, Step 7 prototype decision, and WireMock lifecycle scenario states |
| 2026-03-15 | Codex | Implemented Step 2 in `java/bfa-service-resource`: added the appointment resource package, deterministic placeholder orchestration, upstream RestClient skeleton, and standalone OpenAPI group; verified module compilation |
| 2026-03-15 | Codex | Recorded Step 3 and Step 4 preapproval and added implementation-status tracking at the header and summary levels |
| 2026-03-15 | Codex | Implemented Step 3 and Step 4: added mock-server advisory appointment fixtures and lifecycle scenario tests, switched BFA booking search and slots to upstream-backed behavior with deterministic runtime overlay, and verified both repos with Maven test runs |
| 2026-03-15 | Codex | Made step-summary/status tracking and verified success-criteria checklists an explicit mandatory requirement in the live plan and the shared implementation-plan template |
| 2026-03-16 | Codex | Fixed root-agent public appointment routing, aligned the generated BFA OpenAPI requiredness with the CES advisory schema, expanded the advisory evaluation pack to cover the documented H1 matrix, and corrected Step 7/8 status/cutover seam documentation |
| 2026-03-15 | GitHub Copilot | Implemented Step 5: added OpenApiSpecExportTest for advisory-appointment group, generated openapi-specs/advisory-appointment.json artifact, promoted CES schema from 0.1.0-draft to 1.0.0, verified toolset descriptor alignment |
