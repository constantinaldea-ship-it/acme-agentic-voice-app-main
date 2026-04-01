# CES Agent Deployment Scripts

Canonical deployment tooling for the CES agent package lives in this folder.

These scripts cover the unified CES deployment workflow:

- **Primary entrypoint:** `ces-deploy-manager.py` for both bootstrap imports and incremental CES updates
- **Bootstrap helper:** `deploy-agent.sh --import` remains the underlying full-app CES `apps:importApp` implementation reused by the deploy manager when the app is missing

## Script inventory

| Script | Status | Purpose |
|---|---|---|
| `ces-deploy-manager.py` | Preferred primary entrypoint | Detect missing apps, bootstrap if needed, then plan and apply only changed toolsets, tools, and agents |
| `ces-undeploy-manager.py` | Preferred cleanup | Delete CES agents, tools, and toolsets idempotently using local manifests plus tracked state |
| `validate-package.py` | Shared prerequisite | Cross-reference validation for the CES app package before import or incremental deploy |
| `deploy-agent.sh` | Bootstrap helper | Validate, package, and optionally import the full CES app ZIP when invoked directly or by the deploy manager |
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

This is now the preferred default deploy path because it handles both bootstrap
and incremental updates. On every run it prints the resolved CES app target,
discovered live deployment IDs, and any configured `CES_DEPLOYMENT_ID` match
status before showing the plan. The terminal plan still uses color-coded
sections for `Added`, `Updated`, `Removed`, and `No-op` so review is faster
before approval.

## Bootstrap vs incremental updates

`ces-deploy-manager.py` is now the single recommended entrypoint.

It decides the mode automatically:

- **Brand-new CES app / no app imported yet** → detects the missing app, explains that bootstrap import is required, asks for confirmation, then invokes the existing `deploy-agent.sh --import` path
- **Existing CES app / iterative changes** → continues with the normal incremental deployment plan
- **Existing CES app / one agent missing** → can still create the missing agent as long as the parent app already exists and any referenced toolsets, tools, and child agents are available in the same run

Why the distinction still matters internally:

- `ces-deploy-manager.py` talks to the CES **resource** APIs under an existing
  `projects/*/locations/*/apps/*` parent once bootstrap is complete
- `deploy-agent.sh --import` still uses `apps:importApp`, which remains the repository's authoritative full-app bootstrap implementation reused by the deploy manager
- built-in CES system tools such as `end_session` are not local tool folders and
  are now preserved as built-ins during incremental agent creation

When the app is missing, the deploy manager now catches that condition before it
tries to create child resources. Instead of surfacing a raw `404 Parent
resource does not exist`, it prints that the app is missing, shows the resolved
target, prompts for confirmation, and can run the bootstrap import path for you.

Typical symptom:

```text
HTTP 404
Parent resource does not exist.
```

Recommended default command from `ces-agent/scripts/deploy/`:

```bash
python3 ces-deploy-manager.py
```

If the app is missing, that single command will:

1. detect the missing CES app
2. ask whether it should run the bootstrap import
3. invoke `deploy-agent.sh --import` on your behalf if you confirm
4. re-check the target and continue with incremental/state reconciliation when appropriate

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
full-app import behavior. `ces-deploy-manager.py` now invokes this path
automatically for first-time bootstrap, but `deploy-agent.sh` remains available
for direct/manual full-app imports.

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
