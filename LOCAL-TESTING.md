# Local Testing Guide: Tanzu Platform MCP Server + Skill

This guide walks you through testing the MCP server and skill locally before deploying to Cloud Foundry.

## Prerequisites

- Java 21+
- Maven (or use the included `mvnw` wrapper)
- Claude CLI (`claude`) or Claude Desktop
- Access to Tanzu Platform (URL and either a credential broker or fallback token)

## Environment Setup

### 1. Set Environment Variables

```bash
# Required
export TANZU_PLATFORM_URL="https://tanzu-hub.kuhn-labs.com"

# For local dev (without credential broker):
export TANZU_PLATFORM_FALLBACK_TOKEN="your-bearer-token-here"
```

To get a fallback token for local dev:
1. Log into Tanzu Platform
2. Open browser developer tools (F12)
3. Go to Network tab
4. Find any GraphQL request to `/hub/graphql`
5. Copy the `Authorization` header value (without "Bearer " prefix)

### 2. Build the MCP Server

```bash
cd hub-mcp
./mvnw clean package -DskipTests
```

This creates `target/hub-mcp-0.0.1-SNAPSHOT.jar`.

## Starting the MCP Server

### Option A: Run with Maven (Development)

```bash
cd hub-mcp
./mvnw spring-boot:run
```

### Option B: Run the JAR (Production-like)

```bash
cd hub-mcp
java -jar target/hub-mcp-0.0.1-SNAPSHOT.jar
```

The server starts on **port 8080** with:
- MCP endpoint: `http://localhost:8080/mcp`
- Health check: `http://localhost:8080/actuator/health`

### Verify Server is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "ping": {"status": "UP"},
    ...
  }
}
```

## Testing with Claude CLI

### 1. MCP Configuration

The project includes `.mcp.json` in the project root:

```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "url",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

### 2. Start Claude CLI with the Skill

From the project root directory:

```bash
# Start Claude CLI from the project root (where .mcp.json is located)
cd /Users/corby/Projects/claude/hub
claude
```

Claude CLI will automatically:
- Load the MCP configuration from `.mcp.json`
- Recognize the skill files in `skills/tanzu-platform/`

### 3. Verify MCP Connection

In Claude CLI, ask:
```
What MCP tools are available?
```

You should see the 6 Tanzu Platform tools:
- `tanzu_graphql_query`
- `tanzu_graphql_mutate`
- `tanzu_explore_schema`
- `tanzu_find_entity_path`
- `tanzu_common_queries`
- `tanzu_validate_query`

## Testing with Claude Desktop

### 1. Configure Claude Desktop

Edit `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "tanzu-platform": {
      "type": "url",
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

### 2. Restart Claude Desktop

Completely quit and restart Claude Desktop for the configuration to take effect.

### 3. Loading the Skill

For Claude Desktop, you'll need to reference the skill manually or copy it to a location Claude Desktop can access. You can:

1. **Reference the skill path** in your prompts:
   ```
   Please read the skill at /Users/corby/Projects/claude/hub/skills/tanzu-platform/SKILL.md before answering questions about Tanzu Platform.
   ```

2. **Copy skill to Claude's skills directory** (if available):
   ```bash
   mkdir -p ~/.claude/skills
   cp -r skills/tanzu-platform ~/.claude/skills/
   ```

## Simple Test Queries

### Test 1: List Available Common Queries

**Prompt:**
```
Use the tanzu_common_queries tool with pattern="list" to show me all available query patterns.
```

**Expected:** A list of 15+ pre-built query patterns.

### Test 2: List TAS Foundations

**Prompt:**
```
List all TAS foundations in the Tanzu Platform.
```

**Expected:** Claude should use either:
- `tanzu_common_queries` with pattern `list_foundations`, or
- `tanzu_graphql_query` with a proper foundation query

### Test 3: Explore Schema

**Prompt:**
```
Use tanzu_explore_schema to show me the Entity_Tanzu_TAS_Foundation_Type schema.
```

**Expected:** Type definition with fields, relationships, and an example query.

### Test 4: Find Entity Path

**Prompt:**
```
How do I navigate from an Application to its Foundation in Tanzu Platform?
```

**Expected:** Claude should use `tanzu_find_entity_path` to find:
- Application → Space → Organization → Foundation (via `relationshipsOut.isContainedIn`)

### Test 5: Validate and Execute Query

**Prompt:**
```
First validate, then execute this query to get the first 5 applications with their names and states:

query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 5) {
              edges {
                node {
                  id
                  properties {
                    name
                    state
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

**Expected:** Claude should:
1. Use `tanzu_validate_query` first
2. Then use `tanzu_graphql_query` to execute

### Test 6: Skill-Guided Query Construction

**Prompt:**
```
I want to find all applications in the "production" organization. How should I structure this query?
```

**Expected:** Claude should:
1. Reference the skill for query structure guidance
2. Possibly use `tanzu_explore_schema` to check available filters
3. Construct a query that navigates from Organization to Applications

## Observing Skill + MCP Interaction

To see how the skill guides Claude's tool usage:

### Scenario 1: Query Without Skill Knowledge

Try asking Claude Desktop (without the skill loaded):
```
List all TAS foundations
```

Claude may construct an incorrect query like:
```graphql
query {
  entityQuery {
    Entity_Tanzu_TAS_Foundation_Type(first: 10) { ... }
  }
}
```

### Scenario 2: Query With Skill Knowledge

With the skill loaded (Claude CLI from project root):
```
List all TAS foundations
```

Claude should construct the correct query:
```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 20) { ... }
          }
        }
      }
    }
  }
}
```

The key difference is the skill teaches:
- The required `typed → tanzu → {domain} → {entity} → query(...)` hierarchy
- That domains and entity types must be **lowercase**
- Proper pagination with `edges/node/pageInfo`

## Monitoring and Debugging

### View Server Logs

The MCP server logs at DEBUG level. Watch for:
- Tool invocations
- GraphQL queries being executed
- Schema introspection (first request warms cache)

### Check Cache Status

```bash
curl http://localhost:8080/actuator/caches
```

### Server Metrics

```bash
curl http://localhost:8080/actuator/metrics
```

## Common Issues

### "Connection refused" Error

- Ensure the MCP server is running on port 8080
- Check firewall settings

### "401 Unauthorized" from API

- If using broker: verify the user has granted access to `tanzu-hub` in the broker UI
- If using fallback token: verify `TANZU_PLATFORM_FALLBACK_TOKEN` is set and not expired
- Tokens expire - get a fresh one if needed

### "Schema not loaded" Errors

- The schema loads asynchronously on startup
- Wait 10-15 seconds after server start for initial load
- Check server logs for any introspection errors

### Claude CLI Not Finding MCP Config

- Ensure you're running `claude` from the project root directory
- The `.mcp.json` file must be in the current directory

## Quick Start Script

Save this as `start-local-test.sh`:

```bash
#!/bin/bash

# Check for required environment variables
if [ -z "$TANZU_PLATFORM_URL" ]; then
    echo "ERROR: TANZU_PLATFORM_URL must be set"
    echo ""
    echo "export TANZU_PLATFORM_URL='https://tanzu-hub.kuhn-labs.com'"
    echo "export TANZU_PLATFORM_FALLBACK_TOKEN='your-token-here'  # for local dev"
    exit 1
fi

# Build if needed
if [ ! -f "hub-mcp/target/hub-mcp-0.0.1-SNAPSHOT.jar" ]; then
    echo "Building MCP server..."
    cd hub-mcp && ./mvnw package -DskipTests -q && cd ..
fi

# Start the server
echo "Starting MCP server on http://localhost:8080/mcp"
echo "Press Ctrl+C to stop"
echo ""
java -jar hub-mcp/target/hub-mcp-0.0.1-SNAPSHOT.jar
```

Make it executable:
```bash
chmod +x start-local-test.sh
```

## Next Steps

After successful local testing:

1. **Integration Testing**: Run through all test scenarios
2. **Cloud Foundry Deployment**: Deploy using the manifest in the implementation plan
3. **Update MCP URL**: Point Claude to the deployed URL instead of localhost

