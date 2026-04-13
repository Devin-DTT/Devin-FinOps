package com.devin.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;

/**
 * Base class for domain cache services that read endpoint data from Redis.
 * Provides the common {@code readKey()} pattern used by all domain services.
 */
@Slf4j
public abstract class AbstractRedisCacheService {

    protected final StringRedisTemplate redisTemplate;
    protected final ObjectMapper mapper;
    private final String redisKeyPrefix;

    protected AbstractRedisCacheService(StringRedisTemplate redisTemplate,
                                        ObjectMapper mapper,
                                        String redisKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.mapper = mapper;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    protected Optional<JsonNode> readKey(String endpointName) {
        try {
            String key = redisKeyPrefix + endpointName;
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null && !raw.isEmpty()) {
                return Optional.of(mapper.readTree(raw));
            }
        } catch (Exception e) {
            log.warn("Failed to read Redis key for {}: {}", endpointName, e.getMessage());
        }
        return Optional.empty();
    }
}
