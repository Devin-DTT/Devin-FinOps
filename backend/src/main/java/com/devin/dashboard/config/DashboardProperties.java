package com.devin.dashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Centralised configuration properties for the Devin FinOps Dashboard.
 *
 * <p>All magic numbers that were previously hard-coded across the codebase
 * are now exposed as configurable properties under the {@code dashboard.*} prefix.</p>
 *
 * <p>Override via {@code application.properties}, environment variables, or
 * command-line arguments. For example:</p>
 * <pre>
 *   dashboard.polling-interval-seconds=10
 *   DASHBOARD_POLLING_INTERVAL_SECONDS=10
 * </pre>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {

    /** WebSocket polling interval in seconds (default: 5). */
    private long pollingIntervalSeconds = 5;

    /** Organization discovery refresh interval in seconds (default: 60). */
    private long orgDiscoveryRefreshSeconds = 60;

    /** Organization discovery HTTP timeout in seconds (default: 10). */
    private long orgDiscoveryTimeoutSeconds = 10;

    /** Number of scheduler threads for the WebSocket polling pool (default: 2). */
    private int schedulerPoolSize = 2;
}
