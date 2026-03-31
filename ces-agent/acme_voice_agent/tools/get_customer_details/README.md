# get_customer_details_wrapper Tool

> **Note:** CES now imports this capability as a direct `pythonFunction` tool via
> `get_customer_details.json`. The wrapper implementation in
> `python_function/python_code.py` exposes a single `get_customer_details_wrapper` call to
> the model and executes the EIDP → AuthZ → Customer API flow internally through the
> attached `customer_details_openapi` OpenAPI toolset.
>
> **Packaging rule:** this tool is not intended to be imported into CX Agent
> Studio on its own. It depends on the full `acme_voice_agent` package,
> including the `customer_details_agent`, the attached `customer_details_openapi`
> OpenAPI toolset, and package-level environment configuration.

## Overview

This tool now showcases the safer wrapper pattern for CES:

1. The model calls **one** tool: `get_customer_details_wrapper`
2. Deterministic Python code performs the internal authentication chain
3. The wrapper returns only the final customer payload + summary to the agent

Under the hood, the customer lookup still uses the same three-step IDP token flow:

1. **EIDP Token** — Authenticates with client credentials to get an access token
2. **AuthZ Token** — Exchanges the EIDP token for a resource-specific authorization token
3. **Customer API** — Fetches customer data using the authorization token + required headers

## CES and Mock-Server Alignment

These values must stay aligned between `ces-agent` and `java/mock-server` for
the deployed test path to work:

| Concern | CES side | Mock-server side |
|--------|----------|------------------|
| Base URL | `acme_voice_agent/environment.json` → `toolsets.customer_details_openapi.openApiToolset.url` | Cloud Run service URL for `mock-server` |
| API key | `tools/get_customer_details/python_function/python_code.py` → `DEFAULT_API_KEY` | `MOCK_API_KEY` env var on the mock-server deployment |
| EIDP client | `client_id=ces-agent-service`, `client_secret=mock-secret` in `python_function/python_code.py` | Accepted by `/oauth/token` in `java/mock-server/docs/idp-token-flow.md` |
| AuthZ request | `resource=customers:personal-data`, `action=read` | Accepted by `/authz/authorize` |
| Customer headers | `deuba-client-id=pb-banking`, `DB-ID=acme-banking-db-01` | Required by `/customers/{partnerId}/personal-data` |

The local CLI client now also supports `--api-key` so it can exercise the same
deployed mock-server protection that CES uses through the toolset config.

## Usage

```bash
# Activate virtual environment
source ../../.venv/bin/activate

# Basic usage (mock server must be running on localhost:8080)
python get_customer_details.py --partner-id 1234567890

# With custom server URL
python get_customer_details.py --partner-id 1234567890 --base-url https://mock-server.example.com

# Against a protected Cloud Run mock-server deployment
python get_customer_details.py --partner-id 1234567890 \
  --base-url https://mock-server.example.com \
  --api-key "$MOCK_API_KEY"

# Verbose mode
python get_customer_details.py --partner-id 1234567890 -v
```

## Test Partner IDs

| Partner ID | Customer | Notes |
|------------|----------|-------|
| `1234567890` | Dr. Maria Musterfrau | Female, single, Berlin |
| `any-other-value` | Hans Müller | Male, married, Berlin (default) |

## CES Agent Integration

The tool is registered in `get_customer_details.json` as a CES-importable
`pythonFunction` for the `customer_details_agent`.

- The agent extracts the `partner_id` from the user's voice request.
- The agent calls **one** tool: `get_customer_details_wrapper`.
- The wrapper then invokes the attached `customer_details_openapi` toolset operations
	internally, so the model does not have to pass bearer tokens between steps.
- In imported CES apps, the wrapper also sends the mock `X-API-Key` header
  explicitly. This avoids the post-2025 imported-agent secret resolution issue
  that breaks inline `apiKeyConfig` values for this demo flow.
- The CES runtime bridge for those helper operations exists only when the full
  app is imported and the tool runs under the correctly wired agent/toolset
  configuration.

For local development and CLI testing, `get_customer_details.py` remains the
reference HTTP client implementation.

## Dependencies

- Local CLI/reference client: `requests`
- CES runtime wrapper: Python built-ins and the CES runtime bridge; do not
  assume the local `requests` dependency is part of the CX Agent Studio runtime
