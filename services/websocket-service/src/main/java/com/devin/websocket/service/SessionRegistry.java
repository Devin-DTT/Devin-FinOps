package com.devin.websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains the registry of active WebSocket sessions and provides
 * efficient broadcast to all connected clients.
 */
@Slf4j
@Service
public class SessionRegistry {

    private final Map<String, WebSocketSession> sessions =
            new ConcurrentHashMap<>();

    /**
     * Registers a new WebSocket session.
     */
    public void register(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Session registered: {} (total: {})",
                session.getId(), sessions.size());
    }

    /**
     * Removes a WebSocket session.
     */
    public void unregister(String sessionId) {
        sessions.remove(sessionId);
        log.info("Session unregistered: {} (total: {})",
                sessionId, sessions.size());
    }

    /**
     * Broadcasts a text message to all connected WebSocket sessions.
     */
    public void broadcast(String payload) {
        TextMessage message = new TextMessage(payload);
        for (Map.Entry<String, WebSocketSession> entry
                : sessions.entrySet()) {
            WebSocketSession session = entry.getValue();
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.error("Failed to send message to session {}: {}",
                            entry.getKey(), e.getMessage());
                }
            } else {
                sessions.remove(entry.getKey());
            }
        }
    }

    /**
     * Sends a message to a specific session.
     */
    public void sendToSession(WebSocketSession session, String payload) {
        if (session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.error("Failed to send message to session {}: {}",
                        session.getId(), e.getMessage());
            }
        }
    }

    /**
     * Returns the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
