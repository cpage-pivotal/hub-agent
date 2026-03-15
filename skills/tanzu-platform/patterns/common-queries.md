# Common Query Patterns

This document contains 20+ pre-built query templates for frequent operations.

## ⚠️ EFFICIENCY GUIDANCE

**Before choosing a pattern, consider the question type:**

| Question Type | Recommended Pattern | Why |
|--------------|---------------------|-----|
| "How many stopped apps per space?" | `count_stopped_apps_by_space` | Pre-aggregated, small response |
| "Spaces with more than N stopped apps?" | `count_stopped_apps_by_space` | Returns counts, not raw data |
| "What's the app state distribution?" | `summarize_app_states` | Returns counts by state |
| "List all spaces" (overview) | `spaces_summary` | No nested app data |
| "List spaces with their apps" (detail) | `spaces_with_apps` | **WARNING: Large response!** |

**Rule of thumb:** If the question is about **counts** or **aggregations**, use an efficiency pattern. Only use detail patterns when you need the actual entity data.

## CRITICAL: Field Names

- **Entity name**: Use `entityName` at entity level, NOT `properties.name`
- **Relationships**: Use `tanzu_tas_{entity}` fields, NOT generic `contains`

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
                  entityName
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
                  entityType
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
                  entityName
                  properties {
                    guid
                    foundation
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
                  entityName
                  properties {
                    guid
                    foundation
                  }
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_space(first: 50) {
                        edges {
                          node {
                            entityName
                            properties {
                              guid
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
                  entityName
                  properties {
                    guid
                    totalAppCount
                    foundation
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

### 6. Spaces with Applications (Important!)

This is the correct way to query spaces with their applications:

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
                    organizationGUID
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
                              instanceCount
                              runningInstanceCount
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

Use this for questions like:
- "Which spaces have stopped apps?"
- "Find spaces with more than N stopped apps"
- "List apps by space"

## Application Queries

### 7. List All Applications

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

### 8. Get Application Details

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
                  entityName
                  properties {
                    state
                    health_status
                    instanceCount
                    runningInstanceCount
                    crashedInstanceCount
                    buildpack
                    spaceGUID
                    foundation
                    routes
                    totalMemoryLimitMB
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

### 9. Find Stopped Applications

```graphql
query StoppedApps {
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
# Filter client-side for state = "STOPPED"
```

### 10. Application to Space Navigation

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

## Security Queries

### 11. Find Critical Vulnerabilities

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

### 12. Find High Severity Vulnerabilities

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

### 13. Find Open Vulnerabilities

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

### 14. Get Vulnerability Details with Affected Artifacts

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

### 15. List Active Alerts

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

### 16. List All Alerts

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

### 17. Get Capacity Recommendations

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

## Multi-Level Navigation Queries

### 18. Application to Foundation Path

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

### 19. Foundation to Applications Path

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
                  relationshipsIn {
                    isContainedIn {
                      tanzu_tas_organization(first: 50) {
                        edges {
                          node {
                            entityName
                            relationshipsIn {
                              isContainedIn {
                                tanzu_tas_space(first: 50) {
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

### 20. Paginated Applications

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
                  entityName
                  properties {
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

### 21. Health Check Query

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

## Efficient Aggregation Queries

### 22. Count Stopped Apps by Space (RECOMMENDED)

Use this for questions like "spaces with more than N stopped apps":

```graphql
# Use tanzu_common_queries(pattern: "count_stopped_apps_by_space")
# Returns pre-computed counts, NOT raw data
```

**Response includes:**
- `spacesWithStoppedApps`: List of spaces with stopped counts
- `insights.spacesWithMoreThan2StoppedApps`: Direct answer to common question
- `summary`: Total counts

### 23. Summarize App States (RECOMMENDED)

Use for app state distribution questions:

```graphql
# Use tanzu_common_queries(pattern: "summarize_app_states")
# Returns counts by state, health, and foundation
```

### 24. Spaces Summary (RECOMMENDED)

Use for space overview without app details:

```graphql
# Use tanzu_common_queries(pattern: "spaces_summary")
# Returns space info with totalAppCount, no nested apps
```

## Usage with MCP Tools

### Using tanzu_common_queries

```
tanzu_common_queries(pattern: "count_stopped_apps_by_space")
```

**EFFICIENCY PATTERNS (use for aggregation questions):**
- `count_stopped_apps_by_space` - **BEST for "spaces with N stopped apps"**
- `summarize_app_states` - App state distribution counts
- `spaces_summary` - Space overview with counts

**DETAIL PATTERNS (use when you need full entity data):**
- `list_foundations`
- `list_organizations`
- `list_spaces`
- `list_applications`
- `find_stopped_apps`
- `spaces_with_apps` - ⚠️ **WARNING: Can return very large responses!**
- And more...

### Using tanzu_graphql_query Directly

```
tanzu_graphql_query(query: "{ entityQuery { ... } }")
```

### Always Validate First

```
tanzu_validate_query(query: "{ entityQuery { ... } }")
```

## Common Mistakes to Avoid

### WRONG - Using `properties.name`

```graphql
# WRONG
node {
  properties {
    name  # This field doesn't exist!
  }
}

# CORRECT
node {
  entityName  # Name is at entity level
  properties {
    guid
    state
  }
}
```

### WRONG - Using generic `contains`

```graphql
# WRONG
relationshipsIn {
  contains {  # This field doesn't exist!
    ...
  }
}

# CORRECT
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) {  # Use snake_case entity type
      ...
    }
  }
}
```
