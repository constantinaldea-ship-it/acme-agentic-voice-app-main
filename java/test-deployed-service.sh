#!/bin/bash

# Integration Test Script for Deployed BFA Service
# Service URL: https://bfa-service-resource-6gxbppksrq-uc.a.run.app
# Created: 2026-02-07

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Base URL for deployed service
BASE_URL="https://bfa-service-resource-6gxbppksrq-uc.a.run.app"

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

# Generate a test JWT token (mock for testing)
# In production, this would come from an auth service
generate_test_token() {
    # Simple base64-encoded JWT-like token for testing
    # Header
    HEADER='{"alg":"HS256","typ":"JWT"}'
    # Payload with test user
    PAYLOAD='{"sub":"test-user-12345","userId":"test-user-12345","name":"Test User","email":"test@acmebank.example","iat":'$(date +%s)',"exp":'$(($(date +%s) + 3600))',"aud":"bfa-service","iss":"test-auth"}'
    
    # Base64 encode
    HEADER_B64=$(echo -n "$HEADER" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    PAYLOAD_B64=$(echo -n "$PAYLOAD" | base64 | tr -d '=' | tr '/+' '_-' | tr -d '\n')
    
    # For testing, we'll use a mock signature
    SIGNATURE="mock-signature-for-testing-only"
    
    echo "${HEADER_B64}.${PAYLOAD_B64}.${SIGNATURE}"
}

# Get auth token
AUTH_TOKEN=$(generate_test_token)
echo -e "${YELLOW}Generated test token for authentication${NC}"
echo "Token: ${AUTH_TOKEN:0:50}..."
echo ""

# Function to print section headers
print_section() {
    echo ""
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}"
}

# Function to test an endpoint
test_endpoint() {
    local description=$1
    local url=$2
    local expected_code=${3:-200}
    local use_auth=${4:-true}
    
    echo ""
    echo -e "${YELLOW}Testing: $description${NC}"
    echo "URL: $url"
    
    # Make request and capture response code
    if [ "$use_auth" = "true" ]; then
        response=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $AUTH_TOKEN" "$url")
    else
        response=$(curl -s -w "\n%{http_code}" "$url")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" -eq "$expected_code" ]; then
        echo -e "${GREEN}✓ PASSED${NC} (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        
        # Pretty print JSON if available (first 500 chars)
        if command -v jq &> /dev/null && echo "$body" | jq -e . >/dev/null 2>&1; then
            echo "$body" | jq -C '.' | head -c 500
            echo ""
        else
            echo "$body" | head -c 500
            echo ""
        fi
    else
        echo -e "${RED}✗ FAILED${NC} (Expected $expected_code, got $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo "Response:"
        echo "$body" | head -c 500
        echo ""
    fi
}

# Start tests
echo -e "${GREEN}Starting Integration Tests for BFA Service${NC}"
echo "Base URL: $BASE_URL"
echo "Timestamp: $(date)"

# ========================================
# Health Check
# ========================================
print_section "Health Check"
test_endpoint "Health endpoint" \
    "$BASE_URL/api/v1/health" 200 false

# ========================================
# Location Services (Branch Locator)
# ========================================
print_section "Location Services - Branch Locator"

test_endpoint "Search branches by city (Frankfurt)" \
    "$BASE_URL/api/v1/branches?city=Frankfurt&limit=3"

test_endpoint "Search branches by postal code" \
    "$BASE_URL/api/v1/branches?postalCode=60311&limit=2"

test_endpoint "Search branches with brand filter" \
    "$BASE_URL/api/v1/branches?city=Hamburg&brand=Deutsche%20Bank"

test_endpoint "Search branches by GPS coordinates" \
    "$BASE_URL/api/v1/branches?latitude=50.1109&longitude=8.6821&radiusKm=5"

test_endpoint "Search wheelchair accessible branches" \
    "$BASE_URL/api/v1/branches?city=Berlin&accessible=true"

test_endpoint "Get specific branch details" \
    "$BASE_URL/api/v1/branches/686496"

# ========================================
# Account Endpoints
# ========================================
print_section "Account Endpoints"

test_endpoint "List all accounts" \
    "$BASE_URL/api/v1/accounts"

test_endpoint "Get specific account details" \
    "$BASE_URL/api/v1/accounts/ACC001"

test_endpoint "Get account balance" \
    "$BASE_URL/api/v1/accounts/ACC001/balance"

test_endpoint "Get account transactions" \
    "$BASE_URL/api/v1/accounts/ACC001/transactions"

# ========================================
# Credit Card Endpoints
# ========================================
print_section "Credit Card Endpoints"

test_endpoint "List all cards" \
    "$BASE_URL/api/v1/cards"

test_endpoint "Get specific card details" \
    "$BASE_URL/api/v1/cards/CARD001"

test_endpoint "Get card balance" \
    "$BASE_URL/api/v1/cards/CARD001/balance"

test_endpoint "Get card transactions" \
    "$BASE_URL/api/v1/cards/CARD001/transactions"

test_endpoint "Get credit limit information" \
    "$BASE_URL/api/v1/cards/CARD001/limit"

test_endpoint "Get rewards balance" \
    "$BASE_URL/api/v1/cards/CARD001/rewards"

test_endpoint "Get card statement" \
    "$BASE_URL/api/v1/cards/CARD001/statements/2026-01"

# ========================================
# Personal Finance Endpoints
# ========================================
print_section "Personal Finance Endpoints"

test_endpoint "Get spending breakdown by category" \
    "$BASE_URL/api/v1/finance/spending/breakdown?period=THIS_MONTH"

test_endpoint "Get spending trend" \
    "$BASE_URL/api/v1/finance/spending/trend?months=3"

test_endpoint "Get top merchants" \
    "$BASE_URL/api/v1/finance/spending/merchants?period=THIS_MONTH&limit=5"

test_endpoint "Get cashflow summary" \
    "$BASE_URL/api/v1/finance/cashflow/summary?period=THIS_MONTH"

test_endpoint "Get budget status" \
    "$BASE_URL/api/v1/finance/budgets/status"

test_endpoint "Get detected recurring transactions" \
    "$BASE_URL/api/v1/finance/recurring/detected"

test_endpoint "Get unusual activity" \
    "$BASE_URL/api/v1/finance/anomalies/unusual?period=THIS_MONTH"

# ========================================
# Non-Banking Services
# ========================================
print_section "Non-Banking Services"

test_endpoint "Get benefits by card tier" \
    "$BASE_URL/api/v1/services/benefits?cardTier=PLATINUM"

test_endpoint "Get insurance coverage" \
    "$BASE_URL/api/v1/services/insurance?cardTier=PLATINUM"

test_endpoint "Get travel benefits" \
    "$BASE_URL/api/v1/services/travel?cardTier=PLATINUM"

test_endpoint "Get partner offers" \
    "$BASE_URL/api/v1/services/offers?category=DINING"

test_endpoint "Get service contacts" \
    "$BASE_URL/api/v1/services/contacts"

# ========================================
# API Documentation
# ========================================
print_section "API Documentation"

test_endpoint "OpenAPI spec for location services" \
    "$BASE_URL/api-docs/location-services" 200 false

test_endpoint "Swagger UI HTML" \
    "$BASE_URL/swagger-ui.html" 302 false

# ========================================
# Summary
# ========================================
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${GREEN}Tests Passed: $TESTS_PASSED${NC}"
if [ $TESTS_FAILED -gt 0 ]; then
    echo -e "${RED}Tests Failed: $TESTS_FAILED${NC}"
else
    echo -e "${GREEN}Tests Failed: $TESTS_FAILED${NC}"
fi
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
