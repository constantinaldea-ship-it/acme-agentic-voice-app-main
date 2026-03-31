# Use Case Spec: AdvisoryAppointmentAgent — Advisory Booking and Lifecycle

> **Agent ID:** `advisory_appointment_agent`  
> **Document Type:** Persona-Driven Use Case Specification  
> **Version:** 1.0  
> **Date:** 2026-03-15  
> **Author:** Codex  
> **Status:** Draft baseline for AGENT-007 implementation  
> **Related Plan:** [AGENT-007 Appointment Context](../implementation-plan/AGENT-007-appointment-context.md)  
> **Backend Source of Truth:** `java/bfa-service-resource` appointment domain  
> **CES Toolset Baseline:** `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml`

---

## 1. Persona Profile

### Sabine — Advisory Booking Persona

| Attribute | Detail |
|-----------|--------|
| **Age** | 43 |
| **Relationship to bank** | Existing retail customer for day-to-day banking, but can also appear as a prospect for advisory topics |
| **Digital literacy** | Medium |
| **Primary language** | German, but comfortable with English if needed |
| **Primary need** | Book an advisory appointment quickly without navigating a multi-screen form |
| **Secondary needs** | Reschedule, cancel, or switch to phone/video if branch slots are unavailable |
| **Accessibility** | Prefers concise spoken options and one question at a time |
| **Trust expectation** | Wants a clear summary before anything is booked |

### Why This Persona

Sabine is the right design center because she exercises both key H1 shapes:

- structured advisory booking for product consultation
- repair and fallback when preferred branch/channel/slot is unavailable

She also exposes the main contract requirements:

- topic-first orchestration
- channel-aware location selection
- day-first then time-slot selection
- contact capture
- explicit summary confirmation

---

## 2. Preparation Baseline

### Runtime Topology

For AGENT-007 preparation work, the runtime path is:

`CES -> bfa-service-resource -> mock-server`

This is intentionally different from the longer-term internal-adapter architecture documented elsewhere. The direct BFA path is the implementation baseline for this phase.

### Access Model

- CES authenticates to `bfa-service-resource` with the existing service-agent token pattern.
- The appointment flow does not require customer legitimation in H1 mock mode.
- The flow does not require `AI_INTERACTION` consent in H1 mock mode because it must support prospects and walk-ins.
- Retrieve, cancel, and reschedule require an opaque `appointmentAccessToken`.

### Prototype Position

`java/voice-banking-app`'s `AppointmentContextAgent` is a legacy prototype and compatibility reference. It is not the contract source of truth for the new build.

---

## 3. Current Implementation Summary

### What Exists

| Area | State |
|------|-------|
| CES branch booking | Not implemented; location agent explicitly stops at branch discovery |
| Java prototype | Supports branch booking, modify, and cancel with a simple type/slot model |
| Mock data | Prototype uses in-code randomness; not contract-first |
| BFA REST pattern | Established in `bfa-service-resource` for location services |
| Upstream mock pattern | Established in sibling `mock-server` with deterministic WireMock mappings |

### Contract Delta from Prototype

The new appointment build introduces concepts missing from the prototype:

- `entryPath` (`SERVICE_REQUEST` vs `PRODUCT_CONSULTATION`)
- `consultationChannel` (`BRANCH`, `PHONE`, `VIDEO`)
- structured topic/service mapping
- booking-eligible location search rather than direct `branchId` input
- day-first slot discovery
- richer contact and existing-customer context
- summary confirmation gate before create/cancel/reschedule
- lifecycle access token

---

## 4. Use Cases

### UC-AA-01: Product Consultation in Branch

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-01 |
| **Title** | Book a branch appointment for product advice |
| **Trigger** | "I want advice about investing next week in Munich." |
| **API Sequence** | `getAppointmentTaxonomy` -> `searchAppointmentBranches` -> `getAppointmentSlots` -> `createAppointment` |
| **Success Criteria** | Agent captures topic, channel, location, day, time, and contact details before booking. Booking only happens after explicit spoken summary confirmation. |

### UC-AA-02: Phone Consultation

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-02 |
| **Title** | Book a phone consultation when branch travel is not preferred |
| **Trigger** | "Can somebody call me about a mortgage?" |
| **API Sequence** | `getAppointmentTaxonomy` -> `searchAppointmentBranches` or remote-center search -> `getAppointmentSlots` -> `createAppointment` |
| **Success Criteria** | Agent does not force branch selection when phone consultation is available. Outcome includes follow-up wording specific to phone appointments. |

### UC-AA-03: Video Consultation

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-03 |
| **Title** | Book a video consultation |
| **Trigger** | "I'd rather do this by video." |
| **API Sequence** | `getAppointmentTaxonomy` -> `searchAppointmentBranches` or remote-center search -> `getAppointmentSlots` -> `createAppointment` |
| **Success Criteria** | Outcome explicitly says that video access details are a mocked follow-up and not a real downstream integration in this phase. |

### UC-AA-04: Service Request Appointment

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-04 |
| **Title** | Book a service-request appointment from free text |
| **Trigger** | "I need an appointment to sort out a standing order problem." |
| **API Sequence** | `searchAppointmentServices` -> `searchAppointmentBranches` -> `getAppointmentSlots` -> `createAppointment` |
| **Success Criteria** | Agent uses service search, supports required comment behavior where configured, and does not force the product-topic flow. |

### UC-AA-05: No Availability Recovery

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-05 |
| **Title** | Recover when the preferred slot or location is unavailable |
| **Trigger** | Requested branch or day has no slots |
| **API Sequence** | `getAppointmentSlots` with fallback suggestions; optionally repeat `searchAppointmentBranches` |
| **Success Criteria** | Agent offers another day, another branch, or another channel. It does not dead-end on the first no-slots response. |

### UC-AA-06: Cancel or Reschedule with Lifecycle Token

| Field | Detail |
|-------|--------|
| **ID** | UC-AA-06 |
| **Title** | Change an existing appointment in mock mode |
| **Trigger** | Caller asks to move or cancel an appointment created earlier in the session or recovered with token |
| **API Sequence** | `getAppointment` -> `cancelAppointment` or `rescheduleAppointment` |
| **Success Criteria** | Lifecycle actions require `appointmentAccessToken`, respect timing rules, and still require explicit confirmation before mutating state. |

---

## 5. Dependencies

| Dependency | Purpose |
|------------|---------|
| `java/bfa-service-resource` appointment domain | Canonical CES-facing contract and orchestration layer |
| `mock-server` appointment fixtures | Deterministic upstream simulation |
| `branches.json` in BFA | Enrichment for physical branch identity and accessibility |
| CES toolset draft OpenAPI | Import baseline for tool semantics |
| Research findings | Domain field model and conversational sequence |

---

## 6. Conversational Design Foundation

### Instruction Rules

- Do not re-greet when transferred from the root agent.
- Ask one question at a time.
- Start with appointment intent and topic, not with time-slot search.
- Never read more than three day or slot options in one turn.
- Never create, cancel, or reschedule without summary confirmation.
- Never invent availability, advisor names, or booking results.
- Offer human handoff when recovery fails or the user explicitly asks for it.

### Tool Boundaries

| Tool | When to use |
|------|-------------|
| `getAppointmentTaxonomy` | Bootstrapping path/channel/topic options |
| `searchAppointmentServices` | Free-text service-request intake |
| `searchAppointmentBranches` | Eligible branches or remote centers |
| `getAppointmentSlots` | Day-first and time-slot retrieval |
| `createAppointment` | Only after summary confirmation |
| `getAppointment` | Follow-up lifecycle retrieval |
| `cancelAppointment` | Cancellation after confirmation |
| `rescheduleAppointment` | Move to a new slot after confirmation |

---

## 7. Remaining H1 Non-Goals

- desired-location consultations
- named-advisor preference flows
- real email delivery
- real video link generation
- list-all-my-appointments customer flow
- real customer master lookup

---

## 8. Evaluation Matrix

| Evaluation ID | Path | Goal | Status |
|---------------|------|------|--------|
| `appointment_routing_english` | `acme_voice_agent/evaluations/appointment_routing_english/` | English root-agent transfer into `advisory_appointment_agent` | Active |
| `appointment_routing_german` | `acme_voice_agent/evaluations/appointment_routing_german/` | German root-agent transfer into `advisory_appointment_agent` | Active |
| `appointment_booking_branch_flow` | `acme_voice_agent/evaluations/appointment_booking_branch_flow/` | Product consultation booking through a physical branch | Active |
| `appointment_phone_consultation` | `acme_voice_agent/evaluations/appointment_phone_consultation/` | Phone consultation selection and booking | Active |
| `appointment_video_consultation` | `acme_voice_agent/evaluations/appointment_video_consultation/` | Video booking with mocked delivery wording | Active |
| `appointment_service_request_booking` | `acme_voice_agent/evaluations/appointment_service_request_booking/` | Free-text service search and booking | Active |
| `appointment_no_slots_recovery` | `acme_voice_agent/evaluations/appointment_no_slots_recovery/` | Recovery to another day, branch, or channel | Active |
| `appointment_invalid_contact_repair` | `acme_voice_agent/evaluations/appointment_invalid_contact_repair/` | Repair invalid email or phone input | Active |
| `appointment_cancel_reschedule` | `acme_voice_agent/evaluations/appointment_cancel_reschedule/` | Cancel with access token and successful reschedule flow | Active |
| `appointment_reschedule_window_closed` | `acme_voice_agent/evaluations/appointment_reschedule_window_closed/` | Reschedule blocked by timing rule | Active |
| `appointment_human_handoff` | `acme_voice_agent/evaluations/appointment_human_handoff/` | Human handoff and escalation from the appointment flow | Active |

---

## 9. Build Readiness Note

This spec is part of the AGENT-007 readiness closure. Build should treat it as a design input, not as generated implementation output.
