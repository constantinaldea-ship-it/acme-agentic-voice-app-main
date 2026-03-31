# BFA Service Deployment Guide

This module-specific guide has been superseded by the repository-level `DEPLOYMENT-GUIDE.md`.

Use the shared Cloud Run deployment suite from the repository root:

```bash
./scripts/cloud/deploy.sh service bfa-service-resource YOUR_GCP_PROJECT
./scripts/cloud/smoke-test.sh service bfa-service-resource https://bfa-service-resource-<hash>-uc.a.run.app
./scripts/cloud/undeploy.sh service bfa-service-resource YOUR_GCP_PROJECT --force
```

For dependency wiring, full-stack orchestration, CES packaging steps, and cost-control cleanup, see:

- `../../DEPLOYMENT-GUIDE.md`
- `../../GCP-RESOURCE-PROVISIONING.md`
