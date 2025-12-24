package org.tanzu.hubmcp.model;

import java.util.Map;

/**
 * Represents a GraphQL request to the Tanzu Platform API.
 */
public record GraphQLRequest(
        String query,
        Map<String, Object> variables,
        String operationName
) {
    public GraphQLRequest(String query) {
        this(query, null, null);
    }

    public GraphQLRequest(String query, Map<String, Object> variables) {
        this(query, variables, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String query;
        private Map<String, Object> variables;
        private String operationName;

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder operationName(String operationName) {
            this.operationName = operationName;
            return this;
        }

        public GraphQLRequest build() {
            return new GraphQLRequest(query, variables, operationName);
        }
    }
}

