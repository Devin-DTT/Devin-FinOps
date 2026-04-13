package com.devin.finops.billing.service;

import com.devin.common.service.AbstractRedisCacheService;
import com.devin.finops.billing.config.BillingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reads billing/consumption data cached by the data-collector from Redis.
 */
@Service
public class BillingCacheService extends AbstractRedisCacheService {

    public BillingCacheService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               BillingProperties properties) {
        super(redisTemplate, objectMapper, properties.getRedisKeyPrefix());
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
            Optional<JsonNode> nodeOpt = readKey("list_users");
            if (nodeOpt.isPresent()) {
                JsonNode node = nodeOpt.get();
                if (node.has("total")) {
                    return node.get("total").asInt(0);
                }
                if (node.has("items") && node.get("items").isArray()) {
                    return node.get("items").size();
                }
            }
        } catch (Exception e) {
            // Already logged in readKey
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
}
