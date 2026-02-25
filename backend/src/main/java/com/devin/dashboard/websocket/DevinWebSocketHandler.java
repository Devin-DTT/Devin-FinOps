package com.devin.dashboard.websocket;

import com.devin.dashboard.config.EndpointLoader;
import com.devin.dashboard.model.EndpointDefinition;
import com.devin.dashboard.service.DevinApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.Disposable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
@RequiredArgsConstructor
public class DevinWebSocketHandler extends TextWebSocketHandler {

    private final DevinApiClient devinApiClient;
    private final EndpointLoader endpointLoader;

    /** Tracks active polling tasks per session so they can be cancelled on disconnect. */
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /** Tracks active reactive subscriptions per session. */
    private final Map<String, List<Disposable>> activeSubscriptions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
        for (EndpointDefinition endpoint : readEndpoints) {
            try {
                Disposable subscription = devinApiClient
                        .get(endpoint, Collections.emptyMap())
                        .collectList()
                        .subscribe(
                                dataChunks -> {
                                    String payload = buildPayload(endpoint.getName(), String.join("", dataChunks));
                                    sendMessage(session, payload);
                                },
                                error -> log.warn("Poll error for endpoint {}: {}",
                                        endpoint.getName(), error.getMessage())
                        );

                activeSubscriptions
                        .computeIfAbsent(session.getId(), k -> Collections.synchronizedList(new ArrayList<>()))
                        .add(subscription);

            } catch (Exception e) {
                log.error("Failed to poll endpoint {}: {}", endpoint.getName(), e.getMessage());
            }
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
