# CES Tools Folder

This directory contains Python tool and tool-manifest artifacts that belong to the
full `acme_voice_agent` CES package.

## Important Packaging Rule

Do not import or deploy this `tools/` directory by itself into CX Agent Studio.

Why:

- some Python tools depend on agent-level tool attachment
- some Python tools call attached OpenAPI toolsets through the CES runtime bridge
- tool execution also depends on sibling package artifacts such as `agents/`,
  `toolsets/`, and `environment.json`

For this repository, the deployable unit is the full `acme_voice_agent/` package,
packaged through `ces-agent/scripts/deploy/deploy-agent.sh`.

## Current Naming Convention

For the customer-details flow, CES now exposes two Python tool variants:

- `get_customer_details_python`
  pure Python direct HTTP implementation
- `get_customer_details_wrapper`
  Python wrapper that orchestrates `customer_details_openapi`

And one OpenAPI toolset:

- `customer_details_openapi`

See:

- `../../scripts/README.md`
- `../../docs/cx-agent-studio/python-runtime-and-cloud-run-connectivity.md`
