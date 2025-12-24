package org.tanzu.hubmcp.model;

import java.util.List;

/**
 * Represents a field definition within a GraphQL type.
 */
public record FieldDefinition(
        String name,
        String description,
        TypeReference type,
        List<InputValue> args,
        boolean deprecated,
        String deprecationReason
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private TypeReference type;
        private List<InputValue> args;
        private boolean deprecated;
        private String deprecationReason;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder type(TypeReference type) {
            this.type = type;
            return this;
        }

        public Builder args(List<InputValue> args) {
            this.args = args;
            return this;
        }

        public Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder deprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public FieldDefinition build() {
            return new FieldDefinition(name, description, type, args, deprecated, deprecationReason);
        }
    }
}

