package com.devin.finops.config;

import com.devin.finops.websocket.FinOpsWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration that registers the FinOps handler.
 * Enables real-time metric streaming to Angular frontend clients.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final FinOpsWebSocketHandler finOpsWebSocketHandler;

    @Value("${finops.websocket.endpoint:/ws/finops}")
    private String websocketEndpoint;

    @Value("${finops.websocket.allowed-origins:*}")
    private String allowedOrigins;

    public WebSocketConfig(FinOpsWebSocketHandler finOpsWebSocketHandler) {
        this.finOpsWebSocketHandler = finOpsWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(finOpsWebSocketHandler, websocketEndpoint)
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
