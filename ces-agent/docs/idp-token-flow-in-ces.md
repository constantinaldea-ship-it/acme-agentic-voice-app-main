# IDP Token Flow in CES — Design & Implementation Notes

> **Status:** Active  
> **Last Updated:** 2026-03-13  
> **Purpose:** Documents how the 3-step IDP token flow is implemented in CES using OpenAPI toolsets.

---

## Problem

The customer details API requires a 3-step authentication flow:

1. **EIDP Token** — `POST /oauth/token` → client credentials → access token
2. **AuthZ Token** — `POST /authz/authorize` → exchange EIDP token → authorization token
3. **Customer API** — `GET /customers/{id}/personal-data` → use AuthZ token → customer data

This is a multi-step chain where each step depends on the output of the previous one.

## Key Question: Can CES Chain These Steps Automatically?

**No.** CES OpenAPI toolsets do **not** support automatic token chaining or multi-step OAuth flows. Each `operationId` is an independent tool the agent can call. CES provides three authentication mechanisms, none of which support custom multi-step flows:

| Auth Mechanism | What It Does | Fits Our Flow? |
|---|---|---|
| `serviceAgentIdTokenAuthConfig` | CES injects a Google ID token (OIDC) for Cloud Run IAM auth | ❌ No — injects Google token, not our EIDP/AuthZ tokens |
| `apiKeyConfig` | CES injects a static API key header | ⚠️ Historically worked, but imported/exported apps now prefer Secret Manager-backed credentials; inline demo values can fail at runtime |
| OAuth2 (CES native) | CES handles OAuth2 client credentials or authorization code | ❌ No — this is for standard OAuth2, not a custom 2-layer EIDP+AuthZ flow |

## Solution: Single Wrapper Tool over the OpenAPI Toolset

CES still does **not** chain these operations automatically. Instead, the
project now showcases a hybrid wrapper pattern:

- The model calls one direct Python wrapper tool: `get_customer_details_wrapper`
- That wrapper tool deterministically invokes the three OpenAPI operations
- The underlying service integration remains in the `customer_details_openapi` OpenAPI toolset

Underlying toolset operations:

```
customer_details_openapi toolset:
  ├── getEidpToken           ← Step 1: POST /oauth/token
  ├── getAuthzToken          ← Step 2: POST /authz/authorize
  └── getCustomerPersonalData ← Step 3: GET /customers/{id}/personal-data
```

The `customer_details_agent` instruction now tells the model to call only
`get_customer_details_wrapper(partner_id)`. Inside the wrapper, deterministic Python
code performs the chain:

1. Call `getEidpToken` → extract `access_token`
2. Call `getAuthzToken` with `Authorization: Bearer {access_token}` → extract `authorization_token`
3. Call `getCustomerPersonalData` with `Authorization: Bearer {authorization_token}` + other headers

**The wrapper code handles token passing between steps.** This works because:
- The model only sees one business-level tool
- Python code deterministically extracts and forwards intermediate tokens
- The OpenAPI toolset still supplies the backend contract and static auth configuration

## Authentication Layers

The toolset uses **two independent auth mechanisms**:

1. **API Key (wrapper-managed in this demo):** `X-API-Key` is now passed explicitly by the Python wrapper on each helper-operation call. This avoids imported-app secret resolution failures for inline demo credentials while still protecting the mock server.

2. **Bearer tokens (wrapper-managed):** The `Authorization: Bearer <token>` header for Steps 2 and 3 is managed inside the Python wrapper. CES does not inject these automatically, but the model also no longer carries them between calls.

## Trade-offs

| Aspect | Impact |
|--------|--------|
| **Security posture** | Intermediate bearer tokens stay inside deterministic Python code rather than the model's working context. |
| **Latency** | The backend still performs 3 sequential API calls, but the model makes only 1 tool call. This reduces orchestration overhead while keeping backend behavior unchanged. |
| **Evaluation** | The wrapper is a direct tool, so it can be referenced in `toolCall` expectations and mocked in scenario-style tests. |
| **Maintenance** | The hybrid pattern adds Python wrapper code that must stay aligned with the OpenAPI toolset contract. |

## File Inventory

| File | Purpose |
|------|---------|
| `toolsets/customer_details/customer_details.json` | Toolset config for the underlying helper operations (`customer_details_openapi`) |
| `toolsets/customer_details/open_api_toolset/open_api_schema.yaml` | OpenAPI 3.0 schema with all 3 helper endpoints |
| `agents/customer_details_agent/customer_details_agent.json` | Agent config referencing all 3 toolIds |
| `agents/customer_details_agent/instruction.txt` | Agent instruction that calls a single wrapper tool |
| `environment.json` | Mock server URL for the toolset |
| `tools/get_customer_details/get_customer_details.json` | CES-importable direct `pythonFunction` wrapper config (`get_customer_details_wrapper`) |
| `tools/get_customer_details/python_function/python_code.py` | Wrapper implementation that chains the helper operations internally |
| `tools/get_customer_details/get_customer_details.py` | Local CLI/reference implementation for direct HTTP testing |

## Comparison Variant: Pure Python Tool

To make the implementation choices visible in CES, the app now also ships a
second direct Python tool:

- `get_customer_details_python`

This comparison tool performs the same three backend calls directly through
`ces_requests` inside Python code and does **not** invoke the
`customer_details_openapi` toolset.

That means CES now exposes the customer-details flow as:

- `get_customer_details_python`
  pure Python direct HTTP implementation
- `get_customer_details_wrapper`
  Python wrapper over `customer_details_openapi`
- `customer_details_openapi`
  OpenAPI toolset with the three helper operations
