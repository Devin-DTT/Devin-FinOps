package com.devin.finops.service;

import com.devin.finops.config.DevinApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DevinApiService configuration and initialization.
 * Note: API calls require a running Devin API instance; these tests
 * verify service construction and configuration binding.
 */
class DevinApiServiceTest {

    private DevinApiService devinApiService;
    private DevinApiProperties apiProperties;

    @BeforeEach
    void setUp() {
        apiProperties = new DevinApiProperties();
        apiProperties.setBaseUrl("https://api.devin.ai/v2/enterprise");
        apiProperties.setPageSize(100);
        apiProperties.setMaxRetries(3);
        apiProperties.setRetryBaseDelayMs(1000);

        ObjectMapper objectMapper = new ObjectMapper();
        devinApiService = new DevinApiService(apiProperties, objectMapper);
    }

    @Test
    void testServiceCreation() {
        assertNotNull(devinApiService);
    }

    @Test
    void testApiPropertiesDefaults() {
        assertEquals("https://api.devin.ai/v2/enterprise", apiProperties.getBaseUrl());
        assertEquals(100, apiProperties.getPageSize());
        assertEquals(3, apiProperties.getMaxRetries());
        assertEquals(1000, apiProperties.getRetryBaseDelayMs());
    }

    @Test
    void testApiPropertiesCustomValues() {
        apiProperties.setBaseUrl("https://custom-api.example.com/v2");
        apiProperties.setPageSize(50);
        apiProperties.setMaxRetries(5);
        apiProperties.setRetryBaseDelayMs(2000);

        assertEquals("https://custom-api.example.com/v2", apiProperties.getBaseUrl());
        assertEquals(50, apiProperties.getPageSize());
        assertEquals(5, apiProperties.getMaxRetries());
        assertEquals(2000, apiProperties.getRetryBaseDelayMs());
    }
}
