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
    
    // Maximum response size before triggering auto-summarization
    private static final int MAX_RESPONSE_CHARS = 50000;

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
            
            EFFICIENCY PATTERNS (use for aggregation questions):
            - count_stopped_apps_by_space: Count stopped apps per space (EFFICIENT for "spaces with N stopped apps")
            - summarize_app_states: Get counts of apps by state across all spaces
            - spaces_summary: Get space summaries with app counts (no app details)
            
            DETAIL PATTERNS (use when you need full entity data):
            - list_foundations: List all TAS foundations
            - list_organizations: List organizations
            - list_spaces: List spaces
            - list_applications: List applications
            - get_foundation_by_name: Get foundation details by name
            - find_vulnerabilities: Find vulnerabilities (by severity)
            - find_critical_cves: Find critical CVE vulnerabilities
            - get_app_health: Get application health status
            - list_spring_apps: List Spring Boot applications
            - list_alerts: List observability alerts
            - check_capacity: Check capacity recommendations
            - list_insights: List platform insights
            - find_stopped_apps: Find stopped/crashed applications
            - spaces_with_apps: List spaces with applications (WARNING: can be large!)
            - list_service_bindings: List service bindings
            - get_artifact_sbom: Get software bill of materials
            
            Use 'list' pattern to see all available patterns.
            Set summarize=true in parameters for token-efficient responses.
            """)
    public String executeCommonQuery(
            @McpToolParam(description = "Query pattern name (e.g., 'count_stopped_apps_by_space'). Use 'list' to see all available patterns.") 
            String pattern,
            
            @McpToolParam(description = "Pattern-specific parameters as JSON (optional). Example: {\"first\": 10, \"summarize\": true}. Use 'summarize': true for token-efficient aggregated responses.", required = false) 
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
            
            // Check if summarization is requested or response is too large
            boolean shouldSummarize = Boolean.TRUE.equals(params.get("summarize"));
            
            // Apply pattern-specific post-processing if applicable
            if (template.hasPostProcessor()) {
                return template.postProcessor().process(pattern, response.data(), shouldSummarize, objectMapper);
            }
            
            // Check response size and auto-summarize if too large
            String formattedResponse = formatResponse(pattern, template.description, query, response);
            if (formattedResponse.length() > MAX_RESPONSE_CHARS && !shouldSummarize) {
                log.warn("Response size {} exceeds threshold, auto-summarizing", formattedResponse.length());
                return formatSummarizedResponse(pattern, template.description, response.data());
            }
            
            return formattedResponse;
            
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
                "Find stopped or crashed applications with their state and space info",
                Map.of("first", 100),
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
                """
        ));
        
        map.put("spaces_with_apps", new QueryTemplate(
                "List spaces with their applications and states - useful for finding spaces with stopped apps",
                Map.of("first", 100, "appFirst", 100),
                """
                query SpacesWithApps($first: Int, $appFirst: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          space {
                            query(first: $first) {
                              edges {
                                node {
                                  id
                                  entityName
                                  properties {
                                    guid
                                    totalAppCount
                                    foundation
                                    organizationGUID
                                  }
                                  relationshipsIn {
                                    isContainedIn {
                                      tanzu_tas_application(first: $appFirst) {
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
        
        // ============================================
        // EFFICIENT AGGREGATION PATTERNS
        // These patterns compute summaries server-side
        // ============================================
        
        map.put("count_stopped_apps_by_space", new QueryTemplate(
                "Count stopped apps per space - EFFICIENT for 'spaces with N stopped apps' questions",
                Map.of("first", 200, "appFirst", 200),
                """
                query CountStoppedAppsBySpace($first: Int, $appFirst: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          space {
                            query(first: $first) {
                              edges {
                                node {
                                  entityName
                                  properties {
                                    foundation
                                    organizationGUID
                                  }
                                  relationshipsIn {
                                    isContainedIn {
                                      tanzu_tas_application(first: $appFirst) {
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
                """,
                TanzuCommonQueriesTool::processStoppedAppsBySpace
        ));
        
        map.put("summarize_app_states", new QueryTemplate(
                "Get aggregate counts of apps by state - EFFICIENT for state distribution questions",
                Map.of("first", 500),
                """
                query SummarizeAppStates($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          application {
                            query(first: $first) {
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
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """,
                TanzuCommonQueriesTool::processAppStateSummary
        ));
        
        map.put("spaces_summary", new QueryTemplate(
                "Get space summaries with app counts (no app details) - EFFICIENT for space overview",
                Map.of("first", 100),
                """
                query SpacesSummary($first: Int) {
                  entityQuery {
                    typed {
                      tanzu {
                        tas {
                          space {
                            query(first: $first) {
                              edges {
                                node {
                                  entityName
                                  properties {
                                    guid
                                    foundation
                                    organizationGUID
                                    totalAppCount
                                    totalMemoryLimitMB
                                    totalMemoryQuotaMB
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
                """
        ));
        
        return map;
    }
    
    // ============================================
    // POST-PROCESSING FUNCTIONS FOR AGGREGATION
    // ============================================
    
    @SuppressWarnings("unchecked")
    private static String processStoppedAppsBySpace(String pattern, Map<String, Object> data, boolean summarize, ObjectMapper mapper) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("pattern", pattern);
            result.put("description", "Spaces with stopped application counts");
            
            // Navigate to the spaces data
            Map<String, Object> entityQuery = (Map<String, Object>) data.get("entityQuery");
            if (entityQuery == null) {
                result.put("summary", "No data returned");
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            }
            
            Map<String, Object> typed = (Map<String, Object>) entityQuery.get("typed");
            Map<String, Object> tanzu = (Map<String, Object>) typed.get("tanzu");
            Map<String, Object> tas = (Map<String, Object>) tanzu.get("tas");
            Map<String, Object> space = (Map<String, Object>) tas.get("space");
            Map<String, Object> query = (Map<String, Object>) space.get("query");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) query.get("edges");
            
            // Process each space and count stopped apps
            List<Map<String, Object>> spacesWithStoppedApps = new ArrayList<>();
            int totalSpaces = 0;
            int totalStoppedApps = 0;
            int totalApps = 0;
            
            for (Map<String, Object> edge : edges) {
                Map<String, Object> node = (Map<String, Object>) edge.get("node");
                String spaceName = (String) node.get("entityName");
                Map<String, Object> props = (Map<String, Object>) node.get("properties");
                String foundation = props != null ? (String) props.get("foundation") : null;
                
                // Get apps in this space
                Map<String, Object> relationshipsIn = (Map<String, Object>) node.get("relationshipsIn");
                int stoppedCount = 0;
                int runningCount = 0;
                List<String> stoppedAppNames = new ArrayList<>();
                
                if (relationshipsIn != null) {
                    Map<String, Object> isContainedIn = (Map<String, Object>) relationshipsIn.get("isContainedIn");
                    if (isContainedIn != null) {
                        Map<String, Object> appConnection = (Map<String, Object>) isContainedIn.get("tanzu_tas_application");
                        if (appConnection != null) {
                            List<Map<String, Object>> appEdges = (List<Map<String, Object>>) appConnection.get("edges");
                            if (appEdges != null) {
                                for (Map<String, Object> appEdge : appEdges) {
                                    Map<String, Object> appNode = (Map<String, Object>) appEdge.get("node");
                                    Map<String, Object> appProps = (Map<String, Object>) appNode.get("properties");
                                    String state = appProps != null ? (String) appProps.get("state") : null;
                                    
                                    totalApps++;
                                    if ("STOPPED".equalsIgnoreCase(state)) {
                                        stoppedCount++;
                                        totalStoppedApps++;
                                        stoppedAppNames.add((String) appNode.get("entityName"));
                                    } else if ("STARTED".equalsIgnoreCase(state)) {
                                        runningCount++;
                                    }
                                }
                            }
                        }
                    }
                }
                
                totalSpaces++;
                
                // Only include spaces that have stopped apps
                if (stoppedCount > 0) {
                    Map<String, Object> spaceInfo = new LinkedHashMap<>();
                    spaceInfo.put("spaceName", spaceName);
                    spaceInfo.put("foundation", foundation);
                    spaceInfo.put("stoppedAppCount", stoppedCount);
                    spaceInfo.put("runningAppCount", runningCount);
                    spaceInfo.put("totalApps", stoppedCount + runningCount);
                    
                    // Only include app names if not too many
                    if (stoppedAppNames.size() <= 10) {
                        spaceInfo.put("stoppedApps", stoppedAppNames);
                    } else {
                        spaceInfo.put("stoppedApps", stoppedAppNames.subList(0, 10));
                        spaceInfo.put("moreStoppedApps", stoppedAppNames.size() - 10);
                    }
                    
                    spacesWithStoppedApps.add(spaceInfo);
                }
            }
            
            // Sort by stopped app count descending
            spacesWithStoppedApps.sort((a, b) -> 
                Integer.compare((Integer) b.get("stoppedAppCount"), (Integer) a.get("stoppedAppCount")));
            
            // Build summary
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalSpaces", totalSpaces);
            summary.put("spacesWithStoppedApps", spacesWithStoppedApps.size());
            summary.put("totalApps", totalApps);
            summary.put("totalStoppedApps", totalStoppedApps);
            
            result.put("summary", summary);
            result.put("spacesWithStoppedApps", spacesWithStoppedApps);
            
            // Provide convenient answers to common questions
            Map<String, Object> insights = new LinkedHashMap<>();
            long spacesWithMoreThan2Stopped = spacesWithStoppedApps.stream()
                    .filter(s -> (Integer) s.get("stoppedAppCount") > 2)
                    .count();
            insights.put("spacesWithMoreThan2StoppedApps", spacesWithMoreThan2Stopped);
            
            if (spacesWithMoreThan2Stopped > 0) {
                insights.put("spacesWithMoreThan2StoppedAppsList", 
                    spacesWithStoppedApps.stream()
                        .filter(s -> (Integer) s.get("stoppedAppCount") > 2)
                        .map(s -> s.get("spaceName") + " (" + s.get("stoppedAppCount") + " stopped)")
                        .toList());
            }
            
            result.put("insights", insights);
            
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"Error processing stopped apps data: " + e.getMessage() + "\"}";
        }
    }
    
    @SuppressWarnings("unchecked")
    private static String processAppStateSummary(String pattern, Map<String, Object> data, boolean summarize, ObjectMapper mapper) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("pattern", pattern);
            result.put("description", "Application state summary");
            
            // Navigate to the apps data
            Map<String, Object> entityQuery = (Map<String, Object>) data.get("entityQuery");
            if (entityQuery == null) {
                result.put("summary", "No data returned");
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            }
            
            Map<String, Object> typed = (Map<String, Object>) entityQuery.get("typed");
            Map<String, Object> tanzu = (Map<String, Object>) typed.get("tanzu");
            Map<String, Object> tas = (Map<String, Object>) tanzu.get("tas");
            Map<String, Object> application = (Map<String, Object>) tas.get("application");
            Map<String, Object> query = (Map<String, Object>) application.get("query");
            List<Map<String, Object>> edges = (List<Map<String, Object>>) query.get("edges");
            Map<String, Object> pageInfo = (Map<String, Object>) query.get("pageInfo");
            
            // Count by state
            Map<String, Integer> stateCount = new LinkedHashMap<>();
            Map<String, Integer> healthCount = new LinkedHashMap<>();
            Map<String, Integer> foundationCount = new LinkedHashMap<>();
            
            for (Map<String, Object> edge : edges) {
                Map<String, Object> node = (Map<String, Object>) edge.get("node");
                Map<String, Object> props = (Map<String, Object>) node.get("properties");
                
                String state = props != null ? (String) props.get("state") : "UNKNOWN";
                String health = props != null ? (String) props.get("health_status") : "UNKNOWN";
                String foundation = props != null ? (String) props.get("foundation") : "UNKNOWN";
                
                stateCount.merge(state != null ? state : "UNKNOWN", 1, Integer::sum);
                healthCount.merge(health != null ? health : "UNKNOWN", 1, Integer::sum);
                foundationCount.merge(foundation != null ? foundation : "UNKNOWN", 1, Integer::sum);
            }
            
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("totalAppsQueried", edges.size());
            summary.put("hasMoreApps", pageInfo != null && Boolean.TRUE.equals(pageInfo.get("hasNextPage")));
            summary.put("byState", stateCount);
            summary.put("byHealthStatus", healthCount);
            summary.put("byFoundation", foundationCount);
            
            result.put("summary", summary);
            
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"Error processing app state summary: " + e.getMessage() + "\"}";
        }
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

    private String formatSummarizedResponse(String pattern, String description, Map<String, Object> data) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("pattern", pattern);
            result.put("description", description);
            result.put("note", "Response was auto-summarized due to size. Use more specific patterns for detailed data.");
            
            // Create a summary by counting entities at each level
            Map<String, Object> summary = summarizeData(data, 0);
            result.put("summary", summary);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"success\": true, \"pattern\": \"" + pattern + "\", \"note\": \"Response summarization issue\"}";
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> summarizeData(Map<String, Object> data, int depth) {
        if (depth > 5) {
            return Map.of("truncated", true);
        }
        
        Map<String, Object> summary = new LinkedHashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof List<?> list) {
                summary.put(key + "_count", list.size());
                if (!list.isEmpty() && list.get(0) instanceof Map) {
                    // Sample first few items
                    summary.put(key + "_sample", list.size() > 3 ? 
                        ((List<?>)list).subList(0, 3) : list);
                }
            } else if (value instanceof Map) {
                summary.put(key, summarizeData((Map<String, Object>) value, depth + 1));
            } else {
                summary.put(key, value);
            }
        }
        
        return summary;
    }

    /**
     * Functional interface for post-processing query results.
     */
    @FunctionalInterface
    private interface PostProcessor {
        String process(String pattern, Map<String, Object> data, boolean summarize, ObjectMapper mapper);
    }

    /**
     * Query template with description, default parameters, query string, and optional post-processor.
     */
    private record QueryTemplate(
            String description,
            Map<String, Object> defaults,
            String query,
            PostProcessor postProcessor
    ) {
        // Constructor without post-processor
        QueryTemplate(String description, Map<String, Object> defaults, String query) {
            this(description, defaults, query, null);
        }
        
        String buildQuery(Map<String, Object> params) {
            return query;
        }

        Map<String, Object> buildVariables(Map<String, Object> params) {
            Map<String, Object> variables = new HashMap<>(defaults);
            variables.putAll(params);
            return variables;
        }
        
        boolean hasPostProcessor() {
            return postProcessor != null;
        }
    }
}

