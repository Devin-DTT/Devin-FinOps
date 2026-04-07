package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages endpoint data caching in Redis, replacing the old
 * ConcurrentHashMap + disk-based SnapshotService from the monolith.
 *
 * <p>Each endpoint response is stored as a Redis key with a configurable TTL.
 * After each poll, the result is also published to a Redis Pub/Sub channel
 * for real-time consumption by the websocket-service.</p>
 */
@Slf4j
@Service
public class RedisSnapshotService {

    private final StringRedisTemplate redisTemplate;
    private final CollectorProperties properties;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public RedisSnapshotService(StringRedisTemplate redisTemplate,
                                CollectorProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Caches the raw API response in Redis with a TTL.
     *
     * @param endpointName the cache key (may include org suffix)
     * @param rawData      the raw JSON string from the API
     */
    public void cacheEndpointData(String endpointName, String rawData) {
        try {
            if (rawData != null && !rawData.isEmpty()) {
                String key = properties.getRedisKeyPrefix() + endpointName;
                redisTemplate.opsForValue().set(key, rawData,
                        Duration.ofSeconds(properties.getRedisKeyTtlSeconds()));
                log.debug("Cached data for endpoint {} in Redis", endpointName);
            }
        } catch (Exception e) {
            log.warn("Failed to cache data for endpoint {}: {}",
                    endpointName, e.getMessage());
        }
    }

    /**
     * Publishes a data update message to the Redis Pub/Sub channel.
     *
     * @param endpointName the endpoint name
     * @param rawData      the raw JSON response
     * @param orgId        optional org ID (null for enterprise endpoints)
     */
    public void publishUpdate(String endpointName, String rawData,
                              String orgId) {
        try {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("type", "data");
            node.put("endpoint", endpointName);
            node.put("timestamp", System.currentTimeMillis());
            if (orgId != null && !orgId.isBlank()) {
                node.put("org_id", orgId);
            }
            if (rawData == null || rawData.isEmpty()) {
                node.putNull("data");
            } else {
                node.set("data", OBJECT_MAPPER.readTree(rawData));
            }
            String message = OBJECT_MAPPER.writeValueAsString(node);
            redisTemplate.convertAndSend(
                    properties.getRedisPubsubChannel(), message);
            log.debug("Published update for endpoint {} to Redis Pub/Sub",
                    endpointName);
        } catch (Exception e) {
            log.error("Failed to publish update for endpoint {}: {}",
                    endpointName, e.getMessage());
        }
    }
}
