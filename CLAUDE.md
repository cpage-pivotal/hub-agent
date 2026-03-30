# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a dual-component system providing a natural language interface to the **Tanzu Platform GraphQL API**:

1. **`hub-mcp/`** — Java Spring Boot MCP server that executes GraphQL queries against Tanzu Platform
2. **`skills/tanzu-platform/`** — Markdown skill documents teaching Claude domain knowledge for constructing correct GraphQL queries

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

**Six MCP tools exposed to Claude:**

| Tool | Method | Purpose |
|------|--------|---------|
| `tanzu_graphql_query` | `TanzuQueryTool` | Execute read queries |
| `tanzu_graphql_mutate` | `TanzuMutateTool` | Execute mutations |
| `tanzu_explore_schema` | `TanzuExploreSchemaTool` | Explore schema with filtering |
| `tanzu_find_entity_path` | `TanzuFindEntityPathTool` | BFS relationship navigation |
| `tanzu_common_queries` | `TanzuCommonQueriesTool` | 15+ pre-built query patterns |
| `tanzu_validate_query` | `TanzuValidateQueryTool` | Syntax validation |

**Key services:**
- `TanzuGraphQLService` — Query execution with 3-retry exponential backoff (5xx/timeouts only)
- `SchemaIntrospectionService` — Schema fetching and caching
- `CredentialBrokerService` — Fetches Tanzu Hub tokens from the Agent Credential Broker via mTLS; caches tokens with TTL-aware refresh

**Caching (Caffeine, 24h TTL):**
- `graphql-schema` — Full introspection result (~1,382 types)
- `entity-relationships` — Pre-computed relationship graph
- `type-definitions` — Individual type lookups
- Schema warms up 5s after startup; daily refresh at 2 AM

### Skill Documents (`skills/tanzu-platform/`)

Structured Markdown teaching Claude how to use the Tanzu Platform GraphQL API:

- `SKILL.md` — Entry point: tool mapping, quick reference
- `domains/` — Entity documentation per domain (TAS, Spring, Observability, Security, Capacity)
- `patterns/` — Query patterns and templates (common queries, filtering, pagination, mutations, entity navigation)
- `reference/` — Entity hierarchy, naming conventions
- `troubleshooting/` — Error recovery, anti-patterns, performance

### Critical API Knowledge

The Tanzu Platform GraphQL schema has 1,382 types. Key conventions Claude must follow when writing queries:

- Entity names use `entityName` field (NOT `properties.name`)
- Relationships use snake_case: `tanzu_tas_application` (NOT generic `contains`)
- Query hierarchy: `entityQuery → typed → tanzu → {domain} → {entity} → query(...)`
- For aggregations, prefer efficiency patterns (e.g. `count_stopped_apps_by_space`) over detail patterns

See `docs/schema-learnings.md` for the full list of critical API knowledge.

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