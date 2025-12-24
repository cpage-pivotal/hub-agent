package org.tanzu.hubmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Represents a GraphQL response from the Tanzu Platform API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphQLResponse(
        Map<String, Object> data,
        List<GraphQLError> errors,
        Map<String, Object> extensions
) {
    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public int getQueryComplexity() {
        if (extensions != null && extensions.containsKey("queryComplexity")) {
            Object complexity = extensions.get("queryComplexity");
            if (complexity instanceof Number) {
                return ((Number) complexity).intValue();
            }
        }
        return 0;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<String, Object> data;
        private List<GraphQLError> errors;
        private Map<String, Object> extensions;

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder errors(List<GraphQLError> errors) {
            this.errors = errors;
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = extensions;
            return this;
        }

        public GraphQLResponse build() {
            return new GraphQLResponse(data, errors, extensions);
        }
    }
}

