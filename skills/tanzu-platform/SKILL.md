# Tanzu Platform Natural Language Interface Skill

## Purpose

This skill provides domain knowledge for constructing effective GraphQL queries against the Tanzu Platform API. The API contains **1,382 types** across 6+ domains, making domain expertise essential for successful query construction.

Use this skill to translate natural language requests into accurate GraphQL queries for Tanzu Platform operations.

## When to Use This Skill

Read this skill **BEFORE** using any Tanzu MCP tools when:

- Constructing queries for Tanzu Platform entities (foundations, applications, spaces, etc.)
- Navigating entity relationships (e.g., "find all apps in a foundation")
- Finding vulnerabilities or security information
- Managing infrastructure and capacity
- Setting up monitoring and alerts
- Troubleshooting query errors

## Available MCP Tools

| Tool | Purpose | When to Use |
|------|---------|-------------|
| `tanzu_validate_query` | Validate query syntax | **Before executing any query** - catches errors early |
| `tanzu_graphql_query` | Execute read queries | Fetching data from the API |
| `tanzu_graphql_mutate` | Execute mutations | Creating, updating, or deleting resources |
| `tanzu_explore_schema` | Discover schema | Finding types, fields, and relationships |
| `tanzu_find_entity_path` | Navigate relationships | Finding paths between entity types |
| `tanzu_common_queries` | Pre-built patterns | Executing common operations quickly |

## ⚠️ CRITICAL: Choosing Efficient Patterns

**Before executing any query, determine if the question asks for COUNTS/AGGREGATIONS vs DETAILS:**

| Question Type | Pattern to Use | Example Questions |
|--------------|----------------|-------------------|
| **Counts/Aggregations** | `count_stopped_apps_by_space`, `summarize_app_states` | "How many stopped apps?", "Spaces with >2 stopped apps?" |
| **Summaries** | `spaces_summary`, `list_spaces` | "List all spaces", "Space overview" |
| **Full Details** | `spaces_with_apps`, `list_applications` | "Show me all apps in each space with their config" |

### Example: Answering "Are there spaces with more than 2 stopped apps?"

**❌ WRONG (huge response, may timeout):**
```
tanzu_common_queries(pattern: "spaces_with_apps")  # Returns ALL spaces with ALL apps!
```

**✅ CORRECT (efficient, pre-aggregated):**
```
tanzu_common_queries(pattern: "count_stopped_apps_by_space")
# Response includes: insights.spacesWithMoreThan2StoppedApps
```

## Query Construction Workflow

Follow this workflow for reliable query construction:

1. **Identify the domain** → Read the relevant `domains/*.md` file
2. **Understand the entities** → Use `tanzu_explore_schema` with domain filter
3. **Plan the navigation** → Use `tanzu_find_entity_path` if crossing entities
4. **Construct the query** → Follow patterns in `patterns/*.md`
5. **Validate before executing** → Use `tanzu_validate_query`
6. **Execute and handle errors** → Use `tanzu_graphql_query`

## Critical: Query Structure

The Tanzu Platform GraphQL API uses a strongly-typed query hierarchy. Queries **MUST** follow this structure:

```
entityQuery → typed → tanzu → {domain} → {entityType} → query(...)
```

### Correct Query Structure

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
# CORRECT - entityName at entity level
node {
  id
  entityId
  entityName                          # This is the entity's name!
  properties {
    guid                              # Properties has guid, state, etc.
    state
  }
}

# WRONG - name is NOT a field on properties types
node {
  properties {
    name                              # ERROR: field "name" doesn't exist!
  }
}
```

### Common Mistakes to Avoid

```graphql
# WRONG - Don't query entity types directly
query {
  entityQuery {
    Entity_Tanzu_TAS_Foundation_Type(first: 10) { ... }
  }
}

# WRONG - Don't use uppercase domain names
query {
  entityQuery {
    typed {
      tanzu {
        TAS {  # Should be lowercase: tas
          Foundation { ... }  # Should be lowercase: foundation
        }
      }
    }
  }
}

# WRONG - name is not a property field
node {
  properties {
    name  # This doesn't exist! Use entityName at entity level
  }
}
```

## Naming Conventions

Understanding naming conventions is critical for this API:

| Component | Convention | Example |
|-----------|------------|---------|
| Domains | lowercase | `tas`, `spring`, `platform` |
| Entity types in queries | lowercase | `foundation`, `application`, `space` |
| Entity type names | PascalCase with `_Type` suffix | `Entity_Tanzu_TAS_Foundation_Type` |
| Query types | PascalCase with `_Query` suffix | `Entity_Tanzu_TAS_Foundation_Query` |
| Properties types | PascalCase with `_Properties` suffix | `Entity_Tanzu_TAS_Foundation_Properties` |
| Relationship entity fields | snake_case | `tanzu_tas_application`, `tanzu_tas_space` |
| Known acronyms | UPPERCASE | `TAS`, `TKG`, `TMC`, `BOSH`, `VM` |

## Entity Fields

### Common Entity Fields (on ALL entity types)

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

### Application Properties (Entity_Tanzu_TAS_Application_Properties)

```graphql
properties {
  guid                  # Application GUID
  state                 # STARTED or STOPPED
  health_status         # RUNNING, DOWN, or STOPPED
  instanceCount         # Desired instances
  runningInstanceCount  # Running instances
  crashedInstanceCount  # Crashed instances
  buildpack             # Buildpack name
  spaceGUID             # Parent space GUID
  foundation            # Foundation name
  routes                # Application routes
  totalMemoryLimitMB    # Memory limit
}
```

### Space Properties (Entity_Tanzu_TAS_Space_Properties)

```graphql
properties {
  guid                  # Space GUID
  foundation            # Foundation name
  organizationGUID      # Parent organization GUID
  totalAppCount         # Number of apps
  totalMemoryLimitMB    # Memory used
  totalMemoryQuotaMB    # Memory quota
}
```

## Domain Quick Reference

| Domain | Key Entities | Description | Skill File |
|--------|--------------|-------------|------------|
| **TAS** | Foundation, Organization, Space, Application | Tanzu Application Service (Cloud Foundry) | `domains/TAS.md` |
| **Spring** | SpringArtifact, Dependency, Runtime | Spring application monitoring | `domains/Spring.md` |
| **Observability** | Alert, Metric, Log, NotificationTarget | Metrics, logs, alerts, traces | `domains/Observability.md` |
| **Security** | Vulnerability, CVE, Insight, Policy | Vulnerabilities, CVEs, compliance | `domains/Security.md` |
| **Capacity** | CapacityInfo, Recommendation | Resource management | `domains/Capacity.md` |

## Entity Hierarchy

The primary TAS entity hierarchy (containment relationships):

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

## Relationship Navigation

### CRITICAL: Relationship Field Names

Relationships use **snake_case entity type names**, NOT generic `contains`:

| To Navigate | Use Field | In Type |
|-------------|-----------|---------|
| Space → Applications | `relationshipsIn.isContainedIn.tanzu_tas_application` | Space |
| Organization → Spaces | `relationshipsIn.isContainedIn.tanzu_tas_space` | Organization |
| Foundation → Organizations | `relationshipsIn.isContainedIn.tanzu_tas_organization` | Foundation |
| Application → Space | `relationshipsOut.isContainedIn.tanzu_tas_space` | Application |
| Space → Organization | `relationshipsOut.isContainedIn.tanzu_tas_organization` | Space |

### Relationship Directions

- **`relationshipsIn`**: Entities that ARE CONTAINED IN this entity (children)
- **`relationshipsOut`**: Entities that this entity IS CONTAINED IN (parents)

### Navigating Down: Space → Applications (CORRECT)

```graphql
query SpacesWithApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 100) {
              edges {
                node {
                  entityName                    # Space name
                  properties {
                    guid
                    totalAppCount
                  }
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_application(first: 100) {  # Snake_case field!
                        edges {
                          node {
                            entityName          # App name  
                            properties {
                              state
                              health_status
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
        }
      }
    }
  }
}
```

### Navigating Up: Application → Space (CORRECT)

```graphql
query AppToSpace {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node {
                  entityName                    # App name
                  properties {
                    state
                  }
                  relationshipsOut {
                    isContainedIn {
                      tanzu_tas_space(first: 1) {  # Snake_case field!
                        edges {
                          node {
                            entityName          # Space name
                            properties {
                              guid
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
        }
      }
    }
  }
}
```

## Critical Rules

1. **Entity name is `entityName`** at entity level, NOT `properties.name`
2. **Relationship fields use snake_case** like `tanzu_tas_application`, NOT `contains`
3. **Always validate queries** before execution using `tanzu_validate_query`
4. **Request only needed fields** to avoid complexity limits
5. **Handle pagination** - use cursor-based patterns from `patterns/pagination.md`
6. **Check relationship direction** - `relationshipsIn` (children) vs `relationshipsOut` (parent)
7. **Domains and entity types are lowercase** in queries
8. **Entity type names use PascalCase** with `_Type` suffix

## Quick Reference Queries

### List All Foundations

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 20) {
              edges {
                node {
                  id
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

### List Applications with State

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 50) {
              edges {
                node {
                  id
                  entityName
                  properties {
                    state
                    health_status
                    instanceCount
                    runningInstanceCount
                  }
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

### Spaces with Their Applications

```graphql
query SpacesWithApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 50) {
              edges {
                node {
                  entityName
                  properties {
                    guid
                    totalAppCount
                  }
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_application(first: 100) {
                        edges {
                          node {
                            entityName
                            properties {
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
        }
      }
    }
  }
}
```

### Find Critical Vulnerabilities

```graphql
query {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: CRITICAL }) {
      edges {
        node {
          cveId
          severity
          score {
            value
            type
          }
        }
      }
    }
  }
}
```

### Get Active Alerts

```graphql
query {
  observabilityAlertQueryProvider {
    alerts(filter: { status: FIRING }) {
      edges {
        node {
          name
          severity
          status
        }
      }
    }
  }
}
```

## Skill Files Reference

### Domain Knowledge
- `domains/TAS.md` - Tanzu Application Service entities and patterns
- `domains/Spring.md` - Spring Boot application monitoring
- `domains/Observability.md` - Metrics, logs, alerts, traces
- `domains/Security.md` - Vulnerabilities, CVEs, compliance
- `domains/Capacity.md` - Resource management, recommendations

### Query Patterns
- `patterns/common-queries.md` - 20+ frequent query templates
- `patterns/entity-navigation.md` - Relationship traversal patterns
- `patterns/pagination.md` - Cursor-based pagination handling
- `patterns/filtering.md` - Filter syntax by entity type
- `patterns/mutations.md` - Safe mutation patterns

### Reference
- `reference/entity-hierarchy.md` - Visual entity tree with relationships
- `reference/type-naming.md` - Type naming conventions explained
- `reference/api-stability.md` - Alpha/Beta/GA API notes

### Troubleshooting
- `troubleshooting/error-recovery.md` - Common errors and fixes
- `troubleshooting/anti-patterns.md` - Query patterns to avoid
- `troubleshooting/performance.md` - Keeping queries efficient

## Environment Variables

The MCP server requires these environment variables:

| Variable | Description | Example |
|----------|-------------|---------|
| `TANZU_PLATFORM_URL` | Tanzu Platform URL | `https://tanzu-hub.kuhn-labs.com` |
| `TOKEN` | Bearer token for authentication | `eyJ...` |

## Getting Help

If a query fails:

1. Check the error message for specific field or type issues
2. Remember: `entityName` is at entity level, not in properties!
3. Use `tanzu_validate_query` to get suggestions
4. Use `tanzu_explore_schema` to verify type/field names
5. Review `troubleshooting/error-recovery.md` for common fixes
6. Check `troubleshooting/anti-patterns.md` to avoid known issues
