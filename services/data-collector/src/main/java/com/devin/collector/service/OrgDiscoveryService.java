package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import com.devin.common.util.JsonResponseParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Discovers and caches the list of organization IDs available in the enterprise.
 */
@Slf4j
@Service
public class OrgDiscoveryService {

    private final DevinApiClient devinApiClient;
    private final OrgApiClient orgApiClient;
    private final EndpointLoader endpointLoader;
    private final CollectorProperties properties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService discoveryExecutor;

    private volatile List<String> cachedOrgIds = Collections.emptyList();
    private volatile boolean initialized = false;

    public OrgDiscoveryService(DevinApiClient devinApiClient,
                               OrgApiClient orgApiClient,
                               EndpointLoader endpointLoader,
                               CollectorProperties properties,
                               ObjectMapper objectMapper) {
        this.devinApiClient = devinApiClient;
        this.orgApiClient = orgApiClient;
        this.endpointLoader = endpointLoader;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.discoveryExecutor =
                Executors.newSingleThreadScheduledExecutor();
    }

    @PostConstruct
    void start() {
        if (orgApiClient.getOrgId().isPresent()) {
            this.cachedOrgIds =
                    List.of(orgApiClient.getOrgId().get());
            this.initialized = true;
            log.info("Single-org mode: using configured org ID {}",
                    orgApiClient.getOrgId().get());
        } else {
            long refreshSeconds = properties.getOrgDiscoveryRefreshSeconds();
            discoveryExecutor.scheduleAtFixedRate(
                    this::refreshOrgIds, 0, refreshSeconds, TimeUnit.SECONDS);
            log.info("Multi-org mode: discovery every {}s (org token: {})",
                    refreshSeconds, orgApiClient.isAvailable());
        }
    }

    @PreDestroy
    void stop() {
        discoveryExecutor.shutdownNow();
    }

    public List<String> getCachedOrgIds() {
        return cachedOrgIds;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isMultiOrg() {
        return orgApiClient.getOrgId().isEmpty();
    }

    private void refreshOrgIds() {
        Optional<EndpointDefinition> listOrgsEndpoint =
                endpointLoader.findByName("list_organizations");
        if (listOrgsEndpoint.isEmpty()) {
            log.warn("list_organizations endpoint not found in endpoints.yaml");
            initialized = true;
            return;
        }

        try {
            long timeoutSeconds = properties.getOrgDiscoveryTimeoutSeconds();
            String responseBody = devinApiClient
                    .get(listOrgsEndpoint.get(), Collections.emptyMap())
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("list_organizations returned empty response.");
                initialized = true;
                return;
            }

            List<String> orgIds = JsonResponseParser.extractIds(
                    responseBody, objectMapper,
                    List.of("organizations"),
                    "id", "org_id");

            if (orgIds.isEmpty()) {
                log.warn("list_organizations returned no org IDs.");
            } else {
                log.info("Discovered {} organization(s): {}",
                        orgIds.size(), orgIds);
                this.cachedOrgIds = List.copyOf(orgIds);
            }

        } catch (Exception e) {
            log.error("Failed to call list_organizations: {}",
                    e.getMessage());
        } finally {
            initialized = true;
        }
    }
}
