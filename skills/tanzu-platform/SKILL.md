# Tanzu Platform Natural Language Interface Skill

## Quick Start — Follow These Steps First

For most Tanzu Platform questions, you only need two steps:

**Step 1: Get a token** (required once per session, tokens last 30 minutes):

```bash
python3 scripts/get-token.py
```

The path is relative to the skill directory. It uses only Python standard library (no pip install needed). It reads `TANZU_HUB_URL`, `TANZU_HUB_USER`, `TANZU_HUB_PASSWORD` from environment variables. Capture the printed token for Step 2.

**Step 2: Call `tanzu_common_queries` with the matching pattern:**

| User Request | Pattern to Use |
|---|---|
| "List my foundations" | `list_foundations` |
| "List organizations" | `list_organizations` |
| "List spaces" / "Space overview" | `list_spaces` or `spaces_summary` |
| "List applications" | `list_applications` |
| "Find stopped apps" | `find_stopped_apps` |
| "Spaces with stopped apps" / "How many stopped apps per space?" | `count_stopped_apps_by_space` |
| "App state distribution" / "How many started vs stopped?" | `summarize_app_states` |
| "Show spaces with their apps" | `spaces_with_apps` |
| "Find vulnerabilities" | `find_vulnerabilities` |
| "Find critical CVEs" | `find_critical_cves` |
| "List alerts" | `list_alerts` |
| "Check capacity" | `check_capacity` |
| "List Spring apps" | `list_spring_apps` |
| "App health status" | `get_app_health` |

Example call:

```
tanzu_common_queries(pattern: "list_foundations", token: "<token from step 1>")
```

**That's it.** If a pre-built pattern matches the request, do NOT use schema exploration, query validation, or the full query construction workflow. Just get the token and call the pattern.

## Decision Tree — When to Use What

```
Can a tanzu_common_queries pattern handle the request?
  YES → Get token → call tanzu_common_queries. Done.
  NO  → Is it a simple read query you can construct from the reference below?
    YES → Get token → build query → tanzu_validate_query → tanzu_graphql_query. Done.
    NO  → Get token → use tanzu_explore_schema to discover types → build query → validate → execute.
```

Only fall through to the full workflow when no pre-built pattern exists and you need to discover the schema.

---

## Authentication

All MCP tools require a `token` argument. Tokens rotate every 30 minutes — always get a fresh token at the start of each Tanzu Platform session.

**Prerequisites:** These env vars must be set:

| Variable | Description |
|----------|-------------|
| `TANZU_HUB_URL` | Tanzu Hub URL (defaults to `https://tanzu-hub.kuhn-labs.com`) |
| `TANZU_HUB_USER` | Your Tanzu Hub username |
| `TANZU_HUB_PASSWORD` | Your Tanzu Hub password |

**Get a token:**

```bash
python3 scripts/get-token.py
```

The path is relative to the skill directory. It uses only Python standard library — no `pip install` required. Capture the printed token and pass it as the `token` argument to every tool call.

## Available MCP Tools

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `tanzu_common_queries` | Pre-built patterns | **Try this first** for any standard operation |
| `tanzu_graphql_query` | Execute read queries | Custom queries not covered by common patterns |
| `tanzu_validate_query` | Validate query syntax | Before executing any custom query |
| `tanzu_graphql_mutate` | Execute mutations | Creating, updating, or deleting resources |
| `tanzu_explore_schema` | Discover schema | Only when you need to find unknown types/fields |
| `tanzu_find_entity_path` | Navigate relationships | Only when crossing unfamiliar entity boundaries |

## Choosing Efficient Patterns

**Before executing any query, determine if the question asks for COUNTS/AGGREGATIONS vs DETAILS:**

| Question Type | Pattern to Use | Example Questions |
|--------------|----------------|-------------------|
| **Counts/Aggregations** | `count_stopped_apps_by_space`, `summarize_app_states` | "How many stopped apps?", "Spaces with >2 stopped apps?" |
| **Summaries** | `spaces_summary`, `list_spaces` | "List all spaces", "Space overview" |
| **Full Details** | `spaces_with_apps`, `list_applications` | "Show me all apps in each space with their config" |

---

## Reference: Query Construction (for custom queries only)

The sections below are reference material for building custom GraphQL queries. Skip this if `tanzu_common_queries` already handles your request.

### Query Structure

Queries **MUST** follow this hierarchy:

```
entityQuery → typed → tanzu → {domain} → {entityType} → query(...)
```

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {                           # Domain (lowercase)
          foundation {                  # Entity type (lowercase)
            query(first: 10) {          # Query method with pagination
              edges {
                node {
                  id
                  entityName            # Name is at entity level, NOT properties!
                }
              }
              pageInfo {
                hasNextPage
                endCursor
              }
            }
          }
        }
      }
    }
  }
}
```

### CRITICAL: Entity Name vs Properties

**The entity name is `entityName` at the entity level, NOT `properties.name`!**

```graphql
# CORRECT
node {
  entityName                          # This is the entity's name!
  properties {
    guid                              # Properties has guid, state, etc.
    state
  }
}

# WRONG - name does NOT exist on properties
node {
  properties {
    name                              # ERROR: field "name" doesn't exist!
  }
}
```

### Naming Conventions

| Component | Convention | Example |
|-----------|------------|---------|
| Domains | lowercase | `tas`, `spring`, `platform` |
| Entity types in queries | lowercase | `foundation`, `application`, `space` |
| Entity type names | PascalCase with `_Type` suffix | `Entity_Tanzu_TAS_Foundation_Type` |
| Properties types | PascalCase with `_Properties` suffix | `Entity_Tanzu_TAS_Foundation_Properties` |
| Relationship entity fields | snake_case | `tanzu_tas_application`, `tanzu_tas_space` |

### Entity Hierarchy

```
Platform
└── Foundation Groups
    └── Foundations (TAS)
        ├── Organizations
        │   └── Spaces
        │       └── Applications
        ├── BOSH Directors
        └── Ops Managers
```

### Relationship Navigation

Relationships use **snake_case entity type names**, NOT generic `contains`:

| To Navigate | Use Field |
|-------------|-----------|
| Space → Applications | `relationshipsIn.isContainedIn.tanzu_tas_application` |
| Organization → Spaces | `relationshipsIn.isContainedIn.tanzu_tas_space` |
| Foundation → Organizations | `relationshipsIn.isContainedIn.tanzu_tas_organization` |
| Application → Space | `relationshipsOut.isContainedIn.tanzu_tas_space` |
| Space → Organization | `relationshipsOut.isContainedIn.tanzu_tas_organization` |

- **`relationshipsIn`**: Children (entities contained IN this entity)
- **`relationshipsOut`**: Parents (entities this entity IS CONTAINED IN)

### Common Entity Fields

```graphql
node {
  id                    # Opaque global ID
  entityId              # Canonical entity identifier
  entityName            # Human-readable name (THIS IS THE NAME!)
  entityType            # Type discriminator
  properties { ... }    # Type-specific properties (no 'name' field!)
  relationshipsIn { ... }   # Incoming relationships
  relationshipsOut { ... }  # Outgoing relationships
  tags { key value }    # Key/value tags
}
```

### Domain Quick Reference

| Domain | Key Entities | Skill File |
|--------|--------------|------------|
| **TAS** | Foundation, Organization, Space, Application | `domains/TAS.md` |
| **Spring** | SpringArtifact, Dependency, Runtime | `domains/Spring.md` |
| **Observability** | Alert, Metric, Log, NotificationTarget | `domains/Observability.md` |
| **Security** | Vulnerability, CVE, Insight, Policy | `domains/Security.md` |
| **Capacity** | CapacityInfo, Recommendation | `domains/Capacity.md` |

### Critical Rules

1. **Entity name is `entityName`** at entity level, NOT `properties.name`
2. **Relationship fields use snake_case** like `tanzu_tas_application`, NOT `contains`
3. **Always validate custom queries** before execution using `tanzu_validate_query`
4. **Request only needed fields** to avoid complexity limits
5. **Handle pagination** with `first: N` at each query level
6. **Domains and entity types are lowercase** in queries

### Skill Files Reference

- `patterns/common-queries.md` - 20+ frequent query templates
- `patterns/entity-navigation.md` - Relationship traversal patterns
- `patterns/pagination.md` - Cursor-based pagination handling
- `patterns/filtering.md` - Filter syntax by entity type
- `patterns/mutations.md` - Safe mutation patterns
- `troubleshooting/error-recovery.md` - Common errors and fixes
- `troubleshooting/anti-patterns.md` - Query patterns to avoid
