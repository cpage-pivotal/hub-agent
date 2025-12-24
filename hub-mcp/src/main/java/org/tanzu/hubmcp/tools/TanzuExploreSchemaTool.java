package org.tanzu.hubmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.model.FieldDefinition;
import org.tanzu.hubmcp.model.TypeDefinition;
import org.tanzu.hubmcp.service.SchemaIntrospectionService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP Tool for exploring the Tanzu Platform GraphQL API schema.
 */
@Component
public class TanzuExploreSchemaTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuExploreSchemaTool.class);
    
    // Common fields that are frequently useful
    private static final Set<String> COMMON_FIELDS = Set.of(
            "id", "name", "properties", "state", "status", "createdAt", "updatedAt",
            "description", "version", "type", "severity", "score"
    );

    private final SchemaIntrospectionService schemaService;
    private final ObjectMapper objectMapper;

    public TanzuExploreSchemaTool(SchemaIntrospectionService schemaService, ObjectMapper objectMapper) {
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_explore_schema", description = """
            Explore the Tanzu Platform GraphQL API schema (1,382 types).
            
            Use this tool to:
            - Look up specific type definitions
            - Search for types by concept (e.g., 'vulnerability', 'application')
            - Filter by domain: TAS, Spring, Observability, Security, Capacity, Fleet, Insights
            - Filter by category: OBJECT, INPUT_OBJECT, ENUM, INTERFACE, SCALAR
            - View entity relationships (RelIn/RelOut fields)
            
            Domain prefixes:
            - TAS: Entity_Tanzu_TAS_* (Foundations, Organizations, Spaces, Applications)
            - Spring: Entity_Tanzu_Spring_* (Spring Boot applications)
            - Security: *Vulnerability*, *CVE* (Security findings)
            - Observability: Observability*, *Alert*, *Metric* (Monitoring)
            - Capacity: Capacity*, *Recommendation* (Resource management)
            
            Results are limited to 20 types per request to avoid overwhelming context.
            """)
    public String exploreSchema(
            @McpToolParam(description = "Specific type name to explore - entity types must include _Type suffix (e.g., Entity_Tanzu_TAS_Application_Type)", required = false) 
            String typeName,
            
            @McpToolParam(description = "Search types/fields by concept (e.g., 'vulnerability', 'application health')", required = false) 
            String search,
            
            @McpToolParam(description = "Filter by domain: TAS, Spring, Observability, Security, Capacity, Fleet, Insights", required = false) 
            String domain,
            
            @McpToolParam(description = "Filter by category: OBJECT, INPUT_OBJECT, ENUM, INTERFACE, SCALAR", required = false) 
            String category,
            
            @McpToolParam(description = "Include relationship fields (relationshipsIn/relationshipsOut) for entity types. Default: false", required = false) 
            Boolean showRelationships,
            
            @McpToolParam(description = "Highlight commonly-used fields. Default: false", required = false) 
            Boolean showCommonFields
    ) {
        log.debug("Exploring schema: typeName={}, search={}, domain={}, category={}", 
                typeName, search, domain, category);
        
        try {
            // If a specific type is requested, return its details
            if (typeName != null && !typeName.isBlank()) {
                return exploreSpecificType(typeName, 
                        Boolean.TRUE.equals(showRelationships), 
                        Boolean.TRUE.equals(showCommonFields));
            }
            
            // Otherwise, search/filter types
            List<TypeDefinition> types = schemaService.searchTypes(search, domain, category);
            return formatTypeList(types, domain, search);
            
        } catch (Exception e) {
            log.error("Error exploring schema: {}", e.getMessage(), e);
            return formatError("Error exploring schema: " + e.getMessage());
        }
    }

    private String exploreSpecificType(String typeName, boolean showRelationships, boolean showCommonFields) {
        Optional<TypeDefinition> typeOpt = schemaService.getTypeDetails(typeName);
        
        if (typeOpt.isEmpty()) {
            // Try to find similar types
            List<String> similar = schemaService.findSimilarTypes(typeName);
            if (!similar.isEmpty()) {
                return formatError("Type '" + typeName + "' not found. Did you mean: " + 
                        String.join(", ", similar) + "?");
            }
            return formatError("Type '" + typeName + "' not found.");
        }

        TypeDefinition type = typeOpt.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", type.name());
        result.put("kind", type.kind());
        
        if (type.description() != null) {
            result.put("description", type.description());
        }
        
        if (type.interfaces() != null && !type.interfaces().isEmpty()) {
            result.put("interfaces", type.interfaces());
        }

        // Format fields
        if (type.fields() != null && !type.fields().isEmpty()) {
            List<Map<String, Object>> fieldsList = new ArrayList<>();
            List<Map<String, Object>> relationshipFields = new ArrayList<>();
            
            for (FieldDefinition field : type.fields()) {
                Map<String, Object> fieldInfo = formatField(field, showCommonFields);
                
                if (field.name().endsWith("_RelIn") || field.name().endsWith("_RelOut")) {
                    if (showRelationships) {
                        relationshipFields.add(fieldInfo);
                    }
                } else {
                    fieldsList.add(fieldInfo);
                }
            }
            
            result.put("fields", fieldsList);
            
            if (showRelationships && !relationshipFields.isEmpty()) {
                result.put("relationships", relationshipFields);
            }
        }

        // Format enum values
        if (type.enumValues() != null && !type.enumValues().isEmpty()) {
            List<Map<String, Object>> enumList = type.enumValues().stream()
                    .map(ev -> {
                        Map<String, Object> evMap = new LinkedHashMap<>();
                        evMap.put("name", ev.name());
                        if (ev.description() != null) {
                            evMap.put("description", ev.description());
                        }
                        if (ev.deprecated()) {
                            evMap.put("deprecated", true);
                            if (ev.deprecationReason() != null) {
                                evMap.put("deprecationReason", ev.deprecationReason());
                            }
                        }
                        return evMap;
                    })
                    .collect(Collectors.toList());
            result.put("enumValues", enumList);
        }

        // Add example query if it's an entity type
        if (type.name() != null && type.name().startsWith("Entity_Tanzu_")) {
            result.put("exampleQuery", generateExampleQuery(type));
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return formatError("Error formatting type details");
        }
    }

    private Map<String, Object> formatField(FieldDefinition field, boolean showCommonFields) {
        Map<String, Object> fieldInfo = new LinkedHashMap<>();
        fieldInfo.put("name", field.name());
        
        if (field.type() != null) {
            fieldInfo.put("type", formatTypeReference(field.type()));
        }
        
        if (field.description() != null) {
            fieldInfo.put("description", field.description());
        }
        
        if (field.args() != null && !field.args().isEmpty()) {
            List<String> argNames = field.args().stream()
                    .map(arg -> arg.name() + ": " + formatTypeReference(arg.type()))
                    .toList();
            fieldInfo.put("arguments", argNames);
        }
        
        if (field.deprecated()) {
            fieldInfo.put("deprecated", true);
            if (field.deprecationReason() != null) {
                fieldInfo.put("deprecationReason", field.deprecationReason());
            }
        }
        
        if (showCommonFields && COMMON_FIELDS.contains(field.name())) {
            fieldInfo.put("commonField", true);
        }
        
        return fieldInfo;
    }

    private String formatTypeReference(org.tanzu.hubmcp.model.TypeReference type) {
        if (type == null) return "Unknown";
        
        StringBuilder sb = new StringBuilder();
        boolean isNonNull = false;
        
        org.tanzu.hubmcp.model.TypeReference current = type;
        List<String> wrappers = new ArrayList<>();
        
        while (current != null) {
            if ("NON_NULL".equals(current.kind())) {
                isNonNull = true;
            } else if ("LIST".equals(current.kind())) {
                wrappers.add("LIST");
            } else if (current.name() != null) {
                sb.append(current.name());
            }
            current = current.ofType();
        }
        
        String typeName = sb.toString();
        
        // Wrap with list notation
        for (int i = wrappers.size() - 1; i >= 0; i--) {
            typeName = "[" + typeName + "]";
        }
        
        if (isNonNull) {
            typeName = typeName + "!";
        }
        
        return typeName;
    }

    private String formatTypeList(List<TypeDefinition> types, String domain, String search) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalFound", types.size());
        
        if (domain != null) {
            result.put("domain", domain);
        }
        if (search != null) {
            result.put("searchTerm", search);
        }
        
        List<Map<String, Object>> typeList = types.stream()
                .map(t -> {
                    Map<String, Object> typeInfo = new LinkedHashMap<>();
                    typeInfo.put("name", t.name());
                    typeInfo.put("kind", t.kind());
                    if (t.description() != null) {
                        // Truncate long descriptions
                        String desc = t.description();
                        if (desc.length() > 100) {
                            desc = desc.substring(0, 100) + "...";
                        }
                        typeInfo.put("description", desc);
                    }
                    if (t.fields() != null) {
                        typeInfo.put("fieldCount", t.fields().size());
                    }
                    return typeInfo;
                })
                .collect(Collectors.toList());
        
        result.put("types", typeList);
        
        if (types.size() == 20) {
            result.put("note", "Results limited to 20 types. Use more specific search terms or filters to narrow results.");
        }

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return formatError("Error formatting type list");
        }
    }

    private String generateExampleQuery(TypeDefinition type) {
        String typeName = type.name();
        
        // Parse entity type name to extract domain and entity
        // Format: Entity_Tanzu_{Domain}_{EntityName}_Type
        String[] parts = typeName.split("_");
        if (parts.length < 5 || !parts[0].equals("Entity") || !parts[1].equals("Tanzu") || !typeName.endsWith("_Type")) {
            // Not a standard entity type, return simple query
            return "# Non-standard entity type - consult schema documentation";
        }
        
        String domain = parts[2].toLowerCase(); // TAS -> tas
        String entityName = parts[3].toLowerCase(); // Foundation -> foundation (simplified)
        
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
        
        // Add properties if available
        if (type.fields() != null) {
            boolean hasProperties = type.fields().stream()
                    .anyMatch(f -> "properties".equals(f.name()));
            if (hasProperties) {
                query.append("                  properties {\n");
                query.append("                    name\n");
                query.append("                  }\n");
            }
        }
        
        query.append("                }\n");
        query.append("              }\n");
        query.append("              pageInfo {\n");
        query.append("                hasNextPage\n");
        query.append("                endCursor\n");
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

