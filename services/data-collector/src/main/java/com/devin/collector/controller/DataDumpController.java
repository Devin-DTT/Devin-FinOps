package com.devin.collector.controller;

import com.devin.collector.config.CollectorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Set;

@Slf4j
@RestController
public class DataDumpController {

    private final StringRedisTemplate redisTemplate;
    private final CollectorProperties properties;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public DataDumpController(StringRedisTemplate redisTemplate,
                              CollectorProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @GetMapping(value = "/dump", produces = MediaType.APPLICATION_JSON_VALUE)
    public String dumpAllEndpoints(
            @RequestParam(required = false) String filter) {
        try {
            String pattern = properties.getRedisKeyPrefix() + (filter != null ? filter : "*");
            Set<String> keys = redisTemplate.keys(pattern);

            ObjectNode root = MAPPER.createObjectNode();
            root.put("generated_at", Instant.now().toString());
            root.put("total_endpoints", keys != null ? keys.size() : 0);

            ObjectNode endpoints = MAPPER.createObjectNode();
            if (keys != null) {
                for (String key : keys) {
                    String value = redisTemplate.opsForValue().get(key);
                    String endpointName = key.replace(properties.getRedisKeyPrefix(), "");
                    ObjectNode entry = MAPPER.createObjectNode();
                    entry.put("redis_key", key);
                    if (value != null && !value.isEmpty()) {
                        try {
                            entry.set("raw_data", MAPPER.readTree(value));
                        } catch (Exception e) {
                            entry.put("raw_data", value);
                        }
                    } else {
                        entry.putNull("raw_data");
                    }
                    endpoints.set(endpointName, entry);
                }
            }
            root.set("endpoints", endpoints);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.error("Failed to dump endpoint data: {}", e.getMessage());
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
