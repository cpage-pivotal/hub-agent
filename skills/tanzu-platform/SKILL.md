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
                  properties {
                    name
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
| Relationship fields | camelCase | `isContainedIn`, `contains` |
| Known acronyms | UPPERCASE | `TAS`, `TKG`, `TMC`, `BOSH`, `VM` |

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

Entities expose relationships through two fields:

- **`relationshipsOut`**: Outgoing relationships (child → parent, use `isContainedIn`)
- **`relationshipsIn`**: Incoming relationships (parent → children, use `contains`)

### Navigating Up (Child to Parent)

To traverse from Application → Space → Organization → Foundation:

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 5) {
              edges {
                node {
                  properties { name }
                  relationshipsOut {
                    isContainedIn {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Space_Type {
                            properties { name }
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

### Navigating Down (Parent to Children)

To traverse from Foundation → Organizations → Spaces → Applications:

```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 5) {
              edges {
                node {
                  properties { name }
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Organization_Type {
                            properties { name }
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

1. **Always validate queries** before execution using `tanzu_validate_query`
2. **Request only needed fields** to avoid complexity limits
3. **Handle pagination** - use cursor-based patterns from `patterns/pagination.md`
4. **Check relationship direction** - `relationshipsIn` vs `relationshipsOut` matters
5. **Use domain filtering** in `tanzu_explore_schema` to narrow 1,382 types
6. **Domains and entity types are lowercase** in queries
7. **Entity type names use PascalCase** with `_Type` suffix
8. **Relationship fields are camelCase** (`isContainedIn`, not `IsContainedIn`)

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
                  properties { name }
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

### List Applications in a Space

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
                  properties {
                    name
                    state
                    instances
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
2. Use `tanzu_validate_query` to get suggestions
3. Use `tanzu_explore_schema` to verify type/field names
4. Review `troubleshooting/error-recovery.md` for common fixes
5. Check `troubleshooting/anti-patterns.md` to avoid known issues
