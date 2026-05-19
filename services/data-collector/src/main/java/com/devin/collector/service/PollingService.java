package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core polling service extracted from the monolith's DevinWebSocketHandler.
 *
 * <p>Runs a SINGLE global polling cycle independent of the number of connected
 * WebSocket clients (resolves P5/P6 from the architecture analysis).
 * Results are cached in Redis and published via Redis Pub/Sub.</p>
 *
 * <p>Supports differentiated polling intervals per endpoint category:</p>
 * <ul>
 *   <li>Sessions: every 5s (default)</li>
 *   <li>Metrics: every 30s (default)</li>
 *   <li>Billing: every 60s (default)</li>
 *   <li>Admin/other: every 300s (default)</li>
 * </ul>
 */
@Slf4j
@Service
public class PollingService {

    private final DevinApiClient devinApiClient;
    private final OrgApiClient orgApiClient;
    private final EndpointLoader endpointLoader;
    private final RedisSnapshotService snapshotService;
    private final OrgDiscoveryService orgDiscoveryService;
    private final SessionDiscoveryService sessionDiscoveryService;
    private final CollectorProperties properties;

    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4);

    private static final Set<String> METRICS_ENDPOINTS = Set.of(
            "get_dau_metrics",
            "get_wau_metrics",
            "get_mau_metrics",
            "get_active_users_metrics",
            "get_sessions_metrics",
            "get_searches_metrics",
            "get_prs_metrics",
            "get_usage_metrics"
    );

    private static final Set<String> SESSION_ENDPOINTS = Set.of(
            "list_sessions",
            "list_enterprise_sessions",
            "get_session",
            "get_session_messages",
            "list_session_tags",
            "get_session_insights",
            "get_enterprise_session",
            "get_enterprise_session_insights"
    );

    private static final Set<String> SESSION_DETAIL_ENDPOINTS = Set.of(
            "get_session",
            "get_session_messages",
            "list_session_tags",
            "get_session_insights",
            "get_enterprise_session",
            "get_enterprise_session_insights"
    );

    private static final Set<String> BILLING_ENDPOINTS = Set.of(
            "list_billing_cycles",
            "get_daily_consumption",
            "get_acu_limits"
    );

    private static final int METRICS_LOOKBACK_DAYS = 30;

    public PollingService(DevinApiClient devinApiClient,
                          OrgApiClient orgApiClient,
                          EndpointLoader endpointLoader,
                          RedisSnapshotService snapshotService,
                          OrgDiscoveryService orgDiscoveryService,
                          SessionDiscoveryService sessionDiscoveryService,
                          CollectorProperties properties) {
        this.devinApiClient = devinApiClient;
        this.orgApiClient = orgApiClient;
        this.endpointLoader = endpointLoader;
        this.snapshotService = snapshotService;
        this.orgDiscoveryService = orgDiscoveryService;
        this.sessionDiscoveryService = sessionDiscoveryService;
        this.properties = properties;
    }

    @PostConstruct
    void start() {
        List<EndpointDefinition> readEndpoints =
                endpointLoader.getReadEndpoints();

        Map<Long, List<EndpointDefinition>> grouped = groupByInterval(
                readEndpoints);

        for (Map.Entry<Long, List<EndpointDefinition>> entry
                : grouped.entrySet()) {
            long intervalSeconds = entry.getKey();
            List<EndpointDefinition> endpoints = entry.getValue();
            scheduler.scheduleAtFixedRate(
                    () -> pollEndpoints(endpoints),
                    0, intervalSeconds, TimeUnit.SECONDS);
            log.info("Scheduled {} endpoints with {}s interval: {}",
                    endpoints.size(), intervalSeconds,
                    endpoints.stream()
                            .map(EndpointDefinition::getName)
                            .toList());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        log.info("Polling service shut down.");
    }

    /**
     * Groups endpoints by their polling interval category.
     */
    Map<Long, List<EndpointDefinition>> groupByInterval(
            List<EndpointDefinition> endpoints) {
        Map<Long, List<EndpointDefinition>> grouped = new HashMap<>();
        for (EndpointDefinition ep : endpoints) {
            long interval = resolveInterval(ep);
            grouped.computeIfAbsent(interval,
                    k -> new java.util.ArrayList<>()).add(ep);
        }
        return grouped;
    }

    /**
     * Determines the polling interval for a given endpoint.
     */
    long resolveInterval(EndpointDefinition endpoint) {
        String name = endpoint.getName();
        if (SESSION_ENDPOINTS.contains(name)) {
            return properties.getSessionsPollingSeconds();
        }
        if (METRICS_ENDPOINTS.contains(name)) {
            return properties.getMetricsPollingSeconds();
        }
        if (BILLING_ENDPOINTS.contains(name)) {
            return properties.getBillingPollingSeconds();
        }
        return properties.getAdminPollingSeconds();
    }

    /**
     * Polls a batch of endpoints and publishes results to Redis.
     */
    void pollEndpoints(List<EndpointDefinition> endpoints) {
        List<String> orgIds = orgDiscoveryService.getCachedOrgIds();
        boolean pollOrgEndpoints = !orgIds.isEmpty()
                && (orgApiClient.isAvailable() || devinApiClient != null);

        // Refresh session cache before polling session-detail endpoints
        boolean hasDetailEndpoints = endpoints.stream()
                .anyMatch(ep -> SESSION_DETAIL_ENDPOINTS.contains(ep.getName()));
        if (hasDetailEndpoints) {
            try {
                sessionDiscoveryService.refreshFromCache();
            } catch (Exception e) {
                log.warn("Failed to refresh session cache: {}", e.getMessage());
            }
        }

        for (EndpointDefinition endpoint : endpoints) {
            try {
                String scope = endpoint.getScope();

                if ("organization".equalsIgnoreCase(scope)) {
                    if (!pollOrgEndpoints) {
                        continue;
                    }
                    for (String currentOrgId : orgIds) {
                        pollOrgEndpoint(endpoint, currentOrgId);
                    }
                } else {
                    pollEnterpriseEndpoint(endpoint);
                }
            } catch (Exception e) {
                log.error("Failed to poll endpoint {}: {}",
                        endpoint.getName(), e.getMessage());
            }
        }
    }

    private void pollEnterpriseEndpoint(EndpointDefinition endpoint) {
        Map<String, String> queryParams = METRICS_ENDPOINTS.contains(endpoint.getName())
                ? buildMetricsTimeParams() : Collections.emptyMap();

        // Enterprise endpoints that contain {org_id} in their path need
        // per-org iteration, just like pollOrgEndpoint does.
        if (endpoint.getPath().contains("{org_id}")) {
            List<String> orgIds = orgDiscoveryService.getCachedOrgIds();
            if (orgIds.isEmpty()) {
                log.warn("Enterprise endpoint {} requires org_id but no orgs discovered yet",
                        endpoint.getName());
                return;
            }
            boolean multiOrg = orgDiscoveryService.isMultiOrg();
            for (String orgId : orgIds) {
                Map<String, String> pathParams = Map.of("org_id", orgId);
                String cacheKey = multiOrg
                        ? endpoint.getName() + "__org_" + orgId
                        : endpoint.getName();
                pollWithParams(endpoint, pathParams, queryParams, cacheKey, orgId, false);
            }
            return;
        }

        // Enterprise endpoints that contain {session_id} need per-session iteration.
        if (endpoint.getPath().contains("{session_id}")) {
            List<String> sessionIds = sessionDiscoveryService.getEnterpriseSessionIds();
            if (sessionIds.isEmpty()) {
                log.debug("No cached enterprise session IDs - skipping {}",
                        endpoint.getName());
                return;
            }
            for (String sessionId : sessionIds) {
                Map<String, String> pathParams = Map.of("session_id", sessionId);
                String cacheKey = endpoint.getName() + "__session_" + sessionId;
                pollWithParams(endpoint, pathParams, queryParams, cacheKey, null, false);
            }
            return;
        }

        // Enterprise endpoints without path variables
        pollWithParams(endpoint, Collections.emptyMap(), queryParams,
                endpoint.getName(), null, false);
    }

    private void pollOrgEndpoint(EndpointDefinition endpoint,
                                 String currentOrgId) {
        Map<String, String> queryParams = METRICS_ENDPOINTS.contains(endpoint.getName())
                ? buildMetricsTimeParams() : Collections.emptyMap();
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("org_id", currentOrgId);

        // Org endpoints that contain {session_id} need per-session iteration.
        if (endpoint.getPath().contains("{session_id}")) {
            List<String> sessionIds = sessionDiscoveryService.getOrgSessionIds(currentOrgId);
            if (sessionIds.isEmpty()) {
                log.debug("No cached session IDs for org {} - skipping {}",
                        currentOrgId, endpoint.getName());
                return;
            }
            boolean multiOrg = orgDiscoveryService.isMultiOrg();
            for (String sessionId : sessionIds) {
                Map<String, String> sessionPathParams = new HashMap<>(pathParams);
                sessionPathParams.put("session_id", sessionId);
                String cacheKey = multiOrg
                        ? endpoint.getName() + "__org_" + currentOrgId + "__session_" + sessionId
                        : endpoint.getName() + "__session_" + sessionId;
                pollWithParams(endpoint, sessionPathParams, queryParams, cacheKey, currentOrgId, true);
            }
            return;
        }

        boolean multiOrg = orgDiscoveryService.isMultiOrg();
        String cacheKey = multiOrg
                ? endpoint.getName() + "__org_" + currentOrgId
                : endpoint.getName();
        pollWithParams(endpoint, pathParams, queryParams, cacheKey, currentOrgId, true);
    }

    /**
     * Unified helper to poll an endpoint with specific path/query params and cache the result.
     *
     * @param useOrgClient if true and orgApiClient is available, use orgApiClient;
     *                     otherwise always use devinApiClient. This decouples
     *                     client selection from the orgId value (enterprise endpoints
     *                     need orgId in the payload but must use devinApiClient).
     */
    private void pollWithParams(EndpointDefinition endpoint,
                                Map<String, String> pathParams,
                                Map<String, String> queryParams,
                                String cacheKey,
                                String orgId,
                                boolean useOrgClient) {
        Flux<String> responseFlux;
        if (useOrgClient && orgApiClient.isAvailable()) {
            responseFlux = orgApiClient.get(endpoint, pathParams, queryParams);
        } else {
            responseFlux = devinApiClient.get(endpoint, pathParams, queryParams);
        }

        responseFlux
                .collectList()
                .subscribe(
                        dataChunks -> {
                            String rawData = String.join("", dataChunks);
                            if (rawData.isEmpty()) {
                                log.debug("Empty response for endpoint {} - skipping cache/publish",
                                        endpoint.getName());
                                return;
                            }
                            snapshotService.cacheEndpointData(cacheKey, rawData);
                            snapshotService.publishUpdate(
                                    endpoint.getName(), rawData, orgId);
                        },
                        error -> {
                            if (error instanceof WebClientResponseException.NotFound) {
                                log.debug("Endpoint {} returned 404 - skipping",
                                        endpoint.getName());
                            } else {
                                log.warn("Poll error for endpoint {} (cache key {}): {}",
                                        endpoint.getName(), cacheKey,
                                        error.getMessage());
                            }
                        }
                );
    }

    private Map<String, String> buildMetricsTimeParams() {
        Instant now = Instant.now();
        Instant lookback = now.minus(METRICS_LOOKBACK_DAYS, ChronoUnit.DAYS);
        Map<String, String> params = new HashMap<>();
        params.put("time_before",
                String.valueOf(now.getEpochSecond()));
        params.put("time_after",
                String.valueOf(lookback.getEpochSecond()));
        return params;
    }
}
