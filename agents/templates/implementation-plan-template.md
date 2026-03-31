# FR-{NUMBER}: {Feature Title}

> PHASE: DESIGN_ONLY  
> APPROVAL TOKEN: `APPROVE_IMPLEMENTATION`

**FR Number:** FR-{NUMBER}
**Title:** {Full descriptive title}
**Author:** { | Gemini | Codex | Developer Name}
**Created:** {YYYY-MM-DD}
**Status:** {NOT_STARTED | IN_PROGRESS | COMPLETED | BLOCKED}
**Implementation Status:** {Step-level summary, e.g. Step 1 implemented; Step 2 in progress; Step 3 pending}
**Estimated Effort:** {X-Y days}
**Estimated Credits:** {X-Yk tokens expected for this plan/work}
**Related Documents:**
- [FR-{NUMBER} Prompt](../../agents/prompts/fr-{number}-{slug}.md)
- [FR-{NUMBER} Specification](../../docs/specs/FR-{NUMBER}-{slug}.md) {if applicable}
- [Related FR-XX](./FR-XX-{slug}.md) {if applicable}

**Forbidden in this phase (DESIGN_ONLY):** Do not include fenced code blocks, diffs/patch syntax, or explicit file-edit instructions. Keep responses focused on plan, sequencing, risks, and validation strategy.

**Approval request:** Once the plan is ready, ask the reviewer to respond with `APPROVE_IMPLEMENTATION` before sharing implementation patches or commands.

---

## Summary at a Glance

{Add a reviewer-friendly quick scan section that can be understood in under a minute. Keep each row short, concrete, and decision-oriented.}

| Item | Summary |
|------|---------|
| Goal | {One-sentence objective of this plan} |
| Primary Outcome | {What will exist once this plan is implemented} |
| Systems / Repos | {Main repositories, services, or modules touched} |
| Dependencies | {Required upstream systems, documents, approvals, or prior FRs} |
| Implementation Status | {Current step-level implementation status summary. This row is mandatory and must stay current as work progresses.} |
| Key Decisions | {Top architectural choices or boundary decisions} |
| Main Risks | {Top 2-3 risks, unknowns, or constraints} |
| Out of Scope | {Most important exclusions for this phase} |
| Review Focus | {What reviewers should validate first} |

## Executive Summary

{1-2 paragraph summary of what this implementation plan covers. Include:
- High-level approach
- Key components to be built
- Major integration points
- Overall effort and risk assessment}

{If the feature has multiple steps/phases, include a summary table:}

| Step | Description | Effort | Risk |
|------|-------------|--------|------|
| {N.N} | {Brief description} | {X days} | {LOW|MEDIUM|HIGH} |
| {N.N} | {Brief description} | {X days} | {LOW|MEDIUM|HIGH} |

{Example for Voice Banking:
| Step | Description | Effort | Risk |
|------|-------------|--------|------|
| 1.1 | Implement speech-to-text adapter | 1 day | LOW |
| 1.2 | Implement intent classifier | 2 days | MEDIUM |
| 1.3 | Implement balance API endpoint | 1 day | LOW |
| 1.4 | Implement text-to-speech response | 1 day | LOW |
| 1.5 | Integrate voice UI components | 2 days | MEDIUM |
}

---

## Prerequisites

{List everything that must be in place BEFORE starting implementation. Include:
- Dependent FRs that must be complete
- Infrastructure requirements (databases, APIs, services)
- Configuration requirements (env vars, feature flags)
- Test data or fixtures needed
- Documentation that must be reviewed}

Before starting this implementation, verify:

1. **{Prerequisite Category}:** {Description}
   ```bash
   # Verification command
   {command to verify prerequisite}
   # Expected output: {what you should see}
   ```

2. **{Prerequisite Category}:** {Description}
   ```bash
   # Verification command
   {command to verify prerequisite}
   # Expected output: {what you should see}
   ```

{Example for Voice Banking:
1. **Mock Database Initialized:** Account and transaction data available
   ```bash
   curl http://localhost:8080/api/health
   # Expected: { "status": "ok", "database": "connected" }
   ```

2. **Tests Pass:** All existing tests must pass
   ```bash
   cd app/backend && npm test
   # Expected: All tests pass, no errors
   ```

3. **Browser Support:** Web Speech API available
   ```bash
   # Manual check in browser console:
   # 'webkitSpeechRecognition' in window || 'SpeechRecognition' in window
   # Expected: true
   ```
}

---

## Architecture Overview

{Provide a high-level view of how this feature fits into the existing system.
Include:
- Current architecture (before this FR)
- Target architecture (after this FR)
- Data flow diagrams
- Component interactions
- Integration points}

### Current Architecture
```
{ASCII diagram or description of current system}
```

### Target Architecture
```
{ASCII diagram or description of target system after this FR}
```

{Example for Voice Banking:
### Current Flow
```
User → Text Input → API → Response → Text Display
```

### Target Flow (Voice Banking)
```
User → Microphone → Speech-to-Text → NLU Intent Classifier → Banking API → Response Generator → Text-to-Speech → Speaker
                         ↓                    ↓                    ↓
                   Transcription          Intent +            Account Data
                      Text               Entities
```

### Component Diagram
```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                          │
├─────────────────────────────────────────────────────────────────┤
│  VoiceInput  │  ConversationUI  │  VoiceOutput  │  FallbackText │
└──────┬───────┴────────┬─────────┴───────┬───────┴───────────────┘
       │                │                 │
       ▼                ▼                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Backend (Express)                         │
├─────────────────────────────────────────────────────────────────┤
│  SpeechAdapter  │  NLUService  │  BankingService  │  TTSAdapter │
└─────────────────┴──────────────┴──────────────────┴─────────────┘
```
}

---

## Implementation Steps

{Break down the implementation into discrete, sequential steps. Each step should:
- Be completable in 0.5-2 days
- Have clear inputs and outputs
- Be testable independently
- Build on previous steps

Use the format "Step N.N: {Title}" for each step.}

Mandatory requirements for this section:
- Start `Implementation Steps` with a reviewer-friendly summary table using the columns `Step`, `Short Summary`, and `Status`.
- Keep every step `Status` current as work progresses.
- Every step-level `Success Criteria` block must be a checkbox checklist, not plain bullets.
- A success-criteria checkbox may be marked complete only after verification by tests, manual validation, document review, or other explicit evidence.

These requirements are mandatory for every implementation plan that uses this template. Reviewers must be able to scan step progress from the summary table and must be able to trust that every checked success criterion has already been verified.

| Step | Short Summary | Status |
|------|---------------|--------|
| Step {N.N} | {One-line description of what the step implements} | {Not implemented | Partially implemented | Implemented | Blocked} |
| Step {N.N} | {One-line description of what the step implements} | {Not implemented | Partially implemented | Implemented | Blocked} |

## Step {N.N}: {Step Title}

**Objective:** {1-2 sentence description of what this step accomplishes}
**Effort:** {X-Y days}
**Risk:** {LOW | MEDIUM | HIGH}

### Files to Modify

{List ALL files that will be created or modified in this step. Use a table format.}

| File | Change |
|------|--------|
| `{path/to/file.ts}` | {Brief description of change} |
| `{path/to/file.ts}` | {Brief description of change} |

### Type Changes

{If this step involves TypeScript type changes, document them here with before/after examples.}

**File:** `{path/to/file.ts}`

{Describe the change, then show code examples:}

```typescript
// BEFORE (line X-Y):
{existing code}

// AFTER:
{new code}
```

### Implementation Details

{Provide detailed implementation guidance. Include:
- Function signatures
- Algorithm descriptions
- Business logic
- Error handling
- Edge cases to consider}

**File:** `{path/to/file.ts}`

{Describe what to implement, then show code examples:}

```typescript
{code example with comments explaining key decisions}
```

### Testing

{Describe how to test this step. Include:
- Unit tests to write
- Integration tests to write
- Manual verification steps
- Expected test coverage}

**Unit Tests:**
- Test case 1: {Description}
- Test case 2: {Description}

**Manual Verification:**
```bash
# {Step description}
{command}
# Expected: {output}
```

### Success Criteria

{Define what "done" means for this step. Be specific and measurable. This checklist is mandatory. Check an item only after it has been verified.}

- [ ] {Criterion 1}
- [ ] {Criterion 2}
- [ ] {All tests pass}
- [ ] {Manual verification complete}

---

{Repeat "Step N.N" section for each implementation step}

---

## Testing Strategy

{Describe the overall testing approach for this FR. Include:
- Unit testing strategy
- Integration testing strategy
- E2E testing strategy (if applicable)
- Performance testing (if applicable)
- Voice-specific testing (for voice features)
- Manual testing procedures}

### Unit Tests

{List all unit test files and what they cover}

| Test File | Coverage |
|-----------|----------|
| `{path/to/test.test.js}` | {What this test covers} |

### Integration Tests

{Describe integration test scenarios}

1. **{Scenario Name}:** {Description}
   - Setup: {What needs to be configured}
   - Action: {What to do}
   - Expected: {What should happen}

### Voice Testing

{For voice features, describe voice-specific testing:}

1. **Transcription Accuracy:**
   - Test with sample audio files
   - Verify word error rate < 5%
   - Test with various accents/speeds

2. **Intent Classification:**
   - Test all supported intents
   - Verify confidence thresholds
   - Test edge cases and ambiguous inputs

3. **End-to-End Voice Flow:**
   - Test complete voice interaction
   - Measure total latency
   - Verify natural language responses

### Manual Testing

{Provide step-by-step manual testing procedures}

```bash
# Test Case 1: {Description}
{commands or actions}
# Expected: {what you should see}

# Test Case 2: {Description}
{commands or actions}
# Expected: {what you should see}
```

---

## Rollback Plan

{Describe how to rollback this feature if something goes wrong. Include:
- Feature flags to disable
- Environment variables to change
- Code changes to revert
- Database migrations to rollback (if applicable)
- Monitoring to watch during rollback}

### Emergency Rollback

{Quick steps to disable the feature immediately}

1. {Step 1}
2. {Step 2}
3. {Verify rollback successful}

{Example for Voice Banking:
1. Set `VOICE_INPUT_ENABLED=false` in environment
2. Restart backend service
3. Verify voice UI is hidden and text input is shown
}

### Full Rollback

{Steps to completely remove the feature}

1. {Step 1}
2. {Step 2}
3. {Verify rollback successful}

---

## Deployment Plan

{Describe how to deploy this feature. Include:
- Deployment order (backend first, then frontend, etc.)
- Environment variables to set
- Feature flags to enable
- Database migrations to run (if applicable)
- Monitoring to watch during deployment}

### Pre-Deployment Checklist

- [ ] {Item 1}
- [ ] {Item 2}
- [ ] {All tests pass in CI}

### Deployment Steps

1. **{Environment}:** {Description}
   ```bash
   {commands}
   ```

2. **{Environment}:** {Description}
   ```bash
   {commands}
   ```

{Example for Voice Banking:
1. **Backend Deployment:**
   ```bash
   # Set environment variables
   export VOICE_INPUT_ENABLED=true
   export NLU_CONFIDENCE_THRESHOLD=0.85
   
   # Deploy to Render
   git push origin main
   ```

2. **Frontend Deployment:**
   ```bash
   # Deploy to Vercel
   vercel --prod
   ```

3. **Feature Flag Rollout:**
   ```bash
   # Enable for 10% of users initially
   # Monitor error rates and latency
   # Gradually increase to 100%
   ```
}

### Post-Deployment Verification

```bash
# {Verification step}
{command}
# Expected: {output}
```

---

## Success Criteria

{Define what "done" means for the entire FR. These should map to the Acceptance Criteria in the FR prompt. This checklist is mandatory and each item must remain unchecked until verified.}

This section is mandatory. Each criterion must be explicitly verified before it is checked, and the implementation is not complete until the applicable criteria have evidence behind them.

- [ ] AC-1: {Criterion from FR prompt}
- [ ] AC-2: {Criterion from FR prompt}
- [ ] {All unit tests pass}
- [ ] {All integration tests pass}
- [ ] {Manual verification complete}
- [ ] {Documentation updated}
- [ ] {Deployed to production}

---

## Known Issues & Limitations

{Document any known issues, limitations, or technical debt introduced by this implementation.}

- {Issue 1}: {Description and potential impact}
- {Issue 2}: {Description and potential impact}

{Example for Voice Banking:
- Speech recognition accuracy varies by browser (Chrome best, Firefox limited)
- Background noise can affect transcription quality
- Multi-language support not included in this phase
}

{If none, write: "None identified"}

---

## Future Enhancements

{Document potential future improvements or follow-on work.}

- {Enhancement 1}: {Description}
- {Enhancement 2}: {Description}

{Example for Voice Banking:
- Add ML-based intent classification for improved accuracy
- Implement voice biometric authentication
- Add multi-language support (Spanish, French, German)
- Implement proactive suggestions based on user patterns
}

{If none, write: "None planned"}

---

## Mentor Notes (Design Phase)

- Explain key decisions and alternatives considered.
- Provide at most 1–2 concise snippets (≤ ~20 lines each) only when they clarify the plan.
- Summarize trade-offs, risks, and next steps.
- End with a prompt for `APPROVE_IMPLEMENTATION` before moving to execution.

---

## Changelog

{Track major changes to this implementation plan. Use ISO dates and attribute to the real implementor.}

| Date | Author | Change |
|------|--------|--------|
| {YYYY-MM-DD} | {Implementor} | {Description of change} |

{Example:
| Date | Author | Change |
|------|--------|--------|
| 2025-12-20 |  | Initial implementation plan created |
| 2025-12-21 | Gemini | Updated Step 1.2 with confidence threshold handling |
| 2025-12-22 |  | Completed implementation, marked all steps DONE |
}
