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
 *
 * <p>The org service token ({@code DEVIN_ORG_SERVICE_TOKEN}) is also optional.
 * When not configured, organization-scoped endpoints are skipped and only
 * enterprise-scoped data is collected.</p>
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
     * Whether the org service token was provided and this client can
     * actually make API calls.  When the token is missing the client
     * is still created (Spring needs the bean) but all HTTP calls
     * should be skipped by the caller.
     */
    @Getter
    private final boolean available;

    /**
     * Constructs the organization API client, injecting the org service user token
     * and (optionally) the org ID from environment variables.
     *
     * @param orgServiceToken the organization service user token (DEVIN_ORG_SERVICE_TOKEN)
     * @param orgId           the organization ID (DEVIN_ORG_ID), optional
     */
    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String orgServiceToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {
        super(sanitizeToken(orgServiceToken));

        this.available = orgServiceToken != null && !orgServiceToken.isBlank();

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

    private static String sanitizeToken(String orgServiceToken) {
        if (orgServiceToken == null || orgServiceToken.isBlank()) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN is not configured. "
                    + "Organization-scoped endpoints will be skipped. "
                    + "Set DEVIN_ORG_SERVICE_TOKEN to enable them.");
            return "NOT_CONFIGURED";
        }
        if (orgServiceToken.length() < 20) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN appears too short ({} chars). "
                    + "Verify that the correct organization service user token is configured.",
                    orgServiceToken.length());
        }
        return orgServiceToken;
    }
}
