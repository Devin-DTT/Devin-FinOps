package com.devin.finops.admin.service;

import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import com.devin.common.service.BaseApiClient;
import com.devin.finops.admin.config.AdminProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;

/**
 * Proxy for admin operations to the Devin API.
 * Handles both enterprise-scoped and organization-scoped endpoints.
 */
@Slf4j
@Service
public class AdminApiProxy {

    private final EndpointLoader endpointLoader;
    private final EnterpriseClient enterpriseClient;
    private final OrgClient orgClient;

    public AdminApiProxy(EndpointLoader endpointLoader,
                         AdminProperties properties) {
        this.endpointLoader = endpointLoader;
        this.enterpriseClient = new EnterpriseClient(properties.getEnterpriseToken());
        this.orgClient = new OrgClient(properties.getOrgToken());
    }

    // --- IDP Groups ---
    public Mono<String> createIdpGroups(Object body) {
        return executeEnterprise("create_idp_groups", Collections.emptyMap(), body);
    }

    public Mono<String> deleteIdpGroup(String name) {
        return executeEnterprise("delete_idp_group", Map.of("idp_group_name", name), null);
    }

    // --- Knowledge ---
    public Mono<String> createKnowledge(String orgId, Object body) {
        return executeOrg("create_knowledge", Map.of("org_id", orgId), body);
    }

    public Mono<String> createEnterpriseKnowledge(Object body) {
        return executeEnterprise("create_enterprise_knowledge", Collections.emptyMap(), body);
    }

    // --- Playbooks ---
    public Mono<String> createPlaybook(String orgId, Object body) {
        return executeOrg("create_playbook", Map.of("org_id", orgId), body);
    }

    // --- Secrets (always proxy, never cache) ---
    public Mono<String> listSecrets(String orgId) {
        return executeOrg("list_secrets", Map.of("org_id", orgId), null);
    }

    public Mono<String> createSecret(String orgId, Object body) {
        return executeOrg("create_secret", Map.of("org_id", orgId), body);
    }

    // --- Git Permissions ---
    public Mono<String> createGitPermissions(Object body) {
        return executeEnterprise("create_git_permissions", Collections.emptyMap(), body);
    }

    // --- IP Access List ---
    public Mono<String> getIpAccessList() {
        return executeEnterprise("get_ip_access_list", Collections.emptyMap(), null);
    }

    // --- Audit Logs (always proxy, never cache) ---
    public Mono<String> listEnterpriseAuditLogs() {
        return executeEnterprise("list_enterprise_audit_logs", Collections.emptyMap(), null);
    }

    public Mono<String> listOrgAuditLogs(String orgId) {
        return executeEnterprise("list_enterprise_org_audit_logs",
                Map.of("org_id", orgId), null);
    }

    // --- Guardrails ---
    public Mono<String> getGuardrailViolations() {
        // Placeholder - endpoint may not exist yet in endpoints.yaml
        log.debug("Guardrails violations endpoint not yet available");
        return Mono.just("{}");
    }

    private Mono<String> executeEnterprise(String endpointName,
                                           Map<String, String> pathParams,
                                           Object body) {
        EndpointDefinition endpoint = endpointLoader.findByName(endpointName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endpoint not found: " + endpointName));

        if ("GET".equalsIgnoreCase(endpoint.getMethod())) {
            return enterpriseClient.get(endpoint, pathParams)
                    .reduce("", (a, b) -> a + b);
        }
        return enterpriseClient.execute(endpoint, pathParams, body);
    }

    private Mono<String> executeOrg(String endpointName,
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
