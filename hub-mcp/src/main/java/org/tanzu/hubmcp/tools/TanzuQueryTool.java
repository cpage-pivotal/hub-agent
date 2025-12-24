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

import java.util.Map;

/**
 * MCP Tool for executing read-only GraphQL queries against the Tanzu Platform API.
 */
@Component
public class TanzuQueryTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuQueryTool.class);

    private final TanzuGraphQLService graphQLService;
    private final ObjectMapper objectMapper;

    public TanzuQueryTool(TanzuGraphQLService graphQLService, ObjectMapper objectMapper) {
        this.graphQLService = graphQLService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_graphql_query", description = """
            Execute read-only GraphQL queries against the Tanzu Platform API.
            
            Use this tool to query Tanzu Platform data including:
            - TAS foundations, organizations, spaces, and applications
            - Spring Boot application metadata and dependencies
            - Security vulnerabilities and CVEs
            - Observability metrics and alerts
            - Capacity information and recommendations
            
            The API uses Relay-style connections with edges/nodes for lists.
            
            Example query:
            query {
              entityQuery {
                Entity_Tanzu_TAS_Foundation(first: 10) {
                  edges {
                    node {
                      id
                      properties { name }
                    }
                  }
                  pageInfo { hasNextPage endCursor }
                }
              }
            }
            """)
    public String executeQuery(
            @McpToolParam(description = "GraphQL query string. Must be a valid GraphQL query.") 
            String query,
            
            @McpToolParam(description = "Query variables as a JSON object string (optional). Example: {\"first\": 10, \"after\": \"cursor123\"}", required = false) 
            String variables,
            
            @McpToolParam(description = "Named operation to execute if query contains multiple operations (optional)", required = false) 
            String operationName
    ) {
        log.debug("Executing GraphQL query via MCP tool");
        
        try {
            Map<String, Object> variablesMap = parseVariables(variables);
            
            GraphQLRequest request = GraphQLRequest.builder()
                    .query(query)
                    .variables(variablesMap)
                    .operationName(operationName)
                    .build();

            GraphQLResponse response = graphQLService.executeQuery(request);
            
            return formatResponse(response);
            
        } catch (GraphQLException e) {
            log.warn("GraphQL query failed: {}", e.getMessage());
            return formatError(e);
        } catch (Exception e) {
            log.error("Unexpected error executing query: {}", e.getMessage(), e);
            return formatError("Unexpected error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseVariables(String variables) {
        if (variables == null || variables.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(variables, Map.class);
        } catch (JsonProcessingException e) {
            throw new GraphQLException("Invalid variables JSON: " + e.getMessage());
        }
    }

    private String formatResponse(GraphQLResponse response) {
        try {
            Map<String, Object> result = Map.of(
                    "success", true,
                    "data", response.data() != null ? response.data() : Map.of(),
                    "queryComplexity", response.getQueryComplexity()
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\": true, \"data\": {}, \"note\": \"Response serialization issue\"}";
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
}

