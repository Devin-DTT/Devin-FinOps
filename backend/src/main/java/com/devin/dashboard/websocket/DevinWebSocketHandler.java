package com.devin.dashboard.websocket;

import com.devin.dashboard.config.EndpointLoader;
import com.devin.dashboard.model.EndpointDefinition;
import com.devin.dashboard.service.DevinApiClient;
import com.devin.dashboard.service.OrgApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket handler that bridges the Devin API with connected frontend clients.
 *
 * <p>On connection, starts a polling loop (every 5 seconds) that queries all GET
 * endpoints defined in {@code endpoints.yaml} via {@link DevinApiClient}, then
 * pushes the results as JSON messages to the WebSocket client.</p>
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

    public DevinWebSocketHandler(DevinApiClient devinApiClient,
                                  OrgApiClient orgApiClient,
                                  EndpointLoader endpointLoader) {
        this.devinApiClient = devinApiClient;
        this.orgApiClient = orgApiClient;
        this.endpointLoader = endpointLoader;

        // If multi-org mode, start async org discovery that refreshes every 60 seconds
        if (orgApiClient.getOrgId().isEmpty()) {
            orgDiscoveryExecutor.scheduleAtFixedRate(
                    this::refreshOrgIds, 0, 60, TimeUnit.SECONDS);
        } else {
            // Single-org: use configured value, mark as initialized
            this.cachedOrgIds = List.of(orgApiClient.getOrgId().get());
            this.orgDiscoveryInitialized = true;
        }
    }

    /** Tracks active polling tasks per session so they can be cancelled on disconnect. */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** Tracks active reactive subscriptions per session. */
    private final Map<String, List<Disposable>> activeSubscriptions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /** Single-thread executor for asynchronous org discovery (avoids blocking scheduler threads). */
    private final ScheduledExecutorService orgDiscoveryExecutor = Executors.newSingleThreadScheduledExecutor();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** In-memory cache of the latest API response per endpoint (key = endpoint name). */
    private final Map<String, JsonNode> latestSnapshot = new ConcurrentHashMap<>();

    /** Cached list of discovered organization IDs (refreshed every 60 seconds). */
    private volatile List<String> cachedOrgIds = Collections.emptyList();

    /** Whether the initial org discovery has completed at least once. */
    private volatile boolean orgDiscoveryInitialized = false;

    private static final Path SNAPSHOT_DIR = Paths.get("data");
    private static final Path SNAPSHOT_FILE = SNAPSHOT_DIR.resolve("latest-snapshot.json");
    private static final ObjectMapper prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: sessionId={}", session.getId());

        // Start polling every 5 seconds
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> pollEndpoints(session),
                0, 5, TimeUnit.SECONDS
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

        // Use cached org IDs (refreshed asynchronously every 60s)
        List<String> orgIds = cachedOrgIds;

        // Calculate total expected responses
        int totalEndpoints = 0;
        for (EndpointDefinition endpoint : readEndpoints) {
            if ("organization".equalsIgnoreCase(endpoint.getScope())) {
                totalEndpoints += orgIds.size();
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
                    if (orgIds.isEmpty()) {
                        // No orgs available, skip organization endpoints
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
                                        cacheEndpointData(endpoint.getName(), rawData);
                                        sendMessage(session, payload);
                                        if (completedCount.incrementAndGet() >= finalTotalEndpoints) {
                                            writeSnapshotToDisk();
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
        boolean multiOrg = orgApiClient.getOrgId().isEmpty();
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
                            cacheEndpointData(cacheKey, rawData);
                            sendMessage(session, payload);
                            if (completedCount.incrementAndGet() >= totalEndpoints) {
                                writeSnapshotToDisk();
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
     * Asynchronously refreshes the cached list of organization IDs.
     * Runs on a dedicated single-thread executor every 60 seconds to avoid
     * blocking the WebSocket polling scheduler threads.
     */
    private void refreshOrgIds() {
        Optional<EndpointDefinition> listOrgsEndpoint = endpointLoader.findByName("list_organizations");
        if (listOrgsEndpoint.isEmpty()) {
            log.warn("list_organizations endpoint not found in endpoints.yaml. "
                    + "Cannot auto-discover organizations. Skipping organization-scoped endpoints.");
            orgDiscoveryInitialized = true;
            return;
        }

        try {
            String responseBody = devinApiClient.get(listOrgsEndpoint.get(), Collections.emptyMap())
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(java.time.Duration.ofSeconds(10));

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("list_organizations returned empty response. Keeping previous cached org IDs.");
                orgDiscoveryInitialized = true;
                return;
            }

            JsonNode root = objectMapper.readTree(responseBody);
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
            orgDiscoveryInitialized = true;
        }
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
     * Caches the parsed JSON response for the given endpoint name.
     */
    private void cacheEndpointData(String endpointName, String rawData) {
        try {
            if (rawData != null && !rawData.isEmpty()) {
                JsonNode parsed = objectMapper.readTree(rawData);
                latestSnapshot.put(endpointName, parsed);
            }
        } catch (Exception e) {
            log.warn("Failed to cache data for endpoint {}: {}", endpointName, e.getMessage());
        }
    }

    /**
     * Writes the current in-memory snapshot to data/latest-snapshot.json.
     */
    private void writeSnapshotToDisk() {
        try {
            if (!Files.exists(SNAPSHOT_DIR)) {
                Files.createDirectories(SNAPSHOT_DIR);
            }
            ObjectNode snapshotNode = prettyMapper.createObjectNode();
            snapshotNode.put("timestamp", System.currentTimeMillis());
            ObjectNode endpointsNode = snapshotNode.putObject("endpoints");
            for (Map.Entry<String, JsonNode> entry : latestSnapshot.entrySet()) {
                endpointsNode.set(entry.getKey(), entry.getValue());
            }
            String json = prettyMapper.writeValueAsString(snapshotNode);
            Files.writeString(SNAPSHOT_FILE, json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("Snapshot written to {}", SNAPSHOT_FILE);
        } catch (Exception e) {
            log.warn("Failed to write snapshot to disk: {}", e.getMessage());
        }
    }

    /**
     * Returns the latest cached snapshot (read-only view).
     */
    public Map<String, JsonNode> getLatestSnapshot() {
        return Collections.unmodifiableMap(latestSnapshot);
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
