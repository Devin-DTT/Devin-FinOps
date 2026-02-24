package com.devin.finops.websocket;

import com.devin.finops.service.DevinApiService;
import com.devin.finops.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the FinOpsWebSocketHandler.
 */
class FinOpsWebSocketHandlerTest {

    private FinOpsWebSocketHandler handler;
    private DevinApiService devinApiService;
    private MetricsService metricsService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        devinApiService = Mockito.mock(DevinApiService.class);
        metricsService = Mockito.mock(MetricsService.class);
        objectMapper = new ObjectMapper();
        handler = new FinOpsWebSocketHandler(devinApiService, metricsService, objectMapper);
    }

    @Test
    void testAfterConnectionEstablished() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        verify(session).sendMessage(any(TextMessage.class));
        assertEquals(1, handler.getConnectedClientCount());
    }

    @Test
    void testAfterConnectionClosed() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.getConnectedClientCount());

        when(session.isOpen()).thenReturn(false);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        assertEquals(0, handler.getConnectedClientCount());
    }

    @Test
    void testHandleUnknownAction() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        String payload = "{\"action\": \"unknown_action\"}";
        handler.handleMessage(session, new TextMessage(payload));

        // Should send an error response for unknown action
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testHandleInvalidJson() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        String invalidPayload = "not-json";
        handler.handleMessage(session, new TextMessage(invalidPayload));

        // Should send an error response
        verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testBroadcastMessage() throws Exception {
        WebSocketSession session1 = mock(WebSocketSession.class);
        when(session1.getId()).thenReturn("session-1");
        when(session1.isOpen()).thenReturn(true);

        WebSocketSession session2 = mock(WebSocketSession.class);
        when(session2.getId()).thenReturn("session-2");
        when(session2.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        handler.broadcastMessage(java.util.Map.of("type", "test", "data", "hello"));

        // Each session receives: 1 welcome message + 1 broadcast
        verify(session1, times(2)).sendMessage(any(TextMessage.class));
        verify(session2, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void testTransportError() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("test-session-1");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);
        assertEquals(1, handler.getConnectedClientCount());

        when(session.isOpen()).thenReturn(false);
        handler.handleTransportError(session, new RuntimeException("Connection lost"));
        assertEquals(0, handler.getConnectedClientCount());
    }
}
