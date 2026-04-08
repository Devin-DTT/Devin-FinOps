package com.devin.collector.service;

import com.devin.common.service.BaseApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reactive HTTP client for enterprise-scoped Devin API endpoints.
 *
 * <p>Requires {@code DEVIN_ENTERPRISE_SERVICE_TOKEN} to be configured.</p>
 */
@Slf4j
@Service
public class DevinApiClient extends BaseApiClient {

    public DevinApiClient(
            @Value("${DEVIN_ENTERPRISE_SERVICE_TOKEN:}") String enterpriseToken) {
        super(validateToken(enterpriseToken));
    }

    @Override
    protected String getScopeLabel() {
        return "Enterprise";
    }

    private static String validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ENTERPRISE_SERVICE_TOKEN is not configured. "
                    + "Provision a Devin enterprise service user at "
                    + "https://app.devin.ai -> Enterprise Settings -> Service Users "
                    + "and set its token as DEVIN_ENTERPRISE_SERVICE_TOKEN.");
        }
        if (token.length() < 20) {
            log.warn("Enterprise service token appears too short ({} chars).",
                    token.length());
        }
        return token;
    }
}
