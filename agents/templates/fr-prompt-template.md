# FR-{NUMBER}: {Short Descriptive Title}

> PHASE: DESIGN_ONLY  
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**Author:** { | Gemini | Codex | Developer Name}
**Created:** {YYYY-MM-DD}
**Status:** {NOT_STARTED | IN_PROGRESS | COMPLETED | BLOCKED}
**Implementation Plan:** [docs/implementation-plan/FR-{NUMBER}-{slug}.md](../../docs/implementation-plan/FR-{NUMBER}-{slug}.md)
**Related:** {Links to related FRs, e.g., [FR-01 Voice Balance](./fr-01-voice-balance.md)}
**Estimated Credits:** {X-Yk tokens expected for this FR}

**Forbidden in this phase (DESIGN_ONLY):** No fenced code blocks, no diffs/patch syntax, and no explicit file-edit instructions. Keep outputs limited to design intent, scope, risks, test approach, and open questions.

**Approval request:** When the design is ready, explicitly ask the reviewer to reply with `APPROVE_IMPLEMENTATION` before sharing patches or tooling steps.

---

## Feature Overview

{1-3 paragraph summary of what this feature does and why it matters. Answer:
- What problem does this solve?
- What value does it provide to users?
- How does it fit into the broader system?

Keep this high-level and business-focused. Technical details go in Technical Requirements.}

---

## Technical Requirements

{Break down the feature into discrete technical requirements. Each requirement should be:
- Specific: Clearly defined scope
- Measurable: Can be verified as complete
- Independent: Can be implemented separately (where possible)
- Testable: Has clear success criteria

Use the format TR-{N} for each requirement. Include effort estimates.}

### TR-1: {Requirement Title}
- {Bullet point describing what needs to be built}
- {Technical details: interfaces, modules, functions}
- {Integration points: what existing code this touches}
- Feature flag: `{FLAG_NAME}` (default: {true|false}) {if applicable}
- Effort: {X-Y days}

### TR-2: {Requirement Title}
- {Description}
- {Technical details}
- Effort: {X-Y days}

{Add more TR-N sections as needed}

---

## Acceptance Criteria

{Define SMART acceptance criteria that can be verified through testing or manual verification.
Each criterion should be:
- Specific: Exactly what behavior is expected
- Measurable: Can be tested or observed
- Achievable: Realistic given the technical requirements
- Relevant: Directly related to the feature goals
- Testable: Can be verified through automated tests or manual steps

Use checkboxes for tracking completion. Use the format AC-{N}.}

- [ ] AC-1: {Specific, testable criterion}
- [ ] AC-2: {Specific, testable criterion}
- [ ] AC-3: {Specific, testable criterion}
- [ ] AC-4: {All unit tests pass for new/modified code}
- [ ] AC-5: {All integration tests pass}
- [ ] AC-6: {Feature is backward compatible (or breaking changes documented)}

{Add more AC-N items as needed. Aim for 5-10 criteria.}

---

## Files to Create/Modify

{List ALL files that will be created or modified. Use a table format for clarity.
This helps with:
- Scope estimation
- Impact analysis
- Code review planning
- Dependency tracking}

| File | Change |
|------|--------|
| `{path/to/file.ts}` | {CREATE | MODIFY}: {Brief description of change} |
| `{path/to/file.ts}` | {CREATE | MODIFY}: {Brief description of change} |

{Example for Voice Banking:
| File | Change |
|------|--------|
| `app/backend/src/nlu/types.ts` | MODIFY: Add `BalanceInquiryIntent` interface |
| `app/backend/src/nlu/intentClassifier.ts` | CREATE: Intent classification logic |
| `app/backend/src/voice/speechToText.ts` | CREATE: Speech-to-text adapter |
| `app/backend/src/banking/balanceController.ts` | CREATE: Balance inquiry API endpoint |
}

---

## Test Files

{List ALL test files that will be created or modified. Include:
- New test files for new modules
- Existing test files that need updates
- Integration test files
- E2E test files (if applicable)}

| Test File | Coverage |
|-----------|----------|
| `{path/to/test.test.js}` | {What this test covers} |
| `{path/to/test.test.js}` | {What this test covers} |

{Example for Voice Banking:
| Test File | Coverage |
|-----------|----------|
| `app/backend/test/intentClassifier.test.js` | Intent classification for all banking intents |
| `app/backend/test/speechToText.test.js` | Speech-to-text adapter with mock audio |
| `app/backend/test/balanceController.test.js` | Balance API endpoint responses |
| `app/frontend/tests/e2e/voice-balance.spec.ts` | E2E voice balance inquiry flow |
}

---

## Feature Flags

{If this feature uses feature flags, document them here. Feature flags allow:
- Gradual rollout
- A/B testing
- Emergency rollback
- Backward compatibility

If no feature flags are used, write "N/A - No feature flags required"}

| Flag | Default | Description |
|------|---------|-------------|
| `{FLAG_NAME}` | `{true|false}` | {What this flag controls} |

{Example for Voice Banking:
| Flag | Default | Description |
|------|---------|-------------|
| `VOICE_INPUT_ENABLED` | `false` | Enable voice input processing |
| `NLU_CONFIDENCE_THRESHOLD` | `0.85` | Minimum confidence for intent execution |
| `VOICE_CONFIRMATION_REQUIRED` | `true` | Require voice confirmation for sensitive ops |
}

---

## Manual Verification

{Provide step-by-step commands or procedures for manually verifying the feature works.
This should include:
- API curl commands with expected responses
- UI interaction steps
- Voice interaction steps (for voice features)
- Log messages to look for

Make this detailed enough that a QA engineer or another developer can verify the feature.}

```bash
# 1. {Step description}
{command or action}
# Expected: {what you should see}

# 2. {Step description}
{command or action}
# Expected: {what you should see}
```

{Example for Voice Banking:
```bash
# 1. Verify speech-to-text endpoint
curl -X POST -H "Content-Type: audio/wav" \
  --data-binary @test-audio/whats-my-balance.wav \
  http://localhost:8080/api/voice/transcribe
# Expected: { "text": "what's my balance", "confidence": 0.95 }

# 2. Verify intent classification
curl -X POST -H "Content-Type: application/json" \
  -d '{"text": "what is my balance"}' \
  http://localhost:8080/api/nlu/classify
# Expected: { "intent": "balance_inquiry", "confidence": 0.92, "entities": {} }

# 3. Verify balance API
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/accounts/balance
# Expected: { "accounts": [{ "id": "...", "balance": 1234.56, "currency": "EUR" }] }

# 4. Test voice interaction (manual)
# - Click microphone button in UI
# - Say "What's my balance?"
# - Expected: Voice response with account balance
```
}

---

## Dependencies

{List any dependencies this FR has on other FRs or external work.
Format: "Depends on {FR-XX}: {Brief description of dependency}"}

{Example for Voice Banking:
- Depends on FR-00: Requires mock banking database to be initialized
- Depends on FR-01: Requires speech-to-text adapter to be implemented
- External: Requires Web Speech API support in browser
}

{If no dependencies, write: "None - This FR is independent"}

---

## Risks & Mitigations

{Identify potential risks and how to mitigate them. Consider:
- Technical risks (complexity, performance, scalability)
- Integration risks (breaking changes, API compatibility)
- Data risks (data quality, missing data)
- Operational risks (deployment, rollback, monitoring)
- Voice-specific risks (accuracy, latency, noise handling)}

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| {Risk description} | {Low|Medium|High} | {Low|Medium|High} | {How to mitigate} |

{Example for Voice Banking:
| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Speech recognition accuracy below threshold | Medium | High | Implement fallback to text input |
| Voice response latency exceeds 2 seconds | Medium | Medium | Cache common responses, optimize NLU |
| Background noise affects transcription | High | Medium | Add noise filtering, prompt user to retry |
| Browser doesn't support Web Speech API | Low | High | Detect and show text-only fallback UI |
}

{If no significant risks, write: "No significant risks identified"}

---

## Mentor Notes (Design Phase)

- Summarize the rationale behind key decisions.
- Provide at most 1–2 short snippets (≤ ~20 lines each) only if they clarify an approach.
- Call out trade-offs, risks, and next steps succinctly.
- End with an explicit request for `APPROVE_IMPLEMENTATION` if the design is ready.

---

## Notes

{Any additional context, decisions, or considerations that don't fit elsewhere.
This can include:
- Design decisions and rationale
- Alternative approaches considered
- Future enhancements planned
- Known limitations
- Links to external resources}

{Example for Voice Banking:
- Using Web Speech API for development; will switch to cloud service for production.
- Intent classification uses rule-based matching initially; ML model planned for Phase 2.
- Voice confirmation uses simple yes/no; biometric voice auth planned for future.
- Multi-language support deferred to FR-10.
}
