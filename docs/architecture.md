# Tanzu Platform MCP Server - Architecture

This document describes the system architecture and key design decisions.

## System Overview

```
┌─────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│                 │     │    MCP Server        │     │   Tanzu Platform    │
│     Claude      │◄───►│   (Spring Boot)      │◄───►│    GraphQL API      │
│                 │     │                      │     │                     │
└─────────────────┘     └──────────────────────┘     └─────────────────────┘
        │                                                      
        │               ┌──────────────────────┐              
        └──────────────►│       Skill          │              
                        │  (Domain Knowledge)  │              
                        └──────────────────────┘              
```

## Dual-Component Architecture

Neither MCP server nor skill alone can handle a 1,382-type schema:

| Component | Role | Why Needed |
|-----------|------|------------|
| **MCP Server** | API interaction | Executes queries, validates syntax, caches schema |
| **Skill** | Domain knowledge | Teaches correct query patterns, field names, relationships |

Both require equal investment for effective natural language interaction.

## MCP Server Components

### Technology Stack

- **Framework**: Spring Boot 3.x
- **MCP Integration**: Spring AI MCP Server WebFlux
- **HTTP Client**: WebClient (reactive)
- **Caching**: Caffeine (in-memory)
- **Build**: Maven

### Module Structure

```
hub-mcp/
├── config/
│   ├── TanzuPlatformProperties.java  # Configuration properties
│   ├── GraphQLClientConfig.java      # WebClient setup
│   ├── CacheConfig.java              # Caffeine cache config
│   └── SchemaCacheRefreshTask.java   # Scheduled schema refresh
├── service/
│   ├── TanzuGraphQLService.java      # GraphQL execution
│   └── SchemaIntrospectionService.java # Schema caching
├── tools/
│   ├── TanzuQueryTool.java           # tanzu_graphql_query
│   ├── TanzuMutateTool.java          # tanzu_graphql_mutate
│   ├── TanzuExploreSchemaTool.java   # tanzu_explore_schema
│   ├── TanzuFindEntityPathTool.java  # tanzu_find_entity_path
│   ├── TanzuCommonQueriesTool.java   # tanzu_common_queries
│   └── TanzuValidateQueryTool.java   # tanzu_validate_query
├── model/
│   ├── GraphQLRequest.java           # Request record
│   ├── GraphQLResponse.java          # Response record
│   ├── SchemaCache.java              # Cached schema
│   └── ...                           # Type definitions
└── exception/
    ├── GraphQLException.java         # API errors
    └── GlobalExceptionHandler.java   # Error handling
```

### MCP Tools

| Tool | Purpose | Key Features |
|------|---------|--------------|
| `tanzu_graphql_query` | Execute read queries | Retry logic, timeout handling |
| `tanzu_graphql_mutate` | Execute mutations | Safety confirmation for destructive ops |
| `tanzu_explore_schema` | Explore schema | Domain filtering, "did you mean" |
| `tanzu_find_entity_path` | Find relationship paths | BFS algorithm, query templates |
| `tanzu_common_queries` | Pre-built patterns | 15+ ready-to-use queries |
| `tanzu_validate_query` | Validate before execution | Syntax check, field validation |

### Caching Strategy

```
┌─────────────────────────────────────────────────────┐
│                  Caffeine Cache                      │
├─────────────────────────────────────────────────────┤
│  graphql-schema     │ Full introspection result     │
│  (TTL: 24 hours)    │ ~1,382 types                  │
├─────────────────────────────────────────────────────┤
│  entity-relationships│ Relationship graph           │
│  (TTL: 24 hours)     │ Pre-computed paths           │
├─────────────────────────────────────────────────────┤
│  type-definitions   │ Individual type lookups       │
│  (TTL: 24 hours)    │ On-demand caching            │
└─────────────────────────────────────────────────────┘
```

- **Startup warmup**: Schema loads 5 seconds after startup
- **Scheduled refresh**: Daily at 2 AM (configurable)
- **Graceful degradation**: If refresh fails, existing cache remains valid

## Skill Structure

```
skills/tanzu-platform/
├── SKILL.md                    # Main entry point
├── domains/
│   ├── TAS.md                  # TAS entities and properties
│   ├── Spring.md               # Spring monitoring
│   ├── Observability.md        # Alerts, metrics
│   ├── Security.md             # Vulnerabilities
│   └── Capacity.md             # Resource management
├── patterns/
│   ├── common-queries.md       # 20+ query templates
│   ├── entity-navigation.md    # Relationship traversal
│   ├── pagination.md           # Cursor patterns
│   ├── filtering.md            # Filter syntax
│   └── mutations.md            # Mutation patterns
├── reference/
│   ├── entity-hierarchy.md     # Entity tree
│   ├── type-naming.md          # Naming conventions
│   └── api-stability.md        # API versioning
└── troubleshooting/
    ├── error-recovery.md       # Common errors & fixes
    ├── anti-patterns.md        # What NOT to do
    └── performance.md          # Query optimization
```

## Data Flow

### Query Execution Flow

```
1. User → Natural language request
2. Claude → Reads skill for domain knowledge
3. Claude → Constructs GraphQL query
4. Claude → Calls tanzu_validate_query (optional but recommended)
5. Claude → Calls tanzu_graphql_query
6. MCP Server → Validates query syntax
7. MCP Server → Sends to Tanzu Platform API
8. Tanzu API → Returns data or errors
9. MCP Server → Formats response
10. Claude → Interprets and presents to user
```

### Schema Exploration Flow

```
1. Claude → Needs to understand entity structure
2. Claude → Calls tanzu_explore_schema(domain: "TAS")
3. MCP Server → Checks schema cache
4. MCP Server → Returns filtered types (max 20)
5. Claude → Uses information to construct query
```

### Relationship Discovery Flow

```
1. Claude → "How do I get from Application to Foundation?"
2. Claude → Calls tanzu_find_entity_path(from, to, maxDepth)
3. MCP Server → BFS traversal of relationship graph
4. MCP Server → Returns paths + query template
5. Claude → Uses template to build query
```

## Design Decisions

### Why Spring Boot + Spring AI?

| Alternative | Pros | Cons | Decision |
|-------------|------|------|----------|
| Python FastMCP | Fast prototyping | stdio transport, less robust | Not chosen |
| Node.js MCP SDK | JavaScript ecosystem | Not aligned with Tanzu | Not chosen |
| **Spring Boot** | Enterprise-ready, Tanzu alignment | More setup | ✅ Chosen |

Key factors:
- Streamable HTTP transport for cloud deployment
- Native Kubernetes/TAS support
- Consistent with Tanzu technology stack
- Type safety and compile-time validation

### Why Dual Component (MCP + Skill)?

The 1,382-type schema is too large to embed in tool descriptions:
- MCP tools have limited description space
- Domain knowledge requires structured documentation
- Error recovery needs documented patterns
- Relationship navigation needs visual guides

### Why Caffeine Cache?

- In-memory (no external dependencies)
- High performance
- Simple configuration
- Statistics available via Actuator
- Sufficient for single-instance deployment

### Why 24-Hour Schema TTL?

- Schema changes are infrequent
- Introspection query is expensive (~16MB response)
- Daily refresh catches updates
- Startup warmup ensures fresh data on deploy

## Configuration

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `TANZU_PLATFORM_URL` | Yes | Tanzu Platform base URL |
| `BROKER_URL` | Production | Agent Credential Broker URL |
| `BROKER_DELEGATION_TOKEN` | Production | Delegation JWT for the broker |
| `TANZU_PLATFORM_FALLBACK_TOKEN` | Local dev | Static token when no broker is available |

### Application Properties

```yaml
tanzu:
  platform:
    url: ${TANZU_PLATFORM_URL}
    fallback-token: ${TANZU_PLATFORM_FALLBACK_TOKEN:}
    broker:
      url: ${BROKER_URL:}
      delegation-token: ${BROKER_DELEGATION_TOKEN:}
    graphql:
      endpoint: /hub/graphql
      timeout: 30s
      max-retries: 3
  cache:
    schema:
      ttl: 24h
      max-size: 100
      refresh-cron: "0 0 2 * * ?"
```

## Error Handling

### Retry Strategy

- **Retryable**: 5xx errors, timeouts, connection errors
- **Not Retryable**: 4xx errors (client error)
- **Max Retries**: 3 (configurable)
- **Backoff**: Exponential with jitter

### Error Response Format

```json
{
  "success": false,
  "error": "GraphQL query returned errors",
  "errors": [
    {
      "message": "Cannot query field 'name' on type '...'",
      "locations": [{"line": 5, "column": 12}]
    }
  ],
  "details": {}
}
```

## Monitoring

### Actuator Endpoints

| Endpoint | Purpose |
|----------|---------|
| `/actuator/health` | Health check |
| `/actuator/metrics` | Metrics |
| `/actuator/caches` | Cache statistics |
| `/actuator/info` | Application info |

### Key Metrics

- Query success/failure rate
- Query latency (p50, p95, p99)
- Cache hit/miss ratio
- Schema refresh status

## Security Considerations

- Bearer tokens never logged
- Token passed via environment variable
- HTTPS required for production
- No sensitive data cached

## Scalability

### Current Design (Single Instance)

- In-memory Caffeine cache
- Suitable for development and small deployments
- Schema cache per instance

### Future Scaling Options

1. **Redis cache** for shared schema across instances
2. **Load balancer** with sticky sessions
3. **Horizontal scaling** of MCP server instances

