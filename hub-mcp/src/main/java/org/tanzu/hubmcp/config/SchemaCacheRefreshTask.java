package org.tanzu.hubmcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.tanzu.hubmcp.service.SchemaIntrospectionService;

/**
 * Scheduled task to refresh the GraphQL schema cache periodically.
 * 
 * <p>By default, this refreshes the schema cache at 2 AM daily to ensure
 * the cached schema stays in sync with any API changes while minimizing
 * impact on active users.</p>
 * 
 * <p>The refresh schedule can be configured via the property:
 * {@code tanzu.cache.schema.refresh-cron}</p>
 */
@Component
@EnableScheduling
public class SchemaCacheRefreshTask {

    private static final Logger log = LoggerFactory.getLogger(SchemaCacheRefreshTask.class);

    private final SchemaIntrospectionService schemaService;

    public SchemaCacheRefreshTask(SchemaIntrospectionService schemaService) {
        this.schemaService = schemaService;
    }

    /**
     * Refresh the schema cache on a scheduled basis.
     * 
     * <p>Default schedule: 2 AM daily (0 0 2 * * ?)</p>
     * 
     * <p>This operation:
     * <ul>
     *   <li>Clears all schema-related caches</li>
     *   <li>Reloads the schema from the Tanzu Platform API</li>
     *   <li>Rebuilds the entity relationship graph</li>
     * </ul>
     */
    @Scheduled(cron = "${tanzu.cache.schema.refresh-cron:0 0 2 * * ?}")
    public void refreshSchemaCache() {
        log.info("Starting scheduled schema cache refresh");
        long startTime = System.currentTimeMillis();
        
        try {
            schemaService.refreshSchema();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Schema cache refreshed in {}ms", duration);
        } catch (Exception e) {
            log.error("Schema cache refresh failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Warm up the cache on application startup.
     * 
     * <p>This ensures the schema is loaded and available for the first
     * request, avoiding cold-start latency for users.</p>
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void warmupCacheOnStartup() {
        log.info("Warming up schema cache on startup");
        long startTime = System.currentTimeMillis();

        try {
            schemaService.getSchema();
            long duration = System.currentTimeMillis() - startTime;
            log.info("Schema cache warmed up in {}ms ({} types)", duration, schemaService.getSchema().getTypeCount());
        } catch (Exception e) {
            log.warn("Schema warmup failed — will retry on first tool call: {}", e.getMessage());
        }
    }
}

