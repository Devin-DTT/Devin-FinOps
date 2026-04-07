package com.devin.websocket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for RedisMessageSubscriber.
 */
@ExtendWith(MockitoExtension.class)
class RedisMessageSubscriberTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @InjectMocks
    private RedisMessageSubscriber subscriber;

    @Test
    void onMessage_broadcastsToSessions() {
        String payload = "{\"type\":\"data\",\"endpoint\":\"list_sessions\"}";
        Message message = new DefaultMessage(
                "finops:updates".getBytes(),
                payload.getBytes());

        subscriber.onMessage(message, null);

        verify(sessionRegistry).broadcast(payload);
    }
}
