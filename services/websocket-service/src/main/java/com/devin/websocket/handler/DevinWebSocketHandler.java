package com.devin.websocket.handler;

import com.devin.websocket.service.SessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;

/**
 * WebSocket handler for the websocket-service microservice.
 *
 * <p>Unlike the monolith's handler, this does NOT perform any polling.
 * Instead:</p>
 * <ul>
 *   <li>On connection: registers the session and sends the initial snapshot
 *       from Redis (all {@code finops:endpoint:*} keys).</li>
 *   <li>On Redis messages: the {@code RedisMessageSubscriber} broadcasts
 *       to all sessions via {@code SessionRegistry}.</li>
 *   <li>On disconnect: unregisters the session.</li>
 * </ul>
 */
@Slf4j
@Component
public class DevinWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_KEY_PATTERN = "finops:endpoint:*";

    public DevinWebSocketHandler(SessionRegistry sessionRegistry,
                                 StringRedisTemplate redisTemplate) {
        this.sessionRegistry = sessionRegistry;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket connected: sessionId={}", session.getId());
        sessionRegistry.register(session);
        sendInitialSnapshot(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) {
        log.debug("Received message from session {}: {}",
                session.getId(), message.getPayload());
        sessionRegistry.sendToSession(session,
                "{\"type\":\"ack\",\"message\":\"received\"}");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,
                                      CloseStatus status) {
        log.info("WebSocket disconnected: sessionId={}, status={}",
                session.getId(), status);
        sessionRegistry.unregister(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session,
                                     Throwable exception) {
        log.error("WebSocket transport error for session {}: {}",
                session.getId(), exception.getMessage());
        sessionRegistry.unregister(session.getId());
    }

    /**
     * Sends the initial snapshot to a newly connected client by reading
     * all cached endpoint data from Redis.
     */
    private void sendInitialSnapshot(WebSocketSession session) {
        try {
            Set<String> keys = redisTemplate.keys(REDIS_KEY_PATTERN);
            if (keys == null || keys.isEmpty()) {
                log.info("No cached data in Redis for initial snapshot");
                return;
            }

            for (String key : keys) {
                String rawData = redisTemplate.opsForValue().get(key);
                if (rawData != null && !rawData.isEmpty()) {
                    // Key format: finops:endpoint:{endpoint_name}
                    // or finops:endpoint:{endpoint_name}__org_{orgId}
                    String endpointKey = key.replace("finops:endpoint:", "");

                    // Build a payload matching the WebSocket message format
                    String payload = buildSnapshotPayload(endpointKey, rawData);
                    sessionRegistry.sendToSession(session, payload);
                }
            }
            log.info("Sent initial snapshot ({} keys) to session {}",
                    keys.size(), session.getId());
        } catch (Exception e) {
            log.error("Failed to send initial snapshot to session {}: {}",
                    session.getId(), e.getMessage());
        }
    }

    /**
     * Builds a JSON payload for the initial snapshot, matching the format
     * used by the data-collector's Redis Pub/Sub messages.
     */
    private String buildSnapshotPayload(String endpointKey, String rawData) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode node =
                    mapper.createObjectNode();
            node.put("type", "data");
            node.put("timestamp", System.currentTimeMillis());

            // Parse org_id from cache key if present
            // Format: endpoint_name__org_orgId
            String endpointName;
            String orgId = null;
            if (endpointKey.contains("__org_")) {
                int idx = endpointKey.indexOf("__org_");
                endpointName = endpointKey.substring(0, idx);
                orgId = endpointKey.substring(idx + 6);
            } else {
                endpointName = endpointKey;
            }

            node.put("endpoint", endpointName);
            if (orgId != null) {
                node.put("org_id", orgId);
            }

            if (rawData.isEmpty()) {
                node.putNull("data");
            } else {
                node.set("data", mapper.readTree(rawData));
            }
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            log.error("Failed to build snapshot payload for {}: {}",
                    endpointKey, e.getMessage());
            return "{\"type\":\"error\",\"endpoint\":\""
                    + endpointKey.replace("\"", "\\\"") + "\"}";
        }
    }
}
