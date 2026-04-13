package com.devin.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the data-collector service.
 * Supports differentiated polling intervals per endpoint category.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "collector")
public class CollectorProperties {

    /** Polling interval for session endpoints in seconds (default: 5). */
    private long sessionsPollingSeconds = 5;

    /** Polling interval for metrics endpoints in seconds (default: 30). */
    private long metricsPollingSeconds = 30;

    /** Polling interval for billing endpoints in seconds (default: 60). */
    private long billingPollingSeconds = 60;

    /** Polling interval for admin/other endpoints in seconds (default: 300). */
    private long adminPollingSeconds = 300;

    /** Organization discovery refresh interval in seconds (default: 60). */
    private long orgDiscoveryRefreshSeconds = 60;

    /** Organization discovery HTTP timeout in seconds (default: 10). */
    private long orgDiscoveryTimeoutSeconds = 10;

    /** Redis key TTL in seconds (default: 600 = 10 minutes). */
    private long redisKeyTtlSeconds = 600;

    /** Redis Pub/Sub channel name. */
    private String redisPubsubChannel = "finops:updates";

    /** Redis key prefix for cached endpoint data. */
    private String redisKeyPrefix = "finops:endpoint:";

    /** Maximum number of sessions to poll for detail endpoints (default: 20). */
    private int maxSessionDetailPolling = 20;

    /** Whether to automatically dump raw endpoint data to a file (default: true). */
    private boolean dumpEnabled = true;

    /** Path to write the raw endpoint data dump file. */
    private String dumpFilePath = "/app/dump/raw-endpoint-data.json";

    /** Interval in seconds between automatic dump writes (default: 30). */
    private long dumpIntervalSeconds = 30;
}
