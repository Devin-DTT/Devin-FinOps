package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers and caches session IDs by reading the Redis cache populated
 * by the {@link PollingService} for {@code list_sessions} and
 * {@code list_enterprise_sessions}.
 *
 * <p>Follows the same pattern as {@link OrgDiscoveryService} but derives
 * session IDs from already-cached endpoint data instead of making its own
 * API calls.</p>
 */
@Slf4j
@Service
public class SessionDiscoveryService {

    private final StringRedisTemplate redisTemplate;
    private final CollectorProperties properties;
    private final OrgDiscoveryService orgDiscoveryService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Cache: orgId -> list of session_ids.
     * Key "enterprise" is used for enterprise-scoped sessions.
     */
    private volatile Map<String, List<String>> cachedSessionIds = Collections.emptyMap();

    public SessionDiscoveryService(StringRedisTemplate redisTemplate,
                                   CollectorProperties properties,
                                   OrgDiscoveryService orgDiscoveryService) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.orgDiscoveryService = orgDiscoveryService;
    }

    /**
     * Refreshes the session ID cache by reading from Redis keys that were
     * populated by the polling of {@code list_sessions} and
     * {@code list_enterprise_sessions}.
     *
     * <p>Should be called before polling session-detail endpoints.</p>
     */
    public void refreshFromCache() {
        Map<String, List<String>> newCache = new HashMap<>();
        int maxSessions = properties.getMaxSessionDetailPolling();

        // Enterprise sessions
        String enterpriseKey = properties.getRedisKeyPrefix() + "list_enterprise_sessions";
        String enterpriseData = redisTemplate.opsForValue().get(enterpriseKey);
        if (enterpriseData != null) {
            List<String> ids = extractSessionIds(enterpriseData);
            newCache.put("enterprise", limitList(ids, maxSessions));
        }

        // Org sessions
        for (String orgId : orgDiscoveryService.getCachedOrgIds()) {
            String orgKey = orgDiscoveryService.isMultiOrg()
                    ? properties.getRedisKeyPrefix() + "list_sessions__org_" + orgId
                    : properties.getRedisKeyPrefix() + "list_sessions";
            String orgData = redisTemplate.opsForValue().get(orgKey);
            if (orgData != null) {
                List<String> ids = extractSessionIds(orgData);
                newCache.put(orgId, limitList(ids, maxSessions));
            }
        }

        this.cachedSessionIds = Map.copyOf(newCache);
        int total = newCache.values().stream().mapToInt(List::size).sum();
        if (total > 0) {
            log.debug("Session discovery refreshed: {} total session IDs across {} scopes",
                    total, newCache.size());
        }
    }

    /**
     * Returns enterprise-scoped session IDs.
     */
    public List<String> getEnterpriseSessionIds() {
        return cachedSessionIds.getOrDefault("enterprise", Collections.emptyList());
    }

    /**
     * Returns session IDs for a specific organization.
     */
    public List<String> getOrgSessionIds(String orgId) {
        return cachedSessionIds.getOrDefault(orgId, Collections.emptyList());
    }

    /**
     * Returns all known session IDs across all scopes (deduplicated).
     */
    public List<String> getAllSessionIds() {
        return cachedSessionIds.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
    }

    /**
     * Parses a JSON response to extract session IDs.
     * Looks for arrays in "items", "sessions", or the root, and extracts
     * "session_id" or "id" from each element.
     */
    List<String> extractSessionIds(String rawJson) {
        List<String> sessionIds = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(rawJson);
            JsonNode itemsNode = root.has("items") ? root.get("items")
                    : root.has("sessions") ? root.get("sessions")
                    : root.isArray() ? root : null;

            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode sessionNode : itemsNode) {
                    JsonNode idNode = sessionNode.has("session_id")
                            ? sessionNode.get("session_id")
                            : sessionNode.has("id")
                            ? sessionNode.get("id") : null;
                    if (idNode != null && !idNode.asText().isBlank()) {
                        sessionIds.add(idNode.asText());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse session IDs from cached data: {}",
                    e.getMessage());
        }
        return sessionIds;
    }

    /**
     * Returns at most {@code max} elements from the list (the latest ones,
     * assuming the list order reflects recency).
     */
    private static List<String> limitList(List<String> list, int max) {
        if (list.size() <= max) {
            return list;
        }
        // Keep the last N (most recent) entries
        return List.copyOf(list.subList(list.size() - max, list.size()));
    }
}
