# Observability Domain

## Overview

The Observability domain provides monitoring, alerting, and logging capabilities for applications and infrastructure on the Tanzu Platform.

## Key Entity Types

| Entity Type | Description |
|------------|-------------|
| Alert | Active or configured alerts |
| Metric | Application and infrastructure metrics |
| Log | Log entries and streams |
| NotificationTarget | Alert notification endpoints |
| Dashboard | Metric dashboards |

## Common Properties

### Alert Properties
- `name` - Alert name
- `severity` - Alert severity level
- `status` - Current status (FIRING, RESOLVED, PENDING)
- `condition` - Alert trigger condition
- `notificationTargets` - Where to send notifications

### Metric Properties
- `name` - Metric name
- `value` - Current value
- `timestamp` - Measurement time
- `labels` - Metric labels/tags

## Common Queries

### List Active Alerts

```graphql
query ActiveAlerts {
  observabilityAlertQueryProvider {
    alerts(filter: { status: FIRING }) {
      edges {
        node {
          name
          severity
          status
          condition
        }
      }
    }
  }
}
```

### List Notification Targets

```graphql
query NotificationTargets {
  # Use tanzu_explore_schema to discover query structure
}
```

## Alert Severities

| Severity | Description |
|----------|-------------|
| CRITICAL | Immediate action required |
| HIGH | Urgent attention needed |
| MEDIUM | Should be addressed soon |
| LOW | Informational |

## Alert Statuses

| Status | Description |
|--------|-------------|
| FIRING | Alert condition is active |
| RESOLVED | Alert condition no longer active |
| PENDING | Alert is being evaluated |

## Creating Alerts

Use `tanzu_graphql_mutate` with the alert creation mutation. See `patterns/mutations.md` for examples.

## Related Domains

- **TAS** - Application metrics and alerts
- **Spring** - Spring Boot Actuator metrics
- **Capacity** - Resource utilization alerts

## Notes

- Alert configurations may reference specific entities
- Notification targets support various protocols (email, webhook, Slack, etc.)
- Use pagination for large metric result sets
