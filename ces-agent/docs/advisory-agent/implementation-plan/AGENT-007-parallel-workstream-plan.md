# AGENT-007: Parallel Workstream Execution Plan

> PHASE: IMPLEMENT_ALLOWED  
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**FR Number:** AGENT-007  
**Title:** Advisory Appointment Agent — Parallel Workstream Execution Plan  
**Author:** GitHub Copilot  
**Created:** 2026-03-16  
**Status:** IN_PROGRESS  
**Implementation Status:** Shared design anchors are resolved in the primary AGENT-007 plan; Steps 1-6 are implemented in the repository baseline; Step 7 and Step 8 remain partially implemented and are the main late-phase follow-through items.  
**Estimated Effort:** 5-7 calendar days with 3 developers, or 7-9 calendar days with 2 developers  
**Estimated Credits:** 8-12k tokens for planning, staffing review, and execution coordination  
**Related Documents:**
- `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md`
- `ces-agent/docs/advisory-agent/implementation-plan/RESEARCH-FINDINGS.md`
- `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md`
- `ces-agent/docs/advisory-agent/mock-mode-cutover-preparation.md`
- `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml`
- `java/bfa-service-resource/openapi-specs/advisory-appointment.json`

**Approval status:** `APPROVE_IMPLEMENTATION` was already granted for AGENT-007 in the primary implementation plan. This companion plan does not change scope; it assigns work across 2-3 developers, defines hand-off artefacts, and identifies what must remain sequential.

---

## Summary at a Glance

| Item | Summary |
|------|---------|
| Goal | Allocate AGENT-007 work across 2-3 developers with minimal blocking and a single dominant contract model. |
| Primary Outcome | A staffing-ready execution plan that assigns ownership for the resource API, upstream mock domain, CES agent/toolset layer, and late-phase prototype alignment. |
| Systems / Repos | `mock-server`, `java/bfa-service-resource`, `ces-agent`, and `java/voice-banking-app`. |
| Dependencies | Primary AGENT-007 plan, schema appendix, WireMock scenario model, generated OpenAPI export, and root-agent routing rules. |
| Implementation Status | Main AGENT-007 Steps 1-6 are implemented; this plan focuses on efficient ownership and remaining Step 7-8 closure. |
| Key Decisions | Developer B owns the dominant contract and BFA resource spine; Developer A owns deterministic upstream fixture truth; Developer C owns CES routing, toolset semantics, and evaluations. |
| Main Risks | Contract drift across layers, lifecycle semantics diverging between BFA and WireMock, and root-agent routing regressions between appointment and generic location queries. |
| Out of Scope | Real internal `facecc` integration, partner-data integration, desired-location consultations, live notification delivery, and production-grade downstream token exchange. |
| Review Focus | Validate workstream ownership, hand-off artefacts, sequential gate order, and merge cadence before assigning engineers. |

## Executive Summary

This plan turns the approved AGENT-007 four-layer target state into a parallel execution model for a 2-3 developer team. The core rule is that AGENT-007 must continue to have one dominant contract language: the BFA appointment resource API defined in `java/bfa-service-resource` and reflected into CES through the generated OpenAPI surface. Everything else — WireMock fixtures, agent instructions, evaluations, and prototype alignment — must orbit that contract rather than invent parallel models.

The most efficient split is not a pure repo split but a seam split. Developer B owns the contract-first resource API and becomes the critical-path owner. Developer A owns deterministic upstream simulation in `mock-server`, including the lifecycle scenario model and manual/smoke validation artefacts. Developer C owns the CES agent layer, including root-agent routing precedence, toolset import shape, instruction design, and evaluation coverage. Step 7 and Step 8 are intentionally late-phase because they depend on the earlier layers settling first.

| Step | Description | Effort | Risk |
|------|-------------|--------|------|
| 1.0 | Freeze shared design anchors and hand-off artefacts | 0.5-1 day | LOW |
| 2.0 | Run Developer B contract and resource-API spine | 2-3 days | MEDIUM |
| 3.0 | Run Developer A upstream fixture and scenario platform | 1.5-2.5 days | MEDIUM |
| 4.0 | Run Developer C CES routing, toolset, and evaluation layer | 1.5-2 days | MEDIUM |
| 5.0 | Integrate streams, finish Step 7 follow-through, and reduce Step 8 seam risk | 1-2 days | MEDIUM |

---

## Prerequisites

Before parallel execution begins, the team should verify that the shared design anchors are already frozen in the primary AGENT-007 plan and are treated as non-negotiable inputs rather than open debates.

1. **Contract Anchor Exists:** The schema appendix, endpoint inventory, and error vocabulary in `AGENT-007-appointment-context.md` are the single source of truth for request fields, response fields, and requiredness.
2. **Lifecycle Scenario Model Exists:** The WireMock lifecycle state machine in the primary plan is accepted as the deterministic fixture model that supports create, get, reschedule, and cancel.
3. **Root Routing Boundary Exists:** Appointment-booking and lifecycle requests win over generic location routing at the root agent.
4. **OpenAPI Baseline Exists:** `java/bfa-service-resource/openapi-specs/advisory-appointment.json` and `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` already provide the consumer-facing contract surface.
5. **Late-Phase Scope is Explicit:** Step 7 prototype alignment and Step 8 provider-seam hardening remain incomplete and must be treated as follow-through, not as blockers for H1 readiness.

### Sequential decisions that must precede parallel execution

These items are too risky to parallelise independently. They should be owned in the order below even when the actual implementation work later runs in parallel.

| Order | Decision / Gap | Primary Owner | Why It Must Be Sequential | Unblocks |
|------|----------------|---------------|---------------------------|----------|
| 1 | Field-level DTO schemas, endpoint inventory, and error vocabulary | Developer B | A and C cannot safely build fixtures or toolsets against moving request/response fields. | Developer A, Developer C |
| 2 | WireMock lifecycle state model | Developer A with Developer B sign-off | BFA runtime overlay and mock-server scenarios must express the same lifecycle semantics. | Developer B, Developer C |
| 3 | Root-agent routing precedence and public-service exception | Developer C with Developer B sign-off | CES routing must not be finalized until appointment versus location boundaries are frozen. | CES toolset, evaluation coverage |
| 4 | Step 7 prototype direction | Developer B | The repo cannot sustain two competing appointment models. The decision must be early even if implementation is late. | Step 7 follow-through |

### Recommended resolution order for the specific AGENT-007 gaps

| Gap | Owner | Recommended Order | Guidance |
|-----|-------|-------------------|----------|
| Missing field-level DTO schemas | Developer B | First | Freeze once in BFA/OpenAPI and forbid parallel schema invention in mock-server or CES. |
| WireMock state machine | Developer A | Second | Build against the frozen BFA contract and require B sign-off on lifecycle states and transitions. |
| Root-agent routing | Developer C | Third | Implement after the booking-versus-location boundary is frozen, not before. |
| Unresolved Step 7 Java prototype decision | Developer B | Decide first, implement last | Resolve in favor of the BFA model immediately; schedule refactoring only after Steps 2-6 are stable. |

---

## Architecture Overview

### Current Architecture

The repository already contains most of the H1 implementation baseline:
- The primary AGENT-007 plan defines the four-layer target state.
- `mock-server` hosts deterministic advisory appointment fixtures and WireMock mappings.
- `java/bfa-service-resource` exposes the appointment resource API and exports the advisory appointment OpenAPI spec.
- `ces-agent` contains the appointment toolset, specialist agent, root-agent routing updates, and evaluation pack.
- `java/voice-banking-app` still carries the legacy prototype model and remains partially aligned.

### Target Architecture

This execution plan keeps the architecture intentionally simple from a team-allocation perspective:
- **Use-case layer:** conversational boundaries, H1/P2 journey scope, and routing rules.
- **Resource API layer:** the dominant contract in `java/bfa-service-resource`.
- **CES toolset and agent layer:** consumer-facing tool semantics, instruction quality, and evaluation coverage.
- **Prototype alignment layer:** late-phase convergence of `java/voice-banking-app` onto the BFA model.

### Parallel delivery topology

| Layer | Dominant Owner | Supporting Owners | Boundary Rule |
|------|-----------------|-------------------|---------------|
| Use-case layer | Developer C | Developer B | Conversational behavior may not redefine contract fields. |
| Resource API layer | Developer B | Developer A | BFA request/response language is the source of truth. |
| Upstream mock domain | Developer A | Developer B | Fixtures simulate upstream truth and do not invent a second contract. |
| Prototype alignment layer | Developer B | Developer C | Alignment happens after the main resource API and CES surfaces stabilize. |

---

## Parallel Workstream Assignments

### Workstream A — Upstream Fixture and Scenario Platform

| Item | Recommendation |
|------|----------------|
| Owner | Developer A |
| Recommended seniority | Mid-level backend or test engineer with strong WireMock discipline |
| Why this level | The work is fixture-heavy and scenario-heavy rather than architecture-defining, but it requires care with priorities, header matching, and deterministic lifecycle behavior. Senior review is needed only on lifecycle semantics and cutover-seam implications. |
| Primary AGENT-007 step ownership | Step 3 primary; Step 8 supporting; Step 4 and Step 6 enablement support |

#### Files owned

| File | Change |
|------|--------|
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/taxonomy/**` | Taxonomy endpoint mappings and header contracts |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/service-search/**` | Service-search mappings |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/eligibility/**` | Booking-eligible location mappings |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/availability/**` | Slot and day availability mappings |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/lifecycle/**` | Deterministic create/get/reschedule/cancel mappings |
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/errors/**` | Negative-path mappings |
| `java/mock-server/src/main/resources/wiremock/__files/mobile.api/advisory-appointments/**` | Fixture payload bodies |
| `java/mock-server/src/test/java/com/acme/banking/demoaccount/appointment/AdvisoryAppointmentWireMockIntegrationTest.java` | Fixture integration coverage |
| `java/mock-server/test-advisory-appointments.sh` | Smoke verification for manual and CI-like checks |
| `java/mock-server/bruno/**` | Bruno coverage for manual exercise of the upstream mock surface |
| `ces-agent/docs/advisory-agent/mock-mode-cutover-preparation.md` | Upstream-side cutover notes and known fixture limitations |

#### Hand-off artefacts Developer A must produce first

- Fixture catalog manifest covering taxonomy, service search, eligibility, availability, lifecycle, and error payloads
- Scenario matrix for branch, phone, video, no-availability, invalid-contact, cancel, reschedule, blocked reschedule, and handoff-supporting journeys
- WireMock lifecycle state table using the approved scenario names and transitions
- Canonical example payloads per endpoint for BFA integration and CES evaluation design
- Header/profile checklist for `Authorization`, `X-BFA-Client`, `X-Correlation-ID`, and optional `X-Mock-Scenario`

#### What Developer A is blocked on

- Developer B must freeze parameter names, requiredness, and error codes before A finalizes mappings.
- Developer A is not blocked on Developer C and should not wait for CES instruction work to begin.
- Real `facecc` or partner-data consumption should not be started in this workstream because it is outside H1 scope and would add false dependencies.

---

### Workstream B — Contract and Resource API Spine

| Item | Recommendation |
|------|----------------|
| Owner | Developer B |
| Recommended seniority | Senior backend engineer or tech lead |
| Why this level | This stream owns the dominant appointment model, BFA contract shape, validation rules, OpenAPI export, and the Step 7 prototype direction. It has the highest blast radius and must coordinate both other workstreams. |
| Primary AGENT-007 step ownership | Step 1 primary, Step 2 primary, Step 4 primary, Step 5 primary, Step 7 primary, Step 8 consultative ownership |

#### Files owned

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md` | Contract freeze, step status, and late-phase decisions |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentController.java` | Endpoint surface |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentService.java` | Validation and orchestration logic |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentRepository.java` | Runtime overlay and upstream translation support |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentBranchResolver.java` | Booking-specific location resolution |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentSlotService.java` | Slot search, reservation, and lifecycle slot rules |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentMockServerClient.java` | Typed upstream client |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentUpstreamProperties.java` | Upstream header and base URL configuration |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentOpenApiConfig.java` | Standalone OpenAPI group |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentRequests.java` | Query/body request DTOs |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentResponses.java` | Response DTOs and shared view models |
| `java/bfa-service-resource/src/test/java/com/voicebanking/bfa/appointment/OpenApiSpecExportTest.java` | Export and required-parameter assertions |
| `java/bfa-service-resource/openapi-specs/advisory-appointment.json` | Generated contract artefact |
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/AppointmentContextAgent.java` | Step 7 alignment target |
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/integration/AppointmentBookingClient.java` | Provider seam target |
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/integration/MockAppointmentClient.java` | Legacy mock client alignment or deprecation target |

#### Hand-off artefacts Developer B must produce first

- Final endpoint inventory and operation naming
- Field-level request/response DTO schema with required versus conditional fields
- Error vocabulary and status-code mapping
- Upstream header profile and BFA-to-upstream parameter mapping
- Exported OpenAPI snapshot that Developer C can consume without reinterpretation
- Written Step 7 direction that the Java prototype will align to the BFA resource model instead of competing with it

#### What Developer B is blocked on

- Developer B can begin immediately after the shared design freeze.
- Full Step 4 integration is blocked on Developer A delivering stable fixture payloads and lifecycle scenarios.
- CES import validation is not a blocker for B’s initial implementation, but feedback from Developer C should be incorporated before calling the contract stable.

---

### Workstream C — CES Routing, Toolset, and Evaluation Layer

| Item | Recommendation |
|------|----------------|
| Owner | Developer C |
| Recommended seniority | Mid-level CES or conversational engineer with strong product sense |
| Why this level | The work is tool- and behavior-centric rather than deep backend infrastructure, but it requires judgment around routing precedence, instruction clarity, evaluation realism, and voice-friendly repair strategies. |
| Primary AGENT-007 step ownership | Step 6 primary, Step 1 routing support, Step 5 consumer alignment, Step 7 semantic alignment support |

#### Files owned

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Use-case source of truth for H1 journeys |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/advisory_appointment.json` | Toolset descriptor |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` | CES-tuned schema aligned to BFA export |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/advisory_appointment_agent.json` | Specialist agent descriptor |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt` | Specialist instruction |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` | Root-agent child wiring |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` | Routing precedence and public-service exception |
| `ces-agent/acme_voice_agent/evaluations/appointment_*/**` | Appointment routing, booking, repair, lifecycle, and handoff evaluations |
| `ces-agent/docs/advisory-agent/architecture/diagrams/bfa-appointments-flow*.mmd` | Optional visual support when instruction or evaluation changes require updated narrative flow diagrams |

#### Hand-off artefacts Developer C must produce first

- Routing matrix showing when appointment routing beats location routing
- Tool-to-use-case mapping for each H1 journey
- Evaluation matrix covering branch, phone, video, service request, no availability, contact repair, cancel, reschedule, blocked reschedule, and human handoff
- Instruction skeleton that enforces one question at a time, max-three slot options, summary confirmation, and no branch-only drift

#### What Developer C is blocked on

- Developer C needs Developer B’s final operation IDs, required fields, and OpenAPI export before the CES schema can be treated as final.
- Full evaluation reliability depends on Developer A’s deterministic scenario coverage, but Developer C can still begin use-case and routing work before A is finished.
- Developer C should not redefine endpoint semantics in the CES layer; mismatches must be pushed back to B rather than papered over in instructions.

---

## Two-developer fallback

If the team only has two developers available, keep the dominant contract ownership intact and collapse the less architecture-critical streams.

| Owner | Combined Scope | Why |
|------|----------------|-----|
| Developer 1 (Senior) | Developer B stream plus Step 7/8 coordination | The contract and resource API remain the critical path and cannot be split safely. |
| Developer 2 (Mid/Senior) | Developer A stream plus Developer C stream | Fixture authoring, CES wiring, evaluations, smoke scripts, and Bruno can still overlap after the contract freeze and are less dangerous to combine than splitting the API contract. |

---

## Implementation Steps

| Step | Short Summary | Status |
|------|---------------|--------|
| Step 1.0 | Freeze shared design anchors and publish hand-off artefacts | Implemented |
| Step 2.0 | Run Developer B contract and resource-API spine | Implemented for H1 surface; Partially implemented for Step 7 follow-through |
| Step 3.0 | Run Developer A fixture and scenario platform | Implemented for H1 surface |
| Step 4.0 | Run Developer C CES routing, toolset, and evaluation layer | Implemented for H1 surface |
| Step 5.0 | Close late-phase alignment and seam-hardening work | Partially implemented |

## Step 1.0: Freeze shared design anchors and hand-off artefacts

**Objective:** Prevent three parallel developers from building three different appointment models. This step freezes the shared contract, lifecycle semantics, and routing boundary before detailed implementation starts.  
**Effort:** 0.5-1 day  
**Risk:** LOW

### Files to Modify

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md` | Freeze schema appendix, routing precedence, lifecycle state model, and Step 7 decision |
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Lock H1 journey definitions and tool-to-journey mapping |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` | Freeze appointment versus location routing precedence |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` | Freeze child-agent wiring |

### Implementation Details

- Developer B is the tie-breaker for contract fields and requiredness.
- Developer A signs off lifecycle states only after the BFA create/get/reschedule/cancel model is understood.
- Developer C signs off the public-service exception and routing precedence only after booking-versus-location boundaries are frozen.
- No other workstream may override these artefacts locally.

### Success Criteria

- [x] Endpoint inventory is frozen in the main AGENT-007 plan.
- [x] Field-level DTO schemas exist and are reviewable.
- [x] WireMock lifecycle state transitions are defined.
- [x] Root-agent routing precedence is documented.
- [x] Step 7 direction is resolved in favor of aligning the Java prototype to the BFA model.

---

## Step 2.0: Run Developer B contract and resource-API spine

**Objective:** Build and preserve the dominant contract in `java/bfa-service-resource`, then export it cleanly to the CES-facing toolset.  
**Effort:** 2-3 days  
**Risk:** MEDIUM

### Files to Modify

| File | Change |
|------|--------|
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentController.java` | Stabilize resource endpoints |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentService.java` | Stabilize orchestration and validation |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentRepository.java` | Keep mutable overlay behavior deterministic |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentBranchResolver.java` | Keep booking-eligible location rules separate from generic location lookup |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentSlotService.java` | Own slot-search and reservation rules |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentMockServerClient.java` | Consume mock-server as upstream without leaking fixture ownership into BFA |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentRequests.java` | Maintain request DTO requiredness |
| `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/appointment/AppointmentResponses.java` | Maintain response DTO shape |
| `java/bfa-service-resource/src/test/java/com/voicebanking/bfa/appointment/OpenApiSpecExportTest.java` | Assert required fields remain stable in export |
| `java/bfa-service-resource/openapi-specs/advisory-appointment.json` | Exported contract artefact |

### Implementation Details

- This workstream owns the single source of truth for request fields, required parameters, response envelopes, and error vocabulary.
- The BFA layer may translate upstream payloads but must not become a second fixture catalog.
- Step 7 follow-through belongs here because the legacy prototype should conform to the BFA contract, not the other way around.

### Success Criteria

- [x] The appointment API surface exists in `java/bfa-service-resource`.
- [x] The exported OpenAPI spec reflects required parameters used by CES.
- [x] The BFA domain consumes `mock-server` as an upstream rather than owning fixture truth locally.
- [ ] The Java prototype has been fully realigned onto the BFA resource contract.
- [ ] The future provider seam is narrow enough that lifecycle execution is no longer split across multiple abstractions.

---

## Step 3.0: Run Developer A fixture and scenario platform

**Objective:** Provide deterministic upstream behavior and scenario coverage so the BFA contract and CES flows can be tested against a stable simulation boundary.  
**Effort:** 1.5-2.5 days  
**Risk:** MEDIUM

### Files to Modify

| File | Change |
|------|--------|
| `java/mock-server/src/main/resources/wiremock/mappings/mobile.api/advisory-appointments/**` | Stable endpoint mappings and priorities |
| `java/mock-server/src/main/resources/wiremock/__files/mobile.api/advisory-appointments/**` | Stable appointment fixture payloads |
| `java/mock-server/src/test/java/com/acme/banking/demoaccount/appointment/AdvisoryAppointmentWireMockIntegrationTest.java` | Integration proof for lifecycle and negative-path scenarios |
| `java/mock-server/test-advisory-appointments.sh` | Smoke verification of mock-server behavior |
| `java/mock-server/bruno/**` | Manual request flows and reusable API examples |
| `ces-agent/docs/advisory-agent/mock-mode-cutover-preparation.md` | Document fixture and cutover limitations from the upstream perspective |

### Implementation Details

- The mock-server surface should simulate upstream truth, not BFA behavior.
- Lifecycle scenarios must remain deterministic and reusable by smoke scripts, Bruno, and BFA integration tests.
- Negative cases are not optional; they are required to support recovery design in the CES layer.
- This stream explicitly does not own future internal `facecc` or partner-data integration work in H1.

### Success Criteria

- [x] Taxonomy, service search, eligibility, availability, lifecycle, and error mappings exist in `mock-server`.
- [x] WireMock scenario chains cover booked, rescheduled, and cancelled states.
- [x] Smoke and integration verification exist for the upstream mock boundary.
- [x] Branch, phone, video, and negative-path scenarios are deterministic.
- [ ] A future internal provider could replace the mock fixture surface without changing CES behavior or the BFA contract.

---

## Step 4.0: Run Developer C CES routing, toolset, and evaluation layer

**Objective:** Turn the BFA contract into a usable appointment specialist and a safe root-agent routing behavior for real conversational flows.  
**Effort:** 1.5-2 days  
**Risk:** MEDIUM

### Files to Modify

| File | Change |
|------|--------|
| `ces-agent/docs/advisory-agent/usecases/advisory-appointment-agent.md` | Keep the journey matrix and tool mapping current |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/advisory_appointment.json` | Toolset descriptor |
| `ces-agent/acme_voice_agent/toolsets/advisory_appointment/open_api_toolset/open_api_schema.yaml` | CES-facing schema tuned to the BFA export |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/advisory_appointment_agent.json` | Specialist agent definition |
| `ces-agent/acme_voice_agent/agents/advisory_appointment_agent/instruction.txt` | Booking and lifecycle instruction rules |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/voice_banking_agent.json` | Root-agent child routing |
| `ces-agent/acme_voice_agent/agents/voice_banking_agent/instruction.txt` | Routing precedence and public-service exception |
| `ces-agent/acme_voice_agent/evaluations/appointment_*/**` | Evaluation pack for routing, booking, repair, lifecycle, and handoff |

### Implementation Details

- Developer C owns routing behavior but not the underlying contract semantics.
- The CES layer must reflect the BFA export faithfully, especially on required fields and summary-confirmation gating.
- Evaluation coverage should be broad enough to prevent regressions when root-agent instructions or toolset imports change.

### Success Criteria

- [x] The specialist appointment agent is wired into the root agent.
- [x] Booking-qualified location queries route to the appointment agent instead of the generic location agent.
- [x] The CES toolset aligns with the generated advisory appointment OpenAPI spec.
- [x] Evaluations exist for routing, branch, phone, video, service-request, repair, lifecycle, blocked-reschedule, and human handoff journeys.
- [ ] CES behavior has been revalidated after Step 7 prototype alignment changes land.

---

## Step 5.0: Close late-phase alignment and seam-hardening work

**Objective:** Finish the two intentionally deferred pieces of AGENT-007: Step 7 prototype alignment and Step 8 provider-seam narrowing.  
**Effort:** 1-2 days  
**Risk:** MEDIUM

### Files to Modify

| File | Change |
|------|--------|
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/AppointmentContextAgent.java` | Remove legacy request assumptions and align to BFA concepts |
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/integration/AppointmentBookingClient.java` | Narrow provider seam toward BFA-aligned operations |
| `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/integration/MockAppointmentClient.java` | Realign or deprecate legacy mock behavior |
| `ces-agent/docs/advisory-agent/mock-mode-cutover-preparation.md` | Record the real seam boundaries and remaining cutover work |
| `ces-agent/docs/advisory-agent/concept-alignment-matrix.md` | Update divergence status between prototype and BFA resource model |
| `ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md` | Update step status when Step 7-8 are truly complete |

### Implementation Details

- This step must not reopen H1 schema design or root-agent routing debates.
- Prototype alignment is successful only when the legacy prototype no longer competes with the BFA contract language.
- Seam hardening is successful only when a future provider swap becomes closer to a client substitution than a service/repository redesign.

### Success Criteria

- [ ] The Java prototype uses the same dominant appointment concepts as the BFA resource API.
- [ ] The provider seam for lifecycle execution is documented and technically narrower than the current overlay-plus-client split.
- [ ] The primary AGENT-007 plan can move Step 7 from Partially implemented to Implemented with evidence.
- [ ] The primary AGENT-007 plan can move Step 8 from Partially implemented to Implemented with evidence.

---

## Testing Strategy

The testing strategy should mirror the workstream split so each owner can verify their seam locally while still contributing to a shared integration gate.

### Unit Tests

| Test File | Coverage |
|-----------|----------|
| `java/bfa-service-resource/src/test/java/com/voicebanking/bfa/appointment/OpenApiSpecExportTest.java` | Contract export, required parameter checks, and consumer-facing schema stability |
| `java/bfa-service-resource/src/test/java/com/voicebanking/bfa/appointment/*Test.java` | Controller, service, slot, repository, and upstream-client behavior where present |
| `java/voice-banking-app/src/test/java/...appointment...` | Prototype-alignment follow-through once Step 7 work begins |

### Integration Tests

1. **Upstream Fixture Integrity:** `mock-server` mappings and `__files` payloads resolve correctly, enforce headers, and support deterministic lifecycle scenarios.
2. **BFA to Mock-Server Contract:** `java/bfa-service-resource` consumes the upstream mock shape correctly for taxonomy, branches, slots, create, get, reschedule, and cancel.
3. **CES Toolset Alignment:** CES-facing YAML continues to reflect the generated BFA export without drift.
4. **Evaluation Stability:** Root routing and appointment specialist behavior continue to pass booking, recovery, lifecycle, and handoff scenarios.

### Manual Testing

- Run the `mock-server` advisory appointment smoke script and confirm branch, phone, video, and lifecycle cases remain green.
- Run the BFA advisory appointment smoke script against the local service and confirm create/get/reschedule/cancel behavior is still deterministic.
- Review the CES evaluation pack after any root-agent instruction or schema change.
- Re-check the parallel workstream hand-off artefacts whenever Step 7 or Step 8 changes contract or seam assumptions.

---

## Rollback Plan

### Emergency Rollback

1. Disable appointment routing from the root agent and return generic location behavior to its pre-AGENT-007 state.
2. Stop importing or using the advisory appointment toolset in CES.
3. Preserve the current location-service flow and keep advisory appointment work hidden from demos until fixed.

### Full Rollback

1. Revert `mock-server` advisory appointment mappings and body files.
2. Revert the `java/bfa-service-resource` appointment package and OpenAPI export.
3. Revert `ces-agent` appointment toolset, specialist agent, and evaluation pack.
4. Leave the primary AGENT-007 research and planning documents in place as historical design artefacts.

---

## Deployment Plan

### Pre-Deployment Checklist

- [x] Contract anchors and routing precedence are frozen in the primary AGENT-007 plan.
- [x] Deterministic mock-server advisory appointment coverage exists for H1 journeys.
- [x] Generated BFA OpenAPI and CES schema alignment exists.
- [x] CES evaluation scenarios exist for H1 journeys.
- [ ] Step 7 prototype alignment is complete.
- [ ] Step 8 seam hardening is complete.

### Deployment Steps

1. Merge and review the primary contract and workstream documents first.
2. Keep `mock-server` and `java/bfa-service-resource` changes compatible before importing or refreshing the CES toolset.
3. Refresh CES toolset and appointment agent artefacts only after the exported BFA contract is stable.
4. Complete Step 7 prototype alignment and Step 8 seam hardening after the H1 surface is stable and revalidated.

### Post-Deployment Verification

- Confirm the BFA export still matches the CES advisory appointment schema.
- Confirm appointment routing still wins over generic location routing when booking intent and location terms appear together.
- Confirm lifecycle operations still require `appointmentAccessToken` and summary confirmation.
- Confirm no one has reintroduced real internal integration assumptions into the H1 preparation baseline.

---

## Success Criteria

This plan is complete only when staffing decisions, dependencies, and sequential gates are explicit enough that the team can execute without re-litigating basic ownership.

- [x] The three-workstream split is explicit and mapped to AGENT-007 Steps 1-8.
- [x] Each workstream has a named owner, recommended seniority, owned files, hand-off artefacts, and dependency list.
- [x] The risky sequential decisions are identified and ordered before parallel execution.
- [x] The plan explicitly removes real internal `facecc` and partner-data integration from H1 parallel scope.
- [ ] Step 7 prototype alignment is completed and no longer competes with the BFA model.
- [ ] Step 8 seam hardening is completed and the cutover path is narrower than today.

---

## Known Issues & Limitations

- The primary AGENT-007 plan already reflects a partially completed implementation, so this companion plan is an execution-allocation document rather than a greenfield build plan.
- Step 7 and Step 8 remain incomplete, so any staffing decision that assumes the provider seam is already clean will be optimistic.
- `java/voice-banking-app` still carries legacy assumptions that are useful for reference but risky if treated as equal to the BFA contract.

## Future Enhancements

- Add a dedicated post-H1 plan for internal advisory-provider integration once real downstream APIs are accessible.
- Split Step 8 into separate provider-read and provider-write seams if the internal target architecture warrants more granularity.
- Add staffing guidance for a later production cutover team once the H1 preparation layer is accepted.

## Mentor Notes (Execution Planning)

The key staffing decision is to keep one technical owner on the contract and let the other two developers move in parallel only after that contract is frozen. That preserves speed without multiplying interpretation debt. The main trade-off is that Developer B becomes a deliberate critical path, but that is safer than letting the mock domain or CES layer define their own version of the appointment model.

The second important decision is to keep future internal integration out of the H1 workstream split. It is tempting to give Developer A “real upstream integration” work early, but doing so would make the plan look busier while actually increasing blockers and weakening the preparation-first boundary that AGENT-007 intentionally adopted.

Next steps:
- Review and approve the workstream ownership model.
- Assign named engineers to Developer A, B, and C roles.
- Use the primary AGENT-007 plan for step status and this companion plan for staffing, hand-offs, and merge order.

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-03-16 | GitHub Copilot | Created the AGENT-007 parallel workstream execution plan as a companion to the main implementation plan, using the implementation-plan template structure and adapting it to team allocation, hand-off artefacts, sequential gates, and remaining Step 7-8 work. |
