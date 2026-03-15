# Filtering Patterns

This document describes how to filter query results in the Tanzu Platform GraphQL API.

## Filter Syntax

Filters are passed as arguments to query methods. The exact filter syntax varies by entity type.

## Common Filter Patterns

### Property-Based Filtering

Some entities support filtering by property:

```graphql
query FilteredApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(
              first: 50,
              filter: {
                property: "state",
                value: "STARTED"
              }
            ) {
              edges {
                node {
                  properties { name state }
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

### Vulnerability Filtering

Vulnerabilities have dedicated filter types:

```graphql
query FilteredVulns {
  artifactVulnerabilityQuery {
    vulnerabilities(
      filter: {
        severity: CRITICAL,
        triageStatus: OPEN
      }
    ) {
      edges {
        node {
          cveId
          severity
          triageStatus
        }
      }
    }
  }
}
```

### Alert Filtering

Alerts can be filtered by status:

```graphql
query FilteredAlerts {
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

## Discovering Available Filters

Use `tanzu_explore_schema` to discover what filters are available for each entity type:

```
tanzu_explore_schema(
  typeName: "Entity_Tanzu_TAS_Application_Query",
  showRelationships: false
)
```

This will show the `query` field and its arguments, including any filter parameters.

## Filter Value Types

### Enum Filters

Many filters use enum values:

```graphql
# Vulnerability severity
severity: CRITICAL | HIGH | MEDIUM | LOW | UNKNOWN

# Alert status
status: FIRING | RESOLVED | PENDING

# Triage status
triageStatus: OPEN | IN_PROGRESS | RESOLVED | FALSE_POSITIVE | ACCEPTED_RISK
```

### String Filters

Some filters accept string values:

```graphql
filter: {
  property: "name",
  value: "my-app"
}
```

### Boolean Filters

Some filters accept boolean values:

```graphql
filter: {
  isDeprecated: true
}
```

## Combining Filters

When multiple filter criteria are supported, they typically combine with AND logic:

```graphql
query MultiFilter {
  artifactVulnerabilityQuery {
    vulnerabilities(
      filter: {
        severity: CRITICAL,      # AND
        triageStatus: OPEN       # AND
      }
    ) {
      edges {
        node {
          cveId
        }
      }
    }
  }
}
```

## Client-Side Filtering

When server-side filtering isn't available, filter results client-side:

1. Fetch data with pagination
2. Filter in your application code
3. Continue fetching until you have enough results

Example workflow:
```
1. Query applications (first: 100)
2. Filter response for state == "STOPPED"
3. If hasNextPage and need more results, fetch next page
```

## Filter Best Practices

1. **Check available filters** - Use `tanzu_explore_schema` first
2. **Use server-side filtering when possible** - More efficient than client-side
3. **Combine with pagination** - Filter + pagination for large datasets
4. **Handle no results** - Filters may return empty results

## Common Filter Use Cases

### Find Production Applications

```graphql
# May need to filter by tag or naming convention
query ProdApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 100) {
              edges {
                node {
                  properties { name }
                  tags {
                    key
                    value
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
# Then filter client-side for tags matching "environment: production"
```

### Find High-Priority Vulnerabilities

```graphql
query HighPriorityVulns {
  artifactVulnerabilityQuery {
    vulnerabilities(
      filter: {
        severity: CRITICAL,
        triageStatus: OPEN
      },
      first: 50
    ) {
      edges {
        node {
          cveId
          severity
          score { value }
        }
      }
    }
  }
}
```

### Find Active Alerts by Severity

```graphql
query CriticalAlerts {
  observabilityAlertQueryProvider {
    alerts(
      filter: {
        status: FIRING,
        severity: CRITICAL
      }
    ) {
      edges {
        node {
          name
          severity
        }
      }
    }
  }
}
```

## Filter Limitations

1. **Not all entities support filtering** - Some require client-side filtering
2. **Limited operators** - Usually equality, not greater-than/less-than
3. **No full-text search** - Exact matches only in most cases
4. **Case sensitivity** - Check documentation for each filter

## Error Handling

If a filter is invalid, you'll receive an error:

```json
{
  "errors": [
    {
      "message": "Unknown filter field: 'invalidField'",
      "locations": [{"line": 5, "column": 7}]
    }
  ]
}
```

Use `tanzu_validate_query` to catch filter errors before execution.
