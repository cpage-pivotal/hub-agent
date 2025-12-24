package org.tanzu.hubmcp.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Global exception handler for REST endpoints.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GraphQLException.class)
    public ResponseEntity<ErrorResponse> handleGraphQLException(GraphQLException ex) {
        log.error("GraphQL error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "GRAPHQL_ERROR",
                        ex.getMessage(),
                        ex.getDetails()
                ));
    }

    @ExceptionHandler(SchemaValidationException.class)
    public ResponseEntity<ErrorResponse> handleSchemaValidationException(SchemaValidationException ex) {
        log.error("Schema validation error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "SCHEMA_VALIDATION_ERROR",
                        ex.getMessage(),
                        Map.of(
                                "validationErrors", ex.getValidationErrors(),
                                "suggestions", ex.getSuggestions()
                        )
                ));
    }

    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<ErrorResponse> handleWebClientException(WebClientException ex) {
        log.error("Tanzu Platform API connection error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(
                        "TANZU_API_UNAVAILABLE",
                        "Unable to connect to Tanzu Platform API",
                        Map.of("cause", ex.getMessage())
                ));
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(TimeoutException ex) {
        log.error("Query timeout: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorResponse(
                        "QUERY_TIMEOUT",
                        "GraphQL query exceeded timeout limit",
                        Map.of("timeout", "30s")
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR",
                        "An unexpected error occurred",
                        Map.of("type", ex.getClass().getSimpleName())
                ));
    }

    /**
     * Standard error response structure.
     */
    public record ErrorResponse(
            String code,
            String message,
            Map<String, Object> details
    ) {}
}

