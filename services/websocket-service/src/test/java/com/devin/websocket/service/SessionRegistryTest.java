package com.devin.websocket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SessionRegistry.
 * Verifies session management and broadcast behavior.
 */
@ExtendWith(MockitoExtension.class)
class SessionRegistryTest {

    @Mock
    private WebSocketSession session1;

    @Mock
    private WebSocketSession session2;

    private SessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
    }

    @Test
    void register_addsSession() {
        when(session1.getId()).thenReturn("s1");

        registry.register(session1);

        assertThat(registry.getActiveSessionCount()).isEqualTo(1);
    }

    @Test
    void unregister_removesSession() {
        when(session1.getId()).thenReturn("s1");
        registry.register(session1);

        registry.unregister("s1");

        assertThat(registry.getActiveSessionCount()).isEqualTo(0);
    }

    @Test
    void broadcast_sendsToAllOpenSessions() throws Exception {
        when(session1.getId()).thenReturn("s1");
        when(session2.getId()).thenReturn("s2");
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(true);
        registry.register(session1);
        registry.register(session2);

        registry.broadcast("{\"type\":\"data\"}");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2).sendMessage(any(TextMessage.class));
    }

    @Test
    void broadcast_skipsClosedSessions() throws Exception {
        when(session1.getId()).thenReturn("s1");
        when(session2.getId()).thenReturn("s2");
        when(session1.isOpen()).thenReturn(true);
        when(session2.isOpen()).thenReturn(false);
        registry.register(session1);
        registry.register(session2);

        registry.broadcast("{\"type\":\"data\"}");

        verify(session1).sendMessage(any(TextMessage.class));
        verify(session2, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToSession_sendsWhenOpen() throws Exception {
        when(session1.isOpen()).thenReturn(true);

        registry.sendToSession(session1, "{\"test\":true}");

        verify(session1).sendMessage(any(TextMessage.class));
    }

    @Test
    void sendToSession_doesNotSendWhenClosed() throws Exception {
        when(session1.isOpen()).thenReturn(false);

        registry.sendToSession(session1, "{\"test\":true}");

        verify(session1, never()).sendMessage(any(TextMessage.class));
    }
}
