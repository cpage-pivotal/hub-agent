package org.tanzu.hubmcp.exception;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when GraphQL query validation against the schema fails.
 */
public class SchemaValidationException extends RuntimeException {

    private final List<ValidationError> validationErrors;
    private final List<String> suggestions;

    public SchemaValidationException(String message) {
        super(message);
        this.validationErrors = Collections.emptyList();
        this.suggestions = Collections.emptyList();
    }

    public SchemaValidationException(String message, List<ValidationError> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
        this.suggestions = Collections.emptyList();
    }

    public SchemaValidationException(String message, List<ValidationError> validationErrors, List<String> suggestions) {
        super(message);
        this.validationErrors = validationErrors != null ? validationErrors : Collections.emptyList();
        this.suggestions = suggestions != null ? suggestions : Collections.emptyList();
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    /**
     * Represents a single validation error.
     */
    public record ValidationError(
            ErrorType type,
            String message,
            String fieldName,
            String parentType,
            String typeName,
            Location location
    ) {
        public record Location(int line, int column) {}

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private ErrorType type;
            private String message;
            private String fieldName;
            private String parentType;
            private String typeName;
            private Location location;

            public Builder type(ErrorType type) {
                this.type = type;
                return this;
            }

            public Builder message(String message) {
                this.message = message;
                return this;
            }

            public Builder fieldName(String fieldName) {
                this.fieldName = fieldName;
                return this;
            }

            public Builder parentType(String parentType) {
                this.parentType = parentType;
                return this;
            }

            public Builder typeName(String typeName) {
                this.typeName = typeName;
                return this;
            }

            public Builder location(Location location) {
                this.location = location;
                return this;
            }

            public ValidationError build() {
                return new ValidationError(type, message, fieldName, parentType, typeName, location);
            }
        }
    }

    /**
     * Types of validation errors.
     */
    public enum ErrorType {
        SYNTAX_ERROR,
        UNKNOWN_FIELD,
        UNKNOWN_TYPE,
        INVALID_ARGUMENT,
        MISSING_REQUIRED_ARGUMENT,
        TYPE_MISMATCH
    }
}

