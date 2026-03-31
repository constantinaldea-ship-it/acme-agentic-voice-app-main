#!/bin/bash
# =============================================================================
# Advisory Appointment — BFA Service Resource Smoke Test
# =============================================================================
# Exercises all appointment endpoints on the BFA service-resource layer
# (default port 8081).  The BFA proxies to the mock-server upstream, so
# the mock-server must be running (default port 8080) before this script.
#
# Usage:
#   ./test-advisory-appointments.sh              # localhost:8081
#   ./test-advisory-appointments.sh http://localhost:8081
#
# Prerequisites:
#   - mock-server running on port 8080  (cd ../mock-server && ./run.sh)
#   - bfa-service-resource running       (mvn spring-boot:run)
#   - curl, jq
# =============================================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8081}"
API="$BASE_URL/api/v1"
AUTH="Bearer mock-appointment-service-token"
CORR="bfa-test-$(date +%s)"

PASS=0
FAIL=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
check() {
  local label="$1" expected_status="$2" actual_status="$3"
  if [[ "$actual_status" == "$expected_status" ]]; then
    echo "  ✅ $label  (HTTP $actual_status)"
    PASS=$((PASS + 1))
  else
    echo "  ❌ $label  (expected $expected_status, got $actual_status)"
    FAIL=$((FAIL + 1))
  fi
}

# ---------------------------------------------------------------------------
echo "=============================================="
echo "  Advisory Appointment — BFA Smoke Test"
echo "=============================================="
echo "  BFA URL  : $BASE_URL"
echo "  API base : $API"
echo "  Corr-ID  : $CORR"
echo ""

# ---------------------------------------------------------------------------
# 1. Taxonomy
# ---------------------------------------------------------------------------
echo "--- 1. Taxonomy ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-taxonomy")
check "GET /api/v1/appointment-taxonomy" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 2. Service Search
# ---------------------------------------------------------------------------
echo "--- 2. Service Search ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-service-search?query=standing+order")
check "GET service-search (standing order)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-service-search?query=mortgage")
check "GET service-search (mortgage)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 3. Branches (Eligibility)
# ---------------------------------------------------------------------------
echo "--- 3. Branches (Eligibility) ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-branches?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&city=berlin")
check "GET branches (BRANCH Berlin)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-branches?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=PHONE")
check "GET branches (PHONE)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-branches?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=VIDEO")
check "GET branches (VIDEO)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 4. Slots (Availability)
# ---------------------------------------------------------------------------
echo "--- 4. Slots (Availability) ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&locationId=20286143")
check "GET slots (branch default)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=BRANCH&locationId=20286143&selectedDay=2030-06-18")
check "GET slots (branch day 2030-06-18)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=PHONE&locationId=REMOTE-PHONE-DE&selectedDay=2030-06-18")
check "GET slots (phone day)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointment-slots?entryPath=SERVICE_REQUEST&serviceCode=standing-order&consultationChannel=VIDEO&locationId=REMOTE-VIDEO-DE&selectedDay=2030-06-18")
check "GET slots (video day)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 5. Lifecycle (create → get → reschedule → get → cancel → get)
# ---------------------------------------------------------------------------
echo "--- 5. Lifecycle Flow ---"

# BFA manages appointments in-memory after validating slots against upstream.
# We must capture the dynamic appointmentId and appointmentAccessToken from
# the create response, and use real slot IDs from the slots endpoint.

SLOT_CREATE="SLOT-BRANCH-20286143-20300618-0930"
SLOT_RESCHEDULE="SLOT-BRANCH-20286143-20300618-1430"

# 5a. Create (expects 201 Created)
CREATE_RESP=$(curl -s -w "\n%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  -H "Content-Type: application/json" \
  -d "{
    \"entryPath\": \"SERVICE_REQUEST\",
    \"consultationChannel\": \"BRANCH\",
    \"serviceCode\": \"standing-order\",
    \"locationId\": \"20286143\",
    \"selectedDay\": \"2030-06-18\",
    \"selectedTimeSlotId\": \"$SLOT_CREATE\",
    \"customer\": {
      \"salutation\": \"HERR\",
      \"firstName\": \"Hans\",
      \"lastName\": \"Mueller\",
      \"email\": \"hans.mueller@example.com\",
      \"phone\": \"+49 800 1234567\",
      \"isExistingCustomer\": true
    },
    \"summaryConfirmed\": true
  }" \
  -X POST "$API/appointments")
CREATE_STATUS=$(echo "$CREATE_RESP" | tail -1)
CREATE_BODY=$(echo "$CREATE_RESP" | sed '$d')
check "POST create appointment" 201 "$CREATE_STATUS"

# Extract dynamic IDs
APT_ID=$(echo "$CREATE_BODY" | jq -r '.data.appointment.appointmentId')
APT_TOKEN=$(echo "$CREATE_BODY" | jq -r '.data.appointmentAccessToken')
echo "  → appointmentId=$APT_ID  token=$APT_TOKEN"

# 5b. Get (CONFIRMED / BOOKED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointments/$APT_ID?appointmentAccessToken=$APT_TOKEN")
check "GET appointment (CONFIRMED)" 200 "$STATUS"

# 5c. Reschedule (CONFIRMED → RESCHEDULED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  -H "Content-Type: application/json" \
  -d "{
    \"appointmentAccessToken\": \"$APT_TOKEN\",
    \"selectedDay\": \"2030-06-18\",
    \"selectedTimeSlotId\": \"$SLOT_RESCHEDULE\",
    \"summaryConfirmed\": true
  }" \
  -X POST "$API/appointments/$APT_ID/reschedule")
check "POST reschedule appointment" 200 "$STATUS"

# 5d. Get (RESCHEDULED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointments/$APT_ID?appointmentAccessToken=$APT_TOKEN")
check "GET appointment (RESCHEDULED)" 200 "$STATUS"

# 5e. Cancel (RESCHEDULED → CANCELLED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  -H "Content-Type: application/json" \
  -d "{
    \"appointmentAccessToken\": \"$APT_TOKEN\",
    \"reason\": \"schedule conflict\",
    \"summaryConfirmed\": true
  }" \
  -X POST "$API/appointments/$APT_ID/cancel")
check "POST cancel appointment" 200 "$STATUS"

# 5f. Get (CANCELLED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$API/appointments/$APT_ID?appointmentAccessToken=$APT_TOKEN")
check "GET appointment (CANCELLED)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
echo ""
echo "=============================================="
TOTAL=$((PASS + FAIL))
echo "  Results: $PASS/$TOTAL passed"
if [[ $FAIL -gt 0 ]]; then
  echo "  ⚠️  $FAIL test(s) FAILED"
  exit 1
else
  echo "  🎉 All tests passed!"
fi
echo "=============================================="
