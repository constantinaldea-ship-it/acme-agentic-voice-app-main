# AppointmentContextAgent

> **Agent ID:** `appointment-context`  
> **Package:** `com.voicebanking.agent.appointment`  
> **Status:** ✅ Implemented prototype / compatibility reference  
> **Category:** Category 4 — Daily Banking Execution (Read+Write)  
> **Priority:** 🟡 P2 Important  
> **Implementation Plan:** [AGENT-007-appointment-context.md](../../../../../../../../../ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md)

---

## Agent Description

The **AppointmentContextAgent** manages branch appointments and advisor scheduling. It integrates with the Appointment Booking API (I-20) to view existing appointments, check availability, and request new appointments. This agent supports the **"schedule an appointment"** use case identified in the intent analysis and confirmed as a priority feature for H1 2026 (per Acme Bank Kickoff Meeting scope document).

> **Important:** For AGENT-007 build work, this prototype is no longer the source-of-truth contract. The canonical H1 appointment surface is the `bfa-service-resource` appointment API and its CES OpenAPI toolset baseline.

### Role in System

- **Primary Use:** Appointment management for branch visits and advisor meetings
- **Interface:** I-20 Appointment Booking API
- **User Intents:** "Schedule an appointment", "What appointments do I have?", "Cancel my appointment", "Reschedule my meeting"

---

## Capabilities (Tools)

| Tool ID | Description | Input Parameters | Output |
|---------|-------------|------------------|--------|
| `getMyAppointments` | List customer's appointments | `customerId`, `status` (UPCOMING/PAST/CANCELLED) | `appointments`, `count`, `voiceMessage` |
| `checkAvailability` | Check available appointment slots | `type`, `branchId`, `preferredDate`, `preferredTime`, `week` | `slots`, `hasAvailability`, `voiceMessage` |
| `requestAppointment` | Book a new appointment | `customerId`, `type`, `slotId`, `branchId`, `notes` | `appointment`, `confirmationNumber`, `voiceMessage` |
| `modifyAppointment` | Reschedule an existing appointment | `appointmentId`, `newSlotId` | `appointment`, `voiceMessage` |
| `cancelAppointment` | Cancel an existing appointment | `appointmentId`, `reason` | `status`, `voiceMessage` |

### Tool Usage Examples

```
getMyAppointments { customerId: "CUST-123", status: "UPCOMING" }
→ { success: true, count: 2, appointments: [...], voiceMessage: "You have two upcoming appointments..." }

checkAvailability { type: "MORTGAGE_CONSULTATION", branchId: "BR-001", week: "next week" }
→ { success: true, hasAvailability: true, slots: [...], voiceMessage: "I found 3 available slots..." }

requestAppointment { customerId: "CUST-123", type: "GENERAL_INQUIRY", slotId: "SLOT-001" }
→ { success: true, confirmationNumber: "CONF-2026-1234", appointment: {...}, voiceMessage: "I've scheduled your..." }

modifyAppointment { appointmentId: "APT-1001", newSlotId: "SLOT-002" }
→ { success: true, appointment: {...}, voiceMessage: "I've rescheduled your appointment..." }

cancelAppointment { appointmentId: "APT-1001", reason: "Changed plans" }
→ { success: true, status: "CANCELLED", voiceMessage: "Your appointment has been cancelled..." }
```

---

## Problem Statement

### Business Problem
Customers need to:
- Schedule appointments with branch advisors for complex banking needs
- View and manage their existing appointments
- Reschedule or cancel appointments when plans change
- Find available times that fit their schedule

Voice-enabled appointment management provides convenience and accessibility.

### Technical Problem
Need to:
- Integrate with Appointment Booking API (I-20)
- Support multiple appointment types with different durations and requirements
- Handle natural language date/time expressions
- Enforce business rules (2-hour modification window)
- Provide voice-friendly confirmations and summaries

### FR Coverage
- **FR-010:** Appointment Management (To be created)

---

## Solution Approach

### Architecture Pattern
```
User Request: "Schedule an appointment with an advisor next week"
         │
         ▼
AppointmentContextAgent
    │
    ├── AvailabilityService.checkAvailability()
    │       └── Find available slots based on type, branch, date preferences
    │
    ├── CalendarFormatter
    │       └── Parse natural language dates, format voice responses
    │
    ├── AppointmentBookingClient
    │       └── CRUD operations for appointments
    │
    └── AppointmentLogService
            └── Audit logging for all appointment operations
```

### Key Design Decisions

1. **Appointment Types:** Enum-based with configurable durations
   - `GENERAL_INQUIRY` — 30 min, no confirmation
   - `MORTGAGE_CONSULTATION` — 60 min, requires confirmation
   - `INVESTMENT_ADVICE` — 60 min, requires confirmation
   - `CREDIT_APPLICATION` — 45 min, requires confirmation
   - `ACCOUNT_OPENING` — 45 min, requires confirmation
   - `SAFE_DEPOSIT_BOX` — 15 min, no confirmation
   - `DOCUMENT_CERTIFICATION` — 15 min, no confirmation

2. **Modification Rules:** 2-hour window enforced for modifications and cancellations

3. **Natural Date Parsing:** Supports "today", "tomorrow", "next week", "this Friday", etc.

4. **Voice Responses:** All tools return voice-friendly messages suitable for TTS

---

## Appointment Types

| Type | Description | Duration | Confirmation Required | Advisor Required |
|------|-------------|----------|----------------------|------------------|
| GENERAL_INQUIRY | General banking questions | 30 min | No | No |
| ACCOUNT_OPENING | New account setup | 45 min | Yes | No |
| MORTGAGE_CONSULTATION | Mortgage advisory | 60 min | Yes | Yes |
| INVESTMENT_ADVICE | Wealth management | 60 min | Yes | Yes |
| CREDIT_APPLICATION | Loan or credit | 45 min | Yes | Yes |
| SAFE_DEPOSIT_BOX | Safe deposit access | 15 min | No | No |
| DOCUMENT_CERTIFICATION | Document services | 15 min | No | No |

---

## Dependencies

### Internal
- `AvailabilityService` — Availability checking and slot management
- `CalendarFormatter` — Date/time formatting for voice
- `AppointmentLogService` — Audit logging
- `AppointmentBookingClient` — API integration

### External
- Appointment Booking API (I-20) — Mocked via `MockAppointmentClient`

### Package Structure
```
appointment/
├── AppointmentContextAgent.java      # Main agent
├── AGENT-README.md                   # This file
├── domain/
│   ├── Appointment.java              # Appointment model
│   ├── AppointmentStatus.java        # Status enum
│   ├── AppointmentType.java          # Type enum with durations
│   ├── BookingRequest.java           # Request model with validation
│   └── TimeSlot.java                 # Available slot record
├── integration/
│   ├── AppointmentBookingClient.java # Interface for I-20 API
│   └── MockAppointmentClient.java    # Mock implementation
└── service/
    ├── AppointmentLogService.java    # Audit logging
    ├── AvailabilityService.java      # Availability logic
    └── CalendarFormatter.java        # Date/time utilities
```

---

## Current Gaps

| Gap | Description | Impact | Priority |
|-----|-------------|--------|----------|
| **No Real API Integration** | Uses mock client | High for production | P0 |
| **No Video Appointments** | Only branch appointments | Medium | P2 |
| **No Advisor Preference Matching** | Cannot request specific advisor | Low | P3 |
| **No Reminder System** | Cannot send appointment reminders | Medium | P2 |
| **No Calendar Integration** | Cannot add to customer calendar | Low | P3 |
| **Limited Natural Language** | Basic date parsing only | Medium | P2 |

### Comparison to Implementation Plan

| Planned Tool | Status | Notes |
|--------------|--------|-------|
| `getMyAppointments` | ✅ Implemented | With status filtering |
| `checkAvailability` | ✅ Implemented | With natural date parsing |
| `requestAppointment` | ✅ Implemented | With validation |
| `modifyAppointment` | ✅ Implemented | With 2-hour rule |
| `cancelAppointment` | ✅ Implemented | With reason tracking |

**All 5 planned tools implemented.** ✅

---

## Alternative Approaches

### Current: Agent Pattern
```
AppointmentContextAgent implements Agent
    → Tools for appointment orchestration
    → Called by Orchestrator for scheduling intents
```

### Alternative: Direct Service Integration
Could integrate appointment services directly into the orchestrator, but the Agent pattern provides:
- Clear domain boundaries
- Testability through interface abstraction
- Consistency with other agents

---

## Testing

### Unit Tests
- `AppointmentContextAgentTest.java` — 20+ test cases using stub implementations
- Tests all 5 tools with success and error scenarios
- Uses stub pattern to avoid Mockito compatibility issues

### Test Coverage
- Agent interface compliance
- All tool implementations
- Error handling and validation
- Voice message generation

### Manual Testing
```bash
# Run tests
cd java/voice-banking-app
mvn test -Dtest=AppointmentContextAgentTest
```

---

## Voice Response Examples

### Viewing Appointments
> "You have two upcoming appointments. The first is a general consultation at the Munich Central branch on Tuesday, February 4th at 2:00 PM. The second is an investment advisory meeting on February 12th at 10:00 AM. Would you like details on either?"

### Checking Availability
> "I found 3 available slots for a mortgage consultation. The first is this Friday at 9:00 AM. The second is next Monday at 2:00 PM. Or next Wednesday at 11:00 AM. Which works best for you?"

### Booking Confirmation
> "I've scheduled your mortgage consultation for Thursday, February 6th at 9:00 AM at the Munich Central branch with Hans Müller. Your confirmation number is CONF-2026-1234. You'll receive a confirmation email shortly."

### Cancellation
> "Your general inquiry appointment for Thursday, February 6th has been cancelled. Would you like to schedule a new appointment?"

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| 2026-01-25 |  | Initial implementation — All 5 tools, domain models, services, integration client |

---

## Related Documentation

- [AGENT-007 Implementation Plan](../../../../../../../../../ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md)
- [AGENT-ARCHITECTURE-MASTER.md](../../../../../docs/implementation-plan/AGENT-ARCHITECTURE-MASTER.md)
- [Voice Banking Deck](../../../../../docs/business/voice-banking-deck.md) — Appointments H1 26 priority
