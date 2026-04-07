package com.devin.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * WebSocket Service entry point.
 * Subscribes to Redis Pub/Sub and broadcasts data to connected WebSocket clients.
 */
@SpringBootApplication
public class WebSocketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSocketServiceApplication.class, args);
    }
}
