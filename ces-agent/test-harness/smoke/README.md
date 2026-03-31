# CES Smoke Tests

`ces-agent/test-harness/smoke/` is the canonical home for reusable smoke testing of
deployed CES resources and e2e-remote OpenAPI/backend endpoints.

This framework now covers two layers:

- **CES runtime checks**
  direct tools, toolset-derived tools, deployed resource presence
- **E2E-remote HTTP/OpenAPI checks**
  Cloud Run health endpoints, gateway invocation paths, and raw OpenAPI-backed endpoints

## Folder Layout

```text
ces-agent/test-harness/smoke/
├── ces-runtime-smoke.py
├── README.md
├── suites/
│   ├── ces/
│   │   └── customer-details-smoke-suite.json
│   └── e2e-remote/
│       ├── discovery-plan-e2e-remote-services.json
│       └── discovery-plan-e2e-remote-smoke-suite.json
└── tests/
    └── test_ces_runtime_smoke.py
```

## Main Improvements

- one reusable runner for both CES and non-CES e2e-remote checks
- JSON-defined suites instead of hardcoded shell-only flows
- OpenAPI-aware service catalogs that map env vars to contract files
- failure artifacts written per test under `.artifacts/`
- raw response previews printed on failure for faster debugging
- shared top-level runner in `ces-agent/test-harness/run-smoke-tests.sh`
- auto-discovery of all CES and e2e-remote suites from dedicated folders

## Quick Start

Run everything:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-smoke-tests.sh all
```

Run only CES runtime smoke tests:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-smoke-tests.sh ces
```

Run only e2e-remote HTTP/OpenAPI smoke tests:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-smoke-tests.sh e2e-remote
```

Run only the framework unit tests:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-smoke-tests.sh unit
```

If the Cloud Run stack state file is not at the default path, pass it explicitly:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness
./run-smoke-tests.sh e2e-remote /absolute/path/to/discovery-plan.env
```

For backward compatibility, `./run-smoke-tests.sh remote` still works as an alias,
but `e2e-remote` is the preferred name.

## Environment Inputs

The wrapper loads the repo root `.env` and, when present, the discovery-plan
state file from:

- `/Users/constantinaldea/IdeaProjects/ai-account-balance/.tmp/cloud-run/discovery-plan.env`

Common variables:

- `GCP_PROJECT_ID`
- `GCP_LOCATION`
- `CES_APP_ID`
- `CUSTOMER_DETAILS_API_KEY`
- `MOCK_SERVER_URL`
- `BFA_ADAPTER_BRANCH_FINDER_URL`
- `BFA_GATEWAY_URL`
- `BFA_SERVICE_RESOURCE_URL`

## 429 / RESOURCE_EXHAUSTED Troubleshooting

If agent testing or CES smoke execution fails with:

```json
{
  "code": 429,
  "message": "Resource has been exhausted (e.g. check quota).",
  "status": "RESOURCE_EXHAUSTED"
}
```

the failure is typically on the CES or model quota side, not in the backend
OpenAPI services.

Use this quick isolation flow:

1. Run a single CES command first:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py list-resources \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID" \
  --kind tools
```

2. If that single CES call also returns 429, treat it as external quota exhaustion
   in Google Cloud / CES / model capacity.
3. If single CES calls work but a full CES suite fails later, reduce test volume,
   use `--fail-fast`, and rerun after a short pause.
4. If the e2e-remote HTTP/OpenAPI suite passes while CES calls fail, the backend is
   healthy and the issue is isolated to CES/runtime quota.

## Core Commands

List deployed CES tools:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py list-resources \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID" \
  --kind tools
```

Execute a direct CES tool:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py execute-tool \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID" \
  --tool-display-name get_customer_details_wrapper \
  --arg partner_id=1234567890
```

Execute the pure direct-Python CES tool:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py execute-tool \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID" \
  --tool-display-name get_customer_details_python \
  --arg partner_id=1234567890
```

Execute a toolset-derived OpenAPI operation through CES:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py execute-toolset \
  --project "$GCP_PROJECT_ID" \
  --location "$GCP_LOCATION" \
  --app-id "$CES_APP_ID" \
  --toolset-display-name customer_details_openapi \
  --tool-id getEidpToken \
  --arg X-API-Key="$CUSTOMER_DETAILS_API_KEY" \
  --arg grant_type=client_credentials \
  --arg client_id=ces-agent-service \
  --arg client_secret=mock-secret
```

Execute a raw e2e-remote HTTP/OpenAPI endpoint check:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py http-request \
  --method GET \
  --url "${BFA_SERVICE_RESOURCE_URL}/api/v1/appointment-taxonomy" \
  --header "Authorization=Bearer mock-appointment-service-token"
```

Run a suite directly:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 ces-runtime-smoke.py run-suite \
  --suite ./suites/ces/customer-details-smoke-suite.json
```

## Adding More Suites

The wrapper now auto-discovers suites by folder and filename pattern.

- Put CES runtime suites in `suites/ces/`
- Put e2e-remote/OpenAPI suites in `suites/e2e-remote/`
- Name runnable suite files with the suffix `-smoke-suite.json`

Examples:

- `suites/ces/account-balance-smoke-suite.json`
- `suites/e2e-remote/branch-search-e2e-remote-smoke-suite.json`

When you run one of these wrapper modes:

- `./run-smoke-tests.sh ces`
- `./run-smoke-tests.sh e2e-remote`
- `./run-smoke-tests.sh all`

the wrapper executes every matching suite in the appropriate folder automatically.

### CES suite authoring

Use `suites/ces/customer-details-smoke-suite.json` as the reference shape.

For the customer-details comparison flow, the CES inventory should now expose:

- `get_customer_details_python`
- `get_customer_details_wrapper`
- `customer_details_openapi`

Typical CES suite contents:

- `project`
- `location`
- `app_id`
- `tests` using `resource_exists`, `execute_tool`, `toolset_schema`, or `execute_toolset_tool`

### E2E-remote/OpenAPI suite authoring

Use `suites/e2e-remote/discovery-plan-e2e-remote-smoke-suite.json` as the reference shape.

Typical e2e-remote suite contents:

- `services_file` pointing at a service catalog in the same folder
- `tests` using `openapi_operation` or `http_request`

Service catalog files such as `discovery-plan-e2e-remote-services.json` can live beside the suite file. They are not auto-run because only files matching `*-smoke-suite.json` are executed.

## OpenAPI DSL

E2E-remote suites can now use a contract-aware DSL instead of hardcoded URLs.

Use:

- `services_file` to load a separate service catalog
- `type: openapi_operation` to resolve a request from `service + operation_id`
- `request.headers`, `request.query_params`, `request.path_params`, and `request.json_body`
  for runtime inputs

Example:

```json
{
  "services_file": "./discovery-plan-e2e-remote-services.json",
  "tests": [
    {
      "name": "bfa_gateway.invoke.branch_finder_search_city_berlin",
      "type": "openapi_operation",
      "service": "bfa_gateway",
      "operation_id": "invoke",
      "request": {
        "headers": {
          "Authorization": "Bearer smoke-user"
        },
        "json_body": {
          "toolName": "branchFinder",
          "parameters": {
            "city": "Berlin"
          }
        }
      },
      "expected_status": 200
    }
  ]
}
```

## Suites

### `suites/ces/customer-details-smoke-suite.json`

Validates:

- deployed `get_customer_details_wrapper` tool exists
- deployed `customer_details_openapi` toolset exists
- deployed `customer_details_agent` exists
- direct tool execution succeeds through CES
- deployed toolset schema still contains expected operations
- `getEidpToken` executes directly via `toolsetTool`

### `suites/e2e-remote/discovery-plan-e2e-remote-smoke-suite.json`

Consolidates the old Cloud Run checks into the framework with an OpenAPI-aware DSL:

- `mock_server_upstream.getAppointmentTaxonomyUpstream`
- `branch_finder_adapter.health`
- `bfa_gateway.health`
- `bfa_gateway.invoke`
- `advisory_appointment_resource.health`
- `advisory_appointment_resource.getAppointmentTaxonomy`

### `suites/e2e-remote/discovery-plan-e2e-remote-services.json`

Maps each e2e-remote service to:

- its OpenAPI contract file
- the base URL environment variable
- any additional required environment variables
- default headers such as `X-API-Key`

## Failure Debugging

Each `run-suite` invocation writes artifacts under:

- `ces-agent/test-harness/smoke/.artifacts/<timestamp>/`

Artifacts include:

- one JSON file per test with request/response/error data
- `summary.json` with pass/fail totals and artifact references

On failure, the runner prints:

- failing test name and type
- service alias and `operation_id` for `openapi_operation` tests
- failure reason
- response preview
- artifact file path

## Validation

Run the framework unit tests:

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/test-harness/smoke
python3 -m unittest -b tests/test_ces_runtime_smoke.py
```

## References

- Official CES runtime execution API:
  `projects.locations.apps.executeTool`
- `ExecuteToolRequest` supports:
  - `tool`
  - `toolsetTool`
- Generated API reference:
  <https://pkg.go.dev/google.golang.org/api/ces/v1>

## Notes

- The old `scripts/cloud/smoke-test.sh` checks are now represented in
  `suites/e2e-remote/discovery-plan-e2e-remote-smoke-suite.json`.
- Use `suites/ces/` and `suites/e2e-remote/` as the source of truth for new smoke suites
  rather than adding more ad hoc shell checks elsewhere.
