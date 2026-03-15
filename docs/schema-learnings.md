# Tanzu Platform GraphQL API - Critical Schema Learnings

This document captures critical learnings about the Tanzu Platform GraphQL API that are essential for constructing valid queries. These findings were discovered through hands-on testing and error analysis.

> **⚠️ Read this before writing any queries!** The API has several non-obvious patterns that differ from typical GraphQL conventions.

## Executive Summary

| What You Might Expect | What Actually Works |
|----------------------|---------------------|
| `properties.name` | `entityName` at entity level |
| `relationshipsIn.contains` | `relationshipsIn.isContainedIn.tanzu_tas_{entity}` |
| `... on EntityType { }` in relationships | Direct field access - no fragments needed |
| PascalCase for everything | Query paths are lowercase, relationship fields are snake_case |

---

## Critical Learning #1: Entity Names

### The Problem

Entity types like `Entity_Tanzu_TAS_Space_Properties` do **NOT** have a `name` field.

```graphql
# THIS FAILS!
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
                    name    # ❌ ERROR: Cannot query field "name" on type "Entity_Tanzu_TAS_Space_Properties"
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

### The Solution

Entity names are at the **entity level** via `entityName`, not inside `properties`:

```graphql
# THIS WORKS!
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 10) {
              edges {
                node {
                  entityName    # ✅ This is the entity's name
                  properties {
                    guid        # Properties has guid, state, etc.
                    totalAppCount
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

### Entity Level Fields (Available on ALL entities)

```graphql
node {
  id              # Opaque global ID
  entityId        # Canonical entity identifier
  entityName      # Human-readable name (THE NAME!)
  entityType      # Type discriminator
  properties      # Type-specific properties
  relationshipsIn # Incoming relationships (children)
  relationshipsOut # Outgoing relationships (parent)
  tags            # Key/value tags
}
```

---

## Critical Learning #2: Relationship Navigation

### The Problem

Relationship navigation does **NOT** use generic `contains` or `isContainedIn` fields that return union types.

```graphql
# THIS FAILS!
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
                    contains {    # ❌ ERROR: Cannot query field "contains"
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

### The Solution

Relationships use **snake_case entity type field names**:

```graphql
# THIS WORKS!
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 10) {
              edges {
                node {
                  entityName
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_application(first: 100) {  # ✅ Snake_case entity type!
                        edges {
                          node {
                            entityName
                            properties {
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
        }
      }
    }
  }
}
```

### Relationship Field Reference

| From Entity | To Get | Relationship Path |
|-------------|--------|-------------------|
| Space | Applications | `relationshipsIn.isContainedIn.tanzu_tas_application` |
| Space | Service Instances | `relationshipsIn.isContainedIn.tanzu_tas_serviceinstance` |
| Organization | Spaces | `relationshipsIn.isContainedIn.tanzu_tas_space` |
| Foundation | Organizations | `relationshipsIn.isContainedIn.tanzu_tas_organization` |
| Application | Space (parent) | `relationshipsOut.isContainedIn.tanzu_tas_space` |
| Space | Organization (parent) | `relationshipsOut.isContainedIn.tanzu_tas_organization` |
| Organization | Foundation (parent) | `relationshipsOut.isContainedIn.tanzu_tas_foundation` |

### Relationship Direction

- **`relationshipsIn`** → Entities that ARE CONTAINED IN this entity (children)
- **`relationshipsOut`** → Entities that this entity IS CONTAINED IN (parent)

---

## Critical Learning #3: No Inline Fragments Needed

### The Problem

Unlike typical GraphQL union/interface navigation, relationship fields return **typed connections**, not union types.

```graphql
# THIS IS UNNECESSARY AND CAN CAUSE ERRORS!
relationshipsIn {
  isContainedIn {
    ... on Entity_Tanzu_TAS_ApplicationConnection {  # ❌ Wrong
      edges { ... }
    }
  }
}
```

### The Solution

Access the entity-specific field directly:

```graphql
# THIS WORKS!
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # ✅ Direct field access
      edges {
        node {
          entityName
        }
      }
    }
  }
}
```

---

## Critical Learning #4: Query Hierarchy

### The Required Structure

All entity queries must follow this hierarchy:

```
entityQuery → typed → tanzu → {domain} → {entityType} → query(first: N)
```

### Domain Names (lowercase)

| Domain | Query Path |
|--------|------------|
| TAS (Tanzu Application Service) | `tanzu.tas` |
| Spring | `tanzu.spring` |
| Platform | `tanzu.platform` |

### Entity Type Names (lowercase in queries)

| Entity | Query Path |
|--------|------------|
| Foundation | `tas.foundation` |
| Organization | `tas.organization` |
| Space | `tas.space` |
| Application | `tas.application` |

### Full Type Names (PascalCase with _Type suffix)

| Query Path | Full Type Name |
|------------|----------------|
| `tas.foundation` | `Entity_Tanzu_TAS_Foundation_Type` |
| `tas.organization` | `Entity_Tanzu_TAS_Organization_Type` |
| `tas.space` | `Entity_Tanzu_TAS_Space_Type` |
| `tas.application` | `Entity_Tanzu_TAS_Application_Type` |

---

## Critical Learning #5: Properties Types

Each entity type has a corresponding `*_Properties` type with specific fields. These do **NOT** include `name`.

### Application Properties (`Entity_Tanzu_TAS_Application_Properties`)

| Field | Type | Description |
|-------|------|-------------|
| `guid` | String | Application GUID |
| `state` | String | `STARTED` or `STOPPED` |
| `health_status` | String | `RUNNING`, `DOWN`, or `STOPPED` |
| `instanceCount` | Int | Desired instances |
| `runningInstanceCount` | Int | Running instances |
| `crashedInstanceCount` | Int | Crashed instances |
| `buildpack` | String | Buildpack name |
| `spaceGUID` | String | Parent space GUID |
| `foundation` | String | Foundation name |
| `routes` | [String] | Application routes |
| `totalMemoryLimitMB` | Int | Memory limit |
| `springApp` | Boolean | Is Spring Boot app? |

### Space Properties (`Entity_Tanzu_TAS_Space_Properties`)

| Field | Type | Description |
|-------|------|-------------|
| `guid` | String | Space GUID |
| `foundation` | String | Foundation name |
| `organizationGUID` | String | Parent org GUID |
| `totalAppCount` | Int | Number of apps |
| `totalMemoryLimitMB` | Int | Memory used |
| `totalMemoryQuotaMB` | Int | Memory quota |

---

## Complete Working Examples

### Example 1: List Spaces with Their Applications

This is the correct query for "find spaces with stopped apps":

```graphql
query SpacesWithApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 100) {
              edges {
                node {
                  entityName
                  properties {
                    guid
                    totalAppCount
                    foundation
                  }
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_application(first: 100) {
                        edges {
                          node {
                            entityName
                            properties {
                              state
                              health_status
                            }
                          }
                        }
                      }
                    }
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

### Example 2: Navigate Application → Space → Organization → Foundation

```graphql
query AppToFoundation {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 5) {
              edges {
                node {
                  entityName
                  properties { state }
                  relationshipsOut {
                    isContainedIn {
                      tanzu_tas_space(first: 1) {
                        edges {
                          node {
                            entityName
                            relationshipsOut {
                              isContainedIn {
                                tanzu_tas_organization(first: 1) {
                                  edges {
                                    node {
                                      entityName
                                      relationshipsOut {
                                        isContainedIn {
                                          tanzu_tas_foundation(first: 1) {
                                            edges {
                                              node {
                                                entityName
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
              }
            }
          }
        }
      }
    }
  }
}
```

### Example 3: Simple Application List

```graphql
query ListApplications {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 50) {
              edges {
                node {
                  id
                  entityName
                  properties {
                    state
                    health_status
                    instanceCount
                    runningInstanceCount
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

---

## Common Errors and Fixes

### Error: Cannot query field "name" on type "*_Properties"

**Cause:** Using `properties.name`
**Fix:** Use `entityName` at entity level

### Error: Cannot query field "contains" on type "*_RelIn"

**Cause:** Using generic `contains` field
**Fix:** Use `isContainedIn.tanzu_tas_{entity}(first: N)`

### Error: Fragment cannot be spread here

**Cause:** Using inline fragments on relationship types
**Fix:** Use the entity-specific field directly

### Error: Cannot query field "TAS" on type "TanzuQuery"

**Cause:** Using uppercase domain name
**Fix:** Use lowercase: `tanzu.tas`

---

## Schema Structure Overview

```
Entity_Tanzu_TAS_Space_Type
├── id: ID!
├── entityId: EntityId!
├── entityName: String              # THE NAME IS HERE!
├── entityType: String
├── properties: Entity_Tanzu_TAS_Space_Properties
│   ├── guid: String
│   ├── foundation: String
│   ├── organizationGUID: String
│   ├── totalAppCount: Int
│   └── ... (no 'name' field!)
├── relationshipsIn: Entity_Tanzu_TAS_Space_RelIn
│   └── isContainedIn: Entity_Tanzu_TAS_Space_IsContainedIn_RelIn
│       ├── tanzu_tas_application(first, after, ...): ApplicationConnection
│       └── tanzu_tas_serviceinstance(first, after, ...): ServiceInstanceConnection
├── relationshipsOut: Entity_Tanzu_TAS_Space_RelOut
│   └── isContainedIn: Entity_Tanzu_TAS_Space_IsContainedIn_RelOut
│       └── tanzu_tas_organization(first, after, ...): OrganizationConnection
└── tags: [Tag!]
```

---

## Key Takeaways

1. **Entity names are at entity level** (`entityName`), not in `properties`
2. **Relationships use snake_case** entity type fields (`tanzu_tas_application`)
3. **No generic `contains`** - use entity-specific relationship fields
4. **No inline fragments** needed on relationship fields
5. **Query paths are lowercase** (`tas`, `foundation`, not `TAS`, `Foundation`)
6. **Always include pagination** (`first: N`) on queries and relationships

