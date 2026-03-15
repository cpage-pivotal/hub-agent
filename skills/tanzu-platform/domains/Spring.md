# Spring Domain

## Overview

The Spring domain provides monitoring and management capabilities for Spring Boot applications deployed on the Tanzu Platform.

## Key Entity Types

| Entity Type | Description |
|------------|-------------|
| SpringArtifact | Spring Boot application artifact |
| SpringArtifactMetadata | Metadata about Spring artifacts |
| Dependency | Application dependencies |
| Runtime | Spring Boot runtime information |

## Common Properties

### Spring Artifact Properties
- `name` - Artifact name
- `version` - Artifact version
- `springBootVersion` - Spring Boot version
- `javaVersion` - Java runtime version
- `dependencies` - List of dependencies

## Common Queries

### List Spring Applications

```graphql
query ListSpringApps {
  entityQuery {
    typed {
      tanzu {
        spring {
          # Query Spring artifacts here
        }
      }
    }
  }
}
```

### Find Spring Boot Version

```graphql
# Use tanzu_explore_schema to discover exact field names
```

## Related Domains

- **TAS** - Spring apps often run on TAS
- **Security** - Dependency vulnerabilities
- **Observability** - Application metrics

## Notes

- Spring artifacts may be associated with TAS applications
- Dependency analysis reveals transitive dependencies
- Use `tanzu_explore_schema` with `domain: "Spring"` to discover available types
