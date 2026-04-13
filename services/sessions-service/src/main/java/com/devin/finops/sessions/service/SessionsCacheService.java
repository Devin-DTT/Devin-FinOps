package com.devin.finops.sessions.service;

import com.devin.common.service.AbstractRedisCacheService;
import com.devin.finops.sessions.config.SessionsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads session data cached by the data-collector from Redis.
 */
@Service
public class SessionsCacheService extends AbstractRedisCacheService {

    public SessionsCacheService(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                SessionsProperties properties) {
        super(redisTemplate, objectMapper, properties.getRedisKeyPrefix());
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
}
