package com.devin.dashboard.websocket;

import com.devin.dashboard.config.DashboardProperties;
import com.devin.dashboard.config.EndpointLoader;
import com.devin.dashboard.model.EndpointDefinition;
import com.devin.dashboard.service.DevinApiClient;
import com.devin.dashboard.service.OrgApiClient;
import com.devin.dashboard.service.OrgDiscoveryService;
import com.devin.dashboard.service.SnapshotService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket handler that bridges the Devin API with connected frontend clients.
 *
 * <p>On connection, starts a polling loop that queries all GET endpoints defined
 * in {@code endpoints.yaml} via the appropriate API client (enterprise or
 * organization), then pushes the results as JSON messages to the WebSocket client.</p>
 *
 * <p>Delegates caching/persistence to {@link SnapshotService} and organization
 * discovery to {@link OrgDiscoveryService}.</p>
 *
 * <p>On disconnection, all active Flux subscriptions and scheduled tasks are
 * cancelled cleanly to prevent resource leaks.</p>
 */
@Slf4j
@Component
public class DevinWebSocketHandler extends TextWebSocketHandler {

    private final DevinApiClient devinApiClient;
    private final OrgApiClient orgApiClient;
    private final EndpointLoader endpointLoader;
    private final SnapshotService snapshotService;
    private final OrgDiscoveryService orgDiscoveryService;
    private final DashboardProperties properties;

    public DevinWebSocketHandler(DevinApiClient devinApiClient,
                                  OrgApiClient orgApiClient,
                                  EndpointLoader endpointLoader,
                                  SnapshotService snapshotService,
                                  OrgDiscoveryService orgDiscoveryService,
                                  DashboardProperties properties) {
        this.devinApiClient = devinApiClient;
        this.orgApiClient = orgApiClient;
        this.endpointLoader = endpointLoader;
        this.snapshotService = snapshotService;
        this.orgDiscoveryService = orgDiscoveryService;
        this.properties = properties;
        this.scheduler = Executors.newScheduledThreadPool(properties.getSchedulerPoolSize());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
        log.info("WebSocket polling scheduler shut down.");
    }

    /** Tracks active polling tasks per session so they can be cancelled on disconnect. */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** Tracks active reactive subscriptions per session. */
    private final Map<String, List<Disposable>> activeSubscriptions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: sessionId={}", session.getId());

        long pollingInterval = properties.getPollingIntervalSeconds();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> pollEndpoints(session),
                0, pollingInterval, TimeUnit.SECONDS
        );
        scheduledTasks.put(session.getId(), future);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        log.debug("Received message from session {}: {}", session.getId(), message.getPayload());
        // The frontend can send filter commands or endpoint-specific requests here.
        // For now, we acknowledge receipt.
        sendMessage(session, "{\"type\":\"ack\",\"message\":\"received\"}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket disconnected: sessionId={}, status={}", session.getId(), status);
        cleanup(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", session.getId(), exception.getMessage());
        cleanup(session.getId());
    }

    /**
     * Polls all GET endpoints from the configuration and sends results to the WebSocket session.
     *
     * <p>For organization-scoped endpoints, the handler supports two modes:</p>
     * <ul>
     *   <li><b>Single-org mode</b>: if {@code DEVIN_ORG_ID} is configured, uses that ID directly.</li>
     *   <li><b>Multi-org mode</b>: uses cached org IDs (refreshed asynchronously every 60s via
     *       {@code list_organizations}) and iterates over each one. The {@code org_id} is included
     *       as a separate field in the JSON payload to maintain frontend compatibility.</li>
     * </ul>
     */
    private void pollEndpoints(WebSocketSession session) {
        if (!session.isOpen()) {
            cleanup(session.getId());
            return;
        }

        // Dispose previous poll cycle's subscriptions to prevent unbounded growth
        List<Disposable> previousSubscriptions = activeSubscriptions.get(session.getId());
        if (previousSubscriptions != null) {
            previousSubscriptions.forEach(Disposable::dispose);
            previousSubscriptions.clear();
        }

        List<EndpointDefinition> readEndpoints = endpointLoader.getReadEndpoints();

        // Use cached org IDs from OrgDiscoveryService
        List<String> orgIds = orgDiscoveryService.getCachedOrgIds();

        // Whether org endpoints should be polled this cycle
        boolean pollOrgEndpoints = orgApiClient.isAvailable() && !orgIds.isEmpty();

        // Calculate total expected responses (must mirror the skip logic below)
        int totalEndpoints = 0;
        for (EndpointDefinition endpoint : readEndpoints) {
            if ("organization".equalsIgnoreCase(endpoint.getScope())) {
                if (pollOrgEndpoints) {
                    totalEndpoints += orgIds.size();
                }
            } else {
                totalEndpoints++;
            }
        }

        AtomicInteger completedCount = new AtomicInteger(0);
        int finalTotalEndpoints = totalEndpoints;

        for (EndpointDefinition endpoint : readEndpoints) {
            try {
                String scope = endpoint.getScope();

                if ("organization".equalsIgnoreCase(scope)) {
                    if (!pollOrgEndpoints) {
                        // Org client not configured or no orgs discovered -- skip
                        continue;
                    }
                    // Iterate over each discovered org
                    for (String currentOrgId : orgIds) {
                        pollOrgEndpoint(session, endpoint, currentOrgId,
                                completedCount, finalTotalEndpoints);
                    }
                } else {
                    // Enterprise-scoped endpoint
                    Flux<String> responseFlux = devinApiClient.get(endpoint, Collections.emptyMap());

                    Disposable subscription = responseFlux
                            .collectList()
                            .subscribe(
                                    dataChunks -> {
                                        String rawData = String.join("", dataChunks);
                                        String payload = buildPayload(endpoint.getName(), rawData);
                                        snapshotService.cacheEndpointData(endpoint.getName(), rawData);
                                        sendMessage(session, payload);
                                        if (completedCount.incrementAndGet() >= finalTotalEndpoints) {
                                            snapshotService.writeSnapshotToDisk();
                                        }
                                    },
                                    error -> {
                                        log.warn("Poll error for endpoint {}: {}",
                                                endpoint.getName(), error.getMessage());
                                        completedCount.incrementAndGet();
                                    }
                            );

                    activeSubscriptions
                            .computeIfAbsent(session.getId(), k -> Collections.synchronizedList(new ArrayList<>()))
                            .add(subscription);
                }

            } catch (Exception e) {
                log.error("Failed to poll endpoint {}: {}", endpoint.getName(), e.getMessage());
                completedCount.incrementAndGet();
            }
        }
    }

    /**
     * Polls a single organization-scoped endpoint for a specific org ID.
     *
     * <p>The WebSocket message uses the original endpoint name (e.g. {@code list_sessions})
     * for frontend compatibility, with {@code org_id} included as a separate field in the
     * JSON payload. The cache key includes the org ID suffix for snapshot differentiation.</p>
     */
    private void pollOrgEndpoint(WebSocketSession session, EndpointDefinition endpoint,
                                 String currentOrgId, AtomicInteger completedCount, int totalEndpoints) {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("org_id", currentOrgId);

        Flux<String> responseFlux = orgApiClient.get(endpoint, pathParams);

        // Cache key includes org ID for snapshot differentiation in multi-org mode
        boolean multiOrg = orgDiscoveryService.isMultiOrg();
        String cacheKey = multiOrg
                ? endpoint.getName() + "__org_" + currentOrgId
                : endpoint.getName();

        Disposable subscription = responseFlux
                .collectList()
                .subscribe(
                        dataChunks -> {
                            String rawData = String.join("", dataChunks);
                            // Use original endpoint name for frontend switch compatibility;
                            // include org_id as separate field in the JSON payload
                            String payload = buildPayload(endpoint.getName(), rawData, currentOrgId);
                            snapshotService.cacheEndpointData(cacheKey, rawData);
                            sendMessage(session, payload);
                            if (completedCount.incrementAndGet() >= totalEndpoints) {
                                snapshotService.writeSnapshotToDisk();
                            }
                        },
                        error -> {
                            log.warn("Poll error for endpoint {} (org {}): {}",
                                    endpoint.getName(), currentOrgId, error.getMessage());
                            completedCount.incrementAndGet();
                        }
                );

        activeSubscriptions
                .computeIfAbsent(session.getId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(subscription);
    }

    /**
     * Builds a JSON payload wrapping the endpoint data using Jackson for proper serialization.
     */
    private String buildPayload(String endpointName, String data) {
        return buildPayload(endpointName, data, null);
    }

    /**
     * Builds a JSON payload wrapping the endpoint data, optionally including an org_id field.
     *
     * <p>The {@code endpoint} field always uses the original endpoint name (e.g. {@code list_sessions})
     * so the frontend switch statement can match it. The optional {@code org_id} field allows the
     * frontend to differentiate data from different organizations in multi-org mode.</p>
     *
     * @param endpointName the original endpoint name (unchanged for frontend compatibility)
     * @param data         the raw JSON response body
     * @param orgId        the organization ID (null for enterprise-scoped endpoints)
     */
    private String buildPayload(String endpointName, String data, String orgId) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "data");
            node.put("endpoint", endpointName);
            node.put("timestamp", System.currentTimeMillis());
            if (orgId != null && !orgId.isBlank()) {
                node.put("org_id", orgId);
            }
            if (data.isEmpty()) {
                node.putNull("data");
            } else {
                node.set("data", objectMapper.readTree(data));
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Failed to build payload for endpoint {}", endpointName, e);
            return "{\"type\":\"error\",\"endpoint\":\"" + endpointName.replace("\"", "\\\"") + "\"}";
        }
    }

    /**
     * Sends a text message to the WebSocket session, handling errors gracefully.
     */
    private void sendMessage(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Cancels all scheduled tasks and reactive subscriptions for the given session.
     */
    private void cleanup(String sessionId) {
        // Cancel polling task
        ScheduledFuture<?> future = scheduledTasks.remove(sessionId);
        if (future != null) {
            future.cancel(true);
            log.debug("Cancelled polling task for session {}", sessionId);
        }

        // Dispose active Flux subscriptions
        List<Disposable> subscriptions = activeSubscriptions.remove(sessionId);
        if (subscriptions != null) {
            subscriptions.forEach(Disposable::dispose);
            log.debug("Disposed {} subscriptions for session {}", subscriptions.size(), sessionId);
        }
    }
}
