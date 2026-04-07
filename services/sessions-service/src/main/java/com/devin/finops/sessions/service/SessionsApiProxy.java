package com.devin.finops.sessions.service;

import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import com.devin.common.service.BaseApiClient;
import com.devin.finops.sessions.config.SessionsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Proxy for write operations to the Devin API (sessions, schedules).
 * Uses the enterprise token for enterprise-scoped endpoints and
 * org token for organization-scoped endpoints.
 */
@Slf4j
@Service
public class SessionsApiProxy {

    private final EndpointLoader endpointLoader;
    private final EnterpriseClient enterpriseClient;
    private final OrgClient orgClient;

    public SessionsApiProxy(EndpointLoader endpointLoader,
                            SessionsProperties properties) {
        this.endpointLoader = endpointLoader;
        this.enterpriseClient = new EnterpriseClient(properties.getEnterpriseToken());
        this.orgClient = new OrgClient(properties.getOrgToken());
    }

    public Mono<String> getSession(String orgId, String sessionId) {
        return executeOrgEndpoint("get_session",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> getSessionMessages(String orgId, String sessionId) {
        return executeOrgEndpoint("get_session_messages",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> createSession(String orgId, Object body) {
        return executeOrgEndpoint("create_session",
                Map.of("org_id", orgId), body);
    }

    public Mono<String> sendMessage(String orgId, String sessionId, Object body) {
        return executeOrgEndpoint("send_session_message",
                Map.of("org_id", orgId, "session_id", sessionId), body);
    }

    public Mono<String> archiveSession(String orgId, String sessionId) {
        return executeOrgEndpoint("archive_session",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> terminateSession(String orgId, String sessionId) {
        return executeOrgEndpoint("terminate_session",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> deleteSession(String orgId, String sessionId) {
        return executeOrgEndpoint("delete_session",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> listSessionTags(String orgId, String sessionId) {
        return executeOrgEndpoint("list_session_tags",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> updateSessionTags(String orgId, String sessionId, Object body) {
        return executeOrgEndpoint("update_session_tags",
                Map.of("org_id", orgId, "session_id", sessionId), body);
    }

    public Mono<String> getSessionInsights(String orgId, String sessionId) {
        return executeOrgEndpoint("get_session_insights",
                Map.of("org_id", orgId, "session_id", sessionId), null);
    }

    public Mono<String> createSchedule(String orgId, Object body) {
        return executeOrgEndpoint("create_schedule",
                Map.of("org_id", orgId), body);
    }

    private Mono<String> executeOrgEndpoint(String endpointName,
                                            Map<String, String> pathParams,
                                            Object body) {
        EndpointDefinition endpoint = endpointLoader.findByName(endpointName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endpoint not found: " + endpointName));

        if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
            return orgClient.get(endpoint, pathParams)
                    .reduce("", (a, b) -> a + b);
        }
        return orgClient.execute(endpoint, pathParams, body);
    }

    private static class EnterpriseClient extends BaseApiClient {
        EnterpriseClient(String token) {
            super(token);
        }

        @Override
        protected String getScopeLabel() {
            return "Enterprise";
        }
    }

    private static class OrgClient extends BaseApiClient {
        OrgClient(String token) {
            super(token);
        }

        @Override
        protected String getScopeLabel() {
            return "Organization";
        }
    }
}
