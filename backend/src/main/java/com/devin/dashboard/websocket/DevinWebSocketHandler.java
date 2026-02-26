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
    }

    /** Tracks active polling tasks per session so they can be cancelled on disconnect. */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** Tracks active reactive subscriptions per session. */
    private final Map<String, List<Disposable>> activeSubscriptions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /** In-memory cache of the latest API response per endpoint (key = endpoint name). */
    private final Map<String, JsonNode> latestSnapshot = new ConcurrentHashMap<>();

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
     *   <li><b>Multi-org mode</b>: calls {@code list_organizations} via the enterprise API to
     *       discover all org IDs, then iterates over each one. Results are sent with an
     *       endpoint name that includes the org ID (e.g. {@code list_sessions__org_abc123}).</li>
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

        // Resolve the list of org IDs to use for organization-scoped endpoints
        List<String> orgIds = resolveOrgIds();

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
     * The WebSocket endpoint name is suffixed with {@code __org_{orgId}} when in
     * multi-org mode, or kept as-is in single-org mode.
     */
    private void pollOrgEndpoint(WebSocketSession session, EndpointDefinition endpoint,
                                 String currentOrgId, AtomicInteger completedCount, int totalEndpoints) {
        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("org_id", currentOrgId);

        Flux<String> responseFlux = orgApiClient.get(endpoint, pathParams);

        // In multi-org mode, suffix the endpoint name with the org ID
        boolean multiOrg = orgApiClient.getOrgId().isEmpty();
        String endpointKey = multiOrg
                ? endpoint.getName() + "__org_" + currentOrgId
                : endpoint.getName();

        Disposable subscription = responseFlux
                .collectList()
                .subscribe(
                        dataChunks -> {
                            String rawData = String.join("", dataChunks);
                            String payload = buildPayload(endpointKey, rawData);
                            cacheEndpointData(endpointKey, rawData);
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
     * Resolves the list of organization IDs to poll.
     *
     * <p>If {@code DEVIN_ORG_ID} is configured, returns a singleton list with that value
     * (single-org fallback). Otherwise, calls the enterprise {@code list_organizations}
     * endpoint and parses the org IDs from the JSON response.</p>
     *
     * @return list of org IDs (may be empty if discovery fails)
     */
    private List<String> resolveOrgIds() {
        Optional<String> configuredOrgId = orgApiClient.getOrgId();
        if (configuredOrgId.isPresent()) {
            return List.of(configuredOrgId.get());
        }

        // Multi-org: call list_organizations via enterprise API
        Optional<EndpointDefinition> listOrgsEndpoint = endpointLoader.findByName("list_organizations");
        if (listOrgsEndpoint.isEmpty()) {
            log.warn("list_organizations endpoint not found in endpoints.yaml. "
                    + "Cannot auto-discover organizations. Skipping organization-scoped endpoints.");
            return Collections.emptyList();
        }

        try {
            String responseBody = devinApiClient.get(listOrgsEndpoint.get(), Collections.emptyMap())
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(java.time.Duration.ofSeconds(15));

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("list_organizations returned empty response. Skipping organization-scoped endpoints.");
                return Collections.emptyList();
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
            }

            return orgIds;

        } catch (Exception e) {
            log.error("Failed to call list_organizations for multi-org discovery: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Builds a JSON payload wrapping the endpoint data using Jackson for proper serialization.
     */
    private String buildPayload(String endpointName, String data) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("type", "data");
            node.put("endpoint", endpointName);
            node.put("timestamp", System.currentTimeMillis());
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
