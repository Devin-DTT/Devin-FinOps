package com.devin.dashboard.service;

import com.devin.dashboard.config.DashboardProperties;
import com.devin.dashboard.config.EndpointLoader;
import com.devin.dashboard.model.EndpointDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Discovers and caches the list of organization IDs available in the enterprise.
 *
 * <p>Supports two modes:</p>
 * <ul>
 *   <li><b>Single-org</b>: when {@code DEVIN_ORG_ID} is configured, uses that value.</li>
 *   <li><b>Multi-org</b>: periodically calls the enterprise {@code list_organizations}
 *       endpoint to discover all organizations and caches their IDs.</li>
 * </ul>
 *
 * <p>Extracted from {@code DevinWebSocketHandler} to separate org discovery
 * concerns from WebSocket transport.</p>
 */
@Slf4j
@Service
public class OrgDiscoveryService {

    private final DevinApiClient devinApiClient;
    private final OrgApiClient orgApiClient;
    private final EndpointLoader endpointLoader;
    private final DashboardProperties properties;
    private final ScheduledExecutorService discoveryExecutor;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Cached list of discovered organization IDs (refreshed periodically). */
    private volatile List<String> cachedOrgIds = Collections.emptyList();

    /** Whether the initial org discovery has completed at least once. */
    private volatile boolean initialized = false;

    public OrgDiscoveryService(DevinApiClient devinApiClient,
                                OrgApiClient orgApiClient,
                                EndpointLoader endpointLoader,
                                DashboardProperties properties) {
        this.devinApiClient = devinApiClient;
        this.orgApiClient = orgApiClient;
        this.endpointLoader = endpointLoader;
        this.properties = properties;
        this.discoveryExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    void start() {
        if (!orgApiClient.isAvailable()) {
            // No org token configured -- skip discovery entirely
            this.initialized = true;
            log.info("Org discovery disabled: DEVIN_ORG_SERVICE_TOKEN not configured.");
            return;
        }

        if (orgApiClient.getOrgId().isPresent()) {
            // Single-org: use configured value, mark as initialized
            this.cachedOrgIds = List.of(orgApiClient.getOrgId().get());
            this.initialized = true;
            log.info("Single-org mode: using configured org ID {}", orgApiClient.getOrgId().get());
        } else {
            // Multi-org: start periodic discovery
            long refreshSeconds = properties.getOrgDiscoveryRefreshSeconds();
            discoveryExecutor.scheduleAtFixedRate(
                    this::refreshOrgIds, 0, refreshSeconds, TimeUnit.SECONDS);
            log.info("Multi-org mode: discovery will refresh every {}s", refreshSeconds);
        }
    }

    @PreDestroy
    void stop() {
        discoveryExecutor.shutdownNow();
    }

    /**
     * Returns the currently cached list of organization IDs.
     */
    public List<String> getCachedOrgIds() {
        return cachedOrgIds;
    }

    /**
     * Whether the initial discovery pass has completed at least once.
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Whether multi-org mode is active (no fixed org ID configured).
     */
    public boolean isMultiOrg() {
        return orgApiClient.getOrgId().isEmpty();
    }

    /**
     * Asynchronously refreshes the cached list of organization IDs by calling
     * the enterprise {@code list_organizations} endpoint.
     */
    private void refreshOrgIds() {
        Optional<EndpointDefinition> listOrgsEndpoint = endpointLoader.findByName("list_organizations");
        if (listOrgsEndpoint.isEmpty()) {
            log.warn("list_organizations endpoint not found in endpoints.yaml. "
                    + "Cannot auto-discover organizations.");
            initialized = true;
            return;
        }

        try {
            long timeoutSeconds = properties.getOrgDiscoveryTimeoutSeconds();
            String responseBody = devinApiClient.get(listOrgsEndpoint.get(), Collections.emptyMap())
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("list_organizations returned empty response. Keeping previous cached org IDs.");
                initialized = true;
                return;
            }

            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            List<String> orgIds = new ArrayList<>();

            // Try common JSON structures: items[], organizations[], or root array
            JsonNode itemsNode = root.has("items") ? root.get("items")
                    : root.has("organizations") ? root.get("organizations")
                    : root.isArray() ? root : null;

            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode orgNode : itemsNode) {
                    // Try "id" first, then "org_id"
                    JsonNode idNode = orgNode.has("id") ? orgNode.get("id")
                            : orgNode.has("org_id") ? orgNode.get("org_id") : null;
                    if (idNode != null && !idNode.asText().isBlank()) {
                        orgIds.add(idNode.asText());
                    }
                }
            }

            if (orgIds.isEmpty()) {
                log.warn("list_organizations returned no org IDs. Response: {}",
                        responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
            } else {
                log.info("Auto-discovered {} organization(s): {}", orgIds.size(), orgIds);
                this.cachedOrgIds = List.copyOf(orgIds);
            }

        } catch (Exception e) {
            log.error("Failed to call list_organizations for multi-org discovery: {}", e.getMessage());
        } finally {
            initialized = true;
        }
    }
}
