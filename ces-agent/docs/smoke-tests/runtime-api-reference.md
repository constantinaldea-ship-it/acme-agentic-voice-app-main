# CES Runtime Smoke API Reference

## Purpose

`ces-agent/test-harness/smoke/ces-runtime-smoke.py` is a reusable CES runtime test harness.
It validates deployed tools and toolset operations through the live CES API instead of calling backend services directly.

## Supported Commands

### `list-resources`
Lists deployed CES resources inside an app.

Required flags:
- `--project`
- `--location`
- `--app-id`

Optional flags:
- `--endpoint`
- `--kind` (`agents`, `tools`, `toolsets`)

### `execute-tool`
Executes a direct CES tool by display name.

Required flags:
- `--project`
- `--location`
- `--app-id`
- `--tool-display-name`

Optional flags:
- `--endpoint`
- `--arg key=value`
- `--variable key=value`

### `execute-toolset`
Executes one OpenAPI-derived toolset operation through CES.

Required flags:
- `--project`
- `--location`
- `--app-id`
- `--toolset-display-name`
- `--tool-id`

Optional flags:
- `--endpoint`
- `--arg key=value`
- `--variable key=value`

### `run-suite`
Executes a JSON-defined smoke suite.

Required flags:
- `--suite`

Optional flags:
- `--fail-fast`

## Suite JSON Contract

Top-level fields:
- `project`
- `location`
- `app_id`
- optional `endpoint`
- optional `services_file`
- optional inline `services`
- `tests`

Environment placeholders in the form `${ENV_VAR}` are supported in all string values.

### Supported test types

#### `resource_exists`
Checks that a deployed resource can be resolved uniquely by display name.

Required fields:
- `kind`
- `display_name`

#### `toolset_schema`
Checks the deployed toolset schema text for expected operation IDs or snippets.

Required fields:
- `toolset_display_name`

Optional fields:
- `expect_operation_ids`
- `expect_schema_contains`

#### `execute_tool`
Executes a direct CES tool.

Required fields:
- `tool_display_name`

Optional fields:
- `args`
- `variables`
- `expect`

#### `execute_toolset_tool`
Executes one OpenAPI operation from a deployed toolset.

Required fields:
- `toolset_display_name`
- `tool_id`

Optional fields:
- `args`
- `variables`
- `expect`

#### `openapi_operation`
Executes one remote HTTP request by resolving `service + operation_id` from a
separate OpenAPI service catalog.

Required fields:
- `service`
- `operation_id`

Optional fields:
- `request.headers`
- `request.query_params`
- `request.path_params`
- `request.json_body`
- `request.body`
- `request.timeout_seconds`
- `expected_status`
- `expect`

### Service catalog contract

When `services_file` is used, it must point to a JSON file with:

- top-level `services`
- one object per service alias

Each service definition supports:
- `display_name`
- `base_url_env`
- `required_env`
- `openapi_spec`
- `default_headers`

## Expectation Semantics

`expect` supports three operators.

### `exists`
List of dot-paths that must be present in the response.

### `equals`
Object mapping dot-path to exact value.

### `contains`
Object mapping dot-path to required substring.

## Response Shape Rules

Use the correct response root when writing assertions.

- direct tool execution usually returns business data below `response.result`
- toolset-tool execution usually returns operation payload below `response`
- `openapi_operation` responses expose:
  - `status`
  - `headers`
  - `body`
  - `json`

Examples:
- `response.result.success`
- `response.result.customer.firstname`
- `response.access_token`
- `status`
- `json.success`

## Google API Integration

The smoke runner talks to the CES REST API directly.

### Endpoint selection
- `us` → `https://ces.us.rep.googleapis.com`
- `eu` → `https://ces.eu.rep.googleapis.com`
- fallback → `https://ces.googleapis.com`

### Authentication
The script shells out to `gcloud` and uses a bearer token from one of:
- `gcloud auth print-access-token`
- `gcloud auth application-default print-access-token`

### API calls

#### Resource listing
`GET /v1/projects/{project}/locations/{location}/apps/{app_id}/{kind}`

Used for:
- resource discovery
- display-name resolution

#### Runtime tool execution
`POST /v1/projects/{project}/locations/{location}/apps/{app_id}:executeTool`

Payload shape for direct tools:
```json
{
  "tool": "projects/.../tools/...",
  "args": {},
  "variables": {}
}
```

Payload shape for toolset operations:
```json
{
  "toolsetTool": {
    "toolset": "projects/.../toolsets/...",
    "toolId": "getEidpToken"
  },
  "args": {},
  "variables": {}
}
```

## Failure Model

Transport and contract failures are normalized into `SmokeError`.

Examples:
- invalid `key=value` input
- missing environment variables for placeholders
- missing response paths
- duplicate or missing display-name matches
- HTTP failures from CES
- unsupported suite test types

## Recommended Usage Pattern

1. use `list-resources` when diagnosing deployment wiring
2. use `execute-tool` or `execute-toolset` to debug a single runtime path
3. encode the stable assertions in a suite under `ces-agent/test-harness/smoke/suites/`
4. run `python3 -m unittest tests/test_ces_runtime_smoke.py` after changing framework behavior
