# `get_customer_details_python` Tool

Created by Codex on 2026-03-28.

This CES tool is the **pure Python** comparison point for the customer-details flow.

- CES-visible name: `get_customer_details_python`
- Implementation type: direct Python HTTP
- Runtime path: `tools/get_customer_details_python/python_function/python_code.py`

## Purpose

This tool performs the full customer-details flow directly in Python:

1. `POST /oauth/token`
2. `POST /authz/authorize`
3. `GET /customers/{partnerId}/personal-data`

It does **not** call the `customer_details_openapi` toolset.

## Compare With

- `get_customer_details_wrapper`
  Python wrapper that internally calls the `customer_details_openapi` helper methods
- `customer_details_openapi`
  OpenAPI toolset for the same backend contract

## Output Contract

The tool returns the same high-level shape as the wrapper variant:

- `success`
- `summary`
- `customer`
- optional `debug_capture`

This makes side-by-side CES testing straightforward.
