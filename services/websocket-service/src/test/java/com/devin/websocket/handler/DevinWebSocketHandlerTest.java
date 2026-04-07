package com.devin.websocket.handler;

import com.devin.websocket.service.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the websocket-service's DevinWebSocketHandler.
 * Verifies session lifecycle and initial snapshot delivery.
 */
@ExtendWith(MockitoExtension.class)
class DevinWebSocketHandlerTest {

    @Mock
    private SessionRegistry sessionRegistry;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private WebSocketSession session;

    private DevinWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DevinWebSocketHandler(sessionRegistry, redisTemplate);
    }

    @Test
    void afterConnectionEstablished_registersSession() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(redisTemplate.keys(anyString())).thenReturn(Set.of());

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(session);
    }

    @Test
    void afterConnectionEstablished_sendsInitialSnapshot() throws Exception {
        when(session.getId()).thenReturn("session-1");
        Set<String> keys = Set.of(
                "finops:endpoint:list_sessions",
                "finops:endpoint:list_billing_cycles"
        );
        when(redisTemplate.keys("finops:endpoint:*")).thenReturn(keys);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finops:endpoint:list_sessions"))
                .thenReturn("{\"sessions\":[]}");
        when(valueOperations.get("finops:endpoint:list_billing_cycles"))
                .thenReturn("{\"cycles\":[]}");

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry, times(2)).sendToSession(eq(session), anyString());
    }

    @Test
    void afterConnectionEstablished_noKeys_noSnapshotSent() throws Exception {
        when(session.getId()).thenReturn("session-1");
        when(redisTemplate.keys("finops:endpoint:*")).thenReturn(null);

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(session);
        verify(sessionRegistry, never()).sendToSession(any(), anyString());
    }

    @Test
    void afterConnectionClosed_unregistersSession() throws Exception {
        when(session.getId()).thenReturn("session-1");

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionRegistry).unregister("session-1");
    }

    @Test
    void handleTextMessage_sendsAck() throws Exception {
        when(session.getId()).thenReturn("session-1");
        TextMessage message = new TextMessage("hello");

        handler.handleTextMessage(session, message);

        verify(sessionRegistry).sendToSession(eq(session),
                argThat(msg -> msg.contains("\"type\":\"ack\"")));
    }

    @Test
    void handleTransportError_unregistersSession() throws Exception {
        when(session.getId()).thenReturn("session-1");

        handler.handleTransportError(session, new RuntimeException("test error"));

        verify(sessionRegistry).unregister("session-1");
    }

    @Test
    void afterConnectionEstablished_parsesOrgKeyCorrectly() throws Exception {
        when(session.getId()).thenReturn("session-1");
        Set<String> keys = Set.of("finops:endpoint:list_sessions__org_org123");
        when(redisTemplate.keys("finops:endpoint:*")).thenReturn(keys);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("finops:endpoint:list_sessions__org_org123"))
                .thenReturn("{\"sessions\":[]}");

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).sendToSession(eq(session),
                argThat(payload ->
                        payload.contains("\"endpoint\":\"list_sessions\"")
                        && payload.contains("\"org_id\":\"org123\"")));
    }
}
