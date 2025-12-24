package org.tanzu.hubmcp.model;

/**
 * Represents an enum value in a GraphQL enum type.
 */
public record EnumValue(
        String name,
        String description,
        boolean deprecated,
        String deprecationReason
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
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

        public Builder deprecated(boolean deprecated) {
            this.deprecated = deprecated;
            return this;
        }

        public Builder deprecationReason(String deprecationReason) {
            this.deprecationReason = deprecationReason;
            return this;
        }

        public EnumValue build() {
            return new EnumValue(name, description, deprecated, deprecationReason);
        }
    }
}

