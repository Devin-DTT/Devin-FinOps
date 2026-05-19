package com.devin.common.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Shared model for WebSocket payload construction.
 * Used by both data-collector (Redis Pub/Sub publishing) and
 * websocket-service (initial snapshot delivery).
 */
public record WebSocketPayload(String type, String endpoint, long timestamp,
                                String orgId, JsonNode data) {

    /**
     * Serializes this payload to a JSON string matching the WebSocket message format.
     */
    public String toJson(ObjectMapper mapper) throws JsonProcessingException {
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        node.put("endpoint", endpoint);
        node.put("timestamp", timestamp);
        if (orgId != null && !orgId.isBlank()) {
            node.put("org_id", orgId);
        }
        if (data != null) {
            node.set("data", data);
        } else {
            node.putNull("data");
        }
        return mapper.writeValueAsString(node);
    }

    /**
     * Creates a WebSocketPayload from a Redis cache key and raw data.
     * Parses the endpoint name and optional org_id from the cache key format:
     * {@code endpoint_name} or {@code endpoint_name__org_orgId}.
     */
    public static WebSocketPayload fromCacheKey(String endpointKey, String rawData,
                                                 ObjectMapper mapper) {
        String endpointName;
        String orgId = null;

        // Parse composite cache key format:
        //   endpoint_name
        //   endpoint_name__org_orgId
        //   endpoint_name__session_sessionId
        //   endpoint_name__org_orgId__session_sessionId
        String remaining = endpointKey;
        int orgIdx = remaining.indexOf("__org_");
        int sessionIdx = remaining.indexOf("__session_");

        if (orgIdx >= 0) {
            endpointName = remaining.substring(0, orgIdx);
            String afterOrg = remaining.substring(orgIdx + 6);
            // If there's also a __session_ part after __org_, strip it from orgId
            int sessionInOrg = afterOrg.indexOf("__session_");
            orgId = sessionInOrg >= 0 ? afterOrg.substring(0, sessionInOrg) : afterOrg;
        } else if (sessionIdx >= 0) {
            endpointName = remaining.substring(0, sessionIdx);
        } else {
            endpointName = endpointKey;
        }

        JsonNode dataNode = null;
        if (rawData != null && !rawData.isEmpty()) {
            try {
                dataNode = mapper.readTree(rawData);
            } catch (Exception ignored) {
                // If parsing fails, data will be null
            }
        }
        return new WebSocketPayload("data", endpointName,
                System.currentTimeMillis(), orgId, dataNode);
    }
}
