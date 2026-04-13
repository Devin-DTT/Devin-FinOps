package com.devin.websocket.handler;

import com.devin.websocket.config.WebSocketProperties;
import com.devin.websocket.service.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
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
        WebSocketProperties properties = new WebSocketProperties();
        properties.setRedisKeyPrefix("finops:endpoint:");
        handler = new DevinWebSocketHandler(
                sessionRegistry, redisTemplate,
                new ObjectMapper(), properties);
    }

    @SuppressWarnings("unchecked")
    private Cursor<String> createMockCursor(List<String> keys) {
        Cursor<String> cursor = mock(Cursor.class);
        doAnswer(inv -> {
            Consumer<String> action = inv.getArgument(0);
            keys.forEach(action);
            return null;
        }).when(cursor).forEachRemaining(any());
        return cursor;
    }

    private void stubScanReturning(List<String> keys) {
        Cursor<String> cursor = createMockCursor(keys);
        doReturn(cursor).when(redisTemplate).scan(any(ScanOptions.class));
    }

    @Test
    void afterConnectionEstablished_registersSession() throws Exception {
        when(session.getId()).thenReturn("session-1");
        stubScanReturning(List.of());

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(session);
    }

    @Test
    void afterConnectionEstablished_sendsInitialSnapshot() throws Exception {
        when(session.getId()).thenReturn("session-1");
        stubScanReturning(List.of(
                "finops:endpoint:list_sessions",
                "finops:endpoint:list_billing_cycles"
        ));
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
        stubScanReturning(List.of());

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
        stubScanReturning(List.of("finops:endpoint:list_sessions__org_org123"));
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
