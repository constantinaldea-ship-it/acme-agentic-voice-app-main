#!/bin/bash

# Demo Account Mock Server - Startup Script
# This script starts the WireMock mock server on port 8080

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "  Demo Account Mock Server"
echo "=========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed or not in PATH"
    exit 1
fi

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed or not in PATH"
    exit 1
fi

echo "Starting WireMock server on port 8080..."
echo ""
echo "Available endpoints:"
echo "  GET /customers/{partnerId}/personal-data"
echo "  Required header: deuba-client-id (must contain '-banking')"
echo ""
echo "Example:"
echo "  curl -X GET http://localhost:8080/customers/6585363429/personal-data \\"
echo "    -H 'deuba-client-id: pb-banking' \\"
echo "    -H 'Accept: application/json'"
echo ""
echo "Press Ctrl+C to stop the server"
echo "=========================================="
echo ""

mvn spring-boot:run
