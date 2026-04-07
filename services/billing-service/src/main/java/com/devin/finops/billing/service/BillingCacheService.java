package com.devin.finops.billing.service;

import com.devin.finops.billing.config.BillingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads billing/consumption data cached by the data-collector from Redis.
 */
@Slf4j
@Service
public class BillingCacheService {

    private final StringRedisTemplate redisTemplate;
    private final BillingProperties properties;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public BillingCacheService(StringRedisTemplate redisTemplate,
                               BillingProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public Optional<JsonNode> getBillingCycles() {
        return readKey("list_billing_cycles");
    }

    public Optional<JsonNode> getDailyConsumption() {
        return readKey("get_daily_consumption");
    }

    public Optional<JsonNode> getAcuLimits() {
        return readKey("get_acu_limits");
    }

    public Optional<JsonNode> getOrgGroupLimits() {
        return readKey("get_org_group_limits");
    }

    /**
     * Reads user count from Redis (cached by data-collector from list_users endpoint).
     * Used for FinOps KPI calculations (ACU per user).
     */
    public int getUserCount() {
        try {
            String key = properties.getRedisKeyPrefix() + "list_users";
            String raw = redisTemplate.opsForValue().get(key);
            if (raw != null && !raw.isEmpty()) {
                JsonNode node = MAPPER.readTree(raw);
                if (node.has("total")) {
                    return node.get("total").asInt(0);
                }
                if (node.has("items") && node.get("items").isArray()) {
                    return node.get("items").size();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read user count from Redis: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Read a Redis key directly (returns null if not found).
     * Used by BillingController for KPI calculations.
     */
    public JsonNode readKeyDirect(String endpointName) {
        return readKey(endpointName).orElse(null);
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
