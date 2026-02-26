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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OrgApiClient}.
 */
class OrgApiClientTest {

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
    @DisplayName("Constructor throws IllegalStateException when DEVIN_ORG_SERVICE_TOKEN is empty")
    void constructorThrowsWhenTokenEmpty() {
        assertThrows(IllegalStateException.class,
                () -> new OrgApiClient("", "org-123"));
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when DEVIN_ORG_SERVICE_TOKEN is null")
    void constructorThrowsWhenTokenNull() {
        assertThrows(IllegalStateException.class,
                () -> new OrgApiClient(null, "org-123"));
    }

    @Test
    @DisplayName("Constructor throws IllegalStateException when DEVIN_ORG_SERVICE_TOKEN is blank")
    void constructorThrowsWhenTokenBlank() {
        assertThrows(IllegalStateException.class,
                () -> new OrgApiClient("   ", "org-123"));
    }

    @Test
    @DisplayName("getOrgId() returns empty when DEVIN_ORG_ID is not configured (multi-org mode)")
    void getOrgIdReturnsEmptyWhenNotConfigured() {
        String token = "valid-org-service-token-1234567890";
        OrgApiClient client = new OrgApiClient(token, "");
        assertEquals(Optional.empty(), client.getOrgId());
    }

    @Test
    @DisplayName("getOrgId() returns empty when DEVIN_ORG_ID is null")
    void getOrgIdReturnsEmptyWhenNull() {
        String token = "valid-org-service-token-1234567890";
        OrgApiClient client = new OrgApiClient(token, null);
        assertEquals(Optional.empty(), client.getOrgId());
    }

    @Test
    @DisplayName("getOrgId() returns value when DEVIN_ORG_ID is configured")
    void getOrgIdReturnsValueWhenConfigured() {
        String token = "valid-org-service-token-1234567890";
        OrgApiClient client = new OrgApiClient(token, "my-org-123");
        assertEquals(Optional.of("my-org-123"), client.getOrgId());
    }

    @Test
    @DisplayName("get() builds URL with org_id correctly")
    void getBuildsUrlWithOrgId() throws Exception {
        String responseBody = "{\"items\":[]}";
        mockWebServer.enqueue(new MockResponse()
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

        String baseUrl = mockWebServer.url("").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        OrgApiClient client = new OrgApiClient(
                "valid-org-service-token-1234567890", "org-456");

        EndpointDefinition endpoint = EndpointDefinition.builder()
                .name("list_sessions")
                .path("/{org_id}/sessions")
                .method("GET")
                .baseUrl(baseUrl)
                .scope("organization")
                .build();

        Flux<String> result = client.get(endpoint, Map.of("org_id", "org-456"));

        StepVerifier.create(result)
                .expectNextMatches(body -> body.contains("items"))
                .verifyComplete();

        RecordedRequest request = mockWebServer.takeRequest();
        assertNotNull(request.getPath());
        assertTrue(request.getPath().contains("/org-456/sessions"));
        assertTrue(request.getHeader("Authorization").contains(
                "Bearer valid-org-service-token-1234567890"));
    }

    @Test
    @DisplayName("Constructor creates client successfully with valid token")
    void constructorCreatesClientWithValidToken() {
        OrgApiClient client = new OrgApiClient(
                "valid-org-service-token-1234567890", "org-id-123");
        assertNotNull(client);
    }
}
