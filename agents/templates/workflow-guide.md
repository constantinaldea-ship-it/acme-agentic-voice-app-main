# AI Agent Documentation Workflow Guide

**Version:** 2.0
**Last Updated:** 2025-12-20
**Audience:** AI Coding Agents (Gemini, Codex, , and others)
**Project:** Voice Banking Assistant

---

## Phase Gate Protocol

- **Default PHASE:** `DESIGN_ONLY`
- **Approval token:** Only switch to `IMPLEMENT_ALLOWED` after the user writes exactly `APPROVE_IMPLEMENTATION`.
- **Forbidden in `DESIGN_ONLY`:** no fenced code blocks, no diffs/patch syntax (`diff --git`, `@@`, `+/-`), and no explicit file-edit instructions.
- **Allowed in `DESIGN_ONLY`:** architecture notes, plans, pseudo-code in plain text, candidate file lists, risks, test strategy, and questions/assumptions.
- **Allowed in `IMPLEMENT_ALLOWED`:** code patches/diffs, tooling steps, and execution details.

### Design Exit Criteria (before requesting approval)
- Objectives, scope, and risks are clearly stated.
- Candidate files/modules and test approach are listed.
- Open questions/blockers are called out.
- Rollback/monitoring considerations are noted when relevant.
- No fenced code blocks or patch syntax present.

### Implementation Exit Criteria
- Patch/diff shared for all touched files.
- Tests/checks listed with status.
- Summary includes rationale, trade-offs, and risks.
- Documentation and tracking updates are identified.
- Outstanding follow-ups (if any) are explicit.

### Output Examples
- **Valid `DESIGN_ONLY` snippet:**
  - "Plan: Update `agents/templates/workflow-guide.md` to add phase gates. Risks: inconsistency across templates. Tests: linting unaffected."
- **Approval request:**
  - "If this design works, reply with `APPROVE_IMPLEMENTATION` to proceed." (still without code fences or patches)
- **Valid `IMPLEMENT_ALLOWED` snippet:**
  - Provide diffs/patches and commands as usual once the token has been granted.

---

## Table of Contents

1. [Overview](#overview)
2. [Documentation Hierarchy](#documentation-hierarchy)
3. [Workflow: Creating a New FR](#workflow-creating-a-new-fr)
4. [Workflow: Implementing an FR](#workflow-implementing-an-fr)
5. [Workflow: Updating Existing FR](#workflow-updating-existing-fr)
6. [Acceptance Criteria Standards](#acceptance-criteria-standards)
7. [Beads Integration](#beads-integration)
8. [Common Scenarios](#common-scenarios)
9. [Quality Checklist](#quality-checklist)
10. [Anti-Patterns](#anti-patterns)

---

## Overview

This guide defines the **mandatory** workflow for AI agents working on the Voice Banking Assistant project. Following this workflow ensures:

- **Consistency:** All documentation follows the same structure
- **Traceability:** Clear links between prompts, specs, implementation plans, and code
- **Completeness:** Nothing is forgotten or overlooked
- **Quality:** High standards for acceptance criteria and testing
- **Collaboration:** Easy handoffs between agents and human developers

**Golden Rule:** Documentation is not optional. It is a first-class deliverable, equal in importance to code.

---

## Documentation Hierarchy

The Voice Banking Assistant project uses a multi-tier documentation hierarchy:

```
1. FR Prompt File (agents/prompts/fr-{number}-*.md)
   ↓ [MANDATORY, CREATED FIRST]
   
2. Product Requirements Brief Entry (docs/business/product-requirements-brief.md)
   ↓ [MANDATORY, ONE LINE PER FR]
   
3. Detailed Specification (docs/specs/FR-{NUMBER}-*.md)
   ↓ [OPTIONAL, FOR COMPLEX FEATURES]
   
4. Implementation Plan (docs/implementation-plan/FR-{NUMBER}-*.md)
   ↓ [MANDATORY, CREATED BEFORE CODING]
   
5. Beads Issues (tracked in .beads/beads.db)
   ↓ [MANDATORY, FOR TRACKING PROGRESS]
   
6. Code + Tests (app/backend/src, app/frontend/src, app/backend/test)
   ↓ [IMPLEMENTATION]
   
7. ADRs (docs/adr/)
   [OPTIONAL, FOR SIGNIFICANT ARCHITECTURAL DECISIONS]
```

**Key Principles:**

1. **Prompt files are mandatory and created first** - They define what to build
2. **Implementation plans are mandatory and created before coding** - They define how to build
3. **Specs are optional** - Only create for complex features requiring detailed technical design
4. **Beads issues track progress** - Link documentation to implementation work
5. **All documents must be kept in sync** - When one changes, update the others

---

## Workflow: Creating a New FR

**When to use:** You are starting work on a new feature request.

### Step 1: Create FR Prompt File

**Template:** `agents/templates/fr-prompt-template.md`
**Location:** `agents/prompts/fr-{number}-{slug}.md`

**Process:**

1. Copy the template:
   ```bash
   cp agents/templates/fr-prompt-template.md agents/prompts/fr-{number}-{slug}.md
   ```

2. Fill in all sections:
   - Replace all `{placeholders}` with actual content
   - Write clear, specific Technical Requirements (TR-1, TR-2, etc.)
   - Write SMART Acceptance Criteria (AC-1, AC-2, etc.)
   - List all files to create/modify
   - List all test files
   - Document feature flags (if any)
   - Provide manual verification steps

3. **Quality Check:**
   - [ ] All placeholders replaced
   - [ ] Technical Requirements are specific and measurable
   - [ ] Acceptance Criteria are testable
   - [ ] Files list is complete
   - [ ] Test files list is complete
   - [ ] Manual verification steps are detailed

4. Commit the prompt file:
   ```bash
   git add agents/prompts/fr-{number}-{slug}.md
   git commit -m "docs: Add FR-{number} prompt file"
   ```

### Step 2: Add PRB Entry

**File:** `docs/business/product-requirements-brief.md`

**Process:**

1. Add a one-line entry to the PRB:
   ```markdown
   - **FR-{NUMBER}:** {Short title} - [Prompt](../agents/prompts/fr-{number}-{slug}.md) | [Spec](./specs/FR-{NUMBER}-{slug}.md) | [Plan](./implementation-plan/FR-{NUMBER}-{slug}.md)
   ```

2. Keep the entry concise (one line)

3. Link to the prompt file (required), spec (if exists), and implementation plan (will be created)

### Step 3: Create Beads Epic

**Command:** `bd create`

**Process:**

1. Create an epic for the FR:
   ```bash
   bd create "FR-{NUMBER}: {Feature Title}" \
     --id fr-{number}-epic \
     -t epic -p {priority} -l "fr-{number}" \
     --description "See agents/prompts/fr-{number}-{slug}.md for details"
   ```

2. Create documentation tasks:
   ```bash
   bd create "Write FR-{NUMBER} implementation plan" \
     --id fr-{number}-doc-plan \
     -t task -p 1 \
     --deps fr-{number}-epic

   bd create "Update PRB with FR-{NUMBER} entry" \
     --id fr-{number}-doc-prb \
     -t task -p 2 \
     --deps fr-{number}-epic
   ```

3. Mark documentation tasks as complete:
   ```bash
   bd close fr-{number}-doc-prb
   ```

### Step 4: Create Detailed Spec (Optional)

**Template:** `agents/templates/spec-template.md`
**Location:** `docs/specs/FR-{NUMBER}-{slug}.md`

**When to create:**
- Feature is complex (multi-file, multiple integration points)
- Feature requires architectural decisions
- Feature has complex business logic or algorithms
- Feature requires detailed API contracts
- Feature requires database schema changes

**When to skip:**
- Feature is simple (single file, straightforward logic)
- FR prompt file provides sufficient detail
- Feature is a bug fix or minor enhancement

**Process:**

1. Copy the template:
   ```bash
   cp agents/templates/spec-template.md docs/specs/FR-{NUMBER}-{slug}.md
   ```

2. Fill in all sections (see template for guidance)

3. Create Beads task:
   ```bash
   bd create "Write FR-{NUMBER} detailed spec" \
     --id fr-{number}-doc-spec \
     -t task -p 1 \
     --deps fr-{number}-epic
   ```

4. Mark task as complete:
   ```bash
   bd close fr-{number}-doc-spec
   ```

### Step 5: Create Implementation Plan

**Template:** `agents/templates/implementation-plan-template.md`
**Location:** `docs/implementation-plan/FR-{NUMBER}-{slug}.md`

**Process:**

1. Copy the template:
   ```bash
   cp agents/templates/implementation-plan-template.md docs/implementation-plan/FR-{NUMBER}-{slug}.md
   ```

2. Fill in all sections:
   - Executive Summary with effort/risk table
   - Prerequisites with verification commands
   - Architecture Overview with diagrams
   - Detailed implementation steps (Step N.N)
   - Testing Strategy
   - Rollback Plan
   - Deployment Plan
   - Success Criteria

3. **Quality Check:**
   - [ ] All placeholders replaced
   - [ ] Prerequisites are verifiable
   - [ ] Architecture diagrams are clear
   - [ ] Implementation steps are detailed
   - [ ] Each step has success criteria
   - [ ] Testing strategy is comprehensive
   - [ ] Rollback plan is actionable
   - [ ] Deployment plan is complete

4. Create Beads tasks for implementation steps:
   ```bash
   bd create "Implement Step 1.1: {Title}" \
     --id fr-{number}-step-1-1 \
     -t task -p 1 \
     --deps fr-{number}-doc-plan

   bd create "Implement Step 1.2: {Title}" \
     --id fr-{number}-step-1-2 \
     -t task -p 1 \
     --deps fr-{number}-step-1-1
   ```

5. Mark documentation task as complete:
   ```bash
   bd close fr-{number}-doc-plan
   ```

---

## Workflow: Implementing an FR

**When to use:** You are ready to write code for an FR.

### Step 1: Verify Prerequisites

**Before writing any code**, verify all prerequisites from the implementation plan:

```bash
# Example: Verify mock database is initialized
curl http://localhost:8080/api/health

# Example: Verify tests pass
cd app/backend && npm test

# Example: Verify speech service is available
curl http://localhost:8080/api/voice/health
```

**If prerequisites are not met:**
- Stop implementation
- Document the blocker in Beads
- Mark the task as BLOCKED
- Work on unblocking tasks first

### Step 2: Claim Work in Beads

```bash
# Find ready work
bd ready --assignee {your-name}

# Claim a task
bd update fr-{number}-step-1-1 --status in_progress --assignee {your-name}
```

### Step 3: Implement the Step

**Follow the implementation plan exactly:**

1. Read the step details in the implementation plan
2. Identify all files to create/modify
3. Use `codebase-retrieval` to understand existing code
4. Make changes according to the plan
5. Write unit tests for new code
6. Run tests to verify changes

**Quality Standards:**

- Follow TypeScript strict mode
- Add type annotations to all exports
- Handle errors gracefully
- Write clear, self-documenting code
- Add comments for complex logic
- Attribute your work (see AGENTS.md)

### Step 4: Test the Implementation

**Unit Tests:**
```bash
cd app/backend && npm test
```

**Manual Verification:**
```bash
# Follow manual verification steps from the implementation plan
```

**Integration Tests (if applicable):**
```bash
# Run E2E tests
cd app/frontend && npx playwright test
```

### Step 5: Update Beads

```bash
# Add progress notes
bd update fr-{number}-step-1-1 --notes "Implemented speech-to-text adapter, tests passing"

# Mark step as complete
bd close fr-{number}-step-1-1
```

### Step 6: Update Documentation

**Update the implementation plan:**

1. Mark the step as COMPLETE in the changelog
2. Add any notes or lessons learned
3. Update success criteria checkboxes

**Update the FR prompt file:**

1. Check off completed acceptance criteria
2. Update status if all criteria are met

### Step 7: Repeat for Next Step

Continue with Step 2 for the next implementation step.

---

## Workflow: Updating Existing FR

**When to use:** You need to modify an existing FR (bug fix, enhancement, scope change).

### Step 1: Identify What Changed

Determine what aspect of the FR changed:
- Requirements changed (update prompt file)
- Implementation approach changed (update implementation plan)
- Technical design changed (update spec)
- New bugs discovered (create Beads issues)

### Step 2: Update Relevant Documents

**If requirements changed:**
1. Update `agents/prompts/fr-{number}-*.md`
2. Update Technical Requirements or Acceptance Criteria
3. Update timestamp and changelog

**If implementation changed:**
1. Update `docs/implementation-plan/FR-{NUMBER}-*.md`
2. Update affected steps
3. Update timestamp and changelog

**If technical design changed:**
1. Update `docs/specs/FR-{NUMBER}-*.md` (if exists)
2. Update affected sections
3. Update timestamp and changelog

### Step 3: Update Cross-References

Ensure all documents are in sync:
- Prompt file links to implementation plan
- Implementation plan links to prompt file and spec
- PRB entry links to all documents
- Beads issues reference updated documents

### Step 4: Update Beads

```bash
# Create new tasks for additional work
bd create "Fix bug in {component}" \
  --id fr-{number}-bug-fix \
  -t bug -p 0 -l "fr-{number},bug" \
  --deps fr-{number}-epic

# Update epic description if needed
bd update fr-{number}-epic --description "Updated: {what changed}"
```

### Step 5: Communicate Changes

Add a changelog entry to all updated documents:

```markdown
| Date | Author | Change |
|------|--------|--------|
| 2025-12-10 |  | Updated Step 1.2 to handle null beta values |
```

---

## Acceptance Criteria Standards

Acceptance Criteria (AC) are the **most critical** part of an FR prompt file. They define what "done" means.

### SMART Criteria

Every acceptance criterion must be:

- **Specific:** Exactly what behavior is expected
- **Measurable:** Can be tested or observed
- **Achievable:** Realistic given the technical requirements
- **Relevant:** Directly related to the feature goals
- **Testable:** Can be verified through automated tests or manual steps

### Good vs Bad Examples

**❌ BAD:**
```markdown
- [ ] AC-1: Feature works correctly
- [ ] AC-2: Code is well-tested
- [ ] AC-3: Performance is good
```

**Why bad:**
- Not specific (what does "works correctly" mean?)
- Not measurable (how do you verify "well-tested"?)
- Not testable (what is "good" performance?)

**✅ GOOD (Voice Banking Examples):**
```markdown
- [ ] AC-1: Voice input "What's my balance?" is transcribed with ≥95% accuracy
- [ ] AC-2: NLU correctly classifies balance inquiry intent with confidence ≥0.85
- [ ] AC-3: API endpoint `/api/accounts/balance` returns 200 OK with account data
- [ ] AC-4: Voice response is generated within 2 seconds end-to-end
- [ ] AC-5: Unit test coverage for intent classifier is ≥90% (lines, branches, functions)
```

**Why good:**
- Specific (exact API endpoint, exact values, exact thresholds)
- Measurable (can check API response, can measure coverage, can measure latency)
- Testable (can write automated tests or manual verification steps)

### Categories of Acceptance Criteria

**Functional Criteria:**
- API endpoints return expected data
- Business logic produces correct results
- UI displays correct information
- Feature flags control behavior correctly

**Non-Functional Criteria:**
- Performance (response time, throughput)
- Reliability (error handling, edge cases)
- Security (authentication, authorization)
- Maintainability (code quality, test coverage)

**Testing Criteria:**
- Unit tests pass
- Integration tests pass
- E2E tests pass
- Manual verification complete

**Documentation Criteria:**
- Implementation plan complete
- API documentation updated
- README updated (if applicable)

### How Many Acceptance Criteria?

**Aim for 5-10 criteria per FR.**

- Too few (<5): Likely missing important aspects
- Too many (>15): Feature is too large, consider breaking it down

---

## Beads Integration

Beads is the AI agent's persistent memory and issue tracking system. It is **mandatory** to use Beads for all FR work.

### When to Create Beads Issues

**Always create:**
- Epic for each FR
- Tasks for each implementation step
- Tasks for documentation work
- Bugs for issues discovered during implementation

**Example structure:**
```
fr-47-epic (epic)
├── fr-47-doc-plan (task) - Write implementation plan
├── fr-47-doc-prb (task) - Update PRB entry
├── fr-47-step-1-1 (task) - Implement Step 1.1
├── fr-47-step-1-2 (task) - Implement Step 1.2
├── fr-47-step-1-3 (task) - Implement Step 1.3
└── fr-47-step-1-4 (task) - Implement Step 1.4
```

### Linking Beads to Documentation

**In Beads:**
```bash
bd update fr-01-epic \
  --description "FR-01: Voice Balance Inquiry

Prompt: agents/prompts/fr-01-voice-balance.md
Implementation Plan: docs/implementation-plan/FR-01-voice-balance.md
PRB Entry: docs/business/product-requirements-brief.md (line 1)"
```

**In Documentation:**
```markdown
## Implementation Tracking

**Beads Epic:** `fr-01-epic`

Check status:
\`\`\`bash
bd show fr-01-epic
bd list -l fr-01
\`\`\`
```

### Beads Workflow Summary

```bash
# 1. Start work session
bd ready --assignee {your-name}

# 2. Claim a task
bd update {task-id} --status in_progress --assignee {your-name}

# 3. Add progress notes
bd update {task-id} --notes "Implemented X, working on Y"

# 4. Complete task
bd close {task-id}

# 5. Check what's next
bd ready --assignee {your-name}
```

---

## Common Scenarios

### Scenario 1: Simple Feature (No Spec Needed)

**Example:** Add a new field to an existing API response

**Workflow:**
1. Create FR prompt file
2. Add PRB entry
3. Create Beads epic + tasks
4. Create implementation plan
5. Implement
6. Test
7. Update documentation
8. Close Beads tasks

**Skip:** Detailed spec (prompt file is sufficient)

### Scenario 2: Complex Feature (Spec Required)

**Example:** Voice intent classification system with NLU engine and conversation management

**Workflow:**
1. Create FR prompt file
2. Add PRB entry
3. Create Beads epic + tasks
4. Create detailed spec
5. Create implementation plan
6. Implement (multiple steps)
7. Test (unit + integration + E2E)
8. Update documentation
9. Close Beads tasks

**Include:** Detailed spec with architecture, API contracts, algorithms

### Scenario 3: Bug Fix

**Example:** Fix low confidence threshold causing false positives in intent classification

**Workflow:**
1. Create Beads bug issue
2. Link to related FR (if applicable)
3. Create mini implementation plan (can be in Beads description)
4. Implement fix
5. Write regression test
6. Update FR documentation (if bug reveals gap in acceptance criteria)
7. Close Beads bug

**Skip:** New FR prompt file (unless bug reveals need for new feature)

### Scenario 4: Multi-Phase Feature

**Example:** FR-01 Voice Balance, FR-02 Voice Transfers, FR-03 Multi-Turn Dialogue

**Workflow:**
1. Create separate FR prompt file for each phase
2. Create separate implementation plan for each phase
3. Create Beads epic for each phase
4. Link phases with dependencies in Beads
5. Implement phases sequentially
6. Update PRB with all phases

**Structure:**
```
agents/prompts/
  fr-01-voice-balance.md
  fr-02-voice-transfers.md
  fr-03-multi-turn-dialogue.md

docs/implementation-plan/
  FR-01-voice-balance.md
  FR-02-voice-transfers.md
  FR-03-multi-turn-dialogue.md
```

---

## Quality Checklist

Use this checklist before considering an FR complete:

### Documentation Quality

- [ ] FR prompt file exists and is complete
- [ ] All placeholders replaced with actual content
- [ ] Technical Requirements are specific and measurable
- [ ] Acceptance Criteria are SMART and testable
- [ ] Files list is complete and accurate
- [ ] Test files list is complete
- [ ] Manual verification steps are detailed
- [ ] PRB entry exists and links to all documents
- [ ] Implementation plan exists and is complete
- [ ] All steps in implementation plan have success criteria
- [ ] Spec exists (if needed) and is complete
- [ ] All documents are in sync (no contradictions)
- [ ] All documents have timestamps and changelogs
- [ ] All documents attribute work to real implementor

### Implementation Quality

- [ ] All acceptance criteria are met
- [ ] All unit tests pass
- [ ] All integration tests pass (if applicable)
- [ ] All E2E tests pass (if applicable)
- [ ] Manual verification complete
- [ ] Code follows project standards (see AGENTS.md)
- [ ] Code is well-commented
- [ ] Error handling is comprehensive
- [ ] Edge cases are handled
- [ ] Performance is acceptable
- [ ] Security considerations addressed
- [ ] Feature flags configured correctly
- [ ] Environment variables documented

### Beads Quality

- [ ] Epic exists for FR
- [ ] Tasks exist for all implementation steps
- [ ] All tasks have clear descriptions
- [ ] All tasks link to documentation
- [ ] All tasks have correct status
- [ ] All completed tasks are closed
- [ ] Dependencies are correctly set
- [ ] Labels are applied consistently

### Handoff Quality

- [ ] Changelog entries added to all documents
- [ ] Known issues documented
- [ ] Future enhancements documented
- [ ] Rollback plan is actionable
- [ ] Deployment plan is complete
- [ ] Monitoring/alerting configured (if applicable)

---

## Anti-Patterns

Avoid these common mistakes:

### ❌ Anti-Pattern 1: Coding Before Planning

**Problem:** Writing code before creating implementation plan

**Why bad:**
- No clear roadmap
- Easy to miss requirements
- Hard to track progress
- Difficult to hand off work

**Solution:** Always create implementation plan before coding

### ❌ Anti-Pattern 2: Vague Acceptance Criteria

**Problem:** "Feature works correctly", "Code is well-tested"

**Why bad:**
- Not testable
- Not measurable
- Leads to incomplete implementations

**Solution:** Write SMART acceptance criteria (see section above)

### ❌ Anti-Pattern 3: Skipping Documentation Updates

**Problem:** Implementing feature but not updating docs

**Why bad:**
- Documentation becomes stale
- Hard for others to understand changes
- Breaks traceability

**Solution:** Update all relevant documents as part of implementation

### ❌ Anti-Pattern 4: Not Using Beads

**Problem:** Tracking work in head or external tools

**Why bad:**
- No persistent memory
- Hard to resume work after break
- No visibility for others

**Solution:** Use Beads for all work tracking

### ❌ Anti-Pattern 5: Monolithic Implementation Plans

**Problem:** Single 2000-line implementation plan with no structure

**Why bad:**
- Hard to navigate
- Hard to track progress
- Overwhelming for implementors

**Solution:** Break into steps, use clear sections, keep each step focused

### ❌ Anti-Pattern 6: Missing Prerequisites

**Problem:** Starting implementation without verifying prerequisites

**Why bad:**
- Implementation fails due to missing dependencies
- Wasted effort
- Frustration

**Solution:** Always verify prerequisites before starting

### ❌ Anti-Pattern 7: No Rollback Plan

**Problem:** Deploying without knowing how to rollback

**Why bad:**
- Can't recover from failures
- Risky deployments
- Downtime

**Solution:** Always document rollback plan before deployment

### ❌ Anti-Pattern 8: Ignoring Test Failures

**Problem:** Proceeding with implementation despite failing tests

**Why bad:**
- Introduces regressions
- Breaks existing functionality
- Technical debt

**Solution:** Fix all test failures before proceeding

### ❌ Anti-Pattern 9: Copy-Paste Documentation

**Problem:** Copying template without customizing

**Why bad:**
- Placeholders left in
- Generic, unhelpful content
- Wastes reader's time

**Solution:** Fully customize all templates with specific content

### ❌ Anti-Pattern 10: No Attribution

**Problem:** Not attributing work to real implementor

**Why bad:**
- Can't track who did what
- Hard to follow up on questions
- Loses context

**Solution:** Always attribute work (, Gemini, Codex, Developer)

---

## Summary

**The Golden Workflow:**

1. **Plan First:** Create FR prompt → PRB entry → Beads epic → Implementation plan
2. **Implement Second:** Verify prerequisites → Claim work → Code → Test → Update docs
3. **Track Always:** Use Beads for all work tracking
4. **Document Everything:** Keep all documents in sync
5. **Test Thoroughly:** Unit + Integration + E2E + Manual
6. **Handoff Cleanly:** Changelog + Known issues + Future work

**Remember:**
- Documentation is not optional
- Acceptance criteria must be SMART
- Beads is mandatory for tracking
- Templates are your friends
- Quality over speed

**When in doubt:**
- Check the templates in `agents/templates/` for guidance
- Ask for clarification before proceeding
- Err on the side of more documentation, not less

---

**End of Workflow Guide**

