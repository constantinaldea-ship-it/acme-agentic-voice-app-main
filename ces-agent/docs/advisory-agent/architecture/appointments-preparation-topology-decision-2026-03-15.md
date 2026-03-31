# Advisory Appointment Preparation Topology Decision

**Status:** Approved for AGENT-007 preparation build  
**Date:** 2026-03-15  
**Related Plan:** [AGENT-007 Appointment Context](../implementation-plan/AGENT-007-appointment-context.md)

## Decision

For the AGENT-007 preparation build, the runtime topology is:

`CES OpenAPI toolset -> bfa-service-resource -> mock-server`

This is the implementation baseline for all work in this repository until a later ADR replaces it.

## What This Explicitly Means

- CES imports and calls the appointment capability through the `bfa-service-resource` REST API.
- `bfa-service-resource` owns the appointment domain contract exposed to CES.
- `mock-server` simulates the upstream appointment provider and owns immutable fixture truth.
- `bfa-gateway`, AG-004, Glue, and the on-prem advisory path remain future cutover architecture, not current build scope.

## Why This Decision Was Needed

Existing repository artifacts describe two different topologies:

- the current CES integration pattern already uses `bfa-service-resource` directly for OpenAPI toolsets
- the appointment architecture workshop artifacts describe a future `bfa-gateway -> AG-004 -> Glue` production path

Without an explicit preparation decision, backend, CES, and mock-server work would start against incompatible assumptions.

## Consequences

- Build now proceeds against the direct `bfa-service-resource` surface.
- The future internal-adapter path is preserved as a provider seam, not implemented in this phase.
- Any new appointment code or docs that assume `bfa-gateway` routing for the H1 mock build are out of baseline and need an explicit follow-up ADR.
