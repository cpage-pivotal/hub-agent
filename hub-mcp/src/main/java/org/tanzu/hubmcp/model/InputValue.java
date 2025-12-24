package org.tanzu.hubmcp.model;

/**
 * Represents an input value (argument) in a GraphQL field or directive.
 */
public record InputValue(
        String name,
        String description,
        TypeReference type,
        String defaultValue
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private TypeReference type;
        private String defaultValue;

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

        public Builder defaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public InputValue build() {
            return new InputValue(name, description, type, defaultValue);
        }
    }
}

