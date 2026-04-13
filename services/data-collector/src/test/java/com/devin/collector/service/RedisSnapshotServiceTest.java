package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * Unit tests for RedisSnapshotService.
 * Verifies caching and Pub/Sub publishing logic.
 */
@ExtendWith(MockitoExtension.class)
class RedisSnapshotServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CollectorProperties properties;
    private RedisSnapshotService service;

    @BeforeEach
    void setUp() {
        properties = new CollectorProperties();
        properties.setRedisKeyPrefix("finops:endpoint:");
        properties.setRedisKeyTtlSeconds(600);
        properties.setRedisPubsubChannel("finops:updates");

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new RedisSnapshotService(redisTemplate, properties, new ObjectMapper());
    }

    @Test
    void cacheEndpointData_setsKeyWithTTL() {
        service.cacheEndpointData("list_sessions", "{\"sessions\":[]}");

        verify(valueOperations).set(
                eq("finops:endpoint:list_sessions"),
                eq("{\"sessions\":[]}"),
                eq(Duration.ofSeconds(600)));
    }

    @Test
    void cacheEndpointData_nullData_doesNotCache() {
        service.cacheEndpointData("list_sessions", null);

        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void cacheEndpointData_emptyData_doesNotCache() {
        service.cacheEndpointData("list_sessions", "");

        verify(valueOperations, never()).set(anyString(), anyString(), any());
    }

    @Test
    void publishUpdate_sendsCorrectFormat() {
        service.publishUpdate("list_sessions", "{\"sessions\":[]}", "org_123");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("finops:updates"), captor.capture());

        String message = captor.getValue();
        assertThat(message).contains("\"type\":\"data\"");
        assertThat(message).contains("\"endpoint\":\"list_sessions\"");
        assertThat(message).contains("\"org_id\":\"org_123\"");
        assertThat(message).contains("\"timestamp\":");
        assertThat(message).contains("\"data\":");
    }

    @Test
    void publishUpdate_withoutOrgId_excludesOrgField() {
        service.publishUpdate("list_billing_cycles", "{\"cycles\":[]}", null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("finops:updates"), captor.capture());

        String message = captor.getValue();
        assertThat(message).contains("\"type\":\"data\"");
        assertThat(message).contains("\"endpoint\":\"list_billing_cycles\"");
        assertThat(message).doesNotContain("org_id");
    }

    @Test
    void publishUpdate_nullData_setsDataNull() {
        service.publishUpdate("list_sessions", null, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("finops:updates"), captor.capture());

        String message = captor.getValue();
        assertThat(message).contains("\"data\":null");
    }
}
