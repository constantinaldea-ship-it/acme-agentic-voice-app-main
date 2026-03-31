# Quick Reference: Working Deployed Endpoints

**Base URL:** `https://bfa-service-resource-6gxbppksrq-uc.a.run.app`

## ✅ Fully Tested & Working

### Health & Docs (No Auth Required)
```bash
# Health check
curl https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/health

# OpenAPI spec
curl https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api-docs/location-services

# Swagger UI
open https://bfa-service-resource-6gxbppksrq-uc.a.run.app/swagger-ui.html
```

### Location Services (Auth Required) 🌟
```bash
export TOKEN="your-jwt-token-here"

# Search by city
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/branches?city=Frankfurt&limit=3"

# Search by postal code
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/branches?postalCode=60311"

# GPS search (5km radius)
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/branches?latitude=50.1109&longitude=8.6821&radiusKm=5"

# Wheelchair accessible only
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/branches?city=Berlin&accessible=true"

# Get branch details
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/branches/686496"
```

### Accounts (Auth Required)
```bash
# List all accounts
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/accounts"

# Get account details
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/accounts/ACC-001"

# Get balance
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/accounts/ACC-001/balance"

# Get transactions
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/accounts/ACC-001/transactions"
```

### Credit Cards (Auth + Consents Required)
```bash
# List all cards
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/cards"

# Get card details
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/cards/CARD-001"

# Get card balance
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/cards/CARD-001/balance"

# Get credit limit (requires VIEW_CARD_LIMIT consent)
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/cards/CARD-001/limit"

# Get rewards (requires VIEW_REWARDS consent)
curl -H "Authorization: Bearer $TOKEN" \
  "https://bfa-service-resource-6gxbppksrq-uc.a.run.app/api/v1/cards/CARD-001/rewards"
```

## ⚠️ Known Issues (Returning 500)

### Personal Finance
- `/api/v1/finance/spending/breakdown`
- `/api/v1/finance/spending/trend`
- `/api/v1/finance/spending/merchants`
- `/api/v1/finance/recurring/detected`
- `/api/v1/finance/anomalies/unusual`

### Non-Banking Services
- `/api/v1/services/benefits`
- `/api/v1/services/insurance`
- `/api/v1/services/travel`
- `/api/v1/services/offers`
- `/api/v1/services/contacts`

## 🎯 Integration Ready

**Location Services are production-ready** and can be integrated with the CES agent immediately:

```yaml
# ces-agent/agents/location-services-agent.yaml
parameters:
  - name: BFA_SERVICE_URL
    value: "https://bfa-service-resource-6gxbppksrq-uc.a.run.app"
```

## 📊 Test Script

```bash
cd /Users/constantinaldea/IdeaProjects/ai-account-balance/java/bfa-service-resource
./test-deployed-service-enhanced.sh
```
