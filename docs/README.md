# Tanzu Platform Natural Language Interface - Documentation

This directory contains documentation for the Tanzu Platform MCP Server and Skill.

## Quick Links

| Document | Purpose |
|----------|---------|
| [Implementation Status](implementation-status.md) | Project overview and current status |
| [Schema Learnings](schema-learnings.md) | **Critical API knowledge** - read this first! |
| [Architecture](architecture.md) | Design decisions and system architecture |
| [Deployment Guide](deployment.md) | How to build, run, and deploy |

## Project Components

### MCP Server (`hub-mcp/`)

Spring Boot application providing MCP tools for Claude to interact with the Tanzu Platform GraphQL API.

**Tools Available:**
- `tanzu_graphql_query` - Execute read queries
- `tanzu_graphql_mutate` - Execute mutations
- `tanzu_explore_schema` - Explore the API schema
- `tanzu_find_entity_path` - Find relationship paths
- `tanzu_common_queries` - Pre-built query patterns
- `tanzu_validate_query` - Validate queries before execution

### Skill (`skills/tanzu-platform/`)

Domain knowledge for constructing effective GraphQL queries. Claude reads this skill before using the MCP tools.

**Key Files:**
- `SKILL.md` - Main entry point and quick reference
- `domains/*.md` - Domain-specific entity documentation
- `patterns/*.md` - Query patterns and templates
- `troubleshooting/*.md` - Error recovery and anti-patterns

## Getting Started

1. **Read [Schema Learnings](schema-learnings.md)** - Critical knowledge about the API
2. **Obtain a refresh token** for the Agent Credential Broker:
   ```bash
   python3 get-refresh-token.py https://tanzu-hub.kuhn-labs.com
   ```
   Then paste the token into the broker UI under **Provide Refresh Token** for the `tanzu-hub` grant.
3. **Set environment variables:**
   ```bash
   export TANZU_PLATFORM_URL=https://tanzu-hub.kuhn-labs.com
   export BROKER_URL=https://agent-credential-broker.apps.internal:8443
   export BROKER_DELEGATION_TOKEN=<delegation-token-from-broker>
   export BROKER_TARGET_SYSTEM=tanzu-hub
   ```
4. **Run the MCP server:**
   ```bash
   cd hub-mcp
   ./mvnw spring-boot:run
   ```

### Authentication

In production (Cloud Foundry), `hub-mcp` authenticates with the [Agent Credential Broker](https://github.com/example/agent-credential-broker) via mTLS using CF workload identity certificates. The broker holds a refresh token for Tanzu Hub and returns short-lived access tokens on demand.

The `get-refresh-token.py` script performs a headless PKCE login against Tanzu Hub's CSP gateway to obtain a refresh token. This is a one-time operation — the broker renews the token automatically thereafter.

## Critical Knowledge (TL;DR)

Before working with this API, understand these key points:

### Efficient Pattern Selection

For aggregation/count questions, use **efficiency patterns** to avoid large responses:

| Question Type | Use Pattern | NOT |
|--------------|-------------|-----|
| "Spaces with >N stopped apps?" | `count_stopped_apps_by_space` | ~~`spaces_with_apps`~~ |
| "How many apps are stopped?" | `summarize_app_states` | ~~`list_applications`~~ |
| "List all spaces" (overview) | `spaces_summary` | ~~`spaces_with_apps`~~ |

### Entity Names

```graphql
# CORRECT - entityName is at entity level
node {
  entityName          # This is the entity's name!
  properties {
    guid              # Properties has other fields
    state
  }
}

# WRONG - properties.name doesn't exist!
node {
  properties {
    name              # ERROR!
  }
}
```

### Relationship Navigation

```graphql
# CORRECT - use snake_case entity type fields
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # Snake_case!
      edges { node { entityName } }
    }
  }
}

# WRONG - 'contains' doesn't exist!
relationshipsIn {
  contains {  # ERROR!
    edges { ... }
  }
}
```

### Query Structure

```graphql
# CORRECT - use typed query hierarchy
query {
  entityQuery {
    typed {
      tanzu {
        tas {                    # lowercase domain
          foundation {           # lowercase entity type
            query(first: 10) {
              edges {
                node {
                  entityName
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

See [Schema Learnings](schema-learnings.md) for complete details.

