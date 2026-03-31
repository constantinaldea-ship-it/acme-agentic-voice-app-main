# Agent Context Headers — Implementation Summary

## Deliverables

### ✅ Task 1: BFA Service Audit Logging Enhancement

**Files Modified:**
- [AuditService.java](src/main/java/com/voicebanking/bfa/service/AuditService.java)
- [AuditInterceptor.java](src/main/java/com/voicebanking/bfa/interceptor/AuditInterceptor.java)

**Changes:**
1. Added `toolId` and `sessionId` parameters to `logRequestStart()` 
2. Added `agentId`, `toolId`, and `sessionId` parameters to `logRequestComplete()`
3. Updated `AuditRecord` with `toolId` and `sessionId` fields
4. Enhanced audit log format to include all agent context

**Build Status:** ✅ Compiled and tested (68/69 tests pass; 1 pre-existing data failure)

### ✅ Task 2: CES Agent Client Enhancement

**Files Modified:**
- [open_api_schema.yaml](../../../ces-agent/acme_voice_agent/toolsets/location/open_api_toolset/open_api_schema.yaml)
- [test-tool-call.py](../../../ces-agent/scripts/test-tool-call.py)

**Changes:**
1. Added 4 header parameters to both `searchBranches` and `getBranch` operations:
   - `X-Correlation-ID` — request trace ID (auto-generated if missing)
   - `X-Agent-Id` — defaults to `"ces-agent"`
   - `X-Tool-Id` — defaults to operation name
   - `X-Session-ID` — Dialogflow CX session ID

2. Updated test script to send all agent context headers

**Package Status:** ✅ Validated and packaged: `acme-voice-agent-audit-headers-20260208-153545.zip`

---

## Deployment Status

### BFA Service

**Current URL:** https://bfa-service-resource-504803821129.europe-west3.run.app

**Status:** ⏳ **Ready to Deploy** (built but not deployed due to Cloud Build permissions)

**Build Output:** 
- JAR: `target/bfa-service-resource-0.1.0-SNAPSHOT.jar` ✅
- Dockerfile: Created ✅
- Cloud Build: Permission error (requires manual deployment)

**Deployment Options:**

1. **Via Cloud Console:**
   - Go to Cloud Run → bfa-service-resource
   - Deploy from source (upload directory)
   - Set environment: `BFA_SECURITY_ENABLED=false`, `SPRING_PROFILES_ACTIVE=prod`

2. **Via gcloud (with proper permissions):**
   ```bash
   cd java/bfa-service-resource
   gcloud run deploy bfa-service-resource --source . --region us-central1
   ```

3. **Via Local Docker:**
   ```bash
   cd java/bfa-service-resource
   docker build -t bfa-service-resource:latest .
   docker tag bfa-service-resource:latest us-central1-docker.pkg.dev/voice-banking-poc/voice-banking/bfa-service-resource:latest
   docker push us-central1-docker.pkg.dev/voice-banking-poc/voice-banking/bfa-service-resource:latest
   gcloud run deploy bfa-service-resource --image us-central1-docker.pkg.dev/voice-banking-poc/voice-banking/bfa-service-resource:latest --region us-central1
   ```

### CES Agent Package

**Location:** `ces-agent/acme-voice-agent-audit-headers-20260208-153545.zip`

**Import to Dialogflow CX:**
1. Open Dialogflow CX Console: https://dialogflow.cloud.google.com/cx/
2. Select your agent (Acme Voice Agent)
3. Go to Agent Studio
4. Click "Import"
5. Upload the ZIP file
6. Verify header parameters in toolset configuration

---

## Testing Instructions

### 1. Test Agent Context Headers

```bash
cd java/bfa-service-resource
./test-agent-headers.sh https://bfa-service-resource-504803821129.europe-west3.run.app test
```

**Expected:** Both searches return results with matching correlation IDs in response metadata.

### 2. Verify Audit Logs

```bash
gcloud logging read \
  'resource.type=cloud_run_revision
   AND resource.labels.service_name=bfa-service-resource
   AND jsonPayload.logger=AUDIT' \
  --limit 20 \
  --format=json \
  | jq -r '.[] | [.timestamp, .jsonPayload.message] | @tsv'
```

**Expected log format:**
```
REQUEST_START | corr={uuid} | user={user} | agent=ces-agent | tool=searchBranches | session=test-session-001 | op={operation} | GET {path}
REQUEST_COMPLETE | corr={uuid} | user={user} | agent=ces-agent | tool=searchBranches | session=test-session-001 | op={operation} | outcome=SUCCESS | status=200 | duration={ms}ms
```

### 3. CES Agent Test (After Import)

Use the Dialogflow CX simulator or test webhook:
```
User: "Find branches in Munich"
```

Check Cloud Logging for requests from CES agent with:
- `agent=ces-agent`
- `tool=searchBranches`
- `session={dialogflow-session-id}`

---

## Architecture Notes

### Why Dialogflow CX Doesn't Need Custom Client Code

The CES agent is a **Dialogflow CX agent** that uses **managed OpenAPI toolsets**. HTTP calls are made by Dialogflow's runtime, not custom code. To propagate headers:

1. **Declare headers in OpenAPI schema** — Any `in: header` parameters are automatically sent by CX
2. **Set defaults for static values** — `X-Agent-Id` defaults to `"ces-agent"`
3. **Tool ID is injected** — CX sets `X-Tool-Id` to the operation name
4. **Session ID from context** — CX passes its session ID via `X-Session-Id`

This is different from the voice-banking-app's `BfaResourceClient.java`, which is a custom REST client that manually adds headers from MDC.

---

## Documentation Updates

**New Files:**
- [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md) — Detailed deployment instructions
- [test-agent-headers.sh](test-agent-headers.sh) — Test script for header propagation
- [Dockerfile](Dockerfile) — Container definition for Cloud Run

**Updated Files:**
- [QUICK-REFERENCE.md](QUICK-REFERENCE.md) — No changes needed (endpoints unchanged)
- [README.md](README.md) — No changes needed (API contracts unchanged)

---

## Next Steps

1. **Deploy BFA Service** (requires permissions or manual deployment via Console)
2. **Import CES Agent Package** to Dialogflow CX
3. **Run Test Script** to verify audit logging
4. **Check Cloud Logging** for agent context headers
5. **Test in Dialogflow CX Simulator** with branch search queries

---

## Files Ready for Review

### Java (BFA Service)
- ✅ `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/service/AuditService.java`
- ✅ `java/bfa-service-resource/src/main/java/com/voicebanking/bfa/interceptor/AuditInterceptor.java`
- ✅ `java/bfa-service-resource/Dockerfile`
- ✅ `java/bfa-service-resource/test-agent-headers.sh`
- ✅ `java/bfa-service-resource/DEPLOYMENT-GUIDE.md`

### CES Agent
- ✅ `ces-agent/acme_voice_agent/toolsets/location/open_api_toolset/open_api_schema.yaml`
- ✅ `ces-agent/scripts/test-tool-call.py`
- ✅ `ces-agent/acme-voice-agent-audit-headers-20260208-153545.zip` (23 KB)

---

## Questions?

See [DEPLOYMENT-GUIDE.md](DEPLOYMENT-GUIDE.md) for troubleshooting and detailed deployment steps.
