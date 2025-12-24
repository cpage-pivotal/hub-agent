package org.tanzu.hubmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.model.EntityRelationship;
import org.tanzu.hubmcp.service.SchemaIntrospectionService;

import java.util.*;

/**
 * MCP Tool for finding relationship paths between entity types.
 */
@Component
public class TanzuFindEntityPathTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuFindEntityPathTool.class);

    private final SchemaIntrospectionService schemaService;
    private final ObjectMapper objectMapper;

    public TanzuFindEntityPathTool(SchemaIntrospectionService schemaService, ObjectMapper objectMapper) {
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_find_entity_path", description = """
            Find relationship paths between two entity types in the Tanzu Platform.
            
            Use this tool when you need to:
            - Navigate from one entity to another (e.g., Application to Foundation)
            - Understand how entities are related
            - Build queries that traverse multiple entity types
            
            Common entity paths:
            - Entity_Tanzu_TAS_Application_Type → Entity_Tanzu_TAS_Space_Type → Entity_Tanzu_TAS_Organization_Type → Entity_Tanzu_TAS_Foundation_Type
              (via relationshipsOut.isContainedIn at each step)
            - Entity_Tanzu_TAS_Foundation_Type → Entity_Tanzu_TAS_BoshDirector_Type
            
            IMPORTANT: All entity type names must include the '_Type' suffix.
            
            The tool returns possible paths with example query templates showing the correct relationship navigation.
            """)
    public String findPath(
            @McpToolParam(description = "Starting entity type - must include _Type suffix (e.g., Entity_Tanzu_TAS_Application_Type)") 
            String fromType,
            
            @McpToolParam(description = "Target entity type - must include _Type suffix (e.g., Entity_Tanzu_TAS_Foundation_Type)") 
            String toType,
            
            @McpToolParam(description = "Maximum traversal depth (default: 3, max: 5)", required = false) 
            Integer maxDepth
    ) {
        log.debug("Finding path from {} to {}", fromType, toType);
        
        try {
            // Validate input
            if (fromType == null || fromType.isBlank()) {
                return formatError("fromType is required");
            }
            if (toType == null || toType.isBlank()) {
                return formatError("toType is required");
            }
            
            int depth = maxDepth != null ? Math.min(maxDepth, 5) : 3;
            
            // Verify types exist
            if (schemaService.getTypeDetails(fromType).isEmpty()) {
                List<String> similar = schemaService.findSimilarTypes(fromType);
                return formatError("Type '" + fromType + "' not found." + 
                        (similar.isEmpty() ? "" : " Did you mean: " + String.join(", ", similar) + "?"));
            }
            
            if (schemaService.getTypeDetails(toType).isEmpty()) {
                List<String> similar = schemaService.findSimilarTypes(toType);
                return formatError("Type '" + toType + "' not found." + 
                        (similar.isEmpty() ? "" : " Did you mean: " + String.join(", ", similar) + "?"));
            }
            
            // Find paths
            List<List<EntityRelationship>> paths = schemaService.findRelationshipPaths(fromType, toType, depth);
            
            if (paths.isEmpty()) {
                return formatNoPathFound(fromType, toType, depth);
            }
            
            return formatPaths(fromType, toType, paths);
            
        } catch (Exception e) {
            log.error("Error finding path: {}", e.getMessage(), e);
            return formatError("Error finding path: " + e.getMessage());
        }
    }

    private String formatPaths(String fromType, String toType, List<List<EntityRelationship>> paths) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromType", fromType);
        result.put("toType", toType);
        result.put("pathsFound", paths.size());
        
        List<Map<String, Object>> pathList = new ArrayList<>();
        
        for (int i = 0; i < paths.size(); i++) {
            List<EntityRelationship> path = paths.get(i);
            Map<String, Object> pathInfo = new LinkedHashMap<>();
            pathInfo.put("pathNumber", i + 1);
            pathInfo.put("steps", path.size());
            
            // Format path as readable steps
            List<String> steps = new ArrayList<>();
            steps.add(fromType);
            for (EntityRelationship rel : path) {
                steps.add("  --[" + rel.fieldName() + "]--> " + rel.targetEntity());
            }
            pathInfo.put("traversal", steps);
            
            // Generate query template
            pathInfo.put("queryTemplate", generateQueryTemplate(fromType, path));
            
            pathList.add(pathInfo);
        }
        
        result.put("paths", pathList);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return formatError("Error formatting paths");
        }
    }

    private String generateQueryTemplate(String fromType, List<EntityRelationship> path) {
        // Parse entity type name to extract domain and entity
        // Format: Entity_Tanzu_{Domain}_{EntityName}_Type
        String[] parts = fromType.split("_");
        if (parts.length < 5 || !parts[0].equals("Entity") || !parts[1].equals("Tanzu") || !fromType.endsWith("_Type")) {
            return "# Non-standard entity type - consult schema documentation";
        }
        
        String domain = parts[2].toLowerCase(); // TAS -> tas
        String entityName = parts[3].toLowerCase(); // Application -> application
        
        StringBuilder query = new StringBuilder();
        query.append("query {\n");
        query.append("  entityQuery {\n");
        query.append("    typed {\n");
        query.append("      tanzu {\n");
        query.append("        ").append(domain).append(" {\n");
        query.append("          ").append(entityName).append(" {\n");
        query.append("            query(first: 10) {\n");
        query.append("              edges {\n");
        query.append("                node {\n");
        query.append("                  id\n");
        query.append("                  properties { name }\n");
        
        // Build nested relationship traversal
        int indent = 18;
        for (EntityRelationship rel : path) {
            String spaces = " ".repeat(indent);
            query.append(spaces).append(rel.fieldName()).append(" {\n");
            query.append(spaces).append("  edges {\n");
            query.append(spaces).append("    node {\n");
            query.append(spaces).append("      ... on ").append(rel.targetEntity()).append(" {\n");
            query.append(spaces).append("        id\n");
            query.append(spaces).append("        properties { name }\n");
            indent += 8;
        }
        
        // Close all nested blocks
        for (int i = path.size() - 1; i >= 0; i--) {
            indent -= 8;
            String spaces = " ".repeat(indent);
            query.append(spaces).append("      }\n");
            query.append(spaces).append("    }\n");
            query.append(spaces).append("  }\n");
            query.append(spaces).append("}\n");
        }
        
        query.append("                }\n");
        query.append("              }\n");
        query.append("            }\n");
        query.append("          }\n");
        query.append("        }\n");
        query.append("      }\n");
        query.append("    }\n");
        query.append("  }\n");
        query.append("}");
        
        return query.toString();
    }

    private String formatNoPathFound(String fromType, String toType, int maxDepth) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromType", fromType);
        result.put("toType", toType);
        result.put("pathsFound", 0);
        result.put("message", "No path found between " + fromType + " and " + toType + 
                " within " + maxDepth + " steps.");
        result.put("suggestions", List.of(
                "Try increasing maxDepth (current: " + maxDepth + ")",
                "Verify both types are entity types (should start with Entity_Tanzu_)",
                "Use tanzu_explore_schema to check available relationships for each type"
        ));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return formatError("Error formatting response");
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

