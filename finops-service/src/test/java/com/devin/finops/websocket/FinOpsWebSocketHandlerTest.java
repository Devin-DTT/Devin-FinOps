package com.devin.finops.websocket;

import com.devin.finops.config.MetricsProperties;
import com.devin.finops.model.ConsumptionData;
import com.devin.finops.service.DevinApiService;
import com.devin.finops.service.MetricsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit and integration tests for the FinOpsWebSocketHandler.
 * Mirrors Python test coverage from tests/test_integration.py
 * (TestExportDailyACUsCSV, TestEndToEndMetricsCalculation).
 */
class FinOpsWebSocketHandlerTest {

    // =========================================================================
    // BASIC HANDLER TESTS
    // =========================================================================

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

        handler.broadcastMessage(Map.of("type", "test", "data", "hello"));

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

    // =========================================================================
    // INTEGRATION TESTS: fetch_metrics flow
    // (equivalent to Python TestExportDailyACUsCSV / TestEndToEndMetricsCalculation)
    // =========================================================================

    @Nested
    class FetchMetricsIntegrationTests {

        private FinOpsWebSocketHandler integrationHandler;
        private DevinApiService mockApiService;
        private MetricsService realMetricsService;
        private ObjectMapper mapper;
        private List<ConsumptionData> sampleData;

        @BeforeEach
        void setUp() {
            mockApiService = Mockito.mock(DevinApiService.class);

            // Use real MetricsService for integration testing
            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.05);
            config.setCurrency("USD");
            config.setWorkingHoursPerDay(8);
            config.setWorkingDaysPerMonth(22);
            realMetricsService = new MetricsService(config);

            mapper = new ObjectMapper();
            integrationHandler = new FinOpsWebSocketHandler(
                    mockApiService, realMetricsService, mapper);

            // Sample data matching Python SAMPLE_SESSIONS
            sampleData = new ArrayList<>();
            sampleData.add(new ConsumptionData(
                    "int_001", "alice", "org_001", "proj_001", null,
                    "2024-09-01T10:00:00", 200.0, "Engineering", "feature_development",
                    false, true, "Success"));
            sampleData.add(new ConsumptionData(
                    "int_002", "bob", "org_001", "proj_001", null,
                    "2024-09-01T14:00:00", 500.0, "Engineering", "bug_fix",
                    false, false, "Success"));
            sampleData.add(new ConsumptionData(
                    "int_003", "alice", "org_001", "proj_002", null,
                    "2024-09-02T09:00:00", 100.0, "Engineering", "code_review",
                    false, true, "Success"));
            sampleData.add(new ConsumptionData(
                    "int_004", "carol", "org_002", "proj_003", null,
                    "2024-09-03T11:00:00", 300.0, "Product", "feature_development",
                    false, false, "Success"));
            sampleData.add(new ConsumptionData(
                    "int_005", "bob", "org_001", "proj_001", null,
                    "2024-09-04T09:00:00", 150.0, "Engineering", "bug_fix",
                    false, false, "Success"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void testFetchMetricsSendsStatusAndResult() throws Exception {
            when(mockApiService.fetchConsumptionData(anyString(), anyString()))
                    .thenReturn(sampleData);

            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("integration-test-1");
            when(session.isOpen()).thenReturn(true);

            integrationHandler.afterConnectionEstablished(session);

            String fetchRequest = mapper.writeValueAsString(Map.of(
                    "action", "fetch_metrics",
                    "start_date", "2024-09-01",
                    "end_date", "2024-09-30"
            ));
            integrationHandler.handleMessage(session, new TextMessage(fetchRequest));

            // Should receive: 1 welcome + 1 status "Fetching..." + 1 status "Calculating..." + 1 metrics
            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, atLeast(4)).sendMessage(captor.capture());

            List<TextMessage> messages = captor.getAllValues();

            // Verify welcome message
            String welcomeJson = messages.get(0).getPayload();
            Map<String, Object> welcome = mapper.readValue(welcomeJson, Map.class);
            assertEquals("connected", welcome.get("type"));

            // Verify final metrics message
            String metricsJson = messages.get(messages.size() - 1).getPayload();
            Map<String, Object> metricsMsg = mapper.readValue(metricsJson, Map.class);
            assertEquals("metrics", metricsMsg.get("type"));

            // Verify metrics data structure
            Map<String, Object> data = (Map<String, Object>) metricsMsg.get("data");
            assertNotNull(data);
        }

        @Test
        @SuppressWarnings("unchecked")
        void testFetchMetricsCalculatesCorrectTotals() throws Exception {
            when(mockApiService.fetchConsumptionData(anyString(), anyString()))
                    .thenReturn(sampleData);

            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("integration-test-2");
            when(session.isOpen()).thenReturn(true);

            integrationHandler.afterConnectionEstablished(session);

            String fetchRequest = mapper.writeValueAsString(Map.of(
                    "action", "fetch_metrics",
                    "start_date", "2024-09-01",
                    "end_date", "2024-09-30"
            ));
            integrationHandler.handleMessage(session, new TextMessage(fetchRequest));

            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, atLeast(4)).sendMessage(captor.capture());

            // Get the last message (metrics result)
            List<TextMessage> messages = captor.getAllValues();
            String metricsJson = messages.get(messages.size() - 1).getPayload();
            Map<String, Object> metricsMsg = mapper.readValue(metricsJson, Map.class);
            Map<String, Object> data = (Map<String, Object>) metricsMsg.get("data");
            Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");

            // Total ACUs = 200 + 500 + 100 + 300 + 150 = 1250
            double totalAcus = ((Number) metrics.get("02_total_acus")).doubleValue();
            assertEquals(1250.0, totalAcus, 0.01);

            // Total cost = 1250 * 0.05 = 62.50
            double totalCost = ((Number) metrics.get("01_total_monthly_cost")).doubleValue();
            assertEquals(62.50, totalCost, 0.01);

            // Total sessions = 5
            int totalSessions = ((Number) metrics.get("06_total_sessions")).intValue();
            assertEquals(5, totalSessions);

            // Unique users = 3 (alice, bob, carol)
            int uniqueUsers = ((Number) metrics.get("12_unique_users")).intValue();
            assertEquals(3, uniqueUsers);
        }

        @Test
        @SuppressWarnings("unchecked")
        void testFetchMetricsConsistencyChecks() throws Exception {
            // Equivalent to Python TestEndToEndMetricsCalculation consistency tests
            when(mockApiService.fetchConsumptionData(anyString(), anyString()))
                    .thenReturn(sampleData);

            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("consistency-test");
            when(session.isOpen()).thenReturn(true);

            integrationHandler.afterConnectionEstablished(session);

            String fetchRequest = mapper.writeValueAsString(Map.of(
                    "action", "fetch_metrics",
                    "start_date", "2024-09-01",
                    "end_date", "2024-09-30"
            ));
            integrationHandler.handleMessage(session, new TextMessage(fetchRequest));

            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, atLeast(4)).sendMessage(captor.capture());

            List<TextMessage> messages = captor.getAllValues();
            String metricsJson = messages.get(messages.size() - 1).getPayload();
            Map<String, Object> metricsMsg = mapper.readValue(metricsJson, Map.class);
            Map<String, Object> data = (Map<String, Object>) metricsMsg.get("data");
            Map<String, Object> metrics = (Map<String, Object>) data.get("metrics");

            // Verify all 20 metrics are present
            assertEquals(20, metrics.size());

            // Verify cost = acus * price
            double totalAcus = ((Number) metrics.get("02_total_acus")).doubleValue();
            double totalCost = ((Number) metrics.get("01_total_monthly_cost")).doubleValue();
            assertEquals(totalAcus * 0.05, totalCost, 0.01);

            // Verify cost_per_user sums to total
            Map<String, Object> costPerUser = (Map<String, Object>) metrics.get("03_cost_per_user");
            double costSum = costPerUser.values().stream()
                    .mapToDouble(v -> ((Number) v).doubleValue()).sum();
            assertEquals(totalCost, costSum, 0.01);

            // Verify sessions_per_user sums to total
            Map<String, Object> sessionsPerUser = (Map<String, Object>) metrics.get("07_sessions_per_user");
            int sessionSum = sessionsPerUser.values().stream()
                    .mapToInt(v -> ((Number) v).intValue()).sum();
            assertEquals(((Number) metrics.get("06_total_sessions")).intValue(), sessionSum);
        }

        @Test
        @SuppressWarnings("unchecked")
        void testFetchMetricsApiError() throws Exception {
            when(mockApiService.fetchConsumptionData(anyString(), anyString()))
                    .thenThrow(new RuntimeException("API connection failed"));

            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("error-test");
            when(session.isOpen()).thenReturn(true);

            integrationHandler.afterConnectionEstablished(session);

            String fetchRequest = mapper.writeValueAsString(Map.of(
                    "action", "fetch_metrics",
                    "start_date", "2024-09-01",
                    "end_date", "2024-09-30"
            ));
            integrationHandler.handleMessage(session, new TextMessage(fetchRequest));

            ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
            verify(session, atLeast(3)).sendMessage(captor.capture());

            // Last message should be error
            List<TextMessage> messages = captor.getAllValues();
            String errorJson = messages.get(messages.size() - 1).getPayload();
            Map<String, Object> errorMsg = mapper.readValue(errorJson, Map.class);
            assertEquals("error", errorMsg.get("type"));
            assertTrue(((String) errorMsg.get("message")).contains("Failed to fetch metrics"));
        }
    }

    // =========================================================================
    // INTEGRATION TESTS: refresh flow with broadcast
    // =========================================================================

    @Nested
    class RefreshBroadcastTests {

        private FinOpsWebSocketHandler refreshHandler;
        private DevinApiService mockApiService;
        private MetricsService realMetricsService;
        private ObjectMapper mapper;

        @BeforeEach
        void setUp() {
            mockApiService = Mockito.mock(DevinApiService.class);

            MetricsProperties config = new MetricsProperties();
            config.setPricePerAcu(0.05);
            config.setCurrency("USD");
            realMetricsService = new MetricsService(config);

            mapper = new ObjectMapper();
            refreshHandler = new FinOpsWebSocketHandler(
                    mockApiService, realMetricsService, mapper);
        }

        @Test
        void testRefreshBroadcastsToAllClients() throws Exception {
            List<ConsumptionData> data = List.of(
                    new ConsumptionData("s1", "user1", "org1", "p1", null,
                            "2025-01-01T10:00:00", 100.0, "Eng", "Feature",
                            false, true, "Success")
            );
            when(mockApiService.fetchConsumptionData(null, null)).thenReturn(data);

            WebSocketSession session1 = mock(WebSocketSession.class);
            when(session1.getId()).thenReturn("refresh-1");
            when(session1.isOpen()).thenReturn(true);

            WebSocketSession session2 = mock(WebSocketSession.class);
            when(session2.getId()).thenReturn("refresh-2");
            when(session2.isOpen()).thenReturn(true);

            refreshHandler.afterConnectionEstablished(session1);
            refreshHandler.afterConnectionEstablished(session2);

            // Send refresh from session1
            String refreshRequest = mapper.writeValueAsString(Map.of("action", "refresh"));
            refreshHandler.handleMessage(session1, new TextMessage(refreshRequest));

            // session1: 1 welcome + 1 status "Refreshing..." + 1 broadcast metrics
            verify(session1, atLeast(3)).sendMessage(any(TextMessage.class));
            // session2: 1 welcome + 1 broadcast metrics (no status messages)
            verify(session2, atLeast(2)).sendMessage(any(TextMessage.class));
        }

        @Test
        void testRefreshApiErrorDoesNotBroadcast() throws Exception {
            when(mockApiService.fetchConsumptionData(null, null))
                    .thenThrow(new RuntimeException("API unavailable"));

            WebSocketSession session1 = mock(WebSocketSession.class);
            when(session1.getId()).thenReturn("refresh-err-1");
            when(session1.isOpen()).thenReturn(true);

            WebSocketSession session2 = mock(WebSocketSession.class);
            when(session2.getId()).thenReturn("refresh-err-2");
            when(session2.isOpen()).thenReturn(true);

            refreshHandler.afterConnectionEstablished(session1);
            refreshHandler.afterConnectionEstablished(session2);

            String refreshRequest = mapper.writeValueAsString(Map.of("action", "refresh"));
            refreshHandler.handleMessage(session1, new TextMessage(refreshRequest));

            // session1: welcome + status + error
            verify(session1, atLeast(3)).sendMessage(any(TextMessage.class));
            // session2: only welcome (error is not broadcast)
            verify(session2, times(1)).sendMessage(any(TextMessage.class));
        }
    }

    // =========================================================================
    // EDGE CASE TESTS
    // =========================================================================

    @Nested
    class EdgeCaseTests {

        @Test
        void testBroadcastSkipsClosedSessions() throws Exception {
            WebSocketSession openSession = mock(WebSocketSession.class);
            when(openSession.getId()).thenReturn("open-session");
            when(openSession.isOpen()).thenReturn(true);

            WebSocketSession closedSession = mock(WebSocketSession.class);
            when(closedSession.getId()).thenReturn("closed-session");
            when(closedSession.isOpen()).thenReturn(false);

            handler.afterConnectionEstablished(openSession);
            handler.afterConnectionEstablished(closedSession);

            handler.broadcastMessage(Map.of("type", "test"));

            // open session: welcome + broadcast
            verify(openSession, times(2)).sendMessage(any(TextMessage.class));
            // closed session: welcome only (was open during connect), no broadcast
            verify(closedSession, times(1)).sendMessage(any(TextMessage.class));
        }

        @Test
        void testBroadcastHandlesIOException() throws Exception {
            WebSocketSession workingSession = mock(WebSocketSession.class);
            when(workingSession.getId()).thenReturn("working-session");
            when(workingSession.isOpen()).thenReturn(true);

            handler.afterConnectionEstablished(workingSession);

            // broadcastMessage should not throw even if serialization works
            assertDoesNotThrow(() ->
                    handler.broadcastMessage(Map.of("type", "test")));
        }

        @Test
        void testMultipleConnectionsAndDisconnections() throws Exception {
            WebSocketSession s1 = mock(WebSocketSession.class);
            when(s1.getId()).thenReturn("s1");
            when(s1.isOpen()).thenReturn(true);

            WebSocketSession s2 = mock(WebSocketSession.class);
            when(s2.getId()).thenReturn("s2");
            when(s2.isOpen()).thenReturn(true);

            WebSocketSession s3 = mock(WebSocketSession.class);
            when(s3.getId()).thenReturn("s3");
            when(s3.isOpen()).thenReturn(true);

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);
            handler.afterConnectionEstablished(s3);
            assertEquals(3, handler.getConnectedClientCount());

            when(s2.isOpen()).thenReturn(false);
            handler.afterConnectionClosed(s2, CloseStatus.NORMAL);
            assertEquals(2, handler.getConnectedClientCount());

            when(s1.isOpen()).thenReturn(false);
            handler.afterConnectionClosed(s1, CloseStatus.GOING_AWAY);
            assertEquals(1, handler.getConnectedClientCount());
        }

        @Test
        void testEmptyPayloadAction() throws Exception {
            WebSocketSession session = mock(WebSocketSession.class);
            when(session.getId()).thenReturn("empty-action");
            when(session.isOpen()).thenReturn(true);

            handler.afterConnectionEstablished(session);

            // Action missing from payload
            String payload = "{}";
            handler.handleMessage(session, new TextMessage(payload));

            // Should send error for unknown action (empty string)
            verify(session, atLeast(2)).sendMessage(any(TextMessage.class));
        }

        @Test
        void testGetConnectedClientCountExcludesClosedSessions() throws Exception {
            WebSocketSession s1 = mock(WebSocketSession.class);
            when(s1.getId()).thenReturn("s1");
            when(s1.isOpen()).thenReturn(true);

            WebSocketSession s2 = mock(WebSocketSession.class);
            when(s2.getId()).thenReturn("s2");
            when(s2.isOpen()).thenReturn(true);

            handler.afterConnectionEstablished(s1);
            handler.afterConnectionEstablished(s2);
            assertEquals(2, handler.getConnectedClientCount());

            // Mark s1 as closed without calling afterConnectionClosed
            when(s1.isOpen()).thenReturn(false);
            assertEquals(1, handler.getConnectedClientCount());
        }
    }
}
