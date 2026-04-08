package com.devin.collector.service;

import com.devin.common.service.BaseApiClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reactive HTTP client for organization-scoped Devin API endpoints.
 *
 * <p>Falls back to the legacy {@code DEVIN_ORG_SERVICE_TOKEN} for backward
 * compatibility.</p>
 */
@Slf4j
@Service
public class OrgApiClient extends BaseApiClient {

    @Getter
    private final Optional<String> orgId;

    @Getter
    private final boolean available;

    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_USER_TOKEN:}") String serviceUserToken,
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String legacyToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {
        super(sanitizeToken(serviceUserToken, legacyToken));

        String resolvedToken = (serviceUserToken != null && !serviceUserToken.isBlank())
                ? serviceUserToken : legacyToken;
        this.available = resolvedToken != null && !resolvedToken.isBlank();

        if (orgId != null && !orgId.isBlank()) {
            this.orgId = Optional.of(orgId);
            log.info("DEVIN_ORG_ID configured: {}. Single-org mode.", orgId);
        } else {
            this.orgId = Optional.empty();
            log.info("DEVIN_ORG_ID not configured. Multi-org discovery mode.");
        }
    }

    @Override
    protected String getScopeLabel() {
        return "Organization";
    }

    private static String sanitizeToken(String serviceUserToken, String legacyToken) {
        String token = (serviceUserToken != null && !serviceUserToken.isBlank())
                ? serviceUserToken : legacyToken;
        if (token == null || token.isBlank()) {
            log.warn("DEVIN_ORG_SERVICE_USER_TOKEN is not configured. "
                    + "Organization-scoped endpoints will be skipped. "
                    + "Provision an org service user at "
                    + "https://app.devin.ai -> Organization Settings -> Service Users.");
            return "NOT_CONFIGURED";
        }
        if (serviceUserToken == null || serviceUserToken.isBlank()) {
            log.warn("Using legacy DEVIN_ORG_SERVICE_TOKEN. "
                    + "Please migrate to DEVIN_ORG_SERVICE_USER_TOKEN.");
        }
        if (token.length() < 20) {
            log.warn("Organization service user token appears too short ({} chars).",
                    token.length());
        }
        return token;
    }
}
