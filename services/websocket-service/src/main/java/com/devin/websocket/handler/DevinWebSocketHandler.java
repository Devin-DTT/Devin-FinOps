package com.devin.websocket.handler;

import com.devin.common.model.WebSocketPayload;
import com.devin.websocket.config.WebSocketProperties;
import com.devin.websocket.service.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashSet;
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
    private final ObjectMapper objectMapper;
    private final String redisKeyPrefix;

    public DevinWebSocketHandler(SessionRegistry sessionRegistry,
                                 StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 WebSocketProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisKeyPrefix = properties.getRedisKeyPrefix();
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
     * all cached endpoint data from Redis using SCAN (non-blocking).
     */
    private void sendInitialSnapshot(WebSocketSession session) {
        try {
            String keyPattern = redisKeyPrefix + "*";
            Set<String> keys = new HashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(keyPattern).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                cursor.forEachRemaining(keys::add);
            }

            if (keys.isEmpty()) {
                log.info("No cached data in Redis for initial snapshot");
                return;
            }

            for (String key : keys) {
                String rawData = redisTemplate.opsForValue().get(key);
                if (rawData != null && !rawData.isEmpty()) {
                    String endpointKey = key.replace(redisKeyPrefix, "");
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
            WebSocketPayload payload = WebSocketPayload.fromCacheKey(
                    endpointKey, rawData, objectMapper);
            return payload.toJson(objectMapper);
        } catch (Exception e) {
            log.error("Failed to build snapshot payload for {}: {}",
                    endpointKey, e.getMessage());
            try {
                ObjectNode errorNode = objectMapper.createObjectNode();
                errorNode.put("type", "error");
                errorNode.put("endpoint", endpointKey);
                return objectMapper.writeValueAsString(errorNode);
            } catch (Exception ex) {
                return "{\"type\":\"error\"}";
            }
        }
    }
}
