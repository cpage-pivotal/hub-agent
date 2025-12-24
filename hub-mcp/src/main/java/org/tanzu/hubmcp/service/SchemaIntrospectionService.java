package org.tanzu.hubmcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.tanzu.hubmcp.model.*;

import java.time.Instant;
import java.util.*;

/**
 * Service for schema introspection, caching, and exploration.
 */
@Service
public class SchemaIntrospectionService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIntrospectionService.class);

    private final TanzuGraphQLService graphQLService;
    private final CacheManager cacheManager;

    public SchemaIntrospectionService(TanzuGraphQLService graphQLService, CacheManager cacheManager) {
        this.graphQLService = graphQLService;
        this.cacheManager = cacheManager;
    }

    /**
     * Get the cached schema, loading it if necessary.
     */
    @Cacheable(value = "graphql-schema")
    public SchemaCache getSchema() {
        log.info("Loading schema from Tanzu Platform API");
        Map<String, Object> schemaData = graphQLService.introspectSchema();
        return parseSchema(schemaData);
    }

    /**
     * Check if the schema is loaded.
     */
    public boolean isSchemaLoaded() {
        var cache = cacheManager.getCache("graphql-schema");
        return cache != null && cache.get("getSchema") != null;
    }

    /**
     * Get details for a specific type.
     */
    @Cacheable(value = "type-definitions", key = "#typeName")
    public Optional<TypeDefinition> getTypeDetails(String typeName) {
        SchemaCache schema = getSchema();
        return schema.getType(typeName);
    }

    /**
     * Get entity relationships from the schema.
     */
    @Cacheable(value = "entity-relationships")
    public Map<String, List<EntityRelationship>> getEntityRelationships() {
        SchemaCache schema = getSchema();
        return buildRelationshipGraph(schema);
    }

    /**
     * Search for types by name pattern.
     */
    public List<TypeDefinition> searchTypes(String search, String domain, String category) {
        SchemaCache schema = getSchema();
        List<TypeDefinition> types = schema.getTypes();

        return types.stream()
                .filter(t -> matchesSearch(t, search))
                .filter(t -> matchesDomain(t, domain))
                .filter(t -> matchesCategory(t, category))
                .limit(20) // Limit results to avoid overwhelming context
                .toList();
    }

    /**
     * Find types by domain prefix.
     */
    public List<TypeDefinition> getTypesByDomain(String domain) {
        SchemaCache schema = getSchema();
        String prefix = getDomainPrefix(domain);
        if (prefix == null) {
            return Collections.emptyList();
        }
        return schema.getTypesByPrefix(prefix);
    }

    /**
     * Find similar type names using Levenshtein distance.
     */
    public List<String> findSimilarTypes(String typeName) {
        SchemaCache schema = getSchema();
        return schema.getTypes().stream()
                .map(TypeDefinition::name)
                .filter(Objects::nonNull)
                .map(name -> new AbstractMap.SimpleEntry<>(name, levenshteinDistance(typeName.toLowerCase(), name.toLowerCase())))
                .filter(entry -> entry.getValue() <= 3) // Max edit distance of 3
                .sorted(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue))
                .limit(5)
                .map(AbstractMap.SimpleEntry::getKey)
                .toList();
    }

    /**
     * Find similar field names for a given type.
     */
    public List<String> findSimilarFields(String parentType, String fieldName) {
        return getTypeDetails(parentType)
                .map(TypeDefinition::fields)
                .orElse(Collections.emptyList())
                .stream()
                .map(FieldDefinition::name)
                .filter(Objects::nonNull)
                .map(name -> new AbstractMap.SimpleEntry<>(name, levenshteinDistance(fieldName.toLowerCase(), name.toLowerCase())))
                .filter(entry -> entry.getValue() <= 3)
                .sorted(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue))
                .limit(5)
                .map(AbstractMap.SimpleEntry::getKey)
                .toList();
    }

    /**
     * Find relationship paths between two entity types.
     */
    public List<List<EntityRelationship>> findRelationshipPaths(String fromType, String toType, int maxDepth) {
        Map<String, List<EntityRelationship>> graph = getEntityRelationships();
        List<List<EntityRelationship>> paths = new ArrayList<>();
        
        // BFS to find paths
        Queue<List<EntityRelationship>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        visited.add(fromType);
        
        // Initialize with relationships from the starting type
        List<EntityRelationship> startRels = graph.getOrDefault(fromType, Collections.emptyList());
        for (EntityRelationship rel : startRels) {
            List<EntityRelationship> path = new ArrayList<>();
            path.add(rel);
            if (rel.targetEntity().equals(toType)) {
                paths.add(path);
            } else {
                queue.offer(path);
            }
        }
        
        while (!queue.isEmpty() && paths.size() < 5) { // Limit to 5 paths
            List<EntityRelationship> currentPath = queue.poll();
            if (currentPath.size() >= maxDepth) {
                continue;
            }
            
            EntityRelationship lastRel = currentPath.get(currentPath.size() - 1);
            String currentEntity = lastRel.targetEntity();
            
            if (visited.contains(currentEntity)) {
                continue;
            }
            visited.add(currentEntity);
            
            List<EntityRelationship> nextRels = graph.getOrDefault(currentEntity, Collections.emptyList());
            for (EntityRelationship nextRel : nextRels) {
                List<EntityRelationship> newPath = new ArrayList<>(currentPath);
                newPath.add(nextRel);
                
                if (nextRel.targetEntity().equals(toType)) {
                    paths.add(newPath);
                } else if (newPath.size() < maxDepth) {
                    queue.offer(newPath);
                }
            }
        }
        
        return paths;
    }

    /**
     * Refresh the schema cache.
     */
    public void refreshSchema() {
        log.info("Refreshing schema cache");
        var schemaCache = cacheManager.getCache("graphql-schema");
        var relationshipsCache = cacheManager.getCache("entity-relationships");
        var typeDefsCache = cacheManager.getCache("type-definitions");
        
        if (schemaCache != null) schemaCache.clear();
        if (relationshipsCache != null) relationshipsCache.clear();
        if (typeDefsCache != null) typeDefsCache.clear();
        
        getSchema(); // Reload
    }

    @SuppressWarnings("unchecked")
    private SchemaCache parseSchema(Map<String, Object> schemaData) {
        SchemaCache cache = new SchemaCache();
        cache.setTimestamp(Instant.now());

        if (schemaData == null) {
            log.warn("Schema data is null");
            return cache;
        }

        Map<String, Object> schema = (Map<String, Object>) schemaData.get("__schema");
        if (schema == null) {
            log.warn("__schema not found in introspection result");
            return cache;
        }

        List<Map<String, Object>> types = (List<Map<String, Object>>) schema.get("types");
        if (types == null) {
            log.warn("No types found in schema");
            return cache;
        }

        for (Map<String, Object> typeData : types) {
            TypeDefinition type = parseType(typeData);
            if (type != null && type.name() != null && !type.name().startsWith("__")) {
                cache.addType(type);
            }
        }

        log.info("Parsed {} types from schema", cache.getTypeCount());
        return cache;
    }

    @SuppressWarnings("unchecked")
    private TypeDefinition parseType(Map<String, Object> typeData) {
        String name = (String) typeData.get("name");
        String kind = (String) typeData.get("kind");
        String description = (String) typeData.get("description");

        List<FieldDefinition> fields = null;
        List<Map<String, Object>> fieldsData = (List<Map<String, Object>>) typeData.get("fields");
        if (fieldsData != null) {
            fields = fieldsData.stream()
                    .map(this::parseField)
                    .filter(Objects::nonNull)
                    .toList();
        }

        List<String> interfaces = null;
        List<Map<String, Object>> interfacesData = (List<Map<String, Object>>) typeData.get("interfaces");
        if (interfacesData != null) {
            interfaces = interfacesData.stream()
                    .map(i -> (String) i.get("name"))
                    .filter(Objects::nonNull)
                    .toList();
        }

        List<EnumValue> enumValues = null;
        List<Map<String, Object>> enumData = (List<Map<String, Object>>) typeData.get("enumValues");
        if (enumData != null) {
            enumValues = enumData.stream()
                    .map(this::parseEnumValue)
                    .filter(Objects::nonNull)
                    .toList();
        }

        return TypeDefinition.builder()
                .name(name)
                .kind(kind)
                .description(description)
                .fields(fields)
                .interfaces(interfaces)
                .enumValues(enumValues)
                .build();
    }

    @SuppressWarnings("unchecked")
    private FieldDefinition parseField(Map<String, Object> fieldData) {
        String name = (String) fieldData.get("name");
        String description = (String) fieldData.get("description");
        Boolean isDeprecated = (Boolean) fieldData.get("isDeprecated");
        String deprecationReason = (String) fieldData.get("deprecationReason");

        TypeReference type = null;
        Map<String, Object> typeData = (Map<String, Object>) fieldData.get("type");
        if (typeData != null) {
            type = parseTypeReference(typeData);
        }

        List<InputValue> args = null;
        List<Map<String, Object>> argsData = (List<Map<String, Object>>) fieldData.get("args");
        if (argsData != null) {
            args = argsData.stream()
                    .map(this::parseInputValue)
                    .filter(Objects::nonNull)
                    .toList();
        }

        return FieldDefinition.builder()
                .name(name)
                .description(description)
                .type(type)
                .args(args)
                .deprecated(Boolean.TRUE.equals(isDeprecated))
                .deprecationReason(deprecationReason)
                .build();
    }

    @SuppressWarnings("unchecked")
    private TypeReference parseTypeReference(Map<String, Object> typeData) {
        String kind = (String) typeData.get("kind");
        String name = (String) typeData.get("name");
        
        TypeReference ofType = null;
        Map<String, Object> ofTypeData = (Map<String, Object>) typeData.get("ofType");
        if (ofTypeData != null) {
            ofType = parseTypeReference(ofTypeData);
        }

        return TypeReference.builder()
                .kind(kind)
                .name(name)
                .ofType(ofType)
                .build();
    }

    @SuppressWarnings("unchecked")
    private InputValue parseInputValue(Map<String, Object> inputData) {
        String name = (String) inputData.get("name");
        String description = (String) inputData.get("description");
        String defaultValue = (String) inputData.get("defaultValue");

        TypeReference type = null;
        Map<String, Object> typeData = (Map<String, Object>) inputData.get("type");
        if (typeData != null) {
            type = parseTypeReference(typeData);
        }

        return InputValue.builder()
                .name(name)
                .description(description)
                .type(type)
                .defaultValue(defaultValue)
                .build();
    }

    private EnumValue parseEnumValue(Map<String, Object> enumData) {
        String name = (String) enumData.get("name");
        String description = (String) enumData.get("description");
        Boolean isDeprecated = (Boolean) enumData.get("isDeprecated");
        String deprecationReason = (String) enumData.get("deprecationReason");

        return EnumValue.builder()
                .name(name)
                .description(description)
                .deprecated(Boolean.TRUE.equals(isDeprecated))
                .deprecationReason(deprecationReason)
                .build();
    }

    private Map<String, List<EntityRelationship>> buildRelationshipGraph(SchemaCache schema) {
        Map<String, List<EntityRelationship>> graph = new HashMap<>();

        // Find all entity types (types ending with _Type)
        List<TypeDefinition> entityTypes = schema.getTypes().stream()
                .filter(t -> t.name() != null && t.name().endsWith("_Type"))
                .filter(t -> t.name().startsWith("Entity_Tanzu_"))
                .toList();

        log.debug("Building relationship graph for {} entity types", entityTypes.size());

        for (TypeDefinition entity : entityTypes) {
            List<EntityRelationship> relationships = new ArrayList<>();
            
            if (entity.fields() != null) {
                // Look for relationshipsIn and relationshipsOut fields
                for (FieldDefinition field : entity.fields()) {
                    if ("relationshipsIn".equals(field.name())) {
                        // Get the RelIn type and explore its fields
                        String relInTypeName = extractTypeName(field.type());
                        log.debug("Entity {} has relationshipsIn field pointing to type: {}", entity.name(), relInTypeName);
                        if (relInTypeName != null) {
                            relationships.addAll(extractRelationshipsFromType(schema, entity.name(), relInTypeName, "IN"));
                        }
                    } else if ("relationshipsOut".equals(field.name())) {
                        // Get the RelOut type and explore its fields
                        String relOutTypeName = extractTypeName(field.type());
                        log.debug("Entity {} has relationshipsOut field pointing to type: {}", entity.name(), relOutTypeName);
                        if (relOutTypeName != null) {
                            relationships.addAll(extractRelationshipsFromType(schema, entity.name(), relOutTypeName, "OUT"));
                        }
                    }
                }
            }
            
            if (!relationships.isEmpty()) {
                log.debug("Entity {} has {} relationships", entity.name(), relationships.size());
                graph.put(entity.name(), relationships);
            }
        }

        log.info("Built relationship graph with {} entities having relationships", graph.size());
        return graph;
    }
    
    private List<EntityRelationship> extractRelationshipsFromType(SchemaCache schema, String sourceEntity, String relTypeName, String direction) {
        List<EntityRelationship> relationships = new ArrayList<>();
        
        Optional<TypeDefinition> relTypeOpt = schema.getType(relTypeName);
        if (relTypeOpt.isEmpty()) {
            log.warn("RelationshipType {} not found in schema for entity {}", relTypeName, sourceEntity);
            return relationships;
        }
        
        TypeDefinition relType = relTypeOpt.get();
        if (relType.fields() == null || relType.fields().isEmpty()) {
            log.warn("RelationshipType {} has no fields", relTypeName);
            return relationships;
        }
        
        log.debug("Extracting {} relationships from {} type with {} fields", direction, relTypeName, relType.fields().size());
        
        // Each field in the RelIn/RelOut type represents a relationship type
        for (FieldDefinition relField : relType.fields()) {
            String relationshipName = relField.name();
            String fieldTypeName = extractTypeName(relField.type());
            
            log.debug("Relationship field '{}' has type: {}", relationshipName, fieldTypeName);
            
            // The field returns a connection type, we need to find the target entity type
            // by looking at the node type within the connection
            String targetEntity = extractTargetEntityFromConnection(schema, relField.type());
            
            if (targetEntity != null) {
                log.debug("Found relationship: {} --[{}]--> {}", sourceEntity, relationshipName, targetEntity);
                relationships.add(EntityRelationship.builder()
                        .sourceEntity(sourceEntity)
                        .targetEntity(targetEntity)
                        .relationshipType(relationshipName)
                        .direction(direction)
                        .fieldName("relationships" + direction.substring(0, 1) + direction.substring(1).toLowerCase() + "." + relationshipName)
                        .build());
            } else {
                log.debug("Could not extract target entity for relationship field: {}", relationshipName);
            }
        }
        
        log.debug("Extracted {} relationships from {}", relationships.size(), relTypeName);
        return relationships;
    }
    
    private String extractTargetEntityFromConnection(SchemaCache schema, TypeReference typeRef) {
        // Get the relationship type name (e.g., Entity_Tanzu_TAS_Application_IsContainedIn_RelOut)
        String relTypeName = extractTypeName(typeRef);
        if (relTypeName == null) {
            log.debug("Field type is null, cannot extract relationship type name");
            return null;
        }
        
        log.debug("Looking for relationship type: {}", relTypeName);
        
        // Get the relationship type definition
        Optional<TypeDefinition> relTypeOpt = schema.getType(relTypeName);
        if (relTypeOpt.isEmpty()) {
            log.debug("Relationship type '{}' not found in schema", relTypeName);
            return null;
        }
        
        TypeDefinition relType = relTypeOpt.get();
        if (relType.fields() == null) {
            log.debug("Relationship type '{}' has no fields", relTypeName);
            return null;
        }
        
        log.debug("Relationship type '{}' has {} fields", relTypeName, relType.fields().size());
        
        // The fields in this type are snake_case entity names (e.g., tanzu_tas_space, tanzu_tas_organization)
        // We need to convert these to proper entity type names (e.g., Entity_Tanzu_TAS_Space_Type)
        List<String> targetEntities = new ArrayList<>();
        for (FieldDefinition field : relType.fields()) {
            String snakeCaseName = field.name();
            // Convert snake_case to Entity_X_Type format
            String entityType = convertSnakeCaseToEntityType(snakeCaseName);
            log.debug("Field '{}' -> entity type '{}'", snakeCaseName, entityType);
            
            // Verify this entity type exists in the schema
            if (schema.getType(entityType).isPresent()) {
                targetEntities.add(entityType);
            } else {
                log.debug("Entity type '{}' not found in schema", entityType);
            }
        }
        
        // Return the first valid target entity (there may be multiple in some cases)
        if (!targetEntities.isEmpty()) {
            log.debug("Found {} target entities: {}", targetEntities.size(), targetEntities);
            return targetEntities.get(0);
        }
        
        log.debug("No valid target entity found in relationship type '{}'", relTypeName);
        return null;
    }
    
    /**
     * Converts snake_case entity name to Entity_X_Type format.
     * Example: tanzu_tas_space -> Entity_Tanzu_TAS_Space_Type
     * 
     * Special handling for known acronyms like TAS, Platform components.
     */
    private String convertSnakeCaseToEntityType(String snakeCaseName) {
        if (snakeCaseName == null || snakeCaseName.isEmpty()) {
            return null;
        }
        
        // Known acronyms and special cases that should be uppercase
        Set<String> acronyms = Set.of("tas", "tkg", "tmc", "aws", "gcp", "azure", "vm", "bosh");
        
        // Split by underscore and capitalize each part
        String[] parts = snakeCaseName.split("_");
        StringBuilder result = new StringBuilder("Entity");
        
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (acronyms.contains(part.toLowerCase())) {
                    // Keep acronyms uppercase
                    result.append("_").append(part.toUpperCase());
                } else {
                    // Title case for regular words
                    result.append("_")
                          .append(part.substring(0, 1).toUpperCase())
                          .append(part.substring(1).toLowerCase());
                }
            }
        }
        
        result.append("_Type");
        return result.toString();
    }
    
    private String extractTypeName(TypeReference type) {
        if (type == null) return null;
        
        // Unwrap NON_NULL and LIST to find the actual type name
        if ("NON_NULL".equals(type.kind()) || "LIST".equals(type.kind())) {
            return extractTypeName(type.ofType());
        }
        
        return type.name();
    }

    private boolean matchesSearch(TypeDefinition type, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String lowerSearch = search.toLowerCase();
        String name = type.name();
        String description = type.description();
        
        return (name != null && name.toLowerCase().contains(lowerSearch)) ||
               (description != null && description.toLowerCase().contains(lowerSearch));
    }

    private boolean matchesDomain(TypeDefinition type, String domain) {
        if (domain == null || domain.isBlank()) {
            return true;
        }
        String prefix = getDomainPrefix(domain);
        if (prefix == null) {
            return true;
        }
        return type.name() != null && type.name().contains(prefix);
    }

    private boolean matchesCategory(TypeDefinition type, String category) {
        if (category == null || category.isBlank()) {
            return true;
        }
        return category.equalsIgnoreCase(type.kind());
    }

    private String getDomainPrefix(String domain) {
        if (domain == null) return null;
        return switch (domain.toUpperCase()) {
            case "TAS" -> "Tanzu_TAS";
            case "SPRING" -> "Tanzu_Spring";
            case "OBSERVABILITY" -> "Observability";
            case "SECURITY" -> "Vulnerability";
            case "CAPACITY" -> "Capacity";
            case "FLEET" -> "FleetManagement";
            case "INSIGHTS" -> "Insight";
            default -> null;
        };
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                            dp[i - 1][j - 1] + cost
                    );
                }
            }
        }
        
        return dp[s1.length()][s2.length()];
    }
}

