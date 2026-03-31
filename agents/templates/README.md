# AI Agent Documentation Templates

This directory contains mandatory templates for AI agents working on the Voice Banking Assistant project.

## Project Overview

The Voice Banking Assistant is a production-ready, voice-driven banking application that enables users to perform banking operations through natural language voice commands. The system operates in a simulated banking environment, demonstrating agentic AI capabilities for retail banking use cases.

**Key Capabilities:**
- Voice input processing with speech-to-text conversion
- Natural language understanding (NLU) for banking intent recognition
- Agentic AI orchestration with multi-step reasoning
- Conversational AI with context retention and multi-turn dialogue
- Mock banking API backend simulating real banking operations
- Voice response generation with text-to-speech

## Templates

### 1. FR Prompt Template
**File:** `fr-prompt-template.md`
**Use for:** Every new feature request
**Purpose:** Define what to build, acceptance criteria, and success metrics

### 2. Implementation Plan Template
**File:** `implementation-plan-template.md`
**Use for:** Every FR before coding begins
**Purpose:** Define how to build, step-by-step implementation guide

### 3. Specification Template
**File:** `spec-template.md`
**Use for:** Complex features requiring detailed technical design (optional)
**Purpose:** Define technical architecture, API contracts, algorithms, data models

### 4. ADR Template
**File:** `adr-template.md`
**Use for:** Significant architectural decisions
**Purpose:** Document and justify technology/architecture choices with alternatives and consequences

### 5. Workflow Guide
**File:** `workflow-guide.md`
**Use for:** Understanding the complete documentation workflow
**Purpose:** Comprehensive guide covering all scenarios and best practices

### 6. Phase Gate Protocol
**File:** `phase-gate-protocol.md`
**Use for:** Understanding the design-first approval workflow
**Purpose:** Ensure proper separation between design and implementation phases

### 7. Agent Instruction Template
**File:** `agent-instruction-template.md`
**Use for:** New or refactored subagent instructions
**Purpose:** Standardize role, scope, tool policy, hand-offs, and safety rules

### 8. Prompt Evaluation Plan Template
**File:** `prompt-evaluation-plan-template.md`
**Use for:** Prompt changes that require golden datasets and LLM-judge evaluation
**Purpose:** Define the evaluation plan, metrics, judge rubrics, and release gates

### 9. Tool Contract Template
**File:** `tool-contract-template.md`
**Use for:** Python tools, thin wrappers, and OpenAPI-backed tool contracts
**Purpose:** Standardize invocation guidance, schemas, safety constraints, and testing expectations

## Quick Start

### Creating a New FR

1. **Copy FR Prompt Template:**
   ```bash
   cp agents/templates/fr-prompt-template.md agents/prompts/fr-{number}-{slug}.md
   ```

2. **Fill in all sections** (replace all `{placeholders}`)

3. **Add PRB entry** in `docs/business/product-requirements-brief.md`

4. **Create Beads epic:**
   ```bash
   bd create "FR-{NUMBER}: {Title}" --id fr-{number}-epic -t epic -p {priority}
   ```

5. **Copy Implementation Plan Template:**
   ```bash
   cp agents/templates/implementation-plan-template.md docs/implementation-plan/FR-{NUMBER}-{slug}.md
   ```

6. **Fill in all sections** (replace all `{placeholders}`)

7. **Create Beads tasks for implementation steps**

8. **Implement, test, document**

## Key Principles

### 1. Documentation is Mandatory
- Documentation is not optional
- It is a first-class deliverable, equal in importance to code
- If it's not documented, it doesn't exist

### 2. Acceptance Criteria Must Be SMART
- **Specific:** Exactly what behavior is expected
- **Measurable:** Can be tested or observed
- **Achievable:** Realistic given the technical requirements
- **Relevant:** Directly related to the feature goals
- **Testable:** Can be verified through automated tests or manual steps

### 3. Use Beads for Tracking
- Create epic for each FR
- Create tasks for each implementation step
- Link Beads issues to documentation
- Update status as work progresses

### 4. Keep Documents in Sync
- When one document changes, update the others
- Maintain cross-references between documents
- Add changelog entries to all updated documents

### 5. Evaluate Prompts Before Release
- Maintain a golden dataset for each critical workflow
- Run deterministic checks before LLM-judge evaluation
- Require a prompt evaluation plan for meaningful instruction changes
- Feed production failures back into the regression dataset

## Voice Banking Examples

### Example FR: Voice Balance Inquiry

**FR Prompt:** `agents/prompts/fr-01-voice-balance-inquiry.md`

**Acceptance Criteria Examples:**
```markdown
- [ ] AC-1: Voice input "What's my balance?" is transcribed with ≥95% accuracy
- [ ] AC-2: NLU correctly classifies balance inquiry intent with confidence ≥0.85
- [ ] AC-3: API endpoint `/api/accounts/balance` returns 200 OK with account data
- [ ] AC-4: Voice response is generated within 2 seconds end-to-end
- [ ] AC-5: Unit test coverage for intent recognition is ≥90%
```

### Example FR: Voice Fund Transfer

**FR Prompt:** `agents/prompts/fr-02-voice-transfer.md`

**Acceptance Criteria Examples:**
```markdown
- [ ] AC-1: Transfer intent is recognized with entities: amount, source, destination
- [ ] AC-2: Voice confirmation is required before executing transfer
- [ ] AC-3: Insufficient funds error is communicated via natural language response
- [ ] AC-4: Transfer API validates source/destination accounts exist
- [ ] AC-5: Audit log captures all transfer attempts with outcomes
```

### Example FR: Multi-Turn Conversation

**FR Prompt:** `agents/prompts/fr-03-multi-turn-dialogue.md`

**Acceptance Criteria Examples:**
```markdown
- [ ] AC-1: Context is maintained across 5+ conversation turns
- [ ] AC-2: Follow-up questions correctly reference previous context
- [ ] AC-3: User can correct previous inputs ("No, I meant savings account")
- [ ] AC-4: Session timeout after 5 minutes of inactivity
- [ ] AC-5: Context reset command ("Start over") clears conversation state
```

## Common Mistakes to Avoid

❌ **Don't:**
- Code before creating implementation plan
- Write vague acceptance criteria ("Feature works correctly")
- Skip documentation updates
- Leave placeholders in final documents
- Start implementation without verifying prerequisites

✅ **Do:**
- Create implementation plan before coding
- Write SMART acceptance criteria with specific, testable conditions
- Update all relevant documents as part of implementation
- Fully customize all templates with specific content
- Verify prerequisites before starting

## Voice Banking Specific Guidelines

### Intent Classification
- Document all supported intents in the NLU specification
- Include confidence thresholds for each intent type
- Define fallback behavior for low-confidence classifications

### Voice Response Design
- Keep responses concise (under 30 seconds of speech)
- Use natural language, avoid technical jargon
- Include confirmation prompts for sensitive operations
- Provide clear error messages with suggested actions

### Security Considerations
- Document authentication requirements for each operation
- Specify which operations require voice confirmation
- Define audit logging requirements
- Mask sensitive data in logs and responses

### Testing Voice Features
- Include sample utterances for each intent
- Test with various accents and speech patterns
- Verify timeout and error handling
- Test multi-turn conversation flows

## Need Help?

1. Read the [Workflow Guide](./workflow-guide.md) for comprehensive guidance
2. Check [AGENTS.md](../../AGENTS.md) for project-specific standards
3. Review [agents/AGENTS.md](../AGENTS.md) for detailed agent specifications
4. Ask for clarification before proceeding

## Enforcement

These standards are **mandatory and non-negotiable**:
- Code reviews will reject PRs with incomplete or low-quality documentation
- AI agents that consistently violate these standards will be flagged
- Documentation quality is as important as code quality

**When in doubt, err on the side of more documentation, not less.**
