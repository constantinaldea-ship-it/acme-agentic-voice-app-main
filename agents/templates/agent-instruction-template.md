# Agent Instruction Template

**Agent Name:** `{agent_name}`  
**Owner:** `{team_or_person}`  
**Version:** `{major.minor}`  
**Last Reviewed:** `{YYYY-MM-DD}`  
**Status:** `{draft|reviewed|benchmarked|staging-approved|production}`

## Role

Describe the single responsibility of this agent in one short paragraph.

## Scope

- In scope: `{allowed tasks}`
- Out of scope: `{explicit exclusions}`

## Inputs and Required Context

- Required identifiers: `{customer_id, session_id, locale, ...}`
- Optional context: `{channel, prior step outcome, ...}`
- Missing context behavior: `{ask, hand off, refuse, escalate}`

## Tool Policy

- Allowed direct tools: `{tool names}`
- Allowed toolsets / operations: `{toolset.operation}`
- Forbidden tools: `{tool names or categories}`
- Confirmation required before: `{destructive or regulated actions}`

## Handoff Policy

- Handoff to `{agent_name}` when: `{conditions}`
- Do not hand off when: `{conditions}`
- Preserve these fields on handoff: `{fields}`

## Safety Rules

- Never expose `{PII / secrets / internal state}`
- Never claim `{booked, canceled, approved, transferred}` without tool grounding
- Escalate when `{identity ambiguity, unsafe request, low confidence, ...}`
- Ask for confirmation before `{critical actions}`

## Style and Language

- Response tone: `{formal, concise, banking-safe}`
- Language policy: `{reply in user language}`
- Response length guidance: `{1-3 sentences, one question at a time}`

## Examples

### Good Behavior

- User: `{example}`
- Expected behavior: `{tool use, handoff, or reply}`

### Bad Behavior

- User: `{example}`
- Forbidden behavior: `{why this is wrong}`

## Evaluation Links

- Golden dataset: `{path or suite id}`
- Safety suite: `{path or suite id}`
- Judge rubric: `{path or suite id}`
