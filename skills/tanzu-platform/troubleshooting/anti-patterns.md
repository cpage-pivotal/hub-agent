# Anti-Patterns: What NOT to Do

This document describes common query patterns that should be avoided.

## Query Structure Anti-Patterns

### 1. Direct Entity Type Query (WRONG)

```graphql
# ANTI-PATTERN: Querying entity types directly
query {
  entityQuery {
    Entity_Tanzu_TAS_Foundation_Type(first: 10) {
      edges {
        node { ... }
      }
    }
  }
}
```

**Why it's wrong**: The API requires the typed query hierarchy.

**Correct pattern**:
```graphql
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 10) { ... }
          }
        }
      }
    }
  }
}
```

### 2. Uppercase Domain Names (WRONG)

```graphql
# ANTI-PATTERN: Uppercase domains
query {
  entityQuery {
    typed {
      tanzu {
        TAS {  # Wrong! Should be lowercase
          Foundation { ... }
        }
      }
    }
  }
}
```

**Why it's wrong**: Query paths use lowercase.

**Correct pattern**:
```graphql
tanzu {
  tas {
    foundation { ... }
  }
}
```

### 3. Missing Type Suffix in Fragments (WRONG)

```graphql
# ANTI-PATTERN: Missing _Type suffix
... on Entity_Tanzu_TAS_Application {
  properties { ... }
}
```

**Why it's wrong**: Entity types always end with `_Type`.

**Correct pattern**:
```graphql
... on Entity_Tanzu_TAS_Application_Type {
  properties { ... }
}
```

### 4. Wrong Relationship Direction (WRONG)

```graphql
# ANTI-PATTERN: Using isContainedIn for parent-to-child
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 1) {
              edges {
                node {
                  relationshipsIn {
                    isContainedIn {  # Wrong direction!
                      ...
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

**Why it's wrong**: `isContainedIn` is for child→parent, not parent→children.

**Correct pattern**:
```graphql
# For parent to children: use relationshipsIn.contains
relationshipsIn {
  contains { ... }
}

# For child to parent: use relationshipsOut.isContainedIn
relationshipsOut {
  isContainedIn { ... }
}
```

### 5. PascalCase Relationship Fields (WRONG)

```graphql
# ANTI-PATTERN: Wrong case for relationship field
relationshipsOut {
  IsContainedIn {  # Wrong! Capital I
    ...
  }
}
```

**Why it's wrong**: Relationship fields are camelCase.

**Correct pattern**:
```graphql
relationshipsOut {
  isContainedIn {  # Correct: lowercase i
    ...
  }
}
```

## Performance Anti-Patterns

### 6. Requesting All Fields (WRONG)

```graphql
# ANTI-PATTERN: Selecting all possible fields
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
                    health_check_type
                    health_check_timeout
                    # ... every possible field
                  }
                  tags { key value }
                  relationshipsIn { ... }
                  relationshipsOut { ... }
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

**Why it's wrong**: Increases query complexity, response size, and execution time.

**Correct pattern**: Request only needed fields.

### 7. Deep Nesting Without Pagination (WRONG)

```graphql
# ANTI-PATTERN: Deep traversal without limits
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query {  # No pagination!
              edges {
                node {
                  relationshipsIn {
                    contains {  # No pagination!
                      edges {
                        node {
                          relationshipsIn {
                            contains {  # No pagination!
                              edges { ... }
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

**Why it's wrong**: Can return massive results, timeout, or exceed complexity limits.

**Correct pattern**: Add pagination at each level.

### 8. No Pagination (WRONG)

```graphql
# ANTI-PATTERN: Missing first/after arguments
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query {  # No pagination arguments!
              edges { ... }
            }
          }
        }
      }
    }
  }
}
```

**Why it's wrong**: May return unbounded results.

**Correct pattern**:
```graphql
query(first: 50) {
  edges { ... }
  pageInfo {
    hasNextPage
    endCursor
  }
}
```

## Validation Anti-Patterns

### 9. Skipping Validation (WRONG)

```
# ANTI-PATTERN: Execute without validation
tanzu_graphql_query(
  query: "untested query string"
)
```

**Why it's wrong**: Wastes API calls on invalid queries.

**Correct pattern**:
```
# Always validate first
tanzu_validate_query(query: "...")
# Then execute
tanzu_graphql_query(query: "...")
```

### 10. Ignoring Validation Suggestions (WRONG)

When `tanzu_validate_query` returns suggestions, ignoring them wastes time.

**Correct pattern**: Apply suggestions before retrying.

## Mutation Anti-Patterns

### 11. Destructive Mutations Without Confirmation (WRONG)

```
# ANTI-PATTERN: Delete without confirmation
tanzu_graphql_mutate(
  mutation: "mutation { deleteAlert(id: '123') { success } }"
  # Missing confirm: true
)
```

**Why it's wrong**: Accidental destructive changes.

**Correct pattern**:
```
tanzu_graphql_mutate(
  mutation: "mutation { deleteAlert(id: '123') { success } }",
  confirm: true
)
```

### 12. Inline Sensitive Data (WRONG)

```graphql
# ANTI-PATTERN: Hardcoded values
mutation {
  createNotificationTarget(input: {
    name: "Slack",
    webhook: "https://hooks.slack.com/secret-token"
  }) { ... }
}
```

**Why it's wrong**: Exposes sensitive data in logs and history.

**Correct pattern**: Use variables for sensitive data.

## Schema Exploration Anti-Patterns

### 13. Requesting Full Schema (WRONG)

```
# ANTI-PATTERN: No filtering
tanzu_explore_schema()  # Returns too much data
```

**Why it's wrong**: 1,382 types is too much to process effectively.

**Correct pattern**:
```
tanzu_explore_schema(
  domain: "TAS",
  search: "application",
  category: "OBJECT"
)
```

### 14. Guessing Type Names (WRONG)

Instead of guessing type names, use schema exploration:

```
# WRONG: Guessing
... on Entity_TAS_Application_Type  # Wrong format!

# CORRECT: Use schema exploration first
tanzu_explore_schema(search: "application", domain: "TAS")
# Then use discovered type name
```

## Summary

| Anti-Pattern | Correct Approach |
|--------------|------------------|
| Direct entity type query | Use typed query hierarchy |
| Uppercase domains | Use lowercase in query paths |
| Missing `_Type` suffix | Always include suffix |
| Wrong relationship direction | Match direction to navigation |
| PascalCase relationship fields | Use camelCase |
| Requesting all fields | Request only needed fields |
| Deep nesting without pagination | Add pagination at each level |
| Skipping validation | Always validate first |
| Mutations without confirmation | Require confirmation for destructive ops |
| Full schema requests | Use domain/search filters |
