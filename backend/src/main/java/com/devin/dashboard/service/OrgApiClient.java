package com.devin.dashboard.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reactive HTTP client for organization-scoped Devin API endpoints.
 * Extends {@link BaseApiClient} for common retry/error handling logic.
 *
 * <p>Organization endpoints use the base URL {@code https://api.devin.ai/v3/organizations/{org_id}/...}
 * and require a separate service user token from enterprise endpoints.</p>
 *
 * <p>The organization ID ({@code DEVIN_ORG_ID}) is optional. When not configured,
 * the backend will auto-discover organizations via the enterprise {@code list_organizations}
 * endpoint and iterate over each one.</p>
 *
 * <p>The org service user token ({@code DEVIN_ORG_SERVICE_USER_TOKEN}) is also optional.
 * When not configured, organization-scoped endpoints are skipped and only
 * enterprise-scoped data is collected.</p>
 */
@Slf4j
@Service
public class OrgApiClient extends BaseApiClient {

    @Getter
    private final Optional<String> orgId;

    @Getter
    private final boolean available;

    /**
     * Constructs the organization API client, injecting the org service user token
     * and (optionally) the org ID from environment variables.
     *
     * <p>Falls back to the legacy {@code DEVIN_ORG_SERVICE_TOKEN} for backward
     * compatibility.</p>
     *
     * @param serviceUserToken the organization service user token (DEVIN_ORG_SERVICE_USER_TOKEN)
     * @param legacyToken      legacy name (DEVIN_ORG_SERVICE_TOKEN), deprecated
     * @param orgId            the organization ID (DEVIN_ORG_ID), optional
     */
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
            log.info("DEVIN_ORG_ID is configured: {}. Single-org mode enabled.", orgId);
        } else {
            this.orgId = Optional.empty();
            log.info("DEVIN_ORG_ID is not configured. Multi-org auto-discovery mode enabled.");
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
            log.warn("Organization service user token appears too short ({} chars). "
                    + "Verify that the correct service user token is configured.",
                    token.length());
        }
        return token;
    }
}
