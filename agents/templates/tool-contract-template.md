# Tool Contract Template

**Tool Name:** `{tool_name}`  
**Owner:** `{team_or_person}`  
**Type:** `{python|openapi|wrapper}`  
**Version:** `{major.minor}`  
**Status:** `{draft|active|deprecated}`

## Purpose

Describe when this tool should be used and what business capability it provides.

## Invocation Guidance

- Use when: `{conditions}`
- Do not use when: `{conditions}`
- Requires confirmation before use: `{yes/no + conditions}`

## Inputs

| Field | Type | Required | Description |
|------|------|----------|-------------|
| `{field}` | `{type}` | `{yes/no}` | `{meaning}` |

## Outputs

| Field | Type | Description |
|------|------|-------------|
| `{field}` | `{type}` | `{meaning}` |

## Errors

| Error Case | Behavior | User-safe Message |
|-----------|----------|-------------------|
| `{case}` | `{technical behavior}` | `{safe summary}` |

## Safety Constraints

- Must not return `{sensitive fields}`
- Must not mutate state unless `{conditions}`
- Must be grounded before claiming `{result types}`

## Testing Requirements

- Unit tests: `{required cases}`
- Mocked dependencies: `{http, auth, db, tool bridge}`
- Contract checks: `{schema or example validation}`
- Regression tests: `{known failure cases}`

## Example

- Input: `{example request}`
- Output: `{example response summary}`
