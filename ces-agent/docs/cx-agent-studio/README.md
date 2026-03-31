# CX Agent Studio Architecture Guide

Author: Codex
Date: 2026-02-07
Status: Draft for implementation teams
Modified by Codex on 2026-02-07 to expand official coverage map.
Modified by  on 2026-02-07 to add design-patterns.md navigation and scope update.

## Purpose
This folder documents Google CX Agent Studio as a distinct 2026 platform for building multi-agent, tool-using conversational systems with declarative configuration, guardrails, callbacks, and evaluation workflows.

This guide is intentionally separate from Dialogflow CX, Conversational Agents legacy patterns, and Google ADK code-first SDK workflows.

## Quick Navigation
- `platform-reference.md`
- `developer-guide.md`
- `design-patterns.md`
- `callback-example.md`
- `python-tools-good-practices.md`
- `python-runtime-and-cloud-run-connectivity.md`
- `header-attribution-thin-wrapper-poc.md`
- `../../vpc/vpc-service-controls-problem-statement.md`
- `cx-agent-studio-vs-alternatives.md`
- `sample-analysis.md`
- `caveats.md`
- `banking-use-case-mapping.md`

## Platform Positioning
| Platform | Primary approach | Best fit | Notable gap vs CX Agent Studio |
|---|---|---|---|
| CX Agent Studio | Declarative app/agent/tool/callback artifacts | Fast assembly of multi-agent CX systems with built-in guardrails and eval assets | Less low-level control than fully code-first orchestration |
| Dialogflow CX | Flow/page/intent deterministic conversation design | Intent-centric IVR/chatbot flows | Less natural agentic orchestration ergonomics |
| Google ADK | Code-first SDK (.py/.ts/.go/.java) | Custom runtime control and code-native orchestration | More engineering effort to reproduce CX Studio-style declarative console workflows |

## Core Concepts
| Concept | What it means in CX Agent Studio | Example artifact |
|---|---|---|
| App | Top-level package binding root agent, global instructions, variables, guardrails, logging, eval thresholds | `sample/Sample_app_2026-02-07-162736/app.json` |
| Agent | Specialized runtime unit with model, instruction, tools, child agents, callbacks | `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/cymbal_retail_agent.json` |
| Instruction | Structured prompt contract with role/persona/constraints/taskflow and transfer/tool directives | `sample/Sample_app_2026-02-07-162736/agents/cymbal_retail_agent/instruction.txt` |
| Tool | Callable capability (Python, OpenAPI, Search, built-ins) | `sample/Sample_app_2026-02-07-162736/tools/*/*.json` |
| Toolset | Grouped OpenAPI-backed actions with shared auth/base URL | `sample/Sample_app_2026-02-07-162736/toolsets/crm_service/crm_service.json` |
| Variable | Typed session/global state used in prompts and callbacks | `variableDeclarations` in `app.json` |
| Guardrail | Safety/policy checks with deterministic action on trigger | `sample/Sample_app_2026-02-07-162736/guardrails/*/*.json` |
| Callback | Runtime interception hooks around model/tool/agent lifecycle | `agents/*/*callbacks*/python_code.py` |
| Evaluation | Declarative scenario/golden test assets for behavior assertions | `sample/Sample_app_2026-02-07-162736/evaluations/*/*.json` |
| Design Pattern | Official reusable callback/tool patterns with code examples | `docs/architecture/cx-agent-studio/design-patterns.md` |

## Multi-Agent Architecture Pattern
```mermaid
graph TD
  U[User] --> A0[cymbal_retail_agent]
  A0 -->|{@AGENT: cymbal_upsell_agent}| A1[cymbal_upsell_agent]
  A0 -->|{@AGENT: out_of_scope_handling}| A2[out_of_scope_handling]
  A2 -->|return transfer| A0

  A0 --> T1[greeting]
  A0 --> T2[get_product_recommendations]
  A0 --> T3[update_cart]
  A1 --> T4[get_landscaping_quote]
  A1 --> T5[approve_discount]
  A1 --> T6[schedule_planting_service]

  A0 --> C1[before_model / after_model callbacks]
  A0 --> C2[after_tool / after_agent callbacks]
  A1 --> C3[before_tool callback]

  G[Guardrails] --> A0
  G --> A1
```

## When To Use CX Agent Studio
Use CX Agent Studio when you need:
- Multi-agent orchestration with explicit transfer semantics and deterministic return behavior.
- Strong declarative governance artifacts for reviews and audits.
- Built-in guardrails, callback hooks, and evaluation assets managed as first-class configuration.
- Fast iteration in a build/evaluate/deploy console workflow.

Prefer alternatives when:
- You need deep custom runtime behavior in code across arbitrary infrastructure boundaries.
- You require full orchestration logic as application code and CI-native artifacts only.

## Scope Of This Documentation Set
This folder includes:
- Config schema and syntax references.
- Developer workflow and first-agent implementation steps.
- Official design patterns with Python code examples.
- Cross-platform comparison matrix.
- Deep analysis of the Cymbal sample app.
- Caveats, anti-patterns, and migration risks.
- Mapping to voice-banking requirements.

## Source Map
Primary source set used in this documentation:
- CX Agent Studio docs: tools, variables, guardrails, callbacks, handoffs, best practices and pattern guidance, evaluation pages, export/import docs, deployment channels, API access, CMEK, audit logging, operations pages.
- Sample package: `sample/Sample_app_2026-02-07-162736/`.

### Provided Link Coverage
| Provided link | Coverage approach |
|---|---|
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/agent-sample | Covered directly from the official sample-agents page and the exported Cymbal sample package. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/instruction | Covered directly from the official instructions page and validated against sample artifacts. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool | Covered directly from official tools page and sample tool artifacts. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/variable | Covered directly from official variables page and sample variable declarations/usage. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/guardrail | Covered directly from official guardrails page and sample guardrail JSONs. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback | Covered directly from official callback docs and sample callback Python implementations. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation | Covered directly from the official evaluation page and sample evaluation assets. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/export | Covered directly from the official export format reference and export/import docs. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/design-pattern | This URL returned HTTP 404 during verification on 2026-03-13. Pattern guidance in this folder is instead mapped from `best-practices`, `callback`, `tool/python`, `tool/open-api`, `reference/python`, and sample analysis. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices | Covered directly from official best-practices docs and applied recommendations. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/google-telephony-platform | Covered directly in deployment architecture/channel sections. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/api-access | Covered directly in runtime/API integration sections. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy | Covered directly in deployment channel overview sections. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/cmek | Covered directly in security/compliance sections. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/audit-logging | Covered directly in security/compliance sections. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/guardrail | Duplicate in provided list; already covered above. |
| https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback | Duplicate in provided list; already covered above. |

Note:
- `agent-sample`, `instruction`, `evaluation`, and `reference/export` all resolved successfully during verification on 2026-03-13.
- The dedicated `design-pattern` URL returned HTTP 404 during the same verification pass, so pattern material in this folder is synthesized from the live `best-practices`, `callback`, `tool/python`, `tool/open-api`, `reference/python`, and sample pages.

### Additional High-Value Topics To Include
These were not all explicitly listed in the original request but are important for production guidance:
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/web-widget`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/twilio`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/five9`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/ccaas`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/conversation-history`
- `https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/flow`

Operational note:
- Navigation references for `version`, `simulator`, and `change-history` appear in the CX docs IA, but the direct aliases may resolve inconsistently in crawler fetches. Use the navigation links in the CX docs console/docs tree and the REST resources (`projects.locations.apps.versions`, `projects.locations.apps.changelogs`) as canonical fallback.

## External References
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/variable
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/guardrail
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/handoff
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/best-practices
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/agent-sample
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/instruction
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/evaluation-batch-upload
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/export
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/reference/export
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/web-widget
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/api-access
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/google-telephony-platform
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/twilio
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/five9
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/deploy/ccaas
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/cmek
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/audit-logging
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/open-api
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/tool/mcp
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/conversation-history
- https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/flow
