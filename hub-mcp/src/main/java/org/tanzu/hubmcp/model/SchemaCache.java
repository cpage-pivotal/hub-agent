package org.tanzu.hubmcp.model;

import java.time.Instant;
import java.util.*;

/**
 * Cached representation of the GraphQL schema.
 */
public class SchemaCache {

    private Instant timestamp;
    private final Map<String, TypeDefinition> types = new HashMap<>();
    private Map<String, List<EntityRelationship>> relationships = new HashMap<>();

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void addType(TypeDefinition type) {
        types.put(type.name(), type);
    }

    public Optional<TypeDefinition> getType(String name) {
        return Optional.ofNullable(types.get(name));
    }

    public List<TypeDefinition> getTypes() {
        return new ArrayList<>(types.values());
    }

    public List<TypeDefinition> getTypesByKind(String kind) {
        return types.values().stream()
                .filter(t -> kind.equals(t.kind()))
                .toList();
    }

    public List<TypeDefinition> getTypesByPrefix(String prefix) {
        return types.values().stream()
                .filter(t -> t.name() != null && t.name().startsWith(prefix))
                .toList();
    }

    public Map<String, List<EntityRelationship>> getRelationships() {
        return relationships;
    }

    public void setRelationships(Map<String, List<EntityRelationship>> relationships) {
        this.relationships = relationships;
    }

    public int getTypeCount() {
        return types.size();
    }
}

