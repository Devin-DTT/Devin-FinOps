package com.devin.collector.service;

import com.devin.common.service.BaseApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Reactive HTTP client for enterprise-scoped Devin API endpoints.
 */
@Slf4j
@Service
public class DevinApiClient extends BaseApiClient {

    public DevinApiClient(
            @Value("${DEVIN_ENTERPRISE_SERVICE_TOKEN:}") String enterpriseToken,
            @Value("${DEVIN_SERVICE_TOKEN:}") String legacyToken) {
        super(resolveToken(enterpriseToken, legacyToken));
    }

    @Override
    protected String getScopeLabel() {
        return "Enterprise";
    }

    private static String resolveToken(String enterpriseToken,
                                       String legacyToken) {
        String serviceToken =
                (enterpriseToken != null && !enterpriseToken.isBlank())
                ? enterpriseToken : legacyToken;
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ENTERPRISE_SERVICE_TOKEN (or DEVIN_SERVICE_TOKEN) "
                    + "is not configured.");
        }
        if (enterpriseToken == null || enterpriseToken.isBlank()) {
            log.warn("DEVIN_ENTERPRISE_SERVICE_TOKEN is not set; "
                    + "falling back to DEVIN_SERVICE_TOKEN.");
        }
        if (serviceToken.length() < 20) {
            log.warn("Enterprise service token appears too short ({} chars).",
                    serviceToken.length());
        }
        return serviceToken;
    }
}
