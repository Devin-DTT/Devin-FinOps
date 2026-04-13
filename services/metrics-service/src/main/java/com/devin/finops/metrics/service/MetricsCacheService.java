package com.devin.finops.metrics.service;

import com.devin.common.service.AbstractRedisCacheService;
import com.devin.finops.metrics.config.MetricsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Reads metrics data cached by the data-collector from Redis.
 * Normalizes time-series data from epoch seconds to ISO date strings.
 */
@Service
public class MetricsCacheService extends AbstractRedisCacheService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    public MetricsCacheService(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               MetricsProperties properties) {
        super(redisTemplate, objectMapper, properties.getRedisKeyPrefix());
    }

    public Optional<JsonNode> getDauMetrics() {
        return readAndNormalize("get_dau_metrics");
    }

    public Optional<JsonNode> getWauMetrics() {
        return readAndNormalize("get_wau_metrics");
    }

    public Optional<JsonNode> getMauMetrics() {
        return readAndNormalize("get_mau_metrics");
    }

    public Optional<JsonNode> getActiveUsersMetrics() {
        return readAndNormalize("get_active_users_metrics");
    }

    public Optional<JsonNode> getSessionsMetrics() {
        return readAndNormalize("get_sessions_metrics");
    }

    public Optional<JsonNode> getSearchesMetrics() {
        return readAndNormalize("get_searches_metrics");
    }

    public Optional<JsonNode> getPrsMetrics() {
        return readAndNormalize("get_prs_metrics");
    }

    public Optional<JsonNode> getUsageMetrics() {
        return readAndNormalize("get_usage_metrics");
    }

    /**
     * Reads from Redis and normalizes time-series entries.
     * The Devin API returns arrays like [{start_time: epoch, end_time: epoch, ...}, ...]
     * This converts epoch seconds to ISO date strings for frontend consumption.
     */
    private Optional<JsonNode> readAndNormalize(String endpointName) {
        Optional<JsonNode> nodeOpt = readKey(endpointName);
        return nodeOpt.map(this::normalizeTimeSeries);
    }

    /**
     * Normalizes time-series data: converts epoch seconds in start_time/end_time
     * fields to ISO date strings and adds a 'date' field.
     */
    private JsonNode normalizeTimeSeries(JsonNode node) {
        // Find the array of entries (may be root array, or under 'items'/'data')
        JsonNode entries;
        if (node.isArray()) {
            entries = node;
        } else if (node.has("items") && node.get("items").isArray()) {
            entries = node.get("items");
        } else if (node.has("data") && node.get("data").isArray()) {
            entries = node.get("data");
        } else {
            return node;
        }

        ArrayNode normalized = mapper.createArrayNode();
        for (JsonNode entry : entries) {
            ObjectNode obj = entry.isObject()
                    ? ((ObjectNode) entry).deepCopy()
                    : mapper.createObjectNode();

            // Convert start_time epoch to ISO date
            if (obj.has("start_time") && obj.get("start_time").isNumber()) {
                long epochSec = obj.get("start_time").asLong(0);
                if (epochSec > 0) {
                    String dateStr = DATE_FMT.format(
                            Instant.ofEpochSecond(epochSec));
                    obj.put("date", dateStr);
                }
            }
            // If there's already a date field as epoch, convert it
            if (obj.has("date") && obj.get("date").isNumber()) {
                long epochSec = obj.get("date").asLong(0);
                if (epochSec > 0) {
                    String dateStr = DATE_FMT.format(
                            Instant.ofEpochSecond(epochSec));
                    obj.put("date", dateStr);
                }
            }
            normalized.add(obj);
        }

        // If original was wrapped in an object, return wrapped
        if (node.isObject()) {
            ObjectNode result = ((ObjectNode) node).deepCopy();
            if (result.has("items")) {
                result.set("items", normalized);
            } else if (result.has("data")) {
                result.set("data", normalized);
            } else {
                return normalized;
            }
            return result;
        }
        return normalized;
    }
}
