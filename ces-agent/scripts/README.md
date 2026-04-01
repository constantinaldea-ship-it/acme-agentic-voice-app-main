# CES Agent Scripts

Helper scripts for testing, validating, and packaging the CES agent.

## Layout

Deployment tooling now lives canonically in `scripts/deploy/`.

- `scripts/deploy/README.md` — canonical deployment folder guide

## Scripts Overview

| Script | Purpose |
|--------|---------|
| `test-tool-call.py` | Call the live BFA API to verify tool call results |
| `prompt_contracts.py` | Parse CES `instruction.txt` files into deterministic prompt contracts |
| `deploy/ces-deploy-manager.py` | Preferred state-aware incremental CES deploy manager for day-to-day updates |
| `deploy/validate-package.py` | Canonical validator for `acme_voice_agent/` package integrity |
| `deploy/deploy-agent.sh` | Canonical full-app ZIP packaging and optional CES import |
| `deploy/manage_datastore.sh` | Canonical fee schedule datastore lifecycle management |
| `vpc/` | Canonical VPC Service Controls toolkit folder |
| `vpc-sc.sh` | Backward-compatible wrapper to the canonical VPC control script |
| `../test-harness/smoke/ces-runtime-smoke.py` | Canonical runtime smoke harness for deployed CES tools and toolset operations |

---

## Critical Packaging Rule

The deployable/importable unit for this repository is the full
`ces-agent/acme_voice_agent/` package.

Do not import `ces-agent/acme_voice_agent/tools/` by itself into CX Agent Studio.

Reason:

- Python tools in this repository can depend on agent-level wiring
- wrapper tools can depend on attached OpenAPI toolsets
- environment resolution depends on package-level artifacts such as
  `agents/`, `toolsets/`, and `environment.json`

Use `ces-deploy-manager.py` as the default CES deployment entrypoint.
It now handles both first-time bootstrap and normal incremental updates.
Use `deploy-agent.sh` directly only when you explicitly want the underlying
full-app packaging/import workflow itself.

See also:

- `../acme_voice_agent/tools/README.md`
- `../docs/cx-agent-studio/python-runtime-and-cloud-run-connectivity.md`

---

## Deployment scripts

Start with `deploy/README.md` for the unified deploy workflow.

### Recommended entrypoints

```bash
cd ces-agent/scripts

# Default CES deployment entrypoint: bootstrap if needed, otherwise run the
# normal incremental resource-level plan/apply flow.
python3 deploy/ces-deploy-manager.py --validate-only
python3 deploy/ces-deploy-manager.py
python3 deploy/ces-deploy-manager.py --status

# Optional direct full-app packaging / import helper
./deploy/deploy-agent.sh --validate
./deploy/deploy-agent.sh --import --location us

# Datastore lifecycle management
./deploy/manage_datastore.sh status
./deploy/manage_datastore.sh provision

```

### First deploy vs follow-up deploys

- Use `python3 deploy/ces-deploy-manager.py ...` as the default command in both
  cases.
- If the target CES app does not exist yet, the deploy manager detects that,
  prompts for confirmation, and dispatches the existing bootstrap import path
  for you.
- If the app already exists, the deploy manager stays on the normal
  resource-level incremental path.
- If only a specific agent is missing from an existing app, the incremental
  deploy manager can create it in dependency order. Built-in tools like
  `end_session` do not need a local tool folder.

## VPC Toolkit

Reversible helper for managing a VPC Service Controls perimeter around the
current CES project.

Why this script exists:

- dry-run, enforce, and delete map directly to `gcloud access-context-manager`
  commands
- this workflow is easier to toggle on and off than a Terraform-only flow
- it matches the current VPC-SC experiment plan documented in
  `../vpc/vpc-service-controls-problem-statement.md`

Terraform can still be added later for long-lived perimeter management, but for
the current experiment the `gcloud` workflow is the better fit because VPC-SC
dry-run and enforce transitions are first-class CLI operations.

### Required setup

Set at least:

- `GCP_PROJECT_ID`
- `VPC_SC_ACCESS_POLICY_ID`

Optional sample values are in:

- `../vpc/vpc-sc.env.example`
- `../vpc/vpc-sc.ingress.example.yaml`
- `../vpc/vpc-sc.egress.example.yaml`
- `../vpc/README.md`

### Commands

```bash
cd ces-agent/scripts

# Print the resolved configuration
../vpc/vpc-sc.sh config

# Create or refresh a dry-run perimeter
../vpc/vpc-sc.sh dry-run-on

# Enforce the dry-run perimeter or create/update an enforced perimeter
../vpc/vpc-sc.sh enforce-on

# Remove only the explicit dry-run spec
../vpc/vpc-sc.sh dry-run-off

# Delete the perimeter entirely
../vpc/vpc-sc.sh off

# Show current enforced and dry-run state
../vpc/vpc-sc.sh status
```

### Notes

- The script auto-loads the repo root `.env` and the default Cloud Run state file
  if present.
- `./vpc-sc.sh` remains available as a thin compatibility wrapper.
- It resolves the current project number from `GCP_PROJECT_ID` unless
  `VPC_SC_PROJECT_NUMBER` is set explicitly.
- The default restricted services baseline is:
  - `ces.googleapis.com`
  - `contactcenterinsights.googleapis.com`
  - `storage.googleapis.com`
  - `bigquery.googleapis.com`
- Ingress and egress rules are optional and can be passed through YAML files via
  env vars.

## validate-package.py

Cross-reference validator for the CES agent package. Checks that all JSON pointers
(rootAgent, globalInstruction, guardrails, agent instructions, toolsets, schemas,
evaluations) resolve correctly. Also enforces **L-01** (no OpenAPI operations in
evaluation `toolCall` expectations) and known **import compatibility** constraints
for manifest fields that CES currently parses as `int32`.

### Usage

```bash
cd ces-agent/scripts

# Validate the default package (../acme_voice_agent/)
python3 deploy/validate-package.py

# Validate a specific directory
python3 deploy/validate-package.py /path/to/agent/package
```

### What It Checks

| # | Check | Description |
|---|-------|-------------|
| 1 | `app.json` | Exists and is valid JSON |
| 1a | Manifest import compatibility | Rejects known import-unsafe values such as decimal `semanticSimilaritySuccessThreshold` |
| 2 | `rootAgent` | Points to an existing `agents/` subdirectory |
| 3 | `globalInstruction` | File reference resolves |
| 4 | Guardrails | L-04 naming: spaces in `app.json`, underscores in folders |
| 5 | Agent instructions | Every agent's `instruction` file exists |
| 5b | Instruction contracts | Enforces local section-order contracts from `scripts/contracts/instruction-contract-rules.json` and validates instruction refs against the owning agent manifest |
| 6 | Agent toolsets | Every toolset reference resolves to `toolsets/` |
| 7 | Toolset schemas | Every OpenAPI schema file exists |
| 8 | Tool inventory | Collects direct tools + OpenAPI operations |
| 9 | Evaluations | L-01 — rejects `toolCall` referencing OpenAPI operations |

---

## Prompt Contract POC

`prompt_contracts.py` is a deterministic parser for CES `instruction.txt` files.
The initial proof of concept is covered by:

- `test_prompt_contracts.py`

Current scope:

- verify required top-level prompt sections
- verify direct-tool and toolset-operation references used in the prompt
- verify high-risk text invariants for `location_services_agent/instruction.txt`
- guard against regressions such as the legacy `limit=5` example bug

### Usage

```bash
cd ces-agent/scripts
python3 -m unittest test_prompt_contracts.py
```

This is intentionally narrower than CES evaluations and runtime smoke tests. It is
meant to catch prompt-definition regressions before deployment.

---

## deploy-agent.sh

Packages `acme_voice_agent/` into a ZIP and either:

- prints CX Agent Studio manual import instructions, or
- imports the ZIP directly via the CES `apps:importApp` REST API when `--import` is used.

Runs `validate-package.py` first — if validation fails, no ZIP is created.

If the packaged app contains `agents/fee_schedule_agent/fee_schedule_agent.json`,
`deploy-agent.sh` first invokes `manage_datastore.sh provision` so the
`fee_schedule_lookup` tool points to a live, indexed Vertex AI Search
datastore before the ZIP is created/imported.

This script packages the full app. It is the authoritative full-app import path
for this repository. It is not the preferred day-to-day workflow when
incremental deploys are sufficient.

### Usage

```bash
cd ces-agent/scripts

cp ../../.env.example ../../.env
# edit ../../.env and set GCP_PROJECT_ID (and GCP_LOCATION if using --import)

# Full workflow: validate → package → print import instructions
./deploy/deploy-agent.sh

# Validate only (no ZIP)
./deploy/deploy-agent.sh --validate

# Override the configured GCP project ID for console links
./deploy/deploy-agent.sh --project my-gcp-project

# Package + import to CES using GCP_PROJECT_ID/GCP_LOCATION from ../../.env
./deploy/deploy-agent.sh --import

# Clean up CES agents/tools/toolsets and the fee schedule datastore
cd ..
./undeploy.sh

# Reimport into an existing app with explicit app ID

./deploy/deploy-agent.sh --import --project my-gcp-project --location eu \
	--app-id acme-voice-eu --import-strategy OVERWRITE
```

### Import Flags

| Flag | Description |
|---|---|
| `--import` | Import the packaged ZIP via CES `apps:importApp` |
| `--location` | CES location, typically `us` or `eu` |
| `--app-id` | Target app ID for new import or reimport |
| `--display-name` | Display name for a newly created imported app |
| `--import-strategy` | `REPLACE` or `OVERWRITE` for conflicts on reimport |
| `--ignore-app-lock` | Ignore app lock during import |
| `--poll-interval` | Poll interval in seconds for long-running import operation |
| `--poll-timeout` | Timeout in seconds for import polling |

### Authentication for `--import`

The import workflow uses Google Cloud bearer tokens obtained from your local environment.

- first tries active gcloud CLI credentials
- falls back to Application Default Credentials (ADC)

Typical setup:

```bash
gcloud auth login
# optional but useful for local ADC-based tooling
gcloud auth application-default login
```

### Output

- `ces-agent/acme_voice_agent-YYYYMMDD-HHMMSS.zip` (versioned)
- `ces-agent/acme_voice_agent.zip` (latest, overwritten each run)
- when `--import` is used: CES operation status plus imported app resource name
- the ZIP contains the full `acme_voice_agent/` package structure expected by the
  repository’s CES tooling

---

## ces-deploy-manager.py

State-aware CES deploy utility for bootstrap and incremental CES resource APIs.

This tool is meant to reduce redundant CES API traffic during local iteration.
It computes a local deployment plan from tracked hashes instead of blindly
re-importing the whole app every time.

Current behavior:

- validates the local package first
- detects whether the target CES app already exists
- shows the resolved CES app ID plus live/configured deployment IDs before execution
- prompts to run bootstrap import when the app is missing
- tracks toolsets, tools, and agents in `.ces-deployment-state.json`
- hashes manifests plus referenced files such as `instruction.txt`,
  Python code, and OpenAPI schemas
- classifies each component as `Added`, `Modified`, or `No-op`
- asks for explicit confirmation before making remote changes
- deploys in dependency order: `toolset -> tool -> agent`
- updates the local state file only for successful deploys
- writes a per-run JSON artifact under `deploy/.artifacts/`

Important constraints:

- bootstrap still reuses the repository's authoritative `deploy-agent.sh --import`
  path under the hood
- removed components are reported but are not deleted remotely
- direct `deploy-agent.sh` usage remains available when you explicitly want the
  standalone full-app import behavior

### Usage

```bash
cd ces-agent/scripts

# Validate and print the incremental plan only
python3 deploy/ces-deploy-manager.py --validate-only

# Print the plan, then ask for confirmation
python3 deploy/ces-deploy-manager.py

# Apply without prompting
python3 deploy/ces-deploy-manager.py --yes

# Inspect the latest local deploy state and run artifact
python3 deploy/ces-deploy-manager.py --status
python3 deploy/ces-deploy-manager.py --status-json

# Use an explicit state file or app root
python3 deploy/ces-deploy-manager.py \
  --state-file ../.ces-deployment-state.json \
  --app-root ../acme_voice_agent \
  --project my-gcp-project \
  --location us \
  --app-id acme-voice-us
```

### State file

Default state path:

- `ces-agent/.ces-deployment-state.json`

Each successful resource deploy records:

- component kind and resource id
- display name
- tracked file hashes
- combined component SHA-256
- deployed timestamp
- resolved CES resource name

Each run artifact additionally records:

- `run_id`
- exact plan and outcome
- before/after hashes
- optional Git commit SHA
- optional remote `updateTime` / `createTime`

### Recommendation

Use `ces-deploy-manager.py` as the default deployment command.
Use `deploy-agent.sh` directly only when you intentionally want to run the
standalone full-app packaging/import workflow yourself.

---

## CES smoke tests

The canonical CES runtime smoke harness now lives outside `scripts/` in:

- `../test-harness/smoke/ces-runtime-smoke.py`
- `../test-harness/smoke/suites/`
- `../test-harness/smoke/tests/`

Use that folder for:

- runtime smoke execution against deployed CES apps
- JSON suite definitions
- framework unit tests

Start here:

- `../test-harness/README.md`
- `../test-harness/smoke/README.md`
- `../docs/smoke-tests/runtime-api-reference.md`
- `../docs/smoke-tests/extending-smoke-suites.md`

The `scripts/ces-runtime-smoke.py` file remains as a backward-compatible shim,
but new usage and documentation should target `ces-agent/test-harness/`.

---

## test-tool-call.py

Calls the live BFA API with the same parameters the CES agent would use, so you can verify tool call results **before** importing into Agent Studio.

### Prerequisites

- Python 3.10+
- Network access to the BFA service (Cloud Run)
- No additional packages required (uses only stdlib)

### Quick Start

```bash
cd ces-agent/scripts

# Search branches in Munich (what the agent does for "I need a branch in Munich")
./test-tool-call.py searchBranches city=München

# Summary mode — top 3 + counts only
./test-tool-call.py searchBranches city=München --summary

# Get full details for a specific branch
./test-tool-call.py getBranch branchId=20173597
```

### Operations

| Operation | Description | Required Params |
|-----------|-------------|-----------------|
| `searchBranches` | Search by city, address, postal code, GPS, brand | At least one of: city, postalCode, latitude+longitude |
| `getBranch` | Full details for a branch | `branchId` |

### searchBranches Parameters

| Parameter | Type | Example | Description |
|-----------|------|---------|-------------|
| `city` | string | `München` | City name (**use German name** — the agent translates) |
| `address` | string | `Alexander` | Street/landmark prefix match |
| `postalCode` | string | `80331` | Postal code prefix |
| `latitude` | float | `48.137` | GPS latitude |
| `longitude` | float | `11.575` | GPS longitude |
| `radiusKm` | float | `5` | Search radius in km (requires lat/lon) |
| `brand` | string | `Postbank` | `"Deutsche Bank"` or `"Postbank"` |
| `accessible` | bool | `true` | Only wheelchair-accessible branches |
| `limit` | int | `3` | Max results (default 10, max 50) |

### Flags

| Flag | Description |
|------|-------------|
| `--summary` | Show only top 3 + counts (quick check) |
| `--raw` | Output raw JSON (pipe to `jq`) |
| `--url URL` | Override base URL (default: `BFA_SERVICE_RESOURCE_URL` from env/state, then `environment.json`) |

### Example Workflows

**Verify evaluation examples match real data:**

```bash
# Munich — instruction says 21 branches
./test-tool-call.py searchBranches city=München --summary

# Cologne — instruction says 18 branches
./test-tool-call.py searchBranches city=Köln --summary

# Karlsruhe — instruction says 3 branches
./test-tool-call.py searchBranches city=Karlsruhe --summary
```

**Verify constraint #8 city translation works:**

```bash
# English "Munich" should be sent as German "München"
./test-tool-call.py searchBranches city=München --summary

# English "Cologne" should be sent as German "Köln"
./test-tool-call.py searchBranches city=Köln --summary
```

**Test edge cases:**

```bash
# No results (non-existent city)
./test-tool-call.py searchBranches city=Entenhausen

# Accessible Postbank in Berlin (returns 0)
./test-tool-call.py searchBranches city=Berlin brand=Postbank accessible=true

# Address search near landmark
./test-tool-call.py searchBranches city=Berlin address=Alexander
```

**Get raw JSON for debugging:**

```bash
./test-tool-call.py searchBranches city=München --raw | jq '.data.totalMatches'
```

### How It Works

1. Reads the BFA base URL from `BFA_SERVICE_RESOURCE_URL` in the repo root `.env` or `.tmp/cloud-run/discovery-plan.env`, then falls back to `../acme_voice_agent/environment.json`
2. Builds the same REST call the CES agent's OpenAPI toolset would make
3. Shows `totalMatches` vs `count` to verify the agent uses the right field
4. Generates the expected agent response per instruction constraint #4
