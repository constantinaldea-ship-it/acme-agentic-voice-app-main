# Advisory Appointment — Concept Alignment Matrix

> Maps naming and concepts across the three layers: CES Agent, BFA Resource API, and Java Prototype.

## Layer Definitions

| Layer | Package / Path | Role |
|-------|---------------|------|
| **CES Agent** | `ces-agent/acme_voice_agent/toolsets/advisory_appointment/` | Voice AI surface; calls BFA via OpenAPI tools |
| **BFA Resource API** | `java/bfa-service-resource/.../appointment/` | Contract-first REST API; proxies to upstream mock |
| **Java Prototype** | `java/voice-banking-app/.../appointment/` | Spring AI agent with in-memory mock; reference only |

## Concept Mapping

| CES Concept | BFA Field | Java Prototype Field | Notes |
|-------------|-----------|---------------------|-------|
| `entryPath` | `entryPath` (query) | — (not present) | Taxonomy navigation breadcrumb |
| `consultationChannel` | `consultationChannel` (query) | — (only BRANCH implicit) | BRANCH / PHONE / VIDEO |
| `topicCode` | `topicCode` (query) | `AppointmentType` enum | Prototype uses enum; BFA uses string code |
| `serviceCode` | `serviceCode` (query) | `AppointmentType` enum | Same mapping as topicCode |
| `locationId` | `locationId` (query) | `branchId` | Prototype field name differs |
| `selectedDay` | `selectedDay` (query) | `requestedTime.toLocalDate()` | BFA uses ISO date string |
| `selectedTimeSlotId` | `selectedTimeSlotId` (body) | `slotId` | Renamed in BFA contract |
| `appointmentAccessToken` | `appointmentAccessToken` (query/header) | — (not present) | BFA uses token for stateless retrieval |
| `appointmentId` | `{appointmentId}` (path) | `appointmentId` | Consistent across all layers |
| — | `advisorMode` (query) | `advisorPreference` | String vs. enum semantics |
| — | `summaryConfirmed` (body) | — (not present) | BFA confirmation gate; CES instruction handles this |
| — | `comment` (body) | `notes` | Field rename |
| `customer` (in create request) | `customer` (body) | `customerId` | BFA embeds full customer context |

## Lifecycle State Mapping

| Action | CES Tool | BFA Endpoint | Java Prototype Method |
|--------|----------|-------------|----------------------|
| Browse topics | `getAppointmentTaxonomy` | `GET /appointment-taxonomy` | — |
| Search services | `searchAppointmentServices` | `GET /appointment-service-search` | — |
| Check eligibility | `getEligibleBranches` | `GET /appointment-branches` | — |
| Get time slots | `getAppointmentSlots` | `GET /appointment-slots` | `getAvailability()` / `getNextAvailable()` |
| Create appointment | `createAppointment` | `POST /appointments` | `createAppointment()` |
| Get appointment | `getAppointmentDetails` | `GET /appointments/{id}` | `getAppointment()` |
| Cancel | `cancelAppointment` | `POST /appointments/{id}/cancel` | `cancelAppointment()` |
| Reschedule | `rescheduleAppointment` | `POST /appointments/{id}/reschedule` | `modifyAppointment()` |
| List my appointments | — (out of scope) | — | `getAppointments()` |

## Key Divergences

1. **Taxonomy & Eligibility**: CES and BFA have a rich discovery flow (taxonomy → service search → eligibility → slots). The Java prototype skips straight to availability with an `AppointmentType` enum. This is acceptable because the prototype predates the BFA contract.

2. **Consultation Channel**: BFA supports BRANCH / PHONE / VIDEO. The prototype only models in-person branch appointments. Future alignment should add a `channel` field to `BookingRequest`.

3. **Access Token**: BFA uses `appointmentAccessToken` for stateless appointment retrieval (no session required). The prototype uses `appointmentId` directly with in-memory storage.

4. **Confirmation Gate**: BFA includes a `summaryConfirmed` boolean in create/cancel/reschedule requests. The CES agent instruction handles confirmation through conversation turns before calling the tool. The Java prototype validates internally.

## Authoritative Model

**The BFA Resource API (`java/bfa-service-resource`) is the authoritative contract.**

- The CES OpenAPI schema (`open_api_schema.yaml`) is derived from the BFA contract
- The Java prototype (`voice-banking-app`) is a reference implementation that predates the BFA model
- Future internal advisory API integrations should target the BFA contract surface
- The Java prototype's `AppointmentBookingClient` interface provides the provider seam for swapping implementations

## Future Alignment Tasks

- [ ] Add `consultationChannel` to `BookingRequest` record
- [ ] Rename `branchId` to `locationId` in prototype
- [ ] Rename `slotId` to `selectedTimeSlotId` in prototype
- [ ] Add `appointmentAccessToken` to appointment retrieval flow
- [ ] Map `AppointmentType` enum values to BFA `topicCode`/`serviceCode` strings
