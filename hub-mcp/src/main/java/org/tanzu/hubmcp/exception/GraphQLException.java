package org.tanzu.hubmcp.exception;

import org.tanzu.hubmcp.model.GraphQLError;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when GraphQL operations fail.
 */
public class GraphQLException extends RuntimeException {

    private final List<GraphQLError> errors;
    private final Map<String, Object> details;

    public GraphQLException(String message) {
        super(message);
        this.errors = Collections.emptyList();
        this.details = Collections.emptyMap();
    }

    public GraphQLException(String message, Throwable cause) {
        super(message, cause);
        this.errors = Collections.emptyList();
        this.details = Collections.emptyMap();
    }

    public GraphQLException(String message, List<GraphQLError> errors) {
        super(message);
        this.errors = errors != null ? errors : Collections.emptyList();
        this.details = Collections.emptyMap();
    }

    public GraphQLException(String message, Map<String, Object> details) {
        super(message);
        this.errors = Collections.emptyList();
        this.details = details != null ? details : Collections.emptyMap();
    }

    public GraphQLException(String message, List<GraphQLError> errors, Map<String, Object> details) {
        super(message);
        this.errors = errors != null ? errors : Collections.emptyList();
        this.details = details != null ? details : Collections.emptyMap();
    }

    public List<GraphQLError> getErrors() {
        return errors;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}

