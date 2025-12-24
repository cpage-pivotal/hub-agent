package org.tanzu.hubmcp.model;

import java.util.List;

/**
 * Represents a GraphQL type definition from schema introspection.
 */
public record TypeDefinition(
        String name,
        String kind,  // OBJECT, INPUT_OBJECT, ENUM, INTERFACE, SCALAR
        String description,
        List<FieldDefinition> fields,
        List<String> interfaces,
        List<EnumValue> enumValues
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String kind;
        private String description;
        private List<FieldDefinition> fields;
        private List<String> interfaces;
        private List<EnumValue> enumValues;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder fields(List<FieldDefinition> fields) {
            this.fields = fields;
            return this;
        }

        public Builder interfaces(List<String> interfaces) {
            this.interfaces = interfaces;
            return this;
        }

        public Builder enumValues(List<EnumValue> enumValues) {
            this.enumValues = enumValues;
            return this;
        }

        public TypeDefinition build() {
            return new TypeDefinition(name, kind, description, fields, interfaces, enumValues);
        }
    }
}

