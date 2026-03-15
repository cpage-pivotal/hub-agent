# Capacity Domain

## Overview

The Capacity domain provides resource management, capacity planning, and optimization recommendations for infrastructure and applications on the Tanzu Platform.

## Key Entity Types

| Entity Type | Description |
|------------|-------------|
| CapacityInfo | Current resource utilization |
| CapacityRecommendation | Optimization recommendations |
| CapacityOptimizeAction | Suggested optimization actions |

## Common Properties

### Capacity Info Properties
- `cpu` - CPU utilization
- `memory` - Memory utilization
- `disk` - Disk utilization
- `timestamp` - Measurement time

### Recommendation Properties
- `classification` - Type of recommendation
- `description` - Human-readable description
- `impact` - Expected impact of action
- `priority` - Recommendation priority

## Common Queries

### Check Capacity Recommendations

```graphql
query CapacityRecs {
  capacityQuery {
    recommendations {
      edges {
        node {
          ... on CapacityOptimizeAction {
            classification
            description
            priority
          }
        }
      }
    }
  }
}
```

### Get Foundation Resource Utilization

```graphql
# Use tanzu_explore_schema to discover exact query structure
```

## Recommendation Classifications

| Classification | Description |
|----------------|-------------|
| SCALE_UP | Increase resources |
| SCALE_DOWN | Decrease resources |
| OPTIMIZE | Improve efficiency |
| MIGRATE | Move workload |

## Related Domains

- **TAS** - Application resource allocation
- **Observability** - Resource metrics and alerts
- **Infrastructure** - Underlying compute resources

## Best Practices

1. **Review recommendations regularly** - Capacity needs change over time
2. **Consider impact** - Some changes may affect availability
3. **Monitor after changes** - Validate recommendations worked
4. **Plan for growth** - Don't just react to current needs

## Notes

- Recommendations are generated based on historical usage patterns
- Some recommendations may require approval before implementation
- Capacity data is refreshed periodically
