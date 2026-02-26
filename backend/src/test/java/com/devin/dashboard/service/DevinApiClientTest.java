package com.devin.dashboard.service;

import com.devin.dashboard.model.EndpointDefinition;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DevinApiClient}.
 */
class DevinApiClientTest {

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when both tokens are empty")
    void constructorThrowsWhenTokensEmpty() {
        assertThrows(IllegalStateException.class,
                () -> new DevinApiClient("", ""));
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when both tokens are null")
    void constructorThrowsWhenTokensNull() {
        assertThrows(IllegalStateException.class,
                () -> new DevinApiClient(null, null));
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when tokens are blank")
    void constructorThrowsWhenTokensBlank() {
        assertThrows(IllegalStateException.class,
                () -> new DevinApiClient("   ", "   "));
    }

    @Test
    @DisplayName("Constructor accepts DEVIN_SERVICE_TOKEN as fallback")
    void constructorAcceptsFallbackToken() {
        String fallbackToken = "fallback-token-for-testing-12345";
        DevinApiClient client = new DevinApiClient("", fallbackToken);
        assertNotNull(client);
    }

    @Test
    @DisplayName("Constructor accepts enterprise token as primary")
    void constructorAcceptsEnterpriseToken() {
        String enterpriseToken = "enterprise-token-for-testing-12345";
        DevinApiClient client = new DevinApiClient(enterpriseToken, "");
        assertNotNull(client);
    }

    @Test
    @DisplayName("get() builds URL correctly with endpoint and calls the API")
    void getBuildsUrlCorrectly() throws Exception {
        String responseBody = "{\"items\":[]}";
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        String baseUrl = mockWebServer.url("").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        DevinApiClient client = new DevinApiClient(
                "test-enterprise-token-1234567890", "");

        EndpointDefinition endpoint = EndpointDefinition.builder()
                .name("list_enterprise_sessions")
                .path("/sessions")
                .method("GET")
                .baseUrl(baseUrl)
                .scope("enterprise")
                .build();

        Flux<String> result = client.get(endpoint, Collections.emptyMap());

        StepVerifier.create(result)
                .expectNextMatches(body -> body.contains("items"))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/sessions"));
        assertTrue(request.getHeader("Authorization").contains("Bearer test-enterprise-token-1234567890"));
    }

    @Test
    @DisplayName("get() substitutes path parameters in URL")
    void getSubstitutesPathParams() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        DevinApiClient client = new DevinApiClient(
                "test-enterprise-token-1234567890", "");

        EndpointDefinition endpoint = EndpointDefinition.builder()
                .name("get_session")
                .path("/sessions/{session_id}")
                .method("GET")
                .baseUrl(baseUrl)
                .scope("enterprise")
                .build();

        Flux<String> result = client.get(endpoint, Map.of("session_id", "abc-123"));

        StepVerifier.create(result)
                .expectNextCount(1)
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertTrue(request.getPath().contains("/sessions/abc-123"));
    }
}
