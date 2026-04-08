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
 * <p>Requires {@code DEVIN_ORG_SERVICE_TOKEN} to be configured.</p>
 */
@Slf4j
@Service
public class OrgApiClient extends BaseApiClient {

    @Getter
    private final Optional<String> orgId;

    @Getter
    private final boolean available;

    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String orgToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {
        super(sanitizeToken(orgToken));

        this.available = orgToken != null && !orgToken.isBlank();

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

    private static String sanitizeToken(String token) {
        if (token == null || token.isBlank()) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN is not configured. "
                    + "Organization-scoped endpoints will be skipped. "
                    + "Provision an org service user at "
                    + "https://app.devin.ai -> Organization Settings -> Service Users.");
            return "NOT_CONFIGURED";
        }
        if (token.length() < 20) {
            log.warn("Organization service token appears too short ({} chars).",
                    token.length());
        }
        return token;
    }
}
