package org.tanzu.hubmcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.tanzu.hubmcp.config.TanzuPlatformProperties;
import org.tanzu.hubmcp.exception.GraphQLException;
import org.tanzu.hubmcp.model.GraphQLRequest;
import org.tanzu.hubmcp.model.GraphQLResponse;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;

/**
 * Service for executing GraphQL queries and mutations against the Tanzu Platform API.
 */
@Service
public class TanzuGraphQLService {

    private static final Logger log = LoggerFactory.getLogger(TanzuGraphQLService.class);

    private final WebClient webClient;
    private final TanzuPlatformProperties properties;

    public TanzuGraphQLService(WebClient tanzuGraphQLClient, TanzuPlatformProperties properties) {
        this.webClient = tanzuGraphQLClient;
        this.properties = properties;
    }

    /**
     * Execute a read-only GraphQL query.
     *
     * @param request the GraphQL request containing query, variables, and operation name
     * @return the GraphQL response
     * @throws GraphQLException if the query fails or returns errors
     */
    public GraphQLResponse executeQuery(GraphQLRequest request) {
        log.debug("Executing GraphQL query: {}", truncateQuery(request.query()));
        return executeGraphQLRequest(request);
    }

    /**
     * Execute a GraphQL mutation.
     *
     * @param request the GraphQL request containing mutation, variables, and operation name
     * @return the GraphQL response
     * @throws GraphQLException if the mutation fails or returns errors
     */
    public GraphQLResponse executeMutation(GraphQLRequest request) {
        log.info("Executing GraphQL mutation: {}", truncateQuery(request.query()));
        validateMutation(request.query());
        return executeGraphQLRequest(request);
    }

    /**
     * Execute a schema introspection query.
     *
     * @return the raw introspection data
     */
    public Map<String, Object> introspectSchema() {
        log.info("Performing schema introspection");
        
        String introspectionQuery = """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                types {
                  ...FullType
                }
                directives {
                  name
                  description
                  locations
                  args {
                    ...InputValue
                  }
                }
              }
            }
            
            fragment FullType on __Type {
              kind
              name
              description
              fields(includeDeprecated: true) {
                name
                description
                args {
                  ...InputValue
                }
                type {
                  ...TypeRef
                }
                isDeprecated
                deprecationReason
              }
              inputFields {
                ...InputValue
              }
              interfaces {
                ...TypeRef
              }
              enumValues(includeDeprecated: true) {
                name
                description
                isDeprecated
                deprecationReason
              }
              possibleTypes {
                ...TypeRef
              }
            }
            
            fragment InputValue on __InputValue {
              name
              description
              type { ...TypeRef }
              defaultValue
            }
            
            fragment TypeRef on __Type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                        }
                      }
                    }
                  }
                }
              }
            }
            """;

        GraphQLRequest request = GraphQLRequest.builder()
                .query(introspectionQuery)
                .build();

        GraphQLResponse response = executeGraphQLRequest(request);
        return response.data();
    }

    private GraphQLResponse executeGraphQLRequest(GraphQLRequest request) {
        try {
            Duration timeout = properties.graphql().timeout();
            int maxRetries = properties.graphql().maxRetries();
            String endpoint = properties.graphql().endpoint();

            GraphQLResponse response = webClient.post()
                    .uri(endpoint)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(GraphQLResponse.class)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofSeconds(1))
                            .filter(this::isRetryableError)
                            .doBeforeRetry(signal ->
                                    log.warn("Retrying GraphQL request, attempt {}", signal.totalRetries() + 1)
                            ))
                    .block();

            if (response == null) {
                throw new GraphQLException("Received null response from GraphQL API");
            }

            if (response.hasErrors()) {
                log.warn("GraphQL response contains errors: {}", response.errors());
                throw new GraphQLException("GraphQL query returned errors", response.errors());
            }

            log.debug("GraphQL query completed successfully, complexity: {}", response.getQueryComplexity());
            return response;

        } catch (WebClientResponseException e) {
            log.error("GraphQL API returned error status: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new GraphQLException("GraphQL API error: " + e.getStatusCode(), 
                    Map.of("status", e.getStatusCode().value(), "body", e.getResponseBodyAsString()));
        } catch (GraphQLException e) {
            throw e;
        } catch (Exception e) {
            log.error("GraphQL request failed: {}", e.getMessage(), e);
            throw new GraphQLException("GraphQL request failed: " + e.getMessage(), e);
        }
    }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException e) {
            int status = e.getStatusCode().value();
            // Retry on 5xx errors and 429 (rate limit)
            return status >= 500 || status == 429;
        }
        // Retry on connection errors
        return throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException;
    }

    private void validateMutation(String mutation) {
        if (mutation == null || mutation.isBlank()) {
            throw new GraphQLException("Mutation cannot be null or empty");
        }
        String trimmed = mutation.trim().toLowerCase();
        if (!trimmed.startsWith("mutation")) {
            throw new GraphQLException("Mutation must start with 'mutation' keyword");
        }
    }

    private String truncateQuery(String query) {
        if (query == null) return "null";
        if (query.length() <= 100) return query;
        return query.substring(0, 100) + "...";
    }
}

