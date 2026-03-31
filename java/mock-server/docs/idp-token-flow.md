# IDP Token Flow — Mock Server Documentation

## Overview

This document describes the simulated Identity Provider (IDP) token flow implemented in the mock server.
The flow demonstrates a three-step authentication and authorization pattern commonly used in enterprise banking APIs.

## CES Alignment Checklist

For the deployed CES test path, the following values must match between
`ces-agent` and `mock-server`:

| Concern | CES value | Mock-server expectation |
|--------|-----------|-------------------------|
| Base URL | `acme_voice_agent/environment.json` → `toolsets.customer_details.openApiToolset.url` | Cloud Run URL of the deployed mock-server |
| API key | `acme_voice_agent/environment.json` → `toolsets.customer_details.apiKeyConfig.apiKeys[0]` | `MOCK_API_KEY` env var, enforced as `X-API-Key` |
| EIDP client | `ces-agent-service` / `mock-secret` | Accepted by `POST /oauth/token` |
| AuthZ payload | `resource=customers:personal-data`, `action=read` | Accepted by `POST /authz/authorize` |
| Customer headers | `deuba-client-id=pb-banking`, `DB-ID=acme-banking-db-01` | Required by `GET /customers/{partnerId}/personal-data` |

The CES Python wrapper does not call HTTP directly. It calls the attached
OpenAPI toolset operations, so the API-key injection happens through CES
toolset configuration rather than in the wrapper code itself.

## Architecture

```
┌─────────────┐     ┌──────────────────┐     ┌───────────────────┐     ┌──────────────────────┐
│  CES Agent  │────▶│  EIDP /oauth/    │────▶│  AuthZ /authz/    │────▶│  Customer API        │
│  (Python)   │     │  token           │     │  authorize        │     │  /customers/{id}/    │
│             │     │                  │     │                   │     │  personal-data       │
└─────────────┘     └──────────────────┘     └───────────────────┘     └──────────────────────┘
      │                    │                        │                          │
      │  Step 1: POST      │                        │                          │
      │  client_credentials │                        │                          │
      │───────────────────▶│                        │                          │
      │  ◀─ access_token   │                        │                          │
      │                    │                        │                          │
      │  Step 2: POST with Bearer {access_token}    │                          │
      │────────────────────────────────────────────▶│                          │
      │  ◀─ authorization_token                     │                          │
      │                    │                        │                          │
      │  Step 3: GET with Authorization, DB-ID, deuba-client-id               │
      │──────────────────────────────────────────────────────────────────────▶│
      │  ◀─ customer personal data (JSON)                                     │
```

## Endpoints

### 1. EIDP Token Endpoint

**URL:** `POST /oauth/token`

**Purpose:** Simulates an Enterprise Identity Provider issuing access tokens via OAuth2 client credentials grant.

#### Request (form-urlencoded)

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=ces-agent-service&client_secret=mock-secret"
```

#### Request (JSON alternative)

```bash
curl -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/json" \
  -d '{"grant_type": "client_credentials", "client_id": "ces-agent-service", "client_secret": "mock-secret"}'
```

#### Success Response (200)

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzd...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read:customers read:accounts"
}
```

#### Error Response (400) — Missing grant_type

```json
{
  "error": "invalid_request",
  "error_description": "Missing or invalid grant_type. Expected client_credentials."
}
```

---

### 2. AuthZ Authorization Endpoint

**URL:** `POST /authz/authorize`

**Purpose:** Simulates an Authorization service that exchanges an EIDP access token for a resource-specific authorization token.

#### Request

```bash
curl -X POST http://localhost:8080/authz/authorize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <eidp_access_token>" \
  -d '{"resource": "customers:personal-data", "action": "read"}'
```

#### Success Response (200)

```json
{
  "authorized": true,
  "authorization_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzd...",
  "permissions": ["read:customers", "read:personal-data"],
  "expires_in": 1800
}
```

#### Error Response (401) — Missing Authorization header

```json
{
  "error": "unauthorized",
  "error_description": "Missing or invalid Authorization header. A valid EIDP Bearer token is required."
}
```

---

### 3. Customer Personal Data Endpoint

**URL:** `GET /customers/{partnerId}/personal-data`

**Purpose:** Returns customer personal data. Now requires three mandatory headers.

#### Required Headers

| Header | Value | Description |
|--------|-------|-------------|
| `deuba-client-id` | Must contain `-banking` (e.g., `pb-banking`) | Client identifier |
| `DB-ID` | Any non-empty value (e.g., `acme-banking-db-01`) | Database identifier |
| `Authorization` | `Bearer <authz_token>` | AuthZ token from step 2 |

#### Request

```bash
curl -X GET http://localhost:8080/customers/1234567890/personal-data \
  -H "deuba-client-id: pb-banking" \
  -H "DB-ID: acme-banking-db-01" \
  -H "Authorization: Bearer <authz_token>" \
  -H "Accept: application/json"
```

#### Success Response (200) — Maria Musterfrau (partnerId: 1234567890)

```json
{
  "firstname": "Maria",
  "lastname": "Musterfrau",
  "academicTitle": "Dr.",
  "fullName": "Dr. Maria Musterfrau",
  "id": 1234567890,
  "gender": "FEMALE",
  "nationality": "DEU",
  ...
}
```

#### Success Response (200) — Hans Müller (any other partnerId)

```json
{
  "firstname": "Hans",
  "lastname": "Müller",
  "fullName": "Hans Müller",
  "id": 1234567891,
  "gender": "MALE",
  "nationality": "DEU",
  ...
}
```

#### Error Responses

| Status | Condition | Body |
|--------|-----------|------|
| 401 | Missing `Authorization` header | `{"error": "unauthorized", "error_description": "..."}` |
| 403 | Missing `DB-ID` header | `{"error": "forbidden", "error_description": "..."}` |
| 404 | Missing or invalid `deuba-client-id` header | (WireMock default: no matching stub) |

---

## Full Flow Example (curl)

```bash
# Step 1: Get EIDP token
EIDP_RESPONSE=$(curl -s -X POST http://localhost:8080/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=ces-agent-service&client_secret=mock-secret")

EIDP_TOKEN=$(echo "$EIDP_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['access_token'])")
echo "EIDP Token: ${EIDP_TOKEN:0:50}..."

# Step 2: Get AuthZ token
AUTHZ_RESPONSE=$(curl -s -X POST http://localhost:8080/authz/authorize \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EIDP_TOKEN" \
  -d '{"resource": "customers:personal-data", "action": "read"}')

AUTHZ_TOKEN=$(echo "$AUTHZ_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['authorization_token'])")
echo "AuthZ Token: ${AUTHZ_TOKEN:0:50}..."

# Step 3: Get customer personal data
curl -s -X GET http://localhost:8080/customers/1234567890/personal-data \
  -H "deuba-client-id: pb-banking" \
  -H "DB-ID: acme-banking-db-01" \
  -H "Authorization: Bearer $AUTHZ_TOKEN" \
  -H "Accept: application/json" | python3 -m json.tool
```

## Test Cases

### Happy Path
1. **EIDP Token** → POST /oauth/token with valid client_credentials → 200 + token
2. **AuthZ Token** → POST /authz/authorize with EIDP Bearer token → 200 + authorization_token
3. **Customer Data (Maria)** → GET /customers/1234567890/personal-data with all headers → 200
4. **Customer Data (Hans)** → GET /customers/any-id/personal-data with all headers → 200

### Negative Cases
5. **EIDP: Missing grant_type** → POST /oauth/token without grant_type → 400
6. **AuthZ: Missing Bearer token** → POST /authz/authorize without Authorization → 401
7. **Customer: Missing Authorization** → GET /customers/{id}/personal-data without Authorization → 401
8. **Customer: Missing DB-ID** → GET /customers/{id}/personal-data without DB-ID → 403
9. **Customer: Missing deuba-client-id** → GET /customers/{id}/personal-data without deuba-client-id → 404
10. **Customer: Invalid deuba-client-id** → GET /customers/{id}/personal-data with invalid client-id → 404

## WireMock Stub Priority Order

| Priority | File | Match Rule |
|----------|------|------------|
| 4 | max-mustermann.json | Wildcard partnerId + all 3 headers |
| 5 | maria-musterfrau.json | partnerId=1234567890 + all 3 headers |
| 5 | token.json | POST /oauth/token + form-urlencoded + grant_type |
| 5 | token-json.json | POST /oauth/token + JSON + grant_type |
| 5 | authorize.json | POST /authz/authorize + Bearer + resource + action |
| 5 | authorize-minimal.json | POST /authz/authorize + Bearer + resource |
| 6 | missing-authorization.json | GET /customers/*/personal-data + no Authorization |
| 6 | missing-db-id.json | GET /customers/*/personal-data + no DB-ID |
| 8 | authorize-unauthorized.json | POST /authz/authorize + no Authorization |
| 10 | token-invalid.json | POST /oauth/token (catch-all fallback) |
