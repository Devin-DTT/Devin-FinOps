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
 * and require a separate token from enterprise endpoints.</p>
 *
 * <p>The organization ID ({@code DEVIN_ORG_ID}) is optional. When not configured,
 * the backend will auto-discover organizations via the enterprise {@code list_organizations}
 * endpoint and iterate over each one.</p>
 */
@Slf4j
@Service
public class OrgApiClient extends BaseApiClient {

    /**
     * Optional org ID. When present, acts as a single-org fallback
     * (skips calling list_organizations). When absent, the handler
     * will discover orgs dynamically.
     */
    @Getter
    private final Optional<String> orgId;

    /**
     * Constructs the organization API client, injecting the org service user token
     * and (optionally) the org ID from environment variables.
     *
     * @param orgServiceToken the organization service user token (DEVIN_ORG_SERVICE_TOKEN)
     * @param orgId           the organization ID (DEVIN_ORG_ID), optional
     * @throws IllegalStateException if the token is not configured
     */
    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String orgServiceToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {
        super(validateToken(orgServiceToken));

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

    private static String validateToken(String orgServiceToken) {
        if (orgServiceToken == null || orgServiceToken.isBlank()) {
            throw new IllegalStateException(
                    "DEVIN_ORG_SERVICE_TOKEN is not configured. "
                    + "Provision a Devin organization service user and set its token as DEVIN_ORG_SERVICE_TOKEN.");
        }
        if (orgServiceToken.length() < 20) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN appears too short ({} chars). "
                    + "Verify that the correct organization service user token is configured.",
                    orgServiceToken.length());
        }
        return orgServiceToken;
    }
}
