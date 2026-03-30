#!/bin/bash
# Start local MCP server for testing with Claude CLI/Desktop

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check for required environment variables
if [ -z "$TANZU_PLATFORM_URL" ]; then
    echo "╔══════════════════════════════════════════════════════════════════╗"
    echo "║  ERROR: TANZU_PLATFORM_URL not set                              ║"
    echo "╠══════════════════════════════════════════════════════════════════╣"
    echo "║  export TANZU_PLATFORM_URL='https://tanzu-hub.kuhn-labs.com'    ║"
    echo "║                                                                  ║"
    echo "║  For auth, set ONE of:                                           ║"
    echo "║  - BROKER_URL + BROKER_DELEGATION_TOKEN (production)             ║"
    echo "║  - TANZU_PLATFORM_FALLBACK_TOKEN (local dev)                     ║"
    echo "╚══════════════════════════════════════════════════════════════════╝"
    exit 1
fi

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║  Tanzu Platform MCP Server - Local Testing                       ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Platform URL: $TANZU_PLATFORM_URL"
if [ -n "$BROKER_URL" ]; then
    echo "Auth: Credential Broker at $BROKER_URL"
elif [ -n "$TANZU_PLATFORM_FALLBACK_TOKEN" ]; then
    echo "Auth: Fallback token (${TANZU_PLATFORM_FALLBACK_TOKEN:0:20}...)"
else
    echo "Auth: WARNING - no broker or fallback token configured"
fi
echo ""

# Build if needed
JAR_PATH="hub-mcp/target/hub-mcp-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "📦 Building MCP server..."
    cd hub-mcp && ./mvnw package -DskipTests -q && cd ..
    echo "✅ Build complete"
    echo ""
fi

# Start the server
echo "🚀 Starting MCP server..."
echo ""
echo "   MCP Endpoint:  http://localhost:8080/mcp"
echo "   Health Check:  http://localhost:8080/actuator/health"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📝 To test with Claude CLI:"
echo ""
echo "   1. Open a new terminal"
echo "   2. cd $SCRIPT_DIR"
echo "   3. claude"
echo ""
echo "   The .mcp.json file will automatically configure the MCP connection."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "Press Ctrl+C to stop the server"
echo ""

java -jar "$JAR_PATH"

