package org.tanzu.hubmcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Represents a GraphQL error in the response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GraphQLError(
        String message,
        List<Location> locations,
        List<String> path,
        Map<String, Object> extensions
) {
    /**
     * Represents a location in the GraphQL query where an error occurred.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Location(int line, int column) {}
}

