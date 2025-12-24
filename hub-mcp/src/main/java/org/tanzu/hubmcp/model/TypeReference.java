package org.tanzu.hubmcp.model;

/**
 * Represents a reference to a GraphQL type, supporting nested type wrappers
 * (e.g., NON_NULL, LIST).
 */
public record TypeReference(
        String kind,
        String name,
        TypeReference ofType
) {
    /**
     * Gets the underlying type name, unwrapping NON_NULL and LIST wrappers.
     */
    public String getUnwrappedTypeName() {
        if (name != null) {
            return name;
        }
        if (ofType != null) {
            return ofType.getUnwrappedTypeName();
        }
        return null;
    }

    /**
     * Checks if this type is non-nullable.
     */
    public boolean isNonNull() {
        return "NON_NULL".equals(kind);
    }

    /**
     * Checks if this type is a list.
     */
    public boolean isList() {
        return "LIST".equals(kind) || (ofType != null && ofType.isList());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String kind;
        private String name;
        private TypeReference ofType;

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder ofType(TypeReference ofType) {
            this.ofType = ofType;
            return this;
        }

        public TypeReference build() {
            return new TypeReference(kind, name, ofType);
        }
    }
}

