# Performance Optimization Guide

This document describes how to keep queries efficient and avoid performance issues.

## Query Complexity

The Tanzu Platform API tracks query complexity. High complexity queries may:
- Take longer to execute
- Time out
- Be rejected

### Factors Affecting Complexity

| Factor | Impact | Mitigation |
|--------|--------|------------|
| Number of fields | Higher | Request only needed fields |
| Nesting depth | Higher | Limit relationship traversal |
| List sizes | Higher | Use smaller `first` values |
| Relationship traversals | Higher | Minimize or separate queries |

## Optimization Strategies

### 1. Request Only Needed Fields

**Slow**:
```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 100) {
              edges {
                node {
                  id
                  entityId
                  entityName
                  entityType
                  createdAt
                  updatedAt
                  version
                  properties {
                    name
                    state
                    instances
                    memory
                    disk_quota
                    buildpack
                    stack
                    detected_start_command
                    # ... many more fields
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

**Faster**:
```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 100) {
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

### 2. Use Appropriate Page Sizes

| Scenario | Recommended `first` |
|----------|---------------------|
| Quick overview | 10-20 |
| Detailed listing | 50-100 |
| Bulk export | 100-200 |
| Single item lookup | 1-5 |

**Example**:
```graphql
# For overview dashboard
query(first: 10) { ... }

# For detailed list
query(first: 50) { ... }
```

### 3. Limit Relationship Depth

Each level of relationship traversal increases complexity.

**High complexity** (3 levels deep):
```graphql
foundation {
  query(first: 10) {
    edges {
      node {
        relationshipsIn {
          contains {
            edges {
              node {
                ... on Organization {
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Space {
                            relationshipsIn {
                              contains {
                                edges { node { ... } }
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
}
```

**Lower complexity** (separate queries):
```graphql
# Query 1: Get foundations
foundation { query(first: 10) { ... } }

# Query 2: Get organizations (with foundation IDs from Query 1)
organization { query(first: 50) { ... } }

# Query 3: Get spaces (with org IDs from Query 2)
space { query(first: 100) { ... } }
```

### 4. Paginate Relationship Connections

Add `first` to relationship queries:

```graphql
relationshipsIn {
  contains(first: 20) {  # Limit relationship results
    edges { ... }
  }
}
```

### 5. Use Filters to Reduce Results

Filter at the API level instead of fetching everything:

**Slow** (fetch all, filter client-side):
```graphql
query {
  artifactVulnerabilityQuery {
    vulnerabilities(first: 1000) {
      edges {
        node { severity cveId }
      }
    }
  }
}
# Then filter for CRITICAL client-side
```

**Faster** (filter at API):
```graphql
query {
  artifactVulnerabilityQuery {
    vulnerabilities(
      filter: { severity: CRITICAL }
      first: 100
    ) {
      edges {
        node { cveId }
      }
    }
  }
}
```

### 6. Avoid Redundant Data

Don't request the same data multiple ways:

```graphql
# REDUNDANT: name appears twice
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node {
                  entityName  # Application name
                  properties {
                    name  # Also application name (redundant)
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

### 7. Use Fragments for Reuse

Fragments don't improve performance directly but help maintain consistent, minimal field sets:

```graphql
fragment AppSummary on Entity_Tanzu_TAS_Application_Type {
  id
  properties {
    name
    state
  }
}

query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 50) {
              edges {
                node {
                  ...AppSummary
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

## Caching Strategies

### Schema Caching

The MCP server caches schema for 24 hours. Benefits:
- Faster validation
- Reduced API calls
- Better "did you mean" suggestions

### Query Result Caching

Consider caching query results for:
- Slowly changing data (foundations, organizations)
- Frequently accessed data (application lists)
- Reference data (entity counts)

## Monitoring Performance

### Response Extensions

Check `extensions.queryComplexity` in responses:

```json
{
  "data": { ... },
  "extensions": {
    "queryComplexity": 145
  }
}
```

### Timing

Monitor query execution time:
- < 1s: Good
- 1-5s: Acceptable for complex queries
- > 5s: Consider optimization
- > 30s: Likely to timeout

## Common Performance Issues

### Issue: Query Timeout

**Symptoms**: Query doesn't return, eventually times out.

**Causes**:
- Too much data requested
- Deep nesting
- Missing pagination

**Solutions**:
1. Reduce `first` values
2. Limit nesting depth
3. Remove unnecessary fields
4. Break into multiple queries

### Issue: Slow Response

**Symptoms**: Query returns but takes several seconds.

**Causes**:
- Large result sets
- Many fields requested
- Complex relationship traversals

**Solutions**:
1. Add filters
2. Reduce fields
3. Paginate relationships
4. Use smaller page sizes

### Issue: High Complexity Warning

**Symptoms**: API returns complexity warning or rejection.

**Causes**:
- Query exceeds complexity budget

**Solutions**:
1. Split into multiple queries
2. Reduce scope
3. Request fewer fields

## Best Practices Summary

| Practice | Benefit |
|----------|---------|
| Request only needed fields | Lower complexity |
| Use appropriate page sizes | Faster responses |
| Limit nesting depth | Avoid timeouts |
| Paginate relationships | Bounded complexity |
| Use filters | Smaller result sets |
| Split large queries | Stay within limits |
| Cache when appropriate | Reduce API calls |
| Monitor complexity | Proactive optimization |
