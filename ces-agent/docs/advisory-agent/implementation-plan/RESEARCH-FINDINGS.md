# Deutsche Bank Appointment Booking Research Findings

## Purpose

This document captures the observed Deutsche Bank appointment-booking patterns that are relevant to an `AppointmentContextAgent` implementation for Acme Bank voice banking.

The focus is on:
- appointment types and booking workflows
- prompts and user guidance
- collected data and validation rules
- implied backend integrations
- gaps versus the current `AppointmentContextAgent`
- recommendations that fit Acme Bank's H1 2026 appointment-scheduling scope

## Sources reviewed

Primary local sources:
- `ces-agent/docs/advisory-agent/advisory-promt.md`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.39.06-1.png`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.40.53-1.png`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.41.13-1.png`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.41.27-1.png`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.42.33-1.png`
- `ces-agent/docs/advisory-agent/screenshots/Screenshot 2026-03-15 at 10.42.42-1.png`

Live/public sources inspected:
- `https://www.deutsche-bank.de/opra4/pfb/advisor-appointments/`
- booking step routes under `#/book/*`
- public translation file under `assets/i18n/de.json`
- public service catalog under `assets/config/pws-services-data.json`
- frontend bundle metadata revealing routes and REST base path

Internal comparison target:
- `java/voice-banking-app/src/main/java/com/voicebanking/agent/appointment/AGENT-README.md`
- current appointment agent/domain classes in `com.voicebanking.agent.appointment`

## Important observation about the screenshots

The screenshots appear to have been browser-translated into English, while the live source strings are German. For requirements work, the German source strings are the more reliable source of truth; the screenshots are still useful for confirming step order, layout intent, and the presence of key fields.

## Executive summary

Deutsche Bank's booking flow is more than a simple slot-booking API wrapper. It is a guided, multi-step appointment orchestration flow with:
- a landing split between service requests and product consultation
- a channel-selection step for branch, video, phone, and desired-location appointments
- topic capture before slot selection
- conditional branch lookup and advisor matching
- structured personal-data capture with strict field validation
- a review/confirmation step before booking submission
- explicit success, error, timeout, and cancellation flows

For voice banking, the key reusable pattern is not the UI itself but the underlying conversation model:
- determine intent category
- capture topic/reason
- capture consultation channel
- capture branch/location if needed
- present day choices, then time choices
- collect contact details
- read back a summary
- require confirmation before booking

## Screenshot analysis summary

| Screenshot | Observed step | Key evidence | Requirement implication |
|---|---|---|---|
| `10.39.06-1` | Landing | Two entry choices: service request vs product consultation | The agent should start by identifying booking intent category, not by asking for a time slot immediately |
| `10.40.53-1` | Branch search | Search box for `PLZ, Stadt oder Straße` | Branch lookup supports postal code, city, or street; voice flow should accept location in multiple forms |
| `10.41.13-1` | Branch results | List/map style results with distance and phone numbers | Branch search should return ranked options, not just a single branch ID |
| `10.41.27-1` | Time slot selection | Day-first selection and then advisor/time choices | Voice flow should separate date selection from time selection and read a limited set of options |
| `10.42.33-1` | Personal data | First name, last name, email, phone, customer toggle, privacy note | Contact capture is part of booking, not an external concern |
| `10.42.42-1` | Confirmation | Editable summary of branch, date/time, advisor, and customer data | Voice flow needs a summary-and-confirm step before final submission |

Additional screenshot-backed observations:
- A persistent fallback contact/callback area is visible throughout the flow.
- The top navigation shows a stable booking journey model: topic selection, consultation process, appointment selection, data entry, confirmation.
- In the screenshot sequence provided, the service-request path appears to skip the consultation-channel decision and proceed directly into branch selection and slot booking.

## Appointment type taxonomy

### 1. Entry-path taxonomy

| Entry path | User-facing label | Meaning | Voice-banking equivalent |
|---|---|---|---|
| Service request | `Termin: Service beauftragen` | Appointment for service/help topics | "Do you need help with a service request or advice on a product?" |
| Product consultation | `Termin: Beratung zu Produkten` | Appointment for advice about banking products | "What would you like advice about?" |

### 2. Consultation channel taxonomy

| Consultation channel | German label | Guidance text summary | Notes for Acme |
|---|---|---|---|
| Branch consultation | `Beratung am Bankstandort` | In-person appointment at a preferred branch or financial agency | High-priority H1 pattern |
| Video consultation | `Videoberatung` | Remote video consultation with extended opening hours | High-priority H1 pattern |
| Telephone consultation | `Telefonberatung` | Remote phone consultation with extended opening hours | High-priority H1 pattern |
| Desired location consultation | `Beratung am Wunschort` | Advisor meets customer at a chosen location such as home/work | Useful pattern, but likely P2 for Acme |

### 3. Advisor-mode taxonomy

The booking content distinguishes advisor modes:
- internal advisor
- independent advisor
- private banking advisor

This matters because channel availability and copy differ by advisor mode. For Acme, the immediate design takeaway is that `consultationChannel` and `advisorMode` should be modeled separately.

### 4. Topic taxonomy

Observed product topic codes and labels:
- `BF` — Immobilienfinanzierung
- `VS` — Zukunftsvorsorge
- `IN` — Investments
- `PK` — PrivatKredit
- `EL` — Sparen / Bausparen
- `BA` — Konto & Karte
- `FC` — FinanzCheck
- `SO` — Service

The live service-request path also uses a searchable service catalog rather than a small fixed topic list.

## User flow diagram

### A. Product consultation flow

Landing
→ topic selection
→ topic-specific clarification / subject specification
→ consultation channel selection
→ branch selection if required
→ date selection
→ time slot selection
→ personal data capture
→ summary / review
→ booking submission
→ success

### B. Service request flow

Landing
→ free-text service search
→ branch selection
→ date selection
→ time slot selection
→ personal data capture
→ summary / review
→ booking submission
→ success

### C. Cancellation flow

Cancellation link with appointment identifier and hash
→ cancellation confirmation
→ cancellation success or error

### D. Exceptional flows observed in the app

- no timeslots available
- unknown ZIP / invalid location
- booking error
- session timeout
- not-allowed / unsupported user segment

## Prompts and user guidance

### High-value prompt inventory

| Step | German source text | Literal English meaning | Pattern to reuse in voice |
|---|---|---|---|
| Landing | `Wofür möchten Sie einen Termin vereinbaren?` | What would you like to schedule an appointment for? | Start broad, then narrow |
| Service search | `Worum geht es?` | What is it about? | Ask for the reason in free language |
| Service prep | `Damit wir uns gut auf den Termin vorbereiten können...` | So we can prepare well for the appointment... | Explain why the information is needed |
| Consultation choice | `Wie möchten Sie beraten werden?` | How would you like to be advised? | Offer channel choices clearly |
| Topic selection | `Ich interessiere mich für` | I am interested in | Topic-first guidance for product advice |
| Desired location | `Wo soll Ihre Beratung stattfinden?` | Where should your consultation take place? | Ask for place only when relevant |
| Timeslot day | `Wählen Sie einen Tag` | Choose a day | Offer dates first |
| Timeslot time | `Wählen Sie die Uhrzeit` | Choose the time | Then offer times |
| Personal data | `Ihre Kontaktdaten` | Your contact details | Explain that contact info is required to complete booking |
| Summary | `Bitte bestätigen Sie Ihre Eingaben` | Please confirm your entries | Always read back the summary |
| Success | `Vielen Dank ...` | Thank you ... | End with confirmation and next steps |

### Channel-selection guidance patterns

Observed guidance is concise and task-oriented:
- branch: explains that the appointment is in a preferred branch/agency
- video: explains remote consultation and extended hours
- phone: explains telephone consultation and extended hours
- desired location: explains advisor travel to the customer's chosen place

This is voice-friendly because each option contains:
- channel name
- plain-language benefit
- operational qualifier if relevant

Recommended voice adaptation:
- "You can do this in a branch, by video, by phone, or at a preferred location. Which would you like?"
- If the user hesitates, offer one-line explanations per option.

### Validation and error messages identified

No field-error state is visible in the provided screenshots, but the live translation data exposes the validation model.

Representative examples:
- invalid ZIP: `Bitte geben Sie eine gültige PLZ ein.`
- unknown ZIP: `Diese PLZ ist leider nicht bekannt.`
- no matching service result: alternate search term or direct callback/consultation recommended
- missing branch selection: `Bitte wählen Sie eine Filiale aus.`
- missing first name / last name / email / phone / salutation
- pattern and length validation for personal data fields
- booking unavailable / generic booking error
- no timeslots available for branch or advisory center
- cancellation success / error / abort flows

### Voice-friendly language patterns worth reusing

Patterns that translate well to voice:
- brief, direct questions
- a short explanation of why data is needed
- polite corrective prompts tied to one field at a time
- summary before final action
- explicit fallback when no result is available

Recommended voice principles:
- ask one question at a time
- never read more than 3 slot options in one turn
- confirm spelled entities such as email and branch choice
- use recovery prompts like "I didn't catch the postal code" or "Would you like another day?"

## Data model requirements

### Observed booking data fields

| Field | Step | Type | Required | Validation / notes | Dependencies |
|---|---|---|---|---|---|
| `entryPath` | landing | enum | yes | `SERVICE_REQUEST` or `PRODUCT_CONSULTATION` | drives next step |
| `serviceSearchTerm` | service search | string | yes for service path | free text; no-result state supported | used only for service-request flow |
| `topicCode` | topic selection | enum | yes for product path | one of `BF`, `VS`, `IN`, `PK`, `EL`, `BA`, `FC`, `SO` | drives follow-up clarification |
| `subjectSpecificationSelections` | topic clarification | list | conditional | topic-specific checkbox selections | depends on `topicCode` |
| `subjectSpecificationInputs` | topic clarification | map | conditional | optional numeric/text inputs for some selections | depends on `topicCode` |
| `comment` | topic clarification | string | optional for most topics; required for `SO` service topic | required-message exists for service topic | depends on `topicCode` |
| `consultationChannel` | consultation choice | enum | yes unless skipped | branch, video, phone, desired location | may be skipped for service path |
| `advisorMode` | derived/system | enum | derived | internal / independent / private banking | changes available channels and copy |
| `branchSearchQuery` | branch search | string | conditional | postal code, city, or street | used for branch lookup |
| `zipCode` | RBC / location lookup | string | conditional | valid German ZIP, exactly 5 digits in ZIP-specific flow | used for remote-center lookup |
| `branchId` | branch selection | identifier | conditional | must be selected when branch choice is required | required for branch-based scheduling |
| `branchDisplayName` | branch selection | string | derived | branch name, address, phone, distance | used in confirmation |
| `desiredLocationCareOf` | desired-location data | string | optional | appears in summary placeholders | only for desired-location consultations |
| `desiredLocationStreetWithHouseNumber` | desired-location data | string | conditional | address validation implied | only for desired-location consultations |
| `desiredLocationZip` | desired-location data | string | conditional | ZIP validation implied | only for desired-location consultations |
| `desiredLocationCity` | desired-location data | string | conditional | address validation implied | only for desired-location consultations |
| `desiredLocationCountry` | desired-location data | string | likely defaulted | address validation service includes country | only for desired-location consultations |
| `selectedDay` | timeslot | date | yes | chosen before time | drives available time options |
| `selectedTimeSlotId` | timeslot | identifier | yes | booking is slot-based | required for final booking |
| `selectedStartTime` | timeslot | datetime | yes | shown in summary and success message | paired with slot |
| `advisorId` | timeslot | identifier | derived/conditional | specific advisor shown with slot | can vary by slot |
| `advisorName` | timeslot | string | derived | displayed to customer | used in summary and confirmation |
| `salutation` | personal data | enum | yes | `Frau` / `Herr` in source flow | required |
| `firstName` | personal data | string | yes | pattern, min 2, max 35 | shown in confirmation |
| `lastName` | personal data | string | yes | pattern, min 2, max 35 | shown in confirmation |
| `email` | personal data | string | yes | pattern, min 5, max 105 | used for confirmation and video access |
| `phone` | personal data | string | yes | pattern, min 9, max 23; async validation implied | used for advisor contact/fallback |
| `isExistingCustomer` | personal data | boolean | yes | yes/no toggle visible | gates extra customer identifiers |
| `branchNumber` | personal data | string | conditional | exactly 3 digits | shown when existing-customer path requires it |
| `accountNumber` | personal data | string | conditional | exactly 7 digits | shown when existing-customer path requires it |
| `privacyConsentNoticeSeen` | personal data | boolean/system | implicit | source says contact data used only for confirmation | should be surfaced in voice summary |
| `bookingConfirmationEmail` | success | derived | yes | email confirmation is always promised | confirmation mechanism is email-centric |
| `videoAccessDelivery` | success | derived | conditional | video access details are sent by email before the appointment | only for video consultations |
| `cancelHash` | cancellation | token/string | conditional | required together with appointment id in cancel flow | cancellation deep-link model |
| `appointmentId` | cancellation/update | identifier | conditional | required for cancellation flow | downstream lifecycle management |

### Topic-specific clarification model

The booking flow collects more than a generic "notes" field.

Observed examples:
- mortgage / property financing: buy, build, modernize, refinance, existing financing discussion
- pension / insurance: retirement planning, income protection, family protection, children's savings
- investments / savings: monthly savings, fixed investment amount, discussion of existing investments
- private credit: new financing project or discussion of existing financing
- account & card: private account opening, new card / credit card
- financial check: overall financial situation, existing financial check discussion

Design implication:
- Acme should model `topicCode` plus `topicDetails`, not just a free-form appointment reason
- voice flow should support both guided options and "something else" free text

## Conditional logic and branching rules

Observed or strongly implied:
- service-request path uses free-text search and then proceeds toward branch appointment booking
- service topic `SO` makes the comment field mandatory
- consultation channel changes subsequent steps:
  - branch consultation requires branch selection
  - desired location requires address capture and likely advisor matching
  - phone/video appear to use advisory-center availability rather than branch-only availability
- advisor mode influences which channels are shown
- existing-customer flag can trigger branch-number and account-number capture
- video success flow adds email delivery of access credentials
- if no timeslots are available, the flow diverts to a fallback message rather than continuing blindly

## Integration points inferred from the live app

## Booking backend

The frontend bundle exposes a REST base path:
- `/opra4x/api/advisor-appointments/rest/`
- relative equivalent: `../../../opra4x/api/advisor-appointments/rest`

This strongly implies an appointment domain backend supporting at least:
- availability lookup
- booking creation
- booking cancellation
- branch/advisor lookup
- possibly customer enrichment and validation calls

## Specific integration capabilities implied

| Capability | Evidence | Likely purpose |
|---|---|---|
| token exchange / refresh | bundle contains access-token retrieval and refresh strings | authenticate frontend calls to booking backend |
| appointment booking service | `AdvisorAppointmentService` string in bundle | create and manage appointments |
| cancellation service | cancellation flow requires `id` and `hash` | secure cancel via emailed/deep-linked token |
| branch locator | branch search by ZIP/city/street, list/map view, branch distance | find eligible branches for appointment booking |
| advisory-center lookup | remote consultation ZIP flow and RBC messaging | assign remote advisors / centers based on geography |
| service-topic catalog | `assets/config/pws-services-data.json` | search service reasons and map them to product/topic domains |
| phone validation | async phone-check function present in bundle | validate callback number before booking |
| address validation / geocoding | address validation request and geocoding hints in bundle | validate desired-location address |
| email confirmation | privacy notice and success content | send booking confirmation email |
| video access delivery | video success content explicitly mentions email access data | send meeting access details before appointment |
| callback / handoff | visible callback service and Genesys callback widget integration | fallback when self-service booking cannot complete |

## Service catalog observations

The public service catalog file indicates:
- allowed source `PWS`
- a large keyword/topic inventory for service discovery
- mapping of product/topic families such as `PK`, `BA`, `EL`, `BF`, `VS`, `FC`

Design implication for Acme:
- a voice-first agent should support both guided category selection and free-text topic search
- the catalog can remain an internal lookup table or search index behind the conversational layer

## Comparison with current AppointmentContextAgent

### What the current implementation already does well

Current strengths in `AppointmentContextAgent`:
- supports lifecycle operations: list, check availability, request, modify, cancel
- already produces voice-friendly response text
- already supports natural-language date input
- already enforces a 2-hour modify/cancel rule
- already returns confirmation numbers and structured appointment payloads

These are good building blocks and should be retained.

### Gap analysis

| Area | Deutsche Bank pattern | Current implementation | Gap |
|---|---|---|---|
| Entry-path handling | service request vs product consultation split | no entry-path model | missing conversation entry classification |
| Consultation channels | branch, video, phone, desired location | appointment types are business-purpose enums, not channel enums | channel-first booking model is missing |
| Topic capture | structured topic taxonomy plus topic-specific clarifications | simple `AppointmentType` plus optional notes | too little domain context before scheduling |
| Service search | searchable free-text service catalog | not supported | no service-request intake flow |
| Guided multi-step state | explicit steps through topic, consultation, slot, data, summary | tool-level operations only | no booking conversation state machine |
| Branch locator | search by ZIP/city/street with ranked results | requires direct `branchId` | no branch discovery flow |
| Remote consultation handling | phone/video advisory center handling | no dedicated remote consultation model | remote channel orchestration missing |
| Desired-location appointments | address capture and validation | not supported | no off-site appointment model |
| Personal data capture | salutation, name, email, phone, customer status, branch/account numbers | only `customerId`, optional notes, contact preference | customer/contact schema far too thin |
| Validation UX | field-level required, length, pattern, and no-results messages | basic request validation only | missing granular validation and repair prompts |
| Summary step | explicit review before submit | immediate booking once request is valid | no final spoken confirmation step |
| Confirmation behavior | email confirmation always; video access via email; phone follow-up wording | generic voice confirmation plus optional contact preference | confirmation mechanics not aligned with observed pattern |
| Cancellation security | link flow includes appointment id plus hash | appointment id only | no secure deep-link cancellation token model |
| Advisor context | slot list includes advisor names and gender-aware phrasing | advisor exists on appointment but is not part of guided selection | advisor-aware slot explanation is limited |
| Topic-specific optional inputs | e.g. planned amount, financing purpose | not supported | insufficient detail collection for advisory preparation |

### Model-level differences

Current model centers on `AppointmentType` values such as:
- `GENERAL_INQUIRY`
- `ACCOUNT_OPENING`
- `MORTGAGE_CONSULTATION`
- `INVESTMENT_ADVICE`
- `CREDIT_APPLICATION`
- quick-service types

The Deutsche Bank flow separates:
- consultation channel
- advisory topic
- service/product entry path
- advisor mode
- slot selection
- customer/contact capture

That separation is more suitable for a voice-guided appointment context agent because it mirrors how users naturally answer booking questions.

## Recommended enhancements for Acme's AppointmentContextAgent

### H1 2026 must-have enhancements

1. Introduce a conversation-state model
- `LANDING`
- `SERVICE_SEARCH`
- `TOPIC_SELECTION`
- `TOPIC_DETAIL_CAPTURE`
- `CONSULTATION_CHANNEL_SELECTION`
- `BRANCH_SELECTION`
- `DAY_SELECTION`
- `TIME_SELECTION`
- `PERSONAL_DATA_CAPTURE`
- `SUMMARY_CONFIRMATION`
- `BOOKING_SUCCESS`
- `BOOKING_ERROR`

2. Replace or augment `AppointmentType` with richer booking context
- `entryPath`
- `consultationChannel`
- `topicCode`
- `topicDetails`
- `advisorMode`

3. Add structured customer/contact capture
- salutation
- first/last name
- email
- phone
- existing-customer flag
- optional customer identifiers when needed

4. Add branch-discovery and slot-presentation tools
- branch search by postal code, city, or street
- ranked branch options with concise spoken summaries
- day-first then time-first slot reading

5. Add a spoken review-and-confirm step before `requestAppointment`
- branch
- consultation channel
- day/time
- advisor if known
- contact details
- topic summary

6. Standardize confirmation output
- always confirm booking number
- always confirm email follow-up
- for video appointments, explicitly mention delivery of access details

### H1 2026 should-have enhancements

1. Support remote consultation channels directly
- branch
- video
- phone

2. Add topic-specific detail capture for the highest-value advisory journeys
- mortgage / financing
- investments / savings
- account opening / card requests

3. Add no-availability recovery prompts
- alternative day
- alternative branch
- fallback callback request
- human handoff

4. Add field-level repair prompts for voice
- invalid email
- invalid phone
- unrecognized branch/location
- missing required reason

### P2 / later enhancements

1. Desired-location appointments
- address capture
- address validation
- advisor travel / location suitability rules

2. Service-catalog search
- synonym handling
- fuzzy match from free-form voice input
- mapping to internal Acme service categories

3. Secure cancel/reschedule deep links
- appointment id plus secure token/hash

4. Advisor-preference / named-advisor journeys
- specific advisor selection where policy allows

## Recommended Acme voice data model

Suggested aggregate model for a future `AppointmentContextAgent`:

- `AppointmentIntentContext`
  - `entryPath`
  - `consultationChannel`
  - `topicCode`
  - `topicDetails`
  - `serviceSearchTerm`
  - `advisorMode`

- `AppointmentPreference`
  - `branchSearchQuery`
  - `branchId`
  - `desiredLocation`
  - `selectedDay`
  - `selectedSlotId`
  - `advisorId`

- `CustomerContactProfile`
  - `isExistingCustomer`
  - `salutation`
  - `firstName`
  - `lastName`
  - `email`
  - `phone`
  - `branchNumber`
  - `accountNumber`

- `BookingReview`
  - `summaryText`
  - `confirmationRequired`
  - `lastConfirmedAt`

- `BookingOutcome`
  - `appointmentId`
  - `confirmationNumber`
  - `confirmationChannel`
  - `videoAccessDeliveryStatus`
  - `followUpInstructions`

## Voice UX adaptations for Acme

### Recommended conversational pattern

1. Identify the booking goal
- service help or product advice

2. Capture the reason
- guided choices where possible
- free text when needed

3. Capture the appointment channel
- branch, phone, video, preferred location

4. Ask for location only if required
- branch search or desired-location address

5. Offer dates first, then time slots
- no more than 3 spoken options at once

6. Collect contact details step by step
- spell back email and phone when confidence is low

7. Read back a compact summary
- reason
- channel
- branch or location
- date/time
- contact details

8. Require an explicit final confirmation
- only then create the booking

### Specific voice safeguards

- If the user says "any branch is fine", branch search should automatically rank nearby options.
- If no slot is available, immediately offer another day or another channel.
- If the user gives a long free-form reason, summarize it back before continuing.
- If the user is already authenticated in voice banking, avoid re-asking for known customer profile data unless required for booking.

## Practical implementation implications for the current codebase

Most of the current code does not need to be discarded. Instead, the current appointment tools can become lower-level execution tools beneath a richer orchestration layer.

Recommended direction:
- keep `getMyAppointments`, `modifyAppointment`, and `cancelAppointment`
- evolve `checkAvailability` to accept consultation channel, topic, branch search context, and advisor mode
- evolve `requestAppointment` to accept a richer booking context rather than only `type`, `slotId`, `branchId`, and `notes`
- introduce a booking-session/context object that survives across multiple voice turns
- add validation/result objects designed for slot-filling and repair prompts

## Conclusion

The Deutsche Bank flow demonstrates a mature appointment-booking pattern built around guided context gathering rather than immediate slot booking. The most relevant lessons for Acme are:
- treat appointment booking as a conversational workflow, not a single API call
- separate channel selection from topic selection
- capture structured reason/context before availability lookup
- collect personal/contact data as part of the booking flow
- require a spoken summary before final submission
- support graceful fallback when no slots are available

For Acme H1 2026, the best-fit subset is:
- branch, phone, and video consultation channels
- structured topic capture for major banking/advisory themes
- branch lookup + day/time slot selection
- customer/contact capture
- explicit spoken confirmation before booking
- email-based booking confirmation

Desired-location appointments, deep service-catalog search, and advanced advisor-routing can follow as later enhancements.
