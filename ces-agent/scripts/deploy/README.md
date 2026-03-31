# CES Agent Deployment Scripts

Canonical deployment tooling for the CES agent package lives in this folder.

These scripts cover three deployment paths:

- **Preferred daily path:** plan and apply incremental CES updates for changed toolsets, tools, and agents
- **Full reset path:** package the full `acme_voice_agent/` app and import it with CES `apps:importApp`

## Script inventory

| Script | Status | Purpose |
|---|---|---|
| `ces-deploy-manager.py` | Preferred | Build a state-aware deployment plan and apply only changed toolsets, tools, and agents |
| `ces-undeploy-manager.py` | Preferred cleanup | Delete CES agents, tools, and toolsets idempotently using local manifests plus tracked state |
| `validate-package.py` | Shared prerequisite | Cross-reference validation for the CES app package before import or incremental deploy |
| `deploy-agent.sh` | Full-app fallback | Validate, package, and optionally import the full CES app ZIP |
| `manage_datastore.sh` | Supported helper | Provision and sync the fee schedule datastore used by the app package |

## Internal layout

- `lib/` — internal Python helpers used by the deploy and undeploy managers
  - `lib/deploy_resource_lib.py`
- `tests/` — deployment-focused regression tests
  - `tests/test_ces_deploy_manager.py`
  - `tests/test_ces_undeploy_manager.py`
  - `tests/test_deploy_resource_lib.py`

## Preferred workflow

From `ces-agent/scripts/deploy/`:

```bash
# Print the incremental deployment plan only
python3 ces-deploy-manager.py --validate-only

# Print the plan, then ask for confirmation
python3 ces-deploy-manager.py

# Skip the confirmation prompt
python3 ces-deploy-manager.py --yes

# Show the latest local deployment status
python3 ces-deploy-manager.py --status

# Show the same status as JSON
python3 ces-deploy-manager.py --status-json
```

This is the preferred day-to-day deploy path because it minimizes redundant CES
API calls, captures a per-run artifact, and keeps the local state aligned with
the deployed resources. The terminal plan uses color-coded sections for
`Added`, `Updated`, `Removed`, and `No-op` so review is faster before approval.

## Bootstrap vs incremental updates

Use the two deployment paths like this:

- **Brand-new CES app / no app imported yet** → start with `./deploy-agent.sh --import`
- **Existing CES app / iterative changes** → use `python3 ces-deploy-manager.py`
- **Existing CES app / one agent missing** → `ces-deploy-manager.py` can create the
  missing agent as long as the parent app already exists and any referenced
  toolsets, tools, and child agents are available in the same run

Why the distinction matters:

- `ces-deploy-manager.py` talks to the CES **resource** APIs under an existing
  `projects/*/locations/*/apps/*` parent
- `deploy-agent.sh --import` uses `apps:importApp`, which is the repository's
  authoritative **bootstrap** path for creating or resetting the app package
- built-in CES system tools such as `end_session` are not local tool folders and
  are now preserved as built-ins during incremental agent creation

If incremental deploy fails with HTTP 404 against the app path, the app itself
is usually missing. In that case, run one full import first and then rerun the
incremental deploy manager.

## Incremental deployment workflow

Use the deploy manager when you want to avoid redundant CES API calls during
iterative development.

It:

- validates the package before planning
- hashes manifests plus referenced files such as `instruction.txt`, Python code,
  and OpenAPI schemas
- classifies components as `Added`, `Updated`, `Removed`, or `No-op`
- deploys in dependency order: `toolset -> tool -> agent`
- writes `.ces-deployment-state.json` only after each successful deploy
- writes one JSON run artifact per invocation under `scripts/deploy/.artifacts/`
- preserves built-in CES tools such as `end_session` during first-time agent creation

```bash
# Print the incremental deployment plan only
python3 ces-deploy-manager.py --validate-only

# Print the plan, then ask for confirmation
python3 ces-deploy-manager.py

# Skip the confirmation prompt
python3 ces-deploy-manager.py --yes
```

Each run artifact contains:

- `run_id`
- target app/project/location metadata
- optional Git commit SHA
- the exact deployment plan
- before/after combined hashes per component
- per-component execution outcome
- optional remote `updateTime` / `createTime` captured from CES responses

The deploy manager also supports a local status view derived from
`.ces-deployment-state.json` and the latest run artifact. This is the preferred
way to inspect the last deployed time, last run id, Git SHA, and remote
`updateTime` values without modifying CES resource descriptions.

## Full-app import workflow

Use the ZIP import path when you intentionally want to treat the local package
as the full source of truth or when you need the repository's authoritative
full-app import behavior. This is also the correct path for the **first deploy**
into a brand-new CES app.

```bash
# Validate only
./deploy-agent.sh --validate

# Package the app and print manual import instructions
./deploy-agent.sh

# Package and import directly into CES
./deploy-agent.sh --import --location us
```

## Compatibility notes

Use this `scripts/deploy/` folder as the single canonical entrypoint for CES deployment, validation, and datastore lifecycle commands.
