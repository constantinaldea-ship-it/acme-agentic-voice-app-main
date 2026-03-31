# Prompt Evaluation Plan Template

**Prompt / Agent:** `{agent_name}`  
**Owner:** `{team_or_person}`  
**Version Under Test:** `{major.minor}`  
**Created:** `{YYYY-MM-DD}`

## Objective

Describe what behavioral change or risk this evaluation plan is meant to measure.

## Datasets

| Dataset | Purpose | Required |
|--------|---------|----------|
| `{golden_dataset}` | Core task success regression | Yes |
| `{safety_dataset}` | Prompt injection / hallucination / PII checks | Yes |
| `{edge_case_dataset}` | Missing data and backend failure handling | Recommended |

## Metrics

| Metric | Type | Threshold |
|-------|------|-----------|
| Task success | Golden comparison | `{threshold}` |
| Correct handoff | Golden comparison | `{threshold}` |
| Confirmation compliance | Rule / judge | `{threshold}` |
| Hallucination rate | Judge / rule | `{max}` |
| PII leakage rate | Rule / judge | `{max}` |
| Language consistency | Rule / judge | `{threshold}` |

## Deterministic Checks

- Required instruction sections present
- Referenced tools and agents exist
- Forbidden patterns absent
- Tool and handoff syntax valid

## LLM-as-a-Judge Rubrics

List each rubric with a short scoring question:

- `{rubric_name}`: `{single evaluation question}`
- `{rubric_name}`: `{single evaluation question}`

## Pairwise Comparison Plan

- Baseline prompt: `{current version}`
- Candidate prompt: `{new version}`
- Compare on: `{datasets}`
- Promotion rule: `{must outperform or be non-inferior on critical metrics}`

## Release Gate

Prompt may advance only if:

- all deterministic checks pass
- no safety threshold regresses
- critical path metrics meet target
- pairwise comparison is acceptable

## Follow-up

Define how failing cases will be turned into new regression examples.
