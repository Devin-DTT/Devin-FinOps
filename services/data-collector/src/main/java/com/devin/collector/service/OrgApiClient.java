package com.devin.collector.service;

import com.devin.common.service.BaseApiClient;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reactive HTTP client for organization-scoped Devin API endpoints.
 */
@Slf4j
@Service
public class OrgApiClient extends BaseApiClient {

    @Getter
    private final Optional<String> orgId;

    @Getter
    private final boolean available;

    public OrgApiClient(
            @Value("${DEVIN_ORG_SERVICE_TOKEN:}") String orgServiceToken,
            @Value("${DEVIN_ORG_ID:}") String orgId) {
        super(sanitizeToken(orgServiceToken));

        this.available = orgServiceToken != null && !orgServiceToken.isBlank();

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

    private static String sanitizeToken(String orgServiceToken) {
        if (orgServiceToken == null || orgServiceToken.isBlank()) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN not configured. "
                    + "Org-scoped endpoints will use enterprise fallback.");
            return "NOT_CONFIGURED";
        }
        if (orgServiceToken.length() < 20) {
            log.warn("DEVIN_ORG_SERVICE_TOKEN appears too short ({} chars).",
                    orgServiceToken.length());
        }
        return orgServiceToken;
    }
}
