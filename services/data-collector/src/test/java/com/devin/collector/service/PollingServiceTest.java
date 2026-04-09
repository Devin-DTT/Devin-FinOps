package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PollingService.
 * Verifies endpoint grouping, interval resolution, and polling logic.
 */
@ExtendWith(MockitoExtension.class)
class PollingServiceTest {

    @Mock
    private DevinApiClient devinApiClient;

    @Mock
    private OrgApiClient orgApiClient;

    @Mock
    private EndpointLoader endpointLoader;

    @Mock
    private RedisSnapshotService snapshotService;

    @Mock
    private OrgDiscoveryService orgDiscoveryService;

    @Mock
    private SessionDiscoveryService sessionDiscoveryService;

    private CollectorProperties properties;
    private PollingService pollingService;

    @BeforeEach
    void setUp() {
        properties = new CollectorProperties();
        properties.setSessionsPollingSeconds(5);
        properties.setMetricsPollingSeconds(30);
        properties.setBillingPollingSeconds(60);
        properties.setAdminPollingSeconds(300);

        pollingService = new PollingService(
                devinApiClient, orgApiClient, endpointLoader,
                snapshotService, orgDiscoveryService,
                sessionDiscoveryService, properties);
    }

    @Test
    void resolveInterval_sessionsEndpoint_returns5s() {
        EndpointDefinition ep = createEndpoint("list_sessions", "enterprise");
        long interval = pollingService.resolveInterval(ep);
        assertThat(interval).isEqualTo(5);
    }

    @Test
    void resolveInterval_metricsEndpoint_returns30s() {
        EndpointDefinition ep = createEndpoint("get_dau_metrics", "enterprise");
        long interval = pollingService.resolveInterval(ep);
        assertThat(interval).isEqualTo(30);
    }

    @Test
    void resolveInterval_billingEndpoint_returns60s() {
        EndpointDefinition ep = createEndpoint("list_billing_cycles", "enterprise");
        long interval = pollingService.resolveInterval(ep);
        assertThat(interval).isEqualTo(60);
    }

    @Test
    void resolveInterval_adminEndpoint_returns300s() {
        EndpointDefinition ep = createEndpoint("list_users", "enterprise");
        long interval = pollingService.resolveInterval(ep);
        assertThat(interval).isEqualTo(300);
    }

    @Test
    void groupByInterval_groupsCorrectly() {
        List<EndpointDefinition> endpoints = List.of(
                createEndpoint("list_sessions", "enterprise"),
                createEndpoint("list_enterprise_sessions", "enterprise"),
                createEndpoint("get_dau_metrics", "enterprise"),
                createEndpoint("list_billing_cycles", "enterprise"),
                createEndpoint("list_users", "enterprise")
        );

        Map<Long, List<EndpointDefinition>> grouped =
                pollingService.groupByInterval(endpoints);

        assertThat(grouped).hasSize(4);
        assertThat(grouped.get(5L)).hasSize(2);   // sessions
        assertThat(grouped.get(30L)).hasSize(1);   // metrics
        assertThat(grouped.get(60L)).hasSize(1);   // billing
        assertThat(grouped.get(300L)).hasSize(1);  // admin
    }

    @Test
    void pollEndpoints_enterpriseEndpoint_callsDevinApiClient() {
        EndpointDefinition ep = createEndpoint("list_users", "enterprise");
        when(orgDiscoveryService.getCachedOrgIds())
                .thenReturn(Collections.emptyList());
        when(devinApiClient.get(any(), anyMap(), anyMap()))
                .thenReturn(Flux.just("{\"users\":[]}"));

        pollingService.pollEndpoints(List.of(ep));

        verify(devinApiClient).get(eq(ep), anyMap(), anyMap());
    }

    @Test
    void pollEndpoints_orgEndpoint_noOrgIds_skips() {
        EndpointDefinition ep = createEndpoint("list_sessions", "organization");
        when(orgDiscoveryService.getCachedOrgIds())
                .thenReturn(Collections.emptyList());

        pollingService.pollEndpoints(List.of(ep));

        verify(devinApiClient, never()).get(any(), anyMap(), anyMap());
        verify(orgApiClient, never()).get(any(), anyMap());
    }

    @Test
    void pollEndpoints_orgEndpoint_withOrgIds_pollsEachOrg() {
        EndpointDefinition ep = createEndpoint("list_sessions", "organization");
        when(orgDiscoveryService.getCachedOrgIds())
                .thenReturn(List.of("org_1", "org_2"));
        when(orgApiClient.isAvailable()).thenReturn(true);
        when(orgDiscoveryService.isMultiOrg()).thenReturn(true);
        when(orgApiClient.get(any(), anyMap()))
                .thenReturn(Flux.just("{\"sessions\":[]}"));

        pollingService.pollEndpoints(List.of(ep));

        verify(orgApiClient, times(2)).get(eq(ep), anyMap());
    }

    @Test
    void pollEndpoints_metricsEndpoint_addsTimeParams() {
        EndpointDefinition ep = createEndpoint("get_dau_metrics", "enterprise");
        when(orgDiscoveryService.getCachedOrgIds())
                .thenReturn(Collections.emptyList());
        when(devinApiClient.get(any(), anyMap(), anyMap()))
                .thenReturn(Flux.just("{\"count\":42}"));

        pollingService.pollEndpoints(List.of(ep));

        verify(devinApiClient).get(eq(ep), eq(Collections.emptyMap()),
                argThat(params ->
                        params.containsKey("time_before")
                        && params.containsKey("time_after")));
    }

    private EndpointDefinition createEndpoint(String name, String scope) {
        EndpointDefinition ep = new EndpointDefinition();
        ep.setName(name);
        ep.setScope(scope);
        ep.setPath("/v3/" + scope + "/" + name);
        ep.setMethod("GET");
        ep.setBaseUrl("https://api.devin.ai");
        return ep;
    }
}
