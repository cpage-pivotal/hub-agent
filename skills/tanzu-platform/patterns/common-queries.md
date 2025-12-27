# Common Query Patterns

This document contains 20+ pre-built query templates for frequent operations.

## Foundation Queries

### 1. List All Foundations

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

### 2. Get Foundation Details

```graphql
query FoundationDetails($first: Int = 10) {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: $first) {
              edges {
                node {
                  id
                  entityId
                  entityName
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

## Organization Queries

### 3. List Organizations

```graphql
query ListOrganizations {
  entityQuery {
    typed {
      tanzu {
        tas {
          organization {
            query(first: 50) {
              edges {
                node {
                  id
                  properties {
                    name
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

### 4. Get Organization with Spaces

```graphql
query OrgWithSpaces {
  entityQuery {
    typed {
      tanzu {
        tas {
          organization {
            query(first: 10) {
              edges {
                node {
                  properties { name }
                  relationshipsIn {
                    contains {
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

## Space Queries

### 5. List Spaces

```graphql
query ListSpaces {
  entityQuery {
    typed {
      tanzu {
        tas {
          space {
            query(first: 100) {
              edges {
                node {
                  id
                  properties {
                    name
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

## Application Queries

### 6. List All Applications

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

### 7. Get Application Details

```graphql
query AppDetails {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node {
                  id
                  entityId
                  properties {
                    name
                    state
                    instances
                    memory
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

### 8. Find Stopped Applications

```graphql
query StoppedApps {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 50) {
              edges {
                node {
                  properties {
                    name
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
# Note: Filter client-side for state = "STOPPED"
```

## Security Queries

### 9. Find Critical Vulnerabilities

```graphql
query CriticalVulnerabilities {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: CRITICAL }) {
      edges {
        node {
          cveId
          severity
          score {
            value
            type
          }
        }
      }
    }
  }
}
```

### 10. Find High Severity Vulnerabilities

```graphql
query HighVulnerabilities {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: HIGH }) {
      edges {
        node {
          cveId
          severity
          score {
            value
            type
          }
        }
      }
    }
  }
}
```

### 11. Find Open Vulnerabilities

```graphql
query OpenVulnerabilities {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { triageStatus: OPEN }) {
      edges {
        node {
          cveId
          severity
          triageStatus
        }
      }
    }
  }
}
```

### 12. Get Vulnerability Details with Affected Artifacts

```graphql
query VulnWithArtifacts {
  artifactVulnerabilityQuery {
    vulnerabilities(filter: { severity: CRITICAL }, first: 10) {
      edges {
        node {
          cveId
          severity
          description
          affectedArtifacts {
            edges {
              node {
                name
                version
              }
            }
          }
        }
      }
    }
  }
}
```

## Observability Queries

### 13. List Active Alerts

```graphql
query ActiveAlerts {
  observabilityAlertQueryProvider {
    alerts(filter: { status: FIRING }) {
      edges {
        node {
          name
          severity
          status
        }
      }
    }
  }
}
```

### 14. List All Alerts

```graphql
query AllAlerts {
  observabilityAlertQueryProvider {
    alerts(first: 50) {
      edges {
        node {
          name
          severity
          status
        }
      }
      pageInfo {
        hasNextPage
        endCursor
      }
    }
  }
}
```

## Capacity Queries

### 15. Get Capacity Recommendations

```graphql
query CapacityRecommendations {
  capacityQuery {
    recommendations {
      edges {
        node {
          ... on CapacityOptimizeAction {
            classification
            description
          }
        }
      }
    }
  }
}
```

## Relationship Navigation Queries

### 16. Application to Foundation Path

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

### 17. Foundation to Applications Path

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
                  relationshipsIn {
                    contains {
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Organization_Type {
                            properties { name }
                            relationshipsIn {
                              contains {
                                edges {
                                  node {
                                    ... on Entity_Tanzu_TAS_Space_Type {
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

## Paginated Queries

### 18. Paginated Applications

```graphql
query PaginatedApps($after: String) {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 20, after: $after) {
              edges {
                node {
                  id
                  properties {
                    name
                    state
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

## Summary Queries

### 19. Platform Overview

Combine multiple queries for a platform summary:

1. Count foundations
2. Count applications
3. Count critical vulnerabilities
4. Count active alerts

### 20. Health Check Query

```graphql
query HealthCheck {
  entityQuery {
    typed {
      tanzu {
        tas {
          foundation {
            query(first: 1) {
              edges {
                node {
                  id
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

## Usage with MCP Tools

These queries can be executed using:

```
tanzu_common_queries with pattern parameter
```

Or directly via:

```
tanzu_graphql_query with query parameter
```

Always validate first:

```
tanzu_validate_query with query parameter
```
