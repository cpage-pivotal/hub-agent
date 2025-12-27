# Entity Navigation Patterns

This document explains how to navigate relationships between entities in the Tanzu Platform GraphQL API.

## Relationship Basics

Every entity has two relationship fields:

- **`relationshipsOut`**: Outgoing relationships (this entity points TO other entities)
- **`relationshipsIn`**: Incoming relationships (other entities point TO this entity)

## Relationship Types

| Relationship | Direction | Meaning | Example |
|--------------|-----------|---------|---------|
| `isContainedIn` | OUT | Child → Parent | App → Space |
| `contains` | IN | Parent → Children | Space → Apps |
| `isAssociatedWith` | OUT | Peer association | App → Service |
| `isDeployedBy` | OUT | Deployment relationship | Foundation → OpsManager |

## Navigation Direction

### Navigating UP (Child to Parent)

Use `relationshipsOut` with `isContainedIn`:

```graphql
query NavigateUp {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 1) {
              edges {
                node {
                  properties { name }
                  relationshipsOut {
                    isContainedIn {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Space_Type {
                            properties { name }
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

### Navigating DOWN (Parent to Children)

Use `relationshipsIn` with `contains`:

```graphql
query NavigateDown {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 1) {
              edges {
                node {
                  properties { name }
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Application_Type {
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
        }
      }
    }
  }
}
```

## Multi-Level Navigation

### Application → Foundation (3 levels up)

```graphql
query AppToFoundation {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 1) {
              edges {
                node {
                  properties { name }
                  # Level 1: App → Space
                  relationshipsOut {
                    isContainedIn {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Space_Type {
                            properties { name }
                            # Level 2: Space → Org
                            relationshipsOut {
                              isContainedIn {
                                edges {
                                  node {
                                    ... on Entity_Tanzu_TAS_Organization_Type {
                                      properties { name }
                                      # Level 3: Org → Foundation
                                      relationshipsOut {
                                        isContainedIn {
                                          edges {
                                            node {
                                              ... on Entity_Tanzu_TAS_Foundation_Type {
                                                properties { name }
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

### Foundation → Applications (3 levels down)

```graphql
query FoundationToApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 1) {
              edges {
                node {
                  properties { name }
                  # Level 1: Foundation → Orgs
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Organization_Type {
                            properties { name }
                            # Level 2: Org → Spaces
                            relationshipsIn {
                              contains {
                                edges {
                                  node {
                                    ... on Entity_Tanzu_TAS_Space_Type {
                                      properties { name }
                                      # Level 3: Space → Apps
                                      relationshipsIn {
                                        contains {
                                          edges {
                                            node {
                                              ... on Entity_Tanzu_TAS_Application_Type {
                                                properties { name state instances }
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

## Using `tanzu_find_entity_path`

For complex navigation, use the `tanzu_find_entity_path` tool:

```
tanzu_find_entity_path(
  fromType: "Entity_Tanzu_TAS_Application_Type",
  toType: "Entity_Tanzu_TAS_Foundation_Type",
  maxDepth: 5
)
```

This returns:
- Available paths between entities
- The relationship fields to use at each step
- A query template for the navigation

## Entity Type Hierarchy

```
Foundation
└── Organization (isContainedIn Foundation)
    └── Space (isContainedIn Organization)
        └── Application (isContainedIn Space)
```

## Common Navigation Patterns

| From | To | Path | Relationship Chain |
|------|-----|------|-------------------|
| App | Space | 1 step | `relationshipsOut.isContainedIn` |
| App | Org | 2 steps | App → Space → Org |
| App | Foundation | 3 steps | App → Space → Org → Foundation |
| Foundation | Apps | 3 steps | Foundation → Org → Space → App |
| Space | Apps | 1 step | `relationshipsIn.contains` |
| Org | Spaces | 1 step | `relationshipsIn.contains` |

## Inline Fragments

When navigating relationships, use inline fragments to access type-specific fields:

```graphql
... on Entity_Tanzu_TAS_Application_Type {
  properties {
    name
    state
  }
}
```

The fragment syntax is required because relationship edges can contain multiple entity types.

## Performance Considerations

1. **Limit depth** - Deep navigation increases query complexity
2. **Use pagination** - Add `first: N` to relationship connections
3. **Select specific fields** - Don't request all properties
4. **Consider direction** - Sometimes starting from the other entity is more efficient

## Common Mistakes

### Wrong relationship direction

```graphql
# WRONG - isContainedIn is for going UP, not down
relationshipsIn {
  isContainedIn { ... }  # This doesn't make sense
}

# CORRECT - Use contains for going down
relationshipsIn {
  contains { ... }
}
```

### Wrong relationship field case

```graphql
# WRONG - Relationship fields are camelCase
relationshipsOut {
  IsContainedIn { ... }  # Capital I is wrong
}

# CORRECT
relationshipsOut {
  isContainedIn { ... }
}
```

### Missing inline fragment

```graphql
# WRONG - Can't directly access properties
relationshipsOut {
  isContainedIn {
    edges {
      node {
        properties { name }  # Error: node could be any type
      }
    }
  }
}

# CORRECT - Use inline fragment
relationshipsOut {
  isContainedIn {
    edges {
      node {
        ... on Entity_Tanzu_TAS_Space_Type {
          properties { name }
        }
      }
    }
  }
}
```
