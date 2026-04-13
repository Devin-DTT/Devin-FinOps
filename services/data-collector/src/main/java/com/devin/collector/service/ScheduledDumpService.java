package com.devin.collector.service;

import com.devin.collector.config.CollectorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@ConditionalOnProperty(name = "collector.dump-enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledDumpService {

    private final StringRedisTemplate redisTemplate;
    private final CollectorProperties properties;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ScheduledDumpService(StringRedisTemplate redisTemplate,
                                 CollectorProperties properties,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void start() {
        long interval = properties.getDumpIntervalSeconds();
        // Initial delay of 10 seconds to let the first polling cycle complete
        scheduler.scheduleAtFixedRate(this::writeDumpFile, 10, interval, TimeUnit.SECONDS);
        log.info("Scheduled automatic dump every {}s to {}", interval, properties.getDumpFilePath());
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    private void writeDumpFile() {
        try {
            String pattern = properties.getRedisKeyPrefix() + "*";
            Set<String> keys = redisTemplate.keys(pattern);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("generated_at", Instant.now().toString());
            root.put("total_endpoints", keys != null ? keys.size() : 0);

            ObjectNode endpoints = objectMapper.createObjectNode();
            if (keys != null) {
                for (String key : keys) {
                    String value = redisTemplate.opsForValue().get(key);
                    String endpointName = key.replace(properties.getRedisKeyPrefix(), "");
                    ObjectNode entry = objectMapper.createObjectNode();
                    entry.put("redis_key", key);
                    if (value != null && !value.isEmpty()) {
                        try {
                            entry.set("raw_data", objectMapper.readTree(value));
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

            File dumpFile = new File(properties.getDumpFilePath());
            // Create parent directories if they don't exist
            if (dumpFile.getParentFile() != null) {
                dumpFile.getParentFile().mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dumpFile, root);
            log.debug("Wrote raw endpoint dump to {} ({} endpoints)",
                       properties.getDumpFilePath(), keys != null ? keys.size() : 0);
        } catch (Exception e) {
            log.warn("Failed to write dump file: {}", e.getMessage());
        }
    }
}
