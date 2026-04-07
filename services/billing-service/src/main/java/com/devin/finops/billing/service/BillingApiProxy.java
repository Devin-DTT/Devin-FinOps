package com.devin.finops.billing.service;

import com.devin.common.config.EndpointLoader;
import com.devin.common.model.EndpointDefinition;
import com.devin.common.service.BaseApiClient;
import com.devin.finops.billing.config.BillingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Proxy for write operations to the Devin API (ACU limits).
 */
@Slf4j
@Service
public class BillingApiProxy {

    private final EndpointLoader endpointLoader;
    private final EnterpriseClient enterpriseClient;

    public BillingApiProxy(EndpointLoader endpointLoader,
                           BillingProperties properties) {
        this.endpointLoader = endpointLoader;
        this.enterpriseClient = new EnterpriseClient(properties.getEnterpriseToken());
    }

    public Mono<String> setOrgAcuLimit(String orgId, Object body) {
        EndpointDefinition endpoint = endpointLoader.findByName("set_org_acu_limit")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endpoint not found: set_org_acu_limit"));
        return enterpriseClient.execute(endpoint, Map.of("org_id", orgId), body);
    }

    public Mono<String> deleteOrgAcuLimit(String orgId) {
        EndpointDefinition endpoint = endpointLoader.findByName("delete_org_acu_limit")
                .orElseThrow(() -> new IllegalArgumentException(
                        "Endpoint not found: delete_org_acu_limit"));
        return enterpriseClient.execute(endpoint, Map.of("org_id", orgId), null);
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
}
