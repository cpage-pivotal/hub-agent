package org.tanzu.hubmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.exception.SchemaValidationException.ErrorType;
import org.tanzu.hubmcp.exception.SchemaValidationException.ValidationError;
import org.tanzu.hubmcp.model.SchemaCache;
import org.tanzu.hubmcp.service.SchemaIntrospectionService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCP Tool for validating GraphQL queries against the schema before execution.
 */
@Component
public class TanzuValidateQueryTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuValidateQueryTool.class);
    
    // Simple regex patterns for basic query parsing
    private static final Pattern QUERY_PATTERN = Pattern.compile("^\\s*(query|mutation|subscription)\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*[{(:]?");
    private static final Pattern TYPE_ON_PATTERN = Pattern.compile("\\.\\.\\.\\s*on\\s+([a-zA-Z_][a-zA-Z0-9_]*)");

    private final SchemaIntrospectionService schemaService;
    private final ObjectMapper objectMapper;

    public TanzuValidateQueryTool(SchemaIntrospectionService schemaService, ObjectMapper objectMapper) {
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_validate_query", description = """
            Validate a GraphQL query against the Tanzu Platform schema without executing it.
            
            Use this tool BEFORE tanzu_graphql_query to:
            - Catch syntax errors early
            - Find unknown field errors with suggestions
            - Validate type names exist
            - Get complexity estimates
            
            Returns validation results with:
            - valid: true/false
            - errors: list of validation errors
            - suggestions: "did you mean" corrections
            - estimatedComplexity: rough query complexity score
            
            This helps avoid failed API calls and provides actionable feedback for fixing queries.
            """)
    public String validateQuery(
            @McpToolParam(description = "GraphQL query string to validate") 
            String query,
            
            @McpToolParam(description = "Query variables to validate types (optional, as JSON string)", required = false) 
            String variables,
            
            @McpToolParam(description = "Attempt to suggest corrections for errors (default: true)", required = false) 
            Boolean suggestFixes
    ) {
        log.debug("Validating GraphQL query");
        
        try {
            boolean suggest = suggestFixes == null || suggestFixes;
            
            // Perform validation
            ValidationResult result = validate(query, suggest);
            
            return formatResult(result);
            
        } catch (Exception e) {
            log.error("Error validating query: {}", e.getMessage(), e);
            return formatError("Validation error: " + e.getMessage());
        }
    }

    private ValidationResult validate(String query, boolean suggestFixes) {
        List<ValidationError> errors = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        
        // Basic null/empty check
        if (query == null || query.isBlank()) {
            errors.add(ValidationError.builder()
                    .type(ErrorType.SYNTAX_ERROR)
                    .message("Query cannot be null or empty")
                    .build());
            return new ValidationResult(false, errors, suggestions, 0, 0);
        }
        
        // Check query starts with valid keyword
        String trimmed = query.trim();
        if (!QUERY_PATTERN.matcher(trimmed).find() && !trimmed.startsWith("{")) {
            errors.add(ValidationError.builder()
                    .type(ErrorType.SYNTAX_ERROR)
                    .message("Query must start with 'query', 'mutation', 'subscription', or '{'")
                    .build());
        }
        
        // Check for balanced braces
        int braceCount = 0;
        int parenCount = 0;
        for (char c : query.toCharArray()) {
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;
            else if (c == '(') parenCount++;
            else if (c == ')') parenCount--;
        }
        
        if (braceCount != 0) {
            errors.add(ValidationError.builder()
                    .type(ErrorType.SYNTAX_ERROR)
                    .message("Unbalanced braces: " + (braceCount > 0 ? "missing " + braceCount + " closing brace(s)" : "extra " + (-braceCount) + " closing brace(s)"))
                    .build());
        }
        
        if (parenCount != 0) {
            errors.add(ValidationError.builder()
                    .type(ErrorType.SYNTAX_ERROR)
                    .message("Unbalanced parentheses: " + (parenCount > 0 ? "missing " + parenCount + " closing parenthesis" : "extra " + (-parenCount) + " closing parenthesis"))
                    .build());
        }
        
        // Get schema for field/type validation
        SchemaCache schema = null;
        try {
            schema = schemaService.getSchema();
        } catch (Exception e) {
            log.warn("Could not load schema for validation: {}", e.getMessage());
            // Continue without schema validation
        }
        
        if (schema != null) {
            // Check for inline fragment types (... on TypeName)
            Matcher typeMatcher = TYPE_ON_PATTERN.matcher(query);
            while (typeMatcher.find()) {
                String typeName = typeMatcher.group(1);
                if (schema.getType(typeName).isEmpty()) {
                    errors.add(ValidationError.builder()
                            .type(ErrorType.UNKNOWN_TYPE)
                            .typeName(typeName)
                            .message("Unknown type '" + typeName + "' in inline fragment")
                            .build());
                    
                    if (suggestFixes) {
                        List<String> similar = schemaService.findSimilarTypes(typeName);
                        if (!similar.isEmpty()) {
                            suggestions.add("Unknown type '" + typeName + "'. Did you mean: " + 
                                    String.join(", ", similar) + "?");
                        }
                    }
                }
            }
            
            // Look for entity type names in the query and validate them
            List<String> entityTypesInQuery = findEntityTypes(query);
            for (String typeName : entityTypesInQuery) {
                if (schema.getType(typeName).isEmpty()) {
                    errors.add(ValidationError.builder()
                            .type(ErrorType.UNKNOWN_TYPE)
                            .typeName(typeName)
                            .message("Unknown entity type '" + typeName + "'")
                            .build());
                    
                    if (suggestFixes) {
                        List<String> similar = schemaService.findSimilarTypes(typeName);
                        if (!similar.isEmpty()) {
                            suggestions.add("Unknown type '" + typeName + "'. Did you mean: " + 
                                    String.join(", ", similar) + "?");
                        }
                    }
                }
            }
        }
        
        // Estimate complexity based on field count and nesting depth
        int fieldCount = countFields(query);
        int maxDepth = calculateMaxDepth(query);
        int estimatedComplexity = fieldCount * (1 + maxDepth / 2);
        
        boolean valid = errors.isEmpty();
        
        return new ValidationResult(valid, errors, suggestions, estimatedComplexity, fieldCount);
    }

    private List<String> findEntityTypes(String query) {
        List<String> types = new ArrayList<>();
        
        // Look for Entity_Tanzu_ prefixed type names
        Pattern entityPattern = Pattern.compile("\\b(Entity_Tanzu_[a-zA-Z_]+)\\b");
        Matcher matcher = entityPattern.matcher(query);
        
        while (matcher.find()) {
            String typeName = matcher.group(1);
            if (!types.contains(typeName)) {
                types.add(typeName);
            }
        }
        
        return types;
    }

    private int countFields(String query) {
        // Count field-like patterns (simple approximation)
        Matcher matcher = FIELD_PATTERN.matcher(query);
        int count = 0;
        Set<String> keywords = Set.of("query", "mutation", "subscription", "fragment", "on", "true", "false", "null");
        
        while (matcher.find()) {
            String field = matcher.group(1);
            if (!keywords.contains(field.toLowerCase())) {
                count++;
            }
        }
        
        return count;
    }

    private int calculateMaxDepth(String query) {
        int maxDepth = 0;
        int currentDepth = 0;
        
        for (char c : query.toCharArray()) {
            if (c == '{') {
                currentDepth++;
                maxDepth = Math.max(maxDepth, currentDepth);
            } else if (c == '}') {
                currentDepth--;
            }
        }
        
        return maxDepth;
    }

    private String formatResult(ValidationResult result) {
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("valid", result.valid);
            
            if (result.valid) {
                response.put("message", "Query is valid and ready to execute");
                response.put("estimatedComplexity", result.estimatedComplexity);
                response.put("fieldsRequested", result.fieldCount);
            } else {
                response.put("errors", result.errors.stream()
                        .map(e -> Map.of(
                                "type", e.type().name(),
                                "message", e.message()
                        ))
                        .toList());
                
                if (!result.suggestions.isEmpty()) {
                    response.put("suggestions", result.suggestions);
                }
            }
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"valid\": " + result.valid + ", \"error\": \"Formatting error\"}";
        }
    }

    private String formatError(String message) {
        try {
            Map<String, Object> result = Map.of(
                    "valid", false,
                    "error", message
            );
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "{\"valid\": false, \"error\": \"" + message + "\"}";
        }
    }

    /**
     * Internal record to hold validation results.
     */
    private record ValidationResult(
            boolean valid,
            List<ValidationError> errors,
            List<String> suggestions,
            int estimatedComplexity,
            int fieldCount
    ) {}
}

