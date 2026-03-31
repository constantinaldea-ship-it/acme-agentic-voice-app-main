#!/bin/bash
# =============================================================================
# Advisory Appointment — Mock-Server Smoke Test
# =============================================================================
# Exercises all advisory-appointment WireMock stubs directly against the
# mock-server (port 8080 by default).  Verifies taxonomy, service search,
# eligibility, availability, and the full create → get → reschedule → cancel
# lifecycle scenario chain.
#
# Usage:
#   ./test-advisory-appointments.sh              # localhost:8080
#   ./test-advisory-appointments.sh https://mock-server.example.com
#
# Prerequisites:
#   - mock-server running (./run.sh or mvn spring-boot:run)
#   - curl, jq
# =============================================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
AUTH="Bearer mock-appointment-service-token"
CLIENT="advisory-appointment-bff"
CORR="test-$(date +%s)"

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

header_args=(
  -H "Authorization: $AUTH"
  -H "X-BFA-Client: $CLIENT"
  -H "X-Correlation-ID: $CORR"
)

echo "=============================================="
echo "  Advisory Appointment — Mock-Server Tests"
echo "=============================================="
echo "  Base URL : $BASE_URL"
echo "  Corr-ID  : $CORR"
echo ""

# ---------------------------------------------------------------------------
# 1. Taxonomy
# ---------------------------------------------------------------------------
echo "--- 1. Taxonomy ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/taxonomy")
check "GET /advisory-appointments/taxonomy" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 2. Service Search
# ---------------------------------------------------------------------------
echo "--- 2. Service Search ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/service-search?query=standing+order")
check "GET service-search (standing order)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/service-search?query=mortgage")
check "GET service-search (mortgage)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/service-search?query=xyznonexistent")
check "GET service-search (no match)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 3. Eligibility
# ---------------------------------------------------------------------------
echo "--- 3. Eligibility ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/eligibility?consultationChannel=BRANCH&city=berlin")
check "GET eligibility (Berlin branch)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/eligibility?consultationChannel=PHONE")
check "GET eligibility (phone remote)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/eligibility?consultationChannel=VIDEO")
check "GET eligibility (video remote)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 4. Availability
# ---------------------------------------------------------------------------
echo "--- 4. Availability ---"
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/availability?consultationChannel=BRANCH&locationId=20286143")
check "GET availability (branch default days)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/availability?consultationChannel=BRANCH&locationId=20286143&selectedDay=2030-06-18")
check "GET availability (branch day 2030-06-18)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/availability?consultationChannel=PHONE&locationId=REMOTE-PHONE-DE&selectedDay=2030-06-18")
check "GET availability (phone day 2030-06-18)" 200 "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/availability?consultationChannel=VIDEO&locationId=REMOTE-VIDEO-DE&selectedDay=2030-06-18")
check "GET availability (video day 2030-06-18)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 5. Lifecycle (scenario state machine)
# ---------------------------------------------------------------------------
echo "--- 5. Lifecycle (create → get → reschedule → get → cancel → get) ---"

# Reset WireMock scenarios to initial state
curl -s -o /dev/null -X POST "$BASE_URL/__admin/scenarios/reset"
echo "  🔄 WireMock scenarios reset to Started"

# 5a. Create
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  -H "Content-Type: application/json" \
  -H "X-Mock-Scenario: branch-lifecycle" \
  -X POST "$BASE_URL/advisory-appointments/lifecycle")
check "POST create appointment (Started → BOOKED)" 200 "$STATUS"

# 5b. Get (BOOKED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/lifecycle/APT-BRANCH-0001?appointmentAccessToken=aat-branch-0001")
check "GET appointment (BOOKED)" 200 "$STATUS"

# 5c. Reschedule (BOOKED → RESCHEDULED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/advisory-appointments/lifecycle/APT-BRANCH-0001/reschedule")
check "POST reschedule (BOOKED → RESCHEDULED)" 200 "$STATUS"

# 5d. Get (RESCHEDULED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/lifecycle/APT-BRANCH-0001?appointmentAccessToken=aat-branch-0001")
check "GET appointment (RESCHEDULED)" 200 "$STATUS"

# 5e. Cancel (RESCHEDULED → CANCELLED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  -H "Content-Type: application/json" \
  -X POST "$BASE_URL/advisory-appointments/lifecycle/APT-BRANCH-0001/cancel")
check "POST cancel (RESCHEDULED → CANCELLED)" 200 "$STATUS"

# 5f. Get (CANCELLED)
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  "${header_args[@]}" \
  "$BASE_URL/advisory-appointments/lifecycle/APT-BRANCH-0001?appointmentAccessToken=aat-branch-0001")
check "GET appointment (CANCELLED)" 200 "$STATUS"

# ---------------------------------------------------------------------------
# 6. Error / Negative Cases
# ---------------------------------------------------------------------------
echo "--- 6. Error Cases ---"

# Missing authorization
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "X-BFA-Client: $CLIENT" \
  -H "X-Correlation-ID: $CORR" \
  "$BASE_URL/advisory-appointments/taxonomy")
check "GET taxonomy (missing auth → 401)" 401 "$STATUS"

# Missing client header
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: $AUTH" \
  -H "X-Correlation-ID: $CORR" \
  "$BASE_URL/advisory-appointments/taxonomy")
check "GET taxonomy (missing client → 403)" 403 "$STATUS"

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
