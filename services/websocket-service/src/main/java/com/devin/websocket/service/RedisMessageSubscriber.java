package com.devin.websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

/**
 * Subscribes to the Redis Pub/Sub channel {@code finops:updates}
 * and broadcasts received messages to all connected WebSocket clients
 * via the {@link SessionRegistry}.
 */
@Slf4j
@Service
public class RedisMessageSubscriber implements MessageListener {

    private final SessionRegistry sessionRegistry;

    public RedisMessageSubscriber(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody());
        log.debug("Received Redis message on channel {}: {} chars",
                new String(message.getChannel()), payload.length());
        sessionRegistry.broadcast(payload);
    }
}
