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
     * Constructs the enterprise API client, injecting the enterprise service user token
     * from {@code DEVIN_ENTERPRISE_SERVICE_TOKEN} (falls back to {@code DEVIN_SERVICE_TOKEN}
     * for backward compatibility).
     *
     * @param enterpriseToken the Devin enterprise service user token
     * @param legacyToken     legacy token (DEVIN_SERVICE_TOKEN) used as fallback
     * @throws IllegalStateException if no token is configured
     */
    public DevinApiClient(
            @Value("${DEVIN_ENTERPRISE_SERVICE_TOKEN:}") String enterpriseToken,
            @Value("${DEVIN_SERVICE_TOKEN:}") String legacyToken) {
        super(resolveToken(enterpriseToken, legacyToken));
    }

    @Override
    protected String getScopeLabel() {
        return "Enterprise";
    }

    private static String resolveToken(String enterpriseToken, String legacyToken) {
        String serviceToken = (enterpriseToken != null && !enterpriseToken.isBlank())
                ? enterpriseToken : legacyToken;
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ENTERPRISE_SERVICE_TOKEN (or DEVIN_SERVICE_TOKEN) is not configured. "
                    + "Provision a Devin enterprise service user and set its token as DEVIN_ENTERPRISE_SERVICE_TOKEN.");
        }
        if (enterpriseToken == null || enterpriseToken.isBlank()) {
            log.warn("DEVIN_ENTERPRISE_SERVICE_TOKEN is not set; falling back to DEVIN_SERVICE_TOKEN. "
                    + "Please migrate to DEVIN_ENTERPRISE_SERVICE_TOKEN.");
        }
        if (serviceToken.length() < 20) {
            log.warn("Enterprise service token appears too short ({} chars). "
                    + "Verify that the correct service user token is configured.", serviceToken.length());
        }
        return serviceToken;
    }
}
