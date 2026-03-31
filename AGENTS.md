---
type: "always_apply"
---

# Agent Specification for Voice Banking App

**Version:** 3.0  
**Last Updated:** 2026-01-17  
**Application:** Voice-Driven Banking Assistant with Agentic AI Capabilities

---

## 1) Overview

The Voice Banking App is a production-ready, voice-driven banking application that enables users to perform banking operations through natural language voice commands. The system operates in a simulated banking environment, providing a comprehensive demonstration of agentic AI capabilities for retail banking use cases.

**Primary capabilities:**
- Voice input processing with speech-to-text conversion
- Natural language understanding (NLU) for banking intent recognition
- Agentic AI orchestration with multi-step reasoning and decision-making
- Conversational AI interface with context retention and multi-turn dialogue
- Mock banking API backend with RESTful endpoints simulating real banking operations
- Voice response generation with text-to-speech capabilities

**Target users:** Retail banking customers seeking hands-free, voice-driven access to their accounts for balance inquiries, transfers, payments, and transaction history.

**Supported Banking Operations:**
- Account balance queries (multi-account handling)
- Fund transfers between accounts (with validation)
- Bill payment processing (with payee management)
- Transaction history retrieval (with filtering and search)
- Account statement generation

---

## 2) Repository Structure & Focus

> ⚠️ **CRITICAL: Primary Implementation is Java**

### Directory Guidance

| Directory | Purpose | AI Agent Behavior |
|-----------|---------|-------------------|
| **`java/`** | Primary codebase — Spring Boot implementation | ✅ **FOCUS HERE** for all implementation work |
| **`docs/`** | Architecture reference (Arc42, interfaces, PRD) — served via Docsify | ✅ Use for architectural decisions and context |
| **`agents/`** | AI agent instructions, templates, prompts | ✅ Follow for workflow and documentation standards |
| **`ai-account-balance-ts/`** | Archived TypeScript MVP (includes TypeScript-specific ADRs) | ⛔ **DO NOT** follow documentation or implement here |

### Serving Documentation

The `docs/` directory is configured with Docsify for easy HTML viewing:

```bash
./docs/serve-docs.sh   # Serves at http://localhost:3000
```

### What to Ignore

**DO NOT** read or follow documentation in `ai-account-balance-ts/docs/` — these are historical artifacts from the TypeScript prototype, including TypeScript-specific ADRs.

**DO NOT** implement features in `ai-account-balance-ts/` — the TypeScript MVP is preserved only for reference (especially the frontend voice UI patterns).

### Reference Value of TypeScript MVP

The `ai-account-balance-ts/` directory is preserved because:
- `app/frontend/` demonstrates voice UI patterns (Web Speech API, consent flow)
- `packages/shared/` shows Zod schema patterns that can inform Java DTOs
- Backend orchestration concepts are documented but **Java is the target implementation**

---

## 3) Agent Role & Rules

- **You may:** fix bugs, implement clearly scoped features, refactor for clarity, add/upgrade tests, improve typings, and update documentation.
- **Ask first before:** adding/upgrading dependencies, changing API contracts or schemas, altering core NLU/intent logic or confidence thresholds, large documentation rewrites, CI/CD changes, deployments, or committing/pushing code.
- **Core responsibilities:** keep code typed and readable, maintain API consistency and useful errors, optimize for voice response latency, and attribute work as the real implementor (****, **Gemini**, **Developer**, or **Codex**).
- **Task doc hygiene:** when touching FR docs, update Status, tick acceptance criteria met, and add a brief changelog line.
- **Post-task note:** include quick lessons/next-step guidance when handing off work.

---

## 4) Phase Gate & Approval Protocol

**Default PHASE:** `DESIGN_ONLY`

- **Transition rule:** Switch to `IMPLEMENT_ALLOWED` after the user gives an explicit implementation approval signal. Accept the canonical token `APPROVE_IMPLEMENTATION` and equivalent approval-semantic wording such as `approved`, `approve`, `go ahead`, `proceed`, `implement`, or similarly clear intent to begin implementation.
- **Design-only contract:** In `DESIGN_ONLY`, responses must exclude fenced code blocks, diffs/patch syntax, and file-edit instructions. Focus on architecture, plans, risks, and tests without executable patches.
- **Implementation guardrails:** Only produce patches/diffs/tooling steps once in `IMPLEMENT_ALLOWED`.
- **Mentor mode:** Briefly explain rationale, share only short helpful snippets (≤ ~20 lines) when necessary, and provide a concise summary of decisions/trade-offs/risks at the end.
- **User override:** Allow the user to bypass the phase gate by explicitly writing `OVERRIDE_PHASE_GATE` and stating their intent. This framework should not be applied for architecture decisions especially ADR, High Level Design, or other high-level decisions, governance and implementation plans.

---

## 5) Codebase Retrieval (MANDATORY)

**This is enforced for all AI agents.** When working in this repo, agents MUST use semantic code retrieval tools for code discovery before making changes or explaining how code works.

### Tool Preference

- **Copilot ():** Use `mcp_augmentcode_codebase-retrieval` as the **PRIMARY** tool for semantic code search.
- **Gemini/Codex:** Use `mcp_augmentcode_codebase-retrieval` as the **PRIMARY** tool for semantic code search.

### Required Behavior (No Exceptions)

- Before **any** implementation, refactor, or non-trivial edit: run codebase retrieval to identify the exact files/symbols involved (routes, controllers, services, schemas, tests, docs).
- Before **any** "how does X work?" explanation: run codebase retrieval to locate the canonical entrypoints and supporting modules, then base the explanation on those files.

### Allowed Exceptions (Narrow)

- Single-file, trivial edits where the user explicitly provided the exact file and code context in the prompt.
- Exact-string lookups (error messages, env var names, literal URLs) may use `rg` first, but still follow with codebase retrieval before concluding architecture/flow changes.

### Tool Choice Guidance

- Use codebase retrieval for semantic discovery ("find auth flow", "where is X handled").
- Use `rg` only for exhaustive exact-match checks ("find all references to `authenticate(`").

### Standard Retrieval Prompt (Copy/Paste)

Ask for *everything you'll touch* in one shot:

> "Find all files/symbols involved in {TASK}. Include entrypoints, routes, middleware, data access, config/env, tests, and docs. Return file paths and key symbol names."

### Retrieval Guidance (Efficiency-First)

- **Default focus:** prioritize code, configuration, and tests.
- **Docs are opt-in:** fetch documentation only when explicitly requested or when blockers arise from missing constraints. Start with a lightweight entrypoint (e.g., `docs/INDEX.md` or `docs/README.md`) before deeper reads.
- **Avoid heavy artifacts:** do not ingest large binaries/PDFs or other "cold storage" assets unless explicitly required.

---

## 6) Coding Standards

- **Java (Primary):** Follow Spring Boot conventions; use proper package structure; prefer constructor injection for DI.
- **TypeScript (Reference Only):** strict mode; ES2020/ESNext modules; `esModuleInterop` + `skipLibCheck`; keep `.js` extensions on relative imports in ESM.
- **Naming & layout:** backend dirs lowercase nouns; backend files camelCase; frontend components PascalCase; tests alongside source or in dedicated test directories.
- **Patterns:** adapter/factory for external services (speech-to-text, text-to-speech, NLU); clear separation of routes → controllers → services → models → repositories → adapters.
- **Style:** explicit return types on exports; handle errors with informative messages/status codes; prefer async/await with rejection handling.
- **Attribution:** new files include a brief header with author/date; substantial edits add a short `// Modified by [Implementor] on YYYY-MM-DD` note near the change.
- **Security:** avoid printing secrets or PII; confirm before introducing new secret handling; prefer environment variables or secret stores; mask account numbers in logs.

---

## 7) Testing & Validation

- **Stack:** Java tests use JUnit 5 + Mockito; TypeScript backend tests use Node `assert`.
- **Expectation:** add/extend tests for new or changed logic; prioritize core business logic (intent recognition, transaction validation) and controllers; mock external services (speech APIs, banking APIs) to avoid live calls.
- **Commands:** 
  - Java: `cd java/voice-banking-app && mvn test`
  - TypeScript: `cd ai-account-balance-ts/app/backend && npm test`
- **Voice testing:** include test audio files for common utterances; verify transcription accuracy against expected text; test intent classification with confidence thresholds.

---

## 8) Voice Processing Pipeline

### Speech-to-Text Integration
- **Primary adapter:** Web Speech API for development; cloud service (Google Cloud Speech, AWS Transcribe, or Azure Speech) for production.
- **Accuracy target:** ≥95% word accuracy for common banking phrases.
- **Latency target:** <500ms for transcription of typical utterances (5-10 words).
- **Error handling:** graceful degradation when speech service unavailable; retry with exponential backoff.

### Natural Language Understanding (NLU)
- **Intent recognition:** classify user utterances into banking intents (balance_inquiry, transfer_funds, pay_bill, transaction_history, account_statement).
- **Entity extraction:** extract amounts, account identifiers, dates, payee names from utterances.
- **Confidence thresholds:** require ≥0.85 confidence for action execution; prompt for clarification below threshold.
- **Context management:** maintain conversation state for multi-turn dialogues; support follow-up questions and corrections.

### Text-to-Speech Integration
- **Primary adapter:** Web Speech API for development; cloud service for production.
- **Voice selection:** natural-sounding voice appropriate for banking context.
- **Response formatting:** convert structured data to natural language responses.

---

## 9) Banking Operations

### Account Balance Queries
- Support multiple account types (checking, savings, credit card).
- Return current balance, available balance, and last updated timestamp.
- Handle multi-account queries ("What are all my balances?").

### Fund Transfers
- Validate source and destination accounts.
- Verify sufficient funds before transfer.
- Require voice confirmation for amounts above threshold.
- Support scheduled and immediate transfers.

### Bill Payments
- Manage payee list (add, edit, remove payees).
- Validate payment amounts and dates.
- Require confirmation for new payees.
- Support recurring payment setup.

### Transaction History
- Filter by date range, amount range, transaction type.
- Search by merchant name or description.
- Paginate results for large histories.
- Support voice navigation ("Show me more", "Go back").

### Account Statements
- Generate statements for specified date ranges.
- Format for voice readout (summary) or download (detailed).
- Include running balance calculations.

---

## 10) Security & Validation

### Intent Confirmation Workflows
- Require explicit voice confirmation for sensitive operations (transfers, payments).
- Use challenge-response for high-value transactions.
- Implement timeout for confirmation prompts.

### Transaction Validation
- Validate amounts against account limits.
- Verify recipient account existence.
- Check for duplicate transactions.
- Enforce daily/weekly transfer limits.

### Simulated Authentication
- Voice-based authentication simulation (PIN, passphrase).
- Session management with timeout.
- Re-authentication for sensitive operations.

### Audit Logging
- Log all banking actions with timestamps.
- Include user identifier, action type, and outcome.
- Mask sensitive data (full account numbers, amounts in logs).

### Error Handling
- Provide natural language explanations for errors.
- Suggest corrective actions when possible.
- Graceful fallback for unrecognized intents.

---

## 11) Beads (`bd`) Issue Tracking & Agent Memory System

### Overview

**Beads** is the AI agent's persistent memory and issue tracking system for this repository.

**Key Benefits:**
- **Persistent Memory:** Agents can store and retrieve task context across sessions
- **Dependency Management:** Track blockers and dependencies between tasks
- **Work Prioritization:** Identify ready-to-work tasks with `bd ready`
- **Traceability:** Link Beads issues to FR documentation and implementation plans

**Database Location:** `.beads/beads.db` (SQLite database, git-ignored)

### Core Commands Reference

| Command | Purpose | Example |
|---------|---------|---------|
| `bd init` | Initialize Beads database for this repo | `bd init` |
| `bd create` | Create new issue (epic, feature, task, bug, chore) | `bd create "Implement balance intent" -t feature -p 1` |
| `bd list` | List issues with optional filters | `bd list --status open --priority 0` |
| `bd show <id>` | Show detailed information about an issue | `bd show vb-feature-balance-intent` |
| `bd ready` | List work that has no open blockers | `bd ready --assignee augment-agent` |
| `bd dep add A B` | Add dependency: A depends on B | `bd dep add task-1 task-2` |
| `bd update <id>` | Update status, priority, assignee, etc. | `bd update task-1 --status in_progress` |
| `bd close <id>` | Close/complete an issue | `bd close task-1` |
| `bd doctor` | Health check for Beads setup | `bd doctor` |

### Common Patterns

```bash
# Create with custom ID and labels
bd create "Implement voice intent recognition" \
  --id vb-feature-balance-intent \
  -t feature -p 1 -l "voice,nlu,banking"

# Check ready work before starting
bd ready --limit 5

# Claim a task
bd update task-id --assignee augment-agent --status in_progress

# Close when done
bd close task-id
```

### Token Efficiency

> **⚡ CRITICAL RULE:** Always query Beads BEFORE reading large files (implementation plans, specs, prompt files). A single `bd show` query can save 98% of tokens compared to reading a 1,800-line implementation plan.

---

## 12) Documentation Standards & Templates

**Documentation is a mandatory first-class deliverable.** All new features (FRs) must follow this standardization.

### Template Library

Use the following templates located in `agents/templates/`:

| Template File | Purpose | When to Use |
|---------------|---------|-------------|
| `fr-prompt-template.md` | Feature definition "contract" | **MANDATORY** for every new FR. Created FIRST. |
| `implementation-plan-template.md` | Execution roadmap | **MANDATORY** before any coding begins. |
| `spec-template.md` | Detailed technical design | **OPTIONAL**. Use for complex features. |
| `workflow-guide.md` | Comprehensive SOP | Reference manual for this entire workflow. |

### Mandatory Workflow

```
1. Create FR Prompt File (agents/prompts/fr-{number}-*.md)
   ↓ [Use fr-prompt-template.md]

2. Add PRB Entry (docs/business/product-requirements-brief.md)
   ↓ [One line per FR with links]

3. Create Beads Epic + Tasks
   ↓ [Track all work in Beads]

4. Create Implementation Plan (docs/implementation-plan/FR-{NUMBER}-*.md)
   ↓ [Use implementation-plan-template.md]

5. Implement + Test + Document
   ↓ [Follow implementation plan steps]

6. Update All Documents
   ↓ [Keep everything in sync]
```

### Acceptance Criteria Standards

Acceptance Criteria must be **SMART**: Specific, Measurable, Achievable, Relevant, Testable.

**❌ BAD:** "Feature works and is tested."

**✅ GOOD:** "API endpoint `/api/accounts/balance` returns 200 OK with JSON schema X; Voice response latency < 2s; Unit test coverage > 90%."

### Documentation Quality Checklist

- [ ] All placeholders replaced with actual content
- [ ] Technical Requirements are specific and measurable
- [ ] Acceptance Criteria are SMART and testable
- [ ] Files list is complete and accurate
- [ ] Test files list is complete
- [ ] Manual verification steps are detailed
- [ ] Beads epic and tasks exist and link to documentation

### Anti-Patterns to Avoid

- **Coding before Planning:** Never write code without an `implementation-plan`.
- **Vague ACs:** Never accept "works correctly" as a criterion.
- **Ignoring Beads:** Always verify context with `bd ready` / `bd show` before asking "what's next?".

---

## 13) E2E Testing & Integration Validation

- **Always run E2E tests before declaring feature completion** - Tests are only valuable if they're executed and verified to pass.
- **Verify API responses match types** - The contract between frontend and backend must be validated through actual integration testing, not just type checking.
- **Voice interaction testing:**
  - Test with sample audio files for common utterances.
  - Verify intent classification accuracy.
  - Test multi-turn conversation flows.
  - Validate error handling for unrecognized speech.
- **Common E2E failure patterns:**
  - Missing properties in API responses (controller returns partial data)
  - Null/undefined checks missing in frontend
  - Speech recognition timing issues
  - Race conditions in async voice processing

---

## 14) Deployment & CI/CD

- GitHub Actions handle build and test; merges require passing tests.
- Document any env var changes before applying.
- For new infrastructure decisions, add an ADR and update relevant docs/README files.
- Feature flags control rollout of new voice capabilities.

---

## 15) Landing the Plane (Session Completion)

**When ending a work session**, you MUST complete ALL steps below. Work is NOT complete until `git push` succeeds.

**MANDATORY WORKFLOW:**

1. **File issues for remaining work** - Create issues for anything that needs follow-up
2. **Run quality gates** (if code changed) - Tests, linters, builds
3. **Update issue status** - Close finished work, update in-progress items
4. **STAGE CHANGES FOR REVIEW** - This is MANDATORY:
   ```bash
   git add -A
   git status  # Show user what will be committed
   ```
5. **WAIT FOR USER APPROVAL** - **NEVER commit or push without explicit user approval**
6. **Clean up** - Clear stashes, prune remote branches
7. **Hand off** - Provide context for next session

**CRITICAL RULES:**
- **NEVER `git commit` or `git push` without explicit user approval** - Always stage and show changes first
- Work is NOT complete until user reviews and approves the commit
- NEVER auto-commit sensitive refactors (anonymization, security changes, large renames)
- If in doubt, ask: "Ready to review and commit these changes?"

---

## 16) Sensitive Content & Anonymization

**Before pushing to any public or shared repository**, ensure the codebase is sanitized:

### Must Be Anonymized

| Category | Original | Replacement |
|----------|----------|-------------|
| Company Name | Acme Bank | Acme Bank |
| Division Names | Private Bank | Premium Banking |
| BIC/SWIFT Codes | ACMEDEXX, ACMEDEFF | ACMEDEXX, ACMEDEFF |
| Email Domains | @db.com | @acmebank.example |
| Internal Domains | *.db.com | *.acme.example |
| Phone Numbers | Real numbers | +49 800 123-4567 |
| Employee Names | Real names | Remove or anonymize |
| Presentation References | "Scope document", "Kickoff" | "Scope document" |

### Must Be Git-Ignored

- `docs/stakeholders/` - Employee PII
- `docs/business/ai-banking-voice-kickoff.md` - Confidential
- `docs/business/voice-banking-deck.md` - Confidential
- `.beads/` - Local state
- `*.pdf`, `*.pptx`, `*.docx` - Binary documents

### Pre-Push Checklist

```bash
# Verify no sensitive content
grep -r "Acme Bank" --include="*.md" --include="*.java" | wc -l  # Should be 0
grep -r "@db.com" --include="*.md" --include="*.java" | wc -l        # Should be 0
git check-ignore docs/stakeholders/test.md                           # Should match
```
