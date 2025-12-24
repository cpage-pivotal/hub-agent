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
import java.util.Set;

/**
 * MCP Tool for executing GraphQL mutations against the Tanzu Platform API.
 */
@Component
public class TanzuMutateTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuMutateTool.class);
    
    private static final Set<String> DESTRUCTIVE_KEYWORDS = Set.of(
            "delete", "remove", "destroy", "drop", "purge", "clear"
    );

    private final TanzuGraphQLService graphQLService;
    private final ObjectMapper objectMapper;

    public TanzuMutateTool(TanzuGraphQLService graphQLService, ObjectMapper objectMapper) {
        this.graphQLService = graphQLService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_graphql_mutate", description = """
            Execute GraphQL mutations against the Tanzu Platform API.
            
            Use this tool for operations that create, update, or delete data such as:
            - Creating or updating policies
            - Configuring notification targets
            - Setting up alerts
            - Managing triage status for vulnerabilities
            
            IMPORTANT: For destructive operations (delete, remove, destroy), 
            you MUST set confirm=true to proceed.
            
            Example mutation:
            mutation CreateAlert($input: ObservabilityMetricAlertCreateInput!) {
              observabilityAlertMutationProvider {
                createMetricAlert(input: $input) {
                  id
                  name
                  status
                }
              }
            }
            """)
    public String executeMutation(
            @McpToolParam(description = "GraphQL mutation string. Must start with 'mutation' keyword.") 
            String mutation,
            
            @McpToolParam(description = "Mutation variables as a JSON object string (optional)", required = false) 
            String variables,
            
            @McpToolParam(description = "Named operation to execute if mutation contains multiple operations (optional)", required = false) 
            String operationName,
            
            @McpToolParam(description = "Safety confirmation for destructive operations. Must be true to execute delete/remove operations.", required = false) 
            Boolean confirm
    ) {
        log.info("Executing GraphQL mutation via MCP tool");
        
        try {
            // Check for destructive operations
            if (isDestructive(mutation) && !Boolean.TRUE.equals(confirm)) {
                return formatError("This mutation appears to be destructive (contains delete/remove/destroy). " +
                        "Please set confirm=true to proceed. Make sure you understand the impact of this operation.");
            }
            
            Map<String, Object> variablesMap = parseVariables(variables);
            
            GraphQLRequest request = GraphQLRequest.builder()
                    .query(mutation)
                    .variables(variablesMap)
                    .operationName(operationName)
                    .build();

            GraphQLResponse response = graphQLService.executeMutation(request);
            
            return formatResponse(response);
            
        } catch (GraphQLException e) {
            log.warn("GraphQL mutation failed: {}", e.getMessage());
            return formatError(e);
        } catch (Exception e) {
            log.error("Unexpected error executing mutation: {}", e.getMessage(), e);
            return formatError("Unexpected error: " + e.getMessage());
        }
    }

    private boolean isDestructive(String mutation) {
        if (mutation == null) return false;
        String lower = mutation.toLowerCase();
        return DESTRUCTIVE_KEYWORDS.stream().anyMatch(lower::contains);
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
                    "message", "Mutation executed successfully"
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\": true, \"message\": \"Mutation executed but response serialization failed\"}";
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

