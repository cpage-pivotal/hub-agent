package org.tanzu.hubmcp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration for schema and entity relationship caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String GRAPHQL_SCHEMA_CACHE = "graphql-schema";
    public static final String ENTITY_RELATIONSHIPS_CACHE = "entity-relationships";
    public static final String TYPE_DEFINITIONS_CACHE = "type-definitions";

    private final TanzuPlatformProperties properties;

    public CacheConfig(TanzuPlatformProperties properties) {
        this.properties = properties;
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                GRAPHQL_SCHEMA_CACHE,
                ENTITY_RELATIONSHIPS_CACHE,
                TYPE_DEFINITIONS_CACHE
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(properties.cache().schema().ttl())
                .maximumSize(properties.cache().schema().maxSize())
                .recordStats()
        );

        return cacheManager;
    }
}

