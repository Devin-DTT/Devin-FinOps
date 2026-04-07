package com.devin.websocket.config;

import com.devin.websocket.handler.DevinWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration that registers the handler at {@code /ws/devin-data}.
 * CORS is handled by the API Gateway; here we allow all origins for internal
 * service-to-service communication.
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
