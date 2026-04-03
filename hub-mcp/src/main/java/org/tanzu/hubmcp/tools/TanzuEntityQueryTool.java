package org.tanzu.hubmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.exception.GraphQLException;
import org.tanzu.hubmcp.model.*;
import org.tanzu.hubmcp.service.SchemaIntrospectionService;
import org.tanzu.hubmcp.service.TanzuGraphQLService;

import java.util.*;

/**
 * Composable MCP tool for querying Tanzu Platform entities.
 * Accepts entity type, optional scope (foundation/org/space), and optional filters,
 * then constructs and executes the correct GraphQL query internally.
 */
@Component
public class TanzuEntityQueryTool {

    private static final Logger log = LoggerFactory.getLogger(TanzuEntityQueryTool.class);
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;

    private static final Map<String, String> DOMAIN_LOOKUP = Map.of(
            "tas", "tas",
            "spring", "spring",
            "platform", "platform"
    );

    private static final Map<String, String> ENTITY_TYPE_TO_DOMAIN = buildEntityTypeToDomain();

    private static final Set<String> VALID_OPERATORS = Set.of(
            "EQ", "NEQ", "LT", "LTE", "GT", "GTE",
            "CONTAINS", "DOESNOTCONTAIN", "STARTSWITH", "ENDSWITH",
            "ISNULL", "ISNOTNULL"
    );

    private final TanzuGraphQLService graphQLService;
    private final SchemaIntrospectionService schemaService;
    private final ObjectMapper objectMapper;

    public TanzuEntityQueryTool(TanzuGraphQLService graphQLService,
                                SchemaIntrospectionService schemaService,
                                ObjectMapper objectMapper) {
        this.graphQLService = graphQLService;
        this.schemaService = schemaService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "tanzu_entity_query", description = """
            Query Tanzu Platform entities with composable scope and filters.
            
            Supports ALL entity types across TAS, Spring, and Platform domains.
            The server constructs the correct GraphQL query internally -- you never need to write GraphQL.
            
            Entity types (domain: tas):
              foundation, organization, space, application, serviceinstance,
              boshdirector, boshvm, buildpack, deployment, domain, droplet,
              isolationsegment, opsmanager, organizationquota, processinstance,
              resource, revision, serviceoffering, serviceplan, stemcell,
              tile, vcenterconfig, vmtype
            
            Entity types (domain: spring):
              application, instance, cloudgateway, appreplica
            
            Entity types (domain: platform):
              foundationgroup, organizationgroup, spacegroup
            
            Scoping: narrow results to a parent entity by passing a scope JSON object.
              Example: {"foundation": "ndc.kuhn-labs.com"} or {"foundation": "X", "organization": "Y"}
            
            Filtering: filter by entity properties.
              Example: {"property": "state", "value": "STOPPED"}
              The server validates property names and suggests corrections if wrong.
            """)
    public String queryEntities(
            @McpToolParam(description = "Entity type to query (e.g., 'foundation', 'organization', 'application')")
            String entityType,

            @McpToolParam(description = "Domain: 'tas' (default), 'spring', or 'platform'. Usually inferred from entityType.", required = false)
            String domain,

            @McpToolParam(description = "Scope to a parent entity as JSON. Keys: foundation, organization, space. Example: {\"foundation\": \"ndc.kuhn-labs.com\"}", required = false)
            String scope,

            @McpToolParam(description = "Property filter as JSON. Keys: property, value, operator (default: EQ). Example: {\"property\": \"state\", \"value\": \"STOPPED\"}", required = false)
            String filter,

            @McpToolParam(description = "What to include in results: 'properties' for full details, 'minimal' for names only (default: minimal)", required = false)
            String include,

            @McpToolParam(description = "Maximum number of results (default: 50, max: 200)", required = false)
            String first
    ) {
        log.debug("Entity query: type={}, domain={}, scope={}, filter={}", entityType, domain, scope, filter);

        try {
            if (entityType == null || entityType.isBlank()) {
                return formatError("entityType is required. Valid types: foundation, organization, space, application, ...");
            }

            String normalizedType = entityType.trim().toLowerCase();
            String resolvedDomain = resolveDomain(normalizedType, domain);
            if (resolvedDomain == null) {
                return formatError("Unknown entity type: '" + entityType + "'. " + listValidEntityTypes());
            }

            Map<String, String> scopeMap = parseScope(scope);
            Map<String, Object> filterMap = parseFilter(filter);
            int pageSize = parsePageSize(first);
            boolean includeProperties = "properties".equalsIgnoreCase(include);

            if (filterMap != null) {
                String validationError = validateFilter(filterMap, normalizedType, resolvedDomain);
                if (validationError != null) {
                    return formatError(validationError);
                }
            }

            String query;
            if (scopeMap != null && !scopeMap.isEmpty()) {
                query = buildScopedQuery(normalizedType, resolvedDomain, scopeMap, filterMap, includeProperties, pageSize);
            } else {
                query = buildDirectQuery(normalizedType, resolvedDomain, filterMap, includeProperties, pageSize);
            }

            log.debug("Constructed GraphQL query: {}", query);

            GraphQLRequest request = GraphQLRequest.builder().query(query).build();
            GraphQLResponse response = graphQLService.executeQuery(request);

            return formatResponse(normalizedType, resolvedDomain, scopeMap, response);

        } catch (GraphQLException e) {
            log.warn("Entity query failed: {}", e.getMessage());
            return formatGraphQLError(e);
        } catch (Exception e) {
            log.error("Unexpected error in entity query: {}", e.getMessage(), e);
            return formatError("Unexpected error: " + e.getMessage());
        }
    }

    private String resolveDomain(String entityType, String domainHint) {
        if (domainHint != null && !domainHint.isBlank()) {
            String normalized = domainHint.trim().toLowerCase();
            if (DOMAIN_LOOKUP.containsKey(normalized)) {
                return normalized;
            }
        }
        return ENTITY_TYPE_TO_DOMAIN.get(entityType);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseScope(String scope) {
        if (scope == null || scope.isBlank()) return null;
        try {
            Map<String, Object> raw = objectMapper.readValue(scope, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            for (var entry : raw.entrySet()) {
                result.put(entry.getKey().toLowerCase(), String.valueOf(entry.getValue()));
            }
            return result;
        } catch (JsonProcessingException e) {
            throw new GraphQLException("Invalid scope JSON: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFilter(String filter) {
        if (filter == null || filter.isBlank()) return null;
        try {
            return objectMapper.readValue(filter, Map.class);
        } catch (JsonProcessingException e) {
            throw new GraphQLException("Invalid filter JSON: " + e.getMessage());
        }
    }

    private int parsePageSize(String first) {
        if (first == null || first.isBlank()) return DEFAULT_PAGE_SIZE;
        try {
            int size = Integer.parseInt(first.trim());
            return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        } catch (NumberFormatException e) {
            return DEFAULT_PAGE_SIZE;
        }
    }

    private String validateFilter(Map<String, Object> filterMap, String entityType, String domain) {
        String property = (String) filterMap.get("property");
        if (property == null || property.isBlank()) {
            return "Filter must include 'property'. Example: {\"property\": \"state\", \"value\": \"STOPPED\"}";
        }

        String operator = (String) filterMap.getOrDefault("operator", "EQ");
        if (!VALID_OPERATORS.contains(operator.toUpperCase())) {
            return "Invalid operator: '" + operator + "'. Valid operators: " + VALID_OPERATORS;
        }

        List<String> validProperties = getPropertiesForEntityType(entityType, domain);
        if (validProperties != null && !validProperties.isEmpty() && !validProperties.contains(property)) {
            return "Property '" + property + "' not found on " + entityType + ". Available properties: " +
                    String.join(", ", validProperties);
        }

        return null;
    }

    private List<String> getPropertiesForEntityType(String entityType, String domain) {
        String propertiesTypeName = buildPropertiesTypeName(entityType, domain);
        try {
            Optional<TypeDefinition> typeDef = schemaService.getTypeDetails(propertiesTypeName);
            if (typeDef.isPresent() && typeDef.get().fields() != null) {
                return typeDef.get().fields().stream()
                        .map(FieldDefinition::name)
                        .toList();
            }
        } catch (Exception e) {
            log.debug("Could not look up properties type {}: {}", propertiesTypeName, e.getMessage());
        }
        return null;
    }

    private String buildPropertiesTypeName(String entityType, String domain) {
        String domainPart = switch (domain) {
            case "tas" -> "TAS";
            case "spring" -> "Spring";
            case "platform" -> "Platform";
            default -> domain;
        };
        String entityPart = capitalizeEntityType(entityType);
        return "Entity_Tanzu_" + domainPart + "_" + entityPart + "_Properties";
    }

    private String capitalizeEntityType(String entityType) {
        return switch (entityType) {
            case "boshdirector" -> "BoshDirector";
            case "boshvm" -> "BoshVM";
            case "isolationsegment" -> "IsolationSegment";
            case "opsmanager" -> "OpsManager";
            case "organizationquota" -> "OrganizationQuota";
            case "processinstance" -> "ProcessInstance";
            case "serviceinstance" -> "ServiceInstance";
            case "serviceoffering" -> "ServiceOffering";
            case "serviceplan" -> "ServicePlan";
            case "vcenterconfig" -> "VcenterConfig";
            case "vmtype" -> "VMType";
            case "cloudgateway" -> "CloudGateway";
            case "appreplica" -> "AppReplica";
            case "foundationgroup" -> "FoundationGroup";
            case "organizationgroup" -> "OrganizationGroup";
            case "spacegroup" -> "SpaceGroup";
            default -> entityType.substring(0, 1).toUpperCase() + entityType.substring(1);
        };
    }

    private String buildDirectQuery(String entityType, String domain,
                                    Map<String, Object> filterMap, boolean includeProperties, int pageSize) {
        StringBuilder query = new StringBuilder("query {\n  entityQuery {\n    typed {\n      tanzu {\n");
        query.append("        ").append(domain).append(" {\n");
        query.append("          ").append(entityType).append(" {\n");
        query.append("            query(first: ").append(pageSize);

        if (filterMap != null) {
            query.append(", filter: ").append(buildFilterArgument(filterMap));
        }

        query.append(") {\n");
        query.append(buildNodeFields(entityType, domain, includeProperties));
        query.append("              pageInfo { hasNextPage endCursor }\n");
        query.append("              totalCount\n");
        query.append("            }\n");
        query.append("          }\n");
        query.append("        }\n");
        query.append("      }\n");
        query.append("    }\n");
        query.append("  }\n");
        query.append("}");

        return query.toString();
    }

    private String buildScopedQuery(String entityType, String domain,
                                    Map<String, String> scopeMap, Map<String, Object> filterMap,
                                    boolean includeProperties, int pageSize) {
        String parentType = findScopeParent(scopeMap);
        String parentName = scopeMap.get(parentType);
        String parentDomain = ENTITY_TYPE_TO_DOMAIN.getOrDefault(parentType, "tas");

        String relationshipField = "tanzu_" + domain + "_" + entityType;

        StringBuilder query = new StringBuilder("query {\n  entityQuery {\n    typed {\n      tanzu {\n");
        query.append("        ").append(parentDomain).append(" {\n");
        query.append("          ").append(parentType).append(" {\n");
        query.append("            query(entityName: [\"").append(escapeGraphQL(parentName)).append("\"], first: 1) {\n");
        query.append("              edges {\n");
        query.append("                node {\n");
        query.append("                  entityName\n");
        query.append("                  relationshipsIn {\n");
        query.append("                    isContainedIn {\n");
        query.append("                      ").append(relationshipField).append("(first: ").append(pageSize);

        if (filterMap != null) {
            query.append(", filter: ").append(buildFilterArgument(filterMap));
        }

        query.append(") {\n");
        query.append(buildNestedNodeFields(entityType, domain, includeProperties));
        query.append("                        pageInfo { hasNextPage endCursor }\n");
        query.append("                      }\n");
        query.append("                    }\n");
        query.append("                  }\n");
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

    private String findScopeParent(Map<String, String> scopeMap) {
        // Pick the most specific scope level that has a value
        // Hierarchy: foundation -> organization -> space
        String[] levels = {"space", "organization", "foundation"};
        for (String level : levels) {
            if (scopeMap.containsKey(level)) {
                return level;
            }
        }
        throw new GraphQLException("Scope must contain at least one of: foundation, organization, space");
    }

    private String buildFilterArgument(Map<String, Object> filterMap) {
        String property = (String) filterMap.get("property");
        Object value = filterMap.get("value");
        String operator = ((String) filterMap.getOrDefault("operator", "EQ")).toUpperCase();

        String filterField = "properties." + property;
        String valueStr = formatFilterValue(value);

        return "{field: \"" + filterField + "\", operator: " + operator + ", values: [" + valueStr + "]}";
    }

    private String formatFilterValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        return "\"" + escapeGraphQL(String.valueOf(value)) + "\"";
    }

    private String buildNodeFields(String entityType, String domain, boolean includeProperties) {
        StringBuilder sb = new StringBuilder();
        sb.append("              edges {\n");
        sb.append("                node {\n");
        sb.append("                  entityId\n");
        sb.append("                  entityName\n");
        sb.append("                  entityType\n");
        if (includeProperties) {
            sb.append(buildPropertiesSelection(entityType, domain, "                  "));
        }
        sb.append("                }\n");
        sb.append("              }\n");
        return sb.toString();
    }

    private String buildNestedNodeFields(String entityType, String domain, boolean includeProperties) {
        StringBuilder sb = new StringBuilder();
        sb.append("                        edges {\n");
        sb.append("                          node {\n");
        sb.append("                            entityId\n");
        sb.append("                            entityName\n");
        sb.append("                            entityType\n");
        if (includeProperties) {
            sb.append(buildPropertiesSelection(entityType, domain, "                            "));
        }
        sb.append("                          }\n");
        sb.append("                        }\n");
        return sb.toString();
    }

    private String buildPropertiesSelection(String entityType, String domain, String indent) {
        List<String> props = getPropertiesForEntityType(entityType, domain);
        if (props == null || props.isEmpty()) {
            return "";
        }

        // Select the most useful properties (limit to avoid overly large queries)
        List<String> selectedProps = props.stream()
                .filter(p -> !p.equals("guid") && !p.endsWith("GUID"))
                .limit(10)
                .toList();

        if (selectedProps.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("properties {\n");
        for (String prop : selectedProps) {
            sb.append(indent).append("  ").append(prop).append("\n");
        }
        sb.append(indent).append("}\n");
        return sb.toString();
    }

    private String escapeGraphQL(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatResponse(String entityType, String domain,
                                  Map<String, String> scopeMap, GraphQLResponse response) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("entityType", entityType);
            result.put("domain", domain);
            if (scopeMap != null && !scopeMap.isEmpty()) {
                result.put("scope", scopeMap);
            }
            result.put("data", response.data() != null ? response.data() : Map.of());
            result.put("queryComplexity", response.getQueryComplexity());
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\": true, \"note\": \"Response serialization issue\"}";
        }
    }

    private String formatError(String message) {
        try {
            Map<String, Object> result = Map.of("success", false, "error", message);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\": false, \"error\": \"" + message + "\"}";
        }
    }

    private String formatGraphQLError(GraphQLException e) {
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

    private String listValidEntityTypes() {
        return "Valid TAS types: foundation, organization, space, application, serviceinstance, " +
                "boshdirector, boshvm, buildpack, deployment, domain, droplet, isolationsegment, " +
                "opsmanager, organizationquota, processinstance, resource, revision, serviceoffering, " +
                "serviceplan, stemcell, tile, vcenterconfig, vmtype. " +
                "Valid Spring types: application (domain: spring), instance, cloudgateway, appreplica. " +
                "Valid Platform types: foundationgroup, organizationgroup, spacegroup.";
    }

    private static Map<String, String> buildEntityTypeToDomain() {
        Map<String, String> map = new HashMap<>();
        for (String type : List.of("foundation", "organization", "space", "application",
                "serviceinstance", "boshdirector", "boshvm", "buildpack", "deployment",
                "domain", "droplet", "isolationsegment", "opsmanager", "organizationquota",
                "processinstance", "resource", "revision", "serviceoffering", "serviceplan",
                "stemcell", "tile", "vcenterconfig", "vmtype")) {
            map.put(type, "tas");
        }
        for (String type : List.of("instance", "cloudgateway", "appreplica")) {
            map.put(type, "spring");
        }
        for (String type : List.of("foundationgroup", "organizationgroup", "spacegroup")) {
            map.put(type, "platform");
        }
        return Collections.unmodifiableMap(map);
    }
}
