package com.devin.finops.sessions.service;

import com.devin.finops.sessions.config.SessionsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads session data cached by the data-collector from Redis.
 */
@Slf4j
@Service
public class SessionsCacheService {

    private final StringRedisTemplate redisTemplate;
    private final SessionsProperties properties;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public SessionsCacheService(StringRedisTemplate redisTemplate,
                                SessionsProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Get cached sessions list (organization-scoped).
     */
    public Optional<JsonNode> getSessionsList() {
        return readKey("list_sessions");
    }

    /**
     * Get cached enterprise sessions list.
     */
    public Optional<JsonNode> getEnterpriseSessionsList() {
        return readKey("list_enterprise_sessions");
    }

    /**
     * Get cached schedules list.
     */
    public Optional<JsonNode> getSchedulesList() {
        return readKey("list_schedules");
    }

    private Optional<JsonNode> readKey(String endpointName) {
        try {
            String key = properties.getRedisKeyPrefix() + endpointName;
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null && !raw.isEmpty()) {
                return Optional.of(MAPPER.readTree(raw));
            }
        } catch (Exception e) {
            log.warn("Failed to read Redis key for {}: {}", endpointName, e.getMessage());
        }
        return Optional.empty();
    }
}
