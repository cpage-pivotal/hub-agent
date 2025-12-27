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

## Common Properties

### Foundation Properties
- `name` - Foundation name
- `api_endpoint` - Cloud Controller API URL
- `version` - TAS version

### Organization Properties
- `name` - Organization name
- `quota` - Quota definition

### Space Properties
- `name` - Space name
- `organization` - Parent organization

### Application Properties
- `name` - Application name
- `state` - Running state (STARTED, STOPPED)
- `instances` - Number of instances
- `memory` - Memory allocation (MB)
- `disk_quota` - Disk quota (MB)
- `buildpack` - Buildpack used
- `stack` - Stack name
- `detected_start_command` - Start command

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
                  properties {
                    name
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

### List Organizations in Foundation

Use relationship navigation from Foundation:

```graphql
query FoundationOrgs {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 10) {
              edges {
                node {
                  properties { name }
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Organization_Type {
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

### List Applications

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
                  properties {
                    name
                    state
                    instances
                    memory
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

### Find Application's Foundation

Navigate up the hierarchy:

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
                  relationshipsOut {
                    isContainedIn {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Space_Type {
                            properties { name }
                            relationshipsOut {
                              isContainedIn {
                                edges {
                                  node {
                                    ... on Entity_Tanzu_TAS_Organization_Type {
                                      properties { name }
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

## Relationship Types

| Relationship | Direction | From | To | Use Case |
|--------------|-----------|------|-----|----------|
| `isContainedIn` | OUT | Child | Parent | Navigate to parent |
| `contains` | IN | Parent | Children | List children |

## Notes

- Application `state` values: `STARTED`, `STOPPED`
- Memory and disk_quota are in MB
- Use pagination for large result sets
