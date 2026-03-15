# TAS Domain (Tanzu Application Service)

## Overview

TAS (Tanzu Application Service) is the Cloud Foundry-based application platform. This domain contains entities for managing foundations, organizations, spaces, and applications.

## Entity Hierarchy

```
Foundation
└── Organization
    └── Space
        └── Application
            ├── Route
            ├── Service Binding
            └── Environment Variables
```

## Key Entity Types

| Entity Type | GraphQL Type Name | Description |
|------------|-------------------|-------------|
| Foundation | `Entity_Tanzu_TAS_Foundation_Type` | TAS installation/deployment |
| Organization | `Entity_Tanzu_TAS_Organization_Type` | Tenant isolation boundary |
| Space | `Entity_Tanzu_TAS_Space_Type` | Application deployment target |
| Application | `Entity_Tanzu_TAS_Application_Type` | Deployed application |
| BOSH Director | `Entity_Tanzu_TAS_BOSHDirector_Type` | Infrastructure manager |
| Ops Manager | `Entity_Tanzu_TAS_OpsManager_Type` | Tile management |

## CRITICAL: Entity Name Field

**The entity name is `entityName` at the entity level, NOT `properties.name`!**

```graphql
# CORRECT
node {
  entityName          # This is the entity's name!
  properties {
    guid              # Properties has guid, state, etc.
  }
}

# WRONG - name doesn't exist on properties
node {
  properties {
    name              # ERROR: field doesn't exist!
  }
}
```

## Common Entity Fields (on all TAS entities)

```graphql
node {
  id                  # Opaque global ID
  entityId            # Canonical entity identifier
  entityName          # Human-readable name (THE NAME!)
  entityType          # Type discriminator
  properties { ... }  # Type-specific properties
  relationshipsIn { ... }
  relationshipsOut { ... }
  tags { key value }
}
```

## Application Properties

`Entity_Tanzu_TAS_Application_Properties`:

| Field | Type | Description |
|-------|------|-------------|
| `guid` | String | Application GUID |
| `state` | String | `STARTED` or `STOPPED` |
| `health_status` | String | `RUNNING`, `DOWN`, or `STOPPED` |
| `instanceCount` | Int | Desired instances |
| `runningInstanceCount` | Int | Running instances |
| `crashedInstanceCount` | Int | Crashed instances |
| `buildpack` | String | Buildpack name |
| `buildpackType` | String | Buildpack type |
| `spaceGUID` | String | Parent space GUID |
| `foundation` | String | Foundation name |
| `routes` | [String] | Application routes |
| `totalMemoryLimitMB` | Int | Memory limit (MB) |
| `stack` | String | Root filesystem |
| `springApp` | Boolean | Is Spring Boot app? |
| `sbomPresent` | Boolean | Has SBOM? |

## Space Properties

`Entity_Tanzu_TAS_Space_Properties`:

| Field | Type | Description |
|-------|------|-------------|
| `guid` | String | Space GUID |
| `foundation` | String | Foundation name |
| `organizationGUID` | String | Parent organization GUID |
| `totalAppCount` | Int | Number of applications |
| `totalMemoryLimitMB` | Int | Memory used (MB) |
| `totalMemoryQuotaMB` | Int | Memory quota (MB) |
| `totalInstancesQuota` | Int | Instance quota |
| `totalServiceInstanceCount` | Int | Service instances |
| `totalRoutesQuota` | Int | Routes quota |

## Organization Properties

`Entity_Tanzu_TAS_Organization_Properties`:

| Field | Type | Description |
|-------|------|-------------|
| `guid` | String | Organization GUID |
| `foundation` | String | Foundation name |

## Common Queries

### List Foundations

```graphql
query ListFoundations {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 20) {
              edges {
                node {
                  id
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
```

### List Applications with State

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

### Find Stopped Applications

```graphql
query FindStoppedApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 100) {
              edges {
                node {
                  entityName
                  properties {
                    state
                    health_status
                    spaceGUID
                    foundation
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
# Filter client-side for state = "STOPPED"
```

### Spaces with Their Applications

This is the CORRECT way to get applications within spaces:

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
            }
          }
        }
      }
    }
  }
}
```

### Application to Space Navigation

Navigate from application up to its space:

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
                  properties {
                    state
                  }
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

## Relationship Navigation

### CRITICAL: Use snake_case Entity Field Names

Relationships do NOT use generic `contains` - they use **snake_case entity type names**:

| From | To | Path |
|------|----|------|
| Space | Applications | `relationshipsIn.isContainedIn.tanzu_tas_application` |
| Organization | Spaces | `relationshipsIn.isContainedIn.tanzu_tas_space` |
| Foundation | Organizations | `relationshipsIn.isContainedIn.tanzu_tas_organization` |
| Application | Space | `relationshipsOut.isContainedIn.tanzu_tas_space` |
| Space | Organization | `relationshipsOut.isContainedIn.tanzu_tas_organization` |
| Organization | Foundation | `relationshipsOut.isContainedIn.tanzu_tas_foundation` |

### Relationship Direction

- **`relationshipsIn`**: Entities contained IN this entity (children)
- **`relationshipsOut`**: Entity this is contained IN (parent)

### Multi-Level Navigation: Application to Foundation

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
                  # App → Space
                  relationshipsOut {
                    isContainedIn {
                      tanzu_tas_space(first: 1) {
                        edges {
                          node {
                            entityName
                            # Space → Organization
                            relationshipsOut {
                              isContainedIn {
                                tanzu_tas_organization(first: 1) {
                                  edges {
                                    node {
                                      entityName
                                      # Org → Foundation
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

## Notes

- Application `state` values: `STARTED`, `STOPPED`
- Application `health_status` values: `RUNNING`, `DOWN`, `STOPPED`
- Memory and disk quota are in MB
- Use pagination for large result sets
- Entity name is `entityName`, NOT `properties.name`
