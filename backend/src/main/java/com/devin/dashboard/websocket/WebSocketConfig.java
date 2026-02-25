package com.devin.dashboard.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration that registers the {@link DevinWebSocketHandler}
 * at the {@code /ws/devin-data} endpoint with open CORS for frontend connectivity.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final DevinWebSocketHandler devinWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(devinWebSocketHandler, "/ws/devin-data")
                .setAllowedOrigins("*");
    }
}
