package com.devin.dashboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reactive HTTP client for enterprise-scoped Devin API endpoints.
 * Extends {@link BaseApiClient} for common retry/error handling logic.
 *
 * <p>Enterprise endpoints use the base URL {@code https://api.devin.ai/v3/enterprise/...}
 * and require an enterprise service user token.</p>
 */
@Slf4j
@Service
public class DevinApiClient extends BaseApiClient {

    /**
     * Constructs the enterprise API client, injecting the enterprise service token
     * from {@code DEVIN_ENTERPRISE_SERVICE_TOKEN}.
     *
     * @param enterpriseToken the Devin enterprise service user token
     * @throws IllegalStateException if the service token is not configured
     */
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
            log.warn("Enterprise service token appears too short ({} chars). "
                    + "Verify that the correct service user token is configured.", token.length());
        }
        return token;
    }
}
