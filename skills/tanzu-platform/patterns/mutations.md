# Mutation Patterns

This document describes safe patterns for executing mutations (create, update, delete operations) against the Tanzu Platform GraphQL API.

## Mutation Basics

Mutations modify data. Unlike queries, mutations:
- Change state on the server
- May have side effects
- Require careful validation
- Should be confirmed before execution (especially destructive ones)

## Using `tanzu_graphql_mutate`

The `tanzu_graphql_mutate` tool includes safety features:
- Detects destructive operations (delete, remove, destroy)
- Requires `confirm: true` for destructive mutations
- Logs mutations for audit trail

## Mutation Structure

```graphql
mutation MutationName($input: InputType!) {
  mutationProvider {
    mutationMethod(input: $input) {
      # Return fields
      id
      status
      # ... other fields
    }
  }
}
```

## Common Mutation Categories

### 1. Alert Mutations

#### Create Metric Alert

```graphql
mutation CreateMetricAlert($input: ObservabilityMetricAlertCreateInput!) {
  observabilityAlertMutationProvider {
    createMetricAlert(input: $input) {
      id
      name
      status
    }
  }
}
```

Variables:
```json
{
  "input": {
    "name": "High CPU Alert",
    "condition": {
      "metric": "cpu_usage",
      "threshold": 80,
      "operator": "GREATER_THAN"
    },
    "severity": "HIGH",
    "notificationTargets": ["target-id-1"]
  }
}
```

#### Update Alert

```graphql
mutation UpdateAlert($id: ID!, $input: AlertUpdateInput!) {
  observabilityAlertMutationProvider {
    updateAlert(id: $id, input: $input) {
      id
      name
      status
    }
  }
}
```

#### Delete Alert (Destructive)

```graphql
mutation DeleteAlert($id: ID!) {
  observabilityAlertMutationProvider {
    deleteAlert(id: $id) {
      success
    }
  }
}
```

**Note**: Requires `confirm: true` in `tanzu_graphql_mutate`.

### 2. Policy Mutations

#### Create Policy

```graphql
mutation CreatePolicy($input: PolicyCreateInput!) {
  policyMutationProvider {
    createPolicy(input: $input) {
      id
      name
      status
    }
  }
}
```

#### Update Policy

```graphql
mutation UpdatePolicy($id: ID!, $input: PolicyUpdateInput!) {
  policyMutationProvider {
    updatePolicy(id: $id, input: $input) {
      id
      name
    }
  }
}
```

### 3. Notification Target Mutations

#### Create Notification Target

```graphql
mutation CreateNotificationTarget($input: NotificationTargetCreateInput!) {
  observabilityNotificationMutationProvider {
    createNotificationTarget(input: $input) {
      id
      name
      type
    }
  }
}
```

## Mutation Best Practices

### 1. Always Validate First

Before executing a mutation, validate the query:

```
tanzu_validate_query(
  query: "mutation CreateAlert($input: ...) { ... }",
  variables: { "input": { ... } }
)
```

### 2. Use Variables for Input

Never inline sensitive data:

```graphql
# WRONG - Hardcoded values
mutation {
  createAlert(input: { name: "Alert", threshold: 80 }) { ... }
}

# CORRECT - Use variables
mutation CreateAlert($input: AlertInput!) {
  createAlert(input: $input) { ... }
}
```

### 3. Request Confirmation for Destructive Operations

The `tanzu_graphql_mutate` tool automatically detects destructive mutations and requires confirmation:

```
tanzu_graphql_mutate(
  mutation: "mutation DeleteAlert($id: ID!) { deleteAlert(id: $id) { success } }",
  variables: { "id": "alert-123" },
  confirm: true  # Required for delete operations
)
```

### 4. Return Relevant Fields

Always return fields that confirm the operation succeeded:

```graphql
mutation CreatePolicy($input: PolicyInput!) {
  createPolicy(input: $input) {
    id          # Confirms creation
    name        # Confirms correct data
    status      # Confirms state
    createdAt   # Confirms timing
  }
}
```

### 5. Handle Errors Gracefully

Mutations can fail. Always check for errors:

```json
{
  "data": null,
  "errors": [
    {
      "message": "Insufficient permissions",
      "path": ["createPolicy"]
    }
  ]
}
```

## Discovering Available Mutations

Use `tanzu_explore_schema` to find mutation types:

```
tanzu_explore_schema(
  search: "mutation",
  category: "OBJECT"
)
```

Or explore specific mutation providers:

```
tanzu_explore_schema(
  typeName: "ObservabilityAlertMutationProvider"
)
```

## Mutation Input Types

Mutation inputs typically end with `Input`:
- `AlertCreateInput`
- `PolicyUpdateInput`
- `NotificationTargetCreateInput`

Explore input types to understand required fields:

```
tanzu_explore_schema(
  typeName: "AlertCreateInput"
)
```

## Transactional Considerations

1. **Atomicity** - Most mutations are atomic
2. **Idempotency** - Some mutations can be safely retried
3. **Ordering** - Multiple mutations execute in order
4. **Rollback** - No automatic rollback; plan accordingly

## Error Recovery

### Mutation Failed

If a mutation fails:
1. Check error message for details
2. Verify input data is valid
3. Check permissions
4. Retry if transient error

### Partial Success

Some batch mutations may partially succeed:
1. Check which items succeeded
2. Retry failed items individually
3. Verify final state with a query

## Audit Trail

All mutations executed via `tanzu_graphql_mutate` are logged:
- Mutation operation
- Variables (sanitized)
- Timestamp
- Success/failure status

## Security Considerations

1. **Validate inputs** - Don't trust user input
2. **Check permissions** - Ensure user has required access
3. **Limit scope** - Request minimum necessary permissions
4. **Confirm destructive ops** - Always require confirmation for delete/remove
5. **Log everything** - Maintain audit trail
