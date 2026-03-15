# Pagination Patterns

The Tanzu Platform GraphQL API uses cursor-based pagination following the GraphQL Relay specification.

## Pagination Basics

All list queries return Connection types with:
- `edges` - Array of Edge objects containing nodes
- `pageInfo` - Pagination metadata

## PageInfo Fields

| Field | Type | Description |
|-------|------|-------------|
| `hasNextPage` | Boolean | More results available after current page |
| `hasPreviousPage` | Boolean | More results available before current page |
| `startCursor` | String | Cursor for first item in current page |
| `endCursor` | String | Cursor for last item in current page |

## Pagination Arguments

| Argument | Type | Description |
|----------|------|-------------|
| `first` | Int | Return first N results |
| `after` | String | Return results after this cursor |
| `last` | Int | Return last N results |
| `before` | String | Return results before this cursor |

## Basic Pagination Query

```graphql
query PaginatedQuery($first: Int!, $after: String) {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: $first, after: $after) {
              edges {
                node {
                  id
                  properties {
                    name
                  }
                }
                cursor
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

## Pagination Workflow

### Step 1: Initial Query

```graphql
query FirstPage {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 20) {
              edges {
                node {
                  properties { name }
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

Response:
```json
{
  "data": {
    "entityQuery": {
      "typed": {
        "tanzu": {
          "tas": {
            "application": {
              "query": {
                "edges": [...],
                "pageInfo": {
                  "hasNextPage": true,
                  "endCursor": "YXJyYXljb25uZWN0aW9uOjE5"
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

### Step 2: Next Page Query

Use the `endCursor` from the previous response:

```graphql
query NextPage {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 20, after: "YXJyYXljb25uZWN0aW9uOjE5") {
              edges {
                node {
                  properties { name }
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

### Step 3: Continue Until Complete

Continue fetching pages until `hasNextPage` is `false`.

## Pagination with Variables

Using query variables makes pagination easier:

```graphql
query Applications($first: Int!, $after: String) {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: $first, after: $after) {
              edges {
                node {
                  properties { name state }
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

Variables for first page:
```json
{
  "first": 20,
  "after": null
}
```

Variables for subsequent pages:
```json
{
  "first": 20,
  "after": "YXJyYXljb25uZWN0aW9uOjE5"
}
```

## Recommended Page Sizes

| Entity Type | Recommended `first` | Notes |
|-------------|---------------------|-------|
| Foundations | 20-50 | Usually few foundations |
| Organizations | 50-100 | Moderate count |
| Spaces | 100-200 | Can be many per org |
| Applications | 50-100 | Balance completeness and performance |
| Vulnerabilities | 50-100 | May have many results |

## Pagination in Relationship Traversal

Pagination also applies to relationship connections:

```graphql
query OrgWithPaginatedSpaces {
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
                    contains(first: 20) {  # Paginate relationships too
                      edges {
                        node {
                          ... on Entity_Tanzu_TAS_Space_Type {
                            properties { name }
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
      }
    }
  }
}
```

## Edge Cursors

Each edge has its own cursor for fine-grained pagination:

```graphql
query WithEdgeCursors {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                cursor  # Individual edge cursor
                node {
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
```

## Counting Total Results

Some queries support a `totalCount` field:

```graphql
query WithCount {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              totalCount  # If available
              edges {
                node {
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
```

## Best Practices

1. **Always request `pageInfo`** - Even if you don't expect multiple pages
2. **Use reasonable page sizes** - 20-100 items typically
3. **Handle empty results** - Check if `edges` is empty
4. **Store cursors** - Keep track of where you are for subsequent requests
5. **Paginate relationships too** - Don't forget nested connections

## Common Mistakes

### Forgetting pageInfo

```graphql
# WRONG - No way to know if there are more pages
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10) {
              edges {
                node { ... }
              }
              # Missing pageInfo!
            }
          }
        }
      }
    }
  }
}
```

### Using offset pagination

```graphql
# WRONG - No offset/skip in cursor-based pagination
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10, offset: 20) {  # offset doesn't exist
              ...
            }
          }
        }
      }
    }
  }
}

# CORRECT - Use cursor
query {
  entityQuery {
    typed {
      tanzu {
        tas {
          application {
            query(first: 10, after: "cursor-string") {
              ...
            }
          }
        }
      }
    }
  }
}
```
