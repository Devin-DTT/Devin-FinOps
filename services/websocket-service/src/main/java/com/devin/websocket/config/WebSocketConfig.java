package com.devin.websocket.config;

import com.devin.websocket.handler.DevinWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration that registers the handler at {@code /ws/devin-data}.
 * CORS is handled by the API Gateway; here we allow all origins for internal
 * service-to-service communication.
 *
 * <p>The text buffer size is increased to 512 KB to support large endpoint
 * responses (e.g. list_enterprise_sessions can exceed 70 KB).</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private static final int MAX_TEXT_MESSAGE_SIZE = 512 * 1024; // 512 KB

    private final DevinWebSocketHandler devinWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(devinWebSocketHandler, "/ws/devin-data")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        container.setMaxBinaryMessageBufferSize(MAX_TEXT_MESSAGE_SIZE);
        container.setMaxSessionIdleTimeout(300_000L); // 5 minutes
        return container;
    }
}
