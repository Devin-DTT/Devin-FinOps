package com.devin.finops.websocket;

import com.devin.finops.model.ConsumptionData;
import com.devin.finops.model.MetricsResult;
import com.devin.finops.service.DevinApiService;
import com.devin.finops.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket handler that streams FinOps metrics in real-time to connected
 * Angular frontend clients.
 *
 * Supports:
 *  - Broadcasting metrics to all connected clients
 *  - On-demand metric recalculation via incoming messages
 *  - Automatic metric push on client connection
 */
@Component
public class FinOpsWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(FinOpsWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final DevinApiService devinApiService;
    private final MetricsService metricsService;
    private final ObjectMapper objectMapper;

    public FinOpsWebSocketHandler(DevinApiService devinApiService,
                                   MetricsService metricsService,
                                   ObjectMapper objectMapper) {
        this.devinApiService = devinApiService;
        this.metricsService = metricsService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        logger.info("WebSocket client connected: {} (total: {})", session.getId(), sessions.size());

        // Send initial acknowledgment
        Map<String, Object> welcome = Map.of(
                "type", "connected",
                "message", "FinOps WebSocket connected",
                "sessionId", session.getId()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        logger.info("Received message from {}: {}", session.getId(), payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(payload, Map.class);
            String action = (String) request.getOrDefault("action", "");

            switch (action) {
                case "fetch_metrics" -> handleFetchMetrics(session, request);
                case "refresh" -> handleRefresh(session);
                default -> {
                    Map<String, Object> errorResponse = Map.of(
                            "type", "error",
                            "message", "Unknown action: " + action
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
                }
            }
        } catch (Exception e) {
            logger.error("Error processing message from {}: {}", session.getId(), e.getMessage());
            Map<String, Object> errorResponse = Map.of(
                    "type", "error",
                    "message", "Failed to process request: " + e.getMessage()
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        logger.info("WebSocket client disconnected: {} (status: {}, remaining: {})",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket transport error for {}: {}", session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Handle a fetch_metrics request from a client.
     * Fetches data from the Devin API, calculates metrics, and sends results.
     */
    private void handleFetchMetrics(WebSocketSession session, Map<String, Object> request)
            throws IOException {
        String startDate = (String) request.get("start_date");
        String endDate = (String) request.get("end_date");

        // Send status update
        sendToSession(session, Map.of("type", "status", "message", "Fetching data from Devin API..."));

        try {
            List<ConsumptionData> data = devinApiService.fetchConsumptionData(startDate, endDate);

            sendToSession(session, Map.of(
                    "type", "status",
                    "message", "Calculating metrics for " + data.size() + " records..."
            ));

            MetricsResult result = metricsService.calculateAllMetrics(data, startDate, endDate);

            Map<String, Object> response = Map.of(
                    "type", "metrics",
                    "data", result,
                    "sessions", data
            );
            sendToSession(session, response);

        } catch (Exception e) {
            logger.error("Failed to fetch metrics: {}", e.getMessage());
            sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Failed to fetch metrics: " + e.getMessage()
            ));
        }
    }

    /**
     * Handle a refresh request - recalculates and broadcasts to all clients.
     */
    private void handleRefresh(WebSocketSession session) throws IOException {
        sendToSession(session, Map.of("type", "status", "message", "Refreshing metrics..."));

        try {
            List<ConsumptionData> data = devinApiService.fetchConsumptionData(null, null);
            MetricsResult result = metricsService.calculateAllMetrics(data, null, null);

            Map<String, Object> response = Map.of(
                    "type", "metrics",
                    "data", result,
                    "sessions", data
            );
            broadcastMessage(response);

        } catch (Exception e) {
            logger.error("Failed to refresh metrics: {}", e.getMessage());
            sendToSession(session, Map.of(
                    "type", "error",
                    "message", "Refresh failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Broadcast a message to all connected WebSocket clients.
     *
     * @param payload Object to serialize and send
     */
    public void broadcastMessage(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            logger.error("Failed to serialize broadcast payload: {}", e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException e) {
                    logger.error("Failed to send message to {}: {}", session.getId(), e.getMessage());
                }
            }
        }
        logger.info("Broadcast sent to {} clients", sessions.size());
    }

    /**
     * Send a message to a specific WebSocket session.
     */
    private void sendToSession(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }

    /**
     * Get the number of currently connected clients.
     */
    public int getConnectedClientCount() {
        return (int) sessions.stream().filter(WebSocketSession::isOpen).count();
    }
}
