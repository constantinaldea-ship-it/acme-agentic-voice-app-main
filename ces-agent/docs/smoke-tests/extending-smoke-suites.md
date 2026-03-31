# Extending CES Smoke Suites

## Goal

This guide explains how to add new smoke cases and how to extend the framework when the current test types are not enough.

Canonical smoke assets live in:
- `ces-agent/test-harness/smoke/ces-runtime-smoke.py`
- `ces-agent/test-harness/smoke/suites/`
- `ces-agent/test-harness/smoke/tests/test_ces_runtime_smoke.py`

## Adding a New Test Case

### Step 1: Choose the right existing test type

Use:
- `resource_exists` for deployment wiring checks
- `toolset_schema` for OpenAPI/toolset contract checks
- `execute_tool` for direct Python tool runtime checks
- `execute_toolset_tool` for one OpenAPI operation executed through CES
- `openapi_operation` for remote HTTP checks resolved from an OpenAPI contract

### Step 2: Copy the closest existing case

Start from `ces-agent/test-harness/smoke/suites/ces/customer-details-smoke-suite.json` and copy a nearby block.
Keep the same field names expected by the runtime.

### Step 3: Set the right target fields

Common target keys:
- `display_name`
- `tool_display_name`
- `toolset_display_name`
- `tool_id`
- `service`
- `operation_id`

The runtime does not validate the suite against a formal JSON schema, so spelling must match exactly.

### Step 4: Add arguments and expectations

Use `args` for tool input.
Use `variables` only when a runtime variable binding is required.
Use `expect` with:
- `exists`
- `equals`
- `contains`

### Step 5: Use the correct response root

- direct tool execution → usually `response.result.*`
- toolset operation execution → usually `response.*`

Examples:
- `response.result.customer.firstname`
- `response.access_token`

### Step 6: Prefer environment placeholders for environment-specific values

Example:

```json
{
  "project": "${GCP_PROJECT_ID}",
  "location": "${GCP_LOCATION}",
  "app_id": "${CES_APP_ID}"
}
```

Use the same style for API keys or dynamic runtime values when appropriate.

For remote OpenAPI suites, prefer a separate `services_file` instead of
embedding base URLs in each test.

### Step 7: Run the suite and adjust only stable assertions

Prefer:
- `exists` for tokens or generated IDs
- `contains` for summaries
- `equals` for deterministic business data

## Adding a New Suite

Create a new JSON file under `ces-agent/test-harness/smoke/suites/`.

Recommended naming:
- `{domain}-smoke-suite.json`
- `{domain}-regression-suite.json`
- `{domain}-contract-suite.json`

Keep each suite focused on one runtime area.

Examples:
- `customer-details-smoke-suite.json`
- `appointments-smoke-suite.json`
- `branch-finder-contract-suite.json`
- `discovery-plan-remote-smoke-suite.json`

## Extending the Framework

There are two main ways to extend the framework.

### Option A: Add new expectation operators

If execution flow stays the same but assertions are too limited, extend `evaluate_expectations(...)` in `ces-agent/test-harness/smoke/ces-runtime-smoke.py`.

Good candidates:
- `not_exists`
- `not_equals`
- `regex`
- `length`
- numeric comparison operators

After changing `evaluate_expectations(...)`:
1. add unit tests in `tests/test_ces_runtime_smoke.py`
2. document the new operator in `runtime-api-reference.md`
3. add at least one suite case that uses it

### Option B: Add a new test type/category

If the execution model changes, add a new `type` branch in `run_suite(...)`.

Typical examples:
- `resource_count`
- `expected_error`
- `tool_chain`
- `latency_budget`
- `openapi_operation`

Implementation steps:
1. define the new JSON shape
2. add a branch in `run_suite(...)`
3. add helper logic if needed
4. raise `SmokeError` for invalid input or failed checks
5. add unit tests for both pass and fail paths
6. document the new type in `runtime-api-reference.md`

## Example: Adding an `expected_error` test type

You would need to update both layers.

### JSON suite example

```json
{
  "name": "customer_details_requires_partner_id",
  "type": "expected_error",
  "tool_display_name": "get_customer_details_wrapper",
  "args": {
    "partner_id": ""
  },
  "expect_error_contains": "partner_id is required"
}
```

### Python runtime changes

In `run_suite(...)`:
- add a branch for `expected_error`
- execute the tool
- assert that the returned payload or raised error contains the expected failure signal

## Unit Test Requirements for Framework Changes

When the smoke framework changes, add tests in `ces-agent/test-harness/smoke/tests/test_ces_runtime_smoke.py` for:
- parser behavior
- response-path evaluation
- contract checks
- new suite branching behavior
- fail-fast behavior when relevant

## Relationship to Backend Contracts

Smoke suites should stay aligned with:
- `ces-agent/acme_voice_agent/toolsets/customer_details/open_api_toolset/open_api_schema.yaml`
- `ces-agent/acme_voice_agent/tools/get_customer_details/python_function/python_code.py`
- `java/mock-server/docs/idp-token-flow.md`
- `ces-agent/docs/idp-token-flow-in-ces.md`
- `java/bfa-gateway/openapi-specs/bfa-gateway.json`
- `java/bfa-adapter-branch-finder/openapi-specs/branch-finder-adapter.json`
- `java/bfa-service-resource/openapi-specs/bfa-resource-all.json`

When a toolset or wrapper contract changes, update:
1. the deployed package
2. the smoke suite assertions
3. the framework tests if parser/runtime behavior changed
