# GCP Resource Provisioning Log

**Project:** from `GCP_PROJECT_ID` in the repository root `.env`  
**Region:** from `GCP_REGION` in the repository root `.env`  
**Automation source of truth:** `DEPLOYMENT-GUIDE.md` and `scripts/cloud/`  

---

## 1. Cloud Run services in the discovery-plan stack

| Service | Purpose | Module path | Deploy command |
|---|---|---|---|
| `mock-server` | WireMock-based upstream for appointment and mock banking APIs | `java/mock-server/` | `./scripts/cloud/deploy.sh service mock-server <PROJECT_ID>` |
| `bfa-adapter-branch-finder` | AG-003 branch finder adapter | `java/bfa-adapter-branch-finder/` | `./scripts/cloud/deploy.sh service bfa-adapter-branch-finder <PROJECT_ID>` |
| `bfa-gateway` | Single-ingress gateway for the adapter topology | `java/bfa-gateway/` | `./scripts/cloud/deploy.sh service bfa-gateway <PROJECT_ID>` |
| `bfa-service-resource` | Resource-oriented BFA API | `java/bfa-service-resource/` | `./scripts/cloud/deploy.sh service bfa-service-resource <PROJECT_ID>` |

### Default runtime profile

| Property | `mock-server` | `bfa-adapter-branch-finder` | `bfa-gateway` | `bfa-service-resource` |
|---|---:|---:|---:|---:|
| Port | 8080 | 8082 | 8081 | 8080 |
| Memory | 1Gi | 512Mi | 512Mi | 1Gi |
| CPU | 1 | 1 | 1 | 1 |
| Max instances | 4 | 3 | 3 | 4 |
| Min instances | 0 | 0 | 0 | 0 |
| Execution env | gen2 | gen2 | gen2 | gen2 |

### Dependency-linked environment variables

| Service | Variables set by shared deploy suite |
|---|---|
| `mock-server` | `MOCK_API_KEY` when exported locally |
| `bfa-gateway` | `ADAPTER_BRANCH_FINDER_URL` |
| `bfa-service-resource` | `BFA_SECURITY_ENABLED=false`, `BFA_APPOINTMENT_UPSTREAM_BASE_URL` |

---

## 2. Full-stack recreate

```bash
cp .env.example .env
# edit .env and set GCP_PROJECT_ID, GCP_REGION, and GCP_LOCATION

gcloud config set project "$GCP_PROJECT_ID"

export MOCK_API_KEY="replace-me-if-needed"
./scripts/cloud/deploy.sh stack discovery-plan
./scripts/cloud/smoke-test.sh stack discovery-plan
```

After deploy, the shared suite records service URLs in:

```bash
.tmp/cloud-run/discovery-plan.env
```

Use those URLs to update any operator tooling that depends on fixed endpoints, including:

- `java/mock-server/bruno/environments/CloudRun.bru`
- any CES environment or manual test harness that pins Cloud Run URLs

---

## 3. Image storage

The current automation publishes images to:

```bash
gcr.io/<PROJECT_ID>/<service-name>
```

Examples:

- `gcr.io/$GCP_PROJECT_ID/mock-server`
- `gcr.io/$GCP_PROJECT_ID/bfa-adapter-branch-finder`
- `gcr.io/$GCP_PROJECT_ID/bfa-gateway`
- `gcr.io/$GCP_PROJECT_ID/bfa-service-resource`

---

## 4. Cost-control teardown

### Safe teardown

Deletes the Cloud Run services but preserves shared registries and other reusable infrastructure.

```bash
./scripts/cloud/undeploy.sh stack discovery-plan --force
```

### Deep cleanup

Deletes Cloud Run services and the corresponding container images for this stack.

```bash
./scripts/cloud/undeploy.sh stack discovery-plan --force --delete-images
```

### Verification

```bash
gcloud run services list --project "$GCP_PROJECT_ID" --region "$GCP_REGION"
gcloud container images list --repository "gcr.io/$GCP_PROJECT_ID"
```

---

## 5. Cleanup policy notes

- Prefer deleting service images, not shared registries/repositories, unless the whole project is being retired.
- Keep `--force` mandatory for undeploy commands to reduce accidental deletions.
- Rotate or unset temporary secrets such as `MOCK_API_KEY` after exercises.
- Use the shared deploy/undeploy suite rather than legacy wrappers for all new runbooks.
