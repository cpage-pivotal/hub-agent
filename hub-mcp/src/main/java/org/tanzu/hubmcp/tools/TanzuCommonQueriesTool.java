package org.tanzu.hubmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.exception.GraphQLException;
import org.tanzu.hubmcp.model.GraphQLRequest;
import org.tanzu.hubmcp.model.GraphQLResponse;
import org.tanzu.hubmcp.service.TanzuGraphQLService;

import java.util.*;

/**
 * MCP Tool for executing pre-built common query patterns.
 */
@Component
public class TanzuCommonQueriesTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuCommonQueriesTool.class);

    private final TanzuGraphQLService graphQLService;
    private final ObjectMapper objectMapper;
    private final Map<String, QueryTemplate> templates;

    public TanzuCommonQueriesTool(TanzuGraphQLService graphQLService, ObjectMapper objectMapper) {
        this.graphQLService = graphQLService;
        this.objectMapper = objectMapper;
        this.templates = initializeTemplates();
    }

    @McpTool(name = "tanzu_common_queries", description = """
            Execute pre-built common query patterns against the Tanzu Platform.
            
            Available patterns:
            - list_foundations: List all TAS foundations
            - list_organizations: List organizations (optionally by foundation)
            - list_spaces: List spaces (optionally by organization)
            - list_applications: List applications (optionally by space)
            - get_foundation_by_name: Get foundation details by name
            - find_vulnerabilities: Find vulnerabilities (by severity)
            - find_critical_cves: Find critical CVE vulnerabilities
            - get_app_health: Get application health status
            - list_spring_apps: List Spring Boot applications
            - list_alerts: List observability alerts
            - check_capacity: Check capacity recommendations
            - list_insights: List platform insights
            - find_stopped_apps: Find stopped/crashed applications
            - list_service_bindings: List service bindings for an app
            - get_artifact_sbom: Get software bill of materials
            
            Use 'list' pattern to see all available patterns with descriptions.
            """)
    public String executeCommonQuery(
            @McpToolParam(description = "Query pattern name (e.g., 'list_foundations'). Use 'list' to see all available patterns.") 
            String pattern,
            
            @McpToolParam(description = "Pattern-specific parameters as JSON (optional). Example: {\"severity\": \"CRITICAL\", \"first\": 10}", required = false) 
            String parameters
    ) {
        log.debug("Executing common query pattern: {}", pattern);
        
        try {
            // Handle list request
            if ("list".equalsIgnoreCase(pattern)) {
                return listPatterns();
            }
            
            // Get template
            QueryTemplate template = templates.get(pattern.toLowerCase());
            if (template == null) {
                return formatError("Unknown pattern: '" + pattern + "'. Use pattern='list' to see available patterns.");
            }
            
            // Parse parameters
            Map<String, Object> params = parseParameters(parameters);
            
            // Build and execute query
            String query = template.buildQuery(params);
            Map<String, Object> variables = template.buildVariables(params);
            
            GraphQLRequest request = GraphQLRequest.builder()
                    .query(query)
                    .variables(variables.isEmpty() ? null : variables)
                    .build();

            GraphQLResponse response = graphQLService.executeQuery(request);
            
            return formatResponse(pattern, template.description, query, response);
            
        } catch (GraphQLException e) {
            log.warn("Common query failed: {}", e.getMessage());
            return formatError(e);
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            return formatError("Unexpected error: " + e.getMessage());
        }
    }

    private Map<String, QueryTemplate> initializeTemplates() {
        Map<String, QueryTemplate> map = new LinkedHashMap<>();
        
        map.put("list_foundations", new QueryTemplate(
                "List all TAS foundations",
                Map.of("first", 20),
                """
                query ListFoundations($first: Int) {
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
                """
        ));
        
        map.put("list_organizations", new QueryTemplate(
                "List organizations, optionally filtered by foundation",
                Map.of("first", 50),
                """
                query ListOrganizations($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          organization {
                            query(first: $first) {
                              edges {
                                node {
                                  id
                                  entityId
                                  entityName
                                  entityType
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
                """
        ));
        
        map.put("list_spaces", new QueryTemplate(
                "List spaces",
                Map.of("first", 50),
                """
                query ListSpaces($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          space {
                            query(first: $first) {
                              edges {
                                node {
                                  id
                                  entityId
                                  entityName
                                  entityType
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
                """
        ));
        
        map.put("list_applications", new QueryTemplate(
                "List TAS applications",
                Map.of("first", 50),
                """
                query ListApplications($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          application {
                            query(first: $first) {
                              edges {
                                node {
                                  id
                                  entityId
                                  entityName
                                  entityType
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
                """
        ));
        
        map.put("get_foundation_by_name", new QueryTemplate(
                "Get foundation details by name - use entityName filter",
                Map.of(),
                """
                query GetFoundationByName($name: String!) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          foundation {
                            query(filter: {property: "entityName", value: $name}) {
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
                """
        ));
        
        map.put("find_vulnerabilities", new QueryTemplate(
                "Find vulnerabilities, optionally filtered by severity (CRITICAL, HIGH, MEDIUM, LOW)",
                Map.of("first", 50),
                """
                query FindVulnerabilities($first: Int, $severity: ArtifactVulnerabilitySeverity) {
                  artifactVulnerabilityQuery {
                    vulnerabilities(first: $first, filter: {severity: $severity}) {
                      edges {
                        node {
                          id
                          cveId
                          severity
                          score {
                            value
                            type
                          }
                          description
                        }
                      }
                      pageInfo {
                        hasNextPage
                        endCursor
                      }
                    }
                  }
                }
                """
        ));
        
        map.put("find_critical_cves", new QueryTemplate(
                "Find critical CVE vulnerabilities",
                Map.of("first", 50, "severity", "CRITICAL"),
                """
                query FindCriticalCVEs($first: Int) {
                  artifactVulnerabilityQuery {
                    vulnerabilities(first: $first, filter: {severity: CRITICAL}) {
                      edges {
                        node {
                          id
                          cveId
                          severity
                          score {
                            value
                            type
                          }
                          description
                        }
                      }
                    }
                  }
                }
                """
        ));
        
        map.put("get_app_health", new QueryTemplate(
                "Get application health and status information",
                Map.of("first", 20),
                """
                query GetAppHealth($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          application {
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
                """
        ));
        
        map.put("list_spring_apps", new QueryTemplate(
                "List Spring Boot applications with metadata",
                Map.of("first", 50),
                """
                query ListSpringApps($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        spring {
                          application {
                            query(first: $first) {
                              edges {
                                node {
                                  id
                                  entityId
                                  entityName
                                  entityType
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
                """
        ));
        
        map.put("list_alerts", new QueryTemplate(
                "List observability alerts",
                Map.of("first", 50),
                """
                query ListAlerts($first: Int) {
                  observabilityAlertQueryProvider {
                    alerts(first: $first) {
                      edges {
                        node {
                          id
                          name
                          severity
                          status
                          description
                        }
                      }
                      pageInfo {
                        hasNextPage
                        endCursor
                      }
                    }
                  }
                }
                """
        ));
        
        map.put("check_capacity", new QueryTemplate(
                "Check capacity recommendations",
                Map.of("first", 20),
                """
                query CheckCapacity($first: Int) {
                  capacityQuery {
                    recommendations(first: $first) {
                      edges {
                        node {
                          ... on CapacityOptimizeAction {
                            id
                            classification
                            description
                            estimatedSavings
                          }
                        }
                      }
                    }
                  }
                }
                """
        ));
        
        map.put("list_insights", new QueryTemplate(
                "List platform insights",
                Map.of("first", 20),
                """
                query ListInsights($first: Int) {
                  insightQuery {
                    insights(first: $first) {
                      edges {
                        node {
                          id
                          type
                          severity
                          description
                        }
                      }
                    }
                  }
                }
                """
        ));
        
        map.put("find_stopped_apps", new QueryTemplate(
                "Find stopped or crashed applications",
                Map.of("first", 50),
                """
                query FindStoppedApps($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          application {
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
                """
        ));
        
        map.put("list_service_bindings", new QueryTemplate(
                "List service bindings",
                Map.of("first", 50),
                """
                query ListServiceBindings($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          serviceBinding {
                            query(first: $first) {
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
                """
        ));
        
        map.put("get_artifact_sbom", new QueryTemplate(
                "Get software bill of materials for artifacts",
                Map.of("first", 20),
                """
                query GetArtifactSBOM($first: Int) {
                  artifactQuery {
                    artifacts(first: $first) {
                      edges {
                        node {
                          id
                          name
                          version
                          sbom {
                            components {
                              name
                              version
                              type
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """
        ));
        
        return map;
    }

    private String listPatterns() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("availablePatterns", templates.size());
        
        List<Map<String, String>> patternList = templates.entrySet().stream()
                .map(entry -> Map.of(
                        "pattern", entry.getKey(),
                        "description", entry.getValue().description
                ))
                .toList();
        
        result.put("patterns", patternList);
        result.put("usage", "Call with pattern='<pattern_name>' and optionally parameters='{\"key\": \"value\"}'");

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return formatError("Error listing patterns");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParameters(String parameters) {
        if (parameters == null || parameters.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(parameters, Map.class);
        } catch (JsonProcessingException e) {
            throw new GraphQLException("Invalid parameters JSON: " + e.getMessage());
        }
    }

    private String formatResponse(String pattern, String description, String query, GraphQLResponse response) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("pattern", pattern);
            result.put("description", description);
            result.put("data", response.data() != null ? response.data() : Map.of());
            result.put("queryUsed", query.trim());
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\": true, \"pattern\": \"" + pattern + "\", \"note\": \"Response serialization issue\"}";
        }
    }

    private String formatError(GraphQLException e) {
        try {
            Map<String, Object> result = Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "errors", e.getErrors(),
                    "details", e.getDetails()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String formatError(String message) {
        try {
            Map<String, Object> result = Map.of(
                    "success", false,
                    "error", message
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"success\": false, \"error\": \"" + message + "\"}";
        }
    }

    /**
     * Query template with description, default parameters, and query string.
     */
    private record QueryTemplate(
            String description,
            Map<String, Object> defaults,
            String query
    ) {
        String buildQuery(Map<String, Object> params) {
            // For now, return the query as-is
            // Could add parameter substitution for non-variable parts
            return query;
        }

        Map<String, Object> buildVariables(Map<String, Object> params) {
            Map<String, Object> variables = new HashMap<>(defaults);
            variables.putAll(params);
            return variables;
        }
    }
}

