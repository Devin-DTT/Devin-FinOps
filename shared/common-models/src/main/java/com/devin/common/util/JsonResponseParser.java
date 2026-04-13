package com.devin.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for extracting IDs from JSON API responses.
 * Handles common patterns: arrays in "items", named arrays, or root arrays,
 * with configurable ID field names.
 */
@Slf4j
public final class JsonResponseParser {

    private JsonResponseParser() {
    }

    /**
     * Extracts IDs from a JSON response by searching for an array (in "items",
     * named wrapper keys, or root) and extracting values from the specified
     * field names.
     *
     * @param rawJson       the raw JSON string
     * @param mapper        ObjectMapper instance to use
     * @param arrayKeys     wrapper keys to look for arrays (e.g. "organizations", "sessions")
     * @param idFieldNames  field names to try for extracting the ID value
     * @return list of extracted ID strings
     */
    public static List<String> extractIds(String rawJson, ObjectMapper mapper,
                                          List<String> arrayKeys,
                                          String... idFieldNames) {
        List<String> ids = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(rawJson);
            JsonNode itemsNode = findArray(root, arrayKeys);

            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode element : itemsNode) {
                    String id = extractIdFromNode(element, idFieldNames);
                    if (id != null && !id.isBlank()) {
                        ids.add(id);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse IDs from JSON: {}", e.getMessage());
        }
        return ids;
    }

    private static JsonNode findArray(JsonNode root, List<String> arrayKeys) {
        if (root.has("items") && root.get("items").isArray()) {
            return root.get("items");
        }
        for (String key : arrayKeys) {
            if (root.has(key) && root.get(key).isArray()) {
                return root.get(key);
            }
        }
        if (root.isArray()) {
            return root;
        }
        return null;
    }

    private static String extractIdFromNode(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (node.has(fieldName)) {
                String value = node.get(fieldName).asText();
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }
}
