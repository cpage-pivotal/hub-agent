# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a dual-component system providing a natural language interface to the **Tanzu Platform GraphQL API**:

1. **`hub-mcp/`** — Java Spring Boot MCP server that constructs and executes GraphQL queries against Tanzu Platform
2. **`skills/tanzu-platform/`** — Single `SKILL.md` mapping user intents to composable entity queries

## Build & Run Commands

All commands run from the `hub-mcp/` directory:

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Build with tests
./mvnw clean package

# Run locally (dev)
./mvnw spring-boot:run

# Quick start (build + run)
../start-local-test.sh
```

**Required environment variables:**
- `TANZU_PLATFORM_URL` — Base URL of the Tanzu Platform instance
- `BROKER_URL` — URL of the Agent Credential Broker (for production/CF deployment)
- `BROKER_DELEGATION_TOKEN` — Delegation JWT for the broker

**For local development (without broker):**
- `TANZU_PLATFORM_FALLBACK_TOKEN` — Raw JWT bearer token (no `Bearer ` prefix)

The server starts on port 8080. MCP endpoint: `http://localhost:8080/mcp`

## Architecture

### MCP Server (`hub-mcp/`)

Spring Boot 3.5.9 / Java 21 application using Spring AI MCP Server (WebMVC transport).

**Three MCP tools exposed to Claude:**

| Tool | Method | Purpose |
|------|--------|---------|
| `tanzu_entity_query` | `TanzuEntityQueryTool` | Composable entity queries with scope and filter |
| `tanzu_graphql_query` | `TanzuQueryTool` | Raw GraphQL escape hatch (with inline validation) |
| `tanzu_graphql_mutate` | `TanzuMutateTool` | Execute mutations |

**Key services:**
- `TanzuGraphQLService` — Query execution with 3-retry exponential backoff (5xx/timeouts only)
- `SchemaIntrospectionService` — Schema fetching and caching
- `CredentialBrokerService` — Fetches Tanzu Hub tokens from the Agent Credential Broker via mTLS; caches tokens with TTL-aware refresh

**Caching (Caffeine, 24h TTL):**
- `graphql-schema` — Full introspection result (~1,382 types)
- `entity-relationships` — Pre-computed relationship graph
- `type-definitions` — Individual type lookups
- Schema warms up 5s after startup; daily refresh at 2 AM

### Skill Document (`skills/tanzu-platform/`)

Single `SKILL.md` file (~70 lines) that maps user intents to `tanzu_entity_query` calls:

- Lists all entity types across TAS (23), Spring (4), and Platform (3) domains
- Shows scoping patterns: `scope: {"foundation": "X"}` to narrow results
- Documents common filter patterns: `filter: {"property": "state", "value": "STOPPED"}`
- The LLM never writes GraphQL — the server constructs queries from composable parameters

### Design Principles

- **Server owns query construction**: The MCP server constructs GraphQL internally from entity type + scope + filter
- **Skill is a routing table**: Maps user intent to tool parameters, no GraphQL syntax
- **Schema validation is server-side**: Property names are validated against the cached schema with helpful error messages
- Entity names use `entityName` field (NOT `properties.name`)
- Relationships use snake_case: `tanzu_tas_application` (NOT generic `contains`)

## Package Structure

Java packages follow Package-by-Feature:

```
org.tanzu.hubmcp/
├── config/      # Spring configuration (properties, WebClient, cache, scheduler)
├── service/     # GraphQL execution and schema introspection
├── tools/       # MCP tool implementations (one class per tool)
├── model/       # Data records (GraphQLRequest, GraphQLResponse, TypeDefinition, etc.)
└── exception/   # GraphQLException, GlobalExceptionHandler
```

## Deployment

Cloud Foundry deployment via `hub-mcp/manifest.yml`. See `docs/deployment.md` for full instructions.