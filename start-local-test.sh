#!/bin/bash
# Start local MCP server for testing with Claude CLI/Desktop

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Check for required environment variables
if [ -z "$TANZU_PLATFORM_URL" ] || [ -z "$TOKEN" ]; then
    echo "╔══════════════════════════════════════════════════════════════════╗"
    echo "║  ERROR: Required environment variables not set                   ║"
    echo "╠══════════════════════════════════════════════════════════════════╣"
    echo "║  Please set the following variables:                             ║"
    echo "║                                                                  ║"
    echo "║  export TANZU_PLATFORM_URL='https://tanzu-hub.kuhn-labs.com'     ║"
    echo "║  export TOKEN='your-bearer-token-here'                           ║"
    echo "╚══════════════════════════════════════════════════════════════════╝"
    exit 1
fi

echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║  Tanzu Platform MCP Server - Local Testing                       ║"
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "Platform URL: $TANZU_PLATFORM_URL"
echo "Token: ${TOKEN:0:20}..."
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

