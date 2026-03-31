#!/bin/bash
# Build and run the BFA MCP Spike locally.
#
# Usage:
#   ./run-local.sh          # Build and run with Maven
#   ./run-local.sh docker   # Build and run with Docker

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

MODE="${1:-maven}"

echo "=== BFA MCP Spike — Local Run ==="
echo ""

case "${MODE}" in
  maven|mvn)
    echo ">>> Building with Maven..."
    (cd "${REPO_ROOT}" && mvn -pl bfa-mcp-spike -am clean package -DskipTests)

    echo ""
    echo ">>> Starting MCP server on http://localhost:8081 ..."
    echo "    MCP endpoint: http://localhost:8081/mcp"
    echo "    Press Ctrl+C to stop."
    echo ""

    (cd "${REPO_ROOT}" && mvn spring-boot:run -pl bfa-mcp-spike)
    ;;

  docker)
    echo ">>> Building JAR..."
    (cd "${REPO_ROOT}" && mvn -pl bfa-mcp-spike -am clean package -DskipTests)

    echo ">>> Building Docker image..."
    docker build -t bfa-mcp-spike:local "${SCRIPT_DIR}"

    echo ""
    echo ">>> Starting container on http://localhost:8081 ..."
    echo "    MCP endpoint: http://localhost:8081/mcp"
    echo "    Press Ctrl+C to stop."
    echo ""

    docker run --rm -p 8081:8081 -e PORT=8081 bfa-mcp-spike:local
    ;;

  *)
    echo "Usage: $0 [maven|docker]"
    exit 1
    ;;
esac
