# Phase Gate Protocol (Reusable)

**Default PHASE:** `DESIGN_ONLY`
**Transition Token:** `APPROVE_IMPLEMENTATION`

## Phase Definitions
- **DESIGN_ONLY:** Explore architecture, plans, risks, test strategy, and assumptions. Do **not** include fenced code blocks, diffs/patch syntax (`diff --git`, `@@`, `+/-`), or explicit file-edit instructions.
- **IMPLEMENT_ALLOWED:** After the user provides `APPROVE_IMPLEMENTATION`, share patches/diffs, commands, and execution details.

## Examples
- **Valid DESIGN_ONLY output:**
  - "Plan: Update `agents/templates/*` to add PHASE headers and approval token usage. Risks: inconsistent language. Tests: template lints unaffected. Next: ask for `APPROVE_IMPLEMENTATION`."
- **Invalid DESIGN_ONLY output:**
  - Including \`\`\` fences, `diff --git`, or step-by-step file edits.
- **Approval request:**
  - "If the design looks good, reply with `APPROVE_IMPLEMENTATION` so I can provide patches."
- **Valid IMPLEMENT_ALLOWED output:**
  - Provide diffs/patches and command snippets as needed once the token is granted.

## Exit Checklists
- **Design Exit:** scope/objectives clear; candidate files/tests listed; risks and open questions noted; rollback/monitoring considerations captured; no fenced code/patch syntax present.
- **Implementation Exit:** patches shared for all files; tests/checks listed with status; rationale/trade-offs/risks summarized; doc/tracking updates identified; follow-ups (if any) captured.

Use this protocol across all templates to keep design and implementation clearly separated and auditable.
