package com.devin.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the in-memory cache of API responses and persists them
 * to {@code data/latest-snapshot.json} after each polling cycle.
 *
 * <p>Extracted from {@code DevinWebSocketHandler} to separate
 * caching/persistence concerns from WebSocket transport.</p>
 */
@Slf4j
@Service
public class SnapshotService {

    private static final Path SNAPSHOT_DIR = Paths.get("data");
    private static final Path SNAPSHOT_FILE = SNAPSHOT_DIR.resolve("latest-snapshot.json");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** In-memory cache of the latest API response per endpoint (key = endpoint name). */
    private final Map<String, JsonNode> latestSnapshot = new ConcurrentHashMap<>();

    /**
     * Caches the parsed JSON response for the given endpoint name.
     *
     * @param endpointName the cache key (may include org suffix in multi-org mode)
     * @param rawData      the raw JSON string from the API
     */
    public void cacheEndpointData(String endpointName, String rawData) {
        try {
            if (rawData != null && !rawData.isEmpty()) {
                JsonNode parsed = OBJECT_MAPPER.readTree(rawData);
                latestSnapshot.put(endpointName, parsed);
            }
        } catch (Exception e) {
            log.warn("Failed to cache data for endpoint {}: {}", endpointName, e.getMessage());
        }
    }

    /**
     * Writes the current in-memory snapshot to {@code data/latest-snapshot.json}.
     */
    public void writeSnapshotToDisk() {
        try {
            if (!Files.exists(SNAPSHOT_DIR)) {
                Files.createDirectories(SNAPSHOT_DIR);
            }
            ObjectNode snapshotNode = PRETTY_MAPPER.createObjectNode();
            snapshotNode.put("timestamp", System.currentTimeMillis());
            ObjectNode endpointsNode = snapshotNode.putObject("endpoints");
            for (Map.Entry<String, JsonNode> entry : latestSnapshot.entrySet()) {
                endpointsNode.set(entry.getKey(), entry.getValue());
            }
            String json = PRETTY_MAPPER.writeValueAsString(snapshotNode);
            Files.writeString(SNAPSHOT_FILE, json,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("Snapshot written to {}", SNAPSHOT_FILE);
        } catch (Exception e) {
            log.warn("Failed to write snapshot to disk: {}", e.getMessage());
        }
    }

    /**
     * Returns the latest cached snapshot (read-only view).
     */
    public Map<String, JsonNode> getLatestSnapshot() {
        return Collections.unmodifiableMap(latestSnapshot);
    }
}
