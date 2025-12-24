package org.tanzu.hubmcp.model;

/**
 * Represents a relationship between two entity types in the Tanzu Platform schema.
 */
public record EntityRelationship(
        String sourceEntity,
        String targetEntity,
        String relationshipType,  // IsContainedIn, Contains, IsAssociatedWith, etc.
        String direction,         // IN or OUT
        String fieldName
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String sourceEntity;
        private String targetEntity;
        private String relationshipType;
        private String direction;
        private String fieldName;

        public Builder sourceEntity(String sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        public Builder targetEntity(String targetEntity) {
            this.targetEntity = targetEntity;
            return this;
        }

        public Builder relationshipType(String relationshipType) {
            this.relationshipType = relationshipType;
            return this;
        }

        public Builder direction(String direction) {
            this.direction = direction;
            return this;
        }

        public Builder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public EntityRelationship build() {
            return new EntityRelationship(sourceEntity, targetEntity, relationshipType, direction, fieldName);
        }
    }
}

