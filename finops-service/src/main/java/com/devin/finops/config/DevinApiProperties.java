package com.devin.finops.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Devin Enterprise API client.
 * Maps to the Python data_adapter.py configuration.
 */
@Component
@ConfigurationProperties(prefix = "devin.api")
public class DevinApiProperties {

    private String baseUrl = "https://api.devin.ai/v2/enterprise";
    private int pageSize = 100;
    private int maxRetries = 3;
    private long retryBaseDelayMs = 1000;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBaseDelayMs() {
        return retryBaseDelayMs;
    }

    public void setRetryBaseDelayMs(long retryBaseDelayMs) {
        this.retryBaseDelayMs = retryBaseDelayMs;
    }
}
