package com.devin.collector.service;

import com.devin.common.service.BaseApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reactive HTTP client for enterprise-scoped Devin API endpoints.
 *
 * <p>Falls back to the legacy {@code DEVIN_ENTERPRISE_SERVICE_TOKEN} for backward
 * compatibility, but logs a deprecation warning.</p>
 */
@Slf4j
@Service
public class DevinApiClient extends BaseApiClient {

    public DevinApiClient(
            @Value("${DEVIN_ENTERPRISE_SERVICE_USER_TOKEN:}") String serviceUserToken,
            @Value("${DEVIN_ENTERPRISE_SERVICE_TOKEN:}") String legacyToken) {
        super(resolveToken(serviceUserToken, legacyToken));
    }

    @Override
    protected String getScopeLabel() {
        return "Enterprise";
    }

    private static String resolveToken(String serviceUserToken,
                                       String legacyToken) {
        String token =
                (serviceUserToken != null && !serviceUserToken.isBlank())
                ? serviceUserToken : legacyToken;
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ENTERPRISE_SERVICE_USER_TOKEN is not configured. "
                    + "Provision a Devin enterprise service user at "
                    + "https://app.devin.ai -> Enterprise Settings -> Service Users "
                    + "and set its token as DEVIN_ENTERPRISE_SERVICE_USER_TOKEN.");
        }
        if (serviceUserToken == null || serviceUserToken.isBlank()) {
            log.warn("Using legacy DEVIN_ENTERPRISE_SERVICE_TOKEN. "
                    + "Please migrate to DEVIN_ENTERPRISE_SERVICE_USER_TOKEN.");
        }
        if (token.length() < 20) {
            log.warn("Enterprise service user token appears too short ({} chars).",
                    token.length());
        }
        return token;
    }
}
