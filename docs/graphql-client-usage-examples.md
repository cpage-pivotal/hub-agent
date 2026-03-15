# GraphQL Client Usage Examples

This document provides practical examples of using the TanzuGraphQLService for common operations.

> **⚠️ Important:** Before using these examples, read [schema-learnings.md](schema-learnings.md) to understand critical API patterns:
> - Entity names use `entityName` at entity level, NOT `properties.name`
> - Relationship navigation uses `tanzu_tas_{entity}` fields, NOT generic `contains`

---

## Basic Query Execution

### Simple Query
```java
@Autowired
private TanzuGraphQLService graphQLService;

public void listFoundations() {
    String query = """
        query {
          entityQuery {
            typed {
              tanzu {
                tas {
                  foundation {
                    query(first: 10) {
                      edges {
                        node {
                          id
                          entityId
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
        """;

    GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .build();

    GraphQLResponse response = graphQLService.executeQuery(request);
    
    if (response.hasErrors()) {
        // Handle errors
        response.errors().forEach(error -> 
            System.err.println("Error: " + error.message())
        );
    } else {
        // Process data
        Map<String, Object> data = response.data();
        // Extract and use data...
    }
}
```

---

## Query with Variables

### Parameterized Query
```java
public void findFoundationByName(String foundationName) {
    String query = """
        query FindFoundation($name: String!) {
          entityQuery {
            typed {
              tanzu {
                tas {
                  foundation {
                    query(
                      first: 1
                      filter: { property: "entityName", value: $name }
                    ) {
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
        """;

    Map<String, Object> variables = Map.of("name", foundationName);

    GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .variables(variables)
            .operationName("FindFoundation")
            .build();

    GraphQLResponse response = graphQLService.executeQuery(request);
    // Process response...
}
```

---

## Pagination Handling

### Cursor-Based Pagination
```java
public List<Map<String, Object>> getAllApplications() {
    List<Map<String, Object>> allApps = new ArrayList<>();
    String cursor = null;
    boolean hasNextPage = true;

    while (hasNextPage) {
        String query = """
            query GetApps($after: String) {
              entityQuery {
                typed {
                  tanzu {
                    tas {
                      application {
                        query(first: 50, after: $after) {
                          edges {
                            node {
                              id
                              entityName
                              properties {
                                state
                                health_status
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
            """;

        Map<String, Object> variables = cursor != null 
            ? Map.of("after", cursor) 
            : Map.of();

        GraphQLRequest request = GraphQLRequest.builder()
                .query(query)
                .variables(variables)
                .build();

        GraphQLResponse response = graphQLService.executeQuery(request);
        
        // Extract applications from response
        Map<String, Object> data = response.data();
        // Navigate: data -> entityQuery -> typed -> tanzu -> tas -> application -> query
        Map<String, Object> appQuery = extractNestedMap(data, 
            "entityQuery", "typed", "tanzu", "tas", "application", "query");
        
        List<Map<String, Object>> edges = (List<Map<String, Object>>) appQuery.get("edges");
        edges.forEach(edge -> {
            Map<String, Object> node = (Map<String, Object>) edge.get("node");
            allApps.add(node);
        });

        // Check for next page
        Map<String, Object> pageInfo = (Map<String, Object>) appQuery.get("pageInfo");
        hasNextPage = (Boolean) pageInfo.get("hasNextPage");
        cursor = (String) pageInfo.get("endCursor");
    }

    return allApps;
}

private Map<String, Object> extractNestedMap(Map<String, Object> map, String... keys) {
    Map<String, Object> current = map;
    for (String key : keys) {
        current = (Map<String, Object>) current.get(key);
        if (current == null) break;
    }
    return current;
}
```

---

## Executing Mutations

### Create/Update Operation
```java
public void createNotificationTarget(String name, String webhookUrl) {
    String mutation = """
        mutation CreateNotificationTarget($input: NotificationTargetInput!) {
          notificationTargetMutation {
            create(input: $input) {
              id
              name
              type
              configuration
            }
          }
        }
        """;

    Map<String, Object> input = Map.of(
        "name", name,
        "type", "WEBHOOK",
        "configuration", Map.of("url", webhookUrl)
    );

    Map<String, Object> variables = Map.of("input", input);

    GraphQLRequest request = GraphQLRequest.builder()
            .query(mutation)
            .variables(variables)
            .operationName("CreateNotificationTarget")
            .build();

    GraphQLResponse response = graphQLService.executeMutation(request);
    
    if (response.hasErrors()) {
        throw new RuntimeException("Mutation failed: " + response.errors());
    }
    
    System.out.println("Created notification target: " + response.data());
}
```

---

## Schema Introspection

### Get All Types
```java
public void exploreSchema() {
    Map<String, Object> schemaData = graphQLService.introspectSchema();
    
    Map<String, Object> schema = (Map<String, Object>) schemaData.get("__schema");
    List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
    
    System.out.println("Schema contains " + types.size() + " types");
    
    // Filter for entity types
    types.stream()
        .filter(type -> {
            String name = (String) type.get("name");
            return name != null && name.startsWith("Entity_Tanzu_");
        })
        .forEach(type -> {
            String name = (String) type.get("name");
            String kind = (String) type.get("kind");
            String description = (String) type.get("description");
            
            System.out.println(name + " (" + kind + ")");
            if (description != null) {
                System.out.println("  " + description);
            }
        });
}
```

### Get Type Details
```java
public void getTypeDetails(String typeName) {
    Map<String, Object> schemaData = graphQLService.introspectSchema();
    
    Map<String, Object> schema = (Map<String, Object>) schemaData.get("__schema");
    List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
    
    Map<String, Object> targetType = types.stream()
        .filter(type -> typeName.equals(type.get("name")))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Type not found: " + typeName));
    
    System.out.println("Type: " + targetType.get("name"));
    System.out.println("Kind: " + targetType.get("kind"));
    System.out.println("Description: " + targetType.get("description"));
    
    List<Map<String, Object>> fields = (List<Map<String, Object>>) targetType.get("fields");
    if (fields != null) {
        System.out.println("\nFields:");
        fields.forEach(field -> {
            String fieldName = (String) field.get("name");
            Map<String, Object> fieldType = (Map<String, Object>) field.get("type");
            String typeName2 = extractTypeName(fieldType);
            
            System.out.println("  " + fieldName + ": " + typeName2);
        });
    }
}

private String extractTypeName(Map<String, Object> typeRef) {
    String kind = (String) typeRef.get("kind");
    String name = (String) typeRef.get("name");
    
    if ("NON_NULL".equals(kind) || "LIST".equals(kind)) {
        Map<String, Object> ofType = (Map<String, Object>) typeRef.get("ofType");
        String innerType = extractTypeName(ofType);
        return "NON_NULL".equals(kind) ? innerType + "!" : "[" + innerType + "]";
    }
    
    return name;
}
```

---

## Error Handling Patterns

### Comprehensive Error Handling
```java
public GraphQLResponse executeQuerySafely(String query) {
    try {
        GraphQLRequest request = GraphQLRequest.builder()
                .query(query)
                .build();
        
        GraphQLResponse response = graphQLService.executeQuery(request);
        
        if (response.hasErrors()) {
            // GraphQL execution errors
            response.errors().forEach(error -> {
                System.err.println("GraphQL Error: " + error.message());
                if (error.locations() != null) {
                    error.locations().forEach(loc ->
                        System.err.println("  at line " + loc.line() + ", column " + loc.column())
                    );
                }
            });
            throw new RuntimeException("Query returned errors");
        }
        
        return response;
        
    } catch (GraphQLException e) {
        // Syntax errors, validation errors
        System.err.println("Query validation failed: " + e.getMessage());
        throw e;
        
    } catch (WebClientResponseException e) {
        // HTTP errors from the API
        System.err.println("API error: " + e.getStatusCode());
        System.err.println("Response: " + e.getResponseBodyAsString());
        throw new RuntimeException("API request failed", e);
        
    } catch (TimeoutException e) {
        // Query timeout
        System.err.println("Query timed out after " + 
            properties.graphql().timeout().toSeconds() + " seconds");
        throw new RuntimeException("Query timeout", e);
    }
}
```

### Retry on Specific Errors
```java
public GraphQLResponse executeWithCustomRetry(String query, int maxAttempts) {
    int attempt = 0;
    Exception lastException = null;
    
    while (attempt < maxAttempts) {
        try {
            GraphQLRequest request = GraphQLRequest.builder()
                    .query(query)
                    .build();
            
            return graphQLService.executeQuery(request);
            
        } catch (WebClientResponseException e) {
            lastException = e;
            int status = e.getStatusCode().value();
            
            // Only retry on rate limit or server errors
            if (status == 429 || status >= 500) {
                attempt++;
                if (attempt < maxAttempts) {
                    // Exponential backoff
                    long waitMs = (long) Math.pow(2, attempt) * 1000;
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                }
            } else {
                // Don't retry on 4xx errors (except 429)
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Query failed", e);
        }
    }
    
    throw new RuntimeException("Query failed after " + maxAttempts + " attempts", lastException);
}
```

---

## Performance Optimization

### Query Complexity Monitoring
```java
public void monitorQueryComplexity(String query) {
    GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .build();
    
    long startTime = System.currentTimeMillis();
    GraphQLResponse response = graphQLService.executeQuery(request);
    long duration = System.currentTimeMillis() - startTime;
    
    int complexity = response.getQueryComplexity();
    
    System.out.println("Query completed in " + duration + "ms");
    System.out.println("Query complexity: " + complexity);
    
    if (complexity > 10000) {
        System.err.println("WARNING: High complexity query detected!");
        System.err.println("Consider breaking this into smaller queries.");
    }
}
```

### Batch Multiple Queries
```java
public Map<String, GraphQLResponse> executeBatch(Map<String, String> namedQueries) {
    Map<String, GraphQLResponse> results = new HashMap<>();
    
    // Execute queries in parallel using CompletableFuture
    List<CompletableFuture<Void>> futures = namedQueries.entrySet().stream()
        .map(entry -> CompletableFuture.runAsync(() -> {
            String name = entry.getKey();
            String query = entry.getValue();
            
            GraphQLRequest request = GraphQLRequest.builder()
                    .query(query)
                    .build();
            
            GraphQLResponse response = graphQLService.executeQuery(request);
            results.put(name, response);
        }))
        .toList();
    
    // Wait for all to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    return results;
}
```

---

## Testing Examples

### Unit Test with Mock
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    
    @Mock
    private TanzuGraphQLService graphQLService;
    
    @InjectMocks
    private MyService myService;
    
    @Test
    void shouldHandleSuccessfulQuery() {
        // Given
        String query = "query { ... }";
        GraphQLResponse mockResponse = GraphQLResponse.builder()
                .data(Map.of("result", "success"))
                .build();
        
        when(graphQLService.executeQuery(any(GraphQLRequest.class)))
                .thenReturn(mockResponse);
        
        // When
        GraphQLResponse result = myService.performQuery(query);
        
        // Then
        assertThat(result.data()).containsKey("result");
        verify(graphQLService).executeQuery(any(GraphQLRequest.class));
    }
    
    @Test
    void shouldHandleGraphQLErrors() {
        // Given
        when(graphQLService.executeQuery(any(GraphQLRequest.class)))
                .thenThrow(new GraphQLException("Syntax error"));
        
        // Then
        assertThrows(GraphQLException.class, () -> 
            myService.performQuery("invalid query")
        );
    }
}
```

---

## Configuration Examples

### Custom Timeout
```yaml
# application-custom.yml
tanzu:
  platform:
    graphql:
      timeout: 60s  # Increase for complex queries
      max-retries: 5
```

### Development vs Production
```yaml
# application-dev.yml
tanzu:
  platform:
    graphql:
      timeout: 10s
      max-retries: 1

logging:
  level:
    org.tanzu.hubmcp: DEBUG
    org.springframework.web.reactive.function.client: DEBUG

# application-prod.yml
tanzu:
  platform:
    graphql:
      timeout: 30s
      max-retries: 3

logging:
  level:
    org.tanzu.hubmcp: INFO
    org.springframework.web.reactive.function.client: WARN
```

---

## Common Patterns

### Entity Relationship Traversal (Correct Pattern)

> **Important:** Use snake_case entity type fields for relationships, NOT inline fragments with `edges/node`.

```java
public void getApplicationWithSpace(String appId) {
    // CORRECT: Use tanzu_tas_{entity} fields for relationship navigation
    String query = """
        query GetAppWithSpace {
          entityQuery {
            typed {
              tanzu {
                tas {
                  application {
                    query(first: 10) {
                      edges {
                        node {
                          id
                          entityName
                          properties {
                            state
                            health_status
                          }
                          
                          # Navigate to parent Space
                          relationshipsOut {
                            isContainedIn {
                              tanzu_tas_space(first: 1) {
                                edges {
                                  node {
                                    entityName
                                    properties {
                                      guid
                                    }
                                    
                                    # Navigate to parent Organization
                                    relationshipsOut {
                                      isContainedIn {
                                        tanzu_tas_organization(first: 1) {
                                          edges {
                                            node {
                                              entityName
                                              
                                              # Navigate to parent Foundation
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
        """;
    
    GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .build();
    
    GraphQLResponse response = graphQLService.executeQuery(request);
    // Process nested response...
}
```

### Spaces with Applications (Parent to Children)

```java
public void getSpacesWithApps() {
    // CORRECT: Use tanzu_tas_application for navigating from Space to Applications
    String query = """
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
                          
                          # Navigate to child Applications
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
        """;
    
    GraphQLRequest request = GraphQLRequest.builder()
            .query(query)
            .build();
    
    GraphQLResponse response = graphQLService.executeQuery(request);
    // Process response - useful for "find spaces with stopped apps"
}
```

---

## Best Practices

1. **Use `entityName` for names**: Entity names are at entity level, NOT `properties.name`
2. **Use snake_case for relationships**: Use `tanzu_tas_application`, NOT `contains`
3. **Always validate before execution**: The service validates syntax automatically
4. **Handle errors gracefully**: Check `response.hasErrors()` before processing data
5. **Use variables for dynamic values**: Don't concatenate strings into queries
6. **Request only needed fields**: Reduces response size and improves performance
7. **Monitor complexity**: Check `response.getQueryComplexity()` for large queries
8. **Use pagination**: Don't fetch all data at once for large result sets
9. **Log appropriately**: Use DEBUG for queries, WARN for retries, ERROR for failures
10. **Configure timeouts**: Adjust based on your query complexity needs

---

## Troubleshooting

### "Cannot query field 'name' on type '*_Properties'"
**Problem**: Using `properties.name` which doesn't exist.
**Solution**: Use `entityName` at the entity level:
```graphql
node {
  entityName           # Correct!
  properties { state } # Properties has other fields, not name
}
```

### "Cannot query field 'contains' on type '*_RelIn'"
**Problem**: Using generic `contains` field which doesn't exist.
**Solution**: Use snake_case entity type fields:
```graphql
relationshipsIn {
  isContainedIn {
    tanzu_tas_application(first: 100) { ... }  # Correct!
  }
}
```

### Query Syntax Errors
```
GraphQLException: Invalid GraphQL syntax at line 5, column 12: ...
```
**Solution**: Check query syntax, use GraphQL validator, or `tanzu_validate_query` tool

### Timeout Errors
```
TimeoutException: Query exceeded timeout limit
```
**Solution**: 
- Reduce query complexity (fewer fields, smaller result set)
- Increase timeout in configuration
- Break into multiple smaller queries

### Connection Errors
```
WebClientException: Unable to connect to Tanzu Platform API
```
**Solution**:
- Verify `TANZU_PLATFORM_URL` is correct and accessible
- Check network connectivity
- Verify token is valid and not expired

### Rate Limiting
```
HTTP 429: Too Many Requests
```
**Solution**: Service automatically retries with backoff, but consider:
- Reducing query frequency
- Implementing request throttling
- Using caching for repeated queries

---

## See Also

- [schema-learnings.md](schema-learnings.md) - Critical API patterns
- [implementation-status.md](implementation-status.md) - Project status
- MCP tool implementations in `hub-mcp/src/main/java/org/tanzu/hubmcp/tools/`
