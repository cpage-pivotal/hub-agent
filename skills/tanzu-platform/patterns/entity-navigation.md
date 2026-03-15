# Entity Navigation Patterns

This document explains how to navigate relationships between entities in the Tanzu Platform GraphQL API.

## CRITICAL: Relationship Field Names

Relationships use **snake_case entity type field names**, NOT generic `contains`:

```graphql
# CORRECT - use snake_case entity type name
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # Snake_case!
      edges {
        node {
          entityName
        }
      }
    }
  }
}

# WRONG - 'contains' does not exist!
relationshipsIn {
  contains {  # ERROR: field doesn't exist!
    edges { ... }
  }
}
```

## Relationship Basics

Every entity has two relationship fields:

- **`relationshipsIn`**: Entities that ARE CONTAINED IN this entity (children)
- **`relationshipsOut`**: Entities that this entity IS CONTAINED IN (parents)

## CRITICAL: Entity Name Field

**The entity name is `entityName` at the entity level, NOT `properties.name`!**

```graphql
# CORRECT
node {
  entityName          # This is the entity's name!
  properties {
    guid              # Properties has other fields
    state
  }
}

# WRONG - name doesn't exist on properties
node {
  properties {
    name              # ERROR: field doesn't exist!
  }
}
```

## Relationship Types and Fields

| Relationship | Direction | Meaning | Field Name Pattern |
|--------------|-----------|---------|-------------------|
| `isContainedIn` | IN | Children of this entity | `tanzu_tas_{entitytype}` |
| `isContainedIn` | OUT | Parent of this entity | `tanzu_tas_{entitytype}` |
| `isAssociatedWith` | IN/OUT | Peer associations | `tanzu_tas_{entitytype}` |

## Navigation Direction

### Navigating DOWN (Parent to Children)

Use `relationshipsIn` with entity-specific field:

```graphql
query SpaceToApps {
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
                      tanzu_tas_application(first: 100) {
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

### Navigating UP (Child to Parent)

Use `relationshipsOut` with entity-specific field:

```graphql
query AppToSpace {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node {
                  entityName
                  relationshipsOut {
                    isContainedIn {
                      tanzu_tas_space(first: 1) {
                        edges {
                          node {
                            entityName
                            properties {
                              guid
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

## TAS Entity Relationship Fields

### Space Relationships

| Direction | RelIn/RelOut Type | Field | Returns |
|-----------|-------------------|-------|---------|
| Children | `Entity_Tanzu_TAS_Space_IsContainedIn_RelIn` | `tanzu_tas_application` | Applications |
| Children | `Entity_Tanzu_TAS_Space_IsContainedIn_RelIn` | `tanzu_tas_serviceinstance` | Service Instances |
| Parent | `Entity_Tanzu_TAS_Space_IsContainedIn_RelOut` | `tanzu_tas_organization` | Organization |

### Organization Relationships

| Direction | Field | Returns |
|-----------|-------|---------|
| Children | `tanzu_tas_space` | Spaces |
| Parent | `tanzu_tas_foundation` | Foundation |

### Foundation Relationships

| Direction | Field | Returns |
|-----------|-------|---------|
| Children | `tanzu_tas_organization` | Organizations |

## Multi-Level Navigation

### Application → Space → Organization → Foundation

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
                  # Level 1: App → Space
                  relationshipsOut {
                    isContainedIn {
                      tanzu_tas_space(first: 1) {
                        edges {
                          node {
                            entityName
                            # Level 2: Space → Org
                            relationshipsOut {
                              isContainedIn {
                                tanzu_tas_organization(first: 1) {
                                  edges {
                                    node {
                                      entityName
                                      # Level 3: Org → Foundation
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

### Foundation → Organizations → Spaces → Applications

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
                  entityName
                  # Level 1: Foundation → Orgs
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_organization(first: 50) {
                        edges {
                          node {
                            entityName
                            # Level 2: Org → Spaces
                            relationshipsIn {
                              isContainedIn {
                                tanzu_tas_space(first: 50) {
                                  edges {
                                    node {
                                      entityName
                                      # Level 3: Space → Apps
                                      relationshipsIn {
                                        isContainedIn {
                                          tanzu_tas_application(first: 100) {
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

## Quick Reference: Relationship Fields

| From | To | Direction | Path |
|------|----|-----------|------|
| Space | Applications | DOWN | `relationshipsIn.isContainedIn.tanzu_tas_application` |
| Space | Organization | UP | `relationshipsOut.isContainedIn.tanzu_tas_organization` |
| Organization | Spaces | DOWN | `relationshipsIn.isContainedIn.tanzu_tas_space` |
| Organization | Foundation | UP | `relationshipsOut.isContainedIn.tanzu_tas_foundation` |
| Foundation | Organizations | DOWN | `relationshipsIn.isContainedIn.tanzu_tas_organization` |
| Application | Space | UP | `relationshipsOut.isContainedIn.tanzu_tas_space` |

## Performance Considerations

1. **Limit depth** - Deep navigation increases query complexity
2. **Use pagination** - Add `first: N` to relationship connections
3. **Select specific fields** - Don't request all properties
4. **Consider direction** - Sometimes starting from the other entity is more efficient

## Common Mistakes

### Wrong relationship field (contains doesn't exist)

```graphql
# WRONG - 'contains' doesn't exist
relationshipsIn {
  contains { ... }  # ERROR!
}

# CORRECT - use snake_case entity type name
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) { ... }
  }
}
```

### Wrong property field (name doesn't exist on properties)

```graphql
# WRONG - name doesn't exist on properties types
node {
  properties { name }  # ERROR!
}

# CORRECT - entityName is at entity level
node {
  entityName
  properties { guid state }
}
```

### Using inline fragments (not needed)

```graphql
# WRONG - inline fragments not needed for relationship fields
relationshipsIn {
  isContainedIn {
    tanzu_tas_application {
      edges {
        node {
          ... on Entity_Tanzu_TAS_Application_Type {  # Unnecessary!
            entityName
          }
        }
      }
    }
  }
}

# CORRECT - relationship fields return typed connections
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {
      edges {
        node {
          entityName
          properties { state }
        }
      }
    }
  }
}
```
