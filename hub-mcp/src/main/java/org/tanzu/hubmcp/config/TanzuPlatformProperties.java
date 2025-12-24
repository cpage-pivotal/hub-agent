package org.tanzu.hubmcp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for Tanzu Platform connection.
 */
@ConfigurationProperties(prefix = "tanzu.platform")
@Validated
public record TanzuPlatformProperties(
        @NotBlank String url,
        @NotBlank String token,
        GraphQLProperties graphql,
        CacheProperties cache
) {
    public TanzuPlatformProperties {
        if (graphql == null) {
            graphql = new GraphQLProperties("/hub/graphql", Duration.ofSeconds(30), 3);
        }
        if (cache == null) {
            cache = new CacheProperties(new SchemaProperties(Duration.ofHours(24), 100));
        }
    }

    public record GraphQLProperties(
            String endpoint,
            Duration timeout,
            int maxRetries
    ) {
        public GraphQLProperties {
            if (endpoint == null) {
                endpoint = "/hub/graphql";
            }
            if (timeout == null) {
                timeout = Duration.ofSeconds(30);
            }
            if (maxRetries <= 0) {
                maxRetries = 3;
            }
        }
    }

    public record CacheProperties(SchemaProperties schema) {
        public CacheProperties {
            if (schema == null) {
                schema = new SchemaProperties(Duration.ofHours(24), 100);
            }
        }
    }

    public record SchemaProperties(Duration ttl, int maxSize) {
        public SchemaProperties {
            if (ttl == null) {
                ttl = Duration.ofHours(24);
            }
            if (maxSize <= 0) {
                maxSize = 100;
            }
        }
    }
}

