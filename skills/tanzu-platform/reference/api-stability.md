# API Stability Reference

This document describes the stability levels of the Tanzu Platform GraphQL API.

## Stability Levels

### GA (Generally Available)

- **Stability**: Stable, production-ready
- **Breaking Changes**: Avoided; deprecated before removal
- **Support**: Full support with SLAs
- **Recommendation**: Safe for production use

### Beta

- **Stability**: Feature complete but may change
- **Breaking Changes**: Possible with notice
- **Support**: Best-effort support
- **Recommendation**: Use with awareness of potential changes

### Alpha

- **Stability**: Experimental, early access
- **Breaking Changes**: Expected and common
- **Support**: Limited or no support
- **Recommendation**: Testing and evaluation only

## Identifying Stability Levels

### Type Descriptions

Some types include stability indicators in their descriptions:

```
"description": "ALPHA: This type is experimental and may change."
```

### Naming Conventions

Some unstable APIs use prefixes:

- `Alpha_*` or `_Alpha` - Alpha stage
- `Beta_*` or `_Beta` - Beta stage
- `Experimental_*` - Experimental

### Deprecation Markers

Use schema introspection to find deprecated fields:

```graphql
query CheckDeprecation {
  __type(name: "Entity_Tanzu_TAS_Application_Type") {
    fields(includeDeprecated: true) {
      name
      isDeprecated
      deprecationReason
    }
  }
}
```

## Known Stable APIs

Based on production usage, these are typically stable:

### Core TAS Entities (GA)
- `Entity_Tanzu_TAS_Foundation_Type`
- `Entity_Tanzu_TAS_Organization_Type`
- `Entity_Tanzu_TAS_Space_Type`
- `Entity_Tanzu_TAS_Application_Type`

### Security APIs (GA)
- `artifactVulnerabilityQuery`
- `vulnerabilities` query

### Observability APIs (GA/Beta)
- `observabilityAlertQueryProvider`
- Alert queries

## Known Beta/Alpha APIs

These APIs may be less stable:

### Capacity (Beta)
- `capacityQuery`
- Capacity recommendations

### Insights (Beta/Alpha)
- `insightQuery`
- Policy insights

## Best Practices

### 1. Check for Deprecations

Before using a field, check if it's deprecated:

```
tanzu_explore_schema(
  typeName: "Entity_Tanzu_TAS_Application_Type",
  showRelationships: true
)
```

Look for `isDeprecated` and `deprecationReason` in output.

### 2. Use Stable Query Patterns

Prefer documented query patterns from `patterns/common-queries.md`.

### 3. Handle API Changes Gracefully

```graphql
# Include fallback fields when possible
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node {
                  properties {
                    name
                    # Primary field
                    state
                    # Fallback if structure changes
                    status @skip(if: true)
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

### 4. Pin to Known Working Queries

Document and reuse queries that are known to work:

```
# Use tanzu_common_queries for stable patterns
tanzu_common_queries(pattern: "list_foundations")
```

### 5. Monitor for Deprecation Warnings

API responses may include deprecation warnings:

```json
{
  "data": { ... },
  "extensions": {
    "deprecations": [
      {
        "field": "oldFieldName",
        "reason": "Use newFieldName instead",
        "removalDate": "2024-06-01"
      }
    ]
  }
}
```

## Version Information

### Checking API Version

The MCP server is configured with the API endpoint:
- Base URL: `TANZU_PLATFORM_URL`
- GraphQL Path: `/hub/graphql`

### Schema Version

Schema version may be available in introspection:

```graphql
query SchemaVersion {
  __schema {
    description
  }
}
```

## Migration Guidelines

When an API changes:

1. **Check deprecation notices** - Look for `deprecationReason`
2. **Find replacement** - Deprecation reason usually indicates alternative
3. **Test new approach** - Validate in non-production
4. **Update queries** - Migrate before removal date
5. **Monitor for issues** - Watch for unexpected behavior

## Reporting Issues

If you encounter:
- Unexpected breaking changes
- Missing deprecation warnings
- API inconsistencies

Report through appropriate channels (documentation may provide issue tracker links).

## Caching Considerations

Given potential API changes:

1. **Cache schema** - Reduce repeated introspection
2. **Refresh periodically** - Detect schema changes
3. **Handle cache invalidation** - Clear cache on major updates
4. **Log schema versions** - Track what version you're using
