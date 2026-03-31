# Callback Example: `creditcards_agent`

Author: Codex
Date: 2026-03-28
Status: Active

## Purpose

This page gives a minimal real callback example from the live `ces-agent`
package so teams can see what callbacks are for in practice.

The example uses a `before_model_callback` on `creditcards_agent` to intercept
urgent lost-card, stolen-card, and fraud requests before the model generates a
normal response.

## Why use a callback here

The prompt already tells the credit-cards specialist to prioritize urgent
safety topics. The callback exists to show the extra value of callbacks:

- deterministic interception before the model runs
- guaranteed safety wording for high-risk triggers
- reduced reliance on prompt compliance for urgent cases

This is the simplest useful pattern for understanding callbacks:

- inspect latest user input
- decide whether the request matches a narrow rule
- return a crafted response if it does
- otherwise return `None` and allow the normal model flow

## Files

- Agent manifest:
  - `acme_voice_agent/agents/creditcards_agent/creditcards_agent.json`
- Callback code:
  - `acme_voice_agent/agents/creditcards_agent/before_model_callbacks/urgent_card_safety/python_code.py`
- Local unit test:
  - `acme_voice_agent/agents/creditcards_agent/test_urgent_card_safety_callback.py`

## What the callback does

The callback:

1. reads the latest user text from `callback_context`
2. checks for urgent card-safety keywords in English and German
3. if matched, returns a deterministic safety-first message
4. if not matched, returns `None` and lets the model respond normally

This demonstrates the key purpose of callbacks in CX Agent Studio:

- use prompts for broad behavior
- use callbacks for deterministic enforcement, short-circuiting, and state mutation

## Why this example is low-risk

- It is isolated to one subagent.
- It only triggers on urgent keywords.
- It does not call external services.
- It does not mutate session state.
- It does not change tool behavior.

## How to reason about it

Use this example when you want to understand:

- when a callback is better than prompt-only behavior
- how to short-circuit a model turn
- how to keep callback logic narrow and auditable

Do not use this example as a template for large business workflows. For those,
callbacks should stay small and call tools or services rather than embedding
heavy logic directly.

## Suggested next examples

After understanding this example, the next useful callback patterns are:

- `before_tool_callback` for policy gating
- `after_tool_callback` for state/bookkeeping
- `after_model_callback` for deterministic output correction

## References

- Google callback docs:
  - https://docs.cloud.google.com/customer-engagement-ai/conversational-agents/ps/callback
- Local callback lifecycle reference:
  - `docs/cx-agent-studio/platform-reference.md`
- Local pattern guide:
  - `docs/cx-agent-studio/design-patterns.md`
