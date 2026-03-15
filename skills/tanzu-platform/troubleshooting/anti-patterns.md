# Anti-Patterns: What NOT to Do

This document describes common query patterns that should be avoided.

## CRITICAL Anti-Patterns (Most Common Mistakes)

### 1. Using `properties.name` (WRONG)

```graphql
# ANTI-PATTERN: name field doesn't exist on properties!
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 10) {
              edges {
                node {
                  properties {
                    name  # WRONG! This field doesn't exist!
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

**Why it's wrong**: The `name` field does NOT exist on `*_Properties` types. Entity names are at the entity level.

**Correct pattern**:
```graphql
node {
  entityName           # Name is at entity level!
  properties {
    guid               # Properties has guid, state, etc.
    state
  }
}
```

### 2. Using Generic `contains` Field (WRONG)

```graphql
# ANTI-PATTERN: 'contains' field doesn't exist!
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 10) {
              edges {
                node {
                  relationshipsIn {
                    contains {  # WRONG! This field doesn't exist!
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
```

**Why it's wrong**: Relationships use snake_case entity type names, not generic `contains`.

**Correct pattern**:
```graphql
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # Use snake_case entity type!
      edges {
        node {
          entityName
        }
      }
    }
  }
}
```

### 3. Using Inline Fragments on Relationships (WRONG)

```graphql
# ANTI-PATTERN: Fragments not needed for typed relationship fields
relationshipsOut {
  isContainedIn {
    edges {
      node {
        ... on Entity_Tanzu_TAS_Space_Type {  # Wrong place!
          entityName
        }
      }
    }
  }
}
```

**Why it's wrong**: Relationship fields return typed connections. Use the entity-specific field instead.

**Correct pattern**:
```graphql
relationshipsOut {
  isContainedIn {
    tanzu_tas_space(first: 1) {
      edges {
        node {
          entityName
        }
      }
    }
  }
}
```

## Query Structure Anti-Patterns

### 4. Direct Entity Type Query (WRONG)

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

### 5. Uppercase Domain Names (WRONG)

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

### 6. Missing Type Suffix in Fragments (WRONG)

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

### 7. PascalCase Relationship Fields (WRONG)

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

### 8. Requesting All Fields (WRONG)

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
                  properties {
                    state
                    instanceCount
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

### 9. Deep Nesting Without Pagination (WRONG)

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
                    isContainedIn {
                      tanzu_tas_organization {  # No pagination!
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
```

**Why it's wrong**: Can return massive results, timeout, or exceed complexity limits.

**Correct pattern**: Add pagination at each level with `first: N`.

### 10. No Pagination (WRONG)

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

### 11. Skipping Validation (WRONG)

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

### 12. Ignoring Validation Suggestions (WRONG)

When `tanzu_validate_query` returns suggestions, ignoring them wastes time.

**Correct pattern**: Apply suggestions before retrying.

## Mutation Anti-Patterns

### 13. Destructive Mutations Without Confirmation (WRONG)

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

## Schema Exploration Anti-Patterns

### 14. Requesting Full Schema (WRONG)

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

### 15. Guessing Type Names (WRONG)

Instead of guessing type names, use schema exploration:

```
# WRONG: Guessing
... on Entity_TAS_Application_Type  # Wrong format!

# CORRECT: Use schema exploration first
tanzu_explore_schema(search: "application", domain: "TAS")
# Then use discovered type name
```

## Quick Reference

| Anti-Pattern | Correct Approach |
|--------------|------------------|
| `properties.name` | Use `entityName` at entity level |
| `relationshipsIn.contains` | Use `relationshipsIn.isContainedIn.tanzu_tas_{entity}` |
| Inline fragments on relationships | Use entity-specific fields directly |
| Direct entity type query | Use typed query hierarchy |
| Uppercase domains | Use lowercase in query paths |
| Missing `_Type` suffix | Always include suffix |
| PascalCase relationship fields | Use camelCase (`isContainedIn`) |
| Requesting all fields | Request only needed fields |
| Deep nesting without pagination | Add pagination at each level |
| Skipping validation | Always validate first |
| Mutations without confirmation | Require confirmation for destructive ops |

## Relationship Field Quick Reference

| To Get | Correct Field |
|--------|---------------|
| Applications | `tanzu_tas_application` |
| Spaces | `tanzu_tas_space` |
| Organizations | `tanzu_tas_organization` |
| Foundations | `tanzu_tas_foundation` |
| Service Instances | `tanzu_tas_serviceinstance` |
