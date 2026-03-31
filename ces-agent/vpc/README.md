# VPC Controls Toolkit

Author: Codex
Date: 2026-03-28
Status: Active

This folder is the canonical home for VPC Service Controls work in `ces-agent`.

## Contents

- `vpc-sc.sh`
  reversible perimeter control script for the current CES project
- `vpc-sc.env.example`
  sample environment variables for Access Context Manager setup
- `vpc-sc.ingress.example.yaml`
  ingress policy skeleton
- `vpc-sc.egress.example.yaml`
  egress policy skeleton
- `vpc-service-controls-problem-statement.md`
  design and evaluation document for the current CES architecture

## Primary Workflow

```bash
source /Users/constantinaldea/IdeaProjects/ai-account-balance/loadenv.sh
export VPC_SC_ACCESS_POLICY_ID=123456789

/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh config
/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh dry-run-on
/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh status
/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh enforce-on
/Users/constantinaldea/IdeaProjects/ai-account-balance/ces-agent/vpc/vpc-sc.sh off
```

## Notes

- `scripts/vpc-sc.sh` remains as a backward-compatible wrapper.
- The current implementation is `gcloud`-first rather than Terraform-first
  because dry-run and enforce transitions are direct Access Context Manager CLI
  operations.
