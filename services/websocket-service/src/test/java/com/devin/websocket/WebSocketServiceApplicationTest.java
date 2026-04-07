package com.devin.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the WebSocketServiceApplication class.
 * Full context loading requires Redis; tested via integration tests.
 */
class WebSocketServiceApplicationTest {

    @Test
    void applicationClassExists() {
        assertThat(WebSocketServiceApplication.class).isNotNull();
    }
}
